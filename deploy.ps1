param (
    [string]$DeviceIp = "",
    [string]$Serial = ""
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
    $target = $DeviceIp
} elseif ($Serial -ne "") {
    $target = $Serial
} else {
    $x04g = adb devices | Select-String "29433A1ND27021"
    if ($x04g) {
        $target = "29433A1ND27021"
    } else {
        $devs = adb devices | Where-Object { $_ -match "\tdevice$" }
        if ($devs.Count -gt 0) {
            $target = ($devs[0] -split "`t")[0]
        } else {
            Write-Host "Nessun dispositivo ADB rilevato!" -ForegroundColor Red
            exit 1
        }
    }
}

Write-Host "Dispositivo target: $target" -ForegroundColor Green
Write-Host "Installazione APK ($apkPath)..." -ForegroundColor Yellow

adb -s $target install -r -d $apkPath

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Installazione completata con successo!" -ForegroundColor Green
    Write-Host "Avvio Aura Clock come Home Launcher..." -ForegroundColor Cyan
    adb -s $target shell monkey -p com.custom.auraclock -c android.intent.category.LAUNCHER 1
    adb -s $target shell cmd package set-home-activity com.custom.auraclock/.MainActivity
} else {
    Write-Host "Errore durante l'installazione via ADB." -ForegroundColor Red
}
