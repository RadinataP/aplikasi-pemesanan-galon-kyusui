[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectPath
)

$ErrorActionPreference = 'Stop'
$ProjectPath = (Resolve-Path -LiteralPath $ProjectPath).Path
if (-not (Test-Path (Join-Path $ProjectPath 'artisan'))) {
    throw "Folder project tidak valid: file artisan tidak ditemukan di $ProjectPath"
}

$sourceRoot = Join-Path $PSScriptRoot ''
$relativeFiles = @(
    'app/Http/Controllers/Api/OwnerController.php',
    'tests/Feature/OrderApiTest.php',
    'tests/Feature/OwnerControllerTest.php'
)
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupRoot = Join-Path $ProjectPath "_backup_before_qris_fixes_$stamp"
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null

foreach ($relative in $relativeFiles) {
    $source = Join-Path $sourceRoot $relative
    $destination = Join-Path $ProjectPath $relative
    if (-not (Test-Path -LiteralPath $source)) { throw "File patch tidak ditemukan: $source" }
    if (-not (Test-Path -LiteralPath $destination)) { throw "File project tidak ditemukan: $destination" }

    $backup = Join-Path $backupRoot $relative
    $backupDirectory = Split-Path -Parent $backup
    New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
    Copy-Item -LiteralPath $destination -Destination $backup -Force
    Copy-Item -LiteralPath $source -Destination $destination -Force
    Write-Host "Diperbarui: $relative" -ForegroundColor Green
}

Write-Host "`nSelesai. Backup file asli: $backupRoot" -ForegroundColor Cyan
Write-Host 'Berikutnya jalankan: php artisan test --filter=OrderApiTest'
Write-Host 'Lalu: php artisan test --filter=OwnerControllerTest'
Write-Host 'Terakhir: php artisan test'
