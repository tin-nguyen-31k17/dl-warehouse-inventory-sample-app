package com.example.warehouseinventoryapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.*
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.datalogic.device.Intents
import com.datalogic.device.ErrorManager
import com.datalogic.device.configuration.*
import java.util.*

class ConfigurationActivity : AppCompatActivity() {

    private lateinit var configurationManager: ConfigurationManager

    // UI Elements
    private lateinit var linearLayoutProperties: LinearLayout
    private lateinit var buttonApplyChanges: Button

    private val CHANNEL_ID = "configuration_changes"

    // Map to store Property to its corresponding interactive View
    private val propertyViewsMap: MutableMap<Property<*>, View> = mutableMapOf()

    // BroadcastReceiver to handle configuration changes
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
                    .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this drawable exists
                    .setContentTitle("Configuration Changed")
                    .setContentText("Properties have been updated.")
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
        linearLayoutProperties = findViewById(R.id.linearLayoutProperties)
        buttonApplyChanges = findViewById(R.id.buttonApplyChanges)

        // Initialize ConfigurationManager with Context
        try {
            configurationManager = ConfigurationManager(this)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to initialize ConfigurationManager", Toast.LENGTH_SHORT).show()
            finish() // Close the activity if initialization fails
            return
        }

        // Set up Notification Channel
        createNotificationChannel()

        // Register receiver for configuration changes
        val filter = IntentFilter(Intents.ACTION_CONFIGURATION_CHANGED)
        registerReceiver(configurationChangeReceiver, filter)

        // Fetch all properties at runtime
        fetchAndDisplayProperties()

        // Set listener for Apply Changes button
        buttonApplyChanges.setOnClickListener {
            applyConfigurationChanges()
        }
    }

    /**
     * Recursively fetches all properties from the property tree.
     */
    private fun getAllProperties(group: PropertyGroup): List<Property<*>> {
        val properties = mutableListOf<Property<*>>()
        val childGroups = group.getGroups()
        val childProperties = group.getProperties()

        // Add properties in this group
        properties.addAll(childProperties)

        // Recursively add properties from child groups
        for (childGroup in childGroups) {
            properties.addAll(getAllProperties(childGroup))
        }

        return properties
    }

    /**
     * Fetches and displays all supported and writable properties.
     */
    private fun fetchAndDisplayProperties() {
        try {
            val rootGroup = configurationManager.getTreeRoot()
            val allProperties = getAllProperties(rootGroup)

            for (property in allProperties) {
                // Check if the property is writable
                val writable = !property.isReadOnly()

                if (property.isSupported() && writable) {
                    val container = createViewForProperty(property)
                    if (container != null) {
                        linearLayoutProperties.addView(container)
                        // The interactive view is already mapped in createViewForProperty
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to fetch properties", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Creates a UI element based on the property type
     */
    private fun createViewForProperty(property: Property<*>): View? {
        val context = this
        var layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 16, 0, 16)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = layoutParams
        }

        // Add a TextView for the property name
        val textViewName = TextView(context).apply {
            text = property.getName()
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        container.addView(textViewName)

        when (property) {
            is BooleanProperty -> {
                val switchView = Switch(context).apply {
                    isChecked = property.get()
                }
                container.addView(switchView)
                propertyViewsMap[property] = switchView
                return container
            }
            is EnumProperty<*> -> {
                val spinner = Spinner(context)
                val enumValues = property.getEnumConstants()

                if (enumValues != null) {
                    val enumNames = enumValues.map { it.toString() }
                    val adapter = ArrayAdapter(
                        context,
                        android.R.layout.simple_spinner_item,
                        enumNames
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinner.adapter = adapter

                    // Set current selection
                    val currentValue = property.get()
                    val index = enumValues.indexOf(currentValue)
                    if (index >= 0) {
                        spinner.setSelection(index)
                    }

                    container.addView(spinner)
                    propertyViewsMap[property] = spinner
                    return container
                }
            }
            is NumericProperty -> {
                val editText = EditText(context).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                    setText(property.get().toString())
                }
                container.addView(editText)
                propertyViewsMap[property] = editText
                return container
            }
            is TextProperty -> {
                val editText = EditText(context).apply {
                    inputType = android.text.InputType.TYPE_CLASS_TEXT
                    setText(property.get())
                }
                container.addView(editText)
                propertyViewsMap[property] = editText
                return container
            }
            is BlobProperty -> {
                // Handling BlobProperty can be complex; provide a placeholder or implement as needed
                val buttonManageBlob = Button(context).apply {
                    text = "Manage ${property.getName()}"
                    setOnClickListener {
                        manageBlobProperty(property)
                    }
                }
                container.addView(buttonManageBlob)
                propertyViewsMap[property] = buttonManageBlob
                return container
            }
            else -> {
                // Unsupported property type
                return null
            }
        }

        return null
    }

    /**
     * Applies the configuration changes based on user input
     */
    private fun applyConfigurationChanges() {
        try {
            for ((property, view) in propertyViewsMap) {
                when (property) {
                    is BooleanProperty -> {
                        val switchView = view as? Switch
                        if (switchView != null) {
                            property.set(switchView.isChecked)
                        }
                    }
                    is EnumProperty<*> -> {
                        val spinner = view as? Spinner
                        if (spinner != null) {
                            val selectedPosition = spinner.selectedItemPosition
                            val enumValues = property.getEnumConstants()
                            if (enumValues != null && selectedPosition in enumValues.indices) {
                                property.set(enumValues[selectedPosition] as Nothing?)
                            }
                        }
                    }
                    is NumericProperty -> {
                        val editText = view as? EditText
                        if (editText != null) {
                            val input = editText.text.toString().toIntOrNull()
                            if (input != null) {
                                property.set(input)
                            } else {
                                Toast.makeText(this, "Invalid input for ${property.getName()}", Toast.LENGTH_SHORT).show()
                                return
                            }
                        }
                    }
                    is TextProperty -> {
                        val editText = view as? EditText
                        if (editText != null) {
                            val input = editText.text.toString()
                            property.set(input)
                        }
                    }
                    is BlobProperty -> {
                        // Handle BlobProperty accordingly
                        continue
                    }
                    else -> {
                        // Unsupported property type
                        continue
                    }
                }
            }

            // Commit the changes
            configurationManager.commit()
            Toast.makeText(this, "Configuration updated successfully", Toast.LENGTH_SHORT).show()
        } catch (e: ConfigException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to apply configuration: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "An unexpected error occurred.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Handles the management of BlobProperty
     */
    private fun manageBlobProperty(blobProperty: BlobProperty?) {
        if (blobProperty == null || !blobProperty.isSupported()) {
            Toast.makeText(this, "Blob Property not supported", Toast.LENGTH_SHORT).show()
            return
        }

        // Implement the logic to manage BlobProperty based on your application's requirements
        // For example, open a dialog to edit Blob data
        Toast.makeText(this, "Managing Blob Property: ${blobProperty.getName()} (Not Implemented)", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(configurationChangeReceiver)
    }

    /**
     * Creates a notification channel for configuration changes.
     */
    private fun createNotificationChannel() {
        // Create the NotificationChannel only on API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Configuration Changes"
            val descriptionText = "Notifications for configuration changes"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
