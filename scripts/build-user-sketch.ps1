# Build user Arduino sketches locally using the free Arduino CLI.
# Nothing is flashed unless TWO explicit USB-only confirmations are supplied.
# Example: .\scripts\build-user-sketch.ps1 -Source C:\Temp\myprogram.ino
param(
    [Parameter(Mandatory=$true)][string]$Source,
    [string]$OutputDirectory = "",
    [string]$ArduinoCliPath = "",
    [string]$Port = "",
    [switch]$FlashUsb,
    [string]$ReplaceCoreAcknowledgement = ""
)
$ErrorActionPreference = "Stop"

# Check hazardous arguments BEFORE running a compiler or contacting the board.
if ($FlashUsb -and [string]::IsNullOrWhiteSpace($Port)) {
    throw "USB upload requires explicit -Port COMx. Never autodetect for writing."
}
if ($FlashUsb -and $ReplaceCoreAcknowledgement -cne "REEMPLAZAR_FIRMWARE") {
    throw "This sketch can REMOVE BLE/Wi-Fi/RGB and overwrite the controller. To flash, explicitly pass -ReplaceCoreAcknowledgement REEMPLAZAR_FIRMWARE after backup and USB recovery review."
}
if (-not $FlashUsb -and $ReplaceCoreAcknowledgement) {
    throw "Do not acknowledge firmware replacement for compile-only mode."
}

$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$sourcePath = (Resolve-Path -LiteralPath $Source -ErrorAction Stop).Path
if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
    throw "Source must be a regular .ino or UTF-8 .txt file."
}
$ext = [System.IO.Path]::GetExtension($sourcePath).ToLowerInvariant()
if ($ext -notin @(".ino", ".txt")) {
    throw "Only .ino and .txt source files are accepted. Export multi-file sketches from Arduino IDE."
}
$bytes = [System.IO.File]::ReadAllBytes($sourcePath)
if ($bytes.Length -lt 1 -or $bytes.Length -gt (128 * 1024)) {
    throw "Source file size must be 1–131072 bytes."
}
# Reject malformed text. A text file imported here is NOT a precompiled .bin.
$decoder = [System.Text.UTF8Encoding]::new($false, $true)
$text = $decoder.GetString($bytes).TrimStart([char]0xFEFF)
if ([string]::IsNullOrWhiteSpace($text) -or $text.Contains([char]0)) {
    throw "The source must be valid, nonempty UTF-8 text."
}

$profile = @{}
foreach ($line in (Get-Content (Join-Path $repo "config\esp32-board.env"))) {
    if ($line.Trim() -match '^([A-Z0-9_]+)=(.+)$') { $profile[$Matches[1]] = $Matches[2].Trim() }
}
$board = $profile["ARDUINO_FQBN"]
$core = $profile["ARDUINO_ESP32_CORE"]
if ($board -ne "esp32:esp32:esp32:PartitionScheme=min_spiffs" -or -not $core) {
    throw "Unreviewed board profile. Do not compile or upload."
}

$cli = $ArduinoCliPath
if (-not $cli) { $cli = $env:ARDUINO_CLI_PATH }
if (-not $cli) {
    $candidate = Get-Command arduino-cli.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) { $cli = $candidate.Source }
}
if (-not $cli) {
    $candidates = @(
        (Join-Path $repo ".tools\arduino-cli.exe"),
        (Join-Path $repo ".tools\arduino-cli\arduino-cli.exe")
    )
    if ($env:LOCALAPPDATA) {
        $candidates += Join-Path $env:LOCALAPPDATA "Programs\Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe"
    }
    if ($env:ProgramFiles) {
        $candidates += Join-Path $env:ProgramFiles "Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe"
    }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { $cli = $candidate; break }
    }
}
if (-not $cli -or -not (Test-Path -LiteralPath $cli -PathType Leaf)) {
    throw "Install the free Arduino CLI, add it to PATH or pass -ArduinoCliPath. No code was compiled/flashed."
}

if (-not $OutputDirectory) { $OutputDirectory = Join-Path $repo "local-validation\user-sketch" }
$OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$scratch = Join-Path ([System.IO.Path]::GetTempPath()) ("esp32-v4-" + [guid]::NewGuid().ToString("N"))
$sketch = Join-Path $scratch "ImportedSketch"
New-Item -ItemType Directory -Force -Path $sketch | Out-Null
try {
    $ino = Join-Path $sketch "ImportedSketch.ino"
    [System.IO.File]::WriteAllText($ino, $text, [System.Text.UTF8Encoding]::new($false))
    Write-Host "Compiling imported source using Arduino CLI and board profile $board; ESP32 core pinned to $core."
    & $cli compile --fqbn $board --output-dir $OutputDirectory $sketch
    if ($LASTEXITCODE -ne 0) { throw "Arduino compilation FAILED; nothing flashed." }
    $binary = Join-Path $OutputDirectory "ImportedSketch.ino.bin"
    if (-not (Test-Path -LiteralPath $binary -PathType Leaf)) {
        throw "Compiler reported success but main app .bin not found."
    }
    $hash = (Get-FileHash -LiteralPath $binary -Algorithm SHA256).Hash
    @(
        "Source=$([System.IO.Path]::GetFileName($sourcePath))",
        "Board=$board",
        "ESP32Core=$core",
        "BinarySHA256=$hash",
        "ControllerServicesPreserved=NOT_GUARANTEED",
        "OTA=DISABLED",
        "USB_Flash=$([bool]$FlashUsb)"
    ) | Set-Content -LiteralPath (Join-Path $OutputDirectory "build-info.txt")
    Write-Host "Compiled .bin: $binary"
    Write-Host "SHA256: $hash"
    if ($FlashUsb) {
        if ($Port -notmatch '^COM[0-9]+$') { throw "Invalid USB serial port. Expected COM5 etc." }
        Write-Warning "About to REPLACE installed controller firmware through USB $Port. Services may disappear."
        & $cli upload --fqbn $board --port $Port --input-dir $OutputDirectory $sketch
        if ($LASTEXITCODE -ne 0) { throw "USB upload FAILED; use base firmware recovery procedure." }
        Write-Host "USB upload complete. BLE/Wi-Fi/RGB require physical verification."
    } else {
        Write-Host "COMPILE ONLY. No firmware was uploaded and the ESP32 keeps running its current controller."
    }
}
finally {
    if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Recurse -Force }
}
