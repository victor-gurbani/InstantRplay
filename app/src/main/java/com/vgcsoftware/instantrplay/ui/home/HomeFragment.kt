package com.vgcsoftware.instantrplay.ui.home

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.slider.Slider
import com.vgcsoftware.instantrplay.AudioRecordingService
import com.vgcsoftware.instantrplay.PreferencesHelper
import com.vgcsoftware.instantrplay.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Use activityViewModels to share ViewModel with Activity
    private val viewModel: HomeViewModel by activityViewModels()

    private val sampleRates = listOf(8000, 16000, 32000, 44100, 48000)
    // Use storedTimes from PreferencesHelper
    private val storedTimes = PreferencesHelper.STORED_TIMES

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val sampleRateLabel: TextView = binding.sampleRateLabel
        val audioSizeLabel: TextView = binding.audioSizeLabel
        val sampleRateSlider: Slider = binding.sampleRateSlider
        val storedTimeLabel: TextView = binding.storedTimeLabel
        val storedTimeSlider: Slider = binding.storedTimeSlider

        sampleRateSlider.value = sampleRates.indexOf(viewModel.getSampleRate()).toFloat()

        // Observe maxRecordingAge changes
        viewModel.maxRecordingAge.observe(viewLifecycleOwner) { age ->
            val index = storedTimes.indexOf(age)
            if (index != -1) {
                storedTimeSlider.value = index.toFloat()
                updateUI(
                    sampleRates[sampleRateSlider.value.toInt()],
                    age,
                    sampleRateLabel,
                    storedTimeLabel,
                    audioSizeLabel
                )
            }
        }

        sampleRateSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                // No action needed on start
            }

            override fun onStopTrackingTouch(slider: Slider) {
                val selectedSampleRate = sampleRates[slider.value.toInt()]
                viewModel.setSampleRate(selectedSampleRate)
                updateUI(
                    selectedSampleRate,
                    storedTimes[storedTimeSlider.value.toInt()],
                    sampleRateLabel,
                    storedTimeLabel,
                    audioSizeLabel
                )
            }
        })

        storedTimeSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                // No action needed on start
            }

            override fun onStopTrackingTouch(slider: Slider) {
                val selectedTime = storedTimes[slider.value.toInt()]
                viewModel.setMaxRecordingAge(selectedTime)
                // UI update will be handled by the observer
            }
        })


        return root
    }

    private fun updateUI(
        sampleRate: Int,
        storedTime: Int,
        sampleRateLabel: TextView,
        storedTimeLabel: TextView,
        audioSizeLabel: TextView
    ) {
        sampleRateLabel.text = "Sample Rate: $sampleRate Hz"
        storedTimeLabel.text = "Stored Time: ${storedTime / 60}h ${storedTime % 60}m"

        val sizePerMinute = viewModel.calculateSizePerMinute(sampleRate)
        val totalSize = sizePerMinute * storedTime

        audioSizeLabel.text = "Audio Size per Minute: ${"%.2f".format(sizePerMinute)} MB\nTotal Size for Stored Time: ${"%.2f".format(totalSize)} MB"

        // Restarting the service
        requireContext().stopService(Intent(requireContext(), AudioRecordingService::class.java))
        Toast.makeText(requireContext(), "Service restarting", Toast.LENGTH_LONG).show()

        val restartIntent = Intent(requireContext(), AudioRecordingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(restartIntent)
        } else {
            requireContext().startService(restartIntent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
