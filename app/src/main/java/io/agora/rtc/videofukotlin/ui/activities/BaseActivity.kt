package io.agora.rtc.videofukotlin.ui.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity

private const val REQUEST_CODE_ALL_PERMISSIONS : Int = 999

abstract class BaseActivity : AppCompatActivity() {
    private val requiredPermissions : Array<String> = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.INTERNET
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermission()
    }

    private fun checkPermission() {
        ActivityCompat.requestPermissions(
            this, requiredPermissions,
            REQUEST_CODE_ALL_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_CODE_ALL_PERMISSIONS) {
            return
        }

        grantResults.forEach {
            if (it != PackageManager.PERMISSION_GRANTED) {
                // May give a hint to let users know
                return
            }
        }

        onAllPermissionsGranted()
    }

    abstract fun onAllPermissionsGranted()
}