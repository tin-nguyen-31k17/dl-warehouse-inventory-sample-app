package com.example.warehouseinventoryapp

import android.Manifest
import android.content.Intent // Import Intent for navigation
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.datalogic.decode.BarcodeManager
import com.datalogic.decode.DecodeResult
import com.datalogic.decode.ReadListener
import com.datalogic.decode.configuration.ScannerProperties
import com.datalogic.device.ErrorManager

class MainActivity : AppCompatActivity() {

    private var barcodeManager: BarcodeManager? = null
    private var readListener: ReadListener? = null
    private lateinit var textViewResult: TextView

    private val PERMISSION_REQUEST_CODE = 100

    // UI Elements
    private lateinit var buttonAddItem: Button
    private lateinit var buttonRemoveItem: Button
    private lateinit var buttonUpdateItem: Button
    private lateinit var buttonViewInventory: Button
    private lateinit var buttonOpenConfiguration: Button // New Button
    private lateinit var recyclerViewInventory: RecyclerView

    // Inventory Management
    private lateinit var inventoryAdapter: InventoryAdapter
    private val inventoryList = mutableListOf<InventoryItem>()
    private var currentAction: ActionType = ActionType.NONE

    enum class ActionType {
        NONE, ADD, REMOVE, UPDATE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textViewResult = findViewById(R.id.textViewResult)

        // Initialize UI elements
        buttonAddItem = findViewById(R.id.buttonAddItem)
        buttonRemoveItem = findViewById(R.id.buttonRemoveItem)
        buttonUpdateItem = findViewById(R.id.buttonUpdateItem)
        buttonViewInventory = findViewById(R.id.buttonViewInventory)
        buttonOpenConfiguration = findViewById(R.id.buttonOpenConfiguration) // Initialize new button
        recyclerViewInventory = findViewById(R.id.recyclerViewInventory)

        // Set up RecyclerView
        inventoryAdapter = InventoryAdapter(inventoryList)
        recyclerViewInventory.layoutManager = LinearLayoutManager(this)
        recyclerViewInventory.adapter = inventoryAdapter

        // Set up button click listeners
        buttonAddItem.setOnClickListener {
            currentAction = ActionType.ADD
            initializeBarcodeManager()
        }

        buttonRemoveItem.setOnClickListener {
            currentAction = ActionType.REMOVE
            initializeBarcodeManager()
        }

        buttonUpdateItem.setOnClickListener {
            currentAction = ActionType.UPDATE
            initializeBarcodeManager()
        }

        buttonViewInventory.setOnClickListener {
            recyclerViewInventory.visibility = if (recyclerViewInventory.visibility == View.GONE) {
                inventoryAdapter.notifyDataSetChanged()
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        // Set up click listener for the new button
        buttonOpenConfiguration.setOnClickListener {
            openConfigurationActivity()
        }

        // Enable exception handling for Datalogic SDK
        ErrorManager.enableExceptions(true)

        // Request permissions
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun hasPermissions(): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val locationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        return cameraPermission == PackageManager.PERMISSION_GRANTED &&
                locationPermission == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeBarcodeManager() {
        try {
            barcodeManager = BarcodeManager()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to initialize BarcodeManager", Toast.LENGTH_SHORT).show()
            return
        }

        readListener = ReadListener { decodeResult ->
            val barcodeData = decodeResult.text
            val symbology = decodeResult.barcodeID.name
            handleScannedData(barcodeData, symbology)
        }

        barcodeManager?.addReadListener(readListener)

        configureScanner()
    }

    private fun handleScannedData(data: String, symbology: String) {
        runOnUiThread {
            textViewResult.text = "Scanned: $data ($symbology)"

            when (currentAction) {
                ActionType.ADD -> addItemToInventory(data)
                ActionType.REMOVE -> removeItemFromInventory(data)
                ActionType.UPDATE -> updateItemInInventory(data)
                else -> {
                    Toast.makeText(this, "No action selected.", Toast.LENGTH_SHORT).show()
                }
            }

            // Reset action
            currentAction = ActionType.NONE
            barcodeManager?.removeReadListener(readListener)
            barcodeManager?.release()
        }
    }

    private fun addItemToInventory(barcode: String) {
        val existingItem = inventoryList.find { it.barcode == barcode }
        if (existingItem != null) {
            existingItem.quantity += 1
        } else {
            val item = InventoryItem(barcode, "Item Name", 1)
            inventoryList.add(item)
        }
        inventoryAdapter.notifyDataSetChanged()
        Toast.makeText(this, "Item added: $barcode", Toast.LENGTH_SHORT).show()
    }

    private fun removeItemFromInventory(barcode: String) {
        val item = inventoryList.find { it.barcode == barcode }
        if (item != null) {
            inventoryList.remove(item)
            inventoryAdapter.notifyDataSetChanged()
            Toast.makeText(this, "Item removed: $barcode", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Item not found: $barcode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateItemInInventory(barcode: String) {
        val item = inventoryList.find { it.barcode == barcode }
        if (item != null) {
            // Update item details as needed
            item.quantity += 1 // Example update
            inventoryAdapter.notifyDataSetChanged()
            Toast.makeText(this, "Item updated: $barcode", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Item not found: $barcode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun configureScanner() {
        try {
            val properties = ScannerProperties.edit(barcodeManager)
            properties.ean13.enable.set(true)
            properties.code128.enable.set(true)
            properties.qrCode.enable.set(true)
            properties.store(barcodeManager, true)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to configure scanner", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        barcodeManager?.removeReadListener(readListener)
        barcodeManager?.release()
    }

    /**
     * Function to open ConfigurationActivity
     */
    private fun openConfigurationActivity() {
        val intent = Intent(this, ConfigurationActivity::class.java)
        startActivity(intent)
    }
}
