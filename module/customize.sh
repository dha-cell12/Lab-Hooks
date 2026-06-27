#!/system/bin/sh
# Magisk module install-time customization for the DeviceSpoofLab Zygisk module.

# Abort if the installer is not Magisk/KernelSU with Zygisk support.
if [ "$BOOTMODE" != "true" ]; then
  ui_print "! Please install from Magisk Manager / KernelSU Manager"
  abort "! Installation from recovery is not supported"
fi

# Zygisk must be enabled; the module is useless without it.
if [ "$ZYGISK_ENABLED" != "1" ]; then
  ui_print "! Zygisk is not enabled"
  ui_print "! Enable Zygisk in Magisk settings (or use KernelSU + ZygiskNext) and reinstall"
  abort "! Aborting: Zygisk required"
fi

ui_print "- Installing DeviceSpoofLabs-Hooks (Zygisk)"
ui_print "- ABI: $ARCH"

# The zygisk/ directory ships the per-ABI .so files; Magisk loads them itself.
# Just fix up permissions on everything we ship.
set_perm_recursive "$MODPATH" 0 0 0755 0644

ui_print "- Done. Reboot to activate."
ui_print "- Configure target apps and spoof profile from the DeviceSpoofLab app."
