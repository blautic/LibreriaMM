package com.example.libreriamm.entity

import kotlinx.serialization.SerialName

@KMMParcelize
@kotlinx.serialization.Serializable
data class Version(
    @SerialName("fldFAccuracy")
    val fldFAccuracy: Float,
    @SerialName("fldNEpoch")
    val fldNEpoch: Int,
    @SerialName("fldFLoss")
    val fldFLoss: Float,
    @SerialName("fldSOptimizer")
    val fldSOptimizer: String,
    @SerialName("fldFLearningRate")
    val fldFLearningRate: Float,
    @SerialName("id")
    val id: Int,
    @SerialName("fkOwner")
    val fkOwner: Int,
    @SerialName("fldDTimeCreateTime")
    val fldDTimeCreateTime: String,
):KMMParcelable
