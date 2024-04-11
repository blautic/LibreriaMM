package com.example.libreriamm.sensor

import BleUUID
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.diegulog.ble.BleManager
import com.diegulog.ble.BleManagerCallback
import com.diegulog.ble.ScanFailure
import com.diegulog.ble.gatt.BlePeripheral
import com.diegulog.ble.gatt.HciStatus
import org.koin.core.component.inject
import java.util.*
import kotlin.experimental.and

class DeviceBluetoothManager (
    bluetoothManagerCallback: BluetoothManagerCallback, val context: Context
){

    private val handler = Handler(Looper.getMainLooper())
    private val bleManager: BleManager

    private val bleManagerCallback: BleManagerCallback = object : BleManagerCallback() {

        override fun onConnectedPeripheral(peripheral: BlePeripheral) {
            super.onConnectedPeripheral(peripheral)
            bluetoothManagerCallback.onConnectedPeripheral(peripheral.address)
        }

        override fun onConnectionFailed(peripheral: BlePeripheral, status: HciStatus) {
            super.onConnectionFailed(peripheral, status)
            bluetoothManagerCallback.onConnectionFailed(peripheral.address)
        }

        override fun onDisconnectedPeripheral(peripheral: BlePeripheral, status: HciStatus) {
            super.onDisconnectedPeripheral(peripheral, status)
            bluetoothManagerCallback.onDisconnectedPeripheral(peripheral.address)
        }

        override fun onDiscoveredPeripheral(peripheral: BlePeripheral, scanResult: ScanResult) {
            super.onDiscoveredPeripheral(peripheral, scanResult)
            //ESCANEA LOS DOS TIPOS DE DISPOSITIVOS
            scanResult.scanRecord?.let {
                if(peripheral.name != "" && peripheral.name != "BUHO") {
                }
                if (checkIfTarget(it.bytes)) {
                    val type = when {
                        it.bytes[6] == 0X25.toByte() && it.bytes[5] == 0xBC.toByte()-> { // Compruebo si es Bio 1
                            TypeSensor.BIO1
                        }
                        it.bytes[6] == 0x26.toByte() && it.bytes[5] == 0xBC.toByte() -> { // Compruebo si es Bio 2
                            TypeSensor.BIO2
                        }
                        it.bytes[5] == 0xBE.toByte() -> { // Compruebo si es Pikku (BLE 5)
                            TypeSensor.PIKKU
                        }
                        it.bytes[5] == 0xBF.toByte() -> { // Compruebo si es Pikku (BLE 4)
                            TypeSensor.PIKKU
                        }
                        else -> { // Por defecto lo asigno al Pikku
                            TypeSensor.PIKKU
                        }
                    }
                    bluetoothManagerCallback.onDiscoveredPeripheral(
                        peripheral.address, ScanResult(
                            name = peripheral.name,
                            address = peripheral.address, rssi = scanResult.rssi,
                            button = checkButton(it.bytes, type),
                            typeSensor = type
                        )
                    )
                }
            }
        }

        private fun checkButton(scan: ByteArray, tipo: TypeSensor): Boolean{
            return when (tipo) {
                TypeSensor.BIO2 -> (scan[10].toInt() == 1)
                TypeSensor.BIO1 -> (scan[10].toInt() == 1)
                TypeSensor.PIKKU -> ((scan[16 + 2] and 0xFF.toByte()).toInt() == 1)
            }
        }

        private fun checkIfTarget(scan: ByteArray): Boolean {
            var res = false

            // Compruebo si es un Bio
            if(scan[5] == 0xBC.toByte()){
                res = true
            }
            // Compruebo si es un Bio2
            if(scan[5] == 0xAC.toByte()){
                res = true
            }
            // Compruebo si es un Pikku (BLE 5)
            if(scan[5] == 0xBE.toByte()){
                res = true
            }
            // Compruebo si es un Pikku (BLE 4)
            if(scan[5] == 0xBF.toByte()){
                res = true
            }

            return res
        }

        override fun onScanFailed(scanFailure: ScanFailure) {
            super.onScanFailed(scanFailure)
            bluetoothManagerCallback.onScanFailed(scanFailure.name)
        }

        override fun onBluetoothAdapterStateChanged(state: Int) {
            super.onBluetoothAdapterStateChanged(state)
        }
    }


    init {
        bleManager = BleManager(context, bleManagerCallback, handler)
    }

    fun startScan() {
        bleManager.scanForPeripherals()
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun cancelConnection(address: String) {
        bleManager.cancelConnection(bleManager.getPeripheral(address))
    }

    fun setNotify(uuid: String, enable: Boolean) {
        for (peripheral in bleManager.connectedPeripherals) {
            //DIFERENCIAR EL TIPO
            peripheral.setNotify(BleUUID.UUID_SERVICE, UUID.fromString(uuid), enable)
        }
    }

    fun connectPeripheral(
        numDevice: Int,
        mac: String,
        sensor: GenericDevice
    ) {
        val peripheral = bleManager.getPeripheral(mac)
        bleManager.connectPeripheral(peripheral,
            sensor.simpleDeviceBluetoothManager.getBlePeripheralCallback())
    }

}