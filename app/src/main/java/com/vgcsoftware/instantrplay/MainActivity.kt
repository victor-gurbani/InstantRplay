package com.vgcsoftware.instantrplay

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.documentfile.provider.DocumentFile
import com.vgcsoftware.instantrplay.databinding.ActivityMainBinding
import com.vgcsoftware.instantrplay.ui.home.HomeViewModel
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import android.widget.PopupMenu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.widget.EditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val homeViewModel: HomeViewModel by viewModels()

    private var SAMPLE_RATE = 32000

    companion object {
        //
    }
    private var isAlarmScheduled = false

    // Permission launcher for audio recording and storage access
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            // Create directory to store files
            val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS), "InstantRplay")
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "InstantRplay")
            }
            if (!dir.exists()) {
                dir.mkdirs()
            }
            while(!dir.exists()) {
                Log.d("MainActivity", "Waiting for directory to be created")
            }
            requestScopedStoragePermission()
            scheduleServiceRestart()
            startAudioRecordingService()
            scheduleServiceRestart() // again just in case
        } else {
            Snackbar.make(findViewById(android.R.id.content), "Permissions required for audio recording", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun requestScopedStoragePermission() {
        // Check if a directory URI is already saved
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedUriString = sharedPreferences.getString("selected_directory_uri", null)

        if (savedUriString != null) {
            // If a URI is already saved, try accessing it
            val savedUri = Uri.parse(savedUriString)
            accessSavedDirectory(savedUri)
        } else {
            // If no URI is saved, open the directory picker for the user to select a folder
            openDirectoryPicker()
        }
    }
    // Activity result launcher for the directory picker
    private val requestDirectoryPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = result.data?.data
                if (uri != null) {
                    handleDirectorySelection(uri)
                }
            }
        }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
        }
    }

    private fun scheduleServiceRestart() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AudioRecordingService::class.java)
        val pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        if (!isAlarmScheduled) {
            isAlarmScheduled = true
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 60 * 1000,  // Start after 1 minute
                60 * 1000,  // Repeat every 1 minute
                pendingIntent
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup view binding for the main activity layout
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // setContentView(R.layout.activity_main) // TODO: Check differences

        // Get the sample rate from SharedPreferences
        SAMPLE_RATE = PreferencesHelper.getSampleRate(this)
        Log.d("MainActivity", "Sample rate: $SAMPLE_RATE")

        if (intent.getStringExtra("START_VALUE") == "SERVICE_ERROR_NOTIFICATION") {
            stopService(Intent(this, AudioRecordingService::class.java))
            // Show toast
            Toast.makeText(this, "Service restarting", Toast.LENGTH_LONG).show()
            val restartIntent = Intent(this, AudioRecordingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
            finishAndRemoveTask()
        }

        // Set up the toolbar and Floating Action Button (FAB)
        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.fab.setOnTouchListener(object : View.OnTouchListener {
            private var isLongPress = false

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isLongPress = true
                        v?.postDelayed({
                            if (isLongPress) {
                                showPopupMenu(v)
                            }
                        }, 140) // Long press duration (140ms)
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isLongPress) return false
                        if (event.eventTime - event.downTime < 140) {
                            // Handle short tap
                            saveLast(30) // Call saveLast with default value if it's a quick tap
                            v?.performClick() // Ensure accessibility services can handle click event
                        }
                        isLongPress = false
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        isLongPress = false
                    }
                }
                return true
            }
        })

        // Override performClick() to handle the accessibility click event
        binding.appBarMain.fab.setOnClickListener {
            saveLast(30) // Perform the click action here for short taps
        }



        // Initialize navigation drawer and controller
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        requestIgnoreBatteryOptimizations()

        // Request permissions for audio recording and media storage access and notifications
        requestPermissions()


    }

    private fun showPopupMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.fab_menu, popup.menu)

        // Handle menu item clicks
        popup.setOnMenuItemClickListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                R.id.action_save_1_min -> saveLast(1)
                R.id.action_save_3_min -> saveLast(3)
                R.id.action_save_5_min -> saveLast(5)
                R.id.action_save_15_min -> saveLast(15)
                R.id.action_save_30_min -> saveLast(30)
                R.id.action_save_60_min -> saveLast(60)
                R.id.action_save_120_min -> saveLast(120)
                R.id.action_save_custom -> showCustomInputDialog()
                R.id.action_save_custom_time_frame -> showCustomTimeFrameDialog()
                else -> false
            }
            true
        }

        popup.show()
    }
    private fun showCustomInputDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enter custom minutes")

        // Set up the input
        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        builder.setView(input)

        // Set up the buttons
        builder.setPositiveButton("OK") { dialog, which ->
            val customTime = input.text.toString().toIntOrNull()
            if (customTime != null && customTime > 0) {
                saveLast(customTime)
            } else {
                Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun showCustomTimeFrameDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_custom_time_frame, null)
        val builder = AlertDialog.Builder(this)
            .setTitle("Select Time Frame")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)

        val dialog = builder.create()
        dialog.show()

        val btnStartDate = dialogView.findViewById<Button>(R.id.btn_start_date)
        val btnStartTime = dialogView.findViewById<Button>(R.id.btn_start_time)
        val tvStartTime = dialogView.findViewById<TextView>(R.id.tv_start_time)
        val btnEndDate = dialogView.findViewById<Button>(R.id.btn_end_date)
        val btnEndTime = dialogView.findViewById<Button>(R.id.btn_end_time)
        val tvEndTime = dialogView.findViewById<TextView>(R.id.tv_end_time)

        val startCalendar = Calendar.getInstance()
        startCalendar.set(Calendar.SECOND, 0)
        startCalendar.set(Calendar.MILLISECOND, 0)
        val endCalendar = Calendar.getInstance()
        endCalendar.set(Calendar.SECOND, 0)
        endCalendar.set(Calendar.MILLISECOND, 0)

        btnStartDate.setOnClickListener {
            val datePickerDialog = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    startCalendar.set(Calendar.YEAR, year)
                    startCalendar.set(Calendar.MONTH, month)
                    startCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    tvStartTime.text = sdf.format(startCalendar.time)
                },
                startCalendar.get(Calendar.YEAR),
                startCalendar.get(Calendar.MONTH),
                startCalendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.show()
        }

        btnStartTime.setOnClickListener {
            val timePickerDialog = TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    startCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    startCalendar.set(Calendar.MINUTE, minute)
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    tvStartTime.text = sdf.format(startCalendar.time)
                },
                startCalendar.get(Calendar.HOUR_OF_DAY),
                startCalendar.get(Calendar.MINUTE),
                true
            )
            timePickerDialog.show()
        }

        btnEndDate.setOnClickListener {
            val datePickerDialog = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    endCalendar.set(Calendar.YEAR, year)
                    endCalendar.set(Calendar.MONTH, month)
                    endCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    tvEndTime.text = sdf.format(endCalendar.time)
                },
                endCalendar.get(Calendar.YEAR),
                endCalendar.get(Calendar.MONTH),
                endCalendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.show()
        }

        btnEndTime.setOnClickListener {
            val timePickerDialog = TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    endCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    endCalendar.set(Calendar.MINUTE, minute)
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    tvEndTime.text = sdf.format(endCalendar.time)
                },
                endCalendar.get(Calendar.HOUR_OF_DAY),
                endCalendar.get(Calendar.MINUTE),
                true
            )
            timePickerDialog.show()
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val startTime = startCalendar.timeInMillis
            val endTime = endCalendar.timeInMillis

            if (startTime >= endTime) {
                Toast.makeText(this, "Start time must be before end time", Toast.LENGTH_SHORT).show()
            } else {
                saveBetween(startTime, endTime)
                dialog.dismiss()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    @RequiresApi(Build.VERSION_CODES.P) // TIRAMISU for Foreground Service
    private fun requestPermissions() {
        // Define the required permissions
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.FOREGROUND_SERVICE,  )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        }

        // Launch the permission request
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startAudioRecordingService() {
        // Start the foreground service for audio recording
        val intent = Intent(this, AudioRecordingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // Opens the directory picker to allow the user to select a directory
    @RequiresApi(Build.VERSION_CODES.O)
    private fun openDirectoryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            // Optional: Specify the initial directory that should be opened
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse("content://com.android.externalstorage.documents/document/primary:Recordings/InstantRplay"))
        }


        requestDirectoryPicker.launch(intent)
    }

    // Handle the selected directory URI after the user selects a directory
    private fun handleDirectorySelection(uri: Uri) {

        // Take persistent permission for the selected directory
        val contentResolver: ContentResolver = contentResolver
        val takeFlags: Int = (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        contentResolver.takePersistableUriPermission(uri, takeFlags)

        // Save the URI to SharedPreferences for future access
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("selected_directory_uri", uri.toString()).apply()

        Log.d(TAG, "Directory selected: $uri")

        // Now you can perform operations in this directory
        listFilesInDirectory(uri)
    }

    // Access the previously saved directory
    private fun accessSavedDirectory(uri: Uri) {
        val contentResolver: ContentResolver = contentResolver

        // Grant URI permissions again
        val takeFlags: Int = (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
            Log.d(TAG, "Persisted URI accessed: $uri")

            // Perform operations in the saved directory
            listFilesInDirectory(uri)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to persist permissions for URI", e)
            // If we fail to access the saved URI, open the picker again
            openDirectoryPicker()
        }
    }

    // List all files in the selected directory (for demonstration purposes)
    private fun listFilesInDirectory(uri: Uri) {
        val documentFile = DocumentFile.fromTreeUri(this, uri)
        documentFile?.listFiles()?.forEach { file ->
            Log.d(TAG, "File found: ${file.name}")
        }
    }




    fun saveLast(minutes: Int) {
        val maxRecordingAge = PreferencesHelper.getMaxRecordingAge(this)
        if (minutes > maxRecordingAge) {
            PreferencesHelper.setMaxRecordingAge(this, minutes)
            AlertDialog.Builder(this)
                .setTitle("Time Window Adjusted")
                .setMessage("Couldn't save last $minutes minutes due to time frame settings - it will be auto adjusted.")
                .setPositiveButton("OK", null)
                .setCancelable(false)
                .show()
            performSaveLast(minutes)
        } else {
            performSaveLast(minutes)
        }
    }

    private fun performSaveLast(minutes: Int) {

        Toast.makeText(this, "Saving audio until now...", Toast.LENGTH_SHORT).show()
        // Restarting the service
        stopService(Intent(this, AudioRecordingService::class.java))

        val restartIntent = Intent(this, AudioRecordingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }

        // Launch a coroutine for the heavy file operations
        lifecycleScope.launch(Dispatchers.IO) {
            // Log.d("saveLast", "Saving PCM files from the last $minutes minutes")
            
            Log.d("saveLast", "Saving PCM files from the last $minutes minutes (Background Thread)")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Saving last $minutes min", Toast.LENGTH_LONG).show()
            }

            // Directory containing PCM files
            val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS), "InstantRplay")
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "InstantRplay")
            }
            val beforeDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            }

            // Current time
            val currentTime = System.currentTimeMillis()

            // Filter PCM files less than 'minutes' old
            // Supposedly filextension is pcm but it is saved as wav
            val filteredFiles = dir.listFiles { file ->
                file.extension == "wav" && file.isFile &&
                        (currentTime - file.lastModified() <= TimeUnit.MINUTES.toMillis(minutes.toLong()))
            }?.sortedBy { it.lastModified() } // Sort by last modified time

            if (filteredFiles.isNullOrEmpty()) {
                Log.d("saveLast", "No PCM files found in the last $minutes minutes.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "No PCM files found in the last $minutes minutes.", Toast.LENGTH_LONG).show()
                }
                return@launch // Exit this coroutine
            }

            // Log the filtered files
            filteredFiles.forEach { file ->
                Log.d("saveLast", "Filtered file: ${file.name}")
            }

            val inAppDir = File(filesDir, "InstantRplay")
            // Ensure the directory exists
            if (!inAppDir.exists()) {
                inAppDir.mkdirs()
            }
            // Create a temporary PCM file to hold concatenated data
            val tempPcmFile = File(inAppDir, "temp_concatenated.pcm")

            try {
                FileOutputStream(tempPcmFile).use { output ->
                    for (file in filteredFiles) {
                        FileInputStream(file).use { input ->
                            input.copyTo(output)
                        }
                    }
                }

                // Convert the concatenated PCM file to WAV
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = currentTime
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH) + 1 // Month is 0-based
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                val second = calendar.get(Calendar.SECOND)
                val formattedTime = String.format("%04d-%02d-%02d_%02d-%02d-%02d", year, month, day, hour, minute, second)

                Log.d("saveLast", "Formatted time: $formattedTime")
                val wavFile = File(beforeDir, "InstantRplay_${formattedTime}_${minutes}min.wav")
                Log.d("saveLast", "Converting tempPCM file to: ${wavFile.absolutePath}")
                rawToWave(tempPcmFile, wavFile) // This will run on Dispatchers.IO

                Log.d("saveLast", "WAV file saved successfully: ${wavFile.absolutePath}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Finished Processing InstantRplay! Check it out in: ${wavFile.absolutePath}", Toast.LENGTH_LONG).show()
                }
                // Add to MediaStore
                MediaScannerConnection.scanFile(this@MainActivity, arrayOf(wavFile.absolutePath), null, null)
            } catch (e: IOException) {
                Log.e("saveLast", "Error processing PCM files", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error saving audio: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                // Clean up temporary PCM file
                if (tempPcmFile.exists()) {
                    tempPcmFile.delete()
                }
            }
        }
    }

    fun saveBetween(startTime: Long, endTime: Long) {
        Toast.makeText(this, "Saving audio between selected times...", Toast.LENGTH_SHORT).show()

        // Launch a coroutine for the heavy file operations
        lifecycleScope.launch(Dispatchers.IO) {
            Log.d("saveBetween", "Saving PCM files from $startTime to $endTime (Background Thread)")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Saving audio from specified time frame", Toast.LENGTH_LONG).show()
            }

            // Directory containing PCM files
            val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS), "InstantRplay")
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "InstantRplay")
            }
            val beforeDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            }

            // Filter PCM files within the selected time frame
            val filteredFiles = dir.listFiles { file ->
                file.extension == "pcm" && file.isFile &&
                        (file.lastModified() >= startTime && file.lastModified() <= endTime)
            }?.sortedBy { it.lastModified() } // Sort by last modified time

            if (filteredFiles.isNullOrEmpty()) {
                Log.d("saveBetween", "No PCM files found in the specified time frame.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "No PCM files found in the specified time frame.", Toast.LENGTH_LONG).show()
                }
                return@launch // Exit this coroutine
            }

            // Log the filtered files
            filteredFiles.forEach { file ->
                Log.d("saveBetween", "Filtered file: ${file.name}")
            }

            val inAppDir = File(filesDir, "InstantRplay")
            // Ensure the directory exists
            if (!inAppDir.exists()) {
                inAppDir.mkdirs()
            }
            // Create a temporary PCM file to hold concatenated data
            val tempPcmFile = File(inAppDir, "temp_concatenated.pcm")

            try {
                FileOutputStream(tempPcmFile).use { output ->
                    for (file in filteredFiles) {
                        FileInputStream(file).use { input ->
                            input.copyTo(output)
                        }
                    }
                }

                // Convert the concatenated PCM file to WAV
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = System.currentTimeMillis()
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH) + 1 // Month is 0-based
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                val second = calendar.get(Calendar.SECOND)
                val formattedTime = String.format("%04d-%02d-%02d_%02d-%02d-%02d", year, month, day, hour, minute, second)

                Log.d("saveBetween", "Formatted time: $formattedTime")
                val wavFile = File(beforeDir, "InstantRplay_${formattedTime}_custom.wav")
                Log.d("saveBetween", "Converting tempPCM file to: ${wavFile.absolutePath}")
                rawToWave(tempPcmFile, wavFile) // This will run on Dispatchers.IO

                Log.d("saveBetween", "WAV file saved successfully: ${wavFile.absolutePath}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Finished Processing InstantRplay! Check it out in: ${wavFile.absolutePath}", Toast.LENGTH_LONG).show()
                }
                // Add to MediaStore
                MediaScannerConnection.scanFile(this@MainActivity, arrayOf(wavFile.absolutePath), null, null)
            } catch (e: IOException) {
                Log.e("saveBetween", "Error processing PCM files", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error saving audio: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                // Clean up temporary PCM file
                if (tempPcmFile.exists()) {
                    tempPcmFile.delete()
                }
            }
        }
    }

    @Throws(IOException::class)
    fun rawToWave(rawFile: File, waveFile: File) {
        val bufferSize = 1024 * 4 // Process 4KB at a time
        val buffer = ByteArray(bufferSize)

        DataOutputStream(FileOutputStream(waveFile)).use { output ->
            // WAVE header
            writeString(output, "RIFF") // chunk id
            writeInt(output, 36 + rawFile.length().toInt()) // chunk size
            writeString(output, "WAVE") // format
            writeString(output, "fmt ") // subchunk 1 id
            writeInt(output, 16) // subchunk 1 size
            writeShort(output, 1.toShort()) // audio format (1 = PCM)
            writeShort(output, 1.toShort()) // number of channels
            writeInt(output, SAMPLE_RATE) // sample rate
            writeInt(output, SAMPLE_RATE * 2) // byte rate
            writeShort(output, 2.toShort()) // block align
            writeShort(output, 16.toShort()) // bits per sample
            writeString(output, "data") // subchunk 2 id
            writeInt(output, rawFile.length().toInt()) // subchunk 2 size

            // Process the file in chunks to avoid OutOfMemoryError
            DataInputStream(FileInputStream(rawFile)).use { input ->
                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break

                    val shorts = ShortArray(bytesRead / 2)
                    ByteBuffer.wrap(buffer, 0, bytesRead).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(shorts)

                    val byteBuffer = ByteBuffer.allocate(shorts.size * 2)
                    for (s in shorts) {
                        byteBuffer.putShort(s)
                    }

                    output.write(byteBuffer.array(), 0, shorts.size * 2)
                }
            }
        }
    }


    @Throws(IOException::class)
    fun fullyReadFileToBytes(file: File): ByteArray {
        val size = file.length().toInt()
        val bytes = ByteArray(size)
        val tmpBuff = ByteArray(size)

        FileInputStream(file).use { fis ->
            var read = fis.read(bytes, 0, size)
            if (read < size) {
                var remain = size - read
                while (remain > 0) {
                    read = fis.read(tmpBuff, 0, remain)
                    System.arraycopy(tmpBuff, 0, bytes, size - remain, read)
                    remain -= read
                }
            }
        }

        return bytes
    }

    @Throws(IOException::class)
    fun writeInt(output: DataOutputStream, value: Int) {
        output.write(value shr 0)
        output.write(value shr 8)
        output.write(value shr 16)
        output.write(value shr 24)
    }

    @Throws(IOException::class)
    fun writeShort(output: DataOutputStream, value: Short) {
        output.write(value.toInt() shr 0)
        output.write(value.toInt() shr 8)
    }

    @Throws(IOException::class)
    fun writeString(output: DataOutputStream, value: String) {
        for (i in value.indices) {
            output.write(value[i].code)
        }
    }

}
