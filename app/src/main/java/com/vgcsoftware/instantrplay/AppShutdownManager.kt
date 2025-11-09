package com.vgcsoftware.instantrplay

import android.app.Activity
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import kotlin.system.exitProcess

object AppShutdownManager {

    fun requestAppTermination(activity: Activity) {
        val context = activity.applicationContext

        cancelServiceRestartAlarm(context)
        stopAudioService(context)
        clearNotifications(context)

        activity.finishAffinity()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.finishAndRemoveTask()
            finishBackgroundAppTasks(context)
        }

        // Delay the process kill slightly so the toast can show and cleanup can finish.
        Handler(Looper.getMainLooper()).postDelayed({
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }, 500)
    }

    private fun cancelServiceRestartAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val alarmIntent = Intent(context, AudioRecordingService::class.java)
        val pendingIntent = PendingIntent.getService(
            context,
            0,
            alarmIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun stopAudioService(context: Context) {
        context.stopService(Intent(context, AudioRecordingService::class.java))
    }

    private fun clearNotifications(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancelAll()
    }

    private fun finishBackgroundAppTasks(context: Context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        activityManager.appTasks.forEach { it.finishAndRemoveTask() }
    }
}
