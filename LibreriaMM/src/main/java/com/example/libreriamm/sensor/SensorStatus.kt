package com.example.libreriamm.sensor


data class SensorStatus(var battery: Int? = null, var operationMode: Int = 0, var button: Int = 0) {
    private var lasAdcBat = 0
    private val avgBat: MutableList<Int> = ArrayList()

    fun setData(parse: BleBytesParser) {

        operationMode = parse.getIntValue(BleBytesParser.FORMAT_UINT8)
        val adc = parse.getIntValue(BleBytesParser.FORMAT_UINT16)

        if (parse.getValue().size > 3) {
            button = parse.getIntValue(BleBytesParser.FORMAT_UINT8)
        }
        setPowerVal(adc)
    }

    private fun setPowerVal(adc: Int) {
        if (adc > 0 && adc != lasAdcBat) {
            //Napier.d(message = "adc $adc ")
            lasAdcBat = adc
            var bat = 0
            //Calculamos el valor de batería desde adc
            //12 bits: 1400 100%   1365:4.08V 1333:4.0   1309:3.9   1283:3.8 1252:3.7  1204:3.6 1160:3.5  1131:3.4 1110:3.3
            if (adc > powerAdc.first()) bat = 100
            else if (adc < powerAdc.last()) bat = 0
            else {
                for (i in powerAdc.indices) {
                    if (powerAdc[i] < adc) {
                        bat = powerPerc[i]
                        break
                    }
                }
            }
            if (avgBat.size >= NSAMPLES_AVG_BATT) avgBat.removeAt(0)

            avgBat.add(bat)
            battery = avgBat.average().toInt()
        }
    }

    override fun toString(): String {
        return "DeviceStatus{" +
                "battery=" + battery +
                ", operationMode=" + operationMode +
                '}'
    }

    override fun equals(other: Any?): Boolean {
        return false
    }

    companion object {
        private const val NSAMPLES_AVG_BATT = 10
        private val powerAdc = intArrayOf(
            1391, 1382, 1354, 1344, 1328, 1314, 1298, 1278, 1270, 1244, 1236,
            1211, 1198, 1177, 1165, 1147, 1130, 1122, 1098, 1084, 1055
        )
        private val powerPerc = intArrayOf(
            100, 95, 90, 85, 80, 75, 70, 65, 60, 55,
            50, 45, 40, 35, 30, 25, 20, 15, 10, 5, 0
        )
    }
}