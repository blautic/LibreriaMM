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
import androidx.work.Data
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
import com.example.libreriamm.entity.ResultadoEstadistica
import com.example.libreriamm.motiondetector.MotionDetector
import com.example.libreriamm.sensor.SensorsManager
import com.example.libreriamm.sensor.TypeData
import com.example.libreriamm.sensor.TypeSensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
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
    private var mediasPuntos = MutableList(17){0f}
    private var listasMediasPuntos = MutableList(17) { MutableList(5) { 0f } }
    private val moveNet = MoveNet.create(context, Device.CPU, ModelType.Lightning)
    private var finalBitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
    private var modelos: List<Model> = listOf()
    private var modelosStatus: MutableList<Int> = mutableListOf()
    private var frecuencias: MutableList<MutableList<Int>> = mutableListOf()
    private var cantidades: MutableList<MutableList<Int>> = mutableListOf()
    private val _motionDetectorCorrectFlow = MutableStateFlow<Pair<Int, Float>?>(null)
    private val motionDetectorCorrectFlow get() = _motionDetectorCorrectFlow.asStateFlow()
    private val _motionDetectorIncorrectFlow = MutableStateFlow<String?>(null)
    private val motionDetectorIncorrectFlow get() = _motionDetectorIncorrectFlow.asStateFlow()
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
    private val sensorFlow get() = _sensorFlow
    private val scope = CoroutineScope(coroutineContext)
    private var currentJob: Job? = null
    private var started = false
    private var series: MutableList<MutableList<Array<Array<Array<Array<FloatArray>>>>>> = mutableListOf()
    private var estadisticas: MutableList<List<ObjetoEstadistica>> = mutableListOf()
    private var explicabilidad: MutableList<List<Triple<Int, List<Pair<Int, Float>>, Int>>> = mutableListOf()
    private var maxExplicabilidad: MutableList<Float> = mutableListOf()
    private var indices: List<Int> = listOf()
    private var posicionInicialEstado: Int? = null



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
                        } else if (it.fkSensor in 7..23) {
                            sDato = valores.keyPoints[it.fkSensor - 7].coordinate.x
                        } else if (it.fkSensor == 84) {
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
                        } else if (it.fkSensor == 85) {
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
                                        motionDetector.second.inference(series[index][series[index].size / 2], -1L)
                                        series[index].clear()
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onIntentRecognized() {
                    _motionIntentFlow.value = null
                    _motionIntentFlow.value = index
                }

                override fun onIncorrectMotionRecognized(mensaje: String) {
                    _motionDetectorIncorrectFlow.value = null
                    _motionDetectorIncorrectFlow.value = if(mensaje.length > 1) mensaje else null
                }

                override fun onTimeCorrect(time: Float) {
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

                    override fun onTimeCorrect(time: Float) {

                    }

                    override fun onIntentRecognized() {

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
        if(modelosStatus.filter { it1 -> it1 == 0 }.isNotEmpty()) {
            val inicioD = LocalDateTime.now()
            Log.d("MMCORE", "Preparando inferencia ${inferenceCounter[0]}")
            Log.d("MMCORE_REND", "Inferencia")
            val duracionMax = modelos.maxOf { it1 -> it1.fldNDuration }
            val dispositivosCombinados = modelos.flatMap { it1 -> it1.dispositivos }
                .distinctBy { it1 -> Pair(it1.fkSensor, it1.fkPosicion) }
            val datas: MutableList<Triple<Int, Int, List<FloatArray>>> = mutableListOf()
            val poses =
                dispositivosCombinados.map { it1 -> it1.fkPosicion }.filter { it2 -> it2 != 0 }
                    .distinct()
            val resultsRawNorma: MutableList<Person> =
                extractUniformElements(moveNetCache.map{it1 -> it1.first}.toMutableList(), duracionMax)
            val resultsRaw: MutableList<Person> =
                extractUniformElements(moveNetCache.map{it1 -> it1.second}.toMutableList(), duracionMax)
            dispositivosCombinados.forEach { disp ->
                var datos: MutableList<Float> = mutableListOf()
                if (disp.fkPosicion != 0) {
                    val datosAux = getDataCache(
                        poses.indexOf(disp.fkPosicion),
                        TypeData.entries.first { it2 -> it2.id == disp.fkSensor }.name,
                        duracionMax
                    )?.map { it1 -> it1.first }
                    if (datosAux != null) {
                        datos = datosAux.toMutableList()
                    } else {
                        datos = mutableListOf()
                    }
                } else {
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
            modelos.forEachIndexed { index, model ->
                //modelos[1].let{model ->
                //val index = 1
                if (indices.contains(index)) {
                    if (media >= 0.2f || model.fkTipo != 2) {
                        modelosStatus[index] = 1
                        var minimoPunto = false
                        model.dispositivos.filter { it1 -> it1.fkPosicion == 0 }.forEach { it1 ->
                            when (it1.fkSensor) {
                                in 29..45 -> {
                                    minimoPunto =
                                        minimoPunto || (mediasPuntos[it1.fkSensor - 29] < MIN_PUNTO)
                                }

                                in 7..23 -> {
                                    minimoPunto =
                                        minimoPunto || (mediasPuntos[it1.fkSensor - 7] < MIN_PUNTO)
                                }
                            }
                        }
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
                                motionDetectors[index].first.inference(
                                    datasList,
                                    inferenceCounter[index],
                                    datas
                                        .filter { it1 ->
                                            model.dispositivos.map { it2 ->
                                                Pair(it2.fkSensor, it2.fkPosicion)
                                            }.contains(Pair(it1.first, it1.second)) }
                                        .map{it1 ->
                                            Triple(
                                                it1.first,
                                                it1.third.takeLast(TypeData.entries.first { it3 ->
                                                    it3.id == model.dispositivos.first { it2 ->
                                                        it1.first == it2.fkSensor && it1.second == it2.fkPosicion
                                                    }.fkSensor
                                                }.fs * model.fldNDuration).mapIndexed { index2, it2 -> Pair(index2+1, it2[0]) },
                                                it1.second)
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
        }
        else{
            Log.d("MMCORE", "Inferencia abortada por ningun motionDetector libre")
        }
        delay(sleepTime)
        read()
    }
    private suspend fun correccionInicial(){
        if(posicionInicialEstado != null) {
            val objetivo = Person()
            var keyPoints: List<KeyPoint> = mutableListOf()
            val lastCachePerson = moveNetCache.last().first
            val datos = estadisticas[posicionInicialEstado!!].filter { it1 -> it1.idPosicion == 0 }.map { it1 -> Pair(it1.id, it1.datos.first()) }
            BodyPart.entries.forEach { bp ->
                val idSensor = when(bp){
                    BodyPart.NOSE -> Pair(7, 29)
                    BodyPart.LEFT_EYE -> Pair(8, 30)
                    BodyPart.RIGHT_EYE -> Pair(9, 31)
                    BodyPart.LEFT_EAR -> Pair(10, 32)
                    BodyPart.RIGHT_EAR -> Pair(11, 33)
                    BodyPart.LEFT_SHOULDER -> Pair(12, 34)
                    BodyPart.RIGHT_SHOULDER -> Pair(13, 35)
                    BodyPart.LEFT_ELBOW -> Pair(14, 36)
                    BodyPart.RIGHT_ELBOW -> Pair(15, 37)
                    BodyPart.LEFT_WRIST -> Pair(16, 38)
                    BodyPart.RIGHT_WRIST -> Pair(17, 39)
                    BodyPart.LEFT_HIP -> Pair(18, 40)
                    BodyPart.RIGHT_HIP -> Pair(19, 41)
                    BodyPart.LEFT_KNEE -> Pair(20, 42)
                    BodyPart.RIGHT_KNEE -> Pair(21, 43)
                    BodyPart.LEFT_ANKLE -> Pair(22, 44)
                    BodyPart.RIGHT_ANKLE -> Pair(23, 45)
                }

            }
            objetivo.keyPoints = keyPoints
            // val objetivo = estadisticas[posicionInicialEstado!!]
            _explicabilidadFlow.value = ""
            delay(1000)
            correccionInicial()
        }
    }

    fun getExplicabilidad(index: Int): String{
        val UMBRAL_IMPORTANCIA = 0.6f
        val resultados = getExplicabilidadDatas(index).filter { it1 -> it1.posicion == 0 }
        var res = ""
        if(resultados.isNotEmpty()){
            var resultado = resultados[0]
            /*val posDesp = resultados.firstOrNull { it1 -> it1.sensor == 84 || it1.sensor == 85 }
            if(posDesp != null){
                if(abs(posDesp.valor) > (UMBRAL_IMPORTANCIA * abs(resultado.valor))){
                    resultado = posDesp
                }
            }*/
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
            res += " "+ when (resultado.sensor) {
                in 7..23 -> if(resultado.valor > 0) getString(context, R.string.derecha) else getString(context, R.string.izquierda)
                in 29..45 -> if(resultado.valor > 0) getString(context, R.string.arriba)  else getString(context, R.string.abajo)
                84 -> if(resultado.valor > 0) getString(context, R.string.derecha) else getString(context, R.string.izquierda)
                85 -> if(resultado.valor > 0) getString(context, R.string.arriba) else getString(context, R.string.abajo)
                else -> {""}
            }
            res = when(resultado.instante){
                0 -> getString(context, R.string.inicioL) + " " + res
                1 -> getString(context, R.string.medio1)+" "+res+" "+getString(context, R.string.medio2)
                2 -> getString(context, R.string.finaliza)+" "+res
                3 -> getString(context, R.string.medio1)+" "+res
                else -> {""}
            }
        }
        return res
    }
    fun getExplicabilidadDatasAsinc(index: Int){
        scope.launch {
            _explicabilidadDatasFlow.value = getExplicabilidadDatas(index)
        }
    }
    fun getExplicabilidadAsinc(index: Int){
        scope.launch {
            _explicabilidadFlow.value = getExplicabilidad(index)
        }
    }
    fun getExplicabilidadDatas(index: Int): List<ResultadoEstadistica>{ // Posicion, (Sensor, Instante, Valor)
        if(index >= estadisticas.size){
            return listOf()
        }
        val datos = explicabilidad[index]
        maxExplicabilidad[index] = 0f
        Log.d("MMCORE-Explicabilidad", "Resultados: ${estadisticas[index].map { r -> "${r.id}-${r.idPosicion}"}} Datos:  ${datos.map { r -> "${r.first}-${r.third}"}}")
        val resultados: MutableList<Triple<Int, MutableList<Float>, Int>> = datos.map { d -> Triple(d.first, MutableList(4){0f}, d.third) }.toMutableList()
        resultados.forEach { res ->
            val dato = datos.firstOrNull { d -> d.first == res.first && d.third == res.third }
            if(dato != null) {
                val est = estadisticas[index].first { e -> e.id == res.first && e.idPosicion == res.third }
                val fase1 = floor(dato.second.size / 4f).toInt()
                val fase2 = dato.second.size - fase1
                dato.second.forEachIndexed { index1, d ->
                    val e = est.datos[index1]
                    val a = d.second - e.media
                    if (abs(a) > 0) {
                        val std = max(e.std, 0.01f)
                        if(std < abs(a)){
                            val b = (a/std)
                            val pos = if(index1 <= fase1) 0 else if(index1 >= fase2) 2 else 1
                            res.second[pos] += b / (if(pos == 2) 2f else 1f)
                            res.second[3] += b / 4f
                        }
                    }
                }
            }else{
                Log.e("MMCORE-Explicabilidad", "No match resultados y datos: resultados: ${estadisticas[index].map { r -> r.id}} Datos:  ${datos.map { r -> r.first}}")
            }
        }
        val res = resultados.flatMapIndexed{index1, triple -> triple.second.mapIndexed{index2, valor -> ResultadoEstadistica(sensor=triple.first, posicion=triple.third, instante=index2, valor=valor)}}.sortedByDescending { it1 -> abs(it1.valor) }
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
        duration = max(1, models.maxOf { it1 -> it1.fldNDuration })
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
                    if (moveNetCache.size < 10 * model.fldNDuration) {
                        return listOf()
                    }
                    /*if (moveNetCache.size < 10 * model.fldNDuration) {
                        val nuevaMoveNetCache = mutableListOf<Person>()
                        for(i in 0 until moveNetCache.size - 1){
                            val p1 = moveNetCache[i].second
                            val p2 = moveNetCache[i+1].second
                            nuevaMoveNetCache.add(p1)
                            nuevaMoveNetCache.add(interpolarPersona(p1, p2))
                        }
                        nuevaMoveNetCache.add(moveNetCacheRaw.last())
                        moveNetCacheRaw = nuevaMoveNetCache
                    }*/
                    val resultsRaw: MutableList<Person> =
                        extractUniformElements(moveNetCache.map{it1 -> it1.second}.toMutableList(), model)
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
    fun onExplicabilidadDatasChange() = explicabilidadDatasFlow
    fun onExplicabilidadChange() = explicabilidadFlow
    fun onMotionDetectorIncorrectChange() = motionDetectorIncorrectFlow
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
    fun onDataInferedChange() = dataInferedFlow
    fun setFrontCamera(front: Boolean){
        frontCamera = front
    }
    fun updateImage(imageProxy: ImageProxy){
        finalBitmap = imageProxy.toBitmap(context)
        addImage(finalBitmap)
    }
    fun setRotacion(degrees: Float){
        rotacion = degrees
    }
    fun addImage(bitmap: Bitmap){
        val instante = LocalDateTime.now()
        finalBitmap = if (!frontCamera) {
            rotateBitmap(bitmap, 90f+rotacion, true)
        } else {
            rotateBitmap(bitmap, 270f+(360-rotacion), true)
        }
        val result = moveNet.estimateSinglePose(finalBitmap)
        _personRawFlow.value = result
        media = 0f
        for(i in 0 until listaMedia.size-1){
            listaMedia[i] = listaMedia[i+1]
        }
        listaMedia[listaMedia.size-1] = result.score
        media = listaMedia.average().toFloat()
        result.keyPoints.forEach {
            it.coordinate.x = 480 - it.coordinate.x
        }
        result.keyPoints.forEachIndexed { j, kp ->
            for(i in 0 until listasMediasPuntos[j].size-1){
                listasMediasPuntos[j][i] = listasMediasPuntos[j][i+1]
            }
            listasMediasPuntos[j][listasMediasPuntos[j].size - 1] = kp.score
            mediasPuntos[j] = listasMediasPuntos[j].average().toFloat()
        }
        val resultRaw = result.copy(keyPoints = normalizePoseKeypoint(result.keyPoints))
        moveNetCache.add(Triple(resultRaw, result, instante))
        var i = 0
        var continuar = true
        while(i < moveNetCache.size && continuar){
            if(Duration.between(moveNetCache[i].third, instante).toMillis() <= duration*1000f){
                i--
                continuar = false
            }
            i++
        }
        if(!continuar){
            Log.d("MMCORE-CACHE", "Borrados $i elementos por exceder ${Duration.between(moveNetCache[i].third, instante).toMillis() / 1000f}: ${moveNetCache.size}")
            for(j in 0 until i){
                if(moveNetCache.size > 1) {
                    moveNetCache.removeAt(0)
                }
            }
        }
        /*if(moveNetCache.size < 10*duration && moveNetCache.size > 5*duration){
            Log.d("MMCORE-CACHE", "Generando duplicados ${moveNetCache.size}/${10*duration}")
            val newMovenetCache = mutableListOf<Triple<Person, Person, LocalDateTime>>()
            for(j in 0 until (moveNetCache.size-1)){
                newMovenetCache.add(Triple(moveNetCache[j].first, moveNetCache[j].second, moveNetCache[j].third))
                newMovenetCache.add(Triple(
                    interpolarPersona(moveNetCache[j].first, moveNetCache[j+1].first),
                    interpolarPersona(moveNetCache[j].second, moveNetCache[j+1].second),
                    moveNetCache[j].third.plusNanos(Duration.between(moveNetCache[j].third, moveNetCache[j+1].third).nano.toLong() / 2L)))
            }
            newMovenetCache.add(Triple(moveNetCache.last().first, moveNetCache.last().second, moveNetCache.last().third))
            moveNetCache = newMovenetCache
        }*/
        //Log.d("MMCORE-MOVENETCACHE", "Size: ${moveNetCache.size}/${moveNetCacheRaw.size} ${tiempos[0]}")
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
    fun correccionesIniciales(index: Int): Boolean{
        if(index >= estadisticas.size){
            return false
        }
        if(estadisticas[index].isEmpty()){
            return false
        }
        if(posicionInicialEstado == null){
            posicionInicialEstado = index
            currentJob = scope.launch {
                correccionInicial()
            }
            return true
        }else{
            return false
        }
    }
}