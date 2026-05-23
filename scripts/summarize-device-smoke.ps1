param(
    [string]$ReportRoot = (Join-Path $PSScriptRoot '..\android\app\build\reports\androidTests\device-smoke'),
    [int]$MaxRuns = 12,
    [switch]$OnlyFailures,
    [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Parse-SummaryFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SummaryPath
    )

    $map = @{}
    foreach ($line in Get-Content $SummaryPath) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        $idx = $line.IndexOf('=')
        if ($idx -lt 0) {
            continue
        }
        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        if ($key.Length -eq 0) {
            continue
        }
        $map[$key] = $value
    }
    return $map
}

function To-Bool {
    param([string]$Text)
    return $Text -eq 'True' -or $Text -eq 'true'
}

function Get-LatestStableSmokeRun {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$Runs
    )

    $stableRuns = @(
        $Runs | Where-Object {
            $_.testClasses -match 'DeviceRegressionTest#device_baseline_persistence_and_entrypoint_are_healthy'
        }
    )

    if ($stableRuns.Count -eq 0) {
        return $null
    }

    return $stableRuns[0]
}

function Get-LatestUnitTestStatus {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    $resultsDir = Join-Path $RepoRoot 'android\app\build\test-results\testDebugUnitTest'
    if (-not (Test-Path $resultsDir)) {
        return [PSCustomObject]@{
            exists = $false
            passed = $false
            failures = 0
            errors = 0
            skipped = 0
            timestamp = ''
            reason = 'test-results directory missing'
        }
    }

    $xmlFiles = @(Get-ChildItem -Path $resultsDir -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
    if ($xmlFiles.Count -eq 0) {
        return [PSCustomObject]@{
            exists = $false
            passed = $false
            failures = 0
            errors = 0
            skipped = 0
            timestamp = ''
            reason = 'no TEST-*.xml files'
        }
    }

    $totalFailures = 0
    $totalErrors = 0
    $totalSkipped = 0
    foreach ($file in $xmlFiles) {
        try {
            [xml]$xml = Get-Content $file.FullName
            $suite = $xml.testsuite
            if ($null -ne $suite) {
                $totalFailures += [int]$suite.failures
                $totalErrors += [int]$suite.errors
                $totalSkipped += [int]$suite.skipped
            }
        }
        catch {
            continue
        }
    }

    $latestFile = $xmlFiles | Sort-Object LastWriteTime -Descending | Select-Object -First 1

    return [PSCustomObject]@{
        exists = $true
        passed = ($totalFailures -eq 0 -and $totalErrors -eq 0)
        failures = $totalFailures
        errors = $totalErrors
        skipped = $totalSkipped
        timestamp = $latestFile.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss')
        reason = ''
    }
}

if (-not (Test-Path $ReportRoot)) {
    throw "Report root not found: $ReportRoot"
}

$summaryFiles = Get-ChildItem -Path $ReportRoot -Filter 'summary.txt' -Recurse -File |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First $MaxRuns

if ($summaryFiles.Count -eq 0) {
    Write-Host "No summary files found under $ReportRoot"
    exit 0
}

$allRuns = foreach ($file in $summaryFiles) {
    $runId = Split-Path $file.DirectoryName -Leaf
    $kv = Parse-SummaryFile -SummaryPath $file.FullName

    $testClasses = if ($kv.ContainsKey('testClasses')) { $kv['testClasses'] } else { '' }
    $failureCategory = if ($kv.ContainsKey('failureCategory')) { $kv['failureCategory'] } else { 'unknown' }
    $passed = if ($kv.ContainsKey('passed')) { To-Bool $kv['passed'] } else { $false }
    $failedCount = if ($kv.ContainsKey('failedTestCount')) { [int]$kv['failedTestCount'] } else { 0 }
    $miuiBlock = if ($kv.ContainsKey('miuiLaunchBlockDetected')) { To-Bool $kv['miuiLaunchBlockDetected'] } else { $false }
    $activityTimeout = if ($kv.ContainsKey('activityStartSyncTimeoutDetected')) { To-Bool $kv['activityStartSyncTimeoutDetected'] } else { $false }
    $missingInstr = if ($kv.ContainsKey('instrumentationMissingInfoDetected')) { To-Bool $kv['instrumentationMissingInfoDetected'] } else { $false }
    $timedOut = if ($kv.ContainsKey('instrumentTimedOut')) { To-Bool $kv['instrumentTimedOut'] } else { $false }
    $suggestion = if ($kv.ContainsKey('diagnosticSuggestion')) { $kv['diagnosticSuggestion'] } else { '' }

    [PSCustomObject]@{
        runId = $runId
        time = $file.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss')
        passed = $passed
        failureCategory = $failureCategory
        failedTestCount = $failedCount
        miuiLaunchBlock = $miuiBlock
        activityStartSyncTimeout = $activityTimeout
        instrumentationMissingInfo = $missingInstr
        instrumentTimedOut = $timedOut
        testClasses = $testClasses
        suggestion = $suggestion
        summaryPath = $file.FullName
    }
}

$runs = @($allRuns)
if ($OnlyFailures) {
    $runs = @($runs | Where-Object { -not $_.passed })
}

if ($runs.Count -eq 0) {
    Write-Host 'No matching runs after filters.'
    exit 0
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$latestStableSmoke = Get-LatestStableSmokeRun -Runs $allRuns
$latestUnitTest = Get-LatestUnitTestStatus -RepoRoot $repoRoot

Write-Host 'Latest stable gate status:'
if ($null -eq $latestStableSmoke) {
    Write-Host '  stable smoke: UNKNOWN (no stable run found in selected range)'
}
else {
    $stableStatus = if ($latestStableSmoke.passed) { 'PASS' } else { 'FAIL' }
    Write-Host "  stable smoke: $stableStatus (run=$($latestStableSmoke.runId), category=$($latestStableSmoke.failureCategory))"
}

if (-not $latestUnitTest.exists) {
    Write-Host "  testDebugUnitTest: UNKNOWN ($($latestUnitTest.reason))"
}
else {
    $unitStatus = if ($latestUnitTest.passed) { 'PASS' } else { 'FAIL' }
    Write-Host "  testDebugUnitTest: $unitStatus (failures=$($latestUnitTest.failures), errors=$($latestUnitTest.errors), skipped=$($latestUnitTest.skipped), updated=$($latestUnitTest.timestamp))"
}

if ($null -ne $latestStableSmoke -and $latestUnitTest.exists) {
    $gatePassed = $latestStableSmoke.passed -and $latestUnitTest.passed
    $gateStatus = if ($gatePassed) { 'PASS' } else { 'FAIL' }
    Write-Host "  overall gate (stable smoke + unit test): $gateStatus"
}

Write-Host ''

$passCount = @($runs | Where-Object { $_.passed }).Count
$failCount = @($runs | Where-Object { -not $_.passed }).Count

Write-Host "Analyzed runs: $($runs.Count) (pass=$passCount fail=$failCount)"
Write-Host ''
Write-Host 'Recent runs:'
foreach ($run in $runs) {
    $status = if ($run.passed) { 'PASS' } else { 'FAIL' }
    Write-Host (
        "  [{0}] {1} category={2} failedTests={3} miuiBlock={4} activityTimeout={5} timedOut={6}" -f
        $status,
        $run.runId,
        $run.failureCategory,
        $run.failedTestCount,
        $run.miuiLaunchBlock,
        $run.activityStartSyncTimeout,
        $run.instrumentTimedOut
    )
}
Write-Host ''

Write-Host 'Failure category counts:'
$runs |
    Group-Object failureCategory |
    Sort-Object Count -Descending |
    Select-Object Name, Count |
    Format-Table -AutoSize | Out-String | Write-Host

$miuiBlockedRuns = @($runs | Where-Object { $_.miuiLaunchBlock })
if ($miuiBlockedRuns.Count -gt 0) {
    Write-Host "MIUI blocked runs: $($miuiBlockedRuns.Count)"
}

$missingInstrumentationRuns = @($runs | Where-Object { $_.instrumentationMissingInfo })
if ($missingInstrumentationRuns.Count -gt 0) {
    Write-Host "Instrumentation-info-missing runs: $($missingInstrumentationRuns.Count)"
}

if ($Json) {
    $jsonPath = Join-Path $ReportRoot 'latest-summary-rollup.json'
    $runs | ConvertTo-Json -Depth 6 | Set-Content -Path $jsonPath -Encoding utf8
    Write-Host "JSON written: $jsonPath"
}
