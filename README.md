# Traffic Light / Pole Detector — Android Camera App

Real-time Android camera app that detects traffic poles and alerts with an audible tone.

## Download & Install (latest release)

> Once you push this repo to GitHub, every commit to `main`/`master` automatically
> builds the APK and publishes it as a **GitHub Release**.

1. Go to the **Releases** tab of your GitHub repo  
2. Download the latest `.apk` file  
3. On your Android device: **Settings → Install unknown apps** → allow your browser/file manager  
4. Open the APK and tap **Install**  
5. Grant camera permission on first launch

---

## Set up GitHub Releases (CI/CD)

### Step 1 — Push to GitHub

```bash
git remote add origin https://github.com/<your-username>/TrafficLightDetectorCamera.git
git push -u origin master
```

The workflow at [`.github/workflows/build-and-release.yml`](.github/workflows/build-and-release.yml)
triggers automatically on every push. No secrets are required — it builds a **debug APK** and
publishes it as a public release.

---

### Step 2 (optional) — Release-signed APK

A release-signed APK is required for Play Store submission and is generally preferred for
distribution. To enable it, add four **Repository Secrets** in  
`GitHub repo → Settings → Secrets and variables → Actions`:

| Secret name       | Value |
|-------------------|-------|
| `KEYSTORE_BASE64` | Base64-encoded `.jks` file (see below) |
| `KEY_ALIAS`       | Alias of the key in the keystore |
| `KEY_PASSWORD`    | Password for the key |
| `STORE_PASSWORD`  | Password for the keystore |

**Generate a keystore** (run once, keep the `.jks` file safe):

```bash
keytool -genkey -v \
  -keystore traffic-detector.jks \
  -alias traffic-detector \
  -keyalg RSA -keysize 2048 -validity 10000
```

**Encode it for the secret:**

```bash
# Linux / macOS / Git Bash
base64 -w 0 traffic-detector.jks | pbcopy   # macOS — copies to clipboard
base64 -w 0 traffic-detector.jks            # Linux — copy the output

# PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("traffic-detector.jks"))
```

Paste the output as the value of `KEYSTORE_BASE64`.

Once all four secrets are set, the next push automatically builds a **signed release APK**.

---

### Manual release with a version tag

```bash
# Trigger from GitHub UI: Actions → Build & Release APK → Run workflow
# Or push a tag:
git tag v1.0.0
git push origin v1.0.0
```

Enter the tag name in the `tag` input field when running manually, or leave blank to use
the auto-incremented build number (`build-42`).

---

## How it works

| Layer | Technology |
|---|---|
| Camera | CameraX (RGBA_8888 ImageAnalysis) |
| Inference | TFLite Task Vision — EfficientDet-Lite0 (COCO int8, ~4.5 MB) |
| Alert | Android `ToneGenerator` (STREAM_ALARM, 600 ms, 2-sec cooldown) |
| Overlay | Custom `OverlayView` — red boxes for poles, green for everything else |

### Detected classes → pole inference

| COCO label | Why it indicates a pole |
|---|---|
| `traffic light` | Signal head on a vertical pole |
| `stop sign` | Sign face on a roadside post |
| `parking meter` | Meter unit on a short post |

> **Better accuracy**: Replace `app/src/main/assets/efficientdet_lite0.tflite` with a
> custom TFLite model trained specifically on traffic poles
> (e.g. from [Roboflow Universe — Traffic Pole](https://universe.roboflow.com/search?q=traffic+pole)).
> The TFLite Task Vision metadata API reads labels from the model file, so no extra
> label file is needed.

---

## Local build

```bash
# 1. Download model
bash scripts/download_model.sh          # Linux/macOS/Git Bash
# .\scripts\download_model.ps1          # PowerShell

# 2. Build debug APK
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and Android SDK (or just open in Android Studio).

---

## Project structure

```
.github/workflows/build-and-release.yml  — CI: download model → build → release
app/src/main/
├── java/com/trafficlightdetector/
│   ├── MainActivity.kt          — CameraX setup, lifecycle, UI updates
│   ├── ObjectDetectorHelper.kt  — TFLite wrapper + traffic-class filter (45% threshold)
│   ├── OverlayView.kt           — Canvas overlay for bounding boxes
│   └── SoundAlertManager.kt     — ToneGenerator with 2-second cooldown
├── res/layout/activity_main.xml
└── assets/
    └── efficientdet_lite0.tflite  (downloaded by CI / scripts — not committed)
scripts/
├── download_model.sh   — Linux/macOS/Git Bash
└── download_model.ps1  — Windows PowerShell
```

## License

Apache 2.0
