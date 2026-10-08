package com.rgbcontroller.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("4b9d0001-7a5e-4d4c-ae88-c8c70d3ab211")
        val COMMAND_CHAR_UUID: UUID = UUID.fromString("4b9d0002-7a5e-4d4c-ae88-c8c70d3ab211")
        val STATE_CHAR_UUID: UUID = UUID.fromString("4b9d0003-7a5e-4d4c-ae88-c8c70d3ab211")
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _deviceState = MutableStateFlow<String>("STATE,0,0,0,0,0")
    val deviceState: StateFlow<String> = _deviceState.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var stateCharacteristic: BluetoothGattCharacteristic? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val currentList = _discoveredDevices.value.toMutableList()
            if (!currentList.any { it.address == device.address }) {
                currentList.add(device)
                _discoveredDevices.value = currentList
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = BleConnectionState.Error("Scan failed with code: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _connectionState.value = BleConnectionState.Discovering
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _connectionState.value = BleConnectionState.Disconnected
                    closeGatt()
                }
            } else {
                _connectionState.value = BleConnectionState.Error("GATT error status: $status")
                closeGatt()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    commandCharacteristic = service.getCharacteristic(COMMAND_CHAR_UUID)
                    stateCharacteristic = service.getCharacteristic(STATE_CHAR_UUID)

                    if (stateCharacteristic != null) {
                        gatt.setCharacteristicNotification(stateCharacteristic, true)
                        val descriptor = stateCharacteristic?.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                    _connectionState.value = BleConnectionState.Ready
                } else {
                    _connectionState.value = BleConnectionState.Error("RGB Service not found")
                }
            } else {
                _connectionState.value = BleConnectionState.Error("Service discovery failed: $status")
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == STATE_CHAR_UUID) {
                val value = characteristic.value?.toString(Charsets.UTF_8) ?: ""
                if (value.isNotEmpty()) {
                    _deviceState.value = value
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == STATE_CHAR_UUID) {
                val value = characteristic.value?.toString(Charsets.UTF_8) ?: ""
                if (value.isNotEmpty()) {
                    _deviceState.value = value
                }
            }
        }
    }

    fun startScan() {
        if (isScanning || bluetoothLeScanner == null) return
        _discoveredDevices.value = emptyList()
        _connectionState.value = BleConnectionState.Scanning
        isScanning = true
        bluetoothLeScanner.startScan(scanCallback)

        handler.postDelayed({
            stopScan()
        }, 10000)
    }

    fun stopScan() {
        if (!isScanning || bluetoothLeScanner == null) return
        bluetoothLeScanner.stopScan(scanCallback)
        isScanning = false
        if (_connectionState.value is BleConnectionState.Scanning) {
            _connectionState.value = BleConnectionState.Disconnected
        }
    }

    fun connect(device: BluetoothDevice) {
        stopScan()
        _connectionState.value = BleConnectionState.Connecting
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        closeGatt()
        _connectionState.value = BleConnectionState.Disconnected
    }

    fun writeCommand(command: String) {
        val char = commandCharacteristic
        val gatt = bluetoothGatt
        if (char != null && gatt != null) {
            char.value = command.toByteArray(Charsets.UTF_8)
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt.writeCharacteristic(char)
        }
    }

    private fun closeGatt() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        commandCharacteristic = null
        stateCharacteristic = null
    }
}
