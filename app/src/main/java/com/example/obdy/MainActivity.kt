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
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) { }
            vm.chooseBackupFolder(uri.toString())
        }
    }
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
        vm.launchFolderPicker = { folderPicker.launch(null) }
        vm.askLocation = { askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
        vm.launchVoice = {
            try { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)) }
            catch (e: Exception) { vm.say("No speech recognition on this phone.") }
        }
        setContent {
            ObdyTheme {
                val keepOn = vm.keepScreenOn && vm.keepAwake
                val dash = false   // Driving mode (old Dashboard mode) works in portrait and landscape
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
            vm.maybeAutoBackup()
            handleOpen(intent)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Home-screen shortcuts open a screen directly. */
    private fun handleOpen(i: Intent?) {
        when (i?.getStringExtra("open")) {
            "live" -> { vm.overlay = null; vm.tab = 1 }
            "service" -> { vm.overlay = null; vm.tab = 4 }
            "scan" -> { vm.overlay = null; vm.tab = 2 }
            "fuses" -> { vm.fuseSel = null; vm.overlay = "fuses" }
            "chat", "lab" -> { vm.overlay = null; vm.tab = 5 }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpen(intent)
    }

    /** Every touch that is a tap (not a scroll) goes in the log with the screen it landed on. Taps on named buttons are also logged by name. */
    private var downX = 0f; private var downY = 0f
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        try {
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y }
                android.view.MotionEvent.ACTION_UP -> {
                    val moved = Math.hypot((ev.x - downX).toDouble(), (ev.y - downY).toDouble())
                    val where = vm.overlay ?: listOf("Home", "Live", "Faults", "Tools", "Car", "Lab").getOrElse(vm.tab) { "tab ${vm.tab}" }
                    AppLog.i(if (moved < 24) "[YOU] tap at ${ev.x.toInt()},${ev.y.toInt()} on $where" + (ScreenLog.labelAt(window.decorView, downX, downY)?.let { " -> \"$it\"" } ?: "") else "[YOU] swipe/scroll ${(ev.x - downX).toInt()},${(ev.y - downY).toInt()} on $where")
                }
            }
        } catch (_: Exception) { }
        return super.dispatchTouchEvent(ev)
    }

    private val shotHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val shotTick = object : Runnable {
        override fun run() {
            try { ScreenLog.snapshot(window.decorView, vm.overlay ?: listOf("Home", "Live", "Faults", "Tools", "Car", "Lab").getOrElse(vm.tab) { "tab ${vm.tab}" }) } catch (_: Throwable) {}
            shotHandler.postDelayed(this, 700)
        }
    }

    override fun onPause() { shotHandler.removeCallbacks(shotTick); AppLog.i("[APP] app went to the background"); super.onPause() }
    override fun onStop() { AppLog.i("[APP] app not visible"); AppLog.flush(); super.onStop() }
    override fun onDestroy() { AppLog.i("[APP] app closed (or rotated)"); AppLog.flush(); super.onDestroy() }

    override fun onResume() {
        AppLog.i("[APP] app in the foreground")
        super.onResume()
        shotHandler.removeCallbacks(shotTick); shotHandler.postDelayed(shotTick, 400)
        vm.refreshPermission()
        vm.autoConnectNow()
        vm.refreshUpdateState()
        vm.refreshChecked()
    }
}
