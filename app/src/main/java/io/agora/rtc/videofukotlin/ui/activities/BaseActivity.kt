package io.agora.rtc.videofukotlin.ui.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.widget.Toast

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

        val notGranted : ArrayList<String> = ArrayList()
        for (i in grantResults.indices) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                notGranted.add(permissions[i])
            }
        }

        if (notGranted.isEmpty()) {
            onAllPermissionsGranted()
        } else {
            Toast.makeText(this,
                buildPermissionHint(notGranted.toTypedArray()),
                Toast.LENGTH_LONG).show()
        }
    }

    private fun buildPermissionHint(permissions : Array<out String>) : String {
        var format = "Permissions required:"
        permissions.forEach {
            format = format + "\n" + it
        }

         return String.format(format, *permissions)
    }

    /**
     * Anything that should be done after all
     * requested permissions are granted.
     */
    abstract fun onAllPermissionsGranted()
}