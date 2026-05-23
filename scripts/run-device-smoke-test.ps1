param(
    [string[]]$TestClasses = @(
        'com.lonquanzj.aireplaymate.DeviceRegressionTest#device_baseline_persistence_and_entrypoint_are_healthy',
        'com.lonquanzj.aireplaymate.DeviceRegressionTest#prompt_builder_respects_policy_sanitization_and_mode_rules',
        'com.lonquanzj.aireplaymate.DeviceRegressionTest#local_fallback_generator_produces_ranked_persona_aware_candidates',
        'com.lonquanzj.aireplaymate.DeviceRegressionTest#llm_debug_store_tracks_state_transitions_and_hints',
        'com.lonquanzj.aireplaymate.DeviceRegressionTest#overlay_diagnostics_store_tracks_flow_and_failure_state',
        'com.lonquanzj.aireplaymate.DeviceRegressionTest#diagnostic_log_store_persists_sanitizes_and_deduplicates_entries',
        'com.lonquanzj.aireplaymate.DeviceRegressionTest#ocr_debug_store_tracks_attempt_result_and_message_previews'
    ),
    [string]$TestClass,
    [ValidateSet('activity-launch-probe', 'ui-entry-probe', 'overlay-long-press-probe')]
    [string]$DiagnosticPreset,
    [string]$AppPackage = 'com.lonquanzj.aireplaymate',
    [string]$TestPackage = 'com.lonquanzj.aireplaymate.test',
    [string]$DeviceSerial,
    [int]$InstrumentTimeoutSeconds = 120,
    [switch]$SkipInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function ConvertTo-ProcessArgumentString {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Args
    )

    # Start-Process can split args containing spaces; quote them explicitly.
    return ($Args | ForEach-Object {
            if ($_ -match '[\s"]') {
                '"' + ($_ -replace '"', '\\"') + '"'
            }
            else {
                $_
            }
        }) -join ' '
}

function Get-SdkDir {
    if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
        return $env:ANDROID_SDK_ROOT
    }

    if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
        return $env:ANDROID_HOME
    }

    $repoLocalProperties = Join-Path $PSScriptRoot '..\local.properties'
    if (Test-Path $repoLocalProperties) {
        $raw = Get-Content $repoLocalProperties -Raw
        $match = [regex]::Match($raw, 'sdk\.dir\s*=\s*(.+)')
        if ($match.Success) {
            $sdk = $match.Groups[1].Value.Trim()
            $sdk = $sdk -replace '\\\\', '\'
            $sdk = $sdk -replace '\\:', ':'
            if (Test-Path $sdk) {
                return $sdk
            }
        }
    }

    $fallback = 'C:\Users\lonqu\AppData\Local\Android\Sdk'
    if (Test-Path $fallback) {
        return $fallback
    }

    throw 'Android SDK path not found. Set ANDROID_SDK_ROOT or configure sdk.dir in local.properties.'
}

function Get-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path $env:JAVA_HOME)) {
        return $env:JAVA_HOME
    }

    $jbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path $jbr) {
        return $jbr
    }

    $temurin = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
    if (Test-Path $temurin) {
        return $temurin
    }

    throw 'JDK not found. Set JAVA_HOME or install Android Studio JBR.'
}

function Read-TextFileWithRetry {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [int]$RetryCount = 5,
        [int]$DelayMs = 300
    )

    if (-not (Test-Path $Path)) {
        return ''
    }

    for ($i = 1; $i -le $RetryCount; $i++) {
        try {
            return Get-Content $Path -Raw
        }
        catch {
            if ($i -eq $RetryCount) {
                return ''
            }
            Start-Sleep -Milliseconds $DelayMs
        }
    }

    return ''
}

function Parse-InstrumentationTestResults {
    param(
        [Parameter(Mandatory = $true)]
        [string]$InstrumentText
    )

    $results = New-Object System.Collections.Generic.List[object]
    if ([string]::IsNullOrWhiteSpace($InstrumentText)) {
        return $results
    }

    $currentClass = ''
    $currentTest = ''

    foreach ($line in ($InstrumentText -split "`r?`n")) {
        if ($line -match '^INSTRUMENTATION_STATUS:\s+class=(.+)$') {
            $currentClass = $Matches[1].Trim()
            continue
        }

        if ($line -match '^INSTRUMENTATION_STATUS:\s+test=(.+)$') {
            $currentTest = $Matches[1].Trim()
            continue
        }

        if ($line -match '^INSTRUMENTATION_STATUS_CODE:\s+(-?\d+)$') {
            $statusCode = [int]$Matches[1]
            if ([string]::IsNullOrWhiteSpace($currentClass) -or [string]::IsNullOrWhiteSpace($currentTest)) {
                continue
            }

            $status = switch ($statusCode) {
                0 { 'passed' }
                -2 { 'failed' }
                default { $null }
            }

            if ($null -eq $status) {
                continue
            }

            $fullName = "$currentClass#$currentTest"
            $existing = $results | Where-Object { $_.name -eq $fullName } | Select-Object -First 1
            if ($null -ne $existing) {
                $existing.status = $status
            }
            else {
                $results.Add([PSCustomObject]@{
                    name = $fullName
                    status = $status
                }) | Out-Null
            }
        }
    }

    return $results
}

function Extract-MiuiLaunchEvidence {
    param(
        [string]$LogcatText,
        [int]$MaxLines = 5
    )

    $evidence = New-Object System.Collections.Generic.List[string]
    if ([string]::IsNullOrWhiteSpace($LogcatText)) {
        return $evidence
    }

    $patterns = @(
        'Abort background activity starts',
        'result code=102',
        'Permission Denied Activity KeyguardLocked',
        'MIUILOG- Permission Denied Activity'
    )

    foreach ($line in ($LogcatText -split "`r?`n")) {
        foreach ($pattern in $patterns) {
            if ($line -match $pattern) {
                $trimmed = $line.Trim()
                if (-not [string]::IsNullOrWhiteSpace($trimmed) -and -not $evidence.Contains($trimmed)) {
                    $evidence.Add($trimmed) | Out-Null
                    if ($evidence.Count -ge $MaxLines) {
                        return $evidence
                    }
                }
                break
            }
        }
    }

    return $evidence
}

function Ensure-DeviceAwakeAndUnlocked {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AdbPath,
        [string[]]$AdbTargetArgs = @()
    )

    Write-Host "[3.5/5] Wake device and dismiss keyguard (best effort)"

    $commands = @(
        @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP'),
        @('shell', 'wm', 'dismiss-keyguard'),
        @('shell', 'input', 'swipe', '600', '2200', '600', '900', '200'),
        @('shell', 'input', 'keyevent', 'KEYCODE_MENU'),
        @('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    )

    $nativeCommandPreference = Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue
    $hasNativeCommandPreference = $null -ne $nativeCommandPreference
    if ($hasNativeCommandPreference) {
        $oldNativeCommandPreference = [bool]$nativeCommandPreference.Value
        $Global:PSNativeCommandUseErrorActionPreference = $false
    }

    try {
        foreach ($commandArgs in $commands) {
            & $AdbPath @AdbTargetArgs @commandArgs 1>$null 2>$null
        }
    }
    finally {
        if ($hasNativeCommandPreference) {
            $Global:PSNativeCommandUseErrorActionPreference = $oldNativeCommandPreference
        }
    }

    Start-Sleep -Seconds 1
}

function Resolve-AppDebugApkPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot,
        [Parameter(Mandatory = $true)]
        [string]$AdbPath,
        [string[]]$AdbTargetArgs = @()
    )

    $debugApkDir = Join-Path $RepoRoot 'android\app\build\outputs\apk\debug'
    $defaultApk = Join-Path $debugApkDir 'app-debug.apk'
    if (Test-Path $defaultApk) {
        return $defaultApk
    }

    if (-not (Test-Path $debugApkDir)) {
        throw "Debug APK output directory not found at $debugApkDir"
    }

    $splitCandidates = @(
        Get-ChildItem -Path $debugApkDir -Filter 'app-*-debug.apk' -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch 'androidTest' } |
            Sort-Object Name
    )

    if ($splitCandidates.Count -eq 0) {
        throw "No debug APK found in $debugApkDir"
    }

    if ($splitCandidates.Count -eq 1) {
        return $splitCandidates[0].FullName
    }

    $deviceAbi = ''
    try {
        $abiResult = & $AdbPath @AdbTargetArgs shell getprop ro.product.cpu.abi 2>$null
        if ($null -ne $abiResult) {
            $deviceAbi = ($abiResult | Out-String).Trim()
        }
    }
    catch {
    }

    if (-not [string]::IsNullOrWhiteSpace($deviceAbi)) {
        $matchedByAbi = $splitCandidates | Where-Object { $_.Name -like "*-$deviceAbi-debug.apk" } | Select-Object -First 1
        if ($null -ne $matchedByAbi) {
            return $matchedByAbi.FullName
        }
    }

    return $splitCandidates[0].FullName
}

function Install-AppAndTestApk {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot,
        [Parameter(Mandatory = $true)]
        [string]$GradlewPath,
        [Parameter(Mandatory = $true)]
        [string]$AdbPath,
        [Parameter(Mandatory = $true)]
        [string[]]$AdbTargetArgs,
        [Parameter(Mandatory = $true)]
        [string]$InstallLogPath,
        [Parameter(Mandatory = $true)]
        [string]$TestPackageName
    )

    & $GradlewPath ':app:assembleDebug' ':app:assembleDebugAndroidTest' '--console=plain' 2>&1 | Tee-Object -FilePath $InstallLogPath -Append | Out-Host
    $gradleExitCode = $LASTEXITCODE
    if ($gradleExitCode -ne 0) {
        throw "Gradle assemble tasks failed, exit code=$gradleExitCode"
    }

    $appApk = Resolve-AppDebugApkPath -RepoRoot $RepoRoot -AdbPath $AdbPath -AdbTargetArgs $AdbTargetArgs
    $testApk = Join-Path $RepoRoot 'android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    if (-not (Test-Path $appApk)) {
        throw "App APK not found at $appApk"
    }
    if (-not (Test-Path $testApk)) {
        throw "Test APK not found at $testApk"
    }

    "Install app APK via adb: $appApk" | Add-Content $InstallLogPath
    & $AdbPath @AdbTargetArgs install -r -d $appApk 2>&1 | Tee-Object -FilePath $InstallLogPath -Append | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to install $appApk"
    }

    Write-Host "  Uninstall stale test package if present"
    "Uninstall stale test package if present: $TestPackageName" | Add-Content $InstallLogPath
    & $AdbPath @AdbTargetArgs uninstall $TestPackageName 2>&1 | Tee-Object -FilePath $InstallLogPath -Append | Out-Host

    "Install test APK via adb: $testApk" | Add-Content $InstallLogPath
    & $AdbPath @AdbTargetArgs install -r -t -d $testApk 2>&1 | Tee-Object -FilePath $InstallLogPath -Append | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to install $testApk"
    }
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $repoRoot

$sdkDir = Get-SdkDir
$javaHome = Get-JavaHome
$adb = Join-Path $sdkDir 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) {
    throw "adb not found at $adb"
}

$gradlew = Join-Path $repoRoot 'gradlew.bat'
if (-not (Test-Path $gradlew)) {
    throw "gradlew.bat not found at $gradlew"
}

$env:JAVA_HOME = $javaHome
$env:Path = "$($javaHome)\bin;$($sdkDir)\platform-tools;$env:Path"

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outDir = Join-Path $repoRoot "android\app\build\reports\androidTests\device-smoke\$timestamp"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

$installLog = Join-Path $outDir 'install-output.txt'
$instrumentLog = Join-Path $outDir 'instrument-output.txt'
$instrumentErrLog = Join-Path $outDir 'instrument-error.txt'
$logcatLog = Join-Path $outDir 'logcat.txt'
$summaryLog = Join-Path $outDir 'summary.txt'

$installExit = -1
$instrumentExit = -1
$instrumentTimedOut = $false
$timedOutAppPid = ''
$passed = $false
$crashed = $false
$failed = $false
$scriptError = ''
$miuiLaunchBlockDetected = $false
$miuiLaunchBlockSummary = ''
$miuiEvidenceLines = @()
$activityStartSyncTimeoutDetected = $false
$instrumentationMissingInfoDetected = $false
$diagnosticSuggestion = ''
$installUserRestricted = $false
$installUserRestrictedSummary = ''
$installDeleteInternalError = $false
$installDeleteInternalErrorSummary = ''
$failureCategory = 'unknown'
$failureSummary = ''
# Normalize test selectors from either array input or comma-joined text.
$selectedTestsRaw = if ([string]::IsNullOrWhiteSpace($TestClass)) {
    @($TestClasses)
}
else {
    @($TestClass)
}

$selectedTests = @(
    $selectedTestsRaw |
        ForEach-Object {
            if ($null -eq $_) { return }
            foreach ($chunk in ($_.ToString() -split ',')) {
                $normalized = $chunk.Trim()
                if (-not [string]::IsNullOrWhiteSpace($normalized)) {
                    $normalized
                }
            }
        }
)

if (-not [string]::IsNullOrWhiteSpace($DiagnosticPreset)) {
    $selectedTests = switch ($DiagnosticPreset) {
        'activity-launch-probe' {
            @('com.lonquanzj.aireplaymate.MainActivityLaunchProbeTest#mainActivity_launches_with_activityScenario')
        }
        'ui-entry-probe' {
            @('com.lonquanzj.aireplaymate.MainActivityUiEntryTest#mainScreen_renders_key_entry_points')
        }
        'overlay-long-press-probe' {
            @('com.lonquanzj.aireplaymate.overlay.OverlayLongPressStyleMenuProbeTest#longPress_onFloatingBubble_triggersStyleMenuCallback')
        }
        default {
            throw "Unsupported diagnostic preset: $DiagnosticPreset"
        }
    }
}

if ($selectedTests.Length -eq 0) {
    throw 'At least one test must be provided via -TestClasses or -TestClass.'
}

$joinedTestClasses = ($selectedTests -join ',')
$testResults = @()
$passedTestNames = @()
$failedTestNames = @()
$adbTargetArgs = @()

try {
    Write-Host "[1/5] Prepare environment"
    Write-Host "  repo: $repoRoot"
    Write-Host "  sdk : $sdkDir"
    Write-Host "  java: $javaHome"
    Write-Host "  out : $outDir"
    Write-Host "  tests: $joinedTestClasses"
    if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
        Write-Host "  device: $DeviceSerial"
    }

    Write-Host "[2/5] Clean stale connected test artifacts"
    Remove-Item -Recurse -Force 'android/app/build/outputs/androidTest-results/connected' -ErrorAction SilentlyContinue

    Write-Host "[3/5] Ensure adb + connected device"
    if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
        if (-not [string]::IsNullOrWhiteSpace($env:AIREPLAYMATE_DEVICE_SERIAL)) {
            $DeviceSerial = $env:AIREPLAYMATE_DEVICE_SERIAL.Trim()
            Write-Host "  using serial from env AIREPLAYMATE_DEVICE_SERIAL: $DeviceSerial"
        }
        elseif (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SERIAL)) {
            $DeviceSerial = $env:ANDROID_SERIAL.Trim()
            Write-Host "  using serial from env ANDROID_SERIAL: $DeviceSerial"
        }
    }

    & $adb start-server | Out-Null
    $devices = & $adb devices
    $devices | Tee-Object -FilePath $summaryLog | Out-Host
    $onlineDevices = @(
        $devices |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ -match '^[^\s].*\tdevice$' }
    )
    if ($onlineDevices.Count -eq 0) {
        throw 'No online device detected. Connect a real device and enable USB debugging.'
    }

    $onlineSerials = @(
        $onlineDevices |
            ForEach-Object { ($_ -split "`t")[0].Trim() }
    )

    if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
        if ($onlineSerials.Count -gt 1) {
            $normalizedSerialGroups = @(
                $onlineSerials |
                    Group-Object { $_ -replace ' \(\d+\)(?=\._adb-tls-connect\._tcp$)', '' }
            )
            if ($normalizedSerialGroups.Count -eq 1) {
                $DeviceSerial = @(
                    $onlineSerials |
                        Sort-Object { if ($_ -match ' \(\d+\)(?=\._adb-tls-connect\._tcp$)') { 1 } else { 0 } }, { $_ }
                )[0]
                Write-Warning "ADB reported duplicate mDNS entries for the same device. Auto-selected '$DeviceSerial'."
            }
            else {
                throw "Multiple online devices detected. Re-run with -DeviceSerial '<serial>'. Online devices: $($onlineSerials -join ', ')"
            }
        }
        else {
            $DeviceSerial = $onlineSerials[0]
        }
    }
    elseif ($onlineSerials -notcontains $DeviceSerial) {
        throw "Requested device '$DeviceSerial' is not online. Online devices: $($onlineSerials -join ', ')"
    }

    $adbTargetArgs = @('-s', $DeviceSerial)
    $env:ANDROID_SERIAL = $DeviceSerial
    "selectedDevice=$DeviceSerial" | Add-Content $summaryLog
    Write-Host "  selected device: $DeviceSerial"

    Ensure-DeviceAwakeAndUnlocked -AdbPath $adb -AdbTargetArgs $adbTargetArgs

    Write-Host "[4/5] Install app + test APK"
    & $adb @adbTargetArgs logcat -c
    if ($SkipInstall) {
        Write-Host "  Skip install because -SkipInstall is set"
        "Skip install because -SkipInstall is set" | Set-Content $installLog
        $installExit = 0
    }
    else {
        Install-AppAndTestApk `
            -RepoRoot $repoRoot `
            -GradlewPath $gradlew `
            -AdbPath $adb `
            -AdbTargetArgs $adbTargetArgs `
            -InstallLogPath $installLog `
            -TestPackageName $TestPackage
        $installExit = 0
    }

    Write-Host "[5/5] Run instrumentation test selection"
    if ($joinedTestClasses -match 'MainActivityUiEntryTest|MainActivityLaunchProbeTest') {
        Write-Warning 'Diagnostic-only Activity tests selected. These probes launch MainActivity under instrumentation and can be blocked by OEM background-activity policies on MIUI devices.'
        Write-Warning 'Do not use these Activity probes as merge gate; prefer default no-Activity DeviceRegressionTest suite for stable pass/fail signal.'
    }
    $runner = "$TestPackage/androidx.test.runner.AndroidJUnitRunner"
    & $adb @adbTargetArgs shell am force-stop $AppPackage | Out-Null
    & $adb @adbTargetArgs shell am force-stop $TestPackage | Out-Null
    if (Test-Path $instrumentLog) { Remove-Item -Force $instrumentLog }
    if (Test-Path $instrumentErrLog) { Remove-Item -Force $instrumentErrLog }
    $instrumentArgs = @($adbTargetArgs + @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $joinedTestClasses, $runner))
    $instrumentProcess = Start-Process -FilePath $adb `
        -ArgumentList (ConvertTo-ProcessArgumentString -Args $instrumentArgs) `
        -RedirectStandardOutput $instrumentLog `
        -RedirectStandardError $instrumentErrLog `
        -NoNewWindow `
        -PassThru

    $instrumentWaitTimedOut = $false
    try {
        Wait-Process -Id $instrumentProcess.Id -Timeout $InstrumentTimeoutSeconds -ErrorAction Stop
    }
    catch {
        if ($_.Exception -is [System.TimeoutException]) {
            $instrumentWaitTimedOut = $true
        }
        else {
            throw
        }
    }

    if ($instrumentWaitTimedOut) {
        $instrumentTimedOut = $true
        $pidofResult = & $adb @adbTargetArgs shell pidof $AppPackage 2>$null
        $timedOutAppPid = if ($null -eq $pidofResult) { '' } else { ($pidofResult | Out-String).Trim() }
        if (-not [string]::IsNullOrWhiteSpace($timedOutAppPid)) {
            # Dump Java thread stacks to logcat before terminating instrumentation.
            $hasNativeCommandPreference = $false
            $oldNativeCommandPreference = $false
            $nativeCommandPreference = Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue
            if ($null -ne $nativeCommandPreference) {
                $hasNativeCommandPreference = $true
                $oldNativeCommandPreference = [bool]$nativeCommandPreference.Value
                $Global:PSNativeCommandUseErrorActionPreference = $false
            }
            try {
                & $adb @adbTargetArgs shell "kill -3 $timedOutAppPid" 1>$null 2>$null
            }
            catch {
            }
            finally {
                if ($hasNativeCommandPreference) {
                    $Global:PSNativeCommandUseErrorActionPreference = $oldNativeCommandPreference
                }
            }
            Start-Sleep -Seconds 2
        }
        Stop-Process -Id $instrumentProcess.Id -Force -ErrorAction SilentlyContinue
    }

    if (-not $instrumentTimedOut) {
        $instrumentProcess.WaitForExit()
        $instrumentProcess.Refresh()
    }

    $instrumentExit = if ($instrumentTimedOut) {
        124
    }
    elseif ($null -eq $instrumentProcess.ExitCode) {
        0
    }
    else {
        $instrumentProcess.ExitCode
    }

    if (Test-Path $instrumentErrLog) {
        Get-Content $instrumentErrLog | Add-Content $instrumentLog
    }

    if (Test-Path $instrumentLog) {
        Get-Content $instrumentLog | Out-Host
    }

    $instrumentTextSnapshot = Read-TextFileWithRetry -Path $instrumentLog
    $missingInstrumentationInfo = $instrumentTextSnapshot -match 'Unable to find instrumentation info for: ComponentInfo' -or $instrumentTextSnapshot -match 'INSTRUMENTATION_FAILED: .+AndroidJUnitRunner'
    if ($SkipInstall -and $missingInstrumentationInfo) {
        Write-Warning 'Detected missing instrumentation info in SkipInstall mode. Reinstalling APKs and retrying instrumentation once.'
        "SkipInstall retry: reinstall app/test APK because instrumentation info is missing" | Add-Content $installLog

        Install-AppAndTestApk `
            -RepoRoot $repoRoot `
            -GradlewPath $gradlew `
            -AdbPath $adb `
            -AdbTargetArgs $adbTargetArgs `
            -InstallLogPath $installLog `
            -TestPackageName $TestPackage

        & $adb @adbTargetArgs shell am force-stop $AppPackage | Out-Null
        & $adb @adbTargetArgs shell am force-stop $TestPackage | Out-Null
        if (Test-Path $instrumentLog) { Remove-Item -Force $instrumentLog }
        if (Test-Path $instrumentErrLog) { Remove-Item -Force $instrumentErrLog }
        $instrumentArgs = @($adbTargetArgs + @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $joinedTestClasses, $runner))
        $instrumentProcess = Start-Process -FilePath $adb `
            -ArgumentList (ConvertTo-ProcessArgumentString -Args $instrumentArgs) `
            -RedirectStandardOutput $instrumentLog `
            -RedirectStandardError $instrumentErrLog `
            -NoNewWindow `
            -PassThru

        $instrumentWaitTimedOut = $false
        try {
            Wait-Process -Id $instrumentProcess.Id -Timeout $InstrumentTimeoutSeconds -ErrorAction Stop
        }
        catch {
            if ($_.Exception -is [System.TimeoutException]) {
                $instrumentWaitTimedOut = $true
            }
            else {
                throw
            }
        }

        if ($instrumentWaitTimedOut) {
            $instrumentTimedOut = $true
            $pidofResult = & $adb @adbTargetArgs shell pidof $AppPackage 2>$null
            $timedOutAppPid = if ($null -eq $pidofResult) { '' } else { ($pidofResult | Out-String).Trim() }
            if (-not [string]::IsNullOrWhiteSpace($timedOutAppPid)) {
                $hasNativeCommandPreference = $false
                $oldNativeCommandPreference = $false
                $nativeCommandPreference = Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue
                if ($null -ne $nativeCommandPreference) {
                    $hasNativeCommandPreference = $true
                    $oldNativeCommandPreference = [bool]$nativeCommandPreference.Value
                    $Global:PSNativeCommandUseErrorActionPreference = $false
                }
                try {
                    & $adb @adbTargetArgs shell "kill -3 $timedOutAppPid" 1>$null 2>$null
                }
                catch {
                }
                finally {
                    if ($hasNativeCommandPreference) {
                        $Global:PSNativeCommandUseErrorActionPreference = $oldNativeCommandPreference
                    }
                }
                Start-Sleep -Seconds 2
            }
            Stop-Process -Id $instrumentProcess.Id -Force -ErrorAction SilentlyContinue
        }

        if (-not $instrumentTimedOut) {
            $instrumentProcess.WaitForExit()
            $instrumentProcess.Refresh()
        }

        $instrumentExit = if ($instrumentTimedOut) {
            124
        }
        elseif ($null -eq $instrumentProcess.ExitCode) {
            0
        }
        else {
            $instrumentProcess.ExitCode
        }

        if (Test-Path $instrumentErrLog) {
            Get-Content $instrumentErrLog | Add-Content $instrumentLog
        }

        if (Test-Path $instrumentLog) {
            Get-Content $instrumentLog | Out-Host
        }
    }
}
catch {
    $scriptError = $_.Exception.Message
    Write-Host "SMOKE TEST ERROR: $scriptError"
}
finally {
    if ($adbTargetArgs.Count -gt 0 -and -not (Test-Path $logcatLog)) {
        try {
            & $adb @adbTargetArgs logcat -d > $logcatLog
        }
        catch {
        }
    }

    $instrumentText = Read-TextFileWithRetry -Path $instrumentLog
    $installText = Read-TextFileWithRetry -Path $installLog
    $logcatText = Read-TextFileWithRetry -Path $logcatLog
    $testResults = @()
    if (-not [string]::IsNullOrWhiteSpace($instrumentText)) {
        $parsedResults = Parse-InstrumentationTestResults -InstrumentText $instrumentText
        if ($null -ne $parsedResults) {
            $testResults = @($parsedResults)
        }
    }
    $passedTestNames = @($testResults | Where-Object { $_.status -eq 'passed' } | ForEach-Object { $_.name })
    $failedTestNames = @($testResults | Where-Object { $_.status -eq 'failed' } | ForEach-Object { $_.name })
    $passed = $instrumentText -match 'INSTRUMENTATION_CODE:\s*-1'
    $crashed = $instrumentText -match 'shortMsg=Process crashed|Process crashed'
    $failed = $instrumentText -match 'INSTRUMENTATION_STATUS_CODE:\s*-2|FAILURES!!!|INSTRUMENTATION_FAILED'
    $installUserRestricted = $installText -match 'INSTALL_FAILED_USER_RESTRICTED|Install canceled by user'
    if ($installUserRestricted) {
        $installUserRestrictedSummary = 'Detected install blocked by device/user policy (INSTALL_FAILED_USER_RESTRICTED). Keep the phone unlocked, allow USB install prompts, and relax MIUI security restrictions before rerun.'
    }
    $installDeleteInternalError = $installText -match 'DELETE_FAILED_INTERNAL_ERROR'
    if ($installDeleteInternalError) {
        $installDeleteInternalErrorSummary = 'Detected test package uninstall internal error (DELETE_FAILED_INTERNAL_ERROR). Usually stale package state; retry or manually uninstall the test package on device.'
    }
    $activityStartSyncTimeoutDetected = $instrumentText -match 'Instrumentation\.startActivitySync' -and $instrumentText -match 'TestTimedOutException'
    $instrumentationMissingInfoDetected = $instrumentText -match 'Unable to find instrumentation info for: ComponentInfo'
    $miuiLaunchBlockDetected = $logcatText -match 'Abort background activity starts|result code=102|Permission Denied Activity KeyguardLocked|MIUILOG- Permission Denied Activity'
    if ($miuiLaunchBlockDetected) {
        $miuiEvidenceLines = @(Extract-MiuiLaunchEvidence -LogcatText $logcatText -MaxLines 5)
    }
    if ($miuiLaunchBlockDetected) {
        $miuiLaunchBlockSummary = 'Detected MIUI/OEM background-activity-start blocking in logcat. Activity-launching instrumentation tests may fail before MainActivity.onCreate. Prefer DeviceRegressionTest as the default baseline on this device.'
    }

    if ($passed -and -not $crashed -and -not $failed -and $instrumentExit -eq 0 -and [string]::IsNullOrWhiteSpace($scriptError)) {
        $failureCategory = 'passed'
        $failureSummary = 'Smoke test finished successfully.'
    }
    elseif ($installUserRestricted) {
        $failureCategory = 'install_user_restricted'
        $failureSummary = $installUserRestrictedSummary
    }
    elseif ($installDeleteInternalError) {
        $failureCategory = 'install_delete_internal_error'
        $failureSummary = $installDeleteInternalErrorSummary
    }
    elseif ($miuiLaunchBlockDetected) {
        $failureCategory = 'miui_launch_block'
        $failureSummary = $miuiLaunchBlockSummary
        $diagnosticSuggestion = 'Use stable gate (`device smoke: stable suite`) for merge decision; run Activity probes only for diagnostics after manually opening app/allowing foreground launch on device.'
    }
    elseif ($crashed) {
        $failureCategory = 'process_crashed'
        $failureSummary = 'Instrumentation reported process crash.'
    }
    elseif ($instrumentTimedOut) {
        $failureCategory = 'instrumentation_timeout'
        $failureSummary = 'Instrumentation process timed out before completion.'
    }
    elseif ($failed) {
        $failureCategory = 'test_failed'
        $failureSummary = 'One or more instrumentation tests failed.'
    }
    elseif (-not [string]::IsNullOrWhiteSpace($scriptError)) {
        $failureCategory = 'script_error'
        $failureSummary = $scriptError
    }
    else {
        $failureCategory = 'unknown'
        $failureSummary = 'Unable to classify failure from current logs.'
    }

    "" | Add-Content $summaryLog
    "testClasses=$joinedTestClasses" | Add-Content $summaryLog
    "appPackage=$AppPackage" | Add-Content $summaryLog
    "testPackage=$TestPackage" | Add-Content $summaryLog
    "installLog=$installLog" | Add-Content $summaryLog
    "instrumentLog=$instrumentLog" | Add-Content $summaryLog
    "logcatLog=$logcatLog" | Add-Content $summaryLog
    "installExit=$installExit" | Add-Content $summaryLog
    "instrumentExit=$instrumentExit" | Add-Content $summaryLog
    "instrumentTimedOut=$instrumentTimedOut" | Add-Content $summaryLog
    "timedOutAppPid=$timedOutAppPid" | Add-Content $summaryLog
    "passed=$passed" | Add-Content $summaryLog
    "crashed=$crashed" | Add-Content $summaryLog
    "failed=$failed" | Add-Content $summaryLog
    "testResultCount=$($testResults.Count)" | Add-Content $summaryLog
    for ($index = 0; $index -lt $testResults.Count; $index++) {
        $result = $testResults[$index]
        "testResult.$($index + 1).name=$($result.name)" | Add-Content $summaryLog
        "testResult.$($index + 1).status=$($result.status)" | Add-Content $summaryLog
    }
    "passedTestCount=$($passedTestNames.Count)" | Add-Content $summaryLog
    "failedTestCount=$($failedTestNames.Count)" | Add-Content $summaryLog
    "passedTests=$($passedTestNames -join '|')" | Add-Content $summaryLog
    "failedTests=$($failedTestNames -join '|')" | Add-Content $summaryLog
    "installUserRestricted=$installUserRestricted" | Add-Content $summaryLog
    "installUserRestrictedSummary=$installUserRestrictedSummary" | Add-Content $summaryLog
    "installDeleteInternalError=$installDeleteInternalError" | Add-Content $summaryLog
    "installDeleteInternalErrorSummary=$installDeleteInternalErrorSummary" | Add-Content $summaryLog
    "miuiLaunchBlockDetected=$miuiLaunchBlockDetected" | Add-Content $summaryLog
    "miuiLaunchBlockSummary=$miuiLaunchBlockSummary" | Add-Content $summaryLog
    "miuiEvidenceCount=$($miuiEvidenceLines.Count)" | Add-Content $summaryLog
    for ($index = 0; $index -lt $miuiEvidenceLines.Count; $index++) {
        "miuiEvidence.$($index + 1)=$($miuiEvidenceLines[$index])" | Add-Content $summaryLog
    }
    "activityStartSyncTimeoutDetected=$activityStartSyncTimeoutDetected" | Add-Content $summaryLog
    "instrumentationMissingInfoDetected=$instrumentationMissingInfoDetected" | Add-Content $summaryLog
    "diagnosticSuggestion=$diagnosticSuggestion" | Add-Content $summaryLog
    "failureCategory=$failureCategory" | Add-Content $summaryLog
    "failureSummary=$failureSummary" | Add-Content $summaryLog
    "scriptError=$scriptError" | Add-Content $summaryLog

    # Keep summary machine-readable for CLI/CI consumers.
    try {
        $summaryText = Read-TextFileWithRetry -Path $summaryLog
        $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
        [System.IO.File]::WriteAllText($summaryLog, $summaryText, $utf8NoBom)
    }
    catch {
    }
}

if ($installUserRestricted) {
    Write-Warning $installUserRestrictedSummary
}

if ($installDeleteInternalError) {
    Write-Warning $installDeleteInternalErrorSummary
}

if ($miuiLaunchBlockDetected) {
    Write-Warning $miuiLaunchBlockSummary
    if ($miuiEvidenceLines.Count -gt 0) {
        Write-Host 'MIUI evidence lines:'
        foreach ($line in $miuiEvidenceLines) {
            Write-Host "  - $line"
        }
    }
    if ($activityStartSyncTimeoutDetected) {
        Write-Host 'Diagnostic signal: Activity launch timeout detected at Instrumentation.startActivitySync.'
    }
    if ($instrumentationMissingInfoDetected) {
        Write-Host 'Diagnostic signal: instrumentation info missing was detected in this run.'
    }
}

if ($testResults.Count -gt 0) {
    Write-Host 'Per-test results:'
    foreach ($result in $testResults) {
        Write-Host "  [$($result.status)] $($result.name)"
    }
    Write-Host "Passed tests: $($passedTestNames.Count)"
    Write-Host "Failed tests: $($failedTestNames.Count)"
}

Write-Host "Failure category: $failureCategory"

if ($passed -and -not $crashed -and -not $failed -and $instrumentExit -eq 0 -and [string]::IsNullOrWhiteSpace($scriptError)) {
    Write-Host "SMOKE TEST PASSED"
    Write-Host "Summary: $summaryLog"
    exit 0
}

Write-Host "SMOKE TEST FAILED"
Write-Host "Summary: $summaryLog"
exit 1
