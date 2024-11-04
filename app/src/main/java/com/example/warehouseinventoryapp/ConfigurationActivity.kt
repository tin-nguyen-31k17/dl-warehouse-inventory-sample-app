package com.example.warehouseinventoryapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.*
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.datalogic.device.ErrorManager
import com.datalogic.device.configuration.*
import java.util.*
import com.google.android.material.bottomnavigation.BottomNavigationView

class ConfigurationActivity : AppCompatActivity() {

    private lateinit var configurationManager: ConfigurationManager

    // UI Elements
    private lateinit var buttonFetchProperties: Button
    private lateinit var spinnerProperties: Spinner
    private lateinit var linearLayoutInput: LinearLayout
    private lateinit var buttonApplyChanges: Button

    // Map to store property names to Property objects
    private val propertiesMap: MutableMap<String, Property<*>> = mutableMapOf()

    // Currently selected property
    private var selectedProperty: Property<*>? = null

    // Current input view
    private var currentInputView: View? = null

    // Notification Channel ID
    private val CHANNEL_ID = "configuration_changes"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the layout
        setContentView(R.layout.activity_configuration)

        // Enable exception handling for Datalogic SDK
        ErrorManager.enableExceptions(true)

        // Initialize UI elements
        buttonFetchProperties = findViewById(R.id.buttonFetchProperties)
        spinnerProperties = findViewById(R.id.spinnerProperties)
        linearLayoutInput = findViewById(R.id.linearLayoutInput)
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

        // Set listeners
        buttonFetchProperties.setOnClickListener {
            fetchProperties()
        }

        spinnerProperties.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                val propertyName = parent.getItemAtPosition(position) as String
                selectedProperty = propertiesMap[propertyName]
                displayInputForProperty(selectedProperty)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedProperty = null
                linearLayoutInput.removeAllViews()
            }
        }

        buttonApplyChanges.setOnClickListener {
            applyConfigurationChanges()
        }

        // Setup BottomNavigationView
        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.selectedItemId = R.id.navigation_configuration
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    true
                }
                R.id.navigation_configuration -> true // Stay here
                R.id.navigation_system -> {
                    startActivity(Intent(this, SystemActivity::class.java))
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
    }

    /**
     * Fetches all supported and writable properties and populates the spinner.
     */
    private fun fetchProperties() {
        try {
            val rootGroup = configurationManager.getTreeRoot()
            val allProperties = getAllProperties(rootGroup)

            val propertyNames = mutableListOf<String>()
            propertiesMap.clear()

            for (property in allProperties) {
                val writable = !property.isReadOnly()

                if (property.isSupported() && writable) {
                    val propertyName = property.getName()
                    propertyNames.add(propertyName)
                    propertiesMap[propertyName] = property
                }
            }

            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                propertyNames.sorted()
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerProperties.adapter = adapter

            Toast.makeText(this, "Properties fetched successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to fetch properties", Toast.LENGTH_SHORT).show()
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
     * Displays the appropriate input field based on the property type.
     */
    private fun displayInputForProperty(property: Property<*>?) {
        linearLayoutInput.removeAllViews()
        currentInputView = null

        if (property == null) {
            return
        }

        val context = this

        when (property) {
            is BooleanProperty -> {
                val switchView = Switch(context).apply {
                    isChecked = property.get()
                }
                linearLayoutInput.addView(switchView)
                currentInputView = switchView
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

                    linearLayoutInput.addView(spinner)
                    currentInputView = spinner
                }
            }
            is NumericProperty -> {
                val editText = EditText(context).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                    setText(property.get().toString())
                }
                linearLayoutInput.addView(editText)
                currentInputView = editText
            }
            is TextProperty -> {
                val editText = EditText(context).apply {
                    inputType = android.text.InputType.TYPE_CLASS_TEXT
                    setText(property.get())
                }
                linearLayoutInput.addView(editText)
                currentInputView = editText
            }
            is BlobProperty -> {
                // Handling BlobProperty can be complex; provide a placeholder or implement as needed
                val buttonManageBlob = Button(context).apply {
                    text = "Manage ${property.getName()}"
                    setOnClickListener {
                        manageBlobProperty(property)
                    }
                }
                linearLayoutInput.addView(buttonManageBlob)
                currentInputView = buttonManageBlob
            }
            else -> {
                Toast.makeText(this, "Unsupported property type", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Applies the configuration changes based on user input
     */
    private fun applyConfigurationChanges() {
        val property = selectedProperty
        val view = currentInputView

        if (property == null || view == null) {
            Toast.makeText(this, "No property selected or input view is missing", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            when (property) {
                is BooleanProperty -> {
                    val switchView = view as Switch
                    property.set(switchView.isChecked)
                }
                is EnumProperty<*> -> {
                    val spinner = view as Spinner
                    val selectedPosition = spinner.selectedItemPosition
                    val enumValues = property.getEnumConstants()

                    if (enumValues != null && selectedPosition in enumValues.indices) {
                        val enumValue = enumValues[selectedPosition]
                        // Ensure the enumValue is of the correct type
                        property.set(enumValue as Nothing)
                    }
                }
                is NumericProperty -> {
                    val editText = view as EditText
                    val input = editText.text.toString().toIntOrNull()
                    if (input != null) {
                        property.set(input)
                    } else {
                        Toast.makeText(this, "Invalid input for ${property.getName()}", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
                is TextProperty -> {
                    val editText = view as EditText
                    val input = editText.text.toString()
                    property.set(input)
                }
                is BlobProperty -> {
                    // Handle BlobProperty accordingly
                    Toast.makeText(this, "Blob properties cannot be set directly here", Toast.LENGTH_SHORT).show()
                    return
                }
                else -> {
                    Toast.makeText(this, "Unsupported property type", Toast.LENGTH_SHORT).show()
                    return
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
