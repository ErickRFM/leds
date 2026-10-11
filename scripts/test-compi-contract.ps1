param([string]$Compiler = "")
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$source = Join-Path $root "tests\compi_capabilities_test.cpp"
if (-not (Test-Path -LiteralPath $source)) { throw "Missing contract tests: $source" }
if ([string]::IsNullOrWhiteSpace($Compiler)) {
    foreach ($name in @("g++", "clang++")) {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue
        if ($cmd) { $Compiler = $cmd.Source; break }
    }
}
if ([string]::IsNullOrWhiteSpace($Compiler)) {
    throw "C++ host compiler missing. Install g++ or clang++, or provide -Compiler."
}
$out = Join-Path ([System.IO.Path]::GetTempPath()) ("compi-capability-test-" + [Guid]::NewGuid().ToString("N") + ".exe")
try {
    & $Compiler -std=c++17 -Wall -Wextra -Werror $source -o $out
    if ($LASTEXITCODE -ne 0) { throw "Contract compilation failed" }
    & $out
    if ($LASTEXITCODE -ne 0) { throw "Contract test failed" }
    Write-Host "PASS: Compi V4 capability contract host tests"
} finally {
    Remove-Item -LiteralPath $out -Force -ErrorAction SilentlyContinue
}
