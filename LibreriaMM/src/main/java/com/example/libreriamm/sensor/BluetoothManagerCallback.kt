package com.example.libreriamm.sensor

interface BluetoothManagerCallback {
    fun onConnectedPeripheral(peripheral: String)
    fun onConnectionFailed(peripheral: String)
    fun onDisconnectedPeripheral(peripheral: String)
    fun onDiscoveredPeripheral(peripheral: String, scanResult: ScanResult)
    fun onScanFailed(scanFailure: String)
    fun onBluetoothAdapterStateChanged(state: AdapterState)
}