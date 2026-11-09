package com.example.obdy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat
import com.example.obdy.ui.ObdyRoot
import com.example.obdy.ui.ObdyTheme

class MainActivity : AppCompatActivity() {
    private val vm: ObdyVm by viewModels()

    private val btPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshPermission() }
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun askBluetooth() {
        val perms = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        btPermissions.launch(perms)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.createChannels(this)
        vm.refreshPermission()
        setContent {
            ObdyTheme {
                val keepOn = vm.keepScreenOn
                SideEffect {
                    if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                ObdyRoot(vm, ::askBluetooth)
            }
        }
        if (savedInstanceState == null) {
            vm.checkServiceNotice()
            vm.autoConnectNow()
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermission()
    }
}
