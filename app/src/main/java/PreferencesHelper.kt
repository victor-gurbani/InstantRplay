package com.vgcsoftware.instantrplay

import android.content.Context
import android.content.SharedPreferences

object PreferencesHelper {

    private const val PREFERENCES_FILE_KEY = "com.vgcsoftware.instantrplay.PREFERENCE_FILE_KEY"
    private const val SAMPLE_RATE_KEY = "sample_rate"
    private const val MAX_RECORDING_AGE_KEY = "max_recording_age"

    // Intervals in minutes
    val STORED_TIMES = listOf(5, 10, 15, 30, 60, 90, 120, 240, 360, 480, 960, 1920, 2880)

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_FILE_KEY, Context.MODE_PRIVATE)
    }

    fun setSampleRate(context: Context, sampleRate: Int) {
        val prefs = getPreferences(context)
        with(prefs.edit()) {
            putInt(SAMPLE_RATE_KEY, sampleRate)
            apply() // Or use commit() if you need synchronous saving
        }
    }

    fun getSampleRate(context: Context, defaultSampleRate: Int = 32000): Int {
        val prefs = getPreferences(context)
        return prefs.getInt(SAMPLE_RATE_KEY, defaultSampleRate)
    }

     // Set the max recording age in preferences
    fun setMaxRecordingAge(context: Context, maxRecordingAge: Int) {
        val prefs = getPreferences(context)
        with(prefs.edit()) {
            putInt(MAX_RECORDING_AGE_KEY, maxRecordingAge)
            apply() // Use apply() for asynchronous commit
        }
    }

    // Get the max recording age from preferences, defaulting to 30 minutes (30 )
    fun getMaxRecordingAge(context: Context, defaultMaxRecordingAge: Int = 30): Int {
        val prefs = getPreferences(context)
        return prefs.getInt(MAX_RECORDING_AGE_KEY, defaultMaxRecordingAge)
    }
}
