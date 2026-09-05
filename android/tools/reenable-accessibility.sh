#!/usr/bin/env bash
# Re-grants Guardia's accessibility service after an install. See reenable-accessibility.ps1 for
# the full explanation; the short version is that installing over an existing build can drop the
# grant, which silently switches off App Lock, and only adb (running as shell, with
# WRITE_SECURE_SETTINGS) can put it back without the user doing it by hand.
#
# The service list is appended to, never overwritten — overwriting it switches off TalkBack and
# every other accessibility service the user depends on.
set -euo pipefail

SERVICE_CLASS="com.guardia.app.core.system.GuardAccessibilityService"
ADB="${ADB:-adb}"
command -v "$ADB" >/dev/null 2>&1 || { echo "adb not found on PATH"; exit 0; }
[ -n "$("$ADB" devices | sed 1d | grep -w device || true)" ] || { echo "No device connected."; exit 0; }

CANDIDATES=("$@")
[ ${#CANDIDATES[@]} -gt 0 ] || CANDIDATES=("com.guardia.app.full" "com.guardia.app")

for id in "${CANDIDATES[@]}"; do
    # `pm list packages` matches on substring, so confirm the exact id came back.
    "$ADB" shell pm list packages "$id" | tr -d '\r' | grep -qx "package:$id" || continue
    service="$id/$SERVICE_CLASS"

    "$ADB" shell appops set "$id" ACCESS_RESTRICTED_SETTINGS allow >/dev/null 2>&1 || true

    current="$("$ADB" shell settings get secure enabled_accessibility_services | tr -d '\r')"
    [ "$current" = "null" ] && current=""

    if printf '%s' "$current" | tr ':' '\n' | grep -qx "$service"; then
        echo "already enabled: $service"
    else
        if [ -n "$current" ]; then new="$current:$service"; else new="$service"; fi
        "$ADB" shell settings put secure enabled_accessibility_services "$new" >/dev/null
        "$ADB" shell settings put secure accessibility_enabled 1 >/dev/null
        echo "enabled: $service"
    fi
done

echo "enabled_accessibility_services = $("$ADB" shell settings get secure enabled_accessibility_services | tr -d '\r')"
