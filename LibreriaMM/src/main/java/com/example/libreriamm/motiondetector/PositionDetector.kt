package com.example.libreriamm.motiondetector

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.libreriamm.camara.Person
import com.example.libreriamm.entity.Model
import com.google.firebase.ml.modeldownloader.CustomModel
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions
import com.google.firebase.ml.modeldownloader.DownloadType
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import timber.log.Timber
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min

class PositionDetector(val positionDetectorListener: PositionDetectorListener) {

    interface PositionDetectorListener {
        fun onOutputScores(outputScores: FloatArray)
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var currentJob: Job? = null

    private val lock = Any()

    //TODO AHORA SON 10 MUESTRAS CADA SEGUNDO

    private var inferenceInterface: Interpreter? = null
    private var options: Interpreter.Options? = null

    private var isStarted = false
    private var estado = 1
    private val ETIQUETAS = 5
    private var contador_reintentos = 0
    @RequiresApi(Build.VERSION_CODES.O)

    fun start() {
        val conditions = CustomModelDownloadConditions.Builder().build()
        FirebaseModelDownloader.getInstance()
            .getModel("clasificador", DownloadType.LATEST_MODEL, conditions)
            .addOnSuccessListener { customModel: CustomModel ->
                val modelFile = customModel.file
                Log.d("MMCORE_POSICION", "Temporary file ${modelFile != null} - ${modelFile?.absolutePath}")
                modelFile?.let { file ->
                    Log.d("MMCORE_POSICION", "Inicializando ${file.name}")
                    currentJob = coroutineScope.launch {
                        synchronized(lock) {
                            Log.d("MMCORE_POSICION", "Lock adquired")
                            val compatList = CompatibilityList()
                            options = Interpreter.Options()
                            Log.d("MMCORE_POSICION", "Options create ${options != null}")
                            if (compatList.isDelegateSupportedOnThisDevice) {
                                Log.d("MMCORE_POSICION", "GPU Accelerate available")
                                val delegateOptions = compatList.bestOptionsForThisDevice
                                options!!.addDelegate(GpuDelegate(delegateOptions))
                                Log.d("MMCORE_POSICION", "GPU Accelerate available ready")
                            } else {
                                Log.d("MMCORE_POSICION", "GPU Accelerate not available")
                                // Fallback to CPU execution if GPU acceleration is not available
                                options!!.setNumThreads(NUM_LITE_THREADS)
                                Log.d("MMCORE_POSICION", "GPU Accelerate not available ready")
                            }
                            Log.d("MMCORE_POSICION", "Creating interpreter...")
                        }
                        inferenceInterface = Interpreter(file, options)
                        isStarted = true
                        estado = 1
                        //Log.d("MMCORE", "Started resolve: ${isStarted}")
                        Log.d("MMCORE_POSICION", "Activada inferencia: ${isStarted}")
                    }
                }
            }.addOnFailureListener { t: Exception? ->
                Log.d("MMCORE_POSICION", "MODEL DOWNLOAD: ${t}")
                Timber.e(t)
                contador_reintentos += 1
                if(contador_reintentos <= 4){
                    start()
                }
            }
    }

    fun stop() {
        contador_reintentos = 0
        if(currentJob!= null) {
            currentJob!!.cancel()
        }
        synchronized(lock) {
            isStarted = false
            Log.d("MMCORE_POSICION", "Desactivada inferencia: $isStarted")
        }
        inferenceInterface?.let {
            it.close()
            inferenceInterface = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun inference(datasList: Array<Array<Array<Array<FloatArray>>>>) {
        synchronized(lock) {
            Log.d("MMCORE_POSICION", "inferencia activa... $isStarted")
            inferenceInterface?.takeIf { isStarted }?.let { interpreter ->
                val mapOfIndicesToOutputs: Map<Int, Array<FloatArray>> =
                    mapOf(0 to arrayOf(FloatArray(ETIQUETAS) { 0f }))
                interpreter.runForMultipleInputsOutputs(datasList, mapOfIndicesToOutputs)
                val scores = FloatArray(ETIQUETAS)
                for (i in 0 until ETIQUETAS) {
                    scores[i] =
                        (mapOfIndicesToOutputs[0]?.get(0)?.get(i) ?: 0f)
                }
                positionDetectorListener?.onOutputScores(scores)
            }
        }
    }


    companion object {
        private val NUM_LITE_THREADS = 1
    }

    init {
        Log.d("MMCORE_POSICION", "Inferencia activa restarted")
    }

}