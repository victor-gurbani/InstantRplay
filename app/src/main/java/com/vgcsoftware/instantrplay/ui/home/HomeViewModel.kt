package com.vgcsoftware.instantrplay.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    // LiveData to manage text updates for the UI
    private val _text = MutableLiveData<String>().apply {
        value = "This is home Fragment"
    }
    val text: LiveData<String> = _text

    // Function to start recording audio (triggered from a service)
    fun startRecording() {
        viewModelScope.launch {
            // Logic to start the recording, either by interacting with a service
            // or managing the process directly.
            // You can update UI states here by modifying LiveData or other state managers.
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

    // You can optionally add more LiveData, StateFlow, or other
    // state management tools to communicate the recording state.
}
