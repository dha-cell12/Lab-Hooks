#include "ds_state.h"
#include <fstream>
#include <sstream>
#include <android/log.h>
#include <sys/stat.h>

#define LOG_TAG "DeviceSpoofLab-Config"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace ds {

// Simple key=value parser for now
void LoadPropertiesFromFile(const char* path) {
    std::ifstream infile(path);
    if (!infile.is_open()) {
        return;
    }

    std::string line;
    while (std::getline(infile, line)) {
        if (line.empty() || line[0] == '#') continue;
        size_t pos = line.find('=');
        if (pos != std::string::npos) {
            std::string key = line.substr(0, pos);
            std::string value = line.substr(pos + 1);
            g_props[key] = value;
        }
    }
    LOGI("Loaded %zu properties from %s", g_props.size(), path);
}

} // namespace ds
