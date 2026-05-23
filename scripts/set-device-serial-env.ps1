param(
    [string]$DeviceSerial,
    [ValidateSet('User', 'Machine', 'Process')]
    [string]$Scope = 'User',
    [switch]$AlsoSetAndroidSerial,
    [switch]$PrintOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-AdbPath {
    $candidates = @()

    if ($env:ANDROID_SDK_ROOT) {
        $candidates += (Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe')
    }
    if ($env:ANDROID_HOME) {
        $candidates += (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe')
    }

    $candidates += 'C:\Users\lonqu\AppData\Local\Android\Sdk\platform-tools\adb.exe'

    foreach ($candidate in $candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path $candidate)) {
            return $candidate
        }
    }

    $adbCmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -ne $adbCmd) {
        return $adbCmd.Source
    }

    throw 'adb not found. Install Android platform-tools or set ANDROID_SDK_ROOT/ANDROID_HOME.'
}

function Resolve-DeviceSerialFromAdb {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AdbPath
    )

    & $AdbPath start-server | Out-Null
    $lines = & $AdbPath devices

    $onlineSerials = @(
        $lines |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ -match '^(.+?)\s+device$' -and $_ -notmatch '^List of devices attached$' } |
            ForEach-Object {
                if ($_ -match '^(.+?)\s+device$') {
                    $Matches[1].Trim()
                }
            }
    )

    if ($onlineSerials.Length -eq 0) {
        throw 'No online device detected. Connect one device and enable USB debugging.'
    }

    if ($onlineSerials.Length -gt 1) {
        throw "Multiple online devices detected. Please pass -DeviceSerial explicitly. Online devices: $($onlineSerials -join ', ')"
    }

    return $onlineSerials[0]
}

$serial = $DeviceSerial
if ([string]::IsNullOrWhiteSpace($serial)) {
    $adbPath = Resolve-AdbPath
    $serial = Resolve-DeviceSerialFromAdb -AdbPath $adbPath
}

if ([string]::IsNullOrWhiteSpace($serial)) {
    throw 'Device serial is empty.'
}

$serial = $serial.Trim()

if ($PrintOnly) {
    Write-Host "AIREPLAYMATE_DEVICE_SERIAL=$serial"
    if ($AlsoSetAndroidSerial) {
        Write-Host "ANDROID_SERIAL=$serial"
    }
    exit 0
}

[Environment]::SetEnvironmentVariable('AIREPLAYMATE_DEVICE_SERIAL', $serial, $Scope)
$env:AIREPLAYMATE_DEVICE_SERIAL = $serial

if ($AlsoSetAndroidSerial) {
    [Environment]::SetEnvironmentVariable('ANDROID_SERIAL', $serial, $Scope)
    $env:ANDROID_SERIAL = $serial
}

Write-Host 'Environment variables updated.'
Write-Host "  scope: $Scope"
Write-Host "  AIREPLAYMATE_DEVICE_SERIAL=$serial"
if ($AlsoSetAndroidSerial) {
    Write-Host "  ANDROID_SERIAL=$serial"
}
Write-Host 'For new terminals to pick up User/Machine scope changes, open a new terminal session.'
