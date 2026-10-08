# RGB Controller - Bootstrap Gradle 8.7 and build Android debug APK on Windows.
# Requires JDK 17 and Android SDK 34 installed (e.g. Android Studio).
param([switch]$SkipTests)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$tools = Join-Path $root ".tools"
$version = "8.7"
$gradle = Join-Path $tools "gradle-$version\bin\gradle.bat"
$archive = Join-Path $tools "gradle-$version-bin.zip"
$checksumFile = Join-Path $tools "gradle-$version-bin.zip.sha256"

New-Item -ItemType Directory -Force -Path $tools | Out-Null
if (-not (Test-Path $gradle)) {
    Write-Host "Downloading official Gradle $version distribution..."
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$version-bin.zip" -OutFile $archive
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$version-bin.zip.sha256" -OutFile $checksumFile
    $expected = (Get-Content $checksumFile -Raw).Trim().Split(" ")[0].ToLowerInvariant()
    $actual = (Get-FileHash -Path $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expected) { throw "Checksum mismatch for Gradle distribution." }
    Expand-Archive -Path $archive -DestinationPath $tools -Force
}
if (-not (Test-Path $gradle)) { throw "Gradle executable not found at $gradle" }
Push-Location $root
try {
    $tasks = if ($SkipTests) { @("assembleDebug") }
             else { @("testDebugUnitTest", "lintDebug", "assembleDebug") }
    & $gradle @tasks --stacktrace
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit $LASTEXITCODE)" }
    $apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) { throw "Build finished but APK was not found" }
    Write-Host "APK generated: $apk"
    Write-Host "SHA256: $((Get-FileHash $apk -Algorithm SHA256).Hash)"
} finally {
    Pop-Location
}
