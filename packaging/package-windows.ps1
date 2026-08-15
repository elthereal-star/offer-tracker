[CmdletBinding()]
param(
    [string]$Version = '0.1.0',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jarName = "offer-tracker-$Version.jar"
$jarPath = Join-Path $projectRoot "target\$jarName"
$stagingDirectory = Join-Path $projectRoot 'target\jpackage-input'
$iconPath = Join-Path $projectRoot 'target\OfferTracker.ico'
$distDirectory = Join-Path $projectRoot 'dist'
$appImageDirectory = Join-Path $distDirectory 'OfferTracker'
$archivePath = Join-Path $distDirectory "OfferTracker-Windows-x64-$Version.zip"
$checksumPath = "$archivePath.sha256"

if (-not $SkipBuild) {
    Push-Location $projectRoot
    try {
        & mvn.cmd clean package
        if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "JAR not found: $jarPath"
}
if (-not (Get-Command jpackage.exe -ErrorAction SilentlyContinue)) {
    throw 'jpackage.exe was not found. Install a JDK 21 distribution and add its bin directory to PATH.'
}

Remove-Item -LiteralPath $stagingDirectory -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $appImageDirectory -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $archivePath -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $checksumPath -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $stagingDirectory -Force | Out-Null
New-Item -ItemType Directory -Path $distDirectory -Force | Out-Null
Copy-Item -LiteralPath $jarPath -Destination (Join-Path $stagingDirectory $jarName)

Add-Type -AssemblyName System.Drawing
$bitmap = New-Object System.Drawing.Bitmap 256, 256
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
try {
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $darkBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(17, 24, 39))
    $greenPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(110, 231, 183)), 16
    $greenPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $greenPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $graphics.FillRectangle($darkBrush, 20, 20, 216, 216)
    $graphics.DrawRectangle($greenPen, 68, 97, 120, 91)
    $graphics.DrawLine($greenPen, 68, 124, 188, 124)
    $graphics.DrawArc($greenPen, 98, 67, 60, 60, 180, 180)
    $icon = [System.Drawing.Icon]::FromHandle($bitmap.GetHicon())
    $stream = [System.IO.File]::Open($iconPath, [System.IO.FileMode]::Create)
    try { $icon.Save($stream) } finally { $stream.Dispose(); $icon.Dispose() }
} finally {
    if ($greenPen) { $greenPen.Dispose() }
    if ($darkBrush) { $darkBrush.Dispose() }
    $graphics.Dispose()
    $bitmap.Dispose()
}

$jpackageArguments = @(
    '--type', 'app-image',
    '--name', 'OfferTracker',
    '--app-version', $Version,
    '--vendor', 'Offer Tracker Contributors',
    '--description', 'Job application and interview progress tracker',
    '--dest', $distDirectory,
    '--input', $stagingDirectory,
    '--main-jar', $jarName,
    '--icon', $iconPath,
    '--java-options', '-Doffertracker.desktop=true',
    '--java-options', '-Dfile.encoding=UTF-8'
)
& jpackage.exe @jpackageArguments
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }

Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'README-Windows.txt') -Destination $appImageDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Stop-OfferTracker.cmd') -Destination $appImageDirectory

Compress-Archive -LiteralPath $appImageDirectory -DestinationPath $archivePath -CompressionLevel Optimal
$hash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
[System.IO.File]::WriteAllText($checksumPath, "$hash  $([System.IO.Path]::GetFileName($archivePath))`r`n",
    [System.Text.UTF8Encoding]::new($false))

Write-Host "Portable package: $archivePath"
Write-Host "SHA256: $hash"
