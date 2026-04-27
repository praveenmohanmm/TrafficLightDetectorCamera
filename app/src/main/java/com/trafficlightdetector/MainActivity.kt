package com.trafficlightdetector

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
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

    /**
     * Current GPS speed in m/s.
     * -1f = no GPS fix yet → we allow alerts (fail-safe).
     */
    @Volatile private var currentSpeedMs: Float = NO_FIX_SPEED

    /** True when the vehicle is moving fast enough to trigger alerts. */
    private val isMoving: Boolean
        get() = currentSpeedMs < 0f || currentSpeedMs >= MOVING_THRESHOLD_MS

    // ── Location listener ─────────────────────────────────────────────────────

    private val locationListener = LocationListener { location ->
        currentSpeedMs = if (location.hasSpeed()) location.speed else NO_FIX_SPEED
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
        // Location is optional — app still works without it (alerts always on)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep screen on while the app is in the foreground
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        soundManager  = SoundAlertManager()
        detector      = ObjectDetectorHelper(context = this, listener = this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        // Camera permission (required)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }

        // Location permission (optional — used only for speed check)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        } else {
            locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ── Location ──────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            // Prefer GPS; fall back to network for initial fix
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)     -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> return   // no provider available — alerts remain always-on
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
                    // Vehicle is stationary — suppress audio, show paused state
                    binding.statusText.text = getString(R.string.stopped)
                    binding.statusText.setBackgroundResource(R.color.status_bg)
                }
                poleDetections.isNotEmpty() -> {
                    val label = poleDetections.first()
                        .categories().maxByOrNull { it.score() }
                        ?.categoryName() ?: "Traffic Light"
                    binding.statusText.text = "DETECTED: ${label.uppercase()}"
                    binding.statusText.setBackgroundResource(R.color.alert_red)
                    soundManager.playAlert()
                }
                else -> {
                    binding.statusText.text = getString(R.string.scanning)
                    binding.statusText.setBackgroundResource(R.color.status_bg)
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        soundManager.release()
        stopLocationUpdates()
    }

    companion object {
        private const val TAG = "MainActivity"

        /** Speed below which alerts are suppressed (m/s). 2 m/s ≈ 7 km/h */
        private const val MOVING_THRESHOLD_MS = 2f

        /** Sentinel meaning GPS has not yet provided a speed reading. */
        private const val NO_FIX_SPEED = -1f

        /** Request a location update every second. */
        private const val LOCATION_INTERVAL_MS = 1_000L

        /** Minimum displacement between updates (0 = every interval). */
        private const val LOCATION_MIN_DISTANCE_M = 0f
    }
}
