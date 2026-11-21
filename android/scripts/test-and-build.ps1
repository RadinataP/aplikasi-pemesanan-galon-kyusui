$ErrorActionPreference = 'Stop'

Write-Host 'Stopping Gradle daemons...'
& .\gradlew.bat --stop
if ($LASTEXITCODE -ne 0) { throw "Gradle --stop gagal dengan exit code $LASTEXITCODE" }

Write-Host 'Running clean unit tests and assembling debug APK (without installing it)...'
& .\gradlew.bat clean :app:testDebugUnitTest :app:assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Build/test gagal dengan exit code $LASTEXITCODE" }

$apk = Join-Path (Get-Location) 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw "Build sukses tetapi APK debug tidak ditemukan: $apk" }
Write-Host "Build dan unit test berhasil. APK: $apk"
Write-Host 'APK belum dipasang ke perangkat mana pun.'
