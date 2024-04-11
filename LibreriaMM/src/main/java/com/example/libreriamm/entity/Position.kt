package com.example.libreriamm.entity

import kotlinx.serialization.SerialName

@KMMParcelize
@kotlinx.serialization.Serializable
data class Position(
    @SerialName("fldSName")
    var fldSName: String = "",
    @SerialName("fldSDescription")
    val fldSDescription: String = "",
    @SerialName("id")
    val id: Int = 0,
): KMMParcelable
