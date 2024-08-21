package com.vgcsoftware.instantrplay
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class BootBroadcastReceiver : BroadcastReceiver() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (arePermissionsGranted(context)) {
                Log.d("BootBroadcastReceiver", "Permissions granted, starting service...")
                val serviceIntent = Intent(context, AudioRecordingService::class.java)
                context.startForegroundService(serviceIntent)
            } else {
                Log.d("BootBroadcastReceiver", "Permissions not granted, cannot start service")
                // Show notification

            }
        }
    }

    private fun arePermissionsGranted(context: Context): Boolean {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.FOREGROUND_SERVICE
        )
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
