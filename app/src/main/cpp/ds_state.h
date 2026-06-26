#pragma once

#include <jni.h>
#include <mutex>
#include <string>
#include <unordered_map>
#include <android/log.h>

#define DS_LOG_TAG "DeviceSpoofLab-Native"
#define DS_LOGI(...) do { if (::ds::IsVerboseLoggingEnabled()) __android_log_print(ANDROID_LOG_INFO, DS_LOG_TAG, __VA_ARGS__); } while (0)
#define DS_LOGW(...) __android_log_print(ANDROID_LOG_WARN,  DS_LOG_TAG, __VA_ARGS__)
#define DS_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, DS_LOG_TAG, __VA_ARGS__)

namespace ds {

// Global properties map
extern std::unordered_map<std::string, std::string> g_props;

bool LookupProperty(const char* name, std::string& out);

bool IsVerboseLoggingEnabled();

void InstallPropertyHooks();

void InstallSystemHooks(dev_t dev, ino_t inode);

// Re-applies LSPlt on .so files loaded after the initial install.
void InstallDlopenHooks(dev_t dev, ino_t inode);

void LoadPropertiesFromFile(const char* path);

void InstallJavaHooks(JNIEnv* env);

}  // namespace ds
