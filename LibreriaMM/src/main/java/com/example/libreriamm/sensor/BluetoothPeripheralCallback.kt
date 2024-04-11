package com.example.libreriamm.sensor

interface BluetoothPeripheralCallback {
    fun onServicesDiscovered(peripheral: String)
    fun onNotificationStateUpdate(peripheral: String, characteristic: String)
    fun onCharacteristicUpdate(peripheral: String, value: ByteArray, characteristic: String)
    fun onCharacteristicWrite(peripheral: String, value: ByteArray, characteristic: String)
    fun onReadRemoteRssi(peripheral: String, rssi: Int)
    fun onMtuChanged(peripheral: String, mtu: Int)
    fun onPhyUpdate(peripheral: String, txPhy: PhyType, rxPhy: PhyType)
    fun onConnectionUpdated(peripheral: String, interval: Int, latency: Int, timeout: Int)
}
