# Execute actual Windows PowerShell 5.1 profile parsing, without Android or firmware builds.
$ErrorActionPreference = "Stop"
$script = Join-Path $PSScriptRoot "verify-local.ps1"
$profile = Join-Path $PSScriptRoot "..\config\esp32-board.env"
$original = [System.IO.File]::ReadAllText($profile)
try {
    # Positive: the ESP32 core version contains digits in the key name.
    & $script -ValidateProfileOnly
    Write-Host "PASS: real board profile accepted"

    # Negative: missing and duplicate keys must fail before any installation.
    foreach ($scenario in @(
        @{ name = "missing-core"; value = "ARDUINO_FQBN=esp32:esp32:esp32:PartitionScheme=min_spiffs`n"; error = "Incomplete board profile" },
        @{ name = "duplicate-core"; value = "$original`nARDUINO_ESP32_CORE=3.0.7`n"; error = "Duplicate board profile key" },
        @{ name = "malformed"; value = "$original`nBOGUS LINE`n"; error = "Invalid ESP32 board profile entry" }
    )) {
        [System.IO.File]::WriteAllText($profile, [string]$scenario.value)
        $rejected = $false
        try {
            & $script -ValidateProfileOnly
        } catch {
            if ($_.Exception.Message.Contains([string]$scenario.error)) {
                $rejected = $true
            } else {
                throw
            }
        }
        if (-not $rejected) { throw "Profile parser accepted invalid case: $($scenario.name)" }
        Write-Host "PASS: rejected $($scenario.name)"
    }
} finally {
    [System.IO.File]::WriteAllText($profile, $original)
}
Write-Host "PASS: PowerShell board profile acceptance and rejection tests"
