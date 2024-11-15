package com.example.libreriamm.entity

import kotlinx.serialization.SerialName

@KMMParcelize
@kotlinx.serialization.Serializable
data class DispositivoSensor(
    @SerialName("fkPosicion")
    val fkPosicion: Int,
    @SerialName("id")
    val id: Int,
    @SerialName("fkSensor")
    val fkSensor: Int,
    @SerialName("fkOwner")
    val fkOwner: Int,
    @SerialName("fldBActive")
    var fldBActive: Int = 1
): KMMParcelable
