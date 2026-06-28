#!/system/bin/sh
# DeviceSpoofLabs Hooks - post-fs-data
# Runs early boot, before most services start.

MODDIR=${0%/*}

# If a user-installed (sideloaded) copy of the companion app exists in
# /data/app, it will shadow the privileged system copy we ship. Log a hint
# so users know to uninstall it for the module to take precedence.
if pm path com.devicespooflab.hooks 2>/dev/null | grep -q '/data/app/'; then
  log -t DeviceSpoofLab "User-installed APK detected at /data/app; system priv-app copy will be ignored until uninstalled."
fi
