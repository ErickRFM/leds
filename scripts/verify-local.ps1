# Local integration pipeline: Android unit tests/lint/APK + ESP32 firmware compile.
# Run from repo root: powershell -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1
# Requires JDK 17, Android SDK Platform 34, and Arduino CLI.
param(
    [string]$Board = "esp32:esp32:esp32",
    [string]$Port = "",
    [switch]$UploadFirmware,
    [switch]$SkipAndroid
)
$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$reports = Join-Path $repo "local-validation"
$firmwareDir = Join-Path $repo "firmware\esp32_rgb"
$arduinoUrl = "https://espressif.github.io/arduino-esp32/package_esp32_index.json"
New-Item -ItemType Directory -Force -Path $reports | Out-Null

if (-not $SkipAndroid) {
    Write-Host "[1/3] Android: tests, lint and debug APK"
    & (Join-Path $repo "android\build-debug.ps1")
    if ($LASTEXITCODE -ne 0) { throw "Android build script failed" }
    $apk = Join-Path $repo "android\app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) { throw "APK missing after build" }
    $apkHash = (Get-FileHash -Path $apk -Algorithm SHA256).Hash
    "$apkHash  app-debug.apk" | Set-Content -Path (Join-Path $reports "apk-sha256.txt")
    Write-Host "Android APK: $apk"
    Write-Host "Android SHA256: $apkHash"
}

Write-Host "[2/3] ESP32: compilation using Arduino-ESP32 3.0.7"
$cli = Get-Command "arduino-cli" -ErrorAction SilentlyContinue
if (-not $cli) {
    throw "arduino-cli is not installed or not in PATH. Install the official Arduino CLI, then rerun."
}
& arduino-cli core update-index --additional-urls $arduinoUrl
if ($LASTEXITCODE -ne 0) { throw "Cannot update Espressif board index" }
& arduino-cli core install esp32:esp32@3.0.7 --additional-urls $arduinoUrl
if ($LASTEXITCODE -ne 0) { throw "Arduino ESP32 core install failed" }
$firmwareOutput = Join-Path $reports "firmware"
New-Item -ItemType Directory -Force -Path $firmwareOutput | Out-Null
& arduino-cli compile --fqbn $Board --output-dir $firmwareOutput $firmwareDir
if ($LASTEXITCODE -ne 0) { throw "ESP32 firmware compile failed" }
Write-Host "ESP32 firmware compiled: $firmwareOutput"

if ($UploadFirmware) {
    if ([string]::IsNullOrWhiteSpace($Port)) {
        throw "Pass -Port COMx together with -UploadFirmware. Do not guess the hardware port."
    }
    Write-Host "[3/3] Uploading to explicitly selected port $Port"
    & arduino-cli upload --fqbn $Board --port $Port --input-dir $firmwareOutput $firmwareDir
    if ($LASTEXITCODE -ne 0) { throw "ESP32 upload failed" }
} else {
    Write-Host "[3/3] Firmware NOT flashed (pass -UploadFirmware -Port COMx after checking wiring)"
}

$sha = (& git -C $repo rev-parse HEAD).Trim()
"commit=$sha" | Set-Content -Path (Join-Path $reports "result.txt")
"android_tests_lint_apk=" + $(if ($SkipAndroid) { "SKIPPED" } else { "PASSED" }) |
    Add-Content -Path (Join-Path $reports "result.txt")
"firmware_compile=PASSED" | Add-Content -Path (Join-Path $reports "result.txt")
"hardware_ble_test=NOT_RUN" | Add-Content -Path (Join-Path $reports "result.txt")
Write-Host "Local build tasks completed. Physical BLE tests must still be performed."
