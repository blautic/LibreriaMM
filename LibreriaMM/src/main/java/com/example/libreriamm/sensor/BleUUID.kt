import java.util.*

object BleUUID {
    val UUID_SERVICE = UUID.fromString("0000ff30-0000-1000-8000-00805f9b34fb")
    val UUID_TAG_OPER = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
    val UUID_ACTIVITY_INDEX = UUID.fromString("0000ff39-0000-1000-8000-00805f9b34fb")
    val UUID_TAG_BTN1 = UUID.fromString("0000ff37-0000-1000-8000-00805f9b34fb")
    val UUID_ACCEL = UUID.fromString("0000ff38-0000-1000-8000-00805f9b34fb")
    val UUID_SCORE_CFGMPU = UUID.fromString("0000ff3c-0000-1000-8000-00805f9b34fb")
    val UUID_NAME = UUID.fromString("0000ff3a-0000-1000-8000-00805f9b34fb")
    val UUID_TR_PERIOD = UUID.fromString("0000ff3b-0000-1000-8000-00805f9b34fb")
    val UUID_FIRMWARE = UUID.fromString("0000ff31-0000-1000-8000-00805f9b34fb")
    val UUID_GROUP = UUID.fromString("0000ff32-0000-1000-8000-00805f9b34fb")
    val UUID_CODE = UUID.fromString("0000ff33-0000-1000-8000-00805f9b34fb")
    val UUID_NUMBER = UUID.fromString("0000ff34-0000-1000-8000-00805f9b34fb")
    const val INT_DEVICE_BTN: Byte = 0x34
    const val TAG_ID_DEVICE = 0xBE.toByte()
    const val POS_ID_DEVICE = 5
    const val TAG_FRAME_ID = 0x55.toByte()
    const val POS_FRAME_ID = 6
    const val DISABLE_REPORT_SENSORS = 0
    const val ENABLE_REPORT_SENSORS = 1
    const val TURN_ON_LED = 0x04
    const val TURN_OFF_LED = 0x05
    const val FLASHING_LED = 0x06
    const val TURN_ON_ENGINE = 0x07
    const val TURN_OFF_ENGINE = 0x08
    const val TURN_OFF_DEVICE = 0x0A
    const val POS_TAG_BTN = 16
    const val POS_VERSION = 7
    const val POS_TAG_CODE = 13
    const val POS_TAG_GROUP = 10
    const val POS_TAG_NUMBER = 17
    const val POS_TAG_BTN1 = 18
    const val POS_TAG_BTN2 = 19
    const val POS_TAG_BATTERY = 20
}