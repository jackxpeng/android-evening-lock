package com.example.eveninglock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private val distractions = arrayOf(
        "com.reddit.frontpage",
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.amazon.avod.thirdpartyclient",
        "tv.pluto.android",
        "com.disney.disneyplus",
        "com.netflix.mediaclient"
    )

    private val entryPoints = arrayOf(
        "com.android.chrome",
        "com.android.vending"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            // Relapse prevention: prevent uninstallation of this app
            devicePolicyManager.setUninstallBlocked(adminComponent, packageName, true)
            
            // Auto-sync current state in case of bugs or reboots
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val shouldBeLocked = currentHour >= 21 || currentHour < 6
            
            devicePolicyManager.setPackagesSuspended(adminComponent, distractions, shouldBeLocked)
            devicePolicyManager.setPackagesSuspended(adminComponent, entryPoints, false) // Always unsuspend entry points
            entryPoints.forEach { pkg ->
                devicePolicyManager.setApplicationHidden(adminComponent, pkg, shouldBeLocked)
            }
            
            // Schedule the daily lock and unlock
            scheduleEveningLock()
        }

        val btnLock = findViewById<Button>(R.id.btnLock)

        btnLock.setOnClickListener {
            setPackagesSuspended(true)
        }
    }

    private fun scheduleEveningLock() {
        val workManager = WorkManager.getInstance(this)

        // Lock at 9:00 PM (21:00) daily
        val lockRequest = PeriodicWorkRequestBuilder<LockWorker>(1, TimeUnit.DAYS)
            .setInputData(workDataOf("SHOULD_LOCK" to true))
            .setInitialDelay(calculateDelayTo(21, 0), TimeUnit.MILLISECONDS)
            .addTag("evening_lock")
            .build()

        // Unlock at 6:00 AM (06:00) daily
        val unlockRequest = PeriodicWorkRequestBuilder<LockWorker>(1, TimeUnit.DAYS)
            .setInputData(workDataOf("SHOULD_LOCK" to false))
            .setInitialDelay(calculateDelayTo(6, 0), TimeUnit.MILLISECONDS)
            .addTag("morning_unlock")
            .build()

        workManager.enqueueUniquePeriodicWork("DailyLock", ExistingPeriodicWorkPolicy.UPDATE, lockRequest)
        workManager.enqueueUniquePeriodicWork("DailyUnlock", ExistingPeriodicWorkPolicy.UPDATE, unlockRequest)
    }

    private fun calculateDelayTo(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }

    private fun setPackagesSuspended(suspended: Boolean) {
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            try {
                val suspendedPackages = devicePolicyManager.setPackagesSuspended(
                    adminComponent,
                    distractions,
                    suspended
                )
                
                // Fix for Chrome/Play Store: Ensure they are never left in a suspended state from older versions
                devicePolicyManager.setPackagesSuspended(adminComponent, entryPoints, false)
                
                entryPoints.forEach { pkg ->
                    devicePolicyManager.setApplicationHidden(adminComponent, pkg, suspended)
                }
                
                if (suspendedPackages.isEmpty()) {
                    val status = if (suspended) "locked" else "unlocked"
                    Toast.makeText(this, "Apps successfully $status", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to update some apps: ${suspendedPackages.joinToString()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        } else {
            Toast.makeText(this, "App is not Device Owner! Please set via ADB.", Toast.LENGTH_LONG).show()
        }
    }
}
