package com.mario.pantallabloqueo

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
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
    private lateinit var numberButtons: List<Button>
    private lateinit var btnDelete: Button
    private lateinit var errorText: TextView
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var backgroundImage: ImageView
    private lateinit var secretArea: View
    private lateinit var wrongPinsText: TextView
    private lateinit var settingsSecretButton: View

    private var currentPin = ""
    private var correctPin = ""
    private var pinLength = 4
    private val wrongPins = mutableListOf<String>()
    private var settingsTapCount = 0
    private var lastTapTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("LockScreenPrefs", Context.MODE_PRIVATE)

        initializeViews()
        loadSettings()
        setupNumberPad()
        setupSecretArea()
        setupSettingsButton()
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
        timeText = findViewById(R.id.timeText)
        dateText = findViewById(R.id.dateText)
        backgroundImage = findViewById(R.id.backgroundImage)
        secretArea = findViewById(R.id.secretArea)
        wrongPinsText = findViewById(R.id.wrongPinsText)
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

        // Highlight wrong buttons
        highlightWrongButtons()

        // Clear PIN after a delay
        currentPin = ""
        android.os.Handler(mainLooper).postDelayed({
            updatePinDots()
            resetButtonHighlights()
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
            wrongPinsText.text = "PINes incorrectos:\n" + wrongPins.takeLast(10).joinToString("\n")
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
        android.os.Handler(mainLooper).postDelayed({
            updateTimeAndDate()
        }, 60000)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        // Prevent back button from closing the app
        // Do nothing
    }
}
