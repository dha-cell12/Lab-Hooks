#pragma once

#include <mutex>
#include <string>
#include <unordered_map>
#include <android/log.h>

#define DS_LOG_TAG "DeviceSpoofLab-Native"
#define DS_LOGI(...) do { if (::ds::IsVerboseLoggingEnabled()) __android_log_print(ANDROID_LOG_INFO, DS_LOG_TAG, __VA_ARGS__); } while (0)
#define DS_LOGW(...) __android_log_print(ANDROID_LOG_WARN,  DS_LOG_TAG, __VA_ARGS__)
#define DS_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, DS_LOG_TAG, __VA_ARGS__)

namespace ds {

// Populated once from Java; read-only after install.
extern std::unordered_map<std::string, std::string> g_props;

bool LookupProperty(const char* name, std::string& out);

bool IsVerboseLoggingEnabled();

void InstallPropertyHooks();

void InstallSystemHooks(dev_t dev, ino_t inode);

// Re-applies LSPlt on .so files loaded after the initial install.
void InstallDlopenHooks(dev_t dev, ino_t inode);

// Initializes LSPlant with Dobby as the inline-hook backend. Must be called
// once per process with a valid JNIEnv before any Java method hook. Returns
// true on success; safe to call multiple times (subsequent calls are no-ops).
bool InitLSPlant(JNIEnv* env);

}  // namespace ds
