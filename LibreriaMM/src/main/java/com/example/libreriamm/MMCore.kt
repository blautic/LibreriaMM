package com.example.libreriamm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.example.libreriamm.camara.BodyPart
import com.example.libreriamm.camara.Device
import com.example.libreriamm.camara.KeyPoint
import com.example.libreriamm.camara.ModelType
import com.example.libreriamm.camara.MoveNet
import com.example.libreriamm.camara.Person
import com.example.libreriamm.camara.PointF
import com.example.libreriamm.camara.YuvToRgbConverter
import com.example.libreriamm.entity.Model
import com.example.libreriamm.entity.Position
import com.example.libreriamm.motiondetector.MotionDetector
import com.example.libreriamm.sensor.SensorsManager
import com.example.libreriamm.sensor.TypeData
import com.example.libreriamm.sensor.TypeSensor
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
@RequiresApi(Build.VERSION_CODES.O)
@ExperimentalGetImage
class MMCore(val context: Context, val coroutineContext: CoroutineContext) {
    data class SensorPosicion(var tipoSensor: MutableList<TypeSensor>, val posicion: Int)

    private var duration = 1
    private var sensoresPosicion: MutableList<SensorPosicion> = mutableListOf()
    private var motionDetectors: MutableList<Pair<MotionDetector, MotionDetector>> = mutableListOf()
    private val deviceManager = SensorsManager(context = context)
    private var moveNetCache = mutableListOf<Person>()
    private var moveNetCacheRaw = mutableListOf<Person>()
    private var frontCamera = false
    private var media = 0f
    private var listaMedia = MutableList(5){0f}
    private var tiempos: MutableList<LocalDateTime> = mutableListOf()
    private val moveNet = MoveNet.create(context, Device.CPU, ModelType.Lightning)
    private var finalBitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
    private var modelos: List<Model> = listOf()
    private var frecuencias: MutableList<MutableList<Int>> = mutableListOf()
    private var cantidades: MutableList<MutableList<Int>> = mutableListOf()
    private val _motionDetectorCorrectFlow = MutableStateFlow<Pair<Int, Float>?>(null)
    private val motionDetectorCorrectFlow get() = _motionDetectorCorrectFlow.asStateFlow()
    private val _motionDetectorFlow = MutableStateFlow<Pair<Int, List<Float>>?>(null)
    private val dataInferedFlow get() = _dataInferedFlow.asStateFlow()
    private val _dataInferedFlow = MutableStateFlow<Array<Array<Array<Array<FloatArray>>>>?>(null)
    private val motionDetectorFlow get() = _motionDetectorFlow.asStateFlow()
    private val _sensorFlow: MutableList<MutableStateFlow<Pair<Int, Float>?>> = mutableListOf()
    private val sensorFlow get() = _sensorFlow
    private val scope = CoroutineScope(coroutineContext)
    private var started = false
    private var series: MutableList<MutableList<Array<Array<Array<Array<FloatArray>>>>>> = mutableListOf()



    private fun setDuration(duration: Int){
        this.duration = max(duration, this.duration)
    }
    private fun addSensorList(lista: MutableList<TypeSensor>, posicion: Int){
        val index = sensoresPosicion.indexOfFirst { it.posicion == posicion }
        if(index > -1){
            lista.forEach { tipo ->
                val indice = sensoresPosicion[index].tipoSensor.indexOfFirst { itTipo -> itTipo == tipo }
                if(indice > -1){
                    sensoresPosicion[index].tipoSensor.removeAt(indice)
                }
            }
        }else{
            sensoresPosicion.add(SensorPosicion(lista, posicion))
        }
    }
    private fun enableCache(numDevice: Int, numSensor: Int, enable: Boolean) =
        deviceManager.getSensorNum(numDevice)?.enableCache(numSensor, enable)
    private fun enableAllCache(enable: Boolean) =
        deviceManager.devices.forEach { dev ->
            dev?.enableAllCache(enable)
        }
    private fun getDataCache(numDevice: Int, sensorType: String, cant: Int) =
        deviceManager.getSensorNum(numDevice)?.getDataCache(sensorType, cant)
    private fun clearCache(numDevice: Int, numSensor: Int) =
        deviceManager.getSensorNum(numDevice)?.clearCache(numSensor)
    private fun clearCache(numDevice: Int, sensorType: String) =
        deviceManager.getSensorNum(numDevice)?.clearCache(sensorType)
    private fun clearAllCache(){
        deviceManager.devices.forEach{
            it?.sensorDatas?.forEachIndexed{index, sensor ->
                it.clearCache(index)
            }
        }
    }
    private fun setSensorsNum(num: Int) =
        deviceManager.setListSize(num)
    private fun startConnectDevice(typeSensors: List<TypeSensor>, position: Int){
        if(position < deviceManager.devices.size) {
            deviceManager.buscando = true
            deviceManager.sensores = typeSensors
            deviceManager.indexConn = position
            deviceManager.startScan()
        }
    }
    private fun clearMoveNetcache(){
        moveNetCache.clear()
        moveNetCacheRaw.clear()
    }
    private fun getMoveNetCacheSize(): Int{
        return moveNetCache.size
    }
    private fun getCenterPoint(landmarks: List<KeyPoint>, leftBodyPart: BodyPart, rightBodyPart: BodyPart): PointF {
        val left = landmarks.find { it.bodyPart == leftBodyPart }?.coordinate ?: PointF(0f, 0f)
        val right = landmarks.find { it.bodyPart == rightBodyPart }?.coordinate ?: PointF(0f, 0f)
        return PointF((left.x + right.x) / 2, (left.y + right.y) / 2)
    }
    private fun getPoseSize(landmarks: List<KeyPoint>, torsoSizeMultiplier: Float = 2.5f): Float {
        //CENTROS DEL HIP Y SHOULDER
        val hipsCenter = getCenterPoint(landmarks, BodyPart.LEFT_HIP, BodyPart.RIGHT_HIP)
        val shouldersCenter = getCenterPoint(landmarks,
            BodyPart.LEFT_SHOULDER,
            BodyPart.RIGHT_SHOULDER
        )

        //CALCULAMOS EL TORSO SIZE
        val torsoSize = sqrt((shouldersCenter.x - hipsCenter.x).pow(2) + (shouldersCenter.y - hipsCenter.y).pow(2))
        //CALCULAMOS EL NUEVO CENTRO
        val poseCenterNew = getCenterPoint(landmarks, BodyPart.LEFT_HIP, BodyPart.RIGHT_HIP)
        val distToPoseCenter = landmarks.map { PointF(it.coordinate.x - poseCenterNew.x, it.coordinate.y - poseCenterNew.y) }
        val maxDist = distToPoseCenter.maxByOrNull { sqrt(it.x.pow(2) + it.y.pow(2)) }?.let { sqrt(it.x.pow(2) + it.y.pow(2)) } ?: 0f
        val normalizedSize = maxOf(torsoSize * torsoSizeMultiplier, maxDist)
        return normalizedSize
    }
    private fun normalizePoseKeypoint(landmarks: List<KeyPoint>): List<KeyPoint> {
        val poseCenter = getCenterPoint(landmarks, BodyPart.LEFT_HIP, BodyPart.RIGHT_HIP)
        val normalizedLandmarks = landmarks.map {
            val normalizedX = (it.coordinate.x - poseCenter.x) / getPoseSize(landmarks)
            val normalizedY = (it.coordinate.y - poseCenter.y) / getPoseSize(landmarks)
            KeyPoint(it.bodyPart, PointF(normalizedX, normalizedY), 0f)
        }
        return normalizedLandmarks
    }
    private fun getMoveNetCache(duration: Int): MutableList<Person> {
        val size = moveNetCache.size
        val step =
            size.toFloat() / (10f * duration)// Cantidad de elementos extraídos deseada (10 en este caso)
        var index = 0f
        val result = mutableListOf<Person>()
        while (index < size) {
            val aux = if(index.roundToInt() >= size) size-1 else index.roundToInt()
            result.add(moveNetCache[aux])
            index += step

            if (result.size >= 10 * duration) {
                break
            }
        }
        return result
    }
    private fun getLastDuration(duration: Int): MutableList<Person> {
        val size = moveNetCache.size
        val index = duration * 10
        val result = mutableListOf<Person>()
        if (size < index){
            return result
        }
        for(i in 0 until index){
            result.add(moveNetCache[(size - index) + i])
        }
        return result
    }
    private fun ImageProxy.toRotatedBitmap(context: Context, degrees: Float, isFrontCamera: Boolean): Bitmap {
        val imageWidth = width
        val imageHeight = height
        val bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)

        YuvToRgbConverter(context).yuvToRgb(image!!, bitmap)

        val rotateMatrix = Matrix().apply {
            postRotate(degrees)
            if (isFrontCamera) {
                // Si es la cámara frontal, aplica una transformación de espejo horizontal.
                postScale(-1f, 1f, imageWidth / 2f, imageHeight / 2f)
            }
        }

        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, imageWidth, imageHeight, rotateMatrix, false)
        bitmap.recycle()

        return rotatedBitmap
    }
    private fun extractUniformElements(list: MutableList<Person>, model: Model): MutableList<Person> {
        val size = list.size
        val step =
            size.toFloat() / (10f * model.fldNDuration.toFloat())// Cantidad de elementos extraídos deseada (10 en este caso)
        var index = 0f
        val result = mutableListOf<Person>()

        while (index < size) {
            var aux = index.roundToInt()
            if(aux >= list.size) {
                aux = (list.size - 1)
            }
            result.add(list[aux])
            index += step

            if (result.size >= 10 * model.fldNDuration) {
                break
            }
        }
        return result
    }
    private suspend fun read(){
        Log.d("MMCORE", "Preparando inferencia")
        modelos.forEachIndexed { index, model ->
            if (media >= 0.2f || model.fkTipo != 2) {
                val contadorF: MutableList<Int> = MutableList(frecuencias[index].size) { 0 }
                val datasList: Array<Array<Array<Array<FloatArray>>>> =
                    Array(frecuencias[index].size) { Array(1) { arrayOf() } }
                for (i in 0 until frecuencias[index].size) {
                    datasList[i][0] = Array(frecuencias[index][i] * model.fldNDuration) {
                        Array(cantidades[index][i]) {
                            floatArrayOf(0f)
                        }
                    }
                }
                var pos = -1
                var nSensor = -1
                var inferir = true
                model.dispositivos.forEachIndexed { _, it ->
                    var frecuencia = 0
                    var posicion = 0
                    var nomSensor = ""
                    for (tipoDato in TypeData.entries) {
                        if (tipoDato.id == it.fkSensor) {
                            frecuencia = tipoDato.fs
                            nomSensor = tipoDato.name
                            posicion = frecuencias[index].indexOf(frecuencia)
                        }
                    }
                    if (pos != it.fkPosicion) {
                        pos = it.fkPosicion
                        if (pos != 0) {
                            nSensor += 1
                        }
                    }
                    if (pos != 0) {
                        if (nSensor < 0) {
                            return
                        }
                        val datos = getDataCache(nSensor, nomSensor, model.fldNDuration)
                        if(datos == null){
                            inferir = false
                        }else{
                            if(datos.size < (frecuencia * model.fldNDuration)){
                                inferir = false
                            }
                        }
                        datos?.forEachIndexed { indexD, itD ->
                            datasList[posicion][0][indexD][contadorF[posicion]][0] = itD.first
                        }
                    } else {
                        val resultsRawNorma: MutableList<Person> =
                            extractUniformElements(moveNetCache, model)
                        val resultsRaw: MutableList<Person> =
                            extractUniformElements(moveNetCacheRaw, model)
                        if(resultsRaw.size < 10*model.fldNDuration || resultsRawNorma.size < 10*model.fldNDuration){
                            inferir = false
                        }
                        var acumuladoX = 0f
                        var acumuladoY = 0f
                        resultsRawNorma.forEachIndexed { indexD, person ->
                            //val valores = person.copy(keyPoints = normalizePoseKeypoint(person.keyPoints))
                            var sDato = 0f
                            when (it.fkSensor) {
                                in 29..45 -> {
                                    sDato = person.keyPoints[it.fkSensor - 29].coordinate.y
                                }
                                in 7..23 -> {
                                    sDato = person.keyPoints[it.fkSensor - 7].coordinate.x
                                }
                                84 -> {
                                    sDato = if (indexD == 0) {
                                        0f
                                    } else {
                                        val x10 =
                                            resultsRaw[indexD - 1].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.x
                                        val x20 =
                                            resultsRaw[indexD - 1].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.x
                                        val x1f =
                                            resultsRaw[indexD].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.x
                                        val x2f =
                                            resultsRaw[indexD].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.x
                                        val x0 = (x20 + x10) / 2f
                                        val xf = (x2f + x1f) / 2f
                                        val alt0 =
                                            resultsRaw[indexD].keyPoints.minBy { puntos -> puntos.coordinate.y }.coordinate.y
                                        val altf =
                                            resultsRaw[indexD].keyPoints.maxBy { puntos -> puntos.coordinate.y }.coordinate.y
                                        val alt = abs(altf - alt0)
                                        if (alt < 1f) {
                                            0f
                                        } else {
                                            acumuladoX += ((xf - x0) / alt)
                                            min(1f, java.lang.Float.max(-1f, acumuladoX))
                                        }
                                        //(person.keyPoints[it.fkSensor - 50].coordinate.x - resultsRaw[index - 1].keyPoints[it.fkSensor - 50].coordinate.x) / abs(person.keyPoints.find {puntos ->  puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.x - person.keyPoints.find { puntos ->  puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.x) / 420f
                                    }
                                }
                                85 -> {
                                    sDato = if (indexD == 0) {
                                        0f
                                    } else {
                                        val y10 =
                                            resultsRaw[indexD - 1].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.y
                                        val y20 =
                                            resultsRaw[indexD - 1].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.y
                                        val y1f =
                                            resultsRaw[indexD].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.y
                                        val y2f =
                                            resultsRaw[indexD].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.y
                                        val y0 = (y20 + y10) / 2f
                                        val yf = (y2f + y1f) / 2f
                                        val alt0 =
                                            resultsRaw[indexD].keyPoints.minBy { puntos -> puntos.coordinate.y }.coordinate.y
                                        val altf =
                                            resultsRaw[indexD].keyPoints.maxBy { puntos -> puntos.coordinate.y }.coordinate.y
                                        val alt = abs(altf - alt0)
                                        if (alt < 1f) {
                                            0f
                                        } else {
                                            acumuladoY += ((yf - y0) / alt)
                                            min(1f, java.lang.Float.max(-1f, acumuladoY))
                                        }
                                        //(person.keyPoints[it.fkSensor - 50].coordinate.x - resultsRaw[index - 1].keyPoints[it.fkSensor - 50].coordinate.x) / abs(person.keyPoints.find {puntos ->  puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.x - person.keyPoints.find { puntos ->  puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.x) / 420f
                                    }
                                }
                            }
                            datasList[posicion][0][indexD][contadorF[posicion]][0] = sDato
                        }
                    }
                    contadorF[posicion] += 1
                }
                if(inferir) {
                    Log.d("MMCORE", "Inferir modelo ${datasList}")
                    motionDetectors[index].first.inference(datasList)
                }else{
                    Log.d("MMCORE", "Abortando inferencia por falta de datos")
                }
            }
        }
        Log.d("MMCORE", "Espera de siguiente inferencia")
        delay(100)
        read()
    }

    fun setmodels(models: List<Model>): Boolean{
        motionDetectors.forEach{
            it.first.stop()
            it.second.stop()
        }
        motionDetectors.clear()
        series.clear()
        clearAllCache()
        clearMoveNetcache()
        sensoresPosicion = mutableListOf()
        modelos = listOf()
        frecuencias = mutableListOf()
        cantidades = mutableListOf()
        models.forEach { model ->
            var posicion = model.dispositivos[0].fkPosicion
            var mpu = false
            var emg = false
            var ecg = false
            model.dispositivos.forEach {
                if(it.fkPosicion != posicion){
                    if(posicion != 0) {
                        val lista = mutableListOf<TypeSensor>()
                        if (mpu && !ecg && !emg) {
                            lista.add(TypeSensor.PIKKU)
                        }
                        if (mpu || ecg || emg) {
                            lista.add(TypeSensor.BIO2)
                        }
                        addSensorList(lista, posicion)
                    }
                    posicion = it.fkPosicion
                    mpu = false
                    emg = false
                    ecg = false
                }
                mpu = mpu || ((it.fkSensor <= 6) && (it.fkSensor >= 0))
                emg = emg || ((it.fkSensor <= 27) && (it.fkSensor >= 24))
                ecg = ecg || ((it.fkSensor <= 28) && (it.fkSensor >= 28))
            }
            if(posicion != 0) {
                val lista = mutableListOf<TypeSensor>()
                if (mpu && !ecg && !emg) {
                    lista.add(TypeSensor.PIKKU)
                }
                if (mpu || ecg || emg) {
                    lista.add(TypeSensor.BIO2)
                }
                addSensorList(lista, posicion)
            }
        }
        val hayTipoSensorVacio = sensoresPosicion.any { it.tipoSensor.isEmpty() }
        return if(hayTipoSensorVacio) {
            false
        }else{
            frecuencias = MutableList(models.size){ mutableListOf() }
            cantidades = MutableList(models.size){ mutableListOf() }
            setSensorsNum(sensoresPosicion.size)
            models.forEachIndexed { indexM, model ->
                series.add(mutableListOf())
                motionDetectors.add(Pair(MotionDetector(model, 0), MotionDetector(model, 1)))
                setDuration(model.fldNDuration)
                model.dispositivos.forEachIndexed{ index, dispositivo ->
                    for(tipoDato in TypeData.entries){
                        if(tipoDato.id == dispositivo.fkSensor){
                            val frecuencia = tipoDato.fs
                            val posicion = frecuencias[indexM].indexOf(frecuencia)
                            if(posicion != -1){
                                cantidades[indexM][posicion] += 1
                            }else{
                                frecuencias[indexM].add(frecuencia)
                                cantidades[indexM].add(1)
                            }
                        }
                    }
                }
            }
            modelos = models
            true
        }
    }
    fun getCapture(index: Int): List<Triple<Int, List<Pair<Int, Float>>, List<Triple<Int, Float, Float>>>>{
        var datas: MutableList<Triple<Int, List<Pair<Int, Float>>, List<Triple<Int, Float, Float>>>> = mutableListOf()
        var data: List<Pair<Int, Float>> = emptyList()
        var cam: List<Triple<Int, Float, Float>> = emptyList()
        val elemento = enumValues<TypeData>().toList()
        var nSensor = -1
        var posicion = -1
        modelos[index].let{ model ->
            model.dispositivos.forEachIndexed { index, it ->
                if (posicion != it.fkPosicion) {
                    posicion = it.fkPosicion
                    if (posicion != 0) {
                        nSensor += 1
                    }
                }
                var sample = 0
                if (posicion != 0) {
                    val elem = elemento.find { it1 -> it1.id == it.fkSensor }
                    if (elem == null) {
                        Log.d("Error Captura", "Elemento ${it.fkSensor} no encontrado")
                        return listOf()
                    }
                    val datos = getDataCache(nSensor, elem.name, model.fldNDuration)
                    if (datos == null) {
                        Log.d("Error Captura", "Cache ${nSensor}:${elem.name} no encontrada")
                        return listOf()
                    }
                    if (datos.isEmpty()) {
                        Log.d("Error Captura", "Cache ${nSensor}:${elem.name} vacia")
                        return listOf()
                    }
                    datos.forEach { it1 ->
                        data = data + Pair(sample, it1.first)
                        sample += 1
                    }
                    datas.add(Triple(it.id, data, listOf()))
                    data = emptyList()
                } else {
                    if (moveNetCache.size < 10 * model.fldNDuration) {
                        return listOf()
                    }
                    val resultsRaw: MutableList<Person> =
                        extractUniformElements(moveNetCache, model)
                    var acumuladoX = 0f
                    var acumuladoY = 0f
                    resultsRaw.forEachIndexed { index, person ->
                        val valores =
                            person.copy(keyPoints = normalizePoseKeypoint(person.keyPoints))
                        var sDato = 0f
                        if (it.fkSensor in 29..45) {
                            sDato = valores.keyPoints[it.fkSensor - 29].coordinate.y
                        } else if (it.fkSensor in 7..23) {
                            cam = cam + Triple(sample,person.keyPoints[it.fkSensor - 7].coordinate.x, person.keyPoints[it.fkSensor - 7].coordinate.y)
                            sDato = valores.keyPoints[it.fkSensor - 7].coordinate.x
                        } else if (it.fkSensor == 84) {
                            sDato = if (index == 0) {
                                0f
                            } else {
                                val x10 =
                                    resultsRaw[index - 1].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.x
                                val x20 =
                                    resultsRaw[index - 1].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.x
                                val x1f =
                                    resultsRaw[index].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.x
                                val x2f =
                                    resultsRaw[index].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.x
                                val x0 = (x20 + x10) / 2f
                                val xf = (x2f + x1f) / 2f
                                val alt0 =
                                    resultsRaw[index].keyPoints.minBy { puntos -> puntos.coordinate.y }.coordinate.y
                                val altf =
                                    resultsRaw[index].keyPoints.maxBy { puntos -> puntos.coordinate.y }.coordinate.y
                                val alt = abs(altf - alt0)
                                if (alt < 1f) {
                                    0f
                                } else {
                                    acumuladoX = acumuladoX + ((xf - x0) / alt)
                                    min(1f, max(-1f, acumuladoX))
                                }
                                //(person.keyPoints[it.fkSensor - 50].coordinate.x - resultsRaw[index - 1].keyPoints[it.fkSensor - 50].coordinate.x) / abs(person.keyPoints.find {puntos ->  puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.x - person.keyPoints.find { puntos ->  puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.x) / 420f
                            }
                        } else if (it.fkSensor == 85) {
                            sDato = if (index == 0) {
                                0f
                            } else {
                                val y10 =
                                    resultsRaw[index - 1].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.y
                                val y20 =
                                    resultsRaw[index - 1].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.y
                                val y1f =
                                    resultsRaw[index].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.y
                                val y2f =
                                    resultsRaw[index].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.y
                                val y0 = (y20 + y10) / 2f
                                val yf = (y2f + y1f) / 2f
                                val alt0 =
                                    resultsRaw[index].keyPoints.minBy { puntos -> puntos.coordinate.y }.coordinate.y
                                val altf =
                                    resultsRaw[index].keyPoints.maxBy { puntos -> puntos.coordinate.y }.coordinate.y
                                val alt = abs(altf - alt0)
                                if (abs(alt) < 1f) {
                                    0f
                                } else {
                                    acumuladoY = acumuladoY + ((yf - y0) / alt)
                                    min(1f, max(-1f, acumuladoY))
                                }
                                //(person.keyPoints[it.fkSensor - 50].coordinate.x - resultsRaw[index - 1].keyPoints[it.fkSensor - 50].coordinate.x) / abs(person.keyPoints.find {puntos ->  puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.x - person.keyPoints.find { puntos ->  puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.x) / 420f
                            }
                        }
                        data = data + Pair(sample, sDato)
                        sample += 1
                    }
                    datas.add(Triple(it.id, data, cam))
                    data = emptyList()
                    cam = emptyList()
                }
            }
        }
        return datas
    }
    fun getLabels(index: Int): List<String>{
        val res: MutableList<String> = mutableListOf()
        if(index < modelos.size){
            modelos[index].movements.forEach {
                res.add(it.fldSLabel)
            }
        }
        return res
    }
    fun startMotionDetector(){
        if(motionDetectors.size <= 0){
            return
        }
        started = true
        enableAllCache(true)
        motionDetectors.forEachIndexed { index, motionDetector ->
            Log.d("MOTIONDETECTOR", "Creando listener")
            motionDetector.first.setMotionDetectorListener(object :
                MotionDetector.MotionDetectorListener {

                override fun onCorrectMotionRecognized(correctProb: Float, datasList: Array<Array<Array<Array<FloatArray>>>>) {
                    series[index].add(datasList)
                }

                override fun onOutputScores(outputScores: FloatArray) {
                    val total = outputScores.sum()
                    val salida: MutableList<Float> = mutableListOf()
                    outputScores.forEach { outputScore ->
                        salida.add(outputScore/total)
                    }
                    //Timber.d("Estado Inferencia: $outputScore - $estadoInferencia - $maxOutput")
                    if(salida.isNotEmpty()){
                        _dataInferedFlow.value = series[index][series[index].size/2]
                        _motionDetectorFlow.value = Pair(index, salida)
                        if(salida[0] < 0.5){
                            if(series[index].isNotEmpty()){
                                motionDetector.second.inference(series[index][series[index].size/2])
                                series[index].clear()
                            }
                        }
                    }
                }
            })
            motionDetector.second.setMotionDetectorListener(object:
                MotionDetector.MotionDetectorListener{
                    override fun onCorrectMotionRecognized(correctProb: Float, datasList: Array<Array<Array<Array<FloatArray>>>>) {

                    }

                    override fun onOutputScores(outputScores: FloatArray) {
                        //Log.d("DURACION", "${outputScores[2]-outputScores[0]}:\t${outputScores[0]}-${outputScores[1]}-${outputScores[2]}")
                        val duracion = modelos[index].fldNDuration * java.lang.Float.max(
                            min(
                                1f,
                                (outputScores[2] - outputScores[0])
                            ), 0f
                        )
                        _motionDetectorCorrectFlow.value = Pair(index, duracion)
                    }
                })
        }
        scope.launch {
            Log.d("MMCORE", "SCOPE LAUNCHED")
            motionDetectors.forEach { motionDetector ->
                Log.d("MMCORE", "Iniciando md")
                motionDetector.first.start()
                motionDetector.second.start()
                Log.d("MMCORE", "Md iniciado")
            }
            Log.d("MMCORE", "Espera md")
            delay((duration * 1000).toLong())
            read()
        }
    }
    fun stopMotionDetector(){
        started = false
        motionDetectors.forEach { motionDetector ->
            motionDetector.first.stop()
            motionDetector.second.stop()
        }
        enableAllCache(false)
        clearMoveNetcache()
        clearAllCache()
        if (scope.isActive) {
            scope.cancel()
        }
    }
    fun getTipoSensores() = sensoresPosicion
    fun getSensorPos(index: Int): Int{
        return if(index < sensoresPosicion.size){
            sensoresPosicion[index].posicion
        }else{
            -1
        }
    }
    fun disconectDevice(numDevice: Int) = deviceManager.disconnect(numDevice)
    fun disconnectAll() =
        deviceManager.disconnectAll()
    fun getSensorType(index: Int): TypeSensor =
        deviceManager.getSensorType(index)
    fun startConnectDevice(posicion: Int) {
        if(posicion < sensoresPosicion.size){
            startConnectDevice(sensoresPosicion[posicion].tipoSensor, posicion)
        }
    }
    fun stopConnectDevice(){
        deviceManager.buscando = false
        deviceManager.sensores = listOf()
        deviceManager.indexConn = null
        deviceManager.stopScan()
    }
    fun onScanFailure() = deviceManager.scanFailureFlow
    fun onConnectionChange() = deviceManager.connectionChange
    fun onMotionDetectorChange() = motionDetectorFlow
    fun onMotionDetectorCorrectChange() = motionDetectorCorrectFlow

    fun onSensorChange(index: Int, typedata: TypeData): StateFlow<Pair<Float, Int>> {
        deviceManager.devices[index]!!.sensorDatas[deviceManager.devices[index]!!.typeSensor.Sensors.indexOf(
            typedata
        )].enableFlow = true
        Log.d("LECTURA", "HAbilitada")
        return deviceManager.devices[index]!!.sensorDatas[deviceManager.devices[index]!!.typeSensor.Sensors.indexOf(
            typedata
        )].dataFlow
    }
    fun onDataInferedChange() = dataInferedFlow
    fun setFrontCamera(front: Boolean){
        frontCamera = front
    }
    fun updateImage(imageProxy: ImageProxy){
        finalBitmap = if (!frontCamera) {
            imageProxy.toRotatedBitmap(context, 90f, true)
        } else {
            imageProxy.toRotatedBitmap(context, 270f, true)
        }
        val result = moveNet.estimateSinglePose(finalBitmap)
        media = 0f
        for(i in 0 until listaMedia.size-1){
            listaMedia[i] = listaMedia[i+1]
            media += listaMedia[i]
        }
        listaMedia[listaMedia.size-1] = result.score
        media += listaMedia[listaMedia.size-1]
        media /= listaMedia.size.toFloat()
        result.keyPoints.forEach {
            it.coordinate.x = 480 - it.coordinate.x
        }
        val resultRaw = result.copy(keyPoints = normalizePoseKeypoint(result.keyPoints))
        moveNetCache.add(resultRaw)
        moveNetCacheRaw.add(result)
        tiempos.add(LocalDateTime.now())
        var i = 0
        var continuar = true
        while(i < tiempos.size && continuar){
            if(tiempos[i].plusSeconds(duration.toLong()).isAfter(LocalDateTime.now())){
                i--
                continuar = false
            }
            i++
        }
        if(!continuar){
            for(j in 0 until i){
                tiempos.removeAt(0)
                moveNetCache.removeAt(0)
                moveNetCacheRaw.removeAt(0)
            }
        }
        imageProxy.close()
    }
    fun addGenericSensor(positionId: Int, sensores: List<TypeData>){
        val listaSens:MutableList<TypeSensor> = mutableListOf()
        val emg = sensores.contains(TypeData.Emg1) ||
                sensores.contains(TypeData.Emg2) ||
                sensores.contains(TypeData.Emg3) ||
                sensores.contains(TypeData.Emg4)
        val ecg = sensores.contains(TypeData.Ecg) ||
                sensores.contains(TypeData.HR)
        val mpu = sensores.contains(TypeData.AccX) ||
                sensores.contains(TypeData.AccY) ||
                sensores.contains(TypeData.AccZ) ||
                sensores.contains(TypeData.GyrX) ||
                sensores.contains(TypeData.GyrY) ||
                sensores.contains(TypeData.GyrZ) ||
                sensores.contains(TypeData.AI)
        if (mpu && !ecg && !emg) {
            listaSens.add(TypeSensor.PIKKU)
        }
        if (mpu || ecg || emg) {
            listaSens.add(TypeSensor.BIO2)
        }
        val position = sensoresPosicion.filter { it.posicion == positionId }.firstOrNull()
        if(position != null){
            sensoresPosicion[sensoresPosicion.indexOf(position)].tipoSensor = listaSens
        }else{
            sensoresPosicion.add(SensorPosicion(tipoSensor = listaSens, posicion = positionId))
            deviceManager.addSensor()
        }
    }
}