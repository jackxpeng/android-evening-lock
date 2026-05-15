package com.example.eveninglock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Calendar

class LockWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val dpm = applicationContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(applicationContext, MyDeviceAdminReceiver::class.java)
        
        // Use the same lists as MainActivity
        val distractions = MainActivity.distractions
        val entryPoints = MainActivity.entryPoints
        
        // Grab the 'lock' state from the data we send to this worker
        // Default to a time-based check if the input data is missing for some reason
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeBasedLock = currentHour >= MainActivity.LOCK_HOUR || currentHour < MainActivity.UNLOCK_HOUR
        val shouldLock = inputData.getBoolean("SHOULD_LOCK", timeBasedLock)

        return try {
            if (dpm.isDeviceOwnerApp(applicationContext.packageName)) {
                dpm.setPackagesSuspended(adminComponent, distractions, shouldLock)
                
                // Fix for Chrome/Play Store: Ensure they are never left in a suspended state from older versions
                dpm.setPackagesSuspended(adminComponent, entryPoints, false)
                
                entryPoints.forEach { pkg ->
                    dpm.setApplicationHidden(adminComponent, pkg, shouldLock)
                }
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
