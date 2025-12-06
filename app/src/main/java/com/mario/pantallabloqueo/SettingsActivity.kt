package com.mario.pantallabloqueo

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var selectBackgroundButton: Button
    private lateinit var backgroundPreview: ImageView
    private lateinit var pinLengthGroup: RadioGroup
    private lateinit var pin4Digits: RadioButton
    private lateinit var pin6Digits: RadioButton
    private lateinit var pinInput: EditText
    private lateinit var confirmPinInput: EditText
    private lateinit var wrongPinsHistory: TextView
    private lateinit var clearWrongPinsButton: Button
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button

    private var selectedImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            selectedImageUri = result.data?.data
            selectedImageUri?.let {
                backgroundPreview.setImageURI(it)
            }
        }
    }

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
        pinLengthGroup = findViewById(R.id.pinLengthGroup)
        pin4Digits = findViewById(R.id.pin4Digits)
        pin6Digits = findViewById(R.id.pin6Digits)
        pinInput = findViewById(R.id.pinInput)
        confirmPinInput = findViewById(R.id.confirmPinInput)
        wrongPinsHistory = findViewById(R.id.wrongPinsHistory)
        clearWrongPinsButton = findViewById(R.id.clearWrongPinsButton)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
    }

    private fun loadCurrentSettings() {
        // Load PIN length
        val currentPinLength = prefs.getInt("pin_length", 4)
        if (currentPinLength == 4) {
            pin4Digits.isChecked = true
        } else {
            pin6Digits.isChecked = true
        }

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

        pinLengthGroup.setOnCheckedChangeListener { _, _ ->
            updatePinInputMaxLength()
            pinInput.text.clear()
            confirmPinInput.text.clear()
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

    private fun updatePinInputMaxLength() {
        val maxLength = if (pin4Digits.isChecked) 4 else 6
        pinInput.filters = arrayOf(android.text.InputFilter.LengthFilter(maxLength))
        confirmPinInput.filters = arrayOf(android.text.InputFilter.LengthFilter(maxLength))
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun clearWrongPins() {
        prefs.edit().putString("wrong_pins", "").apply()
        wrongPinsHistory.text = getString(R.string.no_wrong_pins)
        Toast.makeText(this, getString(R.string.clear_wrong_pins), Toast.LENGTH_SHORT).show()
    }

    private fun saveSettings() {
        val pin = pinInput.text.toString()
        val confirmPin = confirmPinInput.text.toString()
        val pinLength = if (pin4Digits.isChecked) 4 else 6

        // Validate PIN
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

        // Save settings
        val editor = prefs.edit()

        // Save PIN if provided
        if (pin.isNotEmpty()) {
            editor.putString("correct_pin", pin)
        }

        // Save PIN length
        editor.putInt("pin_length", pinLength)

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
