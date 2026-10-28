param(
    [string]$Port = "COM5",
    [switch]$Upload
)
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

# Run from any directory. Enforces 4 MB flash with a 3 MB application partition.
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$sketch = Join-Path $root "firmware\esp32_rgb"
$build = Join-Path $root "build\compi-huge-4mb"
$fqbn = "esp32:esp32:esp32:PartitionScheme=huge_app,FlashSize=4M"
$maxApp = 3145728
if (-not (Get-Command arduino-cli -ErrorAction SilentlyContinue)) {
    throw "arduino-cli is not in PATH."
}
if (-not (Test-Path $sketch)) { throw "Firmware sketch not found: $sketch" }
New-Item -ItemType Directory -Path $build -Force | Out-Null
Write-Host "Compiling Compi for 4 MB ESP32 with Huge APP..." -ForegroundColor Cyan
& arduino-cli compile --fqbn $fqbn --build-path $build $sketch
if ($LASTEXITCODE -ne 0) { throw "Compilation failed. Nothing was flashed." }
$binary = Join-Path $build "esp32_rgb.ino.bin"
if (-not (Test-Path $binary)) { throw "App binary not found at $binary. Nothing was flashed." }
$size = (Get-Item $binary).Length
Write-Host "Application: $size / $maxApp bytes" -ForegroundColor Cyan
if ($size -ge $maxApp) { throw "App exceeds the 3 MB partition. Nothing was flashed." }
if (-not $Upload) {
    Write-Host "Compilation passed. No upload performed. Re-run with -Upload after flash backup." -ForegroundColor Green
    exit 0
}
# Preserve the current identity, owner pairing, calibration and other NVS data.
# Changing partition tables can still make existing settings inaccessible.
$backup = Join-Path $root ("compi-backup-" + (Get-Date -Format "yyyyMMdd-HHmmss") + "-4mb.bin")
Write-Host "Reading 4 MB flash backup into $backup" -ForegroundColor Yellow
& python -m esptool --chip esp32 --port $Port --baud 115200 read-flash 0x0 0x400000 $backup
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $backup)) { throw "Flash backup failed. Nothing was flashed." }
if ((Get-Item $backup).Length -ne 4194304) { throw "Flash backup incomplete. Nothing was flashed." }
Write-Host "Backup verified: $backup" -ForegroundColor Green
Write-Host "Uploading with the SAME 4 MB Huge APP FQBN..." -ForegroundColor Cyan
& arduino-cli upload --port $Port --fqbn $fqbn --input-dir $build $sketch
if ($LASTEXITCODE -ne 0) { throw "Upload failed. Keep your flash backup." }
Write-Host "Upload completed. Test boot on Serial Monitor at 115200 before reconnecting peripherals." -ForegroundColor Green
