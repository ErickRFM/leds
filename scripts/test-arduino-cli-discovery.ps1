# Windows PowerShell 5.1 regression: resolve Arduino CLI before Android or ESP32 builds.
$ErrorActionPreference = "Stop"
$verify = Join-Path $PSScriptRoot "verify-local.ps1"
$temp = Join-Path $env:TEMP ("arduino cli test " + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $temp | Out-Null
try {
    # The tool is optional for profile-only validation.
    & $verify -ValidateProfileOnly

    # Explicitly missing binary must fail before attempting Android build.
    $missing = Join-Path $temp "missing-arduino-cli.exe"
    $rejected = $false
    try {
        & $verify -SkipAndroid -ArduinoCliPath $missing -CheckToolchainOnly
    } catch {
        $rejected = $_.Exception.Message.Contains("Arduino CLI path does not exist")
    }
    if (-not $rejected) { throw "Expected a clear missing-CLI preflight error" }
    Write-Host "PASS: missing CLI path fails before any build"

    # Exercise discovery with a controllable executable shim and path containing spaces.
    $mock = Join-Path $temp "mock-arduino-cli.cmd"
    @'
@echo off
if "%~1"=="version" (
  echo arduino-cli Version: test-double
  exit /b 0
)
exit /b 42
'@ | Set-Content -Path $mock -Encoding Ascii
    & $verify -SkipAndroid -ArduinoCliPath $mock -CheckToolchainOnly
    Write-Host "PASS: explicitly supplied CLI path can be launched"

    # Test the environment override (does not require installing the official CLI).
    $previous = $env:ARDUINO_CLI_PATH
    try {
        $env:ARDUINO_CLI_PATH = $mock
        & $verify -SkipAndroid -CheckToolchainOnly
        Write-Host "PASS: ARDUINO_CLI_PATH override can be launched"
    } finally {
        $env:ARDUINO_CLI_PATH = $previous
    }
} finally {
    Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
}
Write-Host "PASS: Windows Arduino CLI discovery regression"
