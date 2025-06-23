package org.odk.collect.android.summary

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle

class FilterViewModel(
    private val handle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_SELECTED_DATE = "selected_date"
        private const val KEY_SELECTED_FORM = "selected_form"
        private const val KEY_SELECTED_FOKONTANY = "selected_fokontany"
        private const val KEY_SELECTED_COMMUNE = "selected_commune"
    }

    val selectedDate: LiveData<Long?> = handle.getLiveData(KEY_SELECTED_DATE)
    val selectedForm: LiveData<String?> = handle.getLiveData(KEY_SELECTED_FORM)
    val selectedFokontany: LiveData<String?> = handle.getLiveData(KEY_SELECTED_FOKONTANY)
    val selectedCommune: LiveData<String?> = handle.getLiveData(KEY_SELECTED_COMMUNE)

    fun setDate(date: Long?) {
        handle[KEY_SELECTED_DATE] = date
    }

    fun setForm(formId: String?) {
        handle[KEY_SELECTED_FORM] = formId
    }

    fun setFokontany(fokontany: String?) {
        handle[KEY_SELECTED_FOKONTANY] = fokontany
    }

    fun setCommune(commune: String?) {
        handle[KEY_SELECTED_COMMUNE] = commune
    }
}
