# Windows PowerShell version of download_model.sh
# Run from repo root:
#   .\scripts\download_model.ps1

$assetsDir = "app\src\main\assets"
$modelFile = "$assetsDir\efficientdet_lite0.tflite"
$modelUrl  = "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite"

if (-not (Test-Path $assetsDir)) {
    New-Item -ItemType Directory -Path $assetsDir | Out-Null
}

if (Test-Path $modelFile) {
    Write-Host "Model already present: $modelFile"
    exit 0
}

Write-Host "Downloading EfficientDet-Lite0 from MediaPipe model zoo..."
Invoke-WebRequest -Uri $modelUrl -OutFile $modelFile -UseBasicParsing
$size = (Get-Item $modelFile).Length / 1MB
Write-Host ("Saved to {0}  ({1:F1} MB)" -f $modelFile, $size)
