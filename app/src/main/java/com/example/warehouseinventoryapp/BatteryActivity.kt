//package com.example.warehouseinventoryapp
//
//import android.content.Intent
//import android.content.IntentFilter
//import android.os.Bundle
//import android.widget.TextView
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import com.datalogic.device.battery.BatteryEvent
//import com.datalogic.device.battery.BatteryEventListener
//import com.datalogic.device.battery.BatteryInfo
//import com.datalogic.device.battery.BatteryStatus
//import com.datalogic.device.battery.DLBatteryManager
//
//class BatteryActivity : AppCompatActivity() {
//
//    private var batteryManager: DLBatteryManager? = null
//    private lateinit var textViewBatteryInfo: TextView
//    private lateinit var batteryListener: BatteryEventListener
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_battery)
//
//        textViewBatteryInfo = findViewById(R.id.textViewBatteryInfo)
//
//        // Initialize BatteryManager
//        try {
//            batteryManager = DLBatteryManager.getInstance()
//            batteryManager?.initBatteryEvents(this)
//        } catch (e: Exception) {
//            e.printStackTrace()
//            Toast.makeText(this, "Failed to initialize BatteryManager", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        // Retrieve Battery Status
//        val batteryStatus: BatteryStatus? = batteryManager?.getBatteryStatus()
//        val capacityRemaining = batteryManager?.getIntProperty(BatteryInfo.CAPACITY_REMAINING) ?: 0
//        val isCharging = getChargingStatus()
//
//        textViewBatteryInfo.text = "Capacity Remaining: $capacityRemaining mAh\nCharging: $isCharging"
//
//        // Implement BatteryEventListener
//        batteryListener = object : BatteryEventListener {
//            override fun onEvent(event: BatteryEvent, data: Any?) {
//                when(event) {
//                    BatteryEvent.BATTERY_SWAP_EVENT_BEGIN -> {
//                        runOnUiThread {
//                            Toast.makeText(this@BatteryActivity, "Battery swap started.", Toast.LENGTH_SHORT).show()
//                            textViewBatteryInfo.text = "Battery swap started."
//                        }
//                    }
//                    BatteryEvent.BATTERY_SWAP_EVENT_END -> {
//                        runOnUiThread {
//                            Toast.makeText(this@BatteryActivity, "Battery swap ended.", Toast.LENGTH_SHORT).show()
//                            textViewBatteryInfo.text = "Battery swap ended."
//                            // Optionally update battery info after swap
//                            updateBatteryInfo()
//                        }
//                    }
//                    BatteryEvent.BATTERY_SWAP_EVENT_UNKNOWN -> {
//                        runOnUiThread {
//                            Toast.makeText(this@BatteryActivity, "Unknown battery event.", Toast.LENGTH_SHORT).show()
//                            textViewBatteryInfo.text = "Unknown battery event."
//                        }
//                    }
//                }
//            }
//        }
//
//        batteryManager?.addBatteryEventListener(batteryListener)
//    }
//
//    private fun getChargingStatus(): Boolean {
//        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
//        val batteryStatusIntent = registerReceiver(null, intentFilter)
//        val status = batteryStatusIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
//        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
//                status == android.os.BatteryManager.BATTERY_STATUS_FULL
//    }
//
//    private fun updateBatteryInfo() {
//        val batteryStatus: BatteryStatus? = batteryManager?.getBatteryStatus()
//        val capacityRemaining = batteryManager?.getIntProperty(BatteryInfo.CAPACITY_REMAINING) ?: 0
//        val isCharging = getChargingStatus()
//        textViewBatteryInfo.text = "Capacity Remaining: $capacityRemaining mAh\nCharging: $isCharging"
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        batteryManager?.removeBatteryEventListener(batteryListener)
////        batteryManager?.release()
//    }
//}
