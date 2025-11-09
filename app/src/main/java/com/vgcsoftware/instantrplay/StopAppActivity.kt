package com.vgcsoftware.instantrplay

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

class StopAppActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Toast.makeText(this, R.string.stop_shortcut_feedback, Toast.LENGTH_SHORT).show()
        AppShutdownManager.requestAppTermination(this)
    }
}
