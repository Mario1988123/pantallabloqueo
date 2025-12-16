package com.mario.pantallabloqueo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var selectBackgroundButton: Button
    private lateinit var backgroundPreview: ImageView
    private lateinit var lockTypeGroup: RadioGroup
    private lateinit var pin4Digits: RadioButton
    private lateinit var pin6Digits: RadioButton
    private lateinit var patternLock: RadioButton
    private lateinit var pinConfigContainer: LinearLayout
    private lateinit var patternConfigContainer: LinearLayout
    private lateinit var pinInput: EditText
    private lateinit var confirmPinInput: EditText
    private lateinit var patternInput: PatternView
    private lateinit var confirmPatternInput: PatternView
    private lateinit var clearPatternButton: Button
    private lateinit var clearConfirmPatternButton: Button
    private lateinit var wrongPinsHistory: TextView
    private lateinit var clearWrongPinsButton: Button
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button

    private var selectedImageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 1

    private var currentPattern = ""
    private var confirmPattern = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("LockScreenPrefs", Context.MODE_PRIVATE)

        initializeViews()
        loadCurrentSettings()
        setupListeners()
    }

    private fun initializeViews() {
        selectBackgroundButton = findViewById(R.id.selectBackgroundButton)
        backgroundPreview = findViewById(R.id.backgroundPreview)
        lockTypeGroup = findViewById(R.id.lockTypeGroup)
        pin4Digits = findViewById(R.id.pin4Digits)
        pin6Digits = findViewById(R.id.pin6Digits)
        patternLock = findViewById(R.id.patternLock)
        pinConfigContainer = findViewById(R.id.pinConfigContainer)
        patternConfigContainer = findViewById(R.id.patternConfigContainer)
        pinInput = findViewById(R.id.pinInput)
        confirmPinInput = findViewById(R.id.confirmPinInput)
        patternInput = findViewById(R.id.patternInput)
        confirmPatternInput = findViewById(R.id.confirmPatternInput)
        clearPatternButton = findViewById(R.id.clearPatternButton)
        clearConfirmPatternButton = findViewById(R.id.clearConfirmPatternButton)
        wrongPinsHistory = findViewById(R.id.wrongPinsHistory)
        clearWrongPinsButton = findViewById(R.id.clearWrongPinsButton)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
    }

    private fun loadCurrentSettings() {
        // Load lock type
        val currentLockType = prefs.getString("lock_type", MainActivity.LOCK_TYPE_PIN4)
        when (currentLockType) {
            MainActivity.LOCK_TYPE_PIN4 -> pin4Digits.isChecked = true
            MainActivity.LOCK_TYPE_PIN6 -> pin6Digits.isChecked = true
            MainActivity.LOCK_TYPE_PATTERN -> patternLock.isChecked = true
        }

        // Update UI visibility
        updateConfigVisibility()

        // Update max length for PIN inputs
        updatePinInputMaxLength()

        // Load background image
        val imagePath = prefs.getString("background_image", null)
        imagePath?.let {
            try {
                val bitmap = BitmapFactory.decodeFile(it)
                backgroundPreview.setImageBitmap(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Load wrong PINs history
        loadWrongPinsHistory()
    }

    private fun loadWrongPinsHistory() {
        val wrongPinsString = prefs.getString("wrong_pins", "")
        if (wrongPinsString.isNullOrEmpty()) {
            wrongPinsHistory.text = getString(R.string.no_wrong_pins)
        } else {
            val wrongPins = wrongPinsString.split(",").filter { it.isNotEmpty() }
            wrongPinsHistory.text = wrongPins.takeLast(20).joinToString("\n")
        }
    }

    private fun setupListeners() {
        selectBackgroundButton.setOnClickListener {
            openImagePicker()
        }

        lockTypeGroup.setOnCheckedChangeListener { _, _ ->
            updateConfigVisibility()
            updatePinInputMaxLength()
            pinInput.text.clear()
            confirmPinInput.text.clear()
            currentPattern = ""
            confirmPattern = ""
            patternInput.clearPattern()
            confirmPatternInput.clearPattern()
        }

        // Pattern inputs
        patternInput.onPatternCompleted = { pattern ->
            currentPattern = pattern
        }

        confirmPatternInput.onPatternCompleted = { pattern ->
            confirmPattern = pattern
        }

        clearPatternButton.setOnClickListener {
            currentPattern = ""
            patternInput.clearPattern()
        }

        clearConfirmPatternButton.setOnClickListener {
            confirmPattern = ""
            confirmPatternInput.clearPattern()
        }

        clearWrongPinsButton.setOnClickListener {
            clearWrongPins()
        }

        saveButton.setOnClickListener {
            saveSettings()
        }

        cancelButton.setOnClickListener {
            goBackToLockScreen()
        }
    }

    private fun updateConfigVisibility() {
        if (patternLock.isChecked) {
            pinConfigContainer.visibility = View.GONE
            patternConfigContainer.visibility = View.VISIBLE
        } else {
            pinConfigContainer.visibility = View.VISIBLE
            patternConfigContainer.visibility = View.GONE
        }
    }

    private fun updatePinInputMaxLength() {
        val maxLength = if (pin4Digits.isChecked) 4 else 6
        pinInput.filters = arrayOf(android.text.InputFilter.LengthFilter(maxLength))
        confirmPinInput.filters = arrayOf(android.text.InputFilter.LengthFilter(maxLength))
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.data
            selectedImageUri?.let {
                backgroundPreview.setImageURI(it)
            }
        }
    }

    private fun clearWrongPins() {
        prefs.edit().putString("wrong_pins", "").apply()
        wrongPinsHistory.text = getString(R.string.no_wrong_pins)
        Toast.makeText(this, getString(R.string.clear_wrong_pins), Toast.LENGTH_SHORT).show()
    }

    private fun saveSettings() {
        val lockType = when {
            pin4Digits.isChecked -> MainActivity.LOCK_TYPE_PIN4
            pin6Digits.isChecked -> MainActivity.LOCK_TYPE_PIN6
            patternLock.isChecked -> MainActivity.LOCK_TYPE_PATTERN
            else -> MainActivity.LOCK_TYPE_PIN4
        }

        // Validate based on lock type
        if (lockType == MainActivity.LOCK_TYPE_PATTERN) {
            // Validate pattern
            if (currentPattern.isNotEmpty() || confirmPattern.isNotEmpty()) {
                if (currentPattern.length < 4) {
                    Toast.makeText(this, getString(R.string.pattern_too_short), Toast.LENGTH_SHORT).show()
                    return
                }
                if (currentPattern != confirmPattern) {
                    Toast.makeText(this, getString(R.string.patterns_dont_match), Toast.LENGTH_SHORT).show()
                    return
                }
            }
        } else {
            // Validate PIN
            val pin = pinInput.text.toString()
            val confirmPin = confirmPinInput.text.toString()
            val pinLength = if (pin4Digits.isChecked) 4 else 6

            if (pin.isNotEmpty()) {
                if (pin.length != pinLength) {
                    Toast.makeText(
                        this,
                        "El PIN debe tener $pinLength dígitos",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                if (pin != confirmPin) {
                    Toast.makeText(this, getString(R.string.pins_dont_match), Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        // Save settings
        val editor = prefs.edit()

        // Save lock type
        editor.putString("lock_type", lockType)

        // Save PIN or Pattern based on type
        if (lockType == MainActivity.LOCK_TYPE_PATTERN) {
            if (currentPattern.isNotEmpty()) {
                editor.putString("correct_pattern", currentPattern)
            }
        } else {
            val pin = pinInput.text.toString()
            if (pin.isNotEmpty()) {
                editor.putString("correct_pin", pin)
            }
        }

        // Save background image if selected
        selectedImageUri?.let { uri ->
            val imagePath = saveImageToInternalStorage(uri)
            imagePath?.let {
                editor.putString("background_image", it)
            }
        }

        editor.apply()

        Toast.makeText(this, getString(R.string.pin_saved), Toast.LENGTH_SHORT).show()

        // Return to lock screen
        goBackToLockScreen()
    }

    private fun saveImageToInternalStorage(uri: Uri): String? {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, "lock_screen_background.jpg")
            val outputStream = FileOutputStream(file)

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al guardar la imagen", Toast.LENGTH_SHORT).show()
            return null
        }
    }

    private fun goBackToLockScreen() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goBackToLockScreen()
    }
}
