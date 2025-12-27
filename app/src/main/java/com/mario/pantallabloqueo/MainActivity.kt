package com.mario.pantallabloqueo

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        const val LOCK_TYPE_PIN4 = "pin4"
        const val LOCK_TYPE_PIN6 = "pin6"
        const val LOCK_TYPE_PATTERN = "pattern"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var pinDots: List<View>
    private lateinit var numberButtons: List<Button>
    private lateinit var btnDelete: Button
    private lateinit var errorText: TextView
    private lateinit var patternErrorText: TextView
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var backgroundImage: ImageView
    private lateinit var secretArea: View
    private lateinit var wrongPinsText: TextView
    private lateinit var settingsSecretButton: View
    private lateinit var patternView: PatternView
    private lateinit var numberPad: GridLayout
    private lateinit var pinInputContainer: LinearLayout
    private lateinit var timeContainer: LinearLayout

    private var currentPin = ""
    private var correctPin = ""
    private var correctPattern = ""
    private var currentLockType = LOCK_TYPE_PIN4
    private var pinLength = 4
    private val wrongPins = mutableListOf<String>()
    private var settingsTapCount = 0
    private var lastTapTime = 0L

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("LockScreenPrefs", Context.MODE_PRIVATE)

        initializeViews()
        loadSettings()
        setupNumberPad()
        setupSecretArea()
        setupSettingsButton()
        setupQuickModeSwitch()
        setupPatternView()
        updateTimeAndDate()
        updateUIForCurrentMode()
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
            findViewById(R.id.btn0),
            findViewById(R.id.btn1),
            findViewById(R.id.btn2),
            findViewById(R.id.btn3),
            findViewById(R.id.btn4),
            findViewById(R.id.btn5),
            findViewById(R.id.btn6),
            findViewById(R.id.btn7),
            findViewById(R.id.btn8),
            findViewById(R.id.btn9)
        )

        btnDelete = findViewById(R.id.btnDelete)
        errorText = findViewById(R.id.errorText)
        patternErrorText = findViewById(R.id.patternErrorText)
        timeText = findViewById(R.id.timeText)
        dateText = findViewById(R.id.dateText)
        backgroundImage = findViewById(R.id.backgroundImage)
        secretArea = findViewById(R.id.secretArea)
        wrongPinsText = findViewById(R.id.wrongPinsText)
        settingsSecretButton = findViewById(R.id.settingsSecretButton)
        patternView = findViewById(R.id.patternView)
        numberPad = findViewById(R.id.numberPad)
        pinInputContainer = findViewById(R.id.pinInputContainer)
        timeContainer = findViewById(R.id.timeContainer)
    }

    private fun loadSettings() {
        correctPin = prefs.getString("correct_pin", "1234") ?: "1234"
        correctPattern = prefs.getString("correct_pattern", "") ?: ""
        currentLockType = prefs.getString("lock_type", LOCK_TYPE_PIN4) ?: LOCK_TYPE_PIN4

        pinLength = when (currentLockType) {
            LOCK_TYPE_PIN4 -> 4
            LOCK_TYPE_PIN6 -> 6
            else -> 4
        }

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
    }

    private fun setupQuickModeSwitch() {
        // LONG PRESS on time/date area to quickly switch modes
        timeContainer.setOnLongClickListener {
            switchToNextMode()
            true
        }
    }

    private fun switchToNextMode() {
        // Ciclo: PIN4 -> PIN6 -> Patrón -> PIN4
        val newLockType = when (currentLockType) {
            LOCK_TYPE_PIN4 -> LOCK_TYPE_PIN6
            LOCK_TYPE_PIN6 -> LOCK_TYPE_PATTERN
            LOCK_TYPE_PATTERN -> LOCK_TYPE_PIN4
            else -> LOCK_TYPE_PIN4
        }

        currentLockType = newLockType
        pinLength = when (currentLockType) {
            LOCK_TYPE_PIN4 -> 4
            LOCK_TYPE_PIN6 -> 6
            else -> 4
        }

        // Save the new mode
        prefs.edit().putString("lock_type", currentLockType).apply()

        // Reset current input
        currentPin = ""
        patternView.clearPattern()

        // Update UI
        updateUIForCurrentMode()
    }

    private fun updateUIForCurrentMode() {
        when (currentLockType) {
            LOCK_TYPE_PIN4, LOCK_TYPE_PIN6 -> {
                // Show PIN mode
                pinInputContainer.visibility = View.VISIBLE
                numberPad.visibility = View.VISIBLE
                patternView.visibility = View.GONE
                patternErrorText.visibility = View.GONE

                // Update dots visibility
                updatePinDotsVisibility()
                updatePinDots()
            }
            LOCK_TYPE_PATTERN -> {
                // Show pattern mode
                pinInputContainer.visibility = View.GONE
                numberPad.visibility = View.GONE
                patternView.visibility = View.VISIBLE
                errorText.visibility = View.GONE
            }
        }
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

    private fun setupPatternView() {
        patternView.onPatternCompleted = { pattern ->
            checkPattern(pattern)
        }
    }

    private fun setupSecretArea() {
        secretArea.setOnClickListener {
            // Toggle wrong PINs display
            if (wrongPinsText.visibility == View.VISIBLE) {
                wrongPinsText.visibility = View.GONE
            } else {
                showWrongPins()
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
            resetButtonHighlights()
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
        // PIN dinámico basado en hora/fecha actual
        val dynamicPin = getDynamicPin()

        if (currentPin == dynamicPin) {
            // PIN correcto - cierra la app y muestra el home
            finishAffinity()
        } else {
            // PIN incorrecto
            onWrongPin()
        }
    }

    private fun getDynamicPin(): String {
        val calendar = Calendar.getInstance()
        val hour = String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY))
        val minute = String.format("%02d", calendar.get(Calendar.MINUTE))
        val day = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))

        return when (currentLockType) {
            LOCK_TYPE_PIN4 -> "$hour$minute"  // Ej: 14:35 -> "1435"
            LOCK_TYPE_PIN6 -> "$hour$minute$day"  // Ej: 14:35 día 27 -> "143527"
            else -> "$hour$minute"
        }
    }

    private fun checkPattern(pattern: String) {
        if (pattern == correctPattern) {
            // Patrón correcto
            finishAffinity()
        } else {
            // Patrón incorrecto
            onWrongPattern()
        }
    }

    private fun onWrongPin() {
        // Add to wrong PINs list
        wrongPins.add(currentPin)
        saveWrongPins()

        // Show error
        errorText.visibility = View.VISIBLE

        // Highlight wrong buttons
        highlightWrongButtons()

        // Clear PIN after a delay
        val wrongPin = currentPin
        currentPin = ""
        handler.postDelayed({
            updatePinDots()
            resetButtonHighlights()
        }, 1500)
    }

    private fun onWrongPattern() {
        // Add to wrong patterns list
        val patternStr = patternView.getSelectedPattern()
        if (patternStr.isNotEmpty()) {
            wrongPins.add("P:$patternStr")
            saveWrongPins()
        }

        // Show error
        patternErrorText.visibility = View.VISIBLE
        patternView.showWrongPattern()

        // Hide error after delay
        handler.postDelayed({
            patternErrorText.visibility = View.GONE
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

    private fun showWrongPins() {
        if (wrongPins.isEmpty()) {
            wrongPinsText.text = getString(R.string.no_wrong_pins)
        } else {
            wrongPinsText.text = "Códigos incorrectos:\n" + wrongPins.takeLast(10).joinToString("\n")
        }
        wrongPinsText.visibility = View.VISIBLE
    }

    private fun saveWrongPins() {
        prefs.edit().putString("wrong_pins", wrongPins.joinToString(",")).apply()
    }

    private fun updateTimeAndDate() {
        val calendar = Calendar.getInstance()

        // Update time
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        timeText.text = timeFormat.format(calendar.time)

        // Update date
        val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))
        dateText.text = dateFormat.format(calendar.time)

        // Update every minute
        handler.postDelayed({
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
        // Do nothing
    }
}
