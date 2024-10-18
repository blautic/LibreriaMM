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
import com.example.libreriamm.entity.ObjetoEstadistica
import com.example.libreriamm.entity.Position
import com.example.libreriamm.motiondetector.MotionDetector
import com.example.libreriamm.sensor.SensorsManager
import com.example.libreriamm.sensor.TypeData
import com.example.libreriamm.sensor.TypeSensor
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
@RequiresApi(Build.VERSION_CODES.O)
@ExperimentalGetImage
class MMCore(val context: Context, val coroutineContext: CoroutineContext) {
    data class SensorPosicion(var tipoSensor: MutableList<TypeSensor>, val posicion: Int)
    private var sleepTime = 100L
    private var duration = 1
    private var sensoresPosicion: MutableList<SensorPosicion> = mutableListOf()
    private var motionDetectors: MutableList<Pair<MotionDetector, MotionDetector>> = mutableListOf()
    private val deviceManager = SensorsManager(context = context)
    private var moveNetCache = mutableListOf<Person>()
    private var moveNetCacheRaw = mutableListOf<Person>()
    private var frontCamera = true
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
    private val _motionDetectorIncorrectFlow = MutableStateFlow<String?>(null)
    private val motionDetectorIncorrectFlow get() = _motionDetectorIncorrectFlow.asStateFlow()
    private val _motionDetectorFlow = MutableStateFlow<Pair<Int, List<Float>>?>(null)
    private val dataInferedFlow get() = _dataInferedFlow.asStateFlow()
    private val _dataInferedFlow = MutableStateFlow<Array<Array<Array<Array<FloatArray>>>>?>(null)
    private val motionDetectorFlow get() = _motionDetectorFlow.asStateFlow()
    private val _sensorFlow: MutableList<MutableStateFlow<Pair<Int, Float>?>> = mutableListOf()
    private val personRawFlow get() = _personRawFlow.asStateFlow()
    private val _personRawFlow = MutableStateFlow<Person?>(null)
    private val sensorFlow get() = _sensorFlow
    private val scope = CoroutineScope(coroutineContext)
    private var currentJob: Job? = null
    private var started = false
    private var series: MutableList<MutableList<Array<Array<Array<Array<FloatArray>>>>>> = mutableListOf()
    private var estadisticas: MutableList<List<ObjetoEstadistica>> = mutableListOf()
    private var explicabilidad: MutableList<List<Pair<Int, List<Pair<Int, Float>>>>> = mutableListOf()
    private var maxExplicabilidad: MutableList<Float> = mutableListOf()
    private var indices: List<Int> = listOf()



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
    private fun ImageProxy.toBitmap(context: Context): Bitmap{
        val imageWidth = width
        val imageHeight = height
        val bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)

        YuvToRgbConverter(context).yuvToRgb(image!!, bitmap)
        return bitmap
    }
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float, isFrontCamera: Boolean): Bitmap{
        val rotateMatrix = Matrix().apply {
            postRotate(degrees)
            if (isFrontCamera) {
                // Si es la cámara frontal, aplica una transformación de espejo horizontal.
                postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            }
        }

        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotateMatrix, false)
        bitmap.recycle()
        return rotatedBitmap
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
        return extractUniformElements(list, model.fldNDuration)
    }
    private fun extractUniformElements(list: MutableList<Person>, duration: Int): MutableList<Person> {
        val size = list.size
        val step =
            size.toFloat() / (10f * duration.toFloat())// Cantidad de elementos extraídos deseada (10 en este caso)
        var index = 0f
        val result = mutableListOf<Person>()

        while (index < size) {
            var aux = index.roundToInt()
            if(aux >= list.size) {
                aux = (list.size - 1)
            }
            result.add(list[aux])
            index += step

            if (result.size >= 10 * duration) {
                break
            }
        }
        return result
    }
    private fun interpolarPersona(p1: Person, p2: Person): Person{
        val keypoints = mutableListOf<KeyPoint>()
        p1.keyPoints.forEachIndexed{index, kp ->
            keypoints.add(KeyPoint(bodyPart = kp.bodyPart, score = (kp.score + p2.keyPoints[index].score)/2f, coordinate = PointF(x=(kp.coordinate.x + p2.keyPoints[index].coordinate.x)/2f, y=((kp.coordinate.y + p2.keyPoints[index].coordinate.y)/2f))))
        }
        return Person(score=(p1.score + p2.score)/2f, keyPoints = keypoints)
    }
    private fun getShai(index: Int): List<Pair<Int, List<Pair<Int, Float>>>>{
        var datas: MutableList<Pair<Int, List<Pair<Int, Float>>>> = mutableListOf()
        var data: List<Pair<Int, Float>> = emptyList()
        modelos[index].let{ model ->
            model.dispositivos.filter { it.fkPosicion == 0}.forEachIndexed { index, it ->
                var sample = 0
                Log.d("MMCORE-LOG", "MovenetCache Size: ${moveNetCacheRaw.size}")
                if (moveNetCacheRaw.size * 2 < 10 * model.fldNDuration) {
                    return listOf()
                }
                if (moveNetCacheRaw.size < 10 * model.fldNDuration) {
                    val nuevaMoveNetCache = mutableListOf<Person>()
                    for(i in 0 until moveNetCacheRaw.size - 1){
                        val p1 = moveNetCacheRaw[i]
                        val p2 = moveNetCacheRaw[i+1]
                        nuevaMoveNetCache.add(p1)
                        nuevaMoveNetCache.add(interpolarPersona(p1, p2))
                    }
                    nuevaMoveNetCache.add(moveNetCacheRaw.last())
                    moveNetCacheRaw = nuevaMoveNetCache
                }
                val resultsRaw: MutableList<Person> =
                    extractUniformElements(moveNetCacheRaw, model)
                var acumuladoX = 0f
                var acumuladoY = 0f
                resultsRaw.forEachIndexed { index, person ->
                    val valores =
                        person.copy(keyPoints = normalizePoseKeypoint(person.keyPoints))
                    var sDato = 0f
                    if (it.fkSensor in 29..45) {
                        sDato = valores.keyPoints[it.fkSensor - 29].coordinate.y
                    } else if (it.fkSensor in 7..23) {
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
                datas.add(Pair(it.fkSensor, data))
                data = emptyList()
            }
        }
        return datas
    }
    private fun startMotionDetectorIndexPrivate(index: Int, optimizado: Boolean){
        if(motionDetectors.size <= index){
            return
        }
        started = true
        enableAllCache(true)
        motionDetectors[index].let { motionDetector ->
            Log.d("MOTIONDETECTOR", "Creando listener for ($index)")
            motionDetector.first.setMotionDetectorListener(object :
                MotionDetector.MotionDetectorListener {

                override fun onCorrectMotionRecognized(correctProb: Float, datasList: Array<Array<Array<Array<FloatArray>>>>) {
                    series[index].add(datasList)
                }

                override fun onOutputScores(outputScores: FloatArray) {
                    val total = 100f
                    val salida: MutableList<Float> = mutableListOf()
                    outputScores.forEach { outputScore ->
                        salida.add(outputScore/total)
                    }
                    //Timber.d("Estado Inferencia: $outputScore - $estadoInferencia - $maxOutput")
                    if(salida.isNotEmpty()){
                        _motionDetectorFlow.value = Pair(index, salida)
                        if(salida[0] >= maxExplicabilidad[index]){
                            maxExplicabilidad[index] = salida[0]
                            explicabilidad[index] = getShai(index)
                        }
                        if(series[index].size >= 1) {
                            _dataInferedFlow.value = series[index][series[index].size / 2]
                            if(!optimizado) {
                                if (salida[0] < 0.5 && modelos[index].fldBRegresivo == 1) {
                                    if (series[index].isNotEmpty()) {
                                        motionDetector.second.inference(series[index][series[index].size / 2])
                                        series[index].clear()
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onIncorrectMotionRecognized(mensaje: String) {
                    _motionDetectorIncorrectFlow.value = if(mensaje.length > 1) mensaje else null
                }
            })
            if(!optimizado) {
                motionDetector.second.setMotionDetectorListener(object :
                    MotionDetector.MotionDetectorListener {
                    override fun onCorrectMotionRecognized(
                        correctProb: Float,
                        datasList: Array<Array<Array<Array<FloatArray>>>>
                    ) {

                    }

                    override fun onOutputScores(outputScores: FloatArray) {
                        //Log.d("DURACION", "${outputScores[2]-outputScores[0]}:\t${outputScores[0]}-${outputScores[1]}-${outputScores[2]}")
                        /*val duracion = modelos[index].fldNDuration * java.lang.Float.max(
                            min(
                                1f,
                                (outputScores[2] - outputScores[0])
                            ), 0f
                        )*/
                        _motionDetectorCorrectFlow.value = Pair(index, 0f)
                    }

                    override fun onIncorrectMotionRecognized(mensaje: String){

                    }
                })
            }
            Log.d("MMCORE", "Iniciando md")
            motionDetector.first.start()
            if(!optimizado) {
                motionDetector.second.start()
            }
            Log.d("MMCORE", "Md iniciado")
        }
    }

    private suspend fun read(){
        val inicioD = LocalDateTime.now()
        Log.d("MMCORE", "Preparando inferencia")
        Log.d("MMCORE_REND", "Inferencia")
        val duracionMax = modelos.maxOf { it1 -> it1.fldNDuration }
        val dispositivosCombinados = modelos.flatMap { it1 -> it1.dispositivos }.distinctBy { it1 -> Pair(it1.fkSensor, it1.fkPosicion) }
        val datas: MutableList<Triple<Int, Int, List<FloatArray>>> = mutableListOf()
        val poses = dispositivosCombinados.map { it1 -> it1.fkPosicion }.filter { it2 -> it2 != 0 }.distinct()
        val resultsRawNorma: MutableList<Person> =
            extractUniformElements(moveNetCache, duracionMax)
        val resultsRaw: MutableList<Person> =
            extractUniformElements(moveNetCacheRaw, duracionMax)
        dispositivosCombinados.forEach { disp ->
            var datos: MutableList<Float> = mutableListOf()
            if(disp.fkPosicion != 0) {
                datos = getDataCache(
                    poses.indexOf(disp.fkPosicion),
                    TypeData.entries.first { it2 -> it2.id == disp.fkSensor }.name,
                    duracionMax
                )?.map { it1 -> it1.first }?.toMutableList()!!
            }else{
                var acumuladoX = 0f
                var acumuladoY = 0f
                resultsRawNorma.forEachIndexed { indexD, person ->
                    //val valores = person.copy(keyPoints = normalizePoseKeypoint(person.keyPoints))
                    var sDato = 0f
                    when (disp.fkSensor) {
                        in 29..45 -> {
                            sDato = person.keyPoints[disp.fkSensor - 29].coordinate.y
                        }

                        in 7..23 -> {
                            sDato = person.keyPoints[disp.fkSensor - 7].coordinate.x
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
                    datos.add(sDato)
                }
            }
            datas.add(Triple(disp.fkSensor, disp.fkPosicion, datos.map { it1 -> FloatArray(1) { it1 } }))
        }
        val finD = LocalDateTime.now()
        Log.d("MMCORE_REND", "Preparar datos comun: ${Duration.between(inicioD, finD).toMillis()}")
        modelos.forEachIndexed { index, model ->
            //modelos[1].let{model ->
            //val index = 1
            if (indices.contains(index)) {
                if (media >= 0.2f || model.fkTipo != 2) {
                    val inicioP = LocalDateTime.now()
                    var inferir = true
                    val datasList: Array<Array<Array<Array<FloatArray>>>> =
                        Array(frecuencias[index].size) { Array(1) { arrayOf() } }
                    frecuencias[index].forEachIndexed { indexFr, fr ->
                        val dispositivosFrec =
                            model.dispositivos.filter { it1 -> TypeData.entries.first { it2 -> it2.id == it1.fkSensor }.fs == fr }
                        val datasetPartial = Array(dispositivosFrec.size) {
                            Array(fr * model.fldNDuration) {
                                FloatArray(1) { 0f }
                            }
                        }
                        dispositivosFrec.forEachIndexed { index, dispo ->
                            val datasPartial =
                                datas.first { it1 -> (it1.first == dispo.fkSensor) && (it1.second == dispo.fkPosicion) }.third
                            if (datasPartial.size >= fr * model.fldNDuration) {
                                datasetPartial[index] =
                                    datasPartial.take(fr * model.fldNDuration).toTypedArray()
                            } else {
                                inferir = false
                            }
                        }
                        datasList[indexFr][0] = datasetPartial[0].indices.map { colIndex ->
                            datasetPartial.map { fila -> fila[colIndex] }.toTypedArray()
                        }.toTypedArray()
                    }
                    val finP = LocalDateTime.now()
                    Log.d(
                        "MMCORE_REND",
                        "Preparar datos especifico($index): ${
                            Duration.between(inicioP, finP).toMillis()
                        }"
                    )
                    //inferir = inferir && (index == 1)
                    if (inferir) {
                        Log.d(
                            "MMCORE",
                            "Inferir modelo ($index) ${datasList.map { it1 -> it1.map { it2 -> it2.map { it3 -> it3.size } } }}"
                        )
                        motionDetectors[index].first.inference(datasList)
                    } else {
                        Log.d(
                            "MMCORE",
                            "Abortando inferencia por falta de datos ${datasList.map { it1 -> it1.map { it2 -> it2.map { it3 -> it3.size } } }}"
                        )
                    }
                }
            }
        }
        Log.d("MMCORE", "Espera de siguiente inferencia")
        delay(sleepTime)
        read()
    }

    fun getExplicabilidad(index: Int): String{
        if(index >= estadisticas.size){
            return ""
        }
        val datos = explicabilidad[index]
        maxExplicabilidad[index] = 0f
        Log.d("MMCORE", "Resultados: ${estadisticas[index].map { r -> r.id}} Datos:  ${datos.map { r -> r.first}}")
        val resultados: MutableList<Pair<Int, MutableList<Float>>> = datos.map { d -> Pair(d.first, MutableList(4){0f}) }.toMutableList()
        resultados.forEach { res ->
            val dato = datos.firstOrNull { d -> d.first == res.first }
            if(dato != null) {
                val est = estadisticas[index].first { e -> e.id == res.first }
                val cant = ceil(dato.second.size / 3f).toInt()
                dato.second.forEachIndexed { index, d ->
                    val e = est.datos[index]
                    var a = e.media - d.second
                    var mayor = false
                    if (a < 0) {
                        mayor = true
                        a *= -1f
                    }
                    if (a > 0) {
                        val b = (a / max(e.std, 0.01f)) * (if (!mayor) -1 else 1)
                        //res.second[0] += b
                        res.second[(index / cant) + 1] += b
                    }
                }
            }else{
                Log.e("MMCORE", "No match resultados y datos: resultados: ${estadisticas[index].map { r -> r.id}} Datos:  ${datos.map { r -> r.first}}")
            }
        }
        val resultados2 = resultados.flatMapIndexed { index1, par -> par.second.mapIndexed{ index, valor -> Triple(par.first, index, valor)}}.sortedByDescending { abs(it.third) }
        var res = ""
        if(resultados2.size >= 1){
            val secuencia = resultados2[0]
            Log.d("MMCORE", "Resultados Explicabilidad: ${secuencia.first}:${secuencia.second}:${secuencia.third}")
            res = "Mueve mas "
            res += when{
                secuencia.first in 7..23 -> if(secuencia.third > 0) "a la derecha " else "a la izquierda "
                secuencia.first in 29..45 -> if(secuencia.third > 0) "arriba " else "abajo "
                secuencia.first == 84 -> if(secuencia.third > 0) "a la derecha " else "a la izquierda "
                secuencia.first == 85 -> if(secuencia.third > 0) "arriba " else "abajo "
                else -> {}
            }
            res += when(secuencia.first){
                7,8,9,10,11,29,30,31,32,33 -> "la cabeza "
                12, 34 -> "el hombro derecho "
                13, 35 -> "el hombro izquierdo "
                14, 36 -> "el brazo derecho "
                15, 37 -> "el brazo izquierdo "
                16, 38 -> "la mano derecha "
                17, 39 -> "la mano izquierda "
                18, 40 -> "la cadera derecha "
                19, 41 -> "la cadera izquierda "
                20, 42 -> "la pierna derecha "
                21, 43 -> "la pierna izquierda "
                22, 44 -> "el pie derecho "
                23, 45 -> "el pie izquierdo "
                84, 85 -> "el cuerpo "
                else -> {}
            }
            res += when(secuencia.second){
                1 -> "al inicio del movimiento"
                2 -> "a mitad del movimiento"
                3 -> "al final del movimiento"
                else -> ""
            }
        }
        /*resultados.forEachIndexed{index, res ->
            res.second.forEachIndexed{index1, dat ->
                Log.e("MMCORE", "($sensor, $instante) dat: $dat maximo:$maximo")
                if(abs(dat) > abs(maximo)){
                    sensor = index
                    instante = index1
                    maximo = dat
                }
            }
        }
        var res = "Mueve mas "
        res = res + when {
            sensor in 0..16 -> if(maximo > 0f) "a la izquierda " else "a la derecha "
            sensor in 17..33 -> if(maximo > 0f) "arriba " else "abajo "
            sensor == 34 -> if(maximo > 0f) "a la izquierda " else "a la derecha "
            sensor == 35 -> if(maximo > 0f) "arriba " else "abajo "
            else -> ""
        }
        res = res + when(sensor){
            0,1,2,3,4,17,18,19,20,21 -> "la cabeza "
            5,22 -> "el hombro izquierdo "
            6,23 -> "el hombro derecho "
            7,24 -> "el codo izquierdo "
            8,25 -> "el codo derecho "
            9,26 -> "la mano izquierda "
            10,27 -> "la mano derecha "
            11,28 -> "la cadera izquierda "
            12,29 -> "la cadera dereecha "
            13,30 -> "la rodilla izquierda "
            14,31 -> "la rodilla derecha "
            15,32 -> "el pie izquierdo "
            16,33 -> "el pie derecho "
            34,35 -> "el cuerpo "
            else -> ""
        }
        res = res + when(instante){
            1 -> "al inicio del movimiento"
            2 -> "a mitad del movimiento"
            3 -> "al final del movimiento"
            else -> ""
        }*/
        return res
    }
    fun enableAllCache(enable: Boolean) =
        deviceManager.devices.forEach { dev ->
            dev?.enableAllCache(enable)
        }
    fun setExplicabilidad(index: Int, estadistic: List<ObjetoEstadistica>): Boolean{
        Log.d("MMCORE", "SET STADISTIC: ($index) ${estadistic.map { e -> e.id }}")
        if(index >= estadisticas.size){
            return false
        }
        estadisticas[index] = estadistic
        return true
    }
    fun setmodels(models: List<Model>): Boolean{
        if(started){
            return false
        }
        motionDetectors.forEach{
            it.first.stop()
            it.second.stop()
        }
        while(estadisticas.size < models.size){
            estadisticas.add(listOf())
        }
        disconnectAll()
        motionDetectors = mutableListOf()
        series = mutableListOf()
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
                        if (mpu && !ecg && !emg) {
                            lista.add(TypeSensor.CROLL)
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
                if (mpu && !ecg && !emg) {
                    lista.add(TypeSensor.CROLL)
                }
                if (mpu || ecg || emg) {
                    lista.add(TypeSensor.BIO2)
                }
                addSensorList(lista, posicion)
            }
        }
        val hayTipoSensorVacio = sensoresPosicion.any { it.tipoSensor.isEmpty() }
        Log.d("MMCORE", "ListaSensores = ${sensoresPosicion}")
        return if(hayTipoSensorVacio) {
            false
        }else{
            frecuencias = MutableList(models.size){ mutableListOf() }
            cantidades = MutableList(models.size){ mutableListOf() }
            maxExplicabilidad = MutableList(models.size){0f}
            explicabilidad = MutableList(models.size){ listOf() }
            setSensorsNum(sensoresPosicion.size)
            models.forEachIndexed { indexM, model ->
                series.add(mutableListOf())
                motionDetectors.add(Pair(MotionDetector(model, 0), MotionDetector(model, 1)))
                Log.d("MMCORE", "Par detector creado")
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
                    Log.d("MMCORE-LOG", "MovenetCache Size: ${moveNetCacheRaw.size}")
                    if (moveNetCacheRaw.size * 2 < 10 * model.fldNDuration) {
                        return listOf()
                    }
                    if (moveNetCacheRaw.size < 10 * model.fldNDuration) {
                        val nuevaMoveNetCache = mutableListOf<Person>()
                        for(i in 0 until moveNetCacheRaw.size - 1){
                            val p1 = moveNetCacheRaw[i]
                            val p2 = moveNetCacheRaw[i+1]
                            nuevaMoveNetCache.add(p1)
                            nuevaMoveNetCache.add(interpolarPersona(p1, p2))
                        }
                        nuevaMoveNetCache.add(moveNetCacheRaw.last())
                        moveNetCacheRaw = nuevaMoveNetCache
                    }
                    val resultsRaw: MutableList<Person> =
                        extractUniformElements(moveNetCacheRaw, model)
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
    fun setSleepTime(time: Long){
        sleepTime = time
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
    fun startMotionDetector(optimizado: Boolean = true){
        indices = (0..modelos.size).toList()
        stopMotionDetector()
        startMotionDetectorIndex((0 until motionDetectors.size).toList(), optimizado)
        /*for(i in 0..motionDetectors.size){
            startMotionDetectorIndexPrivate(i, optimizado)
        }*/
    }
    fun startMotionDetectorIndex(indexs: List<Int>){
        startMotionDetectorIndex(indexs, true)
    }
    fun startMotionDetectorIndex(indexs: List<Int>, optimizado: Boolean){
        indices = indexs
        //indices = listOf(1)
        stopMotionDetector()
        enableAllCache(true)
        Log.d("MMCORE", "Creando listeners for ($indices)")
        indices.forEach{ index ->
            startMotionDetectorIndexPrivate(index, optimizado)
        }
        currentJob = scope.launch {
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
        if (currentJob != null) {
            currentJob!!.cancel()
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
    fun onPersonDetected() = personRawFlow
    fun onMotionDetectorCorrectChange() = motionDetectorCorrectFlow
    fun onMotionDetectorIncorrectChange() = motionDetectorIncorrectFlow
    fun onSensorChange(index: Int, typedata: TypeData): StateFlow<Pair<Float, Int>> {
        val id = sensoresPosicion.indexOf(sensoresPosicion.first { it.posicion == index })
        deviceManager.devices[id]!!.sensorDatas[deviceManager.devices[id]!!.typeSensor.Sensors.indexOf(
            typedata
        )].enableFlow = true
        Log.d("LECTURA", "Habilitada")
        return deviceManager.devices[id]!!.sensorDatas[deviceManager.devices[id]!!.typeSensor.Sensors.indexOf(
            typedata
        )].dataFlow
    }
    fun onDataInferedChange() = dataInferedFlow
    fun setFrontCamera(front: Boolean){
        frontCamera = front
    }
    fun updateImage(imageProxy: ImageProxy){
        finalBitmap = imageProxy.toBitmap(context)
        addImage(finalBitmap)
    }
    fun addImage(bitmap: Bitmap){
        finalBitmap = if (!frontCamera) {
            rotateBitmap(bitmap, 90f, true)
        } else {
            rotateBitmap(bitmap, 270f, true)
        }
        val result = moveNet.estimateSinglePose(finalBitmap)
        _personRawFlow.value = result
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
                if(tiempos.size > 1) {
                    tiempos.removeAt(0)
                }
                if(moveNetCache.size > 1) {
                    moveNetCache.removeAt(0)
                }
                if(moveNetCacheRaw.size > 1) {
                    moveNetCacheRaw.removeAt(0)
                }
            }
        }
        //imageProxy.close()
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
        if (mpu && !ecg && !emg) {
            listaSens.add(TypeSensor.CROLL)
        }
        if (mpu || ecg || emg) {
            listaSens.add(TypeSensor.BIO2)
        }
        val position = sensoresPosicion.firstOrNull { it.posicion == positionId }
        if(position != null){
            sensoresPosicion[sensoresPosicion.indexOf(position)].tipoSensor = listaSens
        }else{
            sensoresPosicion.add(SensorPosicion(tipoSensor = listaSens, posicion = positionId))
            deviceManager.addSensor()
        }
    }
}