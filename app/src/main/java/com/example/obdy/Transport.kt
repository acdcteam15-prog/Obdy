package com.example.obdy

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/** A pipe to the ELM327 adapter. Three kinds: Bluetooth classic, Bluetooth LE, Wi-Fi. */
interface Transport {
    val label: String
    suspend fun open()
    suspend fun write(text: String)
    /** Reads until the ELM327 prompt character '>' (or the timeout) and returns what arrived. */
    suspend fun readUntilPrompt(timeoutMs: Long): String
    fun drain()
    fun close()
}

private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

@SuppressLint("MissingPermission")
fun deviceName(d: BluetoothDevice): String = try { d.name ?: d.address } catch (e: SecurityException) { d.address }

@SuppressLint("MissingPermission")
class ClassicTransport(private val device: BluetoothDevice) : Transport {
    override val label: String get() = deviceName(device)
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    @Suppress("DEPRECATION")
    override suspend fun open() = withContext(Dispatchers.IO) {
        try { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() } catch (_: Exception) {}
        var s: BluetoothSocket? = null
        try {
            s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            s.connect()
        } catch (e: IOException) {
            try { s?.close() } catch (_: Exception) {}
            AppLog.i("standard Bluetooth socket failed (${e.message}); trying the fallback socket")
            val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            s = m.invoke(device, 1) as BluetoothSocket
            s.connect()
        }
        socket = s; input = s.inputStream; output = s.outputStream
    }

    override suspend fun write(text: String) = withContext(Dispatchers.IO) {
        val o = output ?: throw IOException("Not connected")
        o.write((text + "\r").toByteArray(Charsets.US_ASCII)); o.flush()
    }

    override suspend fun readUntilPrompt(timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val ins = input ?: throw IOException("Not connected")
        val sb = StringBuilder()
        val buf = ByteArray(1024)
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val n = ins.available()
            if (n > 0) {
                val r = ins.read(buf, 0, minOf(n, buf.size))
                if (r < 0) throw IOException("Connection closed")
                sb.append(String(buf, 0, r, Charsets.US_ASCII))
                if (sb.indexOf(">") >= 0) break
            } else delay(6)
        }
        sb.toString()
    }

    override fun drain() { try { val i = input; while (i != null && i.available() > 0) i.skip(i.available().toLong()) } catch (_: Exception) {} }
    override fun close() { try { socket?.close() } catch (_: Exception) {}; socket = null }
}

class WifiTransport(private val ctx: Context, private val host: String, private val port: Int) : Transport {
    override val label: String get() = "Wi-Fi $host:$port"
    private var socket: Socket? = null

    @Suppress("DEPRECATION")
    private fun wifiSocket(): Socket {
        // Use the Wi-Fi network even when Android prefers mobile data (the adapter's Wi-Fi has no internet).
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            for (n in cm.allNetworks) {
                val c = cm.getNetworkCapabilities(n) ?: continue
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return n.socketFactory.createSocket()
            }
        } catch (_: Exception) {}
        return Socket()
    }

    override suspend fun open() = withContext(Dispatchers.IO) {
        val s = wifiSocket()
        s.connect(InetSocketAddress(host, port), 6000)
        s.tcpNoDelay = true
        socket = s
    }

    override suspend fun write(text: String) = withContext(Dispatchers.IO) {
        val s = socket ?: throw IOException("Not connected")
        s.getOutputStream().write((text + "\r").toByteArray(Charsets.US_ASCII)); s.getOutputStream().flush()
    }

    override suspend fun readUntilPrompt(timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val ins = (socket ?: throw IOException("Not connected")).getInputStream()
        val sb = StringBuilder()
        val buf = ByteArray(1024)
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val n = ins.available()
            if (n > 0) {
                val r = ins.read(buf, 0, minOf(n, buf.size))
                if (r < 0) throw IOException("Connection closed")
                sb.append(String(buf, 0, r, Charsets.US_ASCII))
                if (sb.indexOf(">") >= 0) break
            } else delay(6)
        }
        sb.toString()
    }

    override fun drain() { try { val i = socket?.getInputStream(); while (i != null && i.available() > 0) i.skip(i.available().toLong()) } catch (_: Exception) {} }
    override fun close() { try { socket?.close() } catch (_: Exception) {}; socket = null }
}

private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class BleTransport(private val ctx: Context, private val device: BluetoothDevice) : Transport {
    override val label: String get() = deviceName(device)
    private val lock = Any()
    private val incoming = StringBuilder()
    private var gatt: BluetoothGatt? = null
    private var writeCh: BluetoothGattCharacteristic? = null
    private val ready = CompletableDeferred<Unit>()
    @Volatile private var writeDone: CompletableDeferred<Unit>? = null
    @Volatile private var lost: String? = null

    private fun append(b: ByteArray?) {
        if (b == null) return
        synchronized(lock) { incoming.append(String(b, Charsets.US_ASCII)) }
    }

    private fun pick(g: BluetoothGatt): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
        val preferred = listOf("0000fff0", "0000ffe0", "000018f0", "6e400001")
        val ordered = g.services.sortedBy { s -> val i = preferred.indexOfFirst { s.uuid.toString().startsWith(it) }; if (i < 0) 99 else i }
        for (s in ordered) {
            var notify: BluetoothGattCharacteristic? = null
            var write: BluetoothGattCharacteristic? = null
            for (c in s.characteristics) {
                val p = c.properties
                if (notify == null && (p and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0) notify = c
                if (write == null && (p and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) write = c
            }
            if (notify != null && write != null) return notify to write
        }
        return null
    }

    private val cb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                lost = "Bluetooth LE disconnected (status $status)"
                ready.completeExceptionally(IOException(lost))
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val p = if (status == BluetoothGatt.GATT_SUCCESS) pick(g) else null
            if (p == null) { ready.completeExceptionally(IOException("This Bluetooth LE device has no serial (UART) service. Is it an OBD adapter?")); return }
            writeCh = p.second
            g.setCharacteristicNotification(p.first, true)
            val d = p.first.getDescriptor(CCCD)
            if (d == null) { ready.complete(Unit); return }
            val value = if ((p.first.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0)
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, value) else { d.value = value; g.writeDescriptor(d) }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { ready.complete(Unit) }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) { append(value) }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) { append(c.value) }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { writeDone?.complete(Unit) }
    }

    override suspend fun open() {
        gatt = device.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE)
        try {
            withTimeout(15_000) { ready.await() }
        } catch (e: Exception) {
            close()
            if (e is IOException) throw e
            throw IOException("Bluetooth LE connection timed out")
        }
    }

    override suspend fun write(text: String) {
        val g = gatt ?: throw IOException("Not connected")
        val ch = writeCh ?: throw IOException("Not connected")
        lost?.let { throw IOException(it) }
        val bytes = (text + "\r").toByteArray(Charsets.US_ASCII)
        val noResp = (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        val type = if (noResp) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        var i = 0
        while (i < bytes.size) {
            val part = bytes.copyOfRange(i, minOf(i + 20, bytes.size)); i += 20
            val done = CompletableDeferred<Unit>(); writeDone = done
            val ok = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(ch, part, type) == 0
            } else {
                ch.writeType = type; ch.value = part; g.writeCharacteristic(ch)
            }
            if (!ok) throw IOException("Bluetooth LE write failed")
            if (noResp) delay(12) else { try { withTimeout(2000) { done.await() } } catch (_: Exception) {} }
        }
    }

    override suspend fun readUntilPrompt(timeoutMs: Long): String {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            lost?.let { throw IOException(it) }
            synchronized(lock) {
                val k = incoming.indexOf(">")
                if (k >= 0) { val s = incoming.substring(0, k + 1); incoming.delete(0, k + 1); return s }
            }
            delay(6)
        }
        return synchronized(lock) { val s = incoming.toString(); incoming.setLength(0); s }
    }

    override fun drain() { synchronized(lock) { incoming.setLength(0) } }
    override fun close() { try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {}; gatt = null }
}

/** Finds Bluetooth LE adapters nearby (only needed for LE adapters; classic ones are paired in Android settings). */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class BleScanner {
    private var cb: ScanCallback? = null

    fun start(onFound: (BluetoothDevice, Int) -> Unit): Boolean {
        val scanner = BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner ?: return false
        val c = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) { onFound(result.device, result.rssi) }
        }
        cb = c
        scanner.startScan(c)
        return true
    }

    fun stop() {
        val c = cb ?: return
        try { BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner?.stopScan(c) } catch (_: Exception) {}
        cb = null
    }
}
