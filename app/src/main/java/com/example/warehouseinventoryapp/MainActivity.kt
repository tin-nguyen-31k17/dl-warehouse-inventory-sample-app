package com.example.warehouseinventoryapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.warehouseinventoryapp.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.datalogic.decode.BarcodeManager
import com.datalogic.decode.ReadListener
import com.datalogic.decode.configuration.ScannerProperties
import com.datalogic.device.ErrorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var barcodeManager: BarcodeManager? = null
    private var readListener: ReadListener? = null

    private val PERMISSION_REQUEST_CODE = 100

    // Inventory Management
    private lateinit var inventoryAdapter: InventoryAdapter
    private val inventoryList = mutableListOf<InventoryItem>()
    private var currentAction: ActionType = ActionType.NONE

    enum class ActionType {
        NONE, ADD, REMOVE, UPDATE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize UI elements via binding
        binding.textViewResult.text = "Scan a barcode to see the result."

        // Set up RecyclerView
        inventoryAdapter = InventoryAdapter(inventoryList)
        binding.recyclerViewInventory.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewInventory.adapter = inventoryAdapter

        // Set up button click listeners
        binding.buttonAddItem.setOnClickListener {
            currentAction = ActionType.ADD
            initializeBarcodeManager()
        }

        binding.buttonRemoveItem.setOnClickListener {
            currentAction = ActionType.REMOVE
            initializeBarcodeManager()
        }

        binding.buttonUpdateItem.setOnClickListener {
            currentAction = ActionType.UPDATE
            initializeBarcodeManager()
        }

        binding.buttonViewInventory.setOnClickListener {
            binding.recyclerViewInventory.visibility = if (binding.recyclerViewInventory.visibility == View.GONE) {
                inventoryAdapter.notifyDataSetChanged()
                View.VISIBLE
            } else {
                View.GONE
            }
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

        // Setup BottomNavigationView
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    // Already in home, do nothing
                    true
                }
                R.id.navigation_configuration -> {
                    startActivity(Intent(this, ConfigurationActivity::class.java))
                    true
                }
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

        // Optionally, set the selected item
        binding.bottomNavigation.selectedItemId = R.id.navigation_home
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
        // Use lifecycleScope instead of creating a new CoroutineScope
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                barcodeManager = BarcodeManager()
                readListener = ReadListener { decodeResult ->
                    val barcodeData = decodeResult.text
                    val symbology = decodeResult.barcodeID.name
                    // Launch a coroutine on the Main dispatcher to call handleScannedData
                    lifecycleScope.launch(Dispatchers.Main) {
                        handleScannedData(barcodeData, symbology)
                    }
                }
                barcodeManager?.addReadListener(readListener)
                configureScanner()
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to initialize BarcodeManager", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun handleScannedData(data: String, symbology: String) {
        withContext(Dispatchers.Main) {
            binding.textViewResult.text = "Scanned: $data ($symbology)"

            when (currentAction) {
                ActionType.ADD -> addItemToInventory(data)
                ActionType.REMOVE -> removeItemFromInventory(data)
                ActionType.UPDATE -> updateItemInInventory(data)
                else -> {
                    Toast.makeText(this@MainActivity, "No action selected.", Toast.LENGTH_SHORT).show()
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

    private suspend fun configureScanner() {
        try {
            val properties = ScannerProperties.edit(barcodeManager)
            properties.ean13.enable.set(true)
            properties.code128.enable.set(true)
            properties.qrCode.enable.set(true)
            properties.store(barcodeManager, true)
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Failed to configure scanner", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch(Dispatchers.IO) {
            barcodeManager?.removeReadListener(readListener)
            barcodeManager?.release()
        }
    }
}
