package com.vgcsoftware.instantrplay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.app.Service
import android.app.job.JobInfo
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
import android.widget.Toast
import java.io.ByteArrayOutputStream


class AudioRecordingService : Service() {

    /*private fun scheduleJob(context: Context) { // TODO: Implement this
        val componentName = ComponentName(context, MyJobService::class.java)
        val jobInfo = JobInfo.Builder(123, componentName)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)  // Keep job across reboots
            .setMinimumLatency(1 * 1000) // Start as soon as possible
            .build()

        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.schedule(jobInfo)
    }*/

    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private var isRecording = false
    private var errorOccurred = false

    private lateinit var audioRecord: AudioRecord
    private val bufferSizeInBytes: Int = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "AudioRecordingChannel"
        private const val CHUNK_DURATION_MS = 60_000L // 1 minute chunks
        private const val MAX_RECORDING_AGE_MS = 30 * 60 * 1000L // 30 minutes TODO: Implement logic to manage this
        private const val FILE_PREFIX = "audio_chunk_"
        private const val AUDIO_DIR = "InstantRplay" // Sub-directory for audio files
        private const val TAG = "AudioRecordingService"
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (!errorOccurred) {
            try {
                startForeground(NOTIFICATION_ID, createNotification(), FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting foreground service: ${e.message}", e)
                // Show notification
                showErrorNotification()
            }
        } else {
            // Show notification
            // showErrorNotification() // doubled
        }
        // scheduleOldFileDeletion() // will be called when a new file is saved

    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!errorOccurred) {
            try {
                startRecording()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting foreground rec service: ${e.message}", e)
                // Show notification
                showErrorNotification()
            }
        } else {
            // Show notification
            showErrorNotification()
        }

        // If your service was restarted after being killed
        if (flags == START_FLAG_REDELIVERY) {
            // Handle the restart logic
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        errorOccurred = false
        coroutineScope.cancel()
         // Schedule the job to restart the service
        // scheduleJob(this) // TODO: Implement this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        /*val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Recording Service")
            .setContentText("Service is running")
            .setSmallIcon(R.drawable.ic_notification) // Ensure you have an icon resource
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Makes the notification non-dismissible
            .build()*/

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Instnat Rplay Service")
            .setContentText("Currently active")
            .setSmallIcon(R.drawable.ic_microphone)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showErrorNotification() {
        // Stop the recording and release resources
        errorOccurred = true
        stopRecording()
        coroutineScope.cancel()

        Log.d(TAG, "Showing error notification, after killing everything. Recording? $isRecording")
        // Show toast
        Toast.makeText(this, "Error occurred in InstantRplay, check Notification to re-start.", Toast.LENGTH_LONG).show()
        val restartIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("START_VALUE", "SERVICE_ERROR_NOTIFICATION")
        }
        val restartPendingIntent = PendingIntent.getActivity(this, 0, restartIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Service Error")
            .setContentText("Click to open the app")
            .setSmallIcon(R.drawable.ic_microphone) // Ensure you have an icon resource
            .setContentIntent(restartPendingIntent)
            .setAutoCancel(true) // Dismisses the notification when clicked
            .setOngoing(true) // Makes the notification undismissable until clicked
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)


        // Stop the service
        stopSelf()
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Instant Rplay Service",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startRecording() {
        if (isRecording) return
        Log.d("AudioRecordingService", "Recording started")
        Log.d("AudioRecordingService", "Max recording age: $MAX_RECORDING_AGE_MS")
        // this.deleteOldFiles()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            // Lets do this:

            return
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeInBytes
        )


        audioRecord.startRecording()
        isRecording = true

        coroutineScope.launch {
            try {
                recordAndSaveAudio()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        audioRecord.stop()
        audioRecord.release()
    }

    private suspend fun recordAndSaveAudio() { // TODO: run this when stoppin gaudio to prevent loss and when exporting to make it exact
        val buffer = ByteArray(bufferSizeInBytes)
        val chunkBuffer = ByteArrayOutputStream()  // Accumulate data over chunk duration
        var startTime = System.currentTimeMillis()

        Log.d("AudioRecordingService", "Recording and saving audio...")
        while (isRecording) {

            val bytesRead = audioRecord.read(buffer, 0, buffer.size)
            chunkBuffer.write(buffer, 0, bytesRead) // Accumulate data

            if (System.currentTimeMillis() - startTime >= CHUNK_DURATION_MS) {
                saveChunkToStorage(chunkBuffer.toByteArray(), chunkBuffer.size())
                chunkBuffer.reset()  // Clear the buffer for the next chunk
                startTime = System.currentTimeMillis()
            }
        }

        // Handle any remaining data
        if (chunkBuffer.size() > 0) {
            saveChunkToStorage(chunkBuffer.toByteArray(), chunkBuffer.size())
        }
    }

    private fun saveChunkToStorage(buffer: ByteArray, bytesRead: Int) {
        val file: File

        Log.d("AudioRecordingService", "Saving chunk to storage with size: $bytesRead")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Recordings/$AUDIO_DIR") // TODO: Implement directory management like other parts of the code
                put(MediaStore.Audio.Media.DISPLAY_NAME, "${FILE_PREFIX}${UUID.randomUUID()}.pcm")  // Set the actual file name
                put(MediaStore.Audio.Media.TITLE, "${FILE_PREFIX}${UUID.randomUUID()}")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav") // fake mime type to allow a easy give permissions
                put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            }

            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(buffer, 0, bytesRead)
                }

                val absolutePath = getAbsolutePathFromContentUri(this, Uri.parse(it.toString()))
                absolutePath?.let { path ->
                    val file2save = File(path)
                    file2save.setLastModified(System.currentTimeMillis())
                }
                Log.d(TAG, "File saved successfully. Absolute path: $absolutePath. In MediaStore: $it. ")
            }
        } else {
            // Fallback for older versions where we can directly write to external storage
            val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS), AUDIO_DIR)
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), AUDIO_DIR)
            }
            if (!dir.exists()) dir.mkdirs()

            val fileName = "${FILE_PREFIX}${UUID.randomUUID()}.pcm"
            file = File(dir, fileName)

            FileOutputStream(file).use { outputStream ->
                outputStream.write(buffer, 0, bytesRead)
            }

            file.setLastModified(System.currentTimeMillis()) // Ensuring correct timestamp
            Log.d(TAG, "File saved successfully: ${file.absolutePath}")

        }

        this.deleteOldFiles()


    }

    private fun getAbsolutePathFromContentUri(context: Context, contentUri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = context.contentResolver.query(contentUri, projection, null, null, null)

        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                return it.getString(columnIndex)
            }
        }

        return null
    }

    private fun scheduleOldFileDeletion() {
        coroutineScope.launch {
            while (isRecording) {
                deleteOldFiles()
                delay(60_000L) // Check every minute
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteOldFiles() {
        val contentResolver = applicationContext.contentResolver
        val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val currentTime = System.currentTimeMillis()

      // Determine the directory based on the Android version
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS), AUDIO_DIR)
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), AUDIO_DIR)
        }

        Log.d(TAG, "Checking for old files in: ${dir.absolutePath}")

        // Selection criteria to filter files by directory and file prefix
        val selection: String
        val selectionArgs: Array<String>

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 (API level 29) and above
            selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
            selectionArgs = arrayOf(
                "${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Environment.DIRECTORY_RECORDINGS else Environment.DIRECTORY_MUSIC}/$AUDIO_DIR/",
                "$FILE_PREFIX%"
            )

        } else {
            // For older Android versions
            selection = "${MediaStore.Audio.Media.DATA} LIKE ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
            selectionArgs = arrayOf("${dir.absolutePath}/%", "$FILE_PREFIX%")
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        // Query the MediaStore for files matching the criteria
        val cursor = contentResolver.query(audioUri, projection, selection, selectionArgs, null)

        cursor?.use {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val fileId = cursor.getLong(idColumn)
                val fileName = cursor.getString(nameColumn)
                val dateModified = cursor.getLong(dateModifiedColumn) * 1000L // Convert to milliseconds

                val fileAge = currentTime - dateModified
                if (fileAge > MAX_RECORDING_AGE_MS && dateModified > 0) {
                    val deleteUri = ContentUris.withAppendedId(audioUri, fileId)
                    var deleted = false
                    try {
                        deleted = contentResolver.delete(deleteUri, null, null) > 0
                    } catch (e: RecoverableSecurityException) {
                        // Throw the RecoverableSecurityException to be handled
                        // TODO: say smth on error
                        Log.e(TAG, "RecoverableSecurityException: ${e.message}", e)
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException: ${e.message}", e)
                    }
                    Log.d(TAG, "Delete operation finished for file: $fileName with age $fileAge, Result: $deleted")
                    // now log the same but with absolute path
                    val absolutePath = getAbsolutePathFromContentUri(this, deleteUri)
                    // Log.d(TAG, "File: $absolutePath, Age: $fileAge")
                }
            }
        }


        // Continue with file system

        if (cursor == null && dir.exists()) {
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith(FILE_PREFIX)) {
                    val fileAge = currentTime - file.lastModified()
                    Log.d(TAG, "File: ${file.name}, Age: $fileAge")
                    if (fileAge > MAX_RECORDING_AGE_MS && file.lastModified() > 0L) {
                        Log.d("AudioRecordingService", "Deleting file: ${file.name}")
                        val deleted = file.delete()
                        Log.d(TAG, "File system delete operation for file: ${file.name}, Result: $deleted")
                    }
                }
            }
        }
    }




}
