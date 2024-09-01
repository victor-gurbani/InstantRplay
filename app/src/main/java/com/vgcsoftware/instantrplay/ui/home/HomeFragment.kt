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
import androidx.lifecycle.ViewModelProvider
import com.vgcsoftware.instantrplay.AudioRecordingService
import com.vgcsoftware.instantrplay.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: HomeViewModel
    private val sampleRates = listOf(8000, 16000, 32000, 44100, 48000)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout using ViewBinding
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Bind the ViewModel
        viewModel = ViewModelProvider(this).get(HomeViewModel::class.java)

        // UI elements
        val sampleRateLabel: TextView = binding.sampleRateLabel
        val audioSizeLabel: TextView = binding.audioSizeLabel
        val sampleRateSlider: SeekBar = binding.sampleRateSlider

        // Set initial values
        sampleRateSlider.progress = sampleRates.indexOf(viewModel.getSampleRate())
        updateUI(sampleRates[sampleRateSlider.progress], sampleRateLabel, audioSizeLabel)

        // Listen for slider changes
        sampleRateSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {

            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Show toast saying older audios will sound altered when chaning sample rate
                Toast.makeText(requireContext(), "Older audios will sound altered if changed. Current value: ${sampleRates[seekBar!!.progress]}", Toast.LENGTH_LONG).show()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val selectedSampleRate = sampleRates[seekBar?.progress ?: 0]
                viewModel.setSampleRate(selectedSampleRate)
                updateUI(selectedSampleRate, sampleRateLabel, audioSizeLabel)
            }
        })

        return root
    }

    private fun updateUI(sampleRate: Int, sampleRateLabel: TextView, audioSizeLabel: TextView) {
        sampleRateLabel.text = "Sample Rate: $sampleRate Hz"
        val sizePerMinute = viewModel.calculateSizePerMinute(sampleRate)
        // Show rounded result to 2 decimal places
        audioSizeLabel.text = "Audio Size per Minute: ${"%.2f".format(sizePerMinute)} MB\nAudio Size per Hour: ${"%.2f".format(sizePerMinute * 60)} MB"

        requireContext().stopService(Intent(requireContext(), AudioRecordingService::class.java))
        // Show toast
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
