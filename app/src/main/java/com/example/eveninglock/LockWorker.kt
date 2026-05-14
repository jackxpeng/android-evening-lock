package com.example.eveninglock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class LockWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val dpm = applicationContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(applicationContext, MyDeviceAdminReceiver::class.java)
        
        // Grab the 'lock' state from the data we send to this worker
        val shouldLock = inputData.getBoolean("SHOULD_LOCK", false)
        val distractions = arrayOf(
            "com.reddit.frontpage",
            "com.google.android.youtube",
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

        return try {
            if (dpm.isDeviceOwnerApp(applicationContext.packageName)) {
                dpm.setPackagesSuspended(adminComponent, distractions, shouldLock)
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
