<#
.SYNOPSIS
    Re-grants Guardia's accessibility service after an install, and reports why it was lost.

.DESCRIPTION
    Installing a debug build over an existing one can drop the accessibility grant, which silently
    switches off App Lock and per-app face checks. Android offers no way for an app to restore this
    itself — deliberately — so during development it is restored from the host with adb, which runs
    as shell and therefore holds WRITE_SECURE_SETTINGS.

    The service list is *appended to*, never overwritten. Overwriting it is the obvious one-liner and
    it silently switches off TalkBack and every other accessibility service the user relies on.

    Nothing here works on a device you do not control via adb, and none of it is a substitute for the
    user's own consent on a real install. It is a development convenience only.

.EXAMPLE
    .\tools\reenable-accessibility.ps1
    .\tools\reenable-accessibility.ps1 -AppId com.guardia.app
#>
param(
    [string]$AppId = "",
    [string]$Serial = ""
)

$ErrorActionPreference = "Stop"
$ServiceClass = "com.guardia.app.core.system.GuardAccessibilityService"

function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk")) {
        if ($root) {
            $candidate = Join-Path $root "platform-tools\adb.exe"
            if (Test-Path $candidate) { return $candidate }
        }
    }
    return $null
}

$adb = Find-Adb
if (-not $adb) {
    Write-Host "adb not found. Add platform-tools to PATH or set ANDROID_HOME." -ForegroundColor Yellow
    exit 0
}

$adbArgs = @()
if ($Serial) { $adbArgs = @("-s", $Serial) }
function Adb { & $adb @adbArgs @args }

$devices = (Adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" }
if (-not $devices) {
    Write-Host "No device connected." -ForegroundColor Yellow
    exit 0
}

# Whichever build is actually installed. The flavours differ only by an application-ID suffix.
$candidates = if ($AppId) { @($AppId) } else { @("com.guardia.app.full", "com.guardia.app") }
$installed = @()
foreach ($id in $candidates) {
    $found = Adb shell pm list packages $id
    # `pm list packages` matches on substring, so confirm the exact id came back.
    if ($found -match "package:$([regex]::Escape($id))(\r?\n|$)") { $installed += $id }
}
if (-not $installed) {
    Write-Host "Guardia is not installed on this device ($($candidates -join ', '))." -ForegroundColor Yellow
    exit 0
}

foreach ($id in $installed) {
    $service = "$id/$ServiceClass"

    # Android 13+ blocks accessibility for apps whose installer isn't a trusted store. Clearing the
    # app-op is the same thing the user would do by hand under App info > Allow restricted settings.
    Adb shell appops set $id ACCESS_RESTRICTED_SETTINGS allow 2>$null | Out-Null

    $current = (Adb shell settings get secure enabled_accessibility_services).Trim()
    if ($current -eq "null" -or $current -eq "") { $current = "" }

    if ($current -split ":" -contains $service) {
        Write-Host "already enabled: $service" -ForegroundColor DarkGray
    } else {
        $new = if ($current) { "$current`:$service" } else { $service }
        Adb shell settings put secure enabled_accessibility_services "$new" | Out-Null
        Adb shell settings put secure accessibility_enabled 1 | Out-Null
        Write-Host "enabled: $service" -ForegroundColor Green
    }
}

$final = (Adb shell settings get secure enabled_accessibility_services).Trim()
Write-Host "enabled_accessibility_services = $final" -ForegroundColor DarkGray
