package com.example.focusdesk

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class StatsActivity : AppCompatActivity() {

    private lateinit var tvStatCompleted: TextView
    private lateinit var tvStatTotalTime: TextView
    private lateinit var tvStatInterrupted: TextView
    private lateinit var tvHistoryList: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        val btnBack: Button = findViewById(R.id.btnBack)
        val btnClearHistory: Button = findViewById(R.id.btnClearHistory)
        tvStatCompleted = findViewById(R.id.tvStatCompleted)
        tvStatTotalTime = findViewById(R.id.tvStatTotalTime)
        tvStatInterrupted = findViewById(R.id.tvStatInterrupted)
        tvHistoryList = findViewById(R.id.tvHistoryList)

        btnBack.setOnClickListener {
            finish() // Închide Ecranul 2 și revine la Ecranul 1
        }

        btnClearHistory.setOnClickListener {
            SessionRepository.clearAll()
            refreshUI()
        }

        refreshUI()
    }

    private fun refreshUI() {
        tvStatCompleted.text = SessionRepository.completedCount.toString()
        tvStatInterrupted.text = SessionRepository.interruptedCount.toString()

        val totalSec = SessionRepository.totalFocusSeconds
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        tvStatTotalTime.text = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"

        if (SessionRepository.history.isEmpty()) {
            tvHistoryList.text = "Nu există încă sesiuni înregistrate.\nAșază telefonul cu fața în jos pe ecranul principal pentru a începe!"
        } else {
            val builder = StringBuilder()
            for (item in SessionRepository.history) {
                val statusLabel = if (item.completed) {
                    "✅ Finalizată (${item.durationText})"
                } else {
                    "⚠️ Întreruptă după ${item.durationText}"
                }
                builder.append("• [${item.timestamp}]  $statusLabel\n")
            }
            tvHistoryList.text = builder.toString().trimEnd()
        }
    }
}