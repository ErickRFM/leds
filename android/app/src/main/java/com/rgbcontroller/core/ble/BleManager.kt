package com.rgbcontroller.core.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.rgbcontroller.core.model.RgbDeviceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.UUID

/**
 * Single-device BLE transport. All GATT operations are serialized; the last SET wins
 * while a previous WRITE is in flight. Never claim Ready until CCCD + initial READ succeed.
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("4b9d0001-7a5e-4d4c-ae88-c8c70d3ab211")
        val COMMAND_CHAR_UUID: UUID = UUID.fromString("4b9d0002-7a5e-4d4c-ae88-c8c70d3ab211")
        val STATE_CHAR_UUID: UUID = UUID.fromString("4b9d0003-7a5e-4d4c-ae88-c8c70d3ab211")
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val handler = Handler(Looper.getMainLooper())

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _deviceState = MutableStateFlow("Sin estado del ESP32")
    val deviceState: StateFlow<String> = _deviceState.asStateFlow()

    private val _rgbState = MutableStateFlow<RgbDeviceState?>(null)
    val rgbState: StateFlow<RgbDeviceState?> = _rgbState.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var stateCharacteristic: BluetoothGattCharacteristic? = null
    private var scanning = false
    private val pending = ArrayDeque<String>()
    private var writeInFlight = false
    private var activeDevice: BluetoothDevice? = null
    private var manualDisconnect = false
    private var retries = 0
    private val scanTimeout = Runnable { stopScan() }
    private val reconnect = Runnable {
        val device = activeDevice
        if (!manualDisconnect && device != null) connectInternal(device)
    }

    private fun hasPermissions(): Boolean {
        val required = if (Build.VERSION.SDK_INT >= 31) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        return required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (_discoveredDevices.value.none { it.address == device.address }) {
                _discoveredDevices.value = _discoveredDevices.value + device
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            handler.removeCallbacks(scanTimeout)
            _connectionState.value = BleConnectionState.Error("Error de escaneo: $errorCode")
        }
    }

    fun startScan() {
        if (scanning) return
        if (!hasPermissions()) {
            _connectionState.value = BleConnectionState.Error("Concede permisos Bluetooth/ubicación en Ajustes")
            return
        }
        if (adapter?.isEnabled != true) {
            _connectionState.value = BleConnectionState.Error("Activa Bluetooth e inténtalo de nuevo")
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            _connectionState.value = BleConnectionState.Error("Escáner BLE no disponible")
            return
        }
        try {
            _discoveredDevices.value = emptyList()
            _connectionState.value = BleConnectionState.Scanning
            scanner.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback
            )
            scanning = true
            handler.postDelayed(scanTimeout, 10000)
        } catch (e: SecurityException) {
            _connectionState.value = BleConnectionState.Error("Permiso Bluetooth denegado")
        } catch (e: IllegalStateException) {
            _connectionState.value = BleConnectionState.Error("Bluetooth no disponible")
        }
    }

    fun stopScan() {
        handler.removeCallbacks(scanTimeout)
        if (!scanning) return
        scanning = false
        try { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        catch (_: SecurityException) { }
        if (_connectionState.value == BleConnectionState.Scanning) {
            _connectionState.value = BleConnectionState.Disconnected
        }
    }

    fun connect(device: BluetoothDevice) {
        stopScan()
        handler.removeCallbacks(reconnect)
        activeDevice = device
        retries = 0
        manualDisconnect = false
        connectInternal(device)
    }

    private fun connectInternal(device: BluetoothDevice) {
        if (!hasPermissions() || adapter?.isEnabled != true) {
            _connectionState.value = BleConnectionState.Error("Bluetooth o permisos no disponibles")
            return
        }
        closeConnection()
        _rgbState.value = null
        _deviceState.value = "Esperando estado del ESP32"
        _connectionState.value = BleConnectionState.Connecting
        try {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            _connectionState.value = BleConnectionState.Error("Permiso de conexión denegado")
        } catch (e: IllegalArgumentException) {
            _connectionState.value = BleConnectionState.Error("Dispositivo BLE inválido")
        }
    }

    private fun lostLink(message: String) {
        closeConnection()
        _rgbState.value = null
        if (manualDisconnect) {
            _connectionState.value = BleConnectionState.Disconnected
            return
        }
        if (retries < 3 && activeDevice != null) {
            val delayMs = (1000L shl retries).coerceAtMost(4000L)
            retries++
            _connectionState.value = BleConnectionState.Error("$message; reintento $retries/3")
            handler.removeCallbacks(reconnect)
            handler.postDelayed(reconnect, delayMs)
        } else {
            _connectionState.value = BleConnectionState.Error("$message; vuelve a conectar")
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(remote: BluetoothGatt, status: Int, newState: Int) {
            if (remote !== gatt) return
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = BleConnectionState.Discovering
                if (!remote.discoverServices()) lostLink("No se pudieron descubrir servicios")
            } else {
                lostLink("ESP32 desconectado (GATT $status)")
            }
        }

        override fun onServicesDiscovered(remote: BluetoothGatt, status: Int) {
            if (remote !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                lostLink("Error de descubrimiento GATT: $status")
                return
            }
            val service = remote.getService(SERVICE_UUID)
            val command = service?.getCharacteristic(COMMAND_CHAR_UUID)
            val state = service?.getCharacteristic(STATE_CHAR_UUID)
            val descriptor = state?.getDescriptor(CCCD)
            if (command == null || state == null || descriptor == null ||
                !remote.setCharacteristicNotification(state, true)) {
                lostLink("Servicio RGB incompleto")
                return
            }
            commandCharacteristic = command
            stateCharacteristic = state
            val started = if (Build.VERSION.SDK_INT >= 33) {
                remote.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                remote.writeDescriptor(descriptor)
            }
            if (!started) lostLink("No se pudo habilitar notificaciones")
        }

        override fun onDescriptorWrite(remote: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (remote !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS ||
                descriptor.uuid != CCCD || stateCharacteristic == null ||
                !remote.readCharacteristic(stateCharacteristic!!)) {
                lostLink("No se pudo leer estado inicial")
            }
        }

        override fun onCharacteristicRead(
            remote: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            if (remote !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS || characteristic.uuid != STATE_CHAR_UUID) {
                lostLink("Error al leer estado inicial")
                return
            }
            @Suppress("DEPRECATION")
            acceptState(characteristic.value)
            retries = 0
            _connectionState.value = BleConnectionState.Ready
            drainQueue()
        }

        override fun onCharacteristicRead(
            remote: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
            value: ByteArray, status: Int
        ) {
            if (remote !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS || characteristic.uuid != STATE_CHAR_UUID) {
                lostLink("Error al leer estado inicial")
                return
            }
            acceptState(value)
            retries = 0
            _connectionState.value = BleConnectionState.Ready
            drainQueue()
        }

        override fun onCharacteristicChanged(remote: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (remote !== gatt || characteristic.uuid != STATE_CHAR_UUID) return
            @Suppress("DEPRECATION")
            acceptState(characteristic.value)
        }

        override fun onCharacteristicChanged(
            remote: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray
        ) {
            if (remote === gatt && characteristic.uuid == STATE_CHAR_UUID) acceptState(value)
        }

        override fun onCharacteristicWrite(
            remote: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            if (remote !== gatt) return
            synchronized(pending) {
                writeInFlight = false
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    _connectionState.value = BleConnectionState.Error("Falló escritura GATT: $status")
                    pending.clear()
                    return
                }
            }
            drainQueue()
        }
    }

    private fun acceptState(bytes: ByteArray?) {
        val text = bytes?.toString(Charsets.UTF_8) ?: return
        _deviceState.value = text
        RgbDeviceState.parse(text)?.let { _rgbState.value = it }
    }

    fun writeCommand(text: String) {
        if (_connectionState.value != BleConnectionState.Ready) return
        synchronized(pending) {
            if (text.startsWith("SET,")) pending.removeAll { it.startsWith("SET,") }
            if (pending.size >= 16) pending.removeFirst()
            pending.addLast(text)
        }
        drainQueue()
    }

    private fun drainQueue() {
        synchronized(pending) {
            if (writeInFlight || pending.isEmpty() ||
                _connectionState.value != BleConnectionState.Ready) return
            val remote = gatt ?: return
            val characteristic = commandCharacteristic ?: return
            val message = pending.removeFirst()
            val bytes = message.toByteArray(Charsets.UTF_8)
            writeInFlight = true
            val ok = if (Build.VERSION.SDK_INT >= 33) {
                remote.writeCharacteristic(
                    characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = bytes
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                remote.writeCharacteristic(characteristic)
            }
            if (!ok) {
                writeInFlight = false
                _connectionState.value = BleConnectionState.Error("No se pudo iniciar escritura GATT")
                pending.clear()
            }
        }
    }

    fun disconnect() {
        manualDisconnect = true
        handler.removeCallbacks(reconnect)
        stopScan()
        try { gatt?.disconnect() } catch (_: SecurityException) { }
        closeConnection()
        activeDevice = null
        _rgbState.value = null
        _connectionState.value = BleConnectionState.Disconnected
    }

    private fun closeConnection() {
        synchronized(pending) {
            pending.clear()
            writeInFlight = false
            commandCharacteristic = null
            stateCharacteristic = null
        }
        val old = gatt
        gatt = null
        old?.close()
    }

    fun shutdown() {
        disconnect()
        handler.removeCallbacksAndMessages(null)
    }
}
