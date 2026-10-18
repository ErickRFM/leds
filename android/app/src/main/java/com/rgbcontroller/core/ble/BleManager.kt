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
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.rgbcontroller.core.model.RgbDeviceState
import com.rgbcontroller.core.model.RgbDeviceCommand
import com.rgbcontroller.core.model.BleGpioPin
import com.rgbcontroller.core.model.BleGpioSnapshot
import com.rgbcontroller.core.model.DeviceIdCodec
import com.rgbcontroller.core.ble.enrollment.EnrollmentReply
import com.rgbcontroller.core.ble.enrollment.OwnerEnrollmentClient
import com.rgbcontroller.core.ble.enrollment.OwnerSessionStore
import com.rgbcontroller.core.security.BouncyCastleSessionCrypto
import com.rgbcontroller.core.security.Hex
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
        val PUBLIC_ID_CHAR_UUID: UUID = UUID.fromString("4b9d0004-7a5e-4d4c-ae88-c8c70d3ab211")
        val GPIO_STATUS_CHAR_UUID: UUID = UUID.fromString("4b9d0005-7a5e-4d4c-ae88-c8c70d3ab211")
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val WRITE_TIMEOUT_MS = 5000L
    }

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val handler = Handler(Looper.getMainLooper())

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _scanStatistics = MutableStateFlow(BleScanStatistics())
    val scanStatistics: StateFlow<BleScanStatistics> = _scanStatistics.asStateFlow()
    private var rememberedAddress: String? = null

    private val _deviceState = MutableStateFlow("Sin estado del ESP32")
    val deviceState: StateFlow<String> = _deviceState.asStateFlow()

    private val _rgbState = MutableStateFlow<RgbDeviceState?>(null)
    val rgbState: StateFlow<RgbDeviceState?> = _rgbState.asStateFlow()
    private val _stateRevision = MutableStateFlow(0L)
    val stateRevision: StateFlow<Long> = _stateRevision.asStateFlow()
    private val _publicDeviceId = MutableStateFlow<String?>(null)
    val publicDeviceId: StateFlow<String?> = _publicDeviceId.asStateFlow()
    private val _connectedAddress = MutableStateFlow<String?>(null)
    /** Published only after notifications, initial state and identity handshake succeeded. */
    val connectedAddress: StateFlow<String?> = _connectedAddress.asStateFlow()
    private val _gpioPins = MutableStateFlow<List<BleGpioPin>?>(null)
    /** Optional firmware lease snapshot, read-only, and never a GPIO permission. */
    val gpioPins: StateFlow<List<BleGpioPin>?> = _gpioPins.asStateFlow()

    private val _securityState = MutableStateFlow(BleSecurityState.Disconnected)
    /** Firmware-confirmed owner authorization; bonding alone is never Authorized. */
    val securityState: StateFlow<BleSecurityState> = _securityState.asStateFlow()
    private val ownerCrypto = BouncyCastleSessionCrypto()
    private val ownerStore = OwnerSessionStore(context)
    private var ownerRecord: OwnerSessionStore.Record? = null
    private var ownerClient: OwnerEnrollmentClient? = null
    private enum class EnrollmentPhase { IDLE, PAIRING, CHALLENGE, PROOF, RESET }
    private var enrollmentPhase = EnrollmentPhase.IDLE
    private val _authorizationMessage = MutableStateFlow("Conecta el ESP32 para autorizar propietario.")
    val authorizationMessage: StateFlow<String> = _authorizationMessage.asStateFlow()
    private var bondDeadlineMs = 0L
    private val bondPoll = object : Runnable {
        override fun run() {
            val device = activeDevice
            if (device == null || !hasPermissions()) return
            when (_securityState.value) {
                BleSecurityState.Authorized, BleSecurityState.AuthorizationFailed,
                BleSecurityState.Disconnected -> return
                else -> Unit
            }
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                _securityState.value = BleSecurityState.Bonded
                requestAuthorization()
                return
            }
            if (System.currentTimeMillis() >= bondDeadlineMs) {
                if (_securityState.value != BleSecurityState.Authorizing) {
                    _securityState.value = BleSecurityState.Unauthorized
                }
                return
            }
            handler.postDelayed(this, 750)
        }
    }

    private var gatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var stateCharacteristic: BluetoothGattCharacteristic? = null
    private var publicIdCharacteristic: BluetoothGattCharacteristic? = null
    private var gpioStatusCharacteristic: BluetoothGattCharacteristic? = null
    private var gpioReadInFlight = false
    private var gpioPollingDisabled = false
    private val gpioReadTimeout = Runnable {
        // Telemetry is optional. A stalled read never falsely reports GPIO as live.
        _gpioPins.value = null
        gpioReadInFlight = false
        gpioPollingDisabled = true
        drainQueue()
    }
    private val gpioPoll = object : Runnable {
        override fun run() {
            if (_connectionState.value != BleConnectionState.Ready) return
            val remote = gatt
            val characteristic = gpioStatusCharacteristic
            if (!gpioPollingDisabled && remote != null && characteristic != null &&
                !gpioReadInFlight && !writeInFlight &&
                synchronized(pending) { pending.isEmpty() }) {
                gpioReadInFlight = true
                val started = try { remote.readCharacteristic(characteristic) }
                    catch (_: SecurityException) { false }
                    catch (_: IllegalStateException) { false }
                if (started) handler.postDelayed(gpioReadTimeout, 4500)
                else {
                    gpioReadInFlight = false
                    gpioPollingDisabled = true
                }
            }
            handler.postDelayed(this, 3000)
        }
    }
    private var scanning = false
    private var mtuReady = false
    private val pending = ArrayDeque<String>()
    private var writeInFlight = false
    private val writeTimeout = Runnable {
        val stalled = synchronized(pending) { writeInFlight }
        if (stalled && _connectionState.value == BleConnectionState.Ready) {
            // A missing GATT callback must not freeze the sliders indefinitely.
            // The next connection must read authoritative RGB state again.
            lostLink("ESP32 no confirmó escritura BLE en 5 s")
        }
    }
    private var activeDevice: BluetoothDevice? = null
    private var manualDisconnect = false
    private var retries = 0
    private val scanTimeout = Runnable { stopScan() }
    private val connectionTimeout = Runnable {
        if (_connectionState.value == BleConnectionState.Connecting ||
            _connectionState.value == BleConnectionState.Discovering) {
            lostLink("Tiempo de espera de conexión agotado")
        }
    }
    private fun armConnectionTimeout() {
        handler.removeCallbacks(connectionTimeout)
        handler.postDelayed(connectionTimeout, 15000)
    }
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

    private fun acceptAdvertisement(result: ScanResult) {
        if (!scanning) return
        val device = result.device
        val name = result.scanRecord?.deviceName
        val cachedName = try { device.name } catch (_: SecurityException) { null }
        val hasService = result.scanRecord?.serviceUuids
            ?.any { it.uuid == SERVICE_UUID } == true
        val address = try { device.address } catch (_: SecurityException) { null }
        val candidate = BleDiscoveryPolicy.isCandidate(
            name, cachedName, hasService, address, rememberedAddress)
        _scanStatistics.value = _scanStatistics.value.copy(
            advertisements = _scanStatistics.value.advertisements + 1)
        if (!candidate || address == null) return
        if (_discoveredDevices.value.none { it.address == address }) {
            _discoveredDevices.value = _discoveredDevices.value + device
            _scanStatistics.value = _scanStatistics.value.copy(
                compatibleDevices = _discoveredDevices.value.size)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            acceptAdvertisement(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::acceptAdvertisement)
        }

        override fun onScanFailed(errorCode: Int) {
            if (!scanning) return
            scanning = false
            handler.removeCallbacks(scanTimeout)
            _scanStatistics.value = _scanStatistics.value.copy(
                completed = true, failureCode = errorCode)
            _connectionState.value = BleConnectionState.Error(
                "Escaneo BLE falló ($errorCode). Reinicia Bluetooth y vuelve a buscar.")
        }
    }

    fun startScan(knownAddress: String? = null) {
        if (scanning) return
        rememberedAddress = knownAddress?.trim()?.takeIf { it.isNotEmpty() }
        _scanStatistics.value = BleScanStatistics()
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
        // Do not tear down a useful connection when permissions/radio are absent.
        // A valid explicit search supersedes any pending automatic reconnection.
        handler.removeCallbacks(reconnect)
        handler.removeCallbacks(connectionTimeout)
        manualDisconnect = true
        retries = 0
        activeDevice = null
        closeConnection()
        _rgbState.value = null
        _connectionState.value = BleConnectionState.Disconnected
        try {
            _discoveredDevices.value = emptyList()
            _connectionState.value = BleConnectionState.Scanning
            // Avoid controller-side UUID filter; keep app-side match above.
            scanning = true
            scanner.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback
            )
            handler.postDelayed(scanTimeout, 10000)
        } catch (e: SecurityException) {
            scanning = false
            _scanStatistics.value = _scanStatistics.value.copy(completed = true)
            _connectionState.value = BleConnectionState.Error("Permiso Bluetooth denegado")
        } catch (e: IllegalStateException) {
            scanning = false
            _scanStatistics.value = _scanStatistics.value.copy(completed = true)
            _connectionState.value = BleConnectionState.Error("Bluetooth no disponible")
        } catch (e: IllegalArgumentException) {
            scanning = false
            _scanStatistics.value = _scanStatistics.value.copy(completed = true)
            _connectionState.value = BleConnectionState.Error("Configuración de escaneo no compatible")
        }
    }

    fun stopScan() {
        handler.removeCallbacks(scanTimeout)
        if (!scanning) return
        scanning = false
        _scanStatistics.value = _scanStatistics.value.copy(completed = true)
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
        armConnectionTimeout()
        _rgbState.value = null
        _publicDeviceId.value = null
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
        handler.removeCallbacks(connectionTimeout)
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
                // STATE notifications exceed the default 20-byte ATT payload; negotiate MTU first.
                // Never fall through to service discovery with the default 23-byte MTU:
                // STATE notifications may exceed 20 bytes.
                if (!remote.requestMtu(128)) {
                    lostLink("No se pudo iniciar la negociación MTU BLE")
                }
            } else {
                lostLink("ESP32 desconectado (GATT $status)")
            }
        }

        override fun onMtuChanged(remote: BluetoothGatt, mtu: Int, status: Int) {
            if (remote !== gatt) return
            // A failed MTU request may still permit a full initial READ, but notifications
            // will be unreliable; surface a recoverable error rather than show stale RGB.
            if (status != BluetoothGatt.GATT_SUCCESS || mtu < 64) {
                lostLink("No se pudo negociar MTU BLE suficiente (64 bytes)")
            } else {
                mtuReady = true
                if (!remote.discoverServices()) {
                    lostLink("No se pudieron descubrir servicios")
                }
            }
        }

        override fun onServicesDiscovered(remote: BluetoothGatt, status: Int) {
            if (remote !== gatt) return
            if (!mtuReady || status != BluetoothGatt.GATT_SUCCESS) {
                lostLink("Servicios GATT inválidos o MTU sin negociar: $status")
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
            publicIdCharacteristic = service?.getCharacteristic(PUBLIC_ID_CHAR_UUID)
            gpioStatusCharacteristic = service?.getCharacteristic(GPIO_STATUS_CHAR_UUID)
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
            @Suppress("DEPRECATION")
            handleInitialRead(remote, characteristic, characteristic.value, status)
        }

        override fun onCharacteristicRead(
            remote: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
            value: ByteArray, status: Int
        ) {
            if (remote !== gatt) return
            handleInitialRead(remote, characteristic, value, status)
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
            if (remote !== gatt || characteristic.uuid != COMMAND_CHAR_UUID) return
            handler.removeCallbacks(writeTimeout)
            synchronized(pending) { writeInFlight = false }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                lostLink("Falló escritura GATT: $status")
            } else {
                drainQueue()
            }
        }
    }

    private fun handleInitialRead(
        remote: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
        bytes: ByteArray?, status: Int
    ) {
        when (characteristic.uuid) {
            STATE_CHAR_UUID -> {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    lostLink("Error al leer estado RGB inicial")
                } else {
                    completeInitialRead(bytes)
                }
            }
            GPIO_STATUS_CHAR_UUID -> {
                handler.post {
                    handler.removeCallbacks(gpioReadTimeout)
                    gpioReadInFlight = false
                    _gpioPins.value = if (status == BluetoothGatt.GATT_SUCCESS) {
                        BleGpioSnapshot.parse(bytes?.toString(Charsets.UTF_8).orEmpty())
                    } else null
                    if (_gpioPins.value == null) gpioPollingDisabled = true
                    drainQueue()
                }
            }
            PUBLIC_ID_CHAR_UUID -> {
                // Optional identity read: incompatible old firmware still controls RGB.
                _publicDeviceId.value = if (status == BluetoothGatt.GATT_SUCCESS) {
                    DeviceIdCodec.accept(bytes?.toString(Charsets.UTF_8).orEmpty())
                } else null
                finishInitialRead()
            }
        }
    }

    private fun finishInitialRead() {
        handler.removeCallbacks(connectionTimeout)
        retries = 0
        _connectedAddress.value = activeDevice?.address
        _connectionState.value = BleConnectionState.Ready
        handler.removeCallbacks(gpioPoll)
        handler.postDelayed(gpioPoll, 600)
        startBondWatch()
        drainQueue()
    }

    /**
     * Legacy static owner tokens are intentionally unsupported. New firmware
     * requires a physical one-time enrollment and an X25519 proof on each link.
     */
    @Deprecated("Use enrollWithCode from the physical-owner pairing flow")
    fun setOwnerKey(@Suppress("UNUSED_PARAMETER") key: String?) {
        _authorizationMessage.value = "La clave BLE antigua no es compatible; usa el código físico."
    }

    /** Start physical owner enrollment; the 8-hex code appears ONLY on USB serial. */
    fun enrollWithCode(code: String): Boolean {
        if (_connectionState.value != BleConnectionState.Ready ||
            activeDevice?.bondState != BluetoothDevice.BOND_BONDED ||
            enrollmentPhase != EnrollmentPhase.IDLE) return false
        val id = _publicDeviceId.value ?: return false
        if (!code.matches(Regex("[a-fA-F0-9]{8}"))) {
            _authorizationMessage.value = "Código inválido: usa ocho dígitos hexadecimales."
            return false
        }
        return try {
            val record = ownerStore.load(id) ?: run {
                val keys = ownerCrypto.generateKeyPair()
                val generated = OwnerSessionStore.Record(
                    "phone_" + Hex.encode(ownerCrypto.randomBytes(8)), keys, 0
                )
                // Durable before PAIR: never strand ownership on an unpersisted key.
                ownerStore.save(id, generated)
                generated
            }
            ownerRecord = record
            ownerClient = OwnerEnrollmentClient(
                ownerCrypto, record.ownerId, record.keys, record.counter
            )
            enrollmentPhase = EnrollmentPhase.PAIRING
            _securityState.value = BleSecurityState.Authorizing
            _authorizationMessage.value = "Registrando propietario con el ESP32…"
            enqueueProtocol(ownerClient!!.pairLine(code))
            true
        } catch (failure: Exception) {
            failAuthorization("No se pudo guardar o iniciar el propietario: ${failure.message}")
            false
        }
    }

    /**
     * Existing owner recovery requires the physical BOOT recovery window.
     * RESET is authorized by its one-time serial code; it never bypasses firmware.
     */
    fun resetOwnerWithCode(code: String): Boolean {
        if (_connectionState.value != BleConnectionState.Ready ||
            activeDevice?.bondState != BluetoothDevice.BOND_BONDED ||
            _publicDeviceId.value == null || enrollmentPhase != EnrollmentPhase.IDLE ||
            !code.matches(Regex("[a-fA-F0-9]{8}"))) return false
        enrollmentPhase = EnrollmentPhase.RESET
        _securityState.value = BleSecurityState.Authorizing
        _authorizationMessage.value = "Esperando confirmación de reinicio del propietario…"
        enqueueProtocol("RESET $code")
        return true
    }

    private fun failAuthorization(message: String) {
        enrollmentPhase = EnrollmentPhase.IDLE
        ownerClient?.reset()
        _securityState.value = BleSecurityState.AuthorizationFailed
        _authorizationMessage.value = message
    }

    private fun enqueueProtocol(frame: String) {
        // Only internally generated enrollment frames may bypass the RGB codec.
        check(frame.length in 2..125)
        synchronized(pending) {
            if (pending.size >= 16) pending.removeFirst()
            pending.addLast(frame)
        }
        drainQueue()
    }

    private fun acceptEnrollment(reply: EnrollmentReply) {
        val phase = enrollmentPhase
        if (phase == EnrollmentPhase.IDLE) return
        if (reply is EnrollmentReply.Err) {
            failAuthorization("ESP32 rechazó autorización: ${reply.reason}")
            return
        }
        try {
            when (phase) {
                EnrollmentPhase.PAIRING -> {
                    require(reply is EnrollmentReply.Ok) { "PAIR: respuesta inesperada" }
                    val client = ownerClient ?: error("sin propietario")
                    enrollmentPhase = EnrollmentPhase.CHALLENGE
                    _authorizationMessage.value = "Solicitando desafío del ESP32…"
                    enqueueProtocol(client.beginLine())
                }
                EnrollmentPhase.CHALLENGE -> {
                    require(reply is EnrollmentReply.Challenge) { "desafío inválido" }
                    val id = _publicDeviceId.value ?: error("identidad perdida")
                    val client = ownerClient ?: error("sin propietario")
                    val record = ownerRecord ?: error("credencial ausente")
                    val frame = client.authLine(reply)
                    // Reserve counter durably BEFORE transmission so a missing
                    // OK cannot replay the same proof after reconnect/reboot.
                    val updated = record.copy(counter = Math.addExact(record.counter, 1))
                    ownerStore.save(id, updated)
                    ownerRecord = updated
                    enrollmentPhase = EnrollmentPhase.PROOF
                    _authorizationMessage.value = "Verificando prueba criptográfica…"
                    enqueueProtocol(frame)
                }
                EnrollmentPhase.PROOF -> {
                    require(reply is EnrollmentReply.Ok) { "AUTH: respuesta inesperada" }
                    ownerClient?.onAuthenticated()
                    enrollmentPhase = EnrollmentPhase.IDLE
                    _securityState.value = BleSecurityState.Authorized
                    _authorizationMessage.value = "Propietario autenticado por el ESP32."
                    writeCommand("GET")
                }
                EnrollmentPhase.RESET -> {
                    require(reply is EnrollmentReply.Ok) { "RESET: respuesta inesperada" }
                    val id = _publicDeviceId.value ?: error("identidad perdida")
                    ownerStore.clear(id)
                    ownerClient = null
                    ownerRecord = null
                    enrollmentPhase = EnrollmentPhase.IDLE
                    _securityState.value = BleSecurityState.Unauthorized
                    _authorizationMessage.value =
                        "Propietario restablecido. Abre otra ventana física con BOOT para emparejar."
                }
                EnrollmentPhase.IDLE -> Unit
            }
        } catch (failure: Exception) {
            failAuthorization("Error de protocolo de propietario: ${failure.message}")
        }
    }

    /**
     * Begin pairing then prove ownership. Bonding/encryption only prepares an
     * encrypted link; authorization is granted solely by the firmware reply.
     */
    private fun startBondWatch() {
        val device = activeDevice ?: return
        if (!hasPermissions()) {
            _securityState.value = BleSecurityState.Unauthorized
            return
        }
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            _securityState.value = BleSecurityState.Bonded
            requestAuthorization()
            return
        }
        _securityState.value = BleSecurityState.Pairing
        try {
            if (!device.createBond()) {
                _securityState.value = BleSecurityState.Unauthorized
                return
            }
        } catch (_: SecurityException) {
            _securityState.value = BleSecurityState.Unauthorized
            return
        }
        bondDeadlineMs = System.currentTimeMillis() + 20_000
        handler.removeCallbacks(bondPoll)
        handler.postDelayed(bondPoll, 750)
    }

    private fun requestAuthorization() {
        val id = _publicDeviceId.value
        if (id == null) {
            _securityState.value = BleSecurityState.Unauthorized
            _authorizationMessage.value = "Firmware sin identidad pública compatible; no se permite control."
            return
        }
        try {
            val record = ownerStore.load(id)
            if (record == null) {
                _securityState.value = BleSecurityState.Unauthorized
                _authorizationMessage.value =
                    "Sin propietario: mantén BOOT 3 s, lee el código por USB (115200) y emparéjalo."
                return
            }
            ownerRecord = record
            val client = OwnerEnrollmentClient(
                ownerCrypto, record.ownerId, record.keys, record.counter
            )
            ownerClient = client
            enrollmentPhase = EnrollmentPhase.CHALLENGE
            _securityState.value = BleSecurityState.Authorizing
            _authorizationMessage.value = "Revalidando propietario con el ESP32…"
            enqueueProtocol(client.beginLine())
        } catch (failure: Exception) {
            failAuthorization("No fue posible recuperar la identidad segura: ${failure.message}")
        }
    }

    private fun completeInitialRead(bytes: ByteArray?) {
        val frame = bytes?.toString(Charsets.UTF_8).orEmpty()
        val parsed = RgbDeviceState.parse(frame)
        if (parsed == null) {
            lostLink("El ESP32 devolvió un estado RGB inválido")
            return
        }
        _deviceState.value = frame
        _rgbState.value = parsed
        _stateRevision.value += 1
        val identity = publicIdCharacteristic
        val remote = gatt
        if (identity != null && remote != null) {
            val started = try { remote.readCharacteristic(identity) }
                catch (_: SecurityException) { false }
            if (started) return
        }
        finishInitialRead()
    }

    private fun acceptState(bytes: ByteArray?) {
        val text = bytes?.toString(Charsets.UTF_8) ?: return
        // New firmware replies use OK / ERR <reason> / CHALLENGE <nonce> <pubkey>.
        // An obsolete AUTH,OK must NEVER authorize a modern owner session.
        val enrollmentReply = EnrollmentReply.parse(text)
        if (enrollmentReply != null) {
            acceptEnrollment(enrollmentReply)
            return
        }
        if (text == "ERR,INVALID_COMMAND") {
            _authorizationMessage.value = "ESP32 rechazó el comando; verifica autorización y modo."
            return
        }
        _deviceState.value = text
        RgbDeviceState.parse(text)?.let {
            _rgbState.value = it
            _stateRevision.value += 1
        }
    }

    fun writeCommand(text: String) {
        // Privileged RGB controls are not available on an unauthenticated link.
        if (!RgbDeviceCommand.allowed(text) ||
            _connectionState.value != BleConnectionState.Ready ||
            _securityState.value != BleSecurityState.Authorized) return
        synchronized(pending) {
            if (text.startsWith("SET,")) pending.removeAll { it.startsWith("SET,") }
            if (pending.size >= 16) pending.removeFirst()
            pending.addLast(text)
        }
        drainQueue()
    }

    private fun drainQueue() {
        synchronized(pending) {
            if (writeInFlight || gpioReadInFlight || pending.isEmpty() ||
                _connectionState.value != BleConnectionState.Ready) return
            val remote = gatt ?: return
            val characteristic = commandCharacteristic ?: return
            val message = pending.removeFirst()
            val bytes = message.toByteArray(Charsets.UTF_8)
            writeInFlight = true
            val ok = try {
                if (Build.VERSION.SDK_INT >= 33) {
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
            } catch (_: SecurityException) {
                false
            } catch (_: IllegalStateException) {
                false
            }
            if (!ok) {
                writeInFlight = false
                pending.clear()
                // Must close and recover: a failed GATT operation may poison its queue.
                lostLink("No se pudo iniciar escritura GATT")
            } else {
                handler.removeCallbacks(writeTimeout)
                handler.postDelayed(writeTimeout, WRITE_TIMEOUT_MS)
            }
        }
    }

    fun disconnect() {
        manualDisconnect = true
        handler.removeCallbacks(reconnect)
        handler.removeCallbacks(connectionTimeout)
        stopScan()
        try { gatt?.disconnect() } catch (_: SecurityException) { }
        closeConnection()
        activeDevice = null
        _rgbState.value = null
        _publicDeviceId.value = null
        _connectionState.value = BleConnectionState.Disconnected
    }

    private fun closeConnection() {
        handler.removeCallbacks(writeTimeout)
        handler.removeCallbacks(gpioPoll)
        handler.removeCallbacks(gpioReadTimeout)
        handler.removeCallbacks(bondPoll)
        gpioReadInFlight = false
        gpioPollingDisabled = false
        _gpioPins.value = null
        mtuReady = false
        _connectedAddress.value = null
        // Closing the link revokes authorization; the firmware does the same.
        _securityState.value = BleSecurityState.Disconnected
        enrollmentPhase = EnrollmentPhase.IDLE
        ownerClient?.reset()
        ownerClient = null
        ownerRecord = null
        _authorizationMessage.value = "Sin sesión BLE autenticada."
        synchronized(pending) {
            pending.clear()
            writeInFlight = false
            commandCharacteristic = null
            stateCharacteristic = null
            publicIdCharacteristic = null
            gpioStatusCharacteristic = null
            _publicDeviceId.value = null
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
