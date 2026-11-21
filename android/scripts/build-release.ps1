param(
    [Parameter(Mandatory = $true)]
    [string]$ProductionApiBaseUrl
)
$ErrorActionPreference = 'Stop'

if (-not $env:JAVA_HOME) {
    throw 'JAVA_HOME belum diset. Gunakan JDK 17 sebelum menjalankan verifikasi.'
}
$javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path $javaExe)) { throw "Java tidak ditemukan: $javaExe" }
$javaVersion = & $javaExe -version 2>&1 | Out-String
if ($javaVersion -notmatch '17\.') { throw "JDK 17 wajib digunakan. Versi terdeteksi: $javaVersion" }
if (-not (Test-Path '.\gradlew.bat')) { throw 'Jalankan script dari folder android/.' }
if (-not (Test-Path '.\local.properties')) { throw 'local.properties tidak ditemukan; pastikan sdk.dir valid.' }

$uri = $null
if (-not [Uri]::TryCreate($ProductionApiBaseUrl, [UriKind]::Absolute, [ref]$uri)) {
    throw 'ProductionApiBaseUrl bukan URL absolut yang valid.'
}
if ($uri.Scheme -ne 'https') { throw 'URL backend release wajib HTTPS.' }
if (-not $uri.AbsolutePath.EndsWith('/')) { throw 'URL backend release wajib diakhiri /.' }
if ($uri.Host -eq 'example.com' -or $uri.Host.EndsWith('.example.com') -or $uri.Host.EndsWith('.example')) {
    throw 'Host contoh tidak boleh digunakan untuk release. Masukkan host backend produksi yang nyata.'
}

Write-Host '== Stop Gradle daemons ==' -ForegroundColor Cyan
& .\gradlew.bat --stop
if ($LASTEXITCODE -ne 0) { throw 'Gradle --stop gagal.' }

Write-Host '== Unit tests + assemble debug + assemble release ==' -ForegroundColor Cyan
& .\gradlew.bat clean :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug :app:assembleRelease "-PkyusuiProdApiBaseUrl=$ProductionApiBaseUrl"
if ($LASTEXITCODE -ne 0) { throw 'Build/test release gagal. Periksa output Gradle di atas.' }

$apk = '.\app\build\outputs\apk\release\app-release-unsigned.apk'
if (-not (Test-Path $apk)) {
    $apk = '.\app\build\outputs\apk\release\app-release.apk'
}
if (-not (Test-Path $apk)) { throw 'APK release tidak ditemukan setelah build sukses.' }
Write-Host "RELEASE BUILD SUCCESSFUL: $((Resolve-Path $apk).Path)" -ForegroundColor Green
Write-Warning 'APK belum tentu ditandatangani untuk distribusi. Konfigurasikan signing release secara aman sebelum publikasi.'
