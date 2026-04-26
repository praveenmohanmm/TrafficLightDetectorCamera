# Traffic Light / Pole Detector — Android Camera App

Real-time camera app that detects traffic poles and alerts with an audible tone.

## How it works

| Layer | Technology |
|---|---|
| Camera | CameraX (RGBA_8888 ImageAnalysis) |
| Inference | TFLite Task Vision — EfficientDet-Lite0 (COCO int8) |
| Alert | Android `ToneGenerator` (STREAM_ALARM) |
| Overlay | Custom `OverlayView` with labelled bounding boxes |

The model is trained on COCO 2017 which includes **traffic light**, **stop sign**, and
**parking meter** — all objects mounted on roadside poles. When any of these are
detected above the confidence threshold (45 %) an alert tone plays and the
bounding box is highlighted in red.

> **Better accuracy**: Replace `app/src/main/assets/efficientdet_lite0.tflite`
> with a custom TFLite model trained specifically on traffic poles
> (e.g. from [Roboflow Universe — Traffic Pole dataset](https://universe.roboflow.com)).
> The app uses the TFLite Task Vision metadata API so labels are bundled in the
> model file — no extra label file needed.

## Quick start

### 1. Download the TFLite model

**Linux / macOS / Git Bash:**
```bash
bash scripts/download_model.sh
```

**Windows PowerShell:**
```powershell
.\scripts\download_model.ps1
```

This downloads `efficientdet_lite0.tflite` (~4.5 MB) into `app/src/main/assets/`.

### 2. Open in Android Studio

Open the `TrafficLightDetectorCamera` folder in **Android Studio Hedgehog (2023.1)** or later.
Android Studio will sync Gradle automatically.

### 3. Run on device

Connect an Android device (API 24+) with USB debugging enabled and press **Run**.

> The app requires a physical device with a rear camera.
> Camera permission is requested at first launch.

## Project structure

```
app/src/main/
├── java/com/trafficlightdetector/
│   ├── MainActivity.kt          — CameraX setup, lifecycle, UI updates
│   ├── ObjectDetectorHelper.kt  — TFLite Task Vision wrapper + traffic-class filter
│   ├── OverlayView.kt           — Canvas overlay for bounding boxes
│   └── SoundAlertManager.kt     — ToneGenerator with 2-second cooldown
├── res/layout/activity_main.xml — Camera preview + overlay + status banner
└── assets/
    └── efficientdet_lite0.tflite  (download via script — not committed)
```

## Detected classes → pole inference

| COCO label | Why it indicates a pole |
|---|---|
| `traffic light` | Signal head mounted on a vertical pole |
| `stop sign` | Sign face on a roadside post |
| `parking meter` | Metering unit on a short post |

## License

Apache 2.0
