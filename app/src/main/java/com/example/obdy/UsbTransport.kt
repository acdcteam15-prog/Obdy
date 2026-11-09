package com.example.obdy

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

object UsbHost {
    /** USB serial adapters plugged into the phone (FTDI, CH340, CP210x, PL2303, CDC). */
    fun list(ctx: Context): List<UsbSerialDriver> = try {
        UsbSerialProber.getDefaultProber().findAllDrivers(ctx.getSystemService(Context.USB_SERVICE) as UsbManager)
    } catch (e: Exception) { emptyList() }

    fun label(d: UsbSerialDriver): String {
        val dev = d.device
        val name = try { dev.productName } catch (e: Exception) { null }
        return (if (name.isNullOrBlank()) "USB cable" else name) + " (%04X:%04X)".format(dev.vendorId, dev.productId)
    }
}

/** An ELM327 / STN OBD cable on the phone's USB-OTG port. The speed is found by itself (38400, 115200, 9600, 500000). */
class UsbTransport(private val ctx: Context, private val driver: UsbSerialDriver) : Transport {
    override val label: String get() = "USB: " + UsbHost.label(driver)
    private var port: UsbSerialPort? = null
    var baud: Int = 0; private set

    private suspend fun askPermission(manager: UsbManager, dev: UsbDevice) {
        val action = "${ctx.packageName}.USB_PERMISSION"
        val done = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { done.complete(i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) }
        }
        ContextCompat.registerReceiver(ctx, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(ctx, 0, Intent(action).setPackage(ctx.packageName), flags)
            manager.requestPermission(dev, pi)
            val ok = withTimeoutOrNull(30_000L) { done.await() } ?: false
            if (!ok) throw IOException("USB permission was not granted. Press Connect again and tap OK on the Android question.")
        } finally {
            try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    override suspend fun open() {
        val manager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        val dev = driver.device
        if (!manager.hasPermission(dev)) askPermission(manager, dev)
        val conn = manager.openDevice(dev) ?: throw IOException("Android could not open the USB device.")
        withContext(Dispatchers.IO) {
            val p = driver.ports[0]
            p.open(conn)
            port = p
            try { p.setDTR(true); p.setRTS(true) } catch (_: Exception) {}
            var found = 0
            for (b in listOf(38400, 115200, 9600, 500000)) {
                p.setParameters(b, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                if (probe(p)) { found = b; break }
            }
            if (found == 0) {
                close()
                throw IOException("The cable is connected but the adapter does not answer at any speed. Is it an ELM327 OBD cable, plugged into the car, with the ignition on?")
            }
            baud = found
            AppLog.i("USB cable answers at $found baud")
        }
    }

    private fun probe(p: UsbSerialPort): Boolean {
        try {
            val junk = ByteArray(256); while (p.read(junk, 30) > 0) { }
            p.write("ATI\r".toByteArray(Charsets.US_ASCII), 1000)
            val sb = StringBuilder(); val buf = ByteArray(256)
            val end = System.currentTimeMillis() + 1500
            while (System.currentTimeMillis() < end) {
                val n = p.read(buf, 100)
                if (n > 0) { sb.append(String(buf, 0, n, Charsets.US_ASCII)); if (sb.indexOf(">") >= 0) break }
            }
            val t = sb.toString().uppercase()
            AppLog.i("USB probe: " + t.replace('\r', ' ').trim())
            return t.contains("ELM") || t.contains("STN") || t.contains("OBD")
        } catch (e: Exception) { return false }
    }

    override suspend fun write(text: String) = withContext(Dispatchers.IO) {
        val p = port ?: throw IOException("Not connected")
        p.write((text + "\r").toByteArray(Charsets.US_ASCII), 2000)
    }

    override suspend fun readUntilPrompt(timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val p = port ?: throw IOException("Not connected")
        val sb = StringBuilder(); val buf = ByteArray(512)
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val n = p.read(buf, 80)
            if (n > 0) { sb.append(String(buf, 0, n, Charsets.US_ASCII)); if (sb.indexOf(">") >= 0) break }
        }
        sb.toString()
    }

    override fun drain() { try { val p = port; val b = ByteArray(512); while (p != null && p.read(b, 10) > 0) { } } catch (_: Exception) {} }
    override fun close() { try { port?.close() } catch (_: Exception) {}; port = null }
}
