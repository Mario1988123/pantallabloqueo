package com.mario.pantallabloqueo

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var pinDots: List<View>
    private lateinit var numberButtons: List<View>
    private lateinit var btnDelete: View
    private lateinit var errorText: TextView
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var backgroundImage: ImageView
    private lateinit var secretArea: View
    private lateinit var settingsSecretButton: View

    private var currentPin = ""
    private var correctPin = ""
    private var pinLength = 4
    private val wrongPins = mutableListOf<String>()
    private var settingsTapCount = 0
    private var lastTapTime = 0L
    private var actualTime = ""
    private var showingWrongPin = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var restoreTimeRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("LockScreenPrefs", Context.MODE_PRIVATE)

        initializeViews()
        loadSettings()
        setupNumberPad()
        setupSecretArea()
        setupSettingsButton()
        setupTimeContainer()
        updateTimeAndDate()
    }

    private fun initializeViews() {
        // PIN dots
        pinDots = listOf(
            findViewById(R.id.pinDot1),
            findViewById(R.id.pinDot2),
            findViewById(R.id.pinDot3),
            findViewById(R.id.pinDot4),
            findViewById(R.id.pinDot5),
            findViewById(R.id.pinDot6)
        )

        // Number buttons
        numberButtons = listOf(
            findViewById(R.id.btn0Container),
            findViewById(R.id.btn1Container),
            findViewById(R.id.btn2Container),
            findViewById(R.id.btn3Container),
            findViewById(R.id.btn4Container),
            findViewById(R.id.btn5Container),
            findViewById(R.id.btn6Container),
            findViewById(R.id.btn7Container),
            findViewById(R.id.btn8Container),
            findViewById(R.id.btn9Container)
        )

        btnDelete = findViewById(R.id.btnDeleteContainer)
        errorText = findViewById(R.id.errorText)
        timeText = findViewById(R.id.timeText)
        dateText = findViewById(R.id.dateText)
        backgroundImage = findViewById(R.id.backgroundImage)
        secretArea = findViewById(R.id.secretArea)
        settingsSecretButton = findViewById(R.id.settingsSecretButton)
    }

    private fun loadSettings() {
        correctPin = prefs.getString("correct_pin", "1234") ?: "1234"
        pinLength = prefs.getInt("pin_length", 4)

        // Load background image if exists
        val imagePath = prefs.getString("background_image", null)
        imagePath?.let {
            try {
                val bitmap = BitmapFactory.decodeFile(it)
                backgroundImage.setImageBitmap(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Load wrong PINs history
        val wrongPinsString = prefs.getString("wrong_pins", "")
        if (!wrongPinsString.isNullOrEmpty()) {
            wrongPins.clear()
            wrongPins.addAll(wrongPinsString.split(",").filter { it.isNotEmpty() })
        }

        // Update PIN dots visibility
        updatePinDotsVisibility()
    }

    private fun updatePinDotsVisibility() {
        pinDots.forEachIndexed { index, dot ->
            dot.visibility = if (index < pinLength) View.VISIBLE else View.GONE
        }
    }

    private fun setupNumberPad() {
        numberButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                onNumberPressed(index.toString())
            }
        }

        btnDelete.setOnClickListener {
            onDeletePressed()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSecretArea() {
        var longPressStartTime = 0L

        secretArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressStartTime = System.currentTimeMillis()

                    // Schedule showing the wrong PIN after long press delay
                    handler.postDelayed({
                        if (wrongPins.isNotEmpty() && !showingWrongPin) {
                            showingWrongPin = true
                            val lastWrongPin = wrongPins.last()

                            // Format PIN as time (e.g., "1234" -> "12:34", "6142" -> "61:42")
                            val formattedPin = if (lastWrongPin.length >= 2) {
                                val hours = lastWrongPin.substring(0, lastWrongPin.length / 2)
                                val minutes = lastWrongPin.substring(lastWrongPin.length / 2)
                                "$hours:$minutes"
                            } else {
                                lastWrongPin
                            }

                            timeText.text = formattedPin

                            // Restore actual time after configured duration (if not unlimited)
                            val displayDuration = prefs.getInt("display_duration", 1000)
                            if (displayDuration > 0) {
                                restoreTimeRunnable = Runnable {
                                    timeText.text = actualTime
                                    showingWrongPin = false
                                }
                                handler.postDelayed(restoreTimeRunnable!!, displayDuration.toLong())
                            }
                        }
                    }, 500) // Long press delay
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Cancel long press if released too quickly
                    if (System.currentTimeMillis() - longPressStartTime < 500) {
                        handler.removeCallbacksAndMessages(null)
                    }

                    // If unlimited duration mode and showing wrong PIN, restore time on release
                    val displayDuration = prefs.getInt("display_duration", 1000)
                    if (displayDuration == -1 && showingWrongPin) {
                        restoreTimeRunnable?.let { handler.removeCallbacks(it) }
                        timeText.text = actualTime
                        showingWrongPin = false
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSettingsButton() {
        settingsSecretButton.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < 500) {
                settingsTapCount++
                if (settingsTapCount >= 5) {
                    openSettings()
                    settingsTapCount = 0
                }
            } else {
                settingsTapCount = 1
            }
            lastTapTime = currentTime
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTimeContainer() {
        var longPressStartTime = 0L

        timeText.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressStartTime = System.currentTimeMillis()

                    // Schedule showing the wrong PIN after long press delay
                    handler.postDelayed({
                        if (wrongPins.isNotEmpty() && !showingWrongPin) {
                            showingWrongPin = true
                            val lastWrongPin = wrongPins.last()

                            // Format PIN as time (e.g., "1234" -> "12:34", "6142" -> "61:42")
                            val formattedPin = if (lastWrongPin.length >= 2) {
                                val hours = lastWrongPin.substring(0, lastWrongPin.length / 2)
                                val minutes = lastWrongPin.substring(lastWrongPin.length / 2)
                                "$hours:$minutes"
                            } else {
                                lastWrongPin
                            }

                            timeText.text = formattedPin

                            // Restore actual time after configured duration (if not unlimited)
                            val displayDuration = prefs.getInt("display_duration", 1000)
                            if (displayDuration > 0) {
                                restoreTimeRunnable = Runnable {
                                    timeText.text = actualTime
                                    showingWrongPin = false
                                }
                                handler.postDelayed(restoreTimeRunnable!!, displayDuration.toLong())
                            }
                        }
                    }, 500) // Long press delay
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Cancel long press if released too quickly
                    if (System.currentTimeMillis() - longPressStartTime < 500) {
                        handler.removeCallbacksAndMessages(null)
                    }

                    // If unlimited duration mode and showing wrong PIN, restore time on release
                    val displayDuration = prefs.getInt("display_duration", 1000)
                    if (displayDuration == -1 && showingWrongPin) {
                        restoreTimeRunnable?.let { handler.removeCallbacks(it) }
                        timeText.text = actualTime
                        showingWrongPin = false
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onNumberPressed(number: String) {
        if (currentPin.length < pinLength) {
            currentPin += number
            updatePinDots()
            errorText.visibility = View.GONE

            if (currentPin.length == pinLength) {
                checkPin()
            }
        }
    }

    private fun onDeletePressed() {
        if (currentPin.isNotEmpty()) {
            currentPin = currentPin.dropLast(1)
            updatePinDots()
            errorText.visibility = View.GONE
        }
    }

    private fun updatePinDots() {
        pinDots.forEachIndexed { index, dot ->
            if (index < pinLength) {
                if (index < currentPin.length) {
                    dot.setBackgroundResource(R.drawable.pin_dot_filled)
                } else {
                    dot.setBackgroundResource(R.drawable.pin_dot_empty)
                }
            }
        }
    }

    private fun checkPin() {
        if (currentPin == correctPin) {
            // PIN correcto - cierra la app y muestra el home
            finishAffinity()
        } else {
            // PIN incorrecto
            onWrongPin()
        }
    }

    private fun onWrongPin() {
        // Add to wrong PINs list
        wrongPins.add(currentPin)
        saveWrongPins()

        // Show error
        errorText.visibility = View.VISIBLE

        // Clear PIN after a delay
        currentPin = ""
        android.os.Handler(mainLooper).postDelayed({
            updatePinDots()
        }, 1500)
    }

    private fun highlightWrongButtons() {
        currentPin.forEach { digit ->
            val buttonIndex = digit.toString().toInt()
            numberButtons[buttonIndex].setBackgroundResource(R.drawable.button_background_wrong)
        }
    }

    private fun resetButtonHighlights() {
        numberButtons.forEach { button ->
            button.setBackgroundResource(R.drawable.button_background)
        }
    }

    private fun saveWrongPins() {
        prefs.edit().putString("wrong_pins", wrongPins.joinToString(",")).apply()
    }

    private fun updateTimeAndDate() {
        if (!showingWrongPin) {
            val calendar = Calendar.getInstance()

            // Update time
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            actualTime = timeFormat.format(calendar.time)
            timeText.text = actualTime

            // Update date
            val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))
            dateText.text = dateFormat.format(calendar.time)
        }

        // Update every minute
        android.os.Handler(mainLooper).postDelayed({
            updateTimeAndDate()
        }, 60000)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent back button from closing the app
        // Do nothing - intentionally not calling super
    }
}
