package org.odk.collect.android.summary

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MediatorLiveData
import org.odk.collect.android.R
import org.odk.collect.android.external.InstancesContract
import org.odk.collect.android.summary.utils.extractDateFieldAsMillis
import org.odk.collect.android.summary.utils.extractFieldValueFromXml
import org.odk.collect.android.summary.utils.isSameDay
import org.odk.collect.forms.instances.Instance
import org.odk.collect.forms.instances.InstancesRepository
import org.odk.collect.lists.EmptyListView
import org.odk.collect.android.external.FormUriActivity
import org.odk.collect.android.formentry.FormOpeningMode

class SummariseFormsListFragment(
    private val instancesRepository: InstancesRepository,
    private val projectId: String
) : Fragment() {

    private val filterViewModel: FilterViewModel by activityViewModels()

    private val combinedFilter = MediatorLiveData<FilterParams>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.summarise_forms_list_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
            refreshFormsForFilters(view, date ?: 0, formId ?: "", fokontany, commune)
        }
    }

    private fun refreshFormsForFilters(view: View, date: Long, formId: String, fokontany: String?, commune: String?) {
        val listView = view.findViewById<ListView>(R.id.form_list)
        val emptyListView = view.findViewById<EmptyListView>(R.id.empty)

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
        .sortedByDescending {
            extractDateFieldAsMillis(it.instanceFilePath, "end") ?: 0L
        }

        if (filteredForms.isEmpty()) {
            emptyListView.visibility = View.VISIBLE
        } else {
            emptyListView.visibility = View.GONE
            listView.adapter = InstanceListAdapter(requireContext(), filteredForms)

            listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                val instance = filteredForms[position]
                openFormInReadOnlyMode(instance)
            }
        }
    }

    private fun openFormInReadOnlyMode(instance: Instance) {
        val instanceUri = InstancesContract.getUri(projectId, instance.dbId)
        val intent = Intent(requireContext(), FormUriActivity::class.java).apply {
            action = Intent.ACTION_EDIT
            data = instanceUri
            putExtra(FormOpeningMode.FORM_MODE_KEY, FormOpeningMode.VIEW_SENT)
        }

        startActivity(intent)
    }
}
