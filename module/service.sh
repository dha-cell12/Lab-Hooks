#!/system/bin/sh
# DeviceSpoofLabs Hooks - late_start service
# Runs after boot_completed.

MODDIR=${0%/*}

# Wait for boot to fully settle before doing anything.
until [ "$(getprop sys.boot_completed)" = "1" ]; do
  sleep 2
done

log -t DeviceSpoofLab "Zygisk module v1.2 active. Companion APK: $(pm path com.devicespooflab.hooks 2>/dev/null || echo 'NOT INSTALLED')"
