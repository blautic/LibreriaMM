/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.objectdetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.example.libreriamm.camara.BodyPart
import com.example.libreriamm.camara.KeyPoint
import com.example.libreriamm.camara.ObjetLabel
import com.example.libreriamm.camara.Objeto
import com.example.libreriamm.camara.PointF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult

class ObjectDetectorHelper(
    var threshold: Float = THRESHOLD_DEFAULT,
    var maxResults: Int = MAX_RESULTS_DEFAULT,
    var currentDelegate: Int = DELEGATE_CPU,
    var currentModel: Int = MODEL_EFFICIENTDETV0,
    var runningMode: RunningMode = RunningMode.LIVE_STREAM,
    val context: Context,
    // The listener is only used when running in RunningMode.LIVE_STREAM
    var objectDetectorListener: DetectorListener? = null
) {

    // For this example this needs to be a var so it can be reset on changes. If the ObjectDetector
    // will not change, a lazy val would be preferable.
    private var objectDetector: ObjectDetector? = null
    private var imageRotation = 0
    private var objectLabels: MutableList<ObjetLabel> = mutableListOf()
    private lateinit var imageProcessingOptions: ImageProcessingOptions

    init {
        setupObjectDetector()
    }

    fun clearObjectDetector() {
        objectDetector?.close()
        objectDetector = null
    }
    fun setObjetsLabels(objects: List<ObjetLabel>){
        clearObjectDetector()
        objectLabels = objects.toMutableList()
        setupObjectDetector()
    }
    fun addObjetLabel(label: ObjetLabel){
        clearObjectDetector()
        objectLabels.add(label)
        objectLabels = objectLabels.distinct().toMutableList()
        setupObjectDetector()
    }
    fun estimateObjects(image: Bitmap): List<Objeto>{
        val bundle = detectImage(image)
        val res = mutableListOf<Objeto>()
        bundle?.results?.forEach { it1 ->
            it1.detections()?.map{
                val boxRect = RectF(
                    it.boundingBox().left * 480,
                    it.boundingBox().top * 640,
                    it.boundingBox().right * 480,
                    it.boundingBox().bottom * 640
                )
                Pair(boxRect, it.categories()[0])
            }?.forEach {
                res.add(Objeto(ObjetLabel.fromLabel(it.second.categoryName()), it.first, it.second.score()))
            }
        }
        return res
    }

    // Initialize the object detector using current settings on the
    // thread that is using it. CPU can be used with detectors
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the detector
    fun setupObjectDetector() {
        // Set general detection options, including number of used threads
        val baseOptionsBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
            }

            DELEGATE_GPU -> {
                // Is there a check for GPU being supported?
                baseOptionsBuilder.setDelegate(Delegate.GPU)
            }
        }

        val modelName = when (currentModel) {
            MODEL_EFFICIENTDETV0 -> "efficientdet-lite0.tflite"
            MODEL_EFFICIENTDETV2 -> "efficientdet-lite2.tflite"
            else -> "efficientdet-lite0.tflite"
        }

        baseOptionsBuilder.setModelAssetPath(modelName)

        try {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setResultListener(this::returnLivestreamResult)
                .setErrorListener(this::returnLivestreamError)
                .setScoreThreshold(threshold).setRunningMode(runningMode).setCategoryAllowlist(objectLabels.map { it.label })
                .setMaxResults(maxResults).setRunningMode(runningMode).build()

            imageProcessingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(imageRotation).build()

            objectDetector = ObjectDetector.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            objectDetectorListener?.onErrorObject(
                "Object detector failed to initialize. See error logs for details"
            )
            Log.e(TAG, "TFLite failed to load model with error: " + e.message)
        } catch (e: RuntimeException) {
            objectDetectorListener?.onErrorObject(
                "Object detector failed to initialize. See error logs for " + "details",
                GPU_ERROR
            )
            Log.e(
                TAG,
                "Object detector failed to load model with error: " + e.message
            )
        }
    }

    private fun returnLivestreamResult(
        result: ObjectDetectorResult, input: MPImage
    ) {
        val res = mutableListOf<Objeto>()
        result.let { it1 ->
            it1.detections()?.map{
                val boxRect = RectF(
                    480 - it.boundingBox().left,
                    it.boundingBox().top,
                    480 - it.boundingBox().right,
                    it.boundingBox().bottom
                )
                Pair(boxRect, it.categories()[0])
            }?.forEach {
                res.add(Objeto(ObjetLabel.fromLabel(it.second.categoryName()), it.first, it.second.score()))
            }
        }
        objectDetectorListener?.onResults(res)
    }

    // Return errors thrown during detection to this ObjectDetectorHelper's caller
    private fun returnLivestreamError(error: RuntimeException) {
        objectDetectorListener?.onErrorObject(
            error.message ?: "An unknown error has occurred"
        )
    }

    // Return running status of recognizer helper
    fun isClosed(): Boolean {
        return objectDetector == null
    }

    // Accepted a Bitmap and runs object detection inference on it to return results back
    // to the caller
    fun detectImage(image: Bitmap): ResultBundle? {

        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call detectImage" + " while not using RunningMode.IMAGE"
            )
        }

        if (objectDetector == null) return null

        // Inference time is the difference between the system time at the start and finish of the
        // process
        val startTime = SystemClock.uptimeMillis()

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(image).build()

        // Run object detection using MediaPipe Object Detector API
        objectDetector?.detect(mpImage)?.also { detectionResult ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            return ResultBundle(
                listOf(detectionResult),
                inferenceTimeMs,
                image.height,
                image.width
            )
        }

        // If objectDetector?.detect() returns null, this is likely an error. Returning null
        // to indicate this.
        return null
    }

    fun detectLiveStreamImage(bitmap: Bitmap){
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLivestreamFrame" + " while not using RunningMode.LIVE_STREAM"
            )
        }

        val frameTime = SystemClock.uptimeMillis()

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(bitmap).build()

        detectAsync(mpImage, frameTime)
    }

    fun detectLivestreamFrame(imageProxy: ImageProxy,
                              bitmapBuffer: Bitmap) {

        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLivestreamFrame" + " while not using RunningMode.LIVE_STREAM"
            )
        }

        val frameTime = SystemClock.uptimeMillis()

        // If the input image rotation is change, stop all detector
        if (imageProxy.imageInfo.rotationDegrees != imageRotation) {
            imageRotation = imageProxy.imageInfo.rotationDegrees
            clearObjectDetector()
            setupObjectDetector()
            return
        }

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(bitmapBuffer).build()

        detectAsync(mpImage, frameTime)
    }

    // Run object detection using MediaPipe Object Detector API
    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        // As we're using running mode LIVE_STREAM, the detection result will be returned in
        // returnLivestreamResult function
        objectDetector?.detectAsync(mpImage, imageProcessingOptions, frameTime)
    }

    // Wraps results from inference, the time it takes for inference to be performed, and
    // the input image and height for properly scaling UI to return back to callers
    data class ResultBundle(
        val results: List<ObjectDetectorResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val inputImageRotation: Int = 0
    )

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_EFFICIENTDETV0 = 0
        const val MODEL_EFFICIENTDETV2 = 1
        const val MAX_RESULTS_DEFAULT = 3
        const val THRESHOLD_DEFAULT = 0.5F
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1

        const val TAG = "ObjectDetectorHelper"
    }

    // Used to pass results or errors back to the calling class
    interface DetectorListener {
        fun onErrorObject(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(result: List<Objeto>)
    }
}
