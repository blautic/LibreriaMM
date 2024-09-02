package com.example.libreriamm.entity

import kotlinx.serialization.SerialName

@KMMParcelize
@kotlinx.serialization.Serializable
data class VideoImage(
    @SerialName("imagen")
    var imagen: String = "",
    @SerialName("video")
    val video: String = ""
): KMMParcelable
