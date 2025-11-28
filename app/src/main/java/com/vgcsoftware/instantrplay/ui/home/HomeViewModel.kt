package com.vgcsoftware.instantrplay.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.vgcsoftware.instantrplay.PreferencesHelper
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = PreferencesHelper

    // LiveData to manage text updates for the UI
    private val _text = MutableLiveData<String>().apply {
        value = "Use the button below to save the last 30 minutes into Recording."
    }
    val text: LiveData<String> = _text

    private val _maxRecordingAge = MutableLiveData<Int>()
    val maxRecordingAge: LiveData<Int> = _maxRecordingAge

    init {
        _maxRecordingAge.value = preferences.getMaxRecordingAge(application)
    }

    // Function to start recording audio (triggered from a service)
    fun startRecording() {
        viewModelScope.launch {
            // Logic to start the recording, either by interacting with a service
            // or managing the process directly.
            _text.value = "Recording started"
        }
    }

    // Function to stop recording audio
    fun stopRecording() {
        viewModelScope.launch {
            // Logic to stop the recording process.
            _text.value = "Recording stopped"
        }
    }

    // Retrieve the current sample rate from preferences
    fun getSampleRate(): Int {
        return preferences.getSampleRate(this.getApplication())
    }

    // Update the sample rate in preferences
    fun setSampleRate(sampleRate: Int) {
        preferences.setSampleRate(this.getApplication(), sampleRate)
    }

    // Retrieve the current max recording age from preferences
    fun getMaxRecordingAge(): Int {
        return preferences.getMaxRecordingAge(this.getApplication())
    }

    fun setMaxRecordingAge(maxRecordingAge: Int) {
        preferences.setMaxRecordingAge(this.getApplication(), maxRecordingAge)
        _maxRecordingAge.value = maxRecordingAge
    }

    // Calculate the size of audio per minute based on sample rate
    fun calculateSizePerMinute(sampleRate: Int): Double {
        val bitsPerSample = 16 // Assuming 16-bit PCM
        val channels = 1 // Mono
        val bytesPerSecond = (sampleRate * bitsPerSample * channels) / 8
        val bytesPerMinute = bytesPerSecond * 60
        return bytesPerMinute.toDouble() / (1024 * 1024) // Convert to MB
    }
}
