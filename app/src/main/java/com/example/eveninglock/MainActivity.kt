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

    companion object {
        val distractions = arrayOf(
            "com.reddit.frontpage",
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
            "com.google.android.apps.magazines",
            "com.facebook.katana",
            "com.amazon.avod.thirdpartyclient",
            "tv.pluto.android",
            "com.disney.disneyplus",
            "com.netflix.mediaclient"
        )

        val entryPoints = arrayOf(
            "com.android.chrome",
            "com.android.vending"
        )
        
        const val LOCK_HOUR = 21 // 9 PM
        const val UNLOCK_HOUR = 6 // 6 AM
    }

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

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
            val shouldBeLocked = currentHour >= LOCK_HOUR || currentHour < UNLOCK_HOUR
            
            devicePolicyManager.setPackagesSuspended(adminComponent, distractions, shouldBeLocked)
            devicePolicyManager.setPackagesSuspended(adminComponent, entryPoints, false) // Always unsuspend entry points
            entryPoints.forEach { pkg ->
                devicePolicyManager.setApplicationHidden(adminComponent, pkg, shouldBeLocked)
            }
            
            // Schedule the daily lock and unlock
            scheduleEveningLock()
        }

        val btnSync = findViewById<Button>(R.id.btnSync)

        btnSync.setOnClickListener {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val shouldBeLocked = currentHour >= LOCK_HOUR || currentHour < UNLOCK_HOUR
            setPackagesSuspended(shouldBeLocked)
        }
    }

    private fun scheduleEveningLock() {
        val workManager = WorkManager.getInstance(this)

        // Cancel old workers from previous versions if they exist
        workManager.cancelUniqueWork("DailyLock")
        workManager.cancelUniqueWork("DailyUnlock")

        // Lock at LOCK_HOUR daily
        val lockRequest = PeriodicWorkRequestBuilder<LockWorker>(1, TimeUnit.DAYS)
            .setInputData(workDataOf("SHOULD_LOCK" to true))
            .setInitialDelay(calculateDelayTo(LOCK_HOUR, 0), TimeUnit.MILLISECONDS)
            .addTag("evening_lock")
            .build()

        // Unlock at UNLOCK_HOUR daily
        val unlockRequest = PeriodicWorkRequestBuilder<LockWorker>(1, TimeUnit.DAYS)
            .setInputData(workDataOf("SHOULD_LOCK" to false))
            .setInitialDelay(calculateDelayTo(UNLOCK_HOUR, 0), TimeUnit.MILLISECONDS)
            .addTag("morning_unlock")
            .build()

        // Use versioned names to ensure that if we change the hours in the future, 
        // the new schedule is applied without interfering with the "UPDATE" skip bug.
        workManager.enqueueUniquePeriodicWork("Lock_v1_$LOCK_HOUR", ExistingPeriodicWorkPolicy.KEEP, lockRequest)
        workManager.enqueueUniquePeriodicWork("Unlock_v1_$UNLOCK_HOUR", ExistingPeriodicWorkPolicy.KEEP, unlockRequest)
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
