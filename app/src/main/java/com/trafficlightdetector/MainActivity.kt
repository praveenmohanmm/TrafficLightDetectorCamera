package com.poodlesoft.trafficlightdetector

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
import com.poodlesoft.trafficlightdetector.databinding.ActivityMainBinding
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
     * Last confirmed speed in m/s from GPS.
     * Only written when Location.hasSpeed() == true.
     */
    @Volatile private var currentSpeedMs: Float = 0f

    /**
     * True once GPS has delivered at least one update that includes a speed value.
     * Until then we have no ground truth, so we assume the vehicle is moving (alerts on).
     * This handles: cold GPS start, permission denied, network-only provider.
     */
    @Volatile private var hasSpeedReading: Boolean = false

    /**
     * True when alerts should fire.
     * - No speed reading yet → assume moving (fail-open: don't suppress alerts with no data).
     * - Speed reading received → only moving if speed ≥ threshold.
     */
    private val isMoving: Boolean
        get() = !hasSpeedReading || currentSpeedMs >= MOVING_THRESHOLD_MS

    // ── Location listener ─────────────────────────────────────────────────────

    private val locationListener = LocationListener { location ->
        if (location.hasSpeed()) {
            // GPS gave us a real speed value — use it
            currentSpeedMs  = location.speed
            hasSpeedReading = true
        }
        // If hasSpeed() == false (network provider, no fix) don't touch currentSpeedMs.
        // isMoving will stay true via the !hasSpeedReading path so alerts keep working.
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
        if (granted) {
            startLocationUpdates()
        } else {
            // Location is optional — alerts stay on without it (fail-open).
            // Only show rationale if Android says we can ask again.
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(
                    this,
                    "Location access lets the app pause alerts when you're stationary. " +
                    "You can grant it later in App Settings.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
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
                // Apply default zoom after binding
                camera?.cameraControl?.setZoomRatio(DEFAULT_ZOOM)
                observeZoomState()
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ── Location ──────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // Register with BOTH providers simultaneously.
        // GPS gives accurate speed once locked; network gives faster first fix.
        // Both call the same locationListener — whichever fires first wins each update.
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(
                        provider,
                        LOCATION_INTERVAL_MS,
                        LOCATION_MIN_DISTANCE_M,
                        locationListener
                    )
                    Log.d(TAG, "Location updates started on $provider")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not start $provider updates: ${e.message}")
            }
        }
    }

    private fun stopLocationUpdates() {
        try { locationManager.removeUpdates(locationListener) }
        catch (e: Exception) { Log.w(TAG, "removeUpdates failed: ${e.message}") }
    }

    private fun updateSpeedBadge() {
        binding.speedBadge.text = if (hasSpeedReading)
            "${"%.0f".format(currentSpeedMs * 3.6f)} km/h"
        else
            "-- km/h"  // waiting for GPS fix
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

        // Speed gate — alerts only fire above this threshold
        private const val MOVING_THRESHOLD_MS = 2f             // ~7 km/h

        // Location updates
        private const val LOCATION_INTERVAL_MS  = 1_000L
        private const val LOCATION_MIN_DISTANCE_M = 0f

        // Zoom
        private const val DEFAULT_ZOOM = 1.5f
        private const val ZOOM_STEP    = 0.5f

        // Status dot colours
        private val DOT_SCANNING = Color.parseColor("#2ECC71")  // green
        private val DOT_DETECTED = Color.parseColor("#E53935")  // red
        private val DOT_STOPPED  = Color.parseColor("#FFB300")  // amber
    }
}
