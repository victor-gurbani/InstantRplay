import android.app.Service
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.os.Build
import com.vgcsoftware.instantrplay.AudioRecordingService
// TODO: Add to androidmanifest.xml <service android:name=".MyJobService" android:permission="android.permission.BIND_JOB_SERVICE" />
class MyJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val serviceIntent = Intent(this, AudioRecordingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }
}
