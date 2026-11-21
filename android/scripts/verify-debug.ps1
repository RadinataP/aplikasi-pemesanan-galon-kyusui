$ErrorActionPreference = 'Stop'

if (-not $env:JAVA_HOME) {
    throw 'JAVA_HOME belum diset. Gunakan JDK 17 sebelum menjalankan verifikasi.'
}
$javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path $javaExe)) {
    throw "Java tidak ditemukan: $javaExe"
}
$versionStdout = Join-Path $env:TEMP ("kyusui-java-version-out-" + [guid]::NewGuid().ToString() + ".txt")
$versionStderr = Join-Path $env:TEMP ("kyusui-java-version-err-" + [guid]::NewGuid().ToString() + ".txt")
try {
    $javaProcess = Start-Process -FilePath $javaExe -ArgumentList '-version' `
        -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput $versionStdout `
        -RedirectStandardError $versionStderr
    $javaVersion = ((Get-Content $versionStdout -ErrorAction SilentlyContinue) -join "`n") + "`n" +
                   ((Get-Content $versionStderr -ErrorAction SilentlyContinue) -join "`n")
}
finally {
    Remove-Item $versionStdout, $versionStderr -Force -ErrorAction SilentlyContinue
}
if ($javaVersion -notmatch '17\.') {
    throw "JDK 17 wajib digunakan. Versi terdeteksi: $javaVersion"
}
if (-not (Test-Path '.\gradlew.bat')) {
    throw 'Jalankan script dari folder android/ yang berisi gradlew.bat.'
}
if (-not (Test-Path '.\local.properties')) {
    throw 'local.properties tidak ditemukan. Pastikan sdk.dir menunjuk Android SDK yang valid.'
}

Write-Host '== Stop Gradle daemons ==' -ForegroundColor Cyan
& .\gradlew.bat --stop
if ($LASTEXITCODE -ne 0) { throw 'Gradle --stop gagal.' }

Write-Host '== Clean + unit test debug + assemble debug ==' -ForegroundColor Cyan
& .\gradlew.bat clean :app:testDebugUnitTest :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Verifikasi debug gagal. Periksa output Gradle di atas.' }

$apk = '.\app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw 'APK debug tidak ditemukan setelah build sukses.' }
Write-Host "DEBUG BUILD SUCCESSFUL: $((Resolve-Path $apk).Path)" -ForegroundColor Green
