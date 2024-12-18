package com.example.libreriamm.entity

import kotlinx.serialization.SerialName

@KMMParcelize
@kotlinx.serialization.Serializable
data class Creador(
    @SerialName("id")
    val id: Int,
    @SerialName("nombre")
    val nombre: String,
    @SerialName("verificado")
    val verificado: Int,
):KMMParcelable
