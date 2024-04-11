package com.example.libreriamm.entity

import kotlinx.serialization.SerialName

@KMMParcelize
@kotlinx.serialization.Serializable
data class Movement(
    @SerialName("fldSLabel")
    val fldSLabel: String,
    @SerialName("fldSDescription")
    val fldSDescription: String,
    @SerialName("id")
    val id: Int,
    @SerialName("fkOwner")
    val fkOwner: Int,
    @SerialName("fldDTimeCreateTime")
    val fldDTimeCreateTime: String,
): KMMParcelable