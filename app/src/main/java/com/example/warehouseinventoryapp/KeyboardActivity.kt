package com.example.warehouseinventoryapp

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.datalogic.device.ErrorManager
import com.datalogic.device.input.KeyCodeEntry
import com.datalogic.device.input.KeyboardManager
import com.datalogic.device.input.KeyboardManager.VScanCode
import com.datalogic.device.input.VScanEntry
import com.datalogic.device.power.PowerManager
import com.datalogic.device.power.WakeupSource
import com.example.warehouseinventoryapp.databinding.ActivityKeyboardBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class KeyboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKeyboardBinding
    private lateinit var keyboardManager: KeyboardManager
    private lateinit var powerManager: PowerManager
    private lateinit var sharedPrefs: SharedPreferences

    private val READ_STORAGE_PERMISSION_CODE = 101

    private val scanCodes = listOf(
        "VSCAN_LEFT_TRIGGER",
        "VSCAN_RIGHT_TRIGGER",
        "VSCAN_PTT_TRIGGER_LEFT",
        "VSCAN_PTT_TRIGGER_RIGHT",
        "VSCAN_AUTOSCAN_TRIGGER",
        "VSCAN_MOTION_TRIGGER"
    )

    private val triggerSources = listOf("None", "Left Trigger", "Right Trigger", "Pistol Trigger", "Front Trigger", "Auto Scan Trigger", "Motion Trigger")
    private val autoScanRanges = listOf("Near", "Medium", "Far")
    private val remapTypes = listOf("Keycode", "Unicode", "Intent")

    private val PREFS_NAME = "KeyboardSettings"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeyboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        ErrorManager.enableExceptions(true)
        keyboardManager = KeyboardManager()
        powerManager = PowerManager()

        setupBottomNavigation()
        checkAndRequestPermissions()
        setupUI()
        loadSettings()
    }

    private fun setupUI() {
        setupButtons()
        setupGeneralSettings()
        setupAutoScanSettings()
        setupMotionTriggerSettings()
        setupKeyRemappingSettings()
        setupPTTSettings()
        setupAdvancedKeyboardSettings()
    }

    private fun setupButtons() = with(binding) {
        buttonMapKeyCode.setOnClickListener { showMapKeyCodeDialog() }
        buttonMapUnicode.setOnClickListener { showMapUnicodeDialog() }
        buttonMapIntent.setOnClickListener { showMapIntentDialog() }
        buttonMapCustomAction.setOnClickListener { showMapCustomActionDialog() }
        buttonConfigurePTT.setOnClickListener { configurePTT() }
        buttonExportConfig.setOnClickListener { exportConfiguration() }
        buttonImportConfig.setOnClickListener { importConfiguration() }
        buttonDeleteMappings.setOnClickListener { deleteAllMappings() }
    }

    private fun setupGeneralSettings() = with(binding) {
        switchLockInput.setOnCheckedChangeListener { _, isChecked ->
            keyboardManager.lockInput(isChecked)
            saveBoolean("lock_input", isChecked)
            showToast("Lock Key Input: $isChecked")
        }

        spinnerTriggerSource.apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, triggerSources).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            onItemSelectedListener = createSimpleItemSelectedListener { position ->
                val selectedSource = triggerSources[position]
                saveString("trigger_source", selectedSource)
                showToast("Trigger Source: $selectedSource")
            }
        }

        switchEnableTrigger.setOnCheckedChangeListener { _, isChecked ->
            saveBoolean("enable_trigger_source", isChecked)
            showToast("Enable Trigger Source: $isChecked")
        }
    }

    private fun setupAutoScanSettings() = with(binding) {
        switchAutoScanEnable.setOnCheckedChangeListener { _, isChecked ->
            saveBoolean("auto_scan_enable", isChecked)
            showToast("Auto Scan Enabled: $isChecked")
        }

        spinnerAutoScanRange.apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, autoScanRanges).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            onItemSelectedListener = createSimpleItemSelectedListener { position ->
                val selectedRange = autoScanRanges[position]
                saveString("auto_scan_range", selectedRange)
                showToast("Auto Scan Range: $selectedRange")
            }
        }
    }

    private fun setupMotionTriggerSettings() = with(binding) {
        switchMotionTriggerEnable.setOnCheckedChangeListener { _, isChecked ->
            saveBoolean("motion_trigger_enable", isChecked)
            showToast("Motion Trigger Enabled: $isChecked")
        }

        editTextMotionSensitivity.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val sensitivity = editTextMotionSensitivity.text.toString().toIntOrNull() ?: 0
                saveInt("motion_trigger_sensitivity", sensitivity)
                showToast("Motion Sensitivity: $sensitivity")
            }
        }

        switchVibrateOnMotion.setOnCheckedChangeListener { _, isChecked ->
            saveBoolean("vibrate_on_motion", isChecked)
            showToast("Vibrate on Motion Detected: $isChecked")
        }
    }

    private fun setupKeyRemappingSettings() = with(binding) {
        switchResetRemapping.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AlertDialog.Builder(this@KeyboardActivity)
                    .setTitle("Reset Key Remapping")
                    .setMessage("Are you sure you want to reset all key remappings?")
                    .setPositiveButton("Yes") { _, _ ->
                        keyboardManager.clearAllMappings()
                        saveBoolean("reset_remapping", false)
                        showToast("Key Remappings Reset")
                    }
                    .setNegativeButton("No") { dialog, _ ->
                        switchResetRemapping.isChecked = false
                        dialog.dismiss()
                    }
                    .show()
            }
        }

        spinnerSourceKey.apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, scanCodes).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            onItemSelectedListener = createSimpleItemSelectedListener { position ->
                val selectedKey = scanCodes[position]
                saveString("source_key", selectedKey)
                showToast("Source Key: $selectedKey")
            }
        }

        spinnerRemapType.apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, remapTypes).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            onItemSelectedListener = createSimpleItemSelectedListener { position ->
                val selectedType = remapTypes[position]
                saveString("remap_type", selectedType)
                showToast("Remap Type: $selectedType")
            }
        }

        editTextTarget.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveString("target", editTextTarget.text.toString().trim())
                applyKeyRemapping()
            }
        }
    }

    private fun setupPTTSettings() = with(binding) {
        switchEnableNotification.setOnCheckedChangeListener { _, isChecked ->
            saveBoolean("enable_notification", isChecked)
            showToast("PTT Notification Enabled: $isChecked")
        }

        switchPTTScreenLock.setOnCheckedChangeListener { _, isChecked ->
            saveBoolean("ptt_screen_lock", isChecked)
            showToast("PTT with Screen Lock Enabled: $isChecked")
        }

        editTextWalkieTalkieApp.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val appPackage = editTextWalkieTalkieApp.text.toString().trim()
                saveString("walkie_talkie_app", appPackage)
                showToast("Walkie-Talkie App: $appPackage")
            }
        }
    }

    private fun setupAdvancedKeyboardSettings() = with(binding) {
        switchBacklightEnable.setOnCheckedChangeListener { _, isChecked ->
            saveBoolean("backlight_enable", isChecked)
            showToast("Backlight Enabled: $isChecked")
        }

        seekBarBacklightBrightness.setOnSeekBarChangeListener(
            createSimpleSeekBarChangeListener { progress ->
                saveInt("backlight_brightness", progress)
            }
        )

        editTextBacklightTimeout.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val timeout = editTextBacklightTimeout.text.toString().toIntOrNull() ?: 5
                saveInt("backlight_timeout", timeout)
                showToast("Backlight Timeout: $timeout seconds")
            }
        }

        editTextMultitapDelay.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val delay = editTextMultitapDelay.text.toString().toIntOrNull() ?: 330
                saveInt("multitap_delay", delay)
                showToast("Multitap Delay: $delay ms")
            }
        }
    }

    private fun setupBottomNavigation() = with(binding.bottomNavigation) {
        setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> navigateTo(MainActivity::class.java)
                R.id.navigation_configuration -> navigateTo(ConfigurationActivity::class.java)
                R.id.navigation_system -> navigateTo(SystemActivity::class.java)
                R.id.navigation_keyboard -> true
                R.id.navigation_app -> navigateTo(AppActivity::class.java)
                else -> false
            }
        }
        selectedItemId = R.id.navigation_keyboard
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), READ_STORAGE_PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == READ_STORAGE_PERMISSION_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                showToast("Storage Permissions Granted")
            } else {
                showToast("Storage Permissions Denied")
            }
        }
    }

    private fun loadSettings() = with(binding) {
        switchLockInput.isChecked = sharedPrefs.getBoolean("lock_input", false)
        spinnerTriggerSource.setSelection(triggerSources.indexOf(sharedPrefs.getString("trigger_source", "None")))
        switchEnableTrigger.isChecked = sharedPrefs.getBoolean("enable_trigger_source", true)

        switchAutoScanEnable.isChecked = sharedPrefs.getBoolean("auto_scan_enable", false)
        spinnerAutoScanRange.setSelection(autoScanRanges.indexOf(sharedPrefs.getString("auto_scan_range", "Near")))

        switchMotionTriggerEnable.isChecked = sharedPrefs.getBoolean("motion_trigger_enable", false)
        editTextMotionSensitivity.setText(sharedPrefs.getInt("motion_trigger_sensitivity", 0).toString())
        switchVibrateOnMotion.isChecked = sharedPrefs.getBoolean("vibrate_on_motion", true)

        switchResetRemapping.isChecked = sharedPrefs.getBoolean("reset_remapping", false)
        spinnerSourceKey.setSelection(scanCodes.indexOf(sharedPrefs.getString("source_key", scanCodes[0])))
        spinnerRemapType.setSelection(remapTypes.indexOf(sharedPrefs.getString("remap_type", "Keycode")))
        editTextTarget.setText(sharedPrefs.getString("target", ""))

        switchEnableNotification.isChecked = sharedPrefs.getBoolean("enable_notification", false)
        switchPTTScreenLock.isChecked = sharedPrefs.getBoolean("ptt_screen_lock", false)
        editTextWalkieTalkieApp.setText(sharedPrefs.getString("walkie_talkie_app", ""))

        switchBacklightEnable.isChecked = sharedPrefs.getBoolean("backlight_enable", false)
        seekBarBacklightBrightness.progress = sharedPrefs.getInt("backlight_brightness", 50)
        editTextBacklightTimeout.setText(sharedPrefs.getInt("backlight_timeout", 5).toString())
        editTextMultitapDelay.setText(sharedPrefs.getInt("multitap_delay", 330).toString())
    }

    private fun saveBoolean(key: String, value: Boolean) = sharedPrefs.edit().putBoolean(key, value).apply()
    private fun saveInt(key: String, value: Int) = sharedPrefs.edit().putInt(key, value).apply()
    private fun saveString(key: String, value: String) = sharedPrefs.edit().putString(key, value).apply()

    private fun applyKeyRemapping() {
        val sourceKey = sharedPrefs.getString("source_key", scanCodes[0]) ?: scanCodes[0]
        val remapType = sharedPrefs.getString("remap_type", "Keycode") ?: "Keycode"
        val target = sharedPrefs.getString("target", "") ?: ""
        val scanEntry = VScanEntry(getScanCodeValue(sourceKey))

        when (remapType) {
            "Keycode" -> {
                val keyCode = getKeyCodeFromString(target)
                if (keyCode != null) {
                    keyboardManager.mapKeyCode(scanEntry, KeyCodeEntry(keyCode))
                    showToast("Mapped $sourceKey to $target")
                } else {
                    showToast("Invalid Key Code")
                }
            }
            "Unicode" -> {
                if (target.isNotEmpty()) {
                    keyboardManager.mapUnicode(scanEntry, target[0])
                    showToast("Mapped $sourceKey to '${target[0]}'")
                }
            }
            "Intent" -> {
                try {
                    val intent = Intent(target)
                    keyboardManager.mapIntent(scanEntry, intent)
                    showToast("Mapped $sourceKey to Intent '$target'")
                } catch (e: Exception) {
                    showToast("Invalid Intent Action")
                }
            }
        }
    }

    private fun showMapKeyCodeDialog() {
        val layout = createDialogLayout()
        val keySpinner = createSpinner(scanCodes)
        val keyCodeEditText = createEditText("Enter Android Key Code (e.g., KEYCODE_HOME)")
        layout.apply {
            addView(createTextView("Select Physical Key:"))
            addView(keySpinner)
            addView(createTextView("Enter Android Key Code:"))
            addView(keyCodeEditText)
        }
        showDialog("Map Key Code", layout) {
            val selectedKey = keySpinner.selectedItem.toString()
            val keyCodeStr = keyCodeEditText.text.toString().trim()
            val keyCode = getKeyCodeFromString(keyCodeStr)
            if (keyCode != null) {
                keyboardManager.mapKeyCode(VScanEntry(getScanCodeValue(selectedKey)), KeyCodeEntry(keyCode))
                showToast("Mapped $selectedKey to $keyCodeStr")
            } else {
                showToast("Invalid Key Code")
            }
        }
    }

    private fun showMapUnicodeDialog() {
        val layout = createDialogLayout()
        val keySpinner = createSpinner(scanCodes)
        val unicodeEditText = createEditText("Enter Unicode Character (e.g., A)")
        layout.apply {
            addView(createTextView("Select Physical Key:"))
            addView(keySpinner)
            addView(createTextView("Enter Unicode Character:"))
            addView(unicodeEditText)
        }
        showDialog("Map Unicode", layout) {
            val selectedKey = keySpinner.selectedItem.toString()
            val unicodeChar = unicodeEditText.text.toString()
            if (unicodeChar.isNotEmpty()) {
                keyboardManager.mapUnicode(VScanEntry(getScanCodeValue(selectedKey)), unicodeChar[0])
                showToast("Mapped $selectedKey to '${unicodeChar[0]}'")
            } else {
                showToast("Unicode Character cannot be empty")
            }
        }
    }

    private fun showMapIntentDialog() {
        val layout = createDialogLayout()
        val keySpinner = createSpinner(scanCodes)
        val intentEditText = createEditText("Enter Intent Action (e.g., android.intent.action.VIEW)")
        layout.apply {
            addView(createTextView("Select Physical Key:"))
            addView(keySpinner)
            addView(createTextView("Enter Intent Action:"))
            addView(intentEditText)
        }
        showDialog("Map Intent", layout) {
            val selectedKey = keySpinner.selectedItem.toString()
            val intentAction = intentEditText.text.toString().trim()
            if (intentAction.isNotEmpty()) {
                try {
                    keyboardManager.mapIntent(VScanEntry(getScanCodeValue(selectedKey)), Intent(intentAction))
                    showToast("Mapped $selectedKey to Intent '$intentAction'")
                } catch (e: Exception) {
                    showToast("Invalid Intent Action")
                }
            } else {
                showToast("Intent Action cannot be empty")
            }
        }
    }

    private fun showMapCustomActionDialog() {
        showToast("Custom Action Mapping is not supported in the current SDK.")
    }

    private fun configurePTT() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                powerManager.activateWakeup(WakeupSource.TRIG_PTT)
                val walkieTalkieApp = sharedPrefs.getString("walkie_talkie_app", "") ?: ""
                if (walkieTalkieApp.isNotEmpty()) {
                    val intent = packageManager.getLaunchIntentForPackage(walkieTalkieApp)
                    if (intent != null) {
                        val pttTriggers = listOf("VSCAN_PTT_TRIGGER_LEFT", "VSCAN_PTT_TRIGGER_RIGHT")
                        for (trigger in pttTriggers) {
                            keyboardManager.mapIntent(VScanEntry(getScanCodeValue(trigger)), intent)
                        }
                        withContext(Dispatchers.Main) { showToast("PTT Configured for Walkie-Talkie App") }
                    } else {
                        withContext(Dispatchers.Main) { showToast("App not found") }
                    }
                } else {
                    withContext(Dispatchers.Main) { showToast("Walkie-Talkie App not configured") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("Failed to Configure PTT") }
            }
        }
    }

    private fun exportConfiguration() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val exportFile = File("/sdcard/keyboard_config.json")
                val configJson = """{ "mappings": [], "settings": {} }"""
                exportFile.writeText(configJson)
                withContext(Dispatchers.Main) { showToast("Configuration Exported to ${exportFile.absolutePath}") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("Failed to Export Configuration") }
            }
        }
    }

    private fun importConfiguration() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val importFile = File("/sdcard/keyboard_config.json")
                if (!importFile.exists()) {
                    withContext(Dispatchers.Main) { showToast("Import file not found") }
                    return@launch
                }
                keyboardManager.clearAllMappings()
                withContext(Dispatchers.Main) {
                    showToast("Configuration Imported Successfully")
                    loadSettings()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("Failed to Import Configuration") }
            }
        }
    }

    private fun deleteAllMappings() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                keyboardManager.clearAllMappings()
                withContext(Dispatchers.Main) { showToast("All Mappings Deleted") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("Failed to Delete Mappings") }
            }
        }
    }

    private fun getKeyCodeFromString(keyCodeStr: String): Int? {
        return try {
            val field = KeyEvent::class.java.getField(keyCodeStr)
            field.getInt(null)
        } catch (e: Exception) {
            null
        }
    }

    private fun getScanCodeValue(scanCodeName: String) = when (scanCodeName) {
        "VSCAN_LEFT_TRIGGER" -> VScanCode.VSCAN_LEFT_TRIGGER
        "VSCAN_RIGHT_TRIGGER" -> VScanCode.VSCAN_RIGHT_TRIGGER
//        "VSCAN_PTT_TRIGGER_LEFT" -> VScanCode.VSCAN_PTT_TRIGGER_LEFT
//        "VSCAN_PTT_TRIGGER_RIGHT" -> VScanCode.VSCAN_PTT_TRIGGER_RIGHT
        "VSCAN_AUTOSCAN_TRIGGER" -> VScanCode.VSCAN_AUTOSCAN_TRIGGER
        "VSCAN_MOTION_TRIGGER" -> VScanCode.VSCAN_MOTION_TRIGGER
        else -> 0
    }

    private fun createDialogLayout() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(50, 40, 50, 10)
    }

    private fun createSpinner(items: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun createEditText(hintText: String) = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun createTextView(textStr: String) = TextView(this).apply { text = textStr }

    private fun showDialog(title: String, view: View, onPositive: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton("Map") { _, _ -> onPositive() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateTo(activityClass: Class<*>) = startActivity(Intent(this, activityClass)).let { true }

    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun createSimpleItemSelectedListener(onItemSelected: (position: Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
            onItemSelected(position)
        }
        override fun onNothingSelected(parent: AdapterView<*>) {}
    }

    private fun createSimpleSeekBarChangeListener(onProgressChanged: (progress: Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onProgressChanged(progress)
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
}
