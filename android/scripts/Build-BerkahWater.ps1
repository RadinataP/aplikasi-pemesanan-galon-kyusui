$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $project
try {
    if (-not (Test-Path -LiteralPath '.\gradlew.bat')) { throw 'gradlew.bat tidak ditemukan.' }
    $api = 'https://api.berkahwater.my.id/api/v1/'
    & .\gradlew.bat :app:assembleDebug "-PkyusuiApiBaseUrl=$api" "-PkyusuiProdApiBaseUrl=$api"
    if ($LASTEXITCODE -ne 0) { throw 'Build gagal. APK tidak disalin.' }
    $apk = '.\app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw 'APK hasil build tidak ditemukan.' }
    $dist = Join-Path $project 'dist'
    New-Item -ItemType Directory -Path $dist -Force | Out-Null
    $target = Join-Path $dist 'Berkah-Water-DEBUG-TEST-ONLY.apk'
    Copy-Item -LiteralPath $apk -Destination $target -Force
    Get-Item -LiteralPath $target | Select-Object FullName, Length, LastWriteTime
    Write-Host 'APK debug untuk beta testing berhasil diekspor. Uji sebelum publikasi.' -ForegroundColor Green
} finally {
    Pop-Location
}
