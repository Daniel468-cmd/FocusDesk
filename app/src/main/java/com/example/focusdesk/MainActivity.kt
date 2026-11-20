package com.example.focusdesk

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener {

    // Componente UI
    private lateinit var btnDndPermission: Button
    private lateinit var btnStopAlarm: Button
    private lateinit var btnSetDuration: Button
    private lateinit var btnSelectSound: Button
    private lateinit var btnOpenStats: Button
    private lateinit var tvFocusStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvSelectedSound: TextView
    private lateinit var tvHintText: TextView
    private lateinit var tvLuxValue: TextView
    private lateinit var tvLightStatus: TextView

    // Senzori și servicii sistem
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private lateinit var notificationManager: NotificationManager

    // Variabile de stare (Senzori & Timer cu Pauză)
    private var isPhoneFaceDown = false
    private var isLowLight: Boolean? = null
    private var isTimerRunning = false
    private var isSessionPaused = false
    private var countDownTimer: CountDownTimer? = null
    private var activeRingtone: Ringtone? = null

    // Durata configurată și timpul rămas (pentru reluare după pauză)
    private var focusDurationMs: Long = 10 * 1000L // Implicit: 10 secunde
    private var remainingTimeMs: Long = focusDurationMs
    private var selectedSoundUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    // Constante configurabile
    private val LOW_LIGHT_THRESHOLD = 50f
    private val CHANNEL_ID = "focusdesk_alerts_channel"

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }

            selectedSoundUri = uri
            if (uri != null) {
                val ringtone = RingtoneManager.getRingtone(this, uri)
                val title = ringtone?.getTitle(this) ?: "Sunet personalizat"
                tvSelectedSound.text = "Sunet finalizare: $title"
            } else {
                tvSelectedSound.text = "Sunet finalizare: Fără sunet (Doar vibrație)"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inițializare elemente UI
        btnDndPermission = findViewById(R.id.btnDndPermission)
        btnStopAlarm = findViewById(R.id.btnStopAlarm)
        btnSetDuration = findViewById(R.id.btnSetDuration)
        btnSelectSound = findViewById(R.id.btnSelectSound)
        btnOpenStats = findViewById(R.id.btnOpenStats)
        tvFocusStatus = findViewById(R.id.tvFocusStatus)
        tvTimer = findViewById(R.id.tvTimer)
        tvSelectedSound = findViewById(R.id.tvSelectedSound)
        tvHintText = findViewById(R.id.tvHintText)
        tvLuxValue = findViewById(R.id.tvLuxValue)
        tvLightStatus = findViewById(R.id.tvLightStatus)

        remainingTimeMs = focusDurationMs
        tvTimer.text = formatTime(remainingTimeMs)

        // Inițializare servicii sistem și senzori
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        createNotificationChannel()
        requestNotificationPermissionIfNeeded()

        btnDndPermission.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
        }

        btnStopAlarm.setOnClickListener {
            stopCompletionSound()
        }

        btnSetDuration.setOnClickListener { showDurationPickerDialog() }
        tvTimer.setOnClickListener { showDurationPickerDialog() }

        btnSelectSound.setOnClickListener { openSoundPicker() }

        // Deschidere Ecran 2 (Statistici & Istoric)
        btnOpenStats.setOnClickListener {
            val intent = Intent(this, StatsActivity::class.java)
            startActivity(intent)
        }

        if (proximitySensor == null) {
            tvFocusStatus.text = "Senzor proximitate indisponibil"
        }
        if (lightSensor == null) {
            tvLightStatus.text = "Senzor lumină indisponibil"
        }
    }

    override fun onResume() {
        super.onResume()
        updateDndButtonState()
        updateStatsButtonText()

        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        stopCompletionSound()
        countDownTimer?.cancel()
        super.onDestroy()
    }

    private fun updateStatsButtonText() {
        btnOpenStats.text = "📊 Statistici & Istoric (${SessionRepository.completedCount} sesiuni)"
    }

    // --- DIALOG ALEGERE DURATĂ ---

    private fun showDurationPickerDialog() {
        if (isTimerRunning) {
            Toast.makeText(this, "Așteaptă finalizarea sesiunii active!", Toast.LENGTH_SHORT).show()
            return
        }

        val currentTotalSeconds = (focusDurationMs / 1000).toInt()
        val currentMinutes = currentTotalSeconds / 60
        val currentSeconds = currentTotalSeconds % 60

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 16)
        }

        val minutePicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 120
            value = currentMinutes
        }

        val minLabel = TextView(this).apply {
            text = " min   "
            textSize = 16f
        }

        val secondPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 59
            value = currentSeconds
        }

        val secLabel = TextView(this).apply {
            text = " sec"
            textSize = 16f
        }

        container.addView(minutePicker)
        container.addView(minLabel)
        container.addView(secondPicker)
        container.addView(secLabel)

        AlertDialog.Builder(this)
            .setTitle("Setează durata sesiunii de focus")
            .setMessage("Alege durata dorită (minute și secunde):")
            .setView(container)
            .setPositiveButton("Salvează") { _, _ ->
                val totalSeconds = (minutePicker.value * 60) + secondPicker.value
                if (totalSeconds < 3) {
                    Toast.makeText(this, "Durata minimă este de 3 secunde.", Toast.LENGTH_SHORT).show()
                } else {
                    focusDurationMs = totalSeconds * 1000L
                    remainingTimeMs = focusDurationMs
                    isSessionPaused = false
                    tvTimer.text = formatTime(focusDurationMs)
                    tvFocusStatus.text = "MOD ACTIV (Telefon ridicat)"
                    tvFocusStatus.setTextColor(Color.parseColor("#0284C7"))
                }
            }
            .setNegativeButton("Anulează", null)
            .show()
    }

    // --- CONTROL SUNET FINALIZARE ---

    private fun openSoundPicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alege sunetul de finalizare")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedSoundUri)
        }
        ringtonePickerLauncher.launch(intent)
    }

    private fun playCompletionSound() {
        stopCompletionSound()
        val uri = selectedSoundUri ?: return
        try {
            activeRingtone = RingtoneManager.getRingtone(applicationContext, uri)
            activeRingtone?.play()
            btnStopAlarm.visibility = View.VISIBLE
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopCompletionSound() {
        try {
            if (activeRingtone?.isPlaying == true) {
                activeRingtone?.stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            activeRingtone = null
            btnStopAlarm.visibility = View.GONE
        }
    }

    // --- SENZORI ---

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = proximitySensor?.maximumRange ?: 5f

                if (distance < maxRange) {
                    if (!isPhoneFaceDown) {
                        isPhoneFaceDown = true
                        startOrResumeFocusMode()
                    }
                } else {
                    if (isPhoneFaceDown) {
                        isPhoneFaceDown = false
                        handlePhoneLifted()
                    }
                }
            }

            Sensor.TYPE_LIGHT -> {
                val lux = event.values[0]
                tvLuxValue.text = String.format(Locale.getDefault(), "%.0f lux", lux)

                if (isPhoneFaceDown) {
                    isLowLight = null
                    tvLightStatus.text = "Monitorizare lumină în pauză (Telefon cu fața în jos)"
                    tvLightStatus.setTextColor(Color.parseColor("#64748B"))
                    return
                }

                val currentlyLow = lux < LOW_LIGHT_THRESHOLD
                if (currentlyLow) {
                    if (isLowLight != true) {
                        isLowLight = true
                        tvLightStatus.text = "⚠️ Lumină insuficientă! Aprinde o lampă."
                        tvLightStatus.setTextColor(Color.parseColor("#DC2626"))
                        vibratePhone(300)
                        sendSystemNotification(
                            notificationId = 1001,
                            title = "FocusDesk: Lumină insuficientă!",
                            message = "Este prea întuneric pentru citit/lucru. Aprinde o lampă."
                        )
                    }
                } else {
                    if (isLowLight != false) {
                        isLowLight = false
                        tvLightStatus.text = "✅ Lumină optimă pentru lucru/studiu"
                        tvLightStatus.setTextColor(Color.parseColor("#16A34A"))
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- LOGICĂ MOD FOCUS CU PAUZĂ LA RIDICARE PREMATURĂ ---

    private fun startOrResumeFocusMode() {
        stopCompletionSound()
        tvFocusStatus.text = if (isSessionPaused) {
            "MOD FOCUS RELUAT (Nu deranja)"
        } else {
            "MOD FOCUS ACTIV (Nu deranja)"
        }
        tvFocusStatus.setTextColor(Color.parseColor("#16A34A"))
        tvHintText.text = "Sesiune în desfășurare. Nu ridica telefonul până la expirarea timpului!"
        setDoNotDisturb(true)

        if (!isTimerRunning) {
            // Dacă pornim o sesiune nouă (nu reluăm una în pauză), folosim durata completă
            if (!isSessionPaused) {
                remainingTimeMs = focusDurationMs
            }
            isSessionPaused = false

            countDownTimer = object : CountDownTimer(remainingTimeMs, 250) {
                override fun onTick(millisUntilFinished: Long) {
                    remainingTimeMs = millisUntilFinished
                    tvTimer.text = formatTime(millisUntilFinished)
                }

                override fun onFinish() {
                    isTimerRunning = false
                    isSessionPaused = false
                    remainingTimeMs = focusDurationMs
                    tvTimer.text = "00:00"
                    tvFocusStatus.text = "🎉 Sesiune finalizată! (DND dezactivat)"
                    tvHintText.text = "Felicitări! Ridică telefonul pentru a reseta cronometrul."

                    // 1. Dezactivăm DND
                    setDoNotDisturb(false)

                    // 2. Salvăm sesiunea finalizată în istoric
                    SessionRepository.addSession(focusDurationMs, completed = true)
                    updateStatsButtonText()

                    // 3. Feedback auditiv și tactil
                    vibratePhone(500)
                    playCompletionSound()
                }
            }.start()
            isTimerRunning = true
        }
    }

    private fun handlePhoneLifted() {
        stopCompletionSound()
        setDoNotDisturb(false)

        if (isTimerRunning) {
            // Cazul 1: Utilizatorul a ridicat telefonul ÎNAINTE de expirarea timpului!
            countDownTimer?.cancel()
            isTimerRunning = false
            isSessionPaused = true

            // Înghețăm cronometrul la timpul rămas și avertizăm utilizatorul
            tvTimer.text = formatTime(remainingTimeMs)
            tvFocusStatus.text = "⚠️ PAUZĂ! Ai ridicat telefonul prea devreme!"
            tvFocusStatus.setTextColor(Color.parseColor("#DC2626"))
            tvHintText.text = "Pune telefonul din nou cu ecranul în jos pentru a continua de la ${formatTime(remainingTimeMs)}."

            // Înregistrăm întreruperea în statistici
            SessionRepository.addSession(focusDurationMs - remainingTimeMs, completed = false)

            // Vibrație dublă de avertisment + notificare Warning
            vibrateWarningPattern()
            sendSystemNotification(
                notificationId = 1002,
                title = "⚠️ FocusDesk: Avertisment întrerupere!",
                message = "Ai ridicat telefonul înainte de finalul sesiunii! Pune-l din nou jos pentru a continua."
            )
        } else {
            // Cazul 2: Sesiunea era deja terminată (sau neîncepută), resetăm curat
            isSessionPaused = false
            remainingTimeMs = focusDurationMs
            tvTimer.text = formatTime(focusDurationMs)
            tvFocusStatus.text = "MOD ACTIV (Telefon ridicat)"
            tvFocusStatus.setTextColor(Color.parseColor("#0284C7"))
            tvHintText.text = "Așază telefonul cu ecranul în jos pe birou pentru a porni sesiunea de focus."
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = (millis + 500) / 1000 // Rotunjire curată la secunda întreagă
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun setDoNotDisturb(enable: Boolean) {
        if (notificationManager.isNotificationPolicyAccessGranted) {
            val filter = if (enable) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            notificationManager.setInterruptionFilter(filter)
        }
    }

    private fun updateDndButtonState() {
        if (notificationManager.isNotificationPolicyAccessGranted) {
            btnDndPermission.text = "✓ Permisiune Do Not Disturb activă"
            btnDndPermission.setBackgroundColor(Color.parseColor("#16A34A"))
        } else {
            btnDndPermission.text = "Acordă permisiune Do Not Disturb"
            btnDndPermission.setBackgroundColor(Color.parseColor("#EF4444"))
        }
    }

    // --- VIBRAȚII & NOTIFICĂRI ---

    private fun vibratePhone(durationMs: Long) {
        val vibrator = getVibrator()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun vibrateWarningPattern() {
        val vibrator = getVibrator()
        // Pattern: 0ms așteptare, 250ms vibrație, 150ms pauză, 250ms vibrație
        val pattern = longArrayOf(0, 250, 150, 250)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alerte FocusDesk",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avertizări pentru lumină slabă și ridicarea prematură a telefonului"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendSystemNotification(notificationId: Int, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}