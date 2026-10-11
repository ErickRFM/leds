# Local integration pipeline: Android unit tests/lint/APK + ESP32 firmware compile.
# Run from repo root: powershell -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1
# Requires JDK 17, Android SDK Platform 34, and Arduino CLI.
param(
    [string]$Board = "",
    [string]$Port = "",
    [switch]$UploadFirmware,
    [switch]$SkipAndroid,
    [switch]$ValidateProfileOnly,
    [switch]$CheckToolchainOnly,
    [string]$ArduinoCliPath = ""
)
$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$reports = Join-Path $repo "local-validation"
$firmwareDir = Join-Path $repo "firmware\esp32_rgb"
$arduinoUrl = "https://espressif.github.io/arduino-esp32/package_esp32_index.json"
$profilePath = Join-Path $repo "config\esp32-board.env"
if (-not (Test-Path $profilePath)) { throw "Missing ESP32 board profile: $profilePath" }
$profile = @{}
foreach ($line in (Get-Content -Path $profilePath)) {
    $trimmed = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) { continue }
    if ($trimmed -match '^([A-Z0-9_]+)=(.+)$') {
        $key = $Matches[1]
        if ($profile.ContainsKey($key)) { throw "Duplicate board profile key: $key" }
        $profile[$key] = $Matches[2].Trim()
    } else {
        throw "Invalid ESP32 board profile entry: $trimmed"
    }
}
$expectedBoard = $profile["ARDUINO_FQBN"]
$coreVersion = $profile["ARDUINO_ESP32_CORE"]
if ([string]::IsNullOrWhiteSpace($expectedBoard) -or [string]::IsNullOrWhiteSpace($coreVersion)) {
    throw "Incomplete board profile"
}
if ([string]::IsNullOrWhiteSpace($Board)) { $Board = $expectedBoard }
if ($Board -ne $expectedBoard) {
    throw "Board $Board differs from the audited 4 MB layout ($expectedBoard). Update the profile only after a partition and recovery audit."
}
Write-Host "Verified board profile: $Board (ESP32 core $coreVersion)"
if ($ValidateProfileOnly) {
    Write-Host "PASS: ESP32 board profile validation (no build, no flash)"
    return
}
# Resolve Arduino CLI before the (potentially expensive) Android build.
# Reuse an official installation in PATH or from Arduino IDE where available.
$cliPath = $null
if (-not [string]::IsNullOrWhiteSpace($ArduinoCliPath)) {
    if (-not (Test-Path -LiteralPath $ArduinoCliPath -PathType Leaf)) {
        throw "Arduino CLI path does not exist: $ArduinoCliPath"
    }
    $cliPath = (Resolve-Path -LiteralPath $ArduinoCliPath).Path
} elseif (-not [string]::IsNullOrWhiteSpace($env:ARDUINO_CLI_PATH)) {
    if (-not (Test-Path -LiteralPath $env:ARDUINO_CLI_PATH -PathType Leaf)) {
        throw "ARDUINO_CLI_PATH does not exist: $env:ARDUINO_CLI_PATH"
    }
    $cliPath = (Resolve-Path -LiteralPath $env:ARDUINO_CLI_PATH).Path
} else {
    $command = Get-Command "arduino-cli.exe" -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($command) { $cliPath = $command.Source }
    if (-not $cliPath) {
        $candidates = @(
            (Join-Path $repo ".tools\arduino-cli.exe"),
            (Join-Path $repo ".tools\arduino-cli\arduino-cli.exe")
        )
        if ($env:ProgramFiles) {
            $candidates += Join-Path $env:ProgramFiles "Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe"
            $candidates += Join-Path $env:ProgramFiles "Arduino CLI\arduino-cli.exe"
        }
        if ($env:LOCALAPPDATA) {
            $candidates += Join-Path $env:LOCALAPPDATA "Programs\Arduino CLI\arduino-cli.exe"
            $candidates += Join-Path $env:LOCALAPPDATA "Programs\Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe"
        }
        foreach ($candidate in $candidates) {
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                $cliPath = $candidate
                break
            }
        }
    }
}
if (-not $cliPath) {
    throw "Arduino CLI not found. Install with: winget install --id ArduinoSA.CLI --exact ; reopen PowerShell and run: arduino-cli version ; or pass -ArduinoCliPath 'C:\path\arduino-cli.exe'. Your Android APK is already built; rerun this script with -SkipAndroid to compile firmware only. Nothing was flashed."
}
Write-Host "Arduino CLI: $cliPath"
if ($CheckToolchainOnly) {
    & $cliPath version
    if ($LASTEXITCODE -ne 0) { throw "Arduino CLI could not start: $cliPath" }
    Write-Host "PASS: Arduino CLI executable located and runnable (no build, no flash)"
    return
}
if ($UploadFirmware -and [string]::IsNullOrWhiteSpace($Port)) {
    throw "Pass -Port COMx together with -UploadFirmware; do not guess the hardware port."
}
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

Write-Host "[2/3] ESP32: compilation using Arduino-ESP32 $coreVersion"
& $cliPath core update-index --additional-urls $arduinoUrl
if ($LASTEXITCODE -ne 0) { throw "Cannot update Espressif board index" }
& $cliPath core install "esp32:esp32@$coreVersion" --additional-urls $arduinoUrl
if ($LASTEXITCODE -ne 0) { throw "Arduino ESP32 core install failed" }
$firmwareOutput = Join-Path $reports "firmware"
New-Item -ItemType Directory -Force -Path $firmwareOutput | Out-Null
& $cliPath compile --fqbn $Board --output-dir $firmwareOutput $firmwareDir
if ($LASTEXITCODE -ne 0) { throw "ESP32 firmware compile failed" }
Write-Host "ESP32 firmware compiled: $firmwareOutput"

if ($UploadFirmware) {
    if ([string]::IsNullOrWhiteSpace($Port)) {
        throw "Pass -Port COMx together with -UploadFirmware. Do not guess the hardware port."
    }
    Write-Host "[3/3] Uploading to explicitly selected port $Port"
    & $cliPath upload --fqbn $Board --port $Port --input-dir $firmwareOutput $firmwareDir
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
