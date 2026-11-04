param (
    [string]$DeviceIp = ""
)

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "      Aura Clock • Deployment Tool       " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

$apkPath = "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apkPath)) {
    Write-Host "APK non trovato! Eseguo compilazione assembleDebug..." -ForegroundColor Yellow
    Set-Location $PSScriptRoot
    .\gradlew.bat assembleDebug
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Errore di compilazione. Operazione annullata." -ForegroundColor Red
        exit 1
    }
}

if ($DeviceIp -ne "") {
    Write-Host "Connessione ADB a $DeviceIp..." -ForegroundColor Yellow
    adb connect $DeviceIp
}

$devices = adb devices | Select-String "device$"
if ($devices.Count -eq 0) {
    Write-Host "Nessun dispositivo ADB rilevato!" -ForegroundColor Red
    Write-Host "Collega lo Xiaomi Mi Smart Clock via USB oppure specifica l'IP con:" -ForegroundColor Yellow
    Write-Host "  .\deploy.ps1 -DeviceIp 192.168.1.XX" -ForegroundColor White
    exit 1
}

Write-Host "Dispositivo rilevato: $($devices[0])" -ForegroundColor Green
Write-Host "Installazione APK ($apkPath)..." -ForegroundColor Yellow

adb install -r -d $apkPath

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Installazione completata con successo!" -ForegroundColor Green
    Write-Host "Avvio Aura Clock come Home Launcher..." -ForegroundColor Cyan
    adb shell am start -n com.custom.auraclock/.MainActivity
} else {
    Write-Host "Errore durante l'installazione via ADB." -ForegroundColor Red
}
