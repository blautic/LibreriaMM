package com.example.libreriamm.sensor

import BleUUID
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import com.diegulog.ble.gatt.BlePeripheral
import com.diegulog.ble.gatt.BlePeripheralCallback
import com.diegulog.ble.gatt.ConnectionPriority
import com.diegulog.ble.gatt.GattStatus
import java.util.*


class SimpleDeviceBluetoothManager(
    numDevice: Int,
    address: String,
    bluetoothPeripheralCallback: BluetoothPeripheralCallback,
    typeSensor: TypeSensor,
) {
    var enableSensors = GenericDevice.EnableSensors()
    //AQUI AHORA ES CUANDO HAY QUE HACER LA DIFERENCIACIÓN DEL TIPO PERO SOLO EN ALGUNAS COSAS.
    private val blePeripheralCallback: BlePeripheralCallback = object : BlePeripheralCallback() {

        override fun onServicesDiscovered(peripheral: BlePeripheral) {
            super.onServicesDiscovered(peripheral)
            peripheral.requestConnectionPriority(ConnectionPriority.HIGH)
            bluetoothPeripheralCallback.onServicesDiscovered(peripheral.address)
            Log.d("TypeSENSOR", "${typeSensor.name}")
            when (typeSensor) {
                TypeSensor.BIO1 -> {
                    // Acelerometro
                    write(peripheral, UUID.fromString(typeSensor.UUID_TAG_OPER), byteArrayOf(0), typeSensor)
                    enableNotify(peripheral, UUID.fromString(typeSensor.UUID_ACCELEROMETER_CHARACTERISTIC), true, typeSensor)
                    // Estado
                    write(peripheral, UUID.fromString(typeSensor.UUID_TAG_OPER), byteArrayOf(12), typeSensor)
                    enableNotify(peripheral, UUID.fromString(typeSensor.UUID_STATUS_CHARACTERISTIC), true, typeSensor)
                    // EMG
                    write(peripheral, UUID.fromString(typeSensor.UUID_TAG_OPER), byteArrayOf(1), typeSensor)
                    enableNotify(peripheral, UUID.fromString(typeSensor.UUID_ECG_CHARACTERISTIC), true, typeSensor)
                }
                TypeSensor.BIO2 -> {
                    if(enableSensors.mpu) {
                        // Acelerometro
                        write(
                            peripheral,
                            UUID.fromString(typeSensor.UUID_TAG_OPER),
                            byteArrayOf(0),
                            typeSensor
                        )
                        enableNotify(
                            peripheral,
                            UUID.fromString(typeSensor.UUID_ACCELEROMETER_CHARACTERISTIC),
                            true,
                            typeSensor
                        )
                    }
                    // Estado
                    write(peripheral, UUID.fromString(typeSensor.UUID_TAG_OPER), byteArrayOf(12), typeSensor)
                    enableNotify(peripheral, UUID.fromString(typeSensor.UUID_STATUS_CHARACTERISTIC), true, typeSensor)
                    // EMG
                    if(enableSensors.emg) {
                        write(
                            peripheral,
                            UUID.fromString(typeSensor.UUID_CH_FR),
                            byteArrayOf(15, 2),
                            typeSensor
                        )
                        write(
                            peripheral,
                            UUID.fromString(typeSensor.UUID_TAG_OPER),
                            byteArrayOf(4),
                            typeSensor
                        )
                        //write(peripheral, UUID.fromString(typeSensor.UUID_TAG_OPER), byteArrayOf(5), typeSensor)
                        enableNotify(
                            peripheral,
                            UUID.fromString(typeSensor.UUID_ECG_CHARACTERISTIC),
                            true,
                            typeSensor
                        )
                    }
                    // HR
                    if(enableSensors.hr) {
                        write(
                            peripheral,
                            UUID.fromString(typeSensor.UUID_TAG_OPER),
                            byteArrayOf(6),
                            typeSensor
                        )
                        enableNotify(
                            peripheral,
                            UUID.fromString(typeSensor.UUID_HR_CHARACTERISTIC),
                            true,
                            typeSensor
                        )
                    }
                }
                TypeSensor.PIKKU -> {
                    // Estado
                    enableNotify(peripheral, UUID.fromString(typeSensor.UUID_TAG_OPER), true, typeSensor)
                    // Acelerometro
                    write(peripheral, BleUUID.UUID_TR_PERIOD, byteArrayOf(50), typeSensor)
                    enableNotify(peripheral, UUID.fromString(typeSensor.UUID_ACCELEROMETER_CHARACTERISTIC), true, typeSensor)
                    write(peripheral, UUID.fromString(typeSensor.UUID_TR_PERIOD), byteArrayOf(typeSensor.fs), typeSensor)
                    write(peripheral,
                        BleUUID.UUID_SCORE_CFGMPU, byteArrayOf(0x3F, 0x00, 0x00, 0x08, 0x03, 0x03, 0x10), typeSensor)
                    write(
                        peripheral,
                        UUID.fromString(typeSensor.UUID_TAG_OPER),
                        byteArrayOf(BleUUID.ENABLE_REPORT_SENSORS.toByte()),
                        typeSensor
                    )
                }
                TypeSensor.CROLL -> {
                    // Estado
                    enableNotify(peripheral, UUID.fromString(typeSensor.UUID_STATUS_CHARACTERISTIC), true, typeSensor)

                    // Acelerometro
                    enableNotify(peripheral, UUID.fromString(typeSensor.UUID_MPU_CHARACTERISTIC), true, typeSensor)
                    write(peripheral, UUID.fromString(typeSensor.UUID_TR_PERIOD), byteArrayOf(0, 16), typeSensor)
                    write(peripheral, UUID.fromString(typeSensor.UUID_TAG_OPER), byteArrayOf(0x0F), typeSensor)
                }
            }

        }

        override fun onCharacteristicUpdate(
            peripheral: BlePeripheral,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic,
            status: GattStatus,
        ) {
            super.onCharacteristicUpdate(peripheral, value, characteristic, status)
            val uuid = characteristic.uuid.toString()
            bluetoothPeripheralCallback.onCharacteristicUpdate(peripheral.address, value!!, uuid)

            //AQUI PONER EL MPU FLOW Y DIFERENCIAR EL DISPOSITIVO.
        }

        override fun onCharacteristicWrite(
            peripheral: BlePeripheral,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic,
            status: GattStatus,
        ) {
            super.onCharacteristicWrite(peripheral, value, characteristic, status)
            if (status == GattStatus.SUCCESS) {
                bluetoothPeripheralCallback.onCharacteristicWrite(
                    peripheral.address,
                    value,
                    characteristic.uuid.toString()
                )

            }
        }

        override fun onReadRemoteRssi(peripheral: BlePeripheral, rssi: Int, status: GattStatus) {
            super.onReadRemoteRssi(peripheral, rssi, status)

            if (status == GattStatus.SUCCESS) {
                bluetoothPeripheralCallback.onReadRemoteRssi(peripheral.address, rssi)
            }
        }

        override fun onPhyUpdate(
            peripheral: BlePeripheral,
            txPhy:  com.diegulog.ble.gatt.PhyType,
            rxPhy: com.diegulog.ble.gatt.PhyType,
            status: GattStatus,
        ) {
            super.onPhyUpdate(peripheral, txPhy, rxPhy, status)
            if (status == GattStatus.SUCCESS) {
                bluetoothPeripheralCallback.onPhyUpdate(
                    peripheral.address,
                    PhyType.fromValue(txPhy.value),
                    PhyType.fromValue(rxPhy.value)
                )
            }
        }

    }

    /*    fun enableZivenMpu(blePeripheral: BlePeripheral, enable: Boolean) {
            val order = byteArrayOf(if (enable) ORDER_MPU else ORDER_IDLE)
            notify(UUID_MPU_CHARACTERISTIC, enable)
            write(blePeripheral, UUID_ORDER, order)
        }*/

    private fun write(blePeripheral: BlePeripheral, uuid: UUID, value: ByteArray, tipo: TypeSensor) {
        blePeripheral.writeCharacteristic(UUID.fromString(tipo.UUID_SERVICE), uuid, value)
    }

    fun enableNotify(blePeripheral: BlePeripheral, uuid: UUID?, enable: Boolean, tipo: TypeSensor) {
        blePeripheral.setNotify(UUID.fromString(tipo.UUID_SERVICE), uuid!!, enable)
    }


    fun getBlePeripheralCallback(): BlePeripheralCallback {
        return blePeripheralCallback
    }

}