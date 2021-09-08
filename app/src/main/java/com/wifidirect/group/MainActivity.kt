package com.wifidirect.group

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.wifidirect.group.receive.DirectReceiveActivity
import com.wifidirect.group.send.DirectSendActivity
import com.wifidirect.group.utils.ToolKit
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "WifiDirectGroup传输"

        btnSend.setOnClickListener {
            if (ToolKit.isWifiEnabled(this)) {
                if (ToolKit.isLocationServiceEnable(this)) {
                    if (checkLocationPermission()) {
                        startActivity(Intent(this, DirectSendActivity::class.java))
                    } else {
                        val locationPermission = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        ActivityCompat.requestPermissions(this, locationPermission, 0x01)
                    }
                } else {
                    ToolKit.showToast(this, "please open 位置服务")
                }
            } else {
                ToolKit.showToast(this, "please open wifi")
            }
        }
        btnReceive.setOnClickListener {
            if (ToolKit.isWifiEnabled(this)) {
                if (checkLocationPermission()) {
                    startActivity(Intent(this, DirectReceiveActivity::class.java))
                } else {
                    val locationPermission = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    ActivityCompat.requestPermissions(this, locationPermission, 0x01)
                }
            } else {
                ToolKit.showToast(this, "please open wifi")
            }
        }
    }

    private fun checkLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            fine == PackageManager.PERMISSION_GRANTED && coarse == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0x01) {
            if (grantResults.isNotEmpty() && grantResults.size == 2) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    ToolKit.showToast(this, "location permission request success")
                } else {
                    ToolKit.showToast(this, "need location permission")
                }
            }
        }
    }
}