package com.example.libreriamm.motiondetector

import android.content.Context
import android.util.Log
import com.example.libreriamm.camara.Person
import com.example.libreriamm.entity.Model
import com.google.firebase.FirebaseApp
import com.google.firebase.ml.modeldownloader.CustomModel
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions
import com.google.firebase.ml.modeldownloader.DownloadType
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import timber.log.Timber
import java.nio.FloatBuffer

data class MoveNetData(
    var pointX: Float = 0f,
    var pointY: Float = 0f,
    var position: Int = 0,
    var sample: Int
)

class MotionDetector(private val model: Model, private val tipo: Int) {

    interface MotionDetectorListener {
        fun onCorrectMotionRecognized(correctProb: Float, datasList: Array<Array<Array<Array<FloatArray>>>>)
        fun onOutputScores(outputScores: FloatArray)
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var currentJob: Job? = null

    private val lock = Any()

    //TODO AHORA SON 10 MUESTRAS CADA SEGUNDO
    private val GESTURE_SAMPLES: Int = model.fldNDuration * 10

    private val NUM_DEVICES: Int = if(model.devices.filter{it.position.id != 0}.isNotEmpty()) model.devices.size else 1

    private var motionDetectorListener: MotionDetectorListener? = null

    private val outputScores: Array<FloatArray> = Array(1) { FloatArray(model.movements.size) }
    //private val outputScores: Map<Int?, Any?> = mapOf(Pair(0, 0f), Pair(0, 0f))
    private val recordingData: Array<ArrayQueue?> =
        Array(1) { ArrayQueue(GESTURE_SAMPLES * NUM_CHANNELS) }

    private var inferenceInterface: Interpreter? = null
    private var options: Interpreter.Options? = null

    var isStarted = false
        private set

    var selectMovIndex = 0

    fun setMotionDetectorListener(motionDetectorListener: MotionDetectorListener?) {
        this.motionDetectorListener = motionDetectorListener
    }

    fun start() {
        val conditions = CustomModelDownloadConditions.Builder().build()
        FirebaseModelDownloader.getInstance()
            .getModel((if(tipo == 0) "mm_" else "mmr_") + model.id.toString(), DownloadType.LATEST_MODEL, conditions)
            .addOnSuccessListener { customModel: CustomModel ->
                val modelFile = customModel.file
                Log.d("MMCORE", "Temporary file ${modelFile != null} - ${modelFile?.absolutePath}")
                modelFile?.let { file ->
                    Log.d("MMCORE", "Inicializando ${file.name}")
                    currentJob = coroutineScope.launch {
                        synchronized(lock) {
                            Log.d("MMCORE", "Lock adquired")
                            val compatList = CompatibilityList()
                            options = Interpreter.Options()
                            Log.d("MMCORE", "Options create ${options != null}")
                            if (compatList.isDelegateSupportedOnThisDevice) {
                                Log.d("MMCORE", "GPU Accelerate available")
                                val delegateOptions = compatList.bestOptionsForThisDevice
                                options!!.addDelegate(GpuDelegate(delegateOptions))
                                Log.d("MMCORE", "GPU Accelerate available ready")
                            } else {
                                Log.d("MMCORE", "GPU Accelerate not available")
                                // Fallback to CPU execution if GPU acceleration is not available
                                options!!.setNumThreads(NUM_LITE_THREADS)
                                Log.d("MMCORE", "GPU Accelerate not available ready")
                            }
                            Log.d("MMCORE", "Creating interpreter...")

                            inferenceInterface = Interpreter(file, options)
                            isStarted = true
                            Log.d("MMCORE", "Started resolve: ${isStarted}")
                        }
                    }
                }
            }.addOnFailureListener { t: Exception? -> Timber.e(t) }
    }

    fun stop() {
        if(currentJob!= null) {
            currentJob!!.cancel()
        }
        synchronized(lock) {
            isStarted = false
        }
        inferenceInterface?.let {
            it.close()
            inferenceInterface = null
        }
    }

    fun inference(datasList: Array<Array<Array<Array<FloatArray>>>>) {
        synchronized(lock) {
            Log.d("MMCORE", "inferencia activa... $isStarted")
            inferenceInterface?.takeIf { isStarted }?.let { interpreter ->
                if(tipo == 0) {
                    Log.d("MMCORE", "Calculando inferencia tipo 1")
                    var mapOfIndicesToOutputs: Map<Int, Array<FloatArray>> =
                        mapOf(0 to arrayOf(floatArrayOf(0f, 0f)))
                    interpreter.runForMultipleInputsOutputs(datasList, mapOfIndicesToOutputs)
                    //Log.d("Resultados", "${mapOfIndicesToOutputs[0]?.get(0)?.get(0)} || ${mapOfIndicesToOutputs[0]?.get(0)?.get(1)}")
                    var totalProb = 0f
                    mapOfIndicesToOutputs[0]?.get(0)?.forEach { prob -> totalProb += prob }
                    var scores = FloatArray(model.movements.size)
                    for (i in 0 until model.movements.size) {
                        scores[i] =
                            ((mapOfIndicesToOutputs[0]?.get(0)?.get(i) ?: 0f) * 100f) / totalProb
                    }
                    if(model.fldSName > "Other"){
                        scores = scores.reversedArray()
                    }
                    if(scores[0] > 80){
                        motionDetectorListener?.onCorrectMotionRecognized(scores[0], datasList)
                    }
                    motionDetectorListener?.onOutputScores(scores)
                }else{
                    Log.d("MMCORE", "Calculando inferencia tipo != 0")
                    var mapOfIndicesToOutputs: Map<Int, Array<FloatArray>> = mapOf(0 to arrayOf(floatArrayOf(0f, 0f, 0f, 0f)))
                    interpreter.runForMultipleInputsOutputs(datasList, mapOfIndicesToOutputs)
                    val scores = FloatArray(4)
                    for (i in scores.indices) {
                        scores[i] = (mapOfIndicesToOutputs[0]?.get(0)?.get(i) ?: 0f)
                    }
                    motionDetectorListener?.onOutputScores(scores)
                }
            }
        }
    }

    fun onMoveNetChanged(result: Person, sample: Int) {
        if (!isStarted) return

        result.keyPoints.forEach { keypoint ->

            val moveNetData = MoveNetData(
                pointX = keypoint.coordinate.x,
                pointY = keypoint.coordinate.y,
                position = keypoint.bodyPart.position,
                sample = sample
            )

            recordingData[0]?.let { queue ->
                queue.queueEnqueue(moveNetData.pointX)
                queue.queueEnqueue(moveNetData.pointY)
            }
        }

        //resultAUX = result

        currentJob = coroutineScope.launch {
            processData()
        }

    }


    private fun processData() {
        var scores = FloatArray(0)

        synchronized(lock) {
            inferenceInterface?.takeIf { isStarted }?.let { interpreter ->

                val testArray = Array(1) {
                    //TODO LA DURACIÓN A CAMBIADO PORQUE AHORA SE PILLAN 10 MUESTRAS EN CADA SEGUNDO
                    Array(model.fldNDuration * 10) {
                        Array(model.devices.filter{it.position.id != 0}.size * NUM_CHANNELS) { FloatArray(1) }
                    }
                }

                val sampleArray =
                    Array(GESTURE_SAMPLES) { Array(NUM_DEVICES * NUM_CHANNELS) { FloatArray(1) } }

                for (sample in 0 until GESTURE_SAMPLES) {
                    for (device in 0 until NUM_DEVICES) {
                        for (sensor in 0 until NUM_CHANNELS) {
                            val value = recordingData[0]!!.queue[sample * NUM_CHANNELS + sensor]
                            sampleArray[sample][device * NUM_CHANNELS + sensor] = floatArrayOf(value)
                        }
                    }
                }

                testArray[0] = sampleArray

                interpreter.run(testArray, outputScores)

                scores = FloatArray(model.movements.size)
                for (i in 0 until model.movements.size) {
                    scores[i] = outputScores[0][i] * 100
                }
            }
        }

        currentJob = coroutineScope.launch(Dispatchers.Main) {
            if (scores.isNotEmpty()) {
                motionDetectorListener?.onOutputScores(scores)
            }
        }
    }

    companion object {
        private const val NUM_CHANNELS = 34
        private var RISE_THRESHOLD = 0.80f
        private val NUM_LITE_THREADS = 4
    }

    init {
        model.movements.sortedBy { it.fldSLabel }.forEachIndexed { index, movement ->
            if (movement.fldSLabel != "Other") selectMovIndex = index
        }
    }

}