package com.example.warehouseinventoryapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.datalogic.device.Intents
import com.example.warehouseinventoryapp.databinding.ActivitySystemBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.datalogic.device.info.SYSTEM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SystemActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySystemBinding

    // Permission request codes
    private val READ_STORAGE_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize View Binding
        binding = ActivitySystemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up button to trigger Firmware Upgrade
        binding.buttonFirmwareUpgrade.setOnClickListener {
            showFirmwareUpgradeDialog()
        }

        // Set up button to retrieve Device Info
        binding.buttonRetrieveDeviceInfo.setOnClickListener {
            retrieveDeviceInfo()
        }

        // Setup BottomNavigationView
        setupBottomNavigation()

        // Check and request READ_EXTERNAL_STORAGE permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                READ_STORAGE_PERMISSION_CODE
            )
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    true
                }
                R.id.navigation_configuration -> {
                    startActivity(Intent(this, ConfigurationActivity::class.java))
                    true
                }
                R.id.navigation_system -> {
                    // Already in SystemActivity, do nothing
                    true
                }
                R.id.navigation_keyboard -> {
                    startActivity(Intent(this, KeyboardActivity::class.java))
                    true
                }
                R.id.navigation_app -> {
                    startActivity(Intent(this, AppActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Set the selected item
        binding.bottomNavigation.selectedItemId = R.id.navigation_system
    }

    /**
     * Handles the result of permission requests.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == READ_STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Read Storage Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Read Storage Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Displays a dialog allowing the user to specify the firmware file path and select reboot/reset options.
     */
    private fun showFirmwareUpgradeDialog() {
        // Create a LinearLayout to hold the dialog's views
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        // EditText for Firmware Path
        val firmwarePathEditText = EditText(this).apply {
            hint = "Enter Firmware File Path (e.g., /sdcard/ota.zip)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }

        // Switch for Reboot Option
        val rebootSwitch = Switch(this).apply {
            text = "Reboot After Upgrade"
            isChecked = true // Default to reboot
        }

        // RadioGroup for Reset Options
        val resetRadioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }

        val radioNone = RadioButton(this).apply {
            text = "No Reset"
            isChecked = true
        }

        val radioFactoryReset = RadioButton(this).apply {
            text = "Factory Reset"
        }

        val radioEnterpriseReset = RadioButton(this).apply {
            text = "Enterprise Reset"
        }

        // Add RadioButtons to RadioGroup
        resetRadioGroup.addView(radioNone)
        resetRadioGroup.addView(radioFactoryReset)
        resetRadioGroup.addView(radioEnterpriseReset)

        // Add views to the layout
        layout.addView(firmwarePathEditText)
        layout.addView(rebootSwitch)
        layout.addView(resetRadioGroup)

        // Build the AlertDialog
        val dialog = AlertDialog.Builder(this)
            .setTitle("Firmware Upgrade")
            .setView(layout)
            .setPositiveButton("Upgrade") { _, _ ->
                val firmwarePath = firmwarePathEditText.text.toString().trim()
                val rebootOption = if (rebootSwitch.isChecked) 1 else 0
                val resetOption = when (resetRadioGroup.checkedRadioButtonId) {
                    radioFactoryReset.id -> 1
                    radioEnterpriseReset.id -> 2
                    else -> 0
                }

                // Initiate Firmware Upgrade with user inputs
                initiateFirmwareUpgrade(firmwarePath, rebootOption, resetOption)
            }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()
    }

    /**
     * Initiates the Firmware Upgrade using the provided parameters.
     *
     * @param firmwarePath The path to the firmware file (e.g., /sdcard/ota.zip)
     * @param rebootOption 1 to request a reboot after upgrade, 0 otherwise
     * @param resetOption 0 for NONE, 1 for FACTORY RESET, 2 for ENTERPRISE RESET
     */
    private fun initiateFirmwareUpgrade(firmwarePath: String, rebootOption: Int, resetOption: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val firmwareFile = File(firmwarePath)

                // Check if firmware file exists
                if (!firmwareFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@SystemActivity,
                            "Firmware file not found at $firmwarePath.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                val firmwareUpgradeIntent = Intent("com.datalogic.systemupdate.action.FIRMWARE_UPDATE").apply {
                    putExtra("path", firmwarePath) // Specify the firmware file path
                    putExtra("reboot", rebootOption) // Request a reboot after upgrade (1 for reboot, 0 otherwise)
                    putExtra("reset", resetOption) // Request reset after upgrade
                }

                // Check if there's an activity to handle the Intent
                if (firmwareUpgradeIntent.resolveActivity(packageManager) != null) {
                    withContext(Dispatchers.Main) {
                        startActivity(firmwareUpgradeIntent)
                        Toast.makeText(
                            this@SystemActivity,
                            "Firmware Upgrade initiated.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@SystemActivity,
                            "Firmware Upgrade action not supported on this device.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SystemActivity,
                        "Failed to initiate Firmware Upgrade.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Retrieves device information using the SYSTEM class and displays it.
     */
    private fun retrieveDeviceInfo() {
        // Use lifecycleScope for coroutine
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Retrieve system information
                val systemInfo = SYSTEM.getVersions()

                // Build a string to display the information
                val infoBuilder = StringBuilder()
                for ((key, value) in systemInfo) {
                    infoBuilder.append("$key: $value\n")
                }

                // Update UI on Main Thread
                withContext(Dispatchers.Main) {
                    binding.textViewDeviceInfo.text = infoBuilder.toString()
                    Toast.makeText(
                        this@SystemActivity,
                        "Device information retrieved.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SystemActivity,
                        "Failed to retrieve device information.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
