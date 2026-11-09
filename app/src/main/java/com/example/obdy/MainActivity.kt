package com.example.obdy

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
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
    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { vm.pickerResult(it) }
    private val voice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (t != null) vm.voiceResult(t)
    }

    private fun askBluetooth() {
        val perms = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        btPermissions.launch(perms)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.createChannels(this)
        vm.refreshPermission()
        vm.launchPicker = { mime -> picker.launch(mime) }
        vm.askLocation = { askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
        vm.launchVoice = {
            try { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)) }
            catch (e: Exception) { vm.say("No speech recognition on this phone.") }
        }
        setContent {
            ObdyTheme {
                val keepOn = vm.keepScreenOn || vm.overlay == "dash"
                val dash = vm.overlay == "dash"
                SideEffect {
                    requestedOrientation = if (dash) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                ObdyRoot(vm, ::askBluetooth)
            }
        }
        if (savedInstanceState == null) {
            vm.checkServiceNotice()
            vm.autoConnectNow()
            vm.autoCheckUpdate()
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermission()
        vm.refreshUpdateState()
    }
}
