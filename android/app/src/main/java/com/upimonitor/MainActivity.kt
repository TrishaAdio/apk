package com.upimonitor

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.upimonitor.databinding.ActivityMainBinding
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        updateSmsStatus()
        if (!hasSmsAccess()) {
            toast("Phone blocked the SMS permission — use \"Enable SMS access\" below")
        }
    }

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        updateSmsStatus()
        if (hasSmsAccess()) {
            toast("SMS access granted")
        } else {
            toast("Not set as default SMS app")
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
                prefs.enabled = true
                ForwardService.start(this)
                if (!hasSmsAccess()) ensureSmsAccess()
            } else {
                prefs.enabled = false
                ForwardService.stop(this)
            }
        }

        binding.testButton.setOnClickListener { sendTest() }
        binding.batteryButton.setOnClickListener { requestIgnoreBattery() }
        binding.smsAccessButton.setOnClickListener { requestDefaultSmsApp() }
    }

    override fun onResume() {
        super.onResume()
        refreshLog()
        updateSmsStatus()
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

    /* --------------------------- SMS access --------------------------- */

    /** True if the app can actually read incoming SMS. */
    private fun hasSmsAccess(): Boolean {
        val perm = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED
        return perm || isDefaultSmsApp()
    }

    private fun isDefaultSmsApp(): Boolean {
        return Telephony.Sms.getDefaultSmsPackage(this) == packageName
    }

    /**
     * Try the normal runtime permission first; many stock ROMs allow it. On
     * ROMs that hard-block it (HyperOS/MIUI), the grant silently fails and the
     * user should then use "Enable SMS access" (default SMS app).
     */
    private fun ensureSmsAccess() {
        val perms = mutableListOf(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    /** Ask to become the default SMS app — this grants SMS access on locked-down ROMs. */
    private fun requestDefaultSmsApp() {
        if (isDefaultSmsApp()) {
            toast("Already the default SMS app — SMS access is on")
            updateSmsStatus()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
                return
            }
        }
        // Legacy (pre-Android 10) path.
        @Suppress("DEPRECATION")
        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        }
        try {
            roleLauncher.launch(intent)
        } catch (e: Exception) {
            toast("Couldn't open the default-SMS-app chooser on this device")
        }
    }

    private fun updateSmsStatus() {
        val text = when {
            isDefaultSmsApp() -> "SMS access: ON (default SMS app)"
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED -> "SMS access: ON (permission granted)"
            else -> "SMS access: OFF — tap \"Enable SMS access\" below"
        }
        binding.smsStatusView.text = text
    }

    /* --------------------------- battery ------------------------------ */

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

    /* --------------------------- test/log ----------------------------- */

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
