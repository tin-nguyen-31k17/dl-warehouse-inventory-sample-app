package com.example.warehouseinventoryapp

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.datalogic.device.nfc.NfcManager

class NfcActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private var nfcManager: NfcManager? = null
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var textViewNfcStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc)

        textViewNfcStatus = findViewById(R.id.textViewNfcStatus)

        // Initialize NFC Manager
        try {
            nfcManager = NfcManager() // Replace with actual initialization if necessary
            // nfcManager?.enable() // Uncomment and replace if SDK requires enabling
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to initialize NFC Manager", Toast.LENGTH_SHORT).show()
        }

        // Initialize NFC Adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }

        // Enable reader mode
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        nfcAdapter?.enableReaderMode(this, this, flags, null)
    }

    override fun onTagDiscovered(tag: Tag?) {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            runOnUiThread {
                Toast.makeText(this, "NDEF Tag Discovered", Toast.LENGTH_SHORT).show()
                textViewNfcStatus.text = "NDEF Tag Discovered"
            }
        } else {
            runOnUiThread {
                Toast.makeText(this, "Unknown Tag Discovered", Toast.LENGTH_SHORT).show()
                textViewNfcStatus.text = "Unknown Tag Discovered"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        nfcAdapter?.disableReaderMode(this)
    }
}
