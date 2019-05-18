package io.agora.rtc.videofukotlin.ui.activities

import android.os.Bundle
import io.agora.rtc.videofukotlin.R

class MainActivity : BaseActivity() {
    override fun onAllPermissionsGranted() {
        // TODO
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
    }
}