package org.odk.collect.android.summary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import org.odk.collect.android.R
import org.odk.collect.android.summary.utils.extractDateFieldAsMillis
import org.odk.collect.android.summary.utils.isSameDay
import org.odk.collect.android.summary.utils.displayDate
import org.odk.collect.forms.instances.Instance
import org.odk.collect.forms.instances.InstancesRepository
import java.io.File
import java.text.DecimalFormat
import java.text.NumberFormat
import javax.xml.parsers.DocumentBuilderFactory
import androidx.lifecycle.MediatorLiveData
import org.odk.collect.android.summary.utils.extractFieldValueFromXml

data class FilterParams(
    val date:      Long?,
    val formId:    String?,
    val fokontany: String?,
    val commune:   String?
)

class SummariseFormsDataFragment(
    private val instancesRepository: InstancesRepository
) : Fragment() {

    private val filterViewModel: FilterViewModel by activityViewModels()

    private val combinedFilter = MediatorLiveData<FilterParams>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.summarise_forms_layout, container, false)

        // Refresh data when filters are set
        combinedFilter.addSource(filterViewModel.selectedDate) { date: Long? ->
            combinedFilter.value = FilterParams(date, filterViewModel.selectedForm.value, filterViewModel.selectedFokontany.value, filterViewModel.selectedCommune.value)
        }
        combinedFilter.addSource(filterViewModel.selectedForm) { form: String? ->
            combinedFilter.value = FilterParams(filterViewModel.selectedDate.value, form, filterViewModel.selectedFokontany.value, filterViewModel.selectedCommune.value)
        }
        combinedFilter.addSource(filterViewModel.selectedFokontany) { fokontany: String? ->
            combinedFilter.value = FilterParams(filterViewModel.selectedDate.value, filterViewModel.selectedForm.value, fokontany, filterViewModel.selectedCommune.value)
        }
        combinedFilter.addSource(filterViewModel.selectedCommune) { commune: String? ->
            combinedFilter.value = FilterParams(filterViewModel.selectedDate.value, filterViewModel.selectedForm.value, filterViewModel.selectedFokontany.value, commune)
        }
        combinedFilter.observe(viewLifecycleOwner) { filters: FilterParams ->
            val (date, formId, fokontany, commune) = filters
            refreshSummaryForFilters(view, date ?: 0, formId ?: "", fokontany, commune)
        }

        return view
    }

    private fun refreshSummaryForFilters(view: View, date: Long, formId: String, fokontany: String?, commune: String?) {
        val finalizedForms = instancesRepository.getAllByStatus(
            Instance.STATUS_COMPLETE,
            Instance.STATUS_SUBMITTED,
            Instance.STATUS_SUBMISSION_FAILED
        )
        val filteredForms = finalizedForms.filter {
            val finalizationDate = extractDateFieldAsMillis(it.instanceFilePath, "end")
            val matchesDate = finalizationDate != null && isSameDay(finalizationDate, date)
            val matchesForm = it.formId == formId
            val fokontanyFormValue = extractFieldValueFromXml(it.instanceFilePath, "fokontany")
            val matchesFokontany = (fokontany == null) || (fokontanyFormValue == fokontany)
            val communeFormValue = extractFieldValueFromXml(it.instanceFilePath, "commune")
            val matchesCommune = (commune == null) || (communeFormValue == commune)

            matchesDate && matchesForm && matchesFokontany && matchesCommune
        }
        val filterFormValue = view.findViewById<TextView>(R.id.filterFormValue)
        val filterDateValue = view.findViewById<TextView>(R.id.filterDateValue)
        val filterFokontanyValue = view.findViewById<TextView>(R.id.filterFokontanyValue)
        val filterCommuneValue = view.findViewById<TextView>(R.id.filterCommuneValue)
        val totalValue = view.findViewById<TextView>(R.id.totalValue)
        val countPaymentsValue = view.findViewById<TextView>(R.id.countPaymentsValue)
        val countPaymentsLabel = view.findViewById<TextView>(R.id.countPaymentsLabel)
        val countHouseholdsValue = view.findViewById<TextView>(R.id.countHouseholdsValue)
        val countHouseholdsLabel = view.findViewById<TextView>(R.id.countHouseholdsLabel)
        val formatter = NumberFormat.getIntegerInstance() as DecimalFormat

        filterFormValue.text = filteredForms.firstOrNull()?.displayName ?: getString(org.odk.collect.strings.R.string.default_label)
        filterDateValue.text = getString(
            org.odk.collect.strings.R.string.label_date,
            displayDate(date)
        )
        filterFokontanyValue.text = getString(
            org.odk.collect.strings.R.string.label_fokontany,
            fokontany ?: getString(org.odk.collect.strings.R.string.filter_all_fokontany_display)
        )
        filterCommuneValue.text = getString(
            org.odk.collect.strings.R.string.label_commune,
            commune ?: getString(org.odk.collect.strings.R.string.filter_all_commune_display)
        )

        val montantTotal = sumFieldAcrossForms(filteredForms, "montant")
        val montantTotalFormatted = formatter.format(montantTotal.toInt())
        val totalDisplay = context?.getString(org.odk.collect.strings.R.string.amount_ariary, montantTotalFormatted)
        totalValue.text = totalDisplay

        val paymentCount = filteredForms.size
        countPaymentsValue.text = paymentCount.toString()
        countPaymentsLabel.text = resources.getQuantityString(
            org.odk.collect.strings.R.plurals.summary_total_payments,
            paymentCount
        )

        val householdsList = getUniqueFieldValues(filteredForms, "hope_id_menage")
        val householdsCount = householdsList.size
        countHouseholdsValue.text = householdsCount.toString()
        countHouseholdsLabel.text = resources.getQuantityString(
            org.odk.collect.strings.R.plurals.summary_total_households,
            householdsCount
        )
    }

    private fun sumFieldAcrossForms(forms: List<Instance>, fieldName: String): Double {
        var total = 0.0
        val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        for (form in forms) {
            val xmlFile = File(form.instanceFilePath)
            if (!xmlFile.exists()) continue

            try {
                val document = documentBuilder.parse(xmlFile)
                val nodes = document.getElementsByTagName(fieldName)
                if (nodes.length > 0) {
                    nodes.item(0).textContent.trim().toDoubleOrNull()?.let {
                        total += it
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return total
    }

    private fun getUniqueFieldValues(forms: List<Instance>, fieldName: String): Set<String> {
        val uniqueValues = mutableSetOf<String>()
        val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        for (form in forms) {
            val xmlFile = File(form.instanceFilePath)
            if (!xmlFile.exists()) continue

            try {
                val document = documentBuilder.parse(xmlFile)
                val nodes = document.getElementsByTagName(fieldName)
                if (nodes.length > 0) {
                    val value = nodes.item(0).textContent.trim()
                    if (value.isNotEmpty()) {
                        uniqueValues.add(value)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return uniqueValues
    }

    private fun setTextViewsVisibility(view: ViewGroup, visibility: Int) {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (child is TextView) {
                child.visibility = visibility
            }
        }
    }
}
