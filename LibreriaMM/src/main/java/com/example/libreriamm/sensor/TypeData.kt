package com.example.libreriamm.sensor

enum class TypeData(
    val fs: Int,
    val id: Int,
) {
    AccX(20,
        1),
    AccY(20,
        2),
    AccZ(20,
        3),
    GyrX(20,
        4),
    GyrY(20,
        5),
    GyrZ(20,
        6),
    Emg1(20,
        24),
    Emg2(20,
        25),
    Emg3(20,
        26),
    Emg4(20,
        27),
    Ecg(20,
        28),
    NOSE_X(10,
        7),
    LEFT_EYE_X(10,
        8),
    RIGHT_EYE_X(10,
        9),
    LEFT_EAR_X(10,
        10),
    RIGHT_EAR_X(10,
        11),
    LEFT_SHOULDER_X(10,
        12),
    RIGHT_SHOULDER_X(10,
        13),
    LEFT_ELBOW_X(10,
        14),
    RIGHT_ELBOW_X(10,
        15),
    LEFT_WRIST_X(10,
        16),
    RIGHT_WRIST_X(10,
        17),
    LEFT_HIP_X(10,
        18),
    RIGHT_HIP_X(10,
        19),
    LEFT_KNEE_X(10,
        20),
    RIGHT_KNEE_X(10,
        21),
    LEFT_ANKLE_X(10,
        22),
    RIGHT_ANKLE_X(10,
        23),
    NOSE_Y(10,
        29),
    LEFT_EYE_Y(10,
        30),
    RIGHT_EYE_Y(10,
        31),
    LEFT_EAR_Y(10,
        32),
    RIGHT_EAR_Y(10,
        33),
    LEFT_SHOULDER_Y(10,
        34),
    RIGHT_SHOULDER_Y(10,
        35),
    LEFT_ELBOW_Y(10,
        36),
    RIGHT_ELBOW_Y(10,
        37),
    LEFT_WRIST_Y(10,
        38),
    RIGHT_WRIST_Y(10,
        39),
    LEFT_HIP_Y(10,
        40),
    RIGHT_HIP_Y(10,
        41),
    LEFT_KNEE_Y(10,
        42),
    RIGHT_KNEE_Y(10,
        43),
    LEFT_ANKLE_Y(10,
        44),
    RIGHT_ANKLE_Y(10,
        45),

    LEFT_PULGAR_X(10,
        111),
    LEFT_INDICE_X(10,
        112),
    LEFT_PINKY_X(10,
        113),
    LEFT_PIE_X(10,
        114),
    LEFT_TALON_X(10,
        115),
    RIGHT_PULGAR_X(10,
        116),
    RIGHT_INDICE_X(10,
        117),
    RIGHT_PINKY_X(10,
        118),
    RIGHT_PIE_X(10,
        119),
    RIGHT_TALON_X(10,
        120),
    LEFT_PULGAR_Y(10,
        121),
    LEFT_INDICE_Y(10,
        122),
    LEFT_PINKY_Y(10,
        123),
    LEFT_PIE_Y(10,
        124),
    LEFT_TALON_Y(10,
        125),
    RIGHT_PULGAR_Y(10,
        126),
    RIGHT_INDICE_Y(10,
        127),
    RIGHT_PINKY_Y(10,
        128),
    RIGHT_PIE_Y(10,
        129),
    RIGHT_TALON_Y(10,
        130),

    PASS_X(10,
        84),
    PASS_Y(10,
        85),

    // Extras
    AI(1,
        101),
    HR(1,
        102),
    ANGULO_FLEXION(20,
        131),
    ANGULO_INCLINACION(20,
        132),
    ANGULO_ROTACION(20,
        133),
    LEFT_SHOULDER_ANGLE(10,
        134),
    RIGHT_SHOULDER_ANGLE(10,
        135),
    LEFT_ELBOW_ANGLE(10,
        136),
    RIGHT_ELBOW_ANGLE(10,
        137),
    LEFT_HIP_ANGLE(10,
        138),
    RIGHT_HIP_ANGLE(10,
        139),
    LEFT_KNEE_ANGLE(10,
        140),
    RIGHT_KNEE_ANGLE(10,
        141),
}