package com.example.libreriamm.entity

import android.os.Parcelable
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlinx.parcelize.Parcelize

class Platform {
    val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"
}


typealias KMMParcelable = Parcelable

typealias KMMParcelize = Parcelize

class KMMTimer constructor(
    delay: Long,
    interval: Long,
    action: () -> Unit,
) {

    fun start() {
    }

    fun cancel() {
    }

}

val platformCoroutineContext: CoroutineContext = Dispatchers.Default