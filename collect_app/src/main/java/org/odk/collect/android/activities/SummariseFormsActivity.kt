package org.odk.collect.android.activities

import android.os.Bundle
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import android.view.Menu
import android.view.MenuItem
import android.view.MenuInflater
import org.odk.collect.android.R
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.odk.collect.android.databinding.TabsLayoutBinding
import org.odk.collect.android.summary.SummariseFormsDataFragment
import org.odk.collect.android.injection.DaggerUtils
import org.odk.collect.android.injection.config.ProjectDependencyModuleFactory
import org.odk.collect.android.projects.ProjectsDataService
import org.odk.collect.androidshared.ui.FragmentFactoryBuilder
import org.odk.collect.androidshared.ui.ListFragmentStateAdapter
import org.odk.collect.androidshared.utils.AppBarUtils.setupAppBarLayout
import org.odk.collect.forms.instances.InstancesRepository
import org.odk.collect.strings.localization.LocalizedActivity
import org.odk.collect.forms.instances.Instance
import org.odk.collect.android.summary.utils.extractDateFieldAsMillis
import org.odk.collect.android.summary.utils.isSameDay
import org.odk.collect.android.summary.utils.extractAllFields
import androidx.activity.viewModels
import org.odk.collect.android.summary.FilterViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import javax.inject.Inject
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.SharedPreferences
import org.odk.collect.android.summary.SummariseFormsListFragment
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.android.material.snackbar.Snackbar
import android.view.View

class SummariseFormsActivity : LocalizedActivity() {
    @Inject
    lateinit var projectDependencyModuleFactory: ProjectDependencyModuleFactory

    @Inject
    lateinit var projectsDataService: ProjectsDataService

    private lateinit var instancesRepository: InstancesRepository

    private lateinit var binding: TabsLayoutBinding

    private val filterViewModel: FilterViewModel by viewModels()

    private val preferences: SharedPreferences by lazy {
        getSharedPreferences("filters", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DaggerUtils.getComponent(this).inject(this)

        val projectId = projectsDataService.requireCurrentProject().uuid
        val projectDependencyModule = projectDependencyModuleFactory.create(projectId)

        instancesRepository = projectDependencyModule.instancesRepository

        supportFragmentManager.fragmentFactory = FragmentFactoryBuilder()
            .forClass(SummariseFormsDataFragment::class) {
                SummariseFormsDataFragment(instancesRepository)
            }
            .forClass(SummariseFormsListFragment::class) {
                SummariseFormsListFragment(instancesRepository, projectId)
            }
            .build()

        super.onCreate(savedInstanceState)
        binding = TabsLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupAppBarLayout(this, getString(org.odk.collect.strings.R.string.summarise_forms))

        // Set default filters for form and date
        setDefaultForm()
        setDefaultDate()

        setUpViewPager()
        setupToolbarFilter()
    }

    private fun setDefaultForm() {
        if (filterViewModel.selectedForm.value == null) {
            val savedForm = preferences.getString("selected_form", null)
            if (savedForm != null) {
                filterViewModel.setForm(savedForm)
            } else {
                val formPairs = getFormPairs()
                filterViewModel.setForm(formPairs.firstOrNull()?.first)
            }
        }
    }

    private fun getFormPairs(): List<Pair<String, String>> {
        val finalizedForms = instancesRepository.getAllByStatus(
            Instance.STATUS_COMPLETE,
            Instance.STATUS_SUBMITTED,
            Instance.STATUS_SUBMISSION_FAILED
        )
        return finalizedForms
            .mapNotNull { instance ->
                val formId = instance.formId
                val formName = instance.displayName
                if (formId != null) {
                    formId to formName
                } else {
                    null
                }
            }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    private fun setDefaultDate() {
        if (filterViewModel.selectedDate.value == null) {
            val savedDate = preferences.getLong("selected_date", -1)
            if (savedDate != -1L) {
                filterViewModel.setDate(savedDate)
            } else {
                filterViewModel.setDate(MaterialDatePicker.todayInUtcMilliseconds())
            }
        }
    }

    private fun setUpViewPager() {

        val fragments = listOf(
            SummariseFormsDataFragment::class.java,
            SummariseFormsListFragment::class.java
        )

        val viewPager = binding.viewPager.also {
            it.adapter =
                ListFragmentStateAdapter(
                    this,
                    fragments
                )
        }

        TabLayoutMediator(binding.tabLayout, viewPager) { tab: TabLayout.Tab, position: Int ->
            tab.text = if (position == 0) {
                getString(org.odk.collect.strings.R.string.data_summary)
            } else {
                getString(org.odk.collect.strings.R.string.data)
            }
        }.attach()
    }

    private fun setupToolbarFilter() {
        val menuHost: MenuHost = this

        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_date_filter, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_filter_form -> {
                        showFormSelectDialog()
                        true
                    }
                    R.id.action_filter_date -> {
                        showDatePicker()
                        true
                    }
                    R.id.action_download_data -> {
                        downloadDataForSelectedDate()
                        true
                    }
                    R.id.action_share_data -> {
                        shareDataForSelectedDate()
                        true
                    }
                    else -> false
                }
            }
        }, this)
    }

    private fun showFormSelectDialog() {
        val formPairs = getFormPairs()
        val formIds = formPairs.map { it.first }
        val formLabels = formPairs.map { it.second }.toTypedArray()

        val selectedForm = filterViewModel.selectedForm.value
        val selectedIndex = formIds.indexOfFirst { it == selectedForm }

        MaterialAlertDialogBuilder(this@SummariseFormsActivity)
            .setTitle(getString(org.odk.collect.strings.R.string.filter_by_form))
            .setSingleChoiceItems(formLabels, selectedIndex) { dialog, which ->
                filterViewModel.setForm(formIds[which])
                preferences.edit().putString("selected_form", formIds[which]).apply()
                dialog.dismiss()
            }
            .show()
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(org.odk.collect.strings.R.string.filter_by_date))
            .setSelection(filterViewModel.selectedDate.value)
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            filterViewModel.setDate(selection)
            preferences.edit().putLong("selected_date", selection).apply()
        }

        picker.show(supportFragmentManager, "DATE_PICKER")
    }

    private fun buildCsvForSelectedDate(): Pair<String, String>? {
        val date = filterViewModel.selectedDate.value ?: MaterialDatePicker.todayInUtcMilliseconds()
        val formId = filterViewModel.selectedForm.value

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

        if (filteredForms.isEmpty()) {
            Toast.makeText(this, getString(org.odk.collect.strings.R.string.no_data_for_date), Toast.LENGTH_SHORT).show()
            return null
        }

        // Build CSV
        val allFieldNames = mutableSetOf<String>()
        val formData = filteredForms.map { instance ->
            val data = extractAllFields(instance.instanceFilePath).toMutableMap()
            data["instanceID"] = File(instance.instanceFilePath).nameWithoutExtension
            allFieldNames.addAll(data.keys)
            data
        }

        val sortedFieldNames = allFieldNames.sorted()
        val rows = mutableListOf(sortedFieldNames.joinToString(","))
        formData.forEach { record ->
            val row = sortedFieldNames.map { field ->
                val raw = record[field] ?: ""
                "\"${raw.replace("\"", "\"\"")}\"" // escape quotes
            }
            rows.add(row.joinToString(","))
        }

        val csv = rows.joinToString("\n")
        val firstFormDisplayName = filteredForms.firstOrNull()?.displayName

        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        val fileName = "${firstFormDisplayName}__${timestamp}.csv"

        return Pair(fileName, csv)
    }

    private fun downloadDataForSelectedDate() {
        val result = buildCsvForSelectedDate() ?: return
        val (fileName, csv) = result

        saveCsvToDownloads(this, fileName, csv)
    }

    private fun saveCsvToDownloads(context: Context, baseFileName: String, csv: String) {
        val rootView = findViewById<View>(android.R.id.content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver

            // Add extension if not already added
            val extension = ".csv"
            var fileName = baseFileName
            if (!fileName.endsWith(extension)) {
                fileName += extension
            }

            // Only access MediaStore.Downloads safely inside API check
            val downloadsUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(downloadsUri, contentValues)
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(csv.toByteArray())
                        outputStream.flush()
                    }

                    val finishValues = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(uri, finishValues, null, null)

                    Snackbar.make(rootView, context.getString(org.odk.collect.strings.R.string.download_file_success, fileName), Snackbar.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Snackbar.make(rootView, context.getString(org.odk.collect.strings.R.string.download_file_error, e.message), Snackbar.LENGTH_LONG).show()
                }
            } else {
                Snackbar.make(rootView, context.getString(org.odk.collect.strings.R.string.create_file_error), Snackbar.LENGTH_LONG).show()
            }
        } else {
            Snackbar.make(rootView, context.getString(org.odk.collect.strings.R.string.android_version_not_supported), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun shareDataForSelectedDate() {
        val result = buildCsvForSelectedDate() ?: return
        val (fileName, csv) = result

        val file = File(cacheDir, fileName)
        file.writeText(csv)

        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        Toast.makeText(this, getString(org.odk.collect.strings.R.string.share_data_in_progress), Toast.LENGTH_SHORT).show()
        startActivity(Intent.createChooser(shareIntent, getString(org.odk.collect.strings.R.string.share_data)))
    }
}
