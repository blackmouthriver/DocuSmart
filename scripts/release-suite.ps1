<#
.SYNOPSIS
  Suite de pruebas previa a un release de DocuSmart (ver docs/requirements/suite-pruebas-release.md).

.DESCRIPTION
  Etapas (se eligen con -Stages, en este orden):
    Static        ktlintCheck, detekt, lintDebug y testDebugUnitTest (los mismos de CI).
    Build         assembleDebug + assembleDebugAndroidTest (los APK que usa la etapa Instrumented).
    Instrumented  Pruebas instrumentadas repartidas entre los dispositivos de -Devices.
                  Cada dispositivo corre su porción (numShards = nº de dispositivos) EN PARALELO,
                  con `am instrument` directo: no depende de Gradle, así que no hay bloqueo del
                  directorio de build entre dispositivos.
    Bundle        bundleRelease firmado (.aab). NO se incluye por defecto: se pide explícitamente.

  Cada etapa se ejecuta por separado (la combinación larga de Gradle supera el límite de 10 min
  de la herramienta del asistente y mata el proceso) y escribe su log en build\release-suite\<fecha>\.
  El script termina con código distinto de 0 si cualquier etapa falla.

.EXAMPLE
  # Solo verificación estática y APKs (sin dispositivos):
  .\scripts\release-suite.ps1 -Stages Static,Build

.EXAMPLE
  # Instrumentadas repartidas en el emulador y el teléfono real:
  .\scripts\release-suite.ps1 -Stages Instrumented -Devices emulator-5554,ZY22G7SB77

.NOTES
  Las pruebas instrumentadas BORRAN/ESCRIBEN datos de la app instalada en cada dispositivo
  (bases de datos, preferencias). Usar solo dispositivos con datos desechables.
#>
[CmdletBinding()]
param(
    [ValidateSet('Static', 'Build', 'Instrumented', 'Bundle')]
    [string[]]$Stages = @('Static', 'Build', 'Instrumented'),

    # Seriales de adb (ver `adb devices`). Obligatorio para la etapa Instrumented.
    [string[]]$Devices = @(),

    # Paquete de la app y de las pruebas instrumentadas.
    [string]$AppId = 'com.docsmart',
    [string]$Runner = 'com.docsmart.test/androidx.test.runner.AndroidJUnitRunner'
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$outDir = Join-Path $repo ("build\release-suite\" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$results = [ordered]@{}

function Invoke-Gradle {
    param([string]$Name, [string[]]$GradleArgs)
    $log = Join-Path $outDir "$Name.log"
    Write-Host "==> gradle $($GradleArgs -join ' ')  (log: $log)"
    & "$repo\gradlew.bat" @GradleArgs '--console=plain' *> $log
    $ok = ($LASTEXITCODE -eq 0)
    $results[$Name] = if ($ok) { 'OK' } else { "FALLÓ (exit $LASTEXITCODE)" }
    return $ok
}

function Invoke-Static {
    # Una invocación por tarea: ninguna se acerca al límite de 10 minutos.
    foreach ($task in 'ktlintCheck', 'detekt', 'lintDebug', 'testDebugUnitTest') {
        [void](Invoke-Gradle -Name "static-$task" -GradleArgs @($task))
    }
}

function Invoke-Build {
    [void](Invoke-Gradle -Name 'build-debug' -GradleArgs @('assembleDebug'))
    [void](Invoke-Gradle -Name 'build-androidtest' -GradleArgs @('assembleDebugAndroidTest'))
}

function Invoke-Bundle {
    if (-not (Test-Path (Join-Path $repo 'keystore.properties'))) {
        throw 'Falta keystore.properties: bundleRelease no puede firmar el .aab.'
    }
    if (Invoke-Gradle -Name 'bundle-release' -GradleArgs @('bundleRelease')) {
        Get-ChildItem (Join-Path $repo 'app\build\outputs\bundle\release\*.aab') |
            ForEach-Object { Write-Host "AAB: $($_.FullName)  ($([math]::Round($_.Length / 1MB, 1)) MB)" }
    }
}

function Invoke-Instrumented {
    if ($Devices.Count -lt 1) { throw 'La etapa Instrumented necesita -Devices (seriales de adb).' }
    $connected = (& $adb devices) | Where-Object { $_ -match "`tdevice$" } | ForEach-Object { ($_ -split "`t")[0] }
    foreach ($d in $Devices) {
        if ($connected -notcontains $d) { throw "Dispositivo '$d' no conectado. Conectados: $($connected -join ', ')" }
    }
    $apk = Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk'
    $testApk = Join-Path $repo 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    foreach ($f in $apk, $testApk) { if (-not (Test-Path $f)) { throw "Falta $f (corre la etapa Build)." } }

    $n = $Devices.Count
    $jobs = @()
    for ($i = 0; $i -lt $n; $i++) {
        $serial = $Devices[$i]
        $log = Join-Path $outDir "instrumented-$serial.log"
        Write-Host "==> $serial : porción $i de $n  (log: $log)"
        $jobs += Start-Job -Name $serial -ArgumentList $adb, $serial, $apk, $testApk, $Runner, $i, $n, $log -ScriptBlock {
            param($adb, $serial, $apk, $testApk, $runner, $idx, $total, $log)
            # Mismo ajuste que CI: sin animaciones (las pruebas de Compose se vuelven inestables con ellas).
            foreach ($s in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') {
                & $adb -s $serial shell settings put global $s 0.0
            }
            & $adb -s $serial install -r -t $apk | Out-File $log
            & $adb -s $serial install -r -t $testApk | Out-File $log -Append
            & $adb -s $serial shell am instrument -w -r `
                -e numShards $total -e shardIndex $idx `
                -e listener com.docsmart.core.ui.test.ClearMocksListener $runner | Out-File $log -Append
            foreach ($s in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') {
                & $adb -s $serial shell settings put global $s 1.0
            }
        }
    }
    $jobs | Wait-Job | Out-Null
    $jobs | Remove-Job

    foreach ($serial in $Devices) {
        $text = Get-Content (Join-Path $outDir "instrumented-$serial.log") -Raw
        # `-r` (raw) escribe INSTRUMENTATION_STATUS_CODE: -2/-1 por cada prueba fallida/con error.
        $failed = ([regex]::Matches($text, 'INSTRUMENTATION_STATUS_CODE: (-1|-2)')).Count
        $ran = ([regex]::Matches($text, 'INSTRUMENTATION_STATUS_CODE: 1\b')).Count
        $crash = $text -match 'INSTRUMENTATION_RESULT: shortMsg=Process crashed'
        $ok = ($failed -eq 0) -and (-not $crash) -and ($ran -gt 0)
        $results["instrumented-$serial"] = if ($ok) { "OK ($ran pruebas)" } else { "FALLÓ ($failed fallidas, $ran iniciadas, crash=$crash)" }
    }
}

foreach ($stage in 'Static', 'Build', 'Instrumented', 'Bundle') {
    if ($Stages -notcontains $stage) { continue }
    & "Invoke-$stage"
}

Write-Host "`n===== RESUMEN (logs en $outDir) ====="
$results.GetEnumerator() | ForEach-Object { Write-Host ("{0,-34} {1}" -f $_.Key, $_.Value) }
if ($results.Values | Where-Object { $_ -like 'FALL*' }) { exit 1 }
