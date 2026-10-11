# PowerShell-only safety checks; never runs Arduino CLI or touches the ESP32.
$ErrorActionPreference = "Stop"
$script = Join-Path $PSScriptRoot "build-user-sketch.ps1"
function ShouldReject([hashtable]$parameters, [string]$expected) {
    $rejected = $false
    try {
        & $script @parameters
    } catch {
        if ($_.Exception.Message -match [regex]::Escape($expected)) {
            $rejected = $true
        } else {
            throw "Unexpected rejection: $($_.Exception.Message)"
        }
    }
    if (-not $rejected) { throw "Dangerous invocation unexpectedly allowed: $expected" }
}
ShouldReject @{ Source = "nonexistent.ino"; FlashUsb = $true } "requires explicit -Port"
ShouldReject @{ Source = "nonexistent.ino"; FlashUsb = $true; Port = "COM5" } "REEMPLAZAR_FIRMWARE"
ShouldReject @{ Source = "nonexistent.ino"; ReplaceCoreAcknowledgement = "REEMPLAZAR_FIRMWARE" } "compile-only mode"
Write-Host "PASS: unsafe firmware upload arguments are rejected before any board/tool access."
