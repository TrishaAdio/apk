package com.upimonitor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.upimonitor.databinding.ActivityMainBinding
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val smsGranted = result[Manifest.permission.RECEIVE_SMS] == true
        if (!smsGranted) {
            toast("SMS permission is required to monitor payments")
            binding.enabledSwitch.isChecked = false
            prefs.enabled = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        loadIntoUi()

        binding.saveButton.setOnClickListener { saveFromUi(); toast("Saved") }

        binding.enabledSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                saveFromUi()
                requestPermissions()
                prefs.enabled = true
                ForwardService.start(this)
            } else {
                prefs.enabled = false
                ForwardService.stop(this)
            }
        }

        binding.testButton.setOnClickListener { sendTest() }
        binding.batteryButton.setOnClickListener { requestIgnoreBattery() }
    }

    override fun onResume() {
        super.onResume()
        refreshLog()
    }

    private fun loadIntoUi() {
        binding.urlInput.setText(prefs.webhookUrl)
        binding.keyInput.setText(prefs.apiKey)
        binding.senderInput.setText(prefs.senderFilter)
        binding.keywordInput.setText(prefs.keywordFilter)
        binding.enabledSwitch.isChecked = prefs.enabled
    }

    private fun saveFromUi() {
        prefs.webhookUrl = binding.urlInput.text.toString()
        prefs.apiKey = binding.keyInput.text.toString()
        prefs.senderFilter = binding.senderInput.text.toString()
        prefs.keywordFilter = binding.keywordInput.text.toString()
    }

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBattery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                toast("Already exempt from battery optimization")
            }
        }
    }

    /** Sends a sample BOB SMS to the server so you can confirm the wiring works. */
    private fun sendTest() {
        saveFromUi()
        if (!prefs.isConfigured()) {
            toast("Enter a webhook URL first")
            return
        }
        val sample =
            "Dear BOB UPI User: Your account is credited with INR 1.00 on " +
                nowStamp() +
                " by UPI Ref No " + (100000000000L + (Math.random() * 8e11).toLong()) +
                "; AvlBal: Rs100.00 - BOB"
        thread {
            val ok = Forwarder.forward(this, "AD-BOBTXN", sample, System.currentTimeMillis())
            runOnUiThread {
                toast(if (ok) "Test sent OK" else "Test failed — check URL/key")
                refreshLog()
            }
        }
    }

    private fun nowStamp(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", java.util.Locale.US)
        return fmt.format(java.util.Date())
    }

    private fun refreshLog() {
        val lines = LogStore.entries(this)
        binding.logView.text = if (lines.isEmpty()) "—" else lines.joinToString("\n")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
