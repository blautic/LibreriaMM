package com.example.libreriamm.entity

import com.example.libreriamm.sensor.TypeData

data class DatosCaptura (
    var sensor: TypeData,
    var posicion: Position?,
    var valores: MutableList<Float>
)