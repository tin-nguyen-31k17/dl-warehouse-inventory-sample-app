package com.example.warehouseinventoryapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.*
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.datalogic.device.Intents
import com.datalogic.device.ErrorManager
import com.datalogic.device.configuration.*

/**
 * Define PropertyID constants as per Datalogic SDK
 * Replace the integer values with actual ones from the SDK if different
 */
object PropertyID {
    const val AIRPLANE_MODE = 1001
    const val BRIGHTNESS_LEVEL = 1002
    const val DEVICE_NAME_BASE = 1003
    const val BT_PAIRING_POLICY = 1004
    const val DEVICE_NAME_SUFFIX = 1005
    const val BT_SILENT_PAIRING_WHITELISTING = 1006

    // Action and Extra constants for intents
    const val ACTION_SET_CONFIGURATION = "com.datalogic.device.configuration.ACTION_SET_CONFIGURATION"
    const val EXTRA_SET_CONFIGURATION_MAP = "com.datalogic.device.configuration.EXTRA_SET_CONFIGURATION_MAP"
}

enum class BTPairingPolicy {
    AUTOMATIC,
    MANUAL,
    DISABLED
}

enum class DeviceNameSuffix {
    MAC_ADDRESS,
    NIC_SPECIFIC_MAC_ADDRESS,
    NONE,
    SERIAL_NUMBER
}

class ConfigurationActivity : AppCompatActivity() {

    private lateinit var configurationManager: ConfigurationManager

    // UI elements
    private lateinit var switchBooleanProperty: Switch
    private lateinit var editTextNumericProperty: EditText
    private lateinit var editTextTextProperty: EditText
    private lateinit var spinnerEnumProperty: Spinner
    private lateinit var spinnerDeviceNameSuffix: Spinner
    private lateinit var buttonSetProperties: Button
    private lateinit var buttonSetPropertiesViaIntent: Button
    private lateinit var buttonManageBlobProperty: Button

    private val CHANNEL_ID = "configuration_changes"

    private val configurationChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Handle the configuration change intent
            if (intent?.action == Intents.ACTION_CONFIGURATION_CHANGED) {
                // Get extras
                val changedMap = intent.getSerializableExtra(Intents.EXTRA_CONFIGURATION_CHANGED_MAP) as? HashMap<Int, Any>
                val errorMap = intent.getSerializableExtra(Intents.EXTRA_CONFIGURATION_ERROR_MAP) as? HashMap<Int, Any>
                val time = intent.getLongExtra(Intents.EXTRA_CONFIGURATION_CHANGED_TIME, 0)

                // Create notification
                val notificationBuilder = NotificationCompat.Builder(this@ConfigurationActivity, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Configuration Changed")
                    .setContentText("Properties have been changed")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)

                with(NotificationManagerCompat.from(this@ConfigurationActivity)) {
                    notify(1, notificationBuilder.build())
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the layout
        setContentView(R.layout.activity_configuration)

        // Enable exception handling for Datalogic SDK
        ErrorManager.enableExceptions(true)

        // Initialize UI elements
        switchBooleanProperty = findViewById(R.id.switchBooleanProperty)
        editTextNumericProperty = findViewById(R.id.editTextNumericProperty)
        editTextTextProperty = findViewById(R.id.editTextTextProperty)
        spinnerEnumProperty = findViewById(R.id.spinnerEnumProperty)
        spinnerDeviceNameSuffix = findViewById(R.id.spinnerDeviceNameSuffix)
        buttonSetProperties = findViewById(R.id.buttonSetProperties)
        buttonSetPropertiesViaIntent = findViewById(R.id.buttonSetPropertiesViaIntent)
        buttonManageBlobProperty = findViewById(R.id.buttonManageBlobProperty)

        // Initialize ConfigurationManager with Context
        try {
            configurationManager = ConfigurationManager(this)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to initialize ConfigurationManager", Toast.LENGTH_SHORT).show()
            return
        }

        // Set up Notification Channel
        createNotificationChannel()

        // Register receiver for configuration changes
        val filter = IntentFilter(Intents.ACTION_CONFIGURATION_CHANGED)
        // Removed invalid addFlags call
        registerReceiver(configurationChangeReceiver, filter)

        // Get properties using PropertyID constants
        val booleanProperty = configurationManager.getPropertyById(PropertyID.AIRPLANE_MODE) as? BooleanProperty
        val numericProperty = configurationManager.getPropertyById(PropertyID.BRIGHTNESS_LEVEL) as? NumericProperty
        val textProperty = configurationManager.getPropertyById(PropertyID.DEVICE_NAME_BASE) as? TextProperty
        val enumProperty = configurationManager.getPropertyById(PropertyID.BT_PAIRING_POLICY) as? EnumProperty<BTPairingPolicy>
        val deviceNameSuffixProperty = configurationManager.getPropertyById(PropertyID.DEVICE_NAME_SUFFIX) as? EnumProperty<DeviceNameSuffix>
        val blobProperty = configurationManager.getPropertyById(PropertyID.BT_SILENT_PAIRING_WHITELISTING) as? BlobProperty

        // Initialize UI with current property values
        initializeUI(booleanProperty, numericProperty, textProperty, enumProperty, deviceNameSuffixProperty)

        // Set listeners
        buttonSetProperties.setOnClickListener {
            setProperties(booleanProperty, numericProperty, textProperty, enumProperty, deviceNameSuffixProperty)
        }

        buttonSetPropertiesViaIntent.setOnClickListener {
            setPropertiesViaIntent(booleanProperty, numericProperty, textProperty, enumProperty, deviceNameSuffixProperty)
        }

        buttonManageBlobProperty.setOnClickListener {
            manageBlobProperty(blobProperty)
        }
    }

    private fun initializeUI(
        booleanProperty: BooleanProperty?,
        numericProperty: NumericProperty?,
        textProperty: TextProperty?,
        enumProperty: EnumProperty<BTPairingPolicy>?,
        deviceNameSuffixProperty: EnumProperty<DeviceNameSuffix>?
    ) {
        // Boolean Property
        if (booleanProperty != null && booleanProperty.isSupported) {
            val currentValue = booleanProperty.get() // Use get() instead of 'value'
            switchBooleanProperty.isChecked = currentValue
        } else {
            switchBooleanProperty.isEnabled = false
        }

        // Numeric Property
        if (numericProperty != null && numericProperty.isSupported) {
            val currentValue = numericProperty.get() // Use get() instead of 'value'
            editTextNumericProperty.setText(currentValue.toString()) // Ensure String is passed
        } else {
            editTextNumericProperty.isEnabled = false
        }

        // Text Property
        if (textProperty != null && textProperty.isSupported) {
            val currentValue = textProperty.get() // Use get() instead of 'value'
            editTextTextProperty.setText(currentValue)
        } else {
            editTextTextProperty.isEnabled = false
        }

        // Enum Property
        if (enumProperty != null && enumProperty.isSupported) {
            val currentValue = enumProperty.get() // Use get() instead of 'value'
            val enumValues = enumProperty.getEnumConstants()
            if (enumValues != null) {
                val adapter = ArrayAdapter<BTPairingPolicy>(
                    this,
                    android.R.layout.simple_spinner_item,
                    enumValues
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerEnumProperty.adapter = adapter
                val index = enumValues.indexOf(currentValue)
                if (index >= 0) {
                    spinnerEnumProperty.setSelection(index)
                }
            }
        } else {
            spinnerEnumProperty.isEnabled = false
        }

        // Device Name Suffix
        if (deviceNameSuffixProperty != null && deviceNameSuffixProperty.isSupported) {
            val currentValue = deviceNameSuffixProperty.get() // Use get() instead of 'value'
            val enumValues = deviceNameSuffixProperty.getEnumConstants()
            if (enumValues != null) {
                val adapter = ArrayAdapter<DeviceNameSuffix>(
                    this,
                    android.R.layout.simple_spinner_item,
                    enumValues
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerDeviceNameSuffix.adapter = adapter
                val index = enumValues.indexOf(currentValue)
                if (index >= 0) {
                    spinnerDeviceNameSuffix.setSelection(index)
                }
            }
        } else {
            spinnerDeviceNameSuffix.isEnabled = false
        }
    }

    private fun setProperties(
        booleanProperty: BooleanProperty?,
        numericProperty: NumericProperty?,
        textProperty: TextProperty?,
        enumProperty: EnumProperty<BTPairingPolicy>?,
        deviceNameSuffixProperty: EnumProperty<DeviceNameSuffix>?
    ) {
        try {
            // Boolean Property
            if (booleanProperty != null && booleanProperty.isSupported) {
                val newValue: Boolean = switchBooleanProperty.isChecked
                booleanProperty.set(newValue) // Use set() method
            }

            // Numeric Property
            if (numericProperty != null && numericProperty.isSupported) {
                val newValue = editTextNumericProperty.text.toString().toIntOrNull()
                if (newValue != null) {
                    numericProperty.set(newValue) // Use set() method
                } else {
                    Toast.makeText(this, "Invalid numeric value", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            // Text Property
            if (textProperty != null && textProperty.isSupported) {
                val newValue = editTextTextProperty.text.toString()
                textProperty.set(newValue) // Use set() method
            }

            // Enum Property
            if (enumProperty != null && enumProperty.isSupported) {
                val selectedEnumValue = spinnerEnumProperty.selectedItem as? BTPairingPolicy
                if (selectedEnumValue != null) {
                    enumProperty.set(selectedEnumValue) // Use set() method
                } else {
                    Toast.makeText(this, "Invalid enum selection", Toast.LENGTH_SHORT).show()
                }
            }

            // Device Name Suffix
            if (deviceNameSuffixProperty != null && deviceNameSuffixProperty.isSupported) {
                val selectedEnumValue = spinnerDeviceNameSuffix.selectedItem as? DeviceNameSuffix
                if (selectedEnumValue != null) {
                    deviceNameSuffixProperty.set(selectedEnumValue) // Use set() method
                } else {
                    Toast.makeText(this, "Invalid enum selection", Toast.LENGTH_SHORT).show()
                }
            }

            // Commit changes
            configurationManager.commit()
            Toast.makeText(this, "Properties set successfully", Toast.LENGTH_SHORT).show()
        } catch (e: ConfigException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to set properties: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setPropertiesViaIntent(
        booleanProperty: BooleanProperty?,
        numericProperty: NumericProperty?,
        textProperty: TextProperty?,
        enumProperty: EnumProperty<BTPairingPolicy>?,
        deviceNameSuffixProperty: EnumProperty<DeviceNameSuffix>?
    ) {
        val configurationMap = HashMap<Int, Any>()

        // Boolean Property
        if (booleanProperty != null && booleanProperty.isSupported) {
            configurationMap[booleanProperty.id] = switchBooleanProperty.isChecked
        }

        // Numeric Property
        if (numericProperty != null && numericProperty.isSupported) {
            val newValue = editTextNumericProperty.text.toString().toIntOrNull()
            if (newValue != null) {
                configurationMap[numericProperty.id] = newValue
            } else {
                Toast.makeText(this, "Invalid numeric value", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // Text Property
        if (textProperty != null && textProperty.isSupported) {
            val newValue = editTextTextProperty.text.toString()
            configurationMap[textProperty.id] = newValue
        }

        // Enum Property
        if (enumProperty != null && enumProperty.isSupported) {
            val selectedEnumValue = spinnerEnumProperty.selectedItem as? BTPairingPolicy
            if (selectedEnumValue != null) {
                configurationMap[enumProperty.id] = selectedEnumValue
            }
        }

        // Device Name Suffix
        if (deviceNameSuffixProperty != null && deviceNameSuffixProperty.isSupported) {
            val selectedEnumValue = spinnerDeviceNameSuffix.selectedItem as? DeviceNameSuffix
            if (selectedEnumValue != null) {
                configurationMap[deviceNameSuffixProperty.id] = selectedEnumValue
            }
        }

        // Send configuration intent
        val intent = Intent(PropertyID.ACTION_SET_CONFIGURATION)
        intent.putExtra(PropertyID.EXTRA_SET_CONFIGURATION_MAP, configurationMap)
        sendBroadcast(intent)
    }

    private fun manageBlobProperty(blobProperty: BlobProperty?) {
        if (blobProperty == null || !blobProperty.isSupported) {
            Toast.makeText(this, "Blob Property not supported", Toast.LENGTH_SHORT).show()
            return
        }

        // Implement logic to manage the Blob property
        // This is a placeholder. Actual implementation depends on the Blob's structure.
        Toast.makeText(this, "Managing Blob Property (Not Implemented)", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(configurationChangeReceiver)
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, only on API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Configuration Changes"
            val descriptionText = "Notifications for configuration changes"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = descriptionText

            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
