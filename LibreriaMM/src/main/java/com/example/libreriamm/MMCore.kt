package com.example.libreriamm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat.getString
import com.example.libreriamm.camara.BodyPart
import com.example.libreriamm.camara.KeyPoint
import com.example.libreriamm.camara.ObjetLabel
import com.example.libreriamm.camara.Objeto
import com.example.libreriamm.camara.Person
import com.example.libreriamm.camara.PointF
import com.example.libreriamm.camara.PoseLandmarkerHelper
import com.example.libreriamm.camara.YuvToRgbConverter
import com.example.libreriamm.entity.DatosCaptura
import com.example.libreriamm.entity.Model
import com.example.libreriamm.entity.ObjetoEstadistica
import com.example.libreriamm.entity.ResultadoEstadistica
import com.example.libreriamm.motiondetector.MotionDetector
import com.example.libreriamm.motiondetector.PositionDetector
import com.example.libreriamm.sensor.GenericDevice
import com.example.libreriamm.sensor.SensorsManager
import com.example.libreriamm.sensor.TypeData
import com.example.libreriamm.sensor.TypeSensor
import com.google.mediapipe.examples.objectdetection.ObjectDetectorHelper
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

@RequiresApi(Build.VERSION_CODES.O)
@ExperimentalGetImage
class MMCore(val context: Context, val coroutineContext: CoroutineContext): PoseLandmarkerHelper.LandmarkerListener, ObjectDetectorHelper.DetectorListener {
    data class SensorPosicion(var tipoSensor: MutableList<TypeSensor>, val posicion: Int)
    enum class ImageDetector(val id: Int){MOVENET(1), MEDIAPIPE(1)}
    private var sleepTime = 100L
    private var duration = 1
    private var sensoresPosicion: MutableList<SensorPosicion> = mutableListOf()
    private var sensoresPosicionAux: MutableList<SensorPosicion> = mutableListOf()
    private var motionDetectors: MutableList<Pair<MotionDetector, MotionDetector>> = mutableListOf()
    private var inferenceCounter: MutableList<Long> = mutableListOf()
    private val deviceManager = SensorsManager(context = context)
    private var moveNetCache = mutableListOf<Triple<Person, Person, LocalDateTime>>()
    private var frontCamera = true
    private var rotacion = 0f
    private var media = 0f
    private val MIN_PUNTO = 0.2f
    private var listaMedia = MutableList(5){0f}
    private var mediasPuntos = MutableList(27){0f}
    private var listasMediasPuntos = MutableList(27) { MutableList(5) { 0f } }
    //private val moveNet = MoveNet.create(context, Device.CPU, ModelType.Lightning)
    private val moveNet = PoseLandmarkerHelper(context=context, poseLandmarkerHelperListener=this)
    private val objetDetector = ObjectDetectorHelper(context=context, objectDetectorListener=this)
    private val moveNetAsync = PoseLandmarkerHelper(context=context, poseLandmarkerHelperListener=null, runningMode= RunningMode.IMAGE)
    private val objetDetectorAsync = ObjectDetectorHelper(threshold=0.2f ,context=context, objectDetectorListener=null, runningMode= RunningMode.IMAGE)
    private var finalBitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
    private var modelos: List<Model> = listOf()
    private var modelosStatus: MutableList<Int> = mutableListOf()
    private var frecuencias: MutableList<MutableList<Int>> = mutableListOf()
    private var cantidades: MutableList<MutableList<Int>> = mutableListOf()
    private val _resultsFlow = MutableStateFlow<List<DatosCaptura>?>(null)
    private val resultsFlow get() = _resultsFlow.asStateFlow()
    private val _motionDetectorCorrectFlow = MutableStateFlow<Pair<Int, Float>?>(null)
    private val motionDetectorCorrectFlow get() = _motionDetectorCorrectFlow.asStateFlow()
    private val _motionDetectorIncorrectFlow = MutableStateFlow<String?>(null)
    private val motionDetectorIncorrectFlow get() = _motionDetectorIncorrectFlow.asStateFlow()
    private val _motionDetectorPosicionFlow = MutableStateFlow<String?>(null)
    private val motionDetectorPosicionFlow get() = _motionDetectorPosicionFlow.asStateFlow()
    private val _explicabilidadDatasFlow = MutableStateFlow<List<ResultadoEstadistica>?>(null)
    private val explicabilidadDatasFlow get() = _explicabilidadDatasFlow.asStateFlow()
    private val _explicabilidadFlow = MutableStateFlow<String?>(null)
    private val explicabilidadFlow get() = _explicabilidadFlow.asStateFlow()
    private val _motionIntentFlow = MutableStateFlow<Int?>(null)
    private val motionIntentFlow get() = _motionIntentFlow.asStateFlow()
    private val _motionDetectorTimeFlow = MutableStateFlow<Pair<Int, Float>?>(null)
    private val motionDetectorTimeFlow get() = _motionDetectorTimeFlow.asStateFlow()
    private val _motionDetectorFlow = MutableStateFlow<Pair<Int, List<Float>>?>(null)
    private val dataInferedFlow get() = _dataInferedFlow.asStateFlow()
    private val _dataInferedFlow = MutableStateFlow<Array<Array<Array<Array<FloatArray>>>>?>(null)
    private val motionDetectorFlow get() = _motionDetectorFlow.asStateFlow()
    private val _sensorFlow: MutableList<MutableStateFlow<Pair<Int, Float>?>> = mutableListOf()
    private val personRawFlow get() = _personRawFlow.asStateFlow()
    private val _personRawFlow = MutableStateFlow<Person?>(null)
    private val personListFlow get() = _personListFlow.asStateFlow()
    private val _personListFlow = MutableStateFlow<List<Person>?>(null)
    private val objectsRawFlow get() = _objectsRawFlow.asStateFlow()
    private val _objectsRawFlow = MutableStateFlow<List<Objeto>?>(null)
    private val cacheDetectorFlow get() = _cacheDetectorFlow.asStateFlow()
    private val _cacheDetectorFlow = MutableStateFlow<List<Pair<Person, List<Objeto>>>>(listOf())
    private var objectsCache: MutableList<Pair<List<Objeto>, LocalDateTime>> = mutableListOf()
    private val sensorFlow get() = _sensorFlow
    private val scope = CoroutineScope(coroutineContext)
    private var currentJob: Job? = null
    private var started = false
    private var series: MutableList<MutableList<Array<Array<Array<Array<FloatArray>>>>>> = mutableListOf()
    private var estadisticas: MutableList<List<ObjetoEstadistica>> = mutableListOf()
    private var explicabilidad: MutableList<List<Triple<Int, List<Pair<Int, Float>>, Int>>> = mutableListOf()
    private var maxExplicabilidad: MutableList<Float> = mutableListOf()
    private var indices: List<Int> = listOf()
    private var bitmapCache: MutableList<Pair<Bitmap, LocalDateTime>> = mutableListOf()
    private var libreInfObj = true
    private var posicionInicialEstado: Int? = null
    private var positionDetector: PositionDetector = PositionDetector(object :
        PositionDetector.PositionDetectorListener {
        override fun onOutputScores(outputScores: FloatArray) {
            Log.d("MMCORE_POSICION", "$posicionInicialEstado: ${outputScores.indices.maxBy { outputScores[it] }}")
            if(posicionInicialEstado != null) {
                Log.d("MMCORE_POSICION", "${modelos[posicionInicialEstado!!].fldSEtiquetaPos}")
                if (modelos[posicionInicialEstado!!].fldSEtiquetaPos != null) {
                    val salida = when (outputScores.indices.maxBy { outputScores[it] }) {
                        0 -> "45Der"
                        1 -> "45Izq"
                        2 -> "Frontal"
                        3 -> "Tumbado"
                        4 -> "TumbadoArriba"
                        else -> ""
                    }
                    Log.d("MMCORE_POSICION", "$salida VS ${modelos[posicionInicialEstado!!].fldSEtiquetaPos}")
                    if (salida != modelos[posicionInicialEstado!!].fldSEtiquetaPos) {
                        _motionDetectorPosicionFlow.value =
                            when (modelos[posicionInicialEstado!!].fldSEtiquetaPos) {
                                "45Der" -> getString(context, R.string.MMCORE_45Der)
                                "45Izq" -> getString(context, R.string.MMCORE_45Izq)
                                "Frontal" -> getString(context, R.string.MMCORE_Frontal)
                                "Tumbado" -> getString(context, R.string.MMCORE_Tumbado)
                                "TumbadoArriba" -> getString(
                                    context,
                                    R.string.MMCORE_TumbadoArriba
                                )

                                else -> getString(context, R.string.MMCORE_UNKNOW)
                            }
                    } else {
                        _motionDetectorPosicionFlow.value = null
                        _motionDetectorPosicionFlow.value =
                            getString(context, R.string.posInicial)
                        posicionInicialEstado = null
                    }
                }
            }
        }
    })



    private fun setDuration(duration: Int){
        this.duration = max(duration, this.duration)
    }
    private fun addSensorList(lista: MutableList<TypeSensor>, posicion: Int){
        val index = sensoresPosicionAux.indexOfFirst { it.posicion == posicion }
        if(index > -1){
            Log.d("MMCORE-SETMODELS", "Sensor Existente")
            val newList: MutableList<TypeSensor> = mutableListOf()
            lista.forEach { tipo ->
                val indice = sensoresPosicionAux[index].tipoSensor.indexOfFirst { itTipo -> itTipo == tipo }
                if(indice > -1){
                    newList.add(tipo)
                }
            }
            sensoresPosicionAux[index].tipoSensor = newList
        }else{
            Log.d("MMCORE-SETMODELS", "Nuevo Sensor")
            sensoresPosicionAux.add(SensorPosicion(lista, posicion))
        }
        Log.d("MMCORE-SETMODELS", "Sensores: ${sensoresPosicionAux.map { it1 ->  "${it1.posicion}-${it1.tipoSensor.map { it2 -> "${it2.name}" }}" }}")
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
    private fun setSensorsNum(num: Int) {
        if(deviceManager.devices.size != num){
            disconnectAll()
        }
        deviceManager.setListSize(num)
    }
    private fun startConnectDevice(typeSensors: List<TypeSensor>, position: Int, enableSensors: GenericDevice.EnableSensors){
        if(position < deviceManager.devices.size) {
            deviceManager.buscando = true
            deviceManager.sensores = typeSensors
            deviceManager.indexConn = position
            deviceManager.enableSensors = enableSensors
            deviceManager.startScan()
        }
    }
    private fun clearMoveNetcache(){
        moveNetCache.clear()
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
            KeyPoint(it.bodyPart, PointF(normalizedX, normalizedY), 0f, it.angle)
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
            result.add(moveNetCache[aux].first)
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
            result.add(moveNetCache[(size - index) + i].first)
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
            if(list[aux].keyPoints.size >= 27) {
                result.add(list[aux])
            }
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
    private fun interpolarCache(p1: Triple<Person, Person, LocalDateTime>, p2: Triple<Person, Person, LocalDateTime>): Triple<Person, Person, LocalDateTime>{
        val p3 = interpolarPersona(p1.first, p2.first)
        val p4 = interpolarPersona(p1.second, p2.second)
        val startMillis = p1.third.toInstant(ZoneOffset.UTC).toEpochMilli()
        val endMillis = p2.third.toInstant(ZoneOffset.UTC).toEpochMilli()

        // Calcula el punto medio en milisegundos
        val midpointMillis = (startMillis + endMillis) / 2

        // Convierte el punto medio de vuelta a LocalDateTime
        val t = LocalDateTime.ofEpochSecond(
            midpointMillis / 1000, // Segundos enteros
            (midpointMillis % 1000 * 1_000_000).toInt(), // Nanosegundos
            ZoneOffset.UTC
        )
        return Triple(p3, p4, t)
    }
    private fun getShai(index: Int): List<Triple<Int, List<Pair<Int, Float>>, Int>>{
        val datas: MutableList<Triple<Int, List<Pair<Int, Float>>, Int>> = mutableListOf()
        var data: List<Pair<Int, Float>> = emptyList()
        modelos[index].let{ model ->
            var nSensor = -1
            var posicion = 0
            model.dispositivos.forEachIndexed { index, it ->
                var sample = 0
                if(it.fkPosicion == 0) {
                    Log.d("MMCORE-LOG", "MovenetCache Size: ${moveNetCache.size}")
                    var cacheTemp = moveNetCache.map { it1 -> it1.second }.toMutableList()
                    if (cacheTemp.size * 2 < 10 * model.fldNDuration) {
                        Log.d("MMCORE-DatosEXplicabilidad", "Cache con datos insuficientes")
                        return listOf()
                    }
                    if (cacheTemp.size < 10 * model.fldNDuration) {
                        val nuevaMoveNetCache = mutableListOf<Person>()
                        for (i in 0 until cacheTemp.size - 1) {
                            val p1 = cacheTemp[i]
                            val p2 = cacheTemp[i + 1]
                            nuevaMoveNetCache.add(p1)
                            nuevaMoveNetCache.add(interpolarPersona(p1, p2))
                        }
                        nuevaMoveNetCache.add(cacheTemp.last())
                        cacheTemp = nuevaMoveNetCache
                    }
                    val resultsRaw: MutableList<Person> =
                        extractUniformElements(cacheTemp, model)
                    var acumuladoX = 0f
                    var acumuladoY = 0f
                    resultsRaw.forEachIndexed { indexA, person ->
                        val valores =
                            person.copy(keyPoints = normalizePoseKeypoint(person.keyPoints))
                        var sDato = 0f
                        if (it.fkSensor in 29..45) {
                            sDato = valores.keyPoints[it.fkSensor - 29].coordinate.y
                        } else
                            if (it.fkSensor in 7..23) {
                            sDato = valores.keyPoints[it.fkSensor - 7].coordinate.x
                        } else
                            if (it.fkSensor == 84) {
                            sDato = if (indexA == 0) {
                                0f
                            } else {
                                val x10 =
                                    resultsRaw[indexA - 1].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.x
                                val x20 =
                                    resultsRaw[indexA - 1].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.x
                                val x1f =
                                    resultsRaw[indexA].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.x
                                val x2f =
                                    resultsRaw[indexA].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.x
                                val x0 = (x20 + x10) / 2f
                                val xf = (x2f + x1f) / 2f
                                val alt0 =
                                    resultsRaw[indexA].keyPoints.minBy { puntos -> puntos.coordinate.y }.coordinate.y
                                val altf =
                                    resultsRaw[indexA].keyPoints.maxBy { puntos -> puntos.coordinate.y }.coordinate.y
                                val alt = abs(altf - alt0)
                                if (alt < 1f) {
                                    0f
                                } else {
                                    acumuladoX = acumuladoX + ((xf - x0) / alt)
                                    min(1f, max(-1f, acumuladoX))
                                }
                                //(person.keyPoints[it.fkSensor - 50].coordinate.x - resultsRaw[index - 1].keyPoints[it.fkSensor - 50].coordinate.x) / abs(person.keyPoints.find {puntos ->  puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.x - person.keyPoints.find { puntos ->  puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.x) / 420f
                            }
                        } else
                            if (it.fkSensor == 85) {
                            sDato = if (indexA == 0) {
                                0f
                            } else {
                                val y10 =
                                    resultsRaw[indexA - 1].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.y
                                val y20 =
                                    resultsRaw[indexA - 1].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.y
                                val y1f =
                                    resultsRaw[indexA].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.y
                                val y2f =
                                    resultsRaw[indexA].keyPoints.find { puntos -> puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.y
                                val y0 = (y20 + y10) / 2f
                                val yf = (y2f + y1f) / 2f
                                val alt0 =
                                    resultsRaw[indexA].keyPoints.minBy { puntos -> puntos.coordinate.y }.coordinate.y
                                val altf =
                                    resultsRaw[indexA].keyPoints.maxBy { puntos -> puntos.coordinate.y }.coordinate.y
                                val alt = abs(altf - alt0)
                                if (abs(alt) < 1f) {
                                    0f
                                } else {
                                    acumuladoY = acumuladoY + ((yf - y0) / alt)
                                    min(1f, max(-1f, acumuladoY))
                                }
                                //(person.keyPoints[it.fkSensor - 50].coordinate.x - resultsRaw[index - 1].keyPoints[it.fkSensor - 50].coordinate.x) / abs(person.keyPoints.find {puntos ->  puntos.bodyPart == BodyPart.RIGHT_HIP }!!.coordinate.x - person.keyPoints.find { puntos ->  puntos.bodyPart == BodyPart.LEFT_HIP }!!.coordinate.x) / 420f
                            }
                        } else
                            if (it.fkSensor in 111..120) {
                                sDato = valores.keyPoints[it.fkSensor - 94].coordinate.x
                            } else
                            if (it.fkSensor in 121..130) {
                                sDato = valores.keyPoints[it.fkSensor - 104].coordinate.y
                            }
                        data = data + Pair(sample, sDato)
                        sample += 1
                    }
                }else{
                    if(posicion != it.fkPosicion){
                        nSensor += 1
                        posicion = it.fkPosicion
                    }
                    val elemento = enumValues<TypeData>().toList()
                    val elem = elemento.find { it1 -> it1.id == it.fkSensor }
                    if (elem == null) {
                        Log.d("MMCORE-DatosEXplicabilidad", "Elemento ${it.fkSensor} no encontrado")
                        return listOf()
                    }
                    val datos = getDataCache(nSensor, elem.name, model.fldNDuration)
                    if (datos == null) {
                        Log.d("MMCORE-DatosEXplicabilidad", "Cache ${nSensor}:${elem.name} no encontrada")
                        return listOf()
                    }
                    if (datos.isEmpty()) {
                        Log.d("MMCORE-DatosEXplicabilidad", "Cache ${nSensor}:${elem.name} vacia")
                        return listOf()
                    }
                    datos.forEach { it1 ->
                        data = data + Pair(sample, it1.first)
                        sample += 1
                    }
                }
                datas.add(Triple(it.fkSensor, data, it.fkPosicion))
                Log.d("MMCORE-DatosEXplicabilidad", "${it.fkSensor}-${it.fkPosicion}: ${data.size}")
                data = emptyList()
            }
        }
        Log.d("MMCORE-DatosEXplicabilidad", "Modelo completo $index/${modelos.size}: ${datas.size}")
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

                override fun onOutputScores(outputScores: FloatArray, datasInferencia: List<Triple<Int, List<Pair<Int, Float>>, Int>>) {
                    val total = 100f
                    val salida: MutableList<Float> = mutableListOf()
                    outputScores.forEach { outputScore ->
                        salida.add(outputScore/total)
                    }
                    //Timber.d("Estado Inferencia: $outputScore - $estadoInferencia - $maxOutput")
                    if(salida.isNotEmpty()){
                        _motionDetectorFlow.value = null
                        _motionDetectorFlow.value = Pair(index, salida)
                        if(salida[0] >= maxExplicabilidad[index]){
                            maxExplicabilidad[index] = salida[0]
                            //explicabilidadImage[index] = getShaiImage(index)
                            //explicabilidad[index] = getShai(index)
                            explicabilidad[index] = datasInferencia
                            Log.d("MMCORE-Explicabilidad", "Explicabilidad añadida: ${explicabilidad[index].size}")
                        }
                        if(series[index].size >= 1) {
                            _dataInferedFlow.value = null
                            _dataInferedFlow.value = series[index][series[index].size / 2]
                            if(!optimizado) {
                                if (salida[0] < 0.5 && modelos[index].fldBRegresivo == 1) {
                                    if (series[index].isNotEmpty()) {
                                        motionDetector.second.inference(
                                            listOf(),
                                            series[index][series[index].size / 2],
                                            -1L
                                        )
                                        series[index].clear()
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onIntentRecognized(datosCaptura: List<DatosCaptura>) {
                    _resultsFlow.value = datosCaptura
                    _motionIntentFlow.value = null
                    _motionIntentFlow.value = index
                }

                override fun onIncorrectMotionRecognized(mensaje: String) {
                    _motionDetectorIncorrectFlow.value = null
                    _motionDetectorIncorrectFlow.value = if(mensaje.length > 1) mensaje else null
                }

                override fun onTimeCorrect(time: Float, datosCaptura: List<DatosCaptura>) {
                    _resultsFlow.value = datosCaptura
                    _motionDetectorTimeFlow.value = null
                    _motionDetectorTimeFlow.value = Pair(index, time)
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

                    override fun onOutputScores(outputScores: FloatArray, datasInferencia: List<Triple<Int, List<Pair<Int, Float>>, Int>>) {
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

                    override fun onTimeCorrect(time: Float, datosCaptura: List<DatosCaptura>) {

                    }

                    override fun onIntentRecognized(datosCaptura: List<DatosCaptura>) {

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
    private fun getVariabilidad(est: ObjetoEstadistica): Float{
        return (est.datos.maxOf { it1 -> it1.media } - est.datos.minOf { it1 -> it1.media })/est.datos.size
    }
    private fun recibeDatosCaptura(
        moveNetCacheCopy: List<Triple<Person, Person, LocalDateTime>>,
        modelosCopy: List<Model>
    ): List<DatosCaptura>{
        val cache = moveNetCacheCopy.map { it.first }.flatMap { it.keyPoints }.groupBy { it.bodyPart }
        val res = mutableListOf<DatosCaptura>()
        val duracionMax = if(modelosCopy.isNotEmpty()) modelosCopy.maxOf { it1 -> it1.fldNDuration } else 2
        TypeData.entries.forEach{
            res.add(DatosCaptura(it, null, mutableListOf()))
        }
        deviceManager.devices.forEach {
            it?.typeSensor?.Sensors?.forEachIndexed { index, sens ->
                val datos = it.getDataCache(index, duracionMax*sens.fs).map { it1 -> it1.first }
                res.first { it1 -> it1.sensor == sens }.valores = datos.takeLast(duracionMax*sens.fs).toMutableList()
            }
        }
        cache.forEach { (bp, kp) ->
            val typeDataX = TypeData.valueOf("${bp.name}_X")
            res.first { it1 -> it1.sensor == typeDataX }.valores = kp.map { it.coordinate.x }.takeLast(typeDataX.fs*duracionMax).toMutableList()
            val typeDataY = TypeData.valueOf("${bp.name}_Y")
            res.first { it1 -> it1.sensor == typeDataY }.valores = kp.map { it.coordinate.y }.takeLast(typeDataY.fs*duracionMax).toMutableList()
            val typeDataAng = TypeData.entries.find { it2 -> it2.name == "${bp.name}_ANGLE" }
            if(typeDataAng != null) {
                res.first { it1 -> it1.sensor == typeDataAng }.valores = kp.map { it.angle }.takeLast(typeDataAng.fs*duracionMax).toMutableList()
                Log.d("ANGULOS", "Angulo de ${bp.name}: ${kp.map { it.angle }}")
            }else{
                Log.d("ANGULOS", "Angulo de ${bp.name} no encontrado")
            }
        }
        return res
    }
    private suspend fun read(){
        while(true) {
            val modelosCopy = modelos.toList()
            val indicesCopy = indices.toList()
            val moveNetCacheCopy = moveNetCache.toList()
            val datosCaptura = recibeDatosCaptura(moveNetCacheCopy, modelosCopy)
            if (modelosStatus.filter { it1 -> it1 == 0 }.isNotEmpty()) {
                val inicioD = LocalDateTime.now()
                Log.d("MMCORE", "Preparando inferencia ${inferenceCounter[0]}")
                Log.d("MMCORE_REND", "Inferencia")
                val duracionMax = modelosCopy.maxOf { it1 -> it1.fldNDuration }
                val dispositivosCombinados = modelosCopy.flatMap { it1 -> it1.dispositivos }
                    .distinctBy { it1 -> Pair(it1.fkSensor, it1.fkPosicion) }
                val datas: MutableList<Triple<Int, Int, List<FloatArray>>> = mutableListOf()
                val poses =
                    dispositivosCombinados.map { it1 -> it1.fkPosicion }.filter { it2 -> it2 != 0 }
                        .distinct()
                val resultsRawNorma: MutableList<Person> =
                    extractUniformElements(
                        moveNetCacheCopy.map { it1 -> it1.first }.toMutableList(),
                        duracionMax
                    )
                val resultsRaw: MutableList<Person> =
                    extractUniformElements(
                        moveNetCacheCopy.map { it1 -> it1.second }.toMutableList(),
                        duracionMax
                    )
                //val datosPosInicial: MutableList<Triple<Int, Int, List<FloatArray>>> = mutableListOf()
                if(posicionInicialEstado != null && resultsRawNorma.size > 0) {
                    var datosPosInicial: Array<Array<Array<Array<FloatArray>>>> = Array(1){Array(1){Array(1){Array(26){FloatArray(1){0f} } } } }
                    val sensoresPosicion = listOf(7, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 29, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45)
                    sensoresPosicion.forEachIndexed { index, i ->
                        datosPosInicial[0][0][0][index][0] = when(i){
                            in 29..45 -> {
                                resultsRawNorma.last().keyPoints[i - 29].coordinate.y
                            }
                            in 7..23 -> {
                                resultsRawNorma.last().keyPoints[i - 7].coordinate.x
                            }
                            else -> {
                                0f
                            }
                        }
                    }
                    positionDetector.inference(datosPosInicial)
                }
                dispositivosCombinados.forEach { disp ->
                    var datos: MutableList<Float> = mutableListOf()
                    if (disp.fkPosicion != 0) {
                        val datosAux = getDataCache(
                            poses.indexOf(disp.fkPosicion),
                            TypeData.entries.first { it2 -> it2.id == disp.fkSensor }.name,
                            duracionMax
                        )?.map { it1 -> it1.first }
                        datos = if (datosAux != null) {
                            datosAux.toMutableList()
                        } else {
                            mutableListOf()
                        }
                    }
                    else {
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

                                in 111..120 -> {
                                    sDato = person.keyPoints[disp.fkSensor - 94].coordinate.x
                                }

                                in 121..130 -> {
                                    sDato = person.keyPoints[disp.fkSensor - 104].coordinate.y
                                }
                            }
                            datos.add(sDato)
                        }
                    }
                    datas.add(
                        Triple(
                            disp.fkSensor,
                            disp.fkPosicion,
                            datos.map { it1 -> FloatArray(1) { it1 } })
                    )
                }
                val finD = LocalDateTime.now()
                Log.d(
                    "MMCORE_REND",
                    "Preparar datos comun: ${Duration.between(inicioD, finD).toMillis()}"
                )
                modelosCopy.forEachIndexed { index, model ->
                    //modelos[1].let{model ->
                    //val index = 1
                    if (indicesCopy.contains(index)) {
                        Log.d("MMCORE_INF", "Media: $media")
                        if (media >= 0.2f || model.fkTipo != 2) {
                            modelosStatus[index] = 1
                            var minimoPunto = false
                            model.dispositivos.filter { it1 -> it1.fkPosicion == 0 }
                                .forEach { it1 ->
                                    when (it1.fkSensor) {
                                        in 29..45 -> {
                                            minimoPunto =
                                                minimoPunto || (mediasPuntos[it1.fkSensor - 29] < MIN_PUNTO)
                                        }

                                        in 7..23 -> {
                                            minimoPunto =
                                                minimoPunto || (mediasPuntos[it1.fkSensor - 7] < MIN_PUNTO)
                                        }

                                        in 111..120 -> {
                                            minimoPunto =
                                                minimoPunto || (mediasPuntos[it1.fkSensor - 94] < MIN_PUNTO)
                                        }

                                        in 121..130 -> {
                                            minimoPunto =
                                                minimoPunto || (mediasPuntos[it1.fkSensor - 104] < MIN_PUNTO)
                                        }
                                    }
                                }
                            Log.d("MMCORE_INF", "Min: $minimoPunto")
                            if (!minimoPunto) {
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
                                                datasPartial.takeLast(fr * model.fldNDuration)
                                                    .toTypedArray()
                                        } else {
                                            inferir = false
                                        }
                                    }
                                    datasList[indexFr][0] =
                                        datasetPartial[0].indices.map { colIndex ->
                                            datasetPartial.map { fila -> fila[colIndex] }
                                                .toTypedArray()
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
                                    motionDetectors[index].first.inference(
                                        datosCaptura,
                                        datasList,
                                        inferenceCounter[index],
                                        datas
                                            .filter { it1 ->
                                                model.dispositivos.map { it2 ->
                                                    Pair(it2.fkSensor, it2.fkPosicion)
                                                }.contains(Pair(it1.first, it1.second))
                                            }
                                            .map { it1 ->
                                                Triple(
                                                    it1.first,
                                                    it1.third.takeLast(TypeData.entries.first { it3 ->
                                                        it3.id == model.dispositivos.first { it2 ->
                                                            it1.first == it2.fkSensor && it1.second == it2.fkPosicion
                                                        }.fkSensor
                                                    }.fs * model.fldNDuration)
                                                        .mapIndexed { index2, it2 ->
                                                            Pair(
                                                                index2 + 1,
                                                                it2[0]
                                                            )
                                                        },
                                                    it1.second
                                                )
                                            }
                                    )
                                    inferenceCounter[index] = inferenceCounter[index] + 1
                                } else {
                                    Log.d(
                                        "MMCORE",
                                        "Abortando inferencia por falta de datos ${datasList.map { it1 -> it1.map { it2 -> it2.map { it3 -> it3.size } } }}"
                                    )
                                }
                            }
                            modelosStatus[index] = 0
                        }
                    }
                }
                Log.d("MMCORE", "Espera de siguiente inferencia")
            } else {
                Log.d("MMCORE", "Inferencia abortada por ningun motionDetector libre")
            }
            delay(sleepTime)
        }
    }

    fun onScanFailure() = deviceManager.scanFailureFlow
    fun onConnectionChange() = deviceManager.connectionChange
    fun onMotionDetectorChange() = motionDetectorFlow
    fun onPersonDetected() = personRawFlow
    fun onPersonsDetected() = personListFlow
    fun onObjectDetected() = objectsRawFlow
    fun onCacheDetector() = cacheDetectorFlow
    fun onMotionDetectorCorrectChange() = motionDetectorCorrectFlow
    fun onResultsDetector() = resultsFlow
    fun onExplicabilidadDatasChange() = explicabilidadDatasFlow
    fun onExplicabilidadChange() = explicabilidadFlow
    fun onMotionDetectorIncorrectChange() = motionDetectorIncorrectFlow
    fun getMotionDetectorPosicion() = motionDetectorPosicionFlow
    fun onMotionDetectorTimeChange() = motionDetectorTimeFlow
    fun onMotionDetectorIntentChange() = motionIntentFlow
    fun onSensorChange(index: Int, typedata: TypeData): StateFlow<Pair<Float, Int>> {
        Log.d("MMCORE", "TypeSensors ($index): $sensoresPosicion")
        val id = sensoresPosicion.indexOfFirst { it.posicion == index }
        deviceManager.devices[id]!!.sensorDatas[deviceManager.devices[id]!!.typeSensor.Sensors.indexOf(
            typedata
        )].enableFlow = true
        Log.d("LECTURA", "Habilitada")
        return deviceManager.devices[id]!!.sensorDatas[deviceManager.devices[id]!!.typeSensor.Sensors.indexOf(
            typedata
        )].dataFlow
    }
    fun onSensorChange(index: Int): StateFlow<List<Pair<Float, TypeData>>> {
        val id = sensoresPosicion.indexOfFirst { it.posicion == index }
        return deviceManager.devices[id]!!.groupedDataFlow
    }
    fun onDataInferedChange() = dataInferedFlow

    fun getExplicabilidad(index: Int): Pair<String, Int>{
        val UMBRAL_IMPORTANCIA = 0.5f
        val POSICION_QUIETO = 0.001f
        val UMBRAL_MOVIMIENTO = 0.1
        val resultados = getExplicabilidadDatas(index).filter { it1 -> (it1.posicion == 0) && (it1.sensor < 100) && (it1.sensor != 35 && it1.sensor != 34 && it1.sensor != 12 && it1.sensor != 13) } // Me quedo solo los de camara y solo los que no sean manos y/o pies
        val crecimientoX = estadisticas[index].filter { it1 -> it1.idPosicion == 84 }.flatMap{ it1 -> it1.datos.map { it2 -> it2.media }.take(it1.datos.size / 2) }.sum()
        val crecimientoY = estadisticas[index].filter { it1 -> it1.idPosicion == 85 }.flatMap{ it1 -> it1.datos.map { it2 -> it2.media }.take(it1.datos.size / 2) }.sum()
        var res = ""
        var resVal = 0
        if(resultados.isNotEmpty()){
            var resultado = resultados[0]
            val maximo = abs(resultado.valor) * UMBRAL_IMPORTANCIA
            val resultadosTorso = resultados.filter { it1 -> (it1.sensor == 84 || it1.sensor == 85) && abs(it1.valor) > maximo }
            if(resultadosTorso.isNotEmpty()){
                resultado = resultadosTorso[0]
                val maximo2 = abs(resultado.valor) * UMBRAL_IMPORTANCIA
                val posDesp = resultadosTorso.firstOrNull { it1 -> (it1.instante == 1 || it1.instante == 3) && abs(it1.valor) > maximo2 }
                if(posDesp != null){
                    resultado = posDesp
                }
            }
            Log.d("MMCORE-EXPLICABILIDAD", "Resultado explicabilidad: $resultado")
            res += when(resultado.sensor){
                7,8,9,10,11,29,30,31,32,33 -> getString(context, R.string.cabeza)
                12, 34 -> getString(context, R.string.hombroD)
                13, 35 -> getString(context, R.string.hombroI)
                14, 36 -> getString(context, R.string.brazoD)
                15, 37 -> getString(context, R.string.brazoI)
                16, 38 -> getString(context, R.string.manoD)
                17, 39 -> getString(context, R.string.manoI)
                18, 40 -> getString(context, R.string.caderaD)
                19, 41 -> getString(context, R.string.caderaI)
                20, 42 -> getString(context, R.string.piernaD)
                21, 43 -> getString(context, R.string.piernaI)
                22, 44 -> getString(context, R.string.pieD)
                23, 45 -> getString(context, R.string.pieI)
                84, 85 -> getString(context, R.string.cuerpo)
                else -> {""}
            }
            resVal += when(resultado.sensor){
                7,8,9,10,11,29,30,31,32,33 -> 1000
                12, 34 -> 2000
                13, 35 -> 300
                14, 36 -> 400
                15, 37 -> 500
                16, 38 -> 600
                17, 39 -> 700
                18, 40 -> 800
                19, 41 -> 900
                20, 42 -> 1000
                21, 43 -> 1100
                22, 44 -> 1200
                23, 45 -> 1300
                84, 85 -> 1400
                else -> {0}
            }
            res += " "+ when (resultado.sensor) {
                in 7..19 ->
                    if(resultado.correccion >= 1)
                        getString(context, R.string.MMCORE_cerca)
                    else
                        if(resultado.correccion > 0)
                            getString(context, R.string.MMCORE_lejos)
                        else
                            if(resultado.valor > 0) getString(context, R.string.MMCORE_derecha) else getString(context, R.string.MMCORE_izquierda)
                in 20..23 ->
                    if(resultado.correccion >= 1)
                        getString(context, R.string.MMCORE_dentro)
                    else
                        if(resultado.correccion > 0)
                            getString(context, R.string.MMCORE_fuera)
                        else
                            if(resultado.valor > 0) getString(context, R.string.MMCORE_derecha) else getString(context, R.string.MMCORE_izquierda)
                in 29..43 ->
                    if(resultado.valor > 0) getString(context, R.string.MMCORE_arriba)  else getString(context, R.string.MMCORE_abajo)
                    /*if(resultado.correccion >= 1)
                        getString(context, R.string.MMCORE_cerca)
                    else
                        if(resultado.correccion > 0)
                            getString(context, R.string.MMCORE_lejos)
                        else
                            if(resultado.valor > 0) getString(context, R.string.MMCORE_arriba)  else getString(context, R.string.MMCORE_abajo)*/
                in 44..45 ->
                    if(modelos[index].fldSEtiquetaPos == "Frontal") {
                        if (resultado.valor > 0) getString(context, R.string.MMCORE_atras) else getString(context, R.string.MMCORE_adelante)
                    }else{
                        if(resultado.valor > 0) getString(context, R.string.MMCORE_arriba)  else getString(context, R.string.MMCORE_abajo)
                    }
                84 -> if(abs(crecimientoX) > UMBRAL_MOVIMIENTO)
                        if(crecimientoX > 0) getString(context, R.string.izquierda) else getString(context, R.string.derecha)
                    else
                        if(resultado.valor > 0) getString(context, R.string.izquierda) else getString(context, R.string.derecha)
                85 -> if(abs(crecimientoY) > UMBRAL_MOVIMIENTO)
                        if(crecimientoY > 0) getString(context, R.string.arriba) else getString(context, R.string.abajo)
                    else
                        if(resultado.valor > 0) getString(context, R.string.arriba) else getString(context, R.string.abajo)
                else -> {""}
            }
            resVal += when (resultado.sensor) {
                in 7..19 ->
                    if(resultado.correccion >= 1)
                        50
                    else
                        if(resultado.correccion > 0)
                            60
                        else
                            if(resultado.valor > 0)
                                30
                            else
                                40
                in 20..23 ->
                    if(resultado.correccion >= 1)
                        70
                    else
                        if(resultado.correccion > 0)
                            80
                        else
                            if(resultado.valor > 0)
                                30
                            else
                                40
                in 29..43 ->
                    if(resultado.valor > 0)
                        10
                    else
                        20
                in 44..45 ->
                    if(modelos[index].fldSEtiquetaPos == "Frontal") {
                        if (resultado.valor > 0)
                            90
                        else
                            0
                    }else{
                        if(resultado.valor > 0)
                            10
                        else
                            20
                    }
                84 -> if(abs(crecimientoX) > UMBRAL_MOVIMIENTO)
                    if(crecimientoX > 0) 40 else 30
                else
                    if(resultado.valor > 0) 40 else 30
                85 -> if(abs(crecimientoY) > UMBRAL_MOVIMIENTO)
                    if(crecimientoY > 0) 10 else 20
                else
                    if(resultado.valor > 0) 10 else 20
                else -> {0}
            }
            res = when(resultado.instante){
                0 -> getString(context, R.string.inicioL) + " " + res
                1 -> if(abs(resultado.variabilidad) > POSICION_QUIETO)
                        getString(context, R.string.medio1)+" "+res+" "+getString(context, R.string.medio2)
                    else
                        getString(context, R.string.medio1a)+" "+res+" "+getString(context, R.string.medio2a)
                2 -> getString(context, R.string.finaliza)+" "+res
                3 -> if(abs(resultado.variabilidad) > POSICION_QUIETO)
                    getString(context, R.string.medio1)+" "+res
                    else
                        getString(context, R.string.medio1a)+" "+res
                else -> {""}
            }
            resVal += resultado.instante + 1
        }
        return Pair(res, resVal)
    }
    fun getExplicabilidadDatasAsinc(index: Int){
        scope.launch {
            _explicabilidadDatasFlow.value = getExplicabilidadDatas(index)
        }
    }
    fun getExplicabilidadAsinc(index: Int){
        scope.launch {
            _explicabilidadFlow.value = getExplicabilidad(index).first
        }
    }
    fun getExplicabilidadDatas(index: Int): List<ResultadoEstadistica>{ // Posicion, (Sensor, Instante, Valor)
        if(index >= estadisticas.size){
            return listOf()
        }
        val estadistica = estadisticas[index]
        val caderaEX = estadistica.firstOrNull{ d -> (d.id == 18 || d.id == 19) && d.idPosicion  == 0}
        val caderaRX = explicabilidad[index].firstOrNull{ d -> (d.first == 18 || d.first == 19) && d.third  == 0}
        val ajusteEscalaX = if(caderaEX != null && caderaRX != null){
            caderaEX.datos.map { it1 -> it1.media }.zip(caderaRX.second.map { it1 -> it1.second}){ e, r ->
                abs(e/r)
            }
        }else{
            listOf()
        }
        val caderaEY = estadistica.firstOrNull{ d -> (d.id == 34 || d.id == 35) && d.idPosicion  == 0}
        val caderaRY = explicabilidad[index].firstOrNull{ d -> (d.first == 34 || d.first == 35) && d.third  == 0}
        val ajusteEscalaY = if(caderaEY != null && caderaRY != null){
            caderaEY.datos.map { it1 -> it1.media }.zip(caderaRY.second.map { it1 -> it1.second}){ e, r ->
                abs(e/r)
            }
        }else{
            listOf()
        }
        val datos = explicabilidad[index].map { it1 ->
            if (it1.second.size == ajusteEscalaX.size && it1.first in listOf(7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,111,112,113,114,115,116,117,118,119,120)){
                Triple(it1.first, it1.second.mapIndexed { ind, pair ->  Pair(pair.first, pair.second * ajusteEscalaX[ind])}, it1.third)
            }else if (it1.second.size == ajusteEscalaY.size && it1.first in listOf(29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,121,122,123,124,125,126,127,128,129,130)){
                Triple(it1.first, it1.second.mapIndexed { ind, pair ->  Pair(pair.first, pair.second * ajusteEscalaY[ind])}, it1.third)
            }else{
                it1
            }
        }

        maxExplicabilidad[index] = 0f
        Log.d("MMCORE-Explicabilidad", "Resultados: ${estadistica.map { r -> "${r.id}-${r.idPosicion}"}} Datos:  ${datos.map { r -> "${r.first}-${r.third}"}}")
        val resultados: MutableList<Triple<Int, Pair<Float, MutableList<Pair<Float, Float>>>, Int>> = datos.map { d -> Triple(d.first, Pair(getVariabilidad(estadistica.first { e -> e.id == d.first && e.idPosicion == d.third }), MutableList(4){Pair(0f, 0f)}), d.third) }.toMutableList()
        resultados.forEach { res ->
            val dato = datos.firstOrNull { d -> d.first == res.first && d.third == res.third }
            if(dato != null) {
                val est = estadistica.first { e -> e.id == res.first && e.idPosicion == res.third }
                val fase1 = floor(dato.second.size / 4f).toInt()
                val fase2 = dato.second.size - fase1
                dato.second.forEachIndexed { index1, d ->
                    val e = est.datos[index1]
                    val a = d.second - e.media
                    var x1 = e.media
                    var x2 = d.second
                    if (x1 < 0) {
                        x1 *= -1
                        x2 *= -1
                    }
                    val correccion = (x2 / x1) / res.second.second.size
                    if (abs(a) > 0) {
                        val std = max(e.std, 0.01f)
                        if(std < abs(a)){
                            val b = (a/std)
                            val pos = if(index1 <= fase1) 0 else if(index1 >= fase2) 2 else 1
                            res.second.second[pos] = Pair(res.second.second[pos].first + (b / (if(pos == 2) 2f else 1f)), res.second.second[pos].second + correccion)
                            res.second.second[3] = Pair(res.second.second[3].first + (b / 4f), res.second.second[3].second + correccion)
                        }
                    }
                }
            }else{
                Log.e("MMCORE-Explicabilidad", "No match resultados y datos: resultados: ${estadistica.map { r -> r.id}} Datos:  ${datos.map { r -> r.first}}")
            }
        }
        val res = resultados.flatMapIndexed{ _, triple -> triple.second.second.mapIndexed{ index2, valor -> ResultadoEstadistica(sensor=triple.first, posicion=triple.third, instante=index2, valor=valor.first, variabilidad=triple.second.first, correccion=valor.second)}}.sortedByDescending { it1 -> abs(it1.valor) }
        return res
    }
    fun enableAllCache(enable: Boolean) =
        deviceManager.devices.forEach { dev ->
            dev?.enableAllCache(enable)
        }
    fun setExplicabilidad(index: Int, estadistic: List<ObjetoEstadistica>): Boolean{
        Log.d("MMCORE", "SET STADISTIC: ($index) ${estadistic.map { e -> "${e.id}-${e.idPosicion}" }}")
        if(index >= estadisticas.size){
            return false
        }
        estadisticas[index] = estadistic
        return true
    }
    fun setmodels(models: List<Model>): Boolean{
        stopMotionDetector()
        if(started){
            Log.e("MMCORE-SETMODELS", "Inferencias en curso")
            return false
        }
        estadisticas = MutableList(models.size){ listOf() }
        inferenceCounter.clear()
        motionDetectors.clear()
        series = mutableListOf()
        clearAllCache()
        clearMoveNetcache()
        _motionDetectorCorrectFlow.value = null
        _motionDetectorIncorrectFlow.value = null
        _explicabilidadDatasFlow.value = null
        _explicabilidadFlow.value = null
        _motionIntentFlow.value = null
        _motionDetectorTimeFlow.value = null
        _motionDetectorFlow.value = null
        _dataInferedFlow.value = null
        _personRawFlow.value = null
        //sensoresPosicion = mutableListOf()
        modelos = listOf()
        modelosStatus = mutableListOf()
        duration = max(1, models.maxOfOrNull { it1 -> it1.fldNDuration }?: 0)
        frecuencias = mutableListOf()
        cantidades = mutableListOf()
        setSensorsList(models)
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
                inferenceCounter.add(0L)
                Log.d("MMCORE", "Par detector creado")
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
            modelosStatus = MutableList(models.size){0}
            true
        }
    }
    fun setSensorsList(models: List<Model>){
        sensoresPosicionAux = mutableListOf()
        models.forEachIndexed { index1, model ->
            Log.e("MMCORE-SETMODELS", "Modelo $index1")
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
                        Log.d("MMCORE-SETMODELS", "Sensor $posicion: ${lista.map { it1 -> it1.name }}")
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
                Log.d("MMCORE-SETMODELS", "Sensor $posicion: ${lista.map { it1 -> it1.name }}")
            }
        }
        var desconectar = false
        if(sensoresPosicion.size == sensoresPosicionAux.size) {
            sensoresPosicion.forEachIndexed { index, sensorPosicion ->
                if (sensorPosicion.tipoSensor.size == sensoresPosicionAux[index].tipoSensor.size) {
                    sensorPosicion.tipoSensor.forEachIndexed { index1, typeSensor ->
                        desconectar = desconectar || (typeSensor != sensoresPosicionAux[index].tipoSensor[index1])
                    }
                }else{
                    desconectar = true
                }
            }
        }else{
            desconectar = true
        }
        sensoresPosicion = sensoresPosicionAux
        if(desconectar) {
            Log.d("MMCORE-SETMODELS", "Desconexion forzada por cambio de sensores")
            disconnectAll()
        }
    }
    fun setUmbrales(umbrales: List<Pair<Int, Int>>){
        umbrales.forEach {
            motionDetectors[it.first].first.ubralObjetivo = it.second
        }
    }
    fun setZonas(motionDetector: Int, zona: Int, valor: Int){
        when(zona){
            0 -> {
                motionDetectors[motionDetector].first.ubralObjetivo = valor
            }
            1 -> {
                motionDetectors[motionDetector].first.zonaA = valor
            }
            2 -> {
                motionDetectors[motionDetector].first.zonaB = valor
            }
            3 -> {
                motionDetectors[motionDetector].first.zonaC = valor
            }
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
                    Log.d("MMCORE-LOG", "MovenetCache Size: ${moveNetCache.size}")
                    /*if (moveNetCache.size < 10 * model.fldNDuration) {
                        return listOf()
                    }*/
                    val nuevaMovenetCache = if (moveNetCache.size < 10 * model.fldNDuration) {
                        val nuevaMoveNetCache = mutableListOf<Triple<Person, Person, LocalDateTime>>()
                        for(i in 0 until moveNetCache.size - 1){
                            nuevaMoveNetCache.add(moveNetCache[i])
                            nuevaMoveNetCache.add(interpolarCache(moveNetCache[i], moveNetCache[i+1]))
                        }
                        nuevaMoveNetCache.add(moveNetCache.last())
                        nuevaMoveNetCache
                    }else{
                        moveNetCache
                    }
                    val resultsRaw: MutableList<Person> =
                        extractUniformElements(nuevaMovenetCache.map{it1 -> it1.second}.toMutableList(), model)
                    var acumuladoX = 0f
                    var acumuladoY = 0f
                    resultsRaw.forEachIndexed { index, person ->
                        val valores =
                            person.copy(keyPoints = normalizePoseKeypoint(person.keyPoints))
                        var sDato = 0f
                        if (it.fkSensor in 29..45) {
                            sDato = valores.keyPoints[it.fkSensor - 29].coordinate.y
                        } else
                            if (it.fkSensor in 7..23) {
                            cam = cam + Triple(sample,person.keyPoints[it.fkSensor - 7].coordinate.x, person.keyPoints[it.fkSensor - 7].coordinate.y)
                            sDato = valores.keyPoints[it.fkSensor - 7].coordinate.x
                        } else
                            if (it.fkSensor == 84) {
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
                        } else
                            if (it.fkSensor == 85) {
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
                        } else
                            if (it.fkSensor in 111 .. 120){
                                cam = cam + Triple(sample,person.keyPoints[it.fkSensor - 94].coordinate.x, person.keyPoints[it.fkSensor - 94].coordinate.y)
                                sDato = valores.keyPoints[it.fkSensor - 94].coordinate.x
                            } else
                            if (it.fkSensor in 121 .. 130){
                                sDato = valores.keyPoints[it.fkSensor - 104].coordinate.y
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
        positionDetector.start()
        currentJob = scope.launch {
            delay((duration * 1000).toLong())
            read()
        }
    }
    fun stopMotionDetector(){
        started = false
        positionDetector.stop()
        motionDetectors.forEach { motionDetector ->
            motionDetector.first.stop()
            motionDetector.second.stop()
        }
        modelosStatus = MutableList(modelosStatus.size){0}
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
    fun disconnectAll(){
        deviceManager.disconnectAll()
    }
    fun getSensorType(index: Int): TypeSensor =
        deviceManager.getSensorType(index)
    fun startConnectDevice(posicion: Int) {
        if(posicion < sensoresPosicion.size){
            val listaSensoresCombinados = modelos.flatMap { it1 -> it1.dispositivos.map { it2 -> if(it2.fkPosicion == sensoresPosicion[posicion].posicion) it2.fkSensor else null } }.filterNotNull().distinct()
            startConnectDevice(sensoresPosicion[posicion].tipoSensor, posicion, GenericDevice.EnableSensors(
                mpu = listaSensoresCombinados.any { it1 -> it1 in listOf(1, 2, 3, 4, 5, 6, 102) },
                emg = listaSensoresCombinados.any { it1 -> it1 in listOf(24, 25, 26, 27) },
                hr = listaSensoresCombinados.any { it1 -> it1 in listOf(101) }
            ))
        }
    }
    fun stopConnectDevice(){
        deviceManager.buscando = false
        deviceManager.sensores = listOf()
        deviceManager.indexConn = null
        deviceManager.stopScan()
    }
    fun setFrontCamera(front: Boolean){
        frontCamera = front
    }
    fun updateImage(imageProxy: ImageProxy){
        /*val bitmapBuffer =
            Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
        objetDetector.detectLivestreamFrame(imageProxy=imageProxy, bitmapBuffer = bitmapBuffer)
        moveNet.detectLiveStream(
            imageProxy = imageProxy,
            isFrontCamera = frontCamera,
            bitmapBuffer = bitmapBuffer
        )*/
        finalBitmap = imageProxy.toBitmap(context)
        addImage(finalBitmap)
    }
    fun setRotacion(degrees: Float){
        rotacion = degrees
    }
    fun addImage(bitmap: Bitmap, enableRotation: Boolean = true) {
        finalBitmap = if (!frontCamera) {
            if (enableRotation) {
                rotateBitmap(bitmap, 90f + rotacion, true)
            } else {
                rotateBitmap(bitmap, rotacion, true)
            }
        } else {
            if (enableRotation) {
                rotateBitmap(bitmap, 270f + (360 - rotacion), true)
            } else {
                rotateBitmap(bitmap, (360 - rotacion), false)
            }
        }
        //objetDetector.detectLivestreamFrame(imageProxy=imageProxy, bitmapBuffer = bitmapBuffer)
        moveNet.detectLiveStreamBitmap(
            bitmap = finalBitmap
        )
        if(objetDetector.objectLabels.size >= 1 && libreInfObj) {
            libreInfObj = false
            Log.d("OBJECTINFERENCE", "Object inference ocupado")
            objetDetector.detectLiveStreamImage(
                bitmap = finalBitmap
            )
        }
        /*bitmapCache.add(Pair(finalBitmap, LocalDateTime.now()))
        val aborrar = bitmapCache.filter { it1 -> it1.second < LocalDateTime.now().minusSeconds(duration*2L) }.size
        for(i in 0 until aborrar){
            bitmapCache.removeAt(0)
        }*/
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
                sensores.contains(TypeData.AI) ||
                sensores.contains(TypeData.ANGULO_ROTACION) ||
                sensores.contains(TypeData.ANGULO_FLEXION) ||
                sensores.contains(TypeData.ANGULO_FLEXION)
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
    fun correccionesIniciales(index: Int): Boolean{
        if(posicionInicialEstado == null){
            posicionInicialEstado = index
            return true
        }else{
            return false
        }
    }
    fun setObjetsLabels(lista: List<ObjetLabel>){
        objetDetector.setObjetsLabels(lista)
        objetDetectorAsync.setObjetsLabels(lista)
    }
    fun addObjetLabel(objeto: ObjetLabel){
        objetDetector.addObjetLabel(objeto)
        objetDetectorAsync.addObjetLabel(objeto)
    }
    fun getCacheObjects() = objectsCache.toList()
    /*fun getCacheInference(){
        scope.launch {
            val res = mutableListOf<Pair<Person, List<Objeto>>>()
            bitmapCache.forEach { bm ->
                val person = moveNetAsync.estimateSinglePose(bm.first)
                val objects = objetDetectorAsync.estimateObjects(bm.first)
                res.add(Pair(person, objects))
            }
            _cacheDetectorFlow.value = res
        }
    }*/

    override fun onResultsPersons(resultList: List<Person>) {
        if(resultList.size > 0) {
            val result = resultList[0]
            val instante = LocalDateTime.now()
            _personRawFlow.value = result
            _personListFlow.value = resultList
            media = 0f
            for (i in 0 until listaMedia.size - 1) {
                listaMedia[i] = listaMedia[i + 1]
            }
            listaMedia[listaMedia.size - 1] = result.score
            media = listaMedia.average().toFloat()
            result.keyPoints.forEach {
                it.coordinate.x = 480 - it.coordinate.x
            }
            result.keyPoints.forEachIndexed { j, kp ->
                for (i in 0 until listasMediasPuntos[j].size - 1) {
                    listasMediasPuntos[j][i] = listasMediasPuntos[j][i + 1]
                }
                listasMediasPuntos[j][listasMediasPuntos[j].size - 1] = kp.score
                mediasPuntos[j] = listasMediasPuntos[j].average().toFloat()
            }
            val resultRaw = result.copy(keyPoints = normalizePoseKeypoint(result.keyPoints))
            moveNetCache.add(Triple(resultRaw, result, instante))
            var i = 0
            var continuar = true
            while (i < moveNetCache.size && continuar) {
                if (Duration.between(moveNetCache[i].third, instante)
                        .toMillis() <= duration * 1000f
                ) {
                    i--
                    continuar = false
                }
                i++
            }
            if (!continuar) {
                Log.d(
                    "MMCORE-CACHE",
                    "Borrados $i elementos por exceder ${
                        Duration.between(
                            moveNetCache[i].third,
                            instante
                        ).toMillis() / 1000f
                    }: ${moveNetCache.size}"
                )
                moveNetCache = moveNetCache.takeLast(moveNetCache.size - i).toMutableList()
            }
        }
    }
    override fun onResults(result: List<Objeto>) {
        _objectsRawFlow.value = result
        /*val instante = LocalDateTime.now()
        if(objectsCache.size > 0) {
            val ultimo = objectsCache.last()
            val periodo = floor(Duration.between(ultimo.second, instante).toMillis() / 100.0).toInt()
            val incrementos = ultimo.first.map { obj ->
                val resultadoAct = result.firstOrNull { it1 -> it1.label == obj.label }
                if(resultadoAct != null){
                    RectF((obj.point.left - resultadoAct.point.left) / periodo,
                        (obj.point.top - resultadoAct.point.top) / periodo,
                        (obj.point.right - resultadoAct.point.right) / periodo,
                        (obj.point.bottom - resultadoAct.point.bottom) / periodo)
                }else{
                    RectF(0f, 0f, 0f, 0f)
                }
            }
            for(i in 1 .. periodo){
                val res = mutableListOf<Objeto>()
                ultimo.first.forEachIndexed { index, obj ->
                    val rect = RectF(
                        obj.point.left + (incrementos[index].left * i),
                        obj.point.top + (incrementos[index].top * i),
                        obj.point.right + (incrementos[index].right * i),
                        obj.point.bottom + (incrementos[index].bottom * i))
                    res.add(Objeto(obj.label, rect, obj.umbral))
                }
                objectsCache.add(Pair(res, instante.plus(Duration.ofMillis( 100L*i ))))
            }
        }
        objectsCache.add(Pair(result, instante))
        val aborrar = objectsCache.filter { it1 -> it1.second < LocalDateTime.now() - Duration.ofSeconds((duration*2).toLong()) }.size
        for(i in 0 until aborrar) {
            objectsCache.removeAt(0)
        }*/
        libreInfObj = true
    }
    override fun onError(error: String, errorCode: Int) {
        Log.e("MMCORE-ERROR", "$errorCode: $error")
    }
    override fun onErrorObject(error: String, errorCode: Int) {
        Log.e("MMCORE-ERROR", "$errorCode: $error")
    }
}