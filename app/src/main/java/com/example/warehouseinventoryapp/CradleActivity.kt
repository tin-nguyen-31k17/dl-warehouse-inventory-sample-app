//package com.example.warehouseinventoryapp
//
//import android.os.Bundle
//import android.widget.TextView
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import com.datalogic.cradle.CradleInsertionEventListener
//import com.datalogic.cradle.DLCradleManager
//import com.datalogic.cradle.InsertionState
//
//class CradleActivity : AppCompatActivity(), CradleInsertionEventListener {
//
//    private var cradleManager: DLCradleManager? = null
//    private lateinit var textViewStatus: TextView
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_cradle)
//
//        textViewStatus = findViewById(R.id.textViewStatus)
//
//        // Initialize CradleManager
//        try {
//            cradleManager = DLCradleManager.getInstance()
//        } catch (e: Exception) {
//            e.printStackTrace()
//            Toast.makeText(this, "Failed to initialize CradleManager", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        // Add listener
//        cradleManager?.addInsertionEventListener(this)
//    }
//
//    override fun onInsertionEvent(state: InsertionState) {
//        when (state) {
//            InsertionState.INSERTED_CORRECTLY -> {
//                runOnUiThread {
//                    Toast.makeText(this, "Device inserted into cradle.", Toast.LENGTH_SHORT).show()
//                    textViewStatus.text = "Device inserted into cradle."
//                    syncData()
//                }
//            }
//            InsertionState.EXTRACTED -> {
//                runOnUiThread {
//                    Toast.makeText(this, "Device removed from cradle.", Toast.LENGTH_SHORT).show()
//                    textViewStatus.text = "Device removed from cradle."
//                }
//            }
//            else -> {}
//        }
//    }
//
//    private fun syncData() {
//        // Implement data synchronization logic here
//        Toast.makeText(this, "Data synchronization started.", Toast.LENGTH_SHORT).show()
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        cradleManager?.removeInsertionEventListener(this)
////        cradleManager?.release()
//    }
//}
