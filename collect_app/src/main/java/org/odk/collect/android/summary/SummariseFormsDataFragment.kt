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
import org.odk.collect.forms.instances.Instance
import org.odk.collect.forms.instances.InstancesRepository
import org.odk.collect.lists.EmptyListView
import java.io.File
import java.text.DecimalFormat
import java.text.NumberFormat
import javax.xml.parsers.DocumentBuilderFactory
import androidx.lifecycle.MediatorLiveData

class SummariseFormsDataFragment(
    private val instancesRepository: InstancesRepository
) : Fragment() {

    private val filterViewModel: FilterViewModel by activityViewModels()

    private val combinedFilter = MediatorLiveData<Pair<Long?, String?>>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.summarise_forms_layout, container, false)

        // Refresh data when filters are set
        combinedFilter.addSource(filterViewModel.selectedDate) { date: Long? ->
            combinedFilter.value = Pair(date, filterViewModel.selectedForm.value)
        }
        combinedFilter.addSource(filterViewModel.selectedForm) { form: String? ->
            combinedFilter.value = Pair(filterViewModel.selectedDate.value, form)
        }
        combinedFilter.observe(viewLifecycleOwner) { filterPair: Pair<Long?, String?> ->
            val (date, formId) = filterPair
            refreshSummaryForDate(view, date ?: 0, formId ?: "")
        }

        return view
    }

    private fun refreshSummaryForDate(view: View, date: Long, formId: String) {
        val finalizedForms = instancesRepository.getAllByStatus(
            Instance.STATUS_COMPLETE,
            Instance.STATUS_SUBMITTED,
            Instance.STATUS_SUBMISSION_FAILED
        )
        val filteredForms = finalizedForms.filter {
            val finalizationDate = extractDateFieldAsMillis(it.instanceFilePath, "end")
            val matchesDate = finalizationDate != null && isSameDay(finalizationDate, date)
            val matchesForm = it.formId == formId

            matchesDate && matchesForm
        }

        val emptyListView = view.findViewById<EmptyListView>(R.id.empty)
        val container = view.findViewById<ViewGroup>(R.id.summary_container)
        val totalValue = view.findViewById<TextView>(R.id.totalValue)
        val countPaymentsValue = view.findViewById<TextView>(R.id.countPaymentsValue)
        val countHouseholdsValue = view.findViewById<TextView>(R.id.countHouseholdsValue)
        val formatter = NumberFormat.getIntegerInstance() as DecimalFormat

        if (filteredForms.isEmpty()) {
            emptyListView.visibility = View.VISIBLE
            setTextViewsVisibility(container, View.GONE)
        } else {
            emptyListView.visibility = View.GONE
            setTextViewsVisibility(container, View.VISIBLE)

            countPaymentsValue.text = filteredForms.size.toString()
            val total = sumFieldAcrossForms(filteredForms, "montant")
            val formattedTotal = formatter.format(total.toInt())
            val totalDisplay = context?.getString(org.odk.collect.strings.R.string.amount_ariary, formattedTotal)
            totalValue.text = totalDisplay
            val householdsList = getUniqueFieldValues(filteredForms, "hope_household_id")
            countHouseholdsValue.text = householdsList.size.toString()
        }
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
