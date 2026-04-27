package com.trafficlightdetector

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.components.containers.Detection
import com.trafficlightdetector.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), ObjectDetectorHelper.DetectorListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var detector: ObjectDetectorHelper
    private lateinit var soundManager: SoundAlertManager
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var locationManager: LocationManager

    /** CameraX Camera object — available after startCamera() binds successfully. */
    private var camera: Camera? = null

    /**
     * Current GPS speed in m/s.
     * -1f = no GPS fix yet → alerts allowed (fail-safe).
     */
    @Volatile private var currentSpeedMs: Float = NO_FIX_SPEED

    private val isMoving: Boolean
        get() = currentSpeedMs < 0f || currentSpeedMs >= MOVING_THRESHOLD_MS

    // ── Location listener ─────────────────────────────────────────────────────

    private val locationListener = LocationListener { location ->
        currentSpeedMs = if (location.hasSpeed()) location.speed else NO_FIX_SPEED
        runOnUiThread { updateSpeedBadge() }
    }

    // ── Permission launchers ──────────────────────────────────────────────────

    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startLocationUpdates()
        // Location optional — app still works without it (alerts always on)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep screen on; extend layout into status bar area
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        soundManager  = SoundAlertManager()
        detector      = ObjectDetectorHelper(context = this, listener = this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        setupZoomButtons()

        // Camera permission (required)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else cameraPermLauncher.launch(Manifest.permission.CAMERA)

        // Location permission (optional — used only for speed check)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) startLocationUpdates()
        else locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // ── Zoom ──────────────────────────────────────────────────────────────────

    private fun setupZoomButtons() {
        binding.btnZoomIn.setOnClickListener  { adjustZoom(+ZOOM_STEP) }
        binding.btnZoomOut.setOnClickListener { adjustZoom(-ZOOM_STEP) }
    }

    private fun adjustZoom(delta: Float) {
        val cam   = camera ?: return
        val state = cam.cameraInfo.zoomState.value ?: return
        val next  = (state.zoomRatio + delta)
            .coerceIn(state.minZoomRatio, state.maxZoomRatio)
        cam.cameraControl.setZoomRatio(next)
    }

    private fun observeZoomState() {
        camera?.cameraInfo?.zoomState?.observe(this) { state ->
            binding.zoomLabel.text = "${"%.1f".format(state.zoomRatio)}×"
        }
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({
            val cameraProvider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val buffer = Bitmap.createBitmap(
                            imageProxy.width,
                            imageProxy.height,
                            Bitmap.Config.ARGB_8888
                        )
                        imageProxy.use {
                            buffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
                        }
                        detector.detect(buffer, imageProxy.imageInfo.rotationDegrees)
                    }
                }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                observeZoomState()
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ── Location ──────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)     -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> return
            }
            locationManager.requestLocationUpdates(
                provider,
                LOCATION_INTERVAL_MS,
                LOCATION_MIN_DISTANCE_M,
                locationListener
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not start location updates: ${e.message}")
        }
    }

    private fun stopLocationUpdates() {
        try { locationManager.removeUpdates(locationListener) }
        catch (e: Exception) { Log.w(TAG, "removeUpdates failed: ${e.message}") }
    }

    private fun updateSpeedBadge() {
        binding.speedBadge.text = if (currentSpeedMs >= 0f)
            "${"%.0f".format(currentSpeedMs * 3.6f)} km/h"
        else
            "-- km/h"
    }

    // ── ObjectDetectorHelper.DetectorListener ────────────────────────────────

    override fun onResults(
        allDetections:  List<Detection>,
        poleDetections: List<Detection>,
        imageWidth:     Int,
        imageHeight:    Int
    ) {
        runOnUiThread {
            binding.overlay.update(allDetections, poleDetections, imageWidth, imageHeight)

            when {
                !isMoving -> {
                    setStatus(getString(R.string.stopped), DOT_STOPPED)
                }
                poleDetections.isNotEmpty() -> {
                    val label = poleDetections.first()
                        .categories().maxByOrNull { it.score() }
                        ?.categoryName()?.uppercase() ?: "TRAFFIC LIGHT"
                    setStatus("DETECTED: $label", DOT_DETECTED)
                    soundManager.playAlert()
                }
                else -> {
                    setStatus(getString(R.string.scanning), DOT_SCANNING)
                }
            }
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            Log.e(TAG, error)
        }
    }

    /** Update the status text and the coloured indicator dot together. */
    private fun setStatus(text: String, dotColor: Int) {
        binding.statusText.text = text
        binding.statusDot.backgroundTintList = ColorStateList.valueOf(dotColor)
    }

    // ── Lifecycle cleanup ─────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        soundManager.release()
        stopLocationUpdates()
    }

    companion object {
        private const val TAG = "MainActivity"

        // Speed gate
        private const val MOVING_THRESHOLD_MS   = 2f          // ~7 km/h
        private const val NO_FIX_SPEED          = -1f

        // Location updates
        private const val LOCATION_INTERVAL_MS  = 1_000L
        private const val LOCATION_MIN_DISTANCE_M = 0f

        // Zoom
        private const val ZOOM_STEP = 0.5f

        // Status dot colours
        private val DOT_SCANNING = Color.parseColor("#2ECC71")  // green
        private val DOT_DETECTED = Color.parseColor("#E53935")  // red
        private val DOT_STOPPED  = Color.parseColor("#FFB300")  // amber
    }
}
