package com.example.focusdesk

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FocusSessionRecord(
    val timestamp: String,
    val durationText: String,
    val completed: Boolean
)

object SessionRepository {
    var completedCount: Int = 0
    var interruptedCount: Int = 0
    var totalFocusSeconds: Long = 0L
    val history: MutableList<FocusSessionRecord> = mutableListOf()

    fun addSession(durationMs: Long, completed: Boolean) {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val now = timeFormat.format(Date())

        val totalSec = durationMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val formattedDur = if (min > 0) "${min}m ${sec}s" else "${sec}s"

        if (completed) {
            completedCount++
            totalFocusSeconds += totalSec
        } else {
            interruptedCount++
        }

        history.add(0, FocusSessionRecord(now, formattedDur, completed))
    }

    fun clearAll() {
        completedCount = 0
        interruptedCount = 0
        totalFocusSeconds = 0L
        history.clear()
    }
}