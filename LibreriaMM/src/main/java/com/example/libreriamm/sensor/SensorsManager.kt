package com.example.libreriamm.sensor


import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class SensorsManager(val context: Context) : BluetoothManagerCallback {

    var devices: MutableList<GenericDevice?> = mutableListOf()
    // SCAN RESULT
    private val _scanResultFlow = MutableStateFlow<ScanResult?>(null)
    private val _connectionChange = MutableStateFlow<Pair<Int, ConnectionState>?>(null)
    private val _scanFailureFlow = MutableStateFlow<String?>(null)
    val scanResultFlow get() = _scanResultFlow.asStateFlow()
    val scanFailureFlow get() = _scanFailureFlow.asStateFlow()
    val connectionChange get() = _connectionChange.asStateFlow()

    private val bluetoothManager: DeviceBluetoothManager = DeviceBluetoothManager(this, context = context)

    fun setListSize(size: Int){
        if(devices.size != size) {
            devices = MutableList(size) {
                GenericDevice(
                    numDevice = 0,
                    address = "",
                    typeSensor = TypeSensor.PIKKU
                )
            }
        }
    }
    fun addSensor(){
        devices.add(GenericDevice(numDevice = 0, address = "", typeSensor = TypeSensor.PIKKU))
    }

    fun startScan() {
        bluetoothManager.startScan()
    }

    fun stopScan() {
        bluetoothManager.stopScan()
    }

    fun getSensorNum(address: String): GenericDevice? {
        return devices.find { it!!.address == address}
    }

    fun getSensorType(index: Int): TypeSensor {
        return getSensorNum(index)!!.typeSensor
    }

    fun getSensorNum(numDevice: Int): GenericDevice? {
        return devices.find { it!!.numDevice == numDevice}
    }

    fun connect(numSensor: Int, address: String, typeSensor: TypeSensor, enableSensors: GenericDevice.EnableSensors) {
        conectando = true
        var sens = getSensorNum(address)
        /*if(sens != null){
            disconnectAndWait(sens)
        }*/
        sens = GenericDevice(numDevice = numSensor,
            address = address,
            typeSensor = typeSensor)
        sens.activeFilters()
        sens.setSensors(enableSensors)
        bluetoothManager.connectPeripheral(sens.numDevice, sens.address, sens)
        sens.setConnectionState(ConnectionState.CONNECTING)
        devices = devices.apply { set(numSensor, sens) }
        buscando = false
    }

    suspend fun disconnectAndWait(sensor: GenericDevice) {
        disconnect(sensor)
        sensor.connectionStateFlow
            .first { it == ConnectionState.DISCONNECTED }
    }
    fun disconnectAll(){
        devices.forEach {
            if(it != null){
                disconnect(it.numDevice)
            }
        }
    }
    fun disconnect(index: Int){
        val sens = getSensorNum(index)
        if (sens != null) {
            disconnect(sens)
        }
        devices[devices.indexOf(sens)] = GenericDevice(numDevice = 0, address = "", typeSensor = TypeSensor.PIKKU)
    }
    private fun disconnect(sensor: GenericDevice) {
        try {
            if(sensor.address != "") {
                bluetoothManager.cancelConnection(sensor.address)
            }
            sensor.setConnectionState(ConnectionState.DISCONNECTED)
            _connectionChange.value = Pair(sensor.numDevice, ConnectionState.DISCONNECTED)
            sensor.setStatus(0)
        }catch(e: Exception){}
    }
    fun isConnected(sensor: GenericDevice): Boolean {
        return sensor.connectionStateFlow.value == ConnectionState.CONNECTED
    }
    fun isConnected(address: String): Int? {
        val sens = getSensorNum(address)
        return sens?.numDevice
    }
    override fun onConnectedPeripheral(peripheral: String) {
        conectando = false
        buscando = false
        sensores = listOf()
        indexConn = null
        val sens = getSensorNum(peripheral)
        if(sens != null) {
            sens.setConnectionState(ConnectionState.CONNECTED)
            _connectionChange.value = Pair(sens.numDevice, ConnectionState.CONNECTED)
        }
    }
    override fun onConnectionFailed(peripheral: String) {
        conectando = false
        buscando = false
        sensores = listOf()
        indexConn = null
        val sens = getSensorNum(peripheral)
        sens!!.setConnectionState(ConnectionState.FAILED)
        _connectionChange.value = Pair(sens.numDevice, ConnectionState.FAILED)
    }
    override fun onDisconnectedPeripheral(peripheral: String) {
        val sens = getSensorNum(peripheral)
        sens?.setConnectionState(ConnectionState.DISCONNECTED)
        if (sens != null) {
            _connectionChange.value = Pair(sens.numDevice, ConnectionState.DISCONNECTED)
            devices[devices.indexOf(sens)]!!.address = ""
            sens.setStatus(0)
        }
    }
    var buscando = false
    var conectando = false
    var sensores: List<TypeSensor> = listOf()
    var indexConn: Int? = null
    var enableSensors = GenericDevice.EnableSensors(true, true, true)
    fun conectar(address: String, typeSensor: TypeSensor, index: Int, enableSensors: GenericDevice.EnableSensors) {
        stopScan()
        buscando = false
        sensores = listOf()
        indexConn = null
        connect(
            numSensor = index,
            address = address,
            typeSensor = typeSensor,
            enableSensors = enableSensors
        )
    }

    override fun onDiscoveredPeripheral(peripheral: String, scanResult: ScanResult) {
        if(buscando){
            Log.d("MMCORE", "Scan result: ${scanResult.name}-${scanResult.address} (${scanResult.button})")
            if(scanResult.button){
                val sens = isConnected(scanResult.address)
                if(sens != null){
                    onScanFailed("Sensor ya conectado en ${sens + 1}")
                }else{
                    if(scanResult.typeSensor in sensores){
                        if(conectando) {
                            onScanFailed("Ya se esta realizando una conexion")
                        }else{
                            stopScan()
                            conectar(scanResult.address, scanResult.typeSensor, indexConn!!, enableSensors)
                        }
                    }else{
                        onScanFailed("Tipo de sensor no valido")
                    }
                }
            }
        }
        _scanResultFlow.value = scanResult
    }
    override fun onScanFailed(scanFailure: String) {
        _scanFailureFlow.value = scanFailure
        _scanFailureFlow.value = null
    }
    override fun onBluetoothAdapterStateChanged(state: AdapterState) {
        // Handle the state change here.
    }
}
