# Offline Arduino C++ build for a locally supplied, complete .ino sketch.
# Never uploads, flashes, starts a network server or performs OTA.
param(
    [Parameter(Mandatory = $true)]
    [string]$SketchPath,
    [string]$ArduinoCliPath = ""
)
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$profilePath = Join-Path $repo "config\esp32-board.env"
if (-not (Test-Path -LiteralPath $profilePath -PathType Leaf)) {
    throw "No se encontró el perfil auditado del ESP32."
}
$profile = @{}
foreach ($line in (Get-Content -LiteralPath $profilePath)) {
    $line = $line.Trim()
    if ($line.Length -eq 0 -or $line.StartsWith("#")) { continue }
    if ($line -notmatch '^([A-Z0-9_]+)=(.+)$') { throw "Perfil ESP32 inválido." }
    if ($profile.ContainsKey($Matches[1])) { throw "Perfil ESP32 duplicado." }
    $profile[$Matches[1]] = $Matches[2].Trim()
}
$board = $profile["ARDUINO_FQBN"]
$core = $profile["ARDUINO_ESP32_CORE"]
if ([string]::IsNullOrWhiteSpace($board) -or [string]::IsNullOrWhiteSpace($core)) {
    throw "Perfil de placa incompleto."
}

$selected = Get-Item -LiteralPath $SketchPath -ErrorAction Stop
$sketchDir = if ($selected.PSIsContainer) { $selected.FullName } else {
    if ($selected.Extension -ine ".ino") { throw "Selecciona la carpeta del sketch o un archivo .ino." }
    $selected.Directory.FullName
}
$name = Split-Path -Path $sketchDir -Leaf
if ($name -notmatch '^[A-Za-z0-9_-]{1,60}$') {
    throw "Nombre de carpeta inválido. Usa letras, números, guion o guion bajo."
}
$entry = Join-Path $sketchDir ($name + ".ino")
if (-not (Test-Path -LiteralPath $entry -PathType Leaf)) {
    throw "Arduino CLI requiere un sketch principal con el mismo nombre que su carpeta: $name.ino"
}
if (-not $selected.PSIsContainer -and $selected.FullName -ne (Get-Item -LiteralPath $entry).FullName) {
    throw "El archivo elegido no es el sketch principal de la carpeta."
}

$cli = $null
if (-not [string]::IsNullOrWhiteSpace($ArduinoCliPath)) {
    $cli = (Resolve-Path -LiteralPath $ArduinoCliPath -ErrorAction Stop).Path
} elseif (-not [string]::IsNullOrWhiteSpace($env:ARDUINO_CLI_PATH)) {
    $cli = (Resolve-Path -LiteralPath $env:ARDUINO_CLI_PATH -ErrorAction Stop).Path
} else {
    $found = Get-Command "arduino-cli.exe" -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($found) { $cli = $found.Source }
    if (-not $cli) {
        foreach ($candidate in @(
            (Join-Path $repo ".tools\arduino-cli.exe"),
            (Join-Path $repo ".tools\arduino-cli\arduino-cli.exe")
        )) {
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { $cli = $candidate; break }
        }
    }
}
if (-not $cli) {
    throw "Arduino CLI no encontrado. Instala con winget install --id ArduinoSA.CLI --exact o usa -ArduinoCliPath."
}
$cliVersion = (& $cli version 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0) { throw "No se pudo iniciar Arduino CLI." }

# The pinned core MUST be installed before compiling. Do not silently update a toolchain.
$coreList = (& $cli core list 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw "No se pudo consultar los cores Arduino instalados." }
$matching = '(?m)^esp32:esp32\s+' + [regex]::Escape($core) + '(\s|$)'
if ($coreList -notmatch $matching) {
    throw "Se requiere esp32:esp32@$core. Instala el core con Arduino CLI y vuelve a compilar."
}

$output = Join-Path $repo ("local-validation\arduino-projects\" + $name)
New-Item -ItemType Directory -Force -Path $output | Out-Null
Write-Host "Compilando sketch: $entry"
Write-Host "Perfil: $board / esp32:esp32@$core"
& $cli compile --fqbn $board --output-dir $output $sketchDir
if ($LASTEXITCODE -ne 0) { throw "Arduino CLI rechazó el sketch. No se ha flasheado nada." }

$bin = Join-Path $output ($name + ".ino.bin")
if (-not (Test-Path -LiteralPath $bin -PathType Leaf)) {
    throw "La compilación terminó sin el binario esperado: $name.ino.bin"
}
$file = Get-Item -LiteralPath $bin
$maxBytes = [long]0x1E0000 - 65536
if ($file.Length -gt $maxBytes) {
    throw "Firmware excede el presupuesto de referencia (slot menos 64 KiB). NO instalar. Audita particiones."
}
$binaryHash = (Get-FileHash -LiteralPath $bin -Algorithm SHA256).Hash
$sourceHash = (Get-FileHash -LiteralPath $entry -Algorithm SHA256).Hash
$manifest = [ordered]@{
    schema = 1
    language = "arduino-cpp"
    sketch = $name
    source_sha256 = $sourceHash
    binary_filename = $file.Name
    binary_sha256 = $binaryHash
    binary_bytes = $file.Length
    expected_board = $board
    expected_esp32_core = $core
    arduino_cli = $cliVersion
    safety = "UNSIGNED_LOCAL_BUILD_NOT_APPROVED_FOR_OTA"
    preserves_control_center = $false
    upload_performed = $false
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $output "manifest.json") -Encoding UTF8
Write-Host "PASS: Binario creado localmente: $bin"
Write-Host "SHA-256: $binaryHash"
Write-Host "Manifest: $(Join-Path $output 'manifest.json')"
Write-Host "NO se grabó firmware. No usar este binario para OTA sin verificación, firma y recuperación USB."
