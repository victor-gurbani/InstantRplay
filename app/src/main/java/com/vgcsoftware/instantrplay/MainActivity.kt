package com.vgcsoftware.instantrplay

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.view.Menu
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
import androidx.documentfile.provider.DocumentFile
import com.vgcsoftware.instantrplay.databinding.ActivityMainBinding
import com.vgcsoftware.instantrplay.ui.home.HomeViewModel
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val homeViewModel: HomeViewModel by viewModels()

    // Permission launcher for audio recording and storage access
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            // Create directory to store files // TODO: Check if this is necessary
            val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS), "InstantRplay")
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "InstantRplay")
            }
            if (!dir.exists()) {
                dir.mkdirs()
            }
            requestScopedStoragePermission()
            startAudioRecordingService()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup view binding for the main activity layout
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // setContentView(R.layout.activity_main) // TODO: Check differences

        // Set up the toolbar and Floating Action Button (FAB)
        setSupportActionBar(binding.appBarMain.toolbar)
        binding.appBarMain.fab.setOnClickListener { view ->
            Snackbar.make(view, "Converting to MP3", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.fab).show()
            saveLast(30)
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

        // Request permissions for audio recording and media storage access and notifications
        requestPermissions()


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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // TODO: request scoped permissions for the folder
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
        Log.d("saveLast", "Saving PCM files from the last $minutes minutes")
        // Directory containing PCM files
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS), "InstantRplay")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "InstantRplay")
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
            Snackbar.make(findViewById(android.R.id.content), "No PCM files found in the last $minutes minutes.", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.fab).show()
            return
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
            val wavFile = File(dir, "output.wav")
            Log.d("saveLast", "Converting WAV file to: ${wavFile.absolutePath}")
            rawToWave(tempPcmFile, wavFile)

            Log.d("saveLast", "WAV file saved successfully: ${wavFile.absolutePath}")
            // Add to MediaStore
            MediaScannerConnection.scanFile(this, arrayOf(wavFile.absolutePath), null, null)
        } catch (e: IOException) {
            Log.e("saveLast", "Error processing PCM files", e)
        } finally {
            // Clean up temporary PCM file
            if (tempPcmFile.exists()) {
                tempPcmFile.delete()
            }
        }
    }


    @Throws(IOException::class)
    fun rawToWave(rawFile: File, waveFile: File) {
        val rawData = ByteArray(rawFile.length().toInt())

        DataInputStream(FileInputStream(rawFile)).use { input ->
            input.readFully(rawData)
        }

        DataOutputStream(FileOutputStream(waveFile)).use { output ->
            // WAVE header
            // see http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
            writeString(output, "RIFF") // chunk id
            writeInt(output, 36 + rawData.size) // chunk size
            writeString(output, "WAVE") // format
            writeString(output, "fmt ") // subchunk 1 id
            writeInt(output, 16) // subchunk 1 size
            writeShort(output, 1.toShort()) // audio format (1 = PCM)
            writeShort(output, 1.toShort()) // number of channels
            writeInt(output, 44100) // sample rate
            writeInt(output, 44100 * 2) // byte rate
            writeShort(output, 2.toShort()) // block align
            writeShort(output, 16.toShort()) // bits per sample
            writeString(output, "data") // subchunk 2 id
            writeInt(output, rawData.size) // subchunk 2 size

            // Audio data (conversion big endian -> little endian)
            val shorts = ShortArray(rawData.size / 2)
            ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            val bytes = ByteBuffer.allocate(shorts.size * 2)
            for (s in shorts) {
                bytes.putShort(s)
            }

            output.write(fullyReadFileToBytes(rawFile))
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
            output.write(value[i].toInt())
        }
    }

}
