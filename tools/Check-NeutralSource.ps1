param(
    [string]$Compiler = $env:GLSL_COMPILER,
    [string]$Output
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $repoRoot
try {
    if ([string]::IsNullOrWhiteSpace($Compiler)) {
        throw 'Configure -Compiler or GLSL_COMPILER; a missing compiler fails validation.'
    }
    $arguments = @('tools/check_shaders.py', '--compiler', $Compiler)
    if ($Output) { $arguments += @('--output', $Output) }
    & python @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Shader compile/link/render fixture failed.' }
    & python -m unittest discover -s tests -v
    if ($LASTEXITCODE -ne 0) { throw 'Neutral source tests failed.' }
} finally {
    Pop-Location
}
