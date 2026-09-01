package com.jtexpress.bevest.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateTimeUtils {

    private val dateTime = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    private val dateOnly = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val monthKeyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
    private val monthLabelFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    fun formatDateTime(millis: Long?): String =
        if (millis == null || millis <= 0) "—" else dateTime.format(Date(millis))

    fun formatDate(millis: Long?): String =
        if (millis == null || millis <= 0) "—" else dateOnly.format(Date(millis))

    fun monthKey(millis: Long): String = monthKeyFmt.format(Date(millis))

    fun monthLabel(key: String): String = try {
        monthLabelFmt.format(monthKeyFmt.parse(key)!!)
    } catch (e: Exception) {
        key
    }

    /** "just now", "2m ago", "1h 4m ago", "3d ago" (plan section 21). */
    fun relativeAge(millis: Long?, now: Long = System.currentTimeMillis()): String {
        if (millis == null || millis <= 0) return "no data"
        val diff = (now - millis).coerceAtLeast(0)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            diff < TimeUnit.SECONDS.toMillis(30) -> "just now"
            minutes < 1 -> "${TimeUnit.MILLISECONDS.toSeconds(diff)}s ago"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ${minutes % 60}m ago"
            else -> "${days}d ago"
        }
    }
}
