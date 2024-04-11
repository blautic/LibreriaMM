package com.example.libreriamm.sensor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SensorData {
    private val _dataFlow = MutableStateFlow(Pair(0f, 0))
    val dataFlow: StateFlow<Pair<Float, Int>> get() = _dataFlow
    val DataCache = mutableListOf<Pair<Float, Int>>()
    var enableCache = false
    fun add(valor: Float, sample: Int){
        _dataFlow.value = Pair(valor, sample)
        if(enableCache){
            DataCache.add(Pair(valor, sample))
        }
    }
}