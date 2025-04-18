package org.odk.collect.android.summary

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import org.odk.collect.forms.instances.Instance
import android.view.LayoutInflater
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.TextView
import org.odk.collect.android.R
import org.odk.collect.material.ErrorsPill
import org.odk.collect.android.summary.utils.extractFieldValueFromXml
import org.odk.collect.android.summary.utils.extractDateFieldAsMillis
import java.text.DecimalFormat
import java.text.NumberFormat

class InstanceListAdapter(
    private val context: Context,
    private val instances: List<Instance>
) : BaseAdapter() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun getCount(): Int = instances.size

    override fun getItem(position: Int): Instance = instances[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.form_chooser_list_item, parent, false)
        val instance = getItem(position)

        val titleView = view.findViewById<TextView>(R.id.form_title)
        val subtitleView = view.findViewById<TextView>(R.id.form_subtitle)
        val subtitleView2 = view.findViewById<TextView>(R.id.form_subtitle2)

        val formatter = NumberFormat.getIntegerInstance() as DecimalFormat

        // Set form title
        titleView.text = instance.displayName

        // Set subtitle: form finalization date
        val endDateLocal = extractDateFieldAsMillis(instance.instanceFilePath, "end")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        subtitleView.text = endDateLocal?.let { dateFormat.format(Date(it)) } ?: ""

        // Set subtitle2: HOPE ID and total
        val hopeId = extractFieldValueFromXml(instance.instanceFilePath, "hope_household_id")
        val total = extractFieldValueFromXml(instance.instanceFilePath, "montant")
        val totalInt = total?.toDoubleOrNull()?.toInt() ?: 0
        val formattedTotal = formatter.format(totalInt)
        val totalDisplay = context.getString(org.odk.collect.strings.R.string.amount_ariary, formattedTotal)
        subtitleView2.visibility = View.VISIBLE
        subtitleView2.text = context.getString(org.odk.collect.strings.R.string.hope_id_and_total, hopeId, totalDisplay)

        val pill = view.findViewById<ErrorsPill>(R.id.chip)
        if (pill != null) {
            pill.visibility = View.GONE
        }

        return view
    }
}
