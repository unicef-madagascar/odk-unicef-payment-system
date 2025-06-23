package org.odk.collect.android.summary.utils

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.io.File
import java.util.Calendar
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

fun extractDateFieldAsMillis(instancePath: String, fieldName: String): Long? {
    return try {
        extractFieldValueFromXml(instancePath, fieldName)?.let { text ->
            val parsedDate = OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            parsedDate.toInstant().toEpochMilli()
        }
    } catch (e: Exception) {
        null
    }
}

fun extractFieldValueFromXml(filePath: String, fieldName: String): String? {
    return try {
        val xmlFile = File(filePath)
        if (!xmlFile.exists()) return null
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        val node = doc.getElementsByTagName(fieldName).item(0) ?: return null
        node.textContent.trim()
    } catch (e: Exception) {
        null
    }
}

fun isSameDay(date1: Long, date2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = date1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = date2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

fun extractAllFields(xmlPath: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    try {
        val file = File(xmlPath)
        if (!file.exists()) return result

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = doc.documentElement

        fun extractRecursively(node: Node, prefix: String = "") {
            val children = node.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val name = if (prefix.isEmpty()) child.nodeName else "$prefix/${child.nodeName}"

                    if (child.childNodes.length == 1 && child.firstChild.nodeType == Node.TEXT_NODE) {
                        result[name] = child.textContent.trim()
                    } else {
                        extractRecursively(child, name)
                    }
                }
            }
        }

        extractRecursively(root)
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return result
}

fun displayDate(epochMillis: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}
