package com.example.vrcapture.fragments

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.animation.doOnCancel
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import com.example.vrcapture.utils.fitSystemWindows
import com.example.vrcapture.databinding.FragmentVideoBinding
import com.example.vrcapture.utils.*
import com.example.vrcapture.R
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.io.File
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// TODO: Base name off the song name
class Recording {
    val filename = System.currentTimeMillis().toString()
}

@SuppressLint("RestrictedApi")
class VideoFragment : BaseFragment<FragmentVideoBinding>(R.layout.fragment_video) {
    companion object {
        private const val TAG = "VRCapture"

        private const val RATIO_4_3_VALUE = 4.0 / 3.0 // aspect ratio 4x3
        private const val RATIO_16_9_VALUE = 16.0 / 9.0 // aspect ratio 16x9
    }

    // An instance for display manager to get display change callbacks
    private val displayManager by lazy { requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture? = null
    private var imageCapture: ImageCapture? = null

    private var server: NettyApplicationEngine? = null

    private var displayId = -1

    // Selector showing which camera is selected (front or back)
    private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA

    private var recording: Recording? = null
    private var photoDelay: Runnable? = null
    private val animateRecord by lazy {
        ObjectAnimator.ofFloat(binding.btnRecordVideo, View.ALPHA, 1f, 0.5f).apply {
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            doOnCancel { binding.btnRecordVideo.alpha = 1f }
        }
    }

    private var tts: TextToSpeech? = null

    // A lazy instance of the current fragment's view binding
    override val binding: FragmentVideoBinding by lazy { FragmentVideoBinding.inflate(layoutInflater) }

    /**
     * A display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        @SuppressLint("UnsafeExperimentalUsageError")
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@VideoFragment.displayId) {
                preview?.targetRotation = view.display.rotation
                videoCapture?.setTargetRotation(view.display.rotation)
                imageCapture?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()

        displayManager.registerDisplayListener(displayListener, null)

        binding.run {
            viewFinder.addOnAttachStateChangeListener(object :
                View.OnAttachStateChangeListener {
                override fun onViewDetachedFromWindow(v: View) =
                    displayManager.registerDisplayListener(displayListener, null)

                override fun onViewAttachedToWindow(v: View) =
                    displayManager.unregisterDisplayListener(displayListener)
            })
            binding.btnRecordVideo.setOnClickListener {
                if (recording == null) {
                    startRecording()
                } else {
                    stopRecording()
                }
            }
            btnSwitchCamera.setOnClickListener { toggleCamera() }

            tts = TextToSpeech(requireContext()) { status ->
                if (status != TextToSpeech.ERROR) {
                    tts?.language = Locale.UK
                }
            }
        }
    }

    /**
     * Create some initial states
     * */
    private fun initViews() {
        adjustInsets()
    }

    /**
     * This methods adds all necessary margins to some views based on window insets and screen orientation
     * */
    private fun adjustInsets() {
        activity?.window?.fitSystemWindows()
        binding.btnRecordVideo.onWindowInsets { view, windowInsets ->
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                view.bottomMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            } else {
                view.endMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).right
            }
        }
    }

    /**
     * Change the facing of camera
     *  toggleButton() function is an Extension function made to animate button rotation
     * */
    private fun toggleCamera() = binding.btnSwitchCamera.toggleButton(
        flag = lensFacing == CameraSelector.DEFAULT_BACK_CAMERA,
        rotationAngle = 180f,
        firstIcon = R.drawable.ic_outline_camera_rear,
        secondIcon = R.drawable.ic_outline_camera_front,
    ) {
        lensFacing = if (it) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }

        startCamera()
    }

    /**
     * Unbinds all the lifecycles from CameraX, then creates new with new parameters
     * */
    private fun startCamera() {
        // This is the Texture View where the camera will be rendered
        val viewFinder = binding.viewFinder

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
            val aspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
            val rotation = viewFinder.display.rotation

            val localCameraProvider = cameraProvider
                ?: throw IllegalStateException("camera initialization failed")

            preview = Preview.Builder()
                .setTargetAspectRatio(aspectRatio)
                .setTargetRotation(rotation)
                .build()

            val videoCaptureConfig = VideoCapture.Builder().apply {
                setVideoFrameRate(60)
                setBitRate(16 * 1024 * 1024)
                setTargetAspectRatio(AspectRatio.RATIO_16_9);
            }
            videoCapture = VideoCapture.Builder
                .fromConfig(videoCaptureConfig.useCaseConfig)
                .setTargetRotation(rotation)
                .build()

            imageCapture = ImageCapture.Builder()
                .setTargetRotation(rotation)
                .build()

            localCameraProvider.unbindAll() // unbind the use-cases before rebinding them

            try {
                camera = localCameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    lensFacing,
                    preview,
                    videoCapture,
                    imageCapture
                )

                preview?.setSurfaceProvider(viewFinder.surfaceProvider)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind use cases", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /**
     *  Detecting the most suitable aspect ratio for current dimensions
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun getVideoCapture(): VideoCapture {
        return videoCapture ?: throw IllegalStateException("camera initialization failed")
    }

    private fun getImageCapture(): ImageCapture {
        return imageCapture ?: throw IllegalStateException("camera initialization failed")
    }

    private fun getImageOutputOptions(recording: Recording): ImageCapture.OutputFileOptions {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, recording.filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, outputDirectory)
            }

            requireContext().contentResolver.run {
                val contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

                ImageCapture.OutputFileOptions.Builder(this, contentUri, contentValues)
            }
        } else {
            File(outputDirectory).mkdirs()
            val file = File("$outputDirectory/${recording.filename}.jpg")

            ImageCapture.OutputFileOptions.Builder(file)
        }.build()
    }

    private fun takePhoto(recording: Recording) {
        getImageCapture().takePicture(
                getImageOutputOptions(recording),
                requireContext().mainExecutor(),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        outputFileResults.savedUri
                                ?.let { uri ->
                                    Log.d(TAG, "Image saved in $uri")
                                }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        val msg = "Video capture failed: ${exception.message}"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        Log.e(TAG, msg)
                        exception.printStackTrace()
                    }
                }
        )
    }

    private fun getVideoOutputOptions(recording: Recording): VideoCapture.OutputFileOptions {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, recording.filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, outputDirectory)
            }

            requireContext().contentResolver.run {
                val contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

                VideoCapture.OutputFileOptions.Builder(this, contentUri, contentValues)
            }
        } else {
            File(outputDirectory).mkdirs()
            val file = File("$outputDirectory/${recording.filename}.mp4")

            VideoCapture.OutputFileOptions.Builder(file)
        }.build()

    }

    private fun startRecording() {
        if (recording != null) return
        val rec = Recording()
        recording = rec

        if (photoDelay != null) {
            Handler().removeCallbacks(photoDelay!!);
            photoDelay = null
        }

        animateRecord.start()
        getVideoCapture().startRecording(
            getVideoOutputOptions(rec),
            requireContext().mainExecutor(),
            object : VideoCapture.OnVideoSavedCallback {
                override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                    outputFileResults.savedUri
                        ?.let { uri ->
                            Log.d(TAG, "Video saved in $uri")
                        }
                }

                override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                    animateRecord.cancel()
                    val msg = "Video capture failed: $message"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    Log.e(TAG, msg)
                    cause?.printStackTrace()
                }
            })
    }

    private fun stopRecording() {
        if (recording == null) return
        val rec = recording!!
        recording = null

        animateRecord.cancel()
        getVideoCapture().stopRecording()

        tts?.speak("Taking photo in 10 seconds", TextToSpeech.QUEUE_FLUSH, null, "photo_after_delay")
        photoDelay = Runnable {
            photoDelay = null
            takePhoto(rec)
            tts?.speak("Photo taken", TextToSpeech.QUEUE_FLUSH, null, "photo_taken")
        }
        Handler().postDelayed(photoDelay, 10 * 1000)
    }

    override fun onPermissionGranted() {
        binding.viewFinder.let { vf ->
            vf.post {
                displayId = vf.display.displayId
                startCamera()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (server == null) {
            Log.d(TAG, "Server started")
            server = embeddedServer(Netty, 8080) {
                install(ContentNegotiation) {
                    gson {}
                }
                routing {
                    get("/") {
                        call.respond(mapOf("message" to "Hello world"))
                    }
                    get("/start") {
                        Log.d(TAG, "/start")
                        try {
                            requireActivity().runOnUiThread {
                                startRecording()
                            }
                        }catch (err: java.lang.Exception){
                            Log.e(TAG, "server error", err);
                            throw  err
                        }
                        call.respond(mapOf("success" to true))
                    }
                    get("/stop") {
                        Log.d(TAG, "/stop")
                        try {
                            requireActivity().runOnUiThread {
                                stopRecording()
                            }
                        }catch (err: java.lang.Exception){
                            Log.e(TAG, "server error", err);
                            throw  err
                        }
                        call.respond(mapOf("success" to true))
                    }
                }
            }
            server?.start(wait = false)
        }
    }

    override fun onStop() {
        super.onStop()

        Log.d(TAG, "Server stopped")
        server?.stop(1000, 5000)
        server = null
    }

    override fun onBackPressed() = requireActivity().finish()
}
