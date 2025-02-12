/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.libreriamm.camara

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerHelper(
    var minPoseDetectionConfidence: Float = DEFAULT_POSE_DETECTION_CONFIDENCE,
    var minPoseTrackingConfidence: Float = DEFAULT_POSE_TRACKING_CONFIDENCE,
    var minPosePresenceConfidence: Float = DEFAULT_POSE_PRESENCE_CONFIDENCE,
    var currentModel: Int = MODEL_POSE_LANDMARKER_LITE,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.LIVE_STREAM,
    val context: Context,
    val poseLandmarkerHelperListener: LandmarkerListener? = null
) {

    // For this example this needs to be a var so it can be reset on changes.
    // If the Pose Landmarker will not change, a lazy val would be preferable.
    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    // Return running status of PoseLandmarkerHelper
    fun isClose(): Boolean {
        return poseLandmarker == null
    }

    // Initialize the Pose landmarker using current settings on the
    // thread that is using it. CPU can be used with Landmarker
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the
    // Landmarker
    fun setupPoseLandmarker() {
        // Set general pose landmarker options
        val baseOptionBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                baseOptionBuilder.setDelegate(Delegate.GPU)
            }
        }

        val modelName =
            when (currentModel) {
                MODEL_POSE_LANDMARKER_FULL -> "pose_landmarker_full.task"
                MODEL_POSE_LANDMARKER_LITE -> "pose_landmarker_lite.task"
                MODEL_POSE_LANDMARKER_HEAVY -> "pose_landmarker_heavy.task"
                else -> "pose_landmarker_full.task"
            }

        baseOptionBuilder.setModelAssetPath(modelName)

        try {
            val baseOptions = baseOptionBuilder.build()
            // Create an option builder with base options and specific
            // options only use for Pose Landmarker.
            var optionsBuilder =
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                    .setMinTrackingConfidence(minPoseTrackingConfidence)
                    .setMinPosePresenceConfidence(minPosePresenceConfidence)
                    .setRunningMode(runningMode)
                    .setNumPoses(DEFAULT_NUM_POSES)
            if(runningMode == RunningMode.LIVE_STREAM){
                optionsBuilder = optionsBuilder.setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }


            val options = optionsBuilder.build()
            poseLandmarker =
                PoseLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            Log.e(
                TAG, "MediaPipe failed to load the task with error: " + e
                    .message
            )
        } catch (e: RuntimeException) {
            Log.e(
                TAG,
                "Image classifier failed to load model with error: " + e.message
            )
        }
    }

    private fun returnLivestreamResult(
        result: PoseLandmarkerResult,
        input: MPImage
    ) {
        val keypoints = mutableListOf<KeyPoint>()
        val res = mutableListOf<Person>()
        var score = 0f
        Log.d("PERSON", "$result")
        result.let { it1 ->
            if(it1.landmarks().isNotEmpty()) {
                it1.landmarks().forEach { lm ->
                    lm.forEachIndexed { index, landmarkt ->
                        val point = landmarkt
                        val part = BodyPart.fromMediaPipe(index)
                        if (part != null) {
                            keypoints.add(
                                KeyPoint(
                                    part,
                                    PointF(point.x() * 480, point.y() * 640),
                                    point.visibility().orElse(0f)
                                )
                            )
                            score += point.visibility().orElse(0f)
                        }
                    }
                    if(keypoints.isNotEmpty()) {
                        score /= keypoints.size
                    }
                    res.add(Person(keypoints.sortedBy{ it.bodyPart.position }, score))
                }
            }
        }
        poseLandmarkerHelperListener?.onResultsPersons(res)
    }

    // Return errors thrown during detection to this PoseLandmarkerHelper's
    // caller
    private fun returnLivestreamError(error: RuntimeException) {
        poseLandmarkerHelperListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    // Accepted a Bitmap and runs pose landmarker inference on it to return
    // results back to the caller
    fun detectImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call detectImage" +
                        " while not using RunningMode.IMAGE"
            )
        }


        // Inference time is the difference between the system time at the
        // start and finish of the process
        val startTime = SystemClock.uptimeMillis()

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(image).build()

        // Run pose landmarker using MediaPipe Pose Landmarker API
        poseLandmarker?.detect(mpImage)?.also { landmarkResult ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            return ResultBundle(
                listOf(landmarkResult),
                inferenceTimeMs,
                image.height,
                image.width
            )
        }
        return null
    }

    fun detectLiveStreamBitmap(
        bitmap: Bitmap
    ){
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLiveStream" +
                        " while not using RunningMode.LIVE_STREAM"
            )
        }
        val frameTime = SystemClock.uptimeMillis()

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(bitmap).build()

        detectAsync(mpImage, frameTime)
    }

    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean,
        bitmapBuffer: Bitmap
    ) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLiveStream" +
                        " while not using RunningMode.LIVE_STREAM"
            )
        }
        val frameTime = SystemClock.uptimeMillis()

        // Copy out RGB bits from the frame to a bitmap buffer

        val matrix = Matrix().apply {
            // Rotate the frame received from the camera to be in the same direction as it'll be shown
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            // flip image if user use front camera
            if (isFrontCamera) {
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        detectAsync(mpImage, frameTime)
    }

    // Run pose landmark using MediaPipe Pose Landmarker API
    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        poseLandmarker?.detectAsync(mpImage, frameTime)
        // As we're using running mode LIVE_STREAM, the landmark result will
        // be returned in returnLivestreamResult function
    }

    fun estimateSinglePose(image: Bitmap): Person{
        val startTime = SystemClock.uptimeMillis()
        val bundle = detectImage(image)
        val keypoints = mutableListOf<KeyPoint>()
        var score = 0f
        bundle?.results?.forEach { it1 ->
            if(it1.landmarks().isNotEmpty()) {
                it1.landmarks()[0].forEachIndexed { index, landmarkt ->
                    val point = landmarkt
                    val part = BodyPart.fromMediaPipe(index)
                    if (part != null) {
                        keypoints.add(
                            KeyPoint(
                                part,
                                PointF(point.x() * 480, point.y() * 640),
                                point.visibility().orElse(0f)
                            )
                        )
                        score += point.visibility().orElse(0f)
                    }
                }
            }
        }
        if(keypoints.isNotEmpty()) {
            score /= keypoints.size
        }
        Log.d("MMCORE_REND", "iempo de lectura: ${SystemClock.uptimeMillis() - startTime}")
        return Person(keypoints.sortedBy{ it.bodyPart.position }, score)
    }

    companion object {
        const val TAG = "PoseLandmarkerHelper"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_POSE_DETECTION_CONFIDENCE = 0.3F
        const val DEFAULT_POSE_TRACKING_CONFIDENCE = 0.3F
        const val DEFAULT_POSE_PRESENCE_CONFIDENCE = 0.3F
        const val DEFAULT_NUM_POSES = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        const val MODEL_POSE_LANDMARKER_FULL = 0
        const val MODEL_POSE_LANDMARKER_LITE = 1
        const val MODEL_POSE_LANDMARKER_HEAVY = 2
    }

    data class ResultBundle(
        val results: List<PoseLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResultsPersons(resultList: List<Person>)
    }
}