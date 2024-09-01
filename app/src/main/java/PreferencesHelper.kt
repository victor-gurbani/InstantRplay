package com.vgcsoftware.instantrplay

import android.content.Context
import android.content.SharedPreferences

object PreferencesHelper {

    private const val PREFERENCES_FILE_KEY = "com.vgcsoftware.instantrplay.PREFERENCE_FILE_KEY"
    private const val SAMPLE_RATE_KEY = "sample_rate"

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
}
