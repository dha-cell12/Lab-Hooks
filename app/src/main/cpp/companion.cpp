#include "zygisk.hpp"
#include "companion_protocol.h"
#include "companion_io.h"
#include "ds_state.h"

#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

// The companion runs in a root daemon process. It is the only side that can
// traverse the module app's sandbox, so it reads the spoof profile and scope
// list there and streams them back over the per-request socket.
//
// REGISTER_ZYGISK_COMPANION may invoke the handler concurrently on multiple
// threads, so all shared state is mutex-guarded.

namespace {

std::mutex g_cache_mutex;

struct FileCache {
    time_t mtime = 0;
    off_t size = -1;
    std::unordered_map<std::string, std::string> data;
    bool loaded = false;
};

FileCache g_profile_cache;
FileCache g_scope_cache;

// Returns true if the file at path is unchanged versus the cache stamp.
bool CacheFresh(const FileCache& cache, const struct stat& st) {
    return cache.loaded &&
           cache.mtime == st.st_mtime &&
           cache.size == st.st_size;
}

// Parse key=value lines ("#" comments, optional surrounding quotes on value).
// Mirrors ConfigManager.readConfigFile so both sides agree on the format.
void ParseKeyValueFile(int fd,
                       std::unordered_map<std::string, std::string>& out) {
    std::string content;
    char buf[4096];
    ssize_t n;
    while ((n = read(fd, buf, sizeof(buf))) > 0) {
        content.append(buf, (size_t)n);
    }

    size_t pos = 0;
    while (pos < content.size()) {
        size_t eol = content.find('\n', pos);
        if (eol == std::string::npos) eol = content.size();
        std::string line = content.substr(pos, eol - pos);
        pos = eol + 1;

        // trim leading/trailing whitespace
        size_t a = line.find_first_not_of(" \t\r");
        if (a == std::string::npos) continue;
        size_t b = line.find_last_not_of(" \t\r");
        line = line.substr(a, b - a + 1);

        if (line.empty() || line[0] == '#') continue;

        size_t eq = line.find('=');
        if (eq == std::string::npos || eq == 0) continue;
        std::string key = line.substr(0, eq);
        std::string value = line.substr(eq + 1);

        // trim key/value
        size_t kb = key.find_last_not_of(" \t");
        if (kb != std::string::npos) key = key.substr(0, kb + 1);
        size_t va = value.find_first_not_of(" \t");
        value = (va == std::string::npos) ? "" : value.substr(va);

        if (value.size() >= 2 && value.front() == '"' && value.back() == '"') {
            value = value.substr(1, value.size() - 2);
        }
        out.emplace(std::move(key), std::move(value));
    }
}

// Parse one-package-per-line scope list ("#" comments). Stored as keys with
// empty values so it reuses the same wire framing as the profile.
void ParseScopeFile(int fd,
                    std::unordered_map<std::string, std::string>& out) {
    std::string content;
    char buf[4096];
    ssize_t n;
    while ((n = read(fd, buf, sizeof(buf))) > 0) {
        content.append(buf, (size_t)n);
    }

    size_t pos = 0;
    while (pos < content.size()) {
        size_t eol = content.find('\n', pos);
        if (eol == std::string::npos) eol = content.size();
        std::string line = content.substr(pos, eol - pos);
        pos = eol + 1;

        size_t a = line.find_first_not_of(" \t\r");
        if (a == std::string::npos) continue;
        size_t b = line.find_last_not_of(" \t\r");
        line = line.substr(a, b - a + 1);

        if (line.empty() || line[0] == '#') continue;
        out.emplace(std::move(line), std::string());
    }
}

// Load a file into its cache if the on-disk mtime/size changed. Returns a copy
// of the parsed map under lock.
std::unordered_map<std::string, std::string> LoadCached(
        const char* path, FileCache& cache,
        void (*parser)(int, std::unordered_map<std::string, std::string>&)) {
    std::lock_guard<std::mutex> lk(g_cache_mutex);

    struct stat st{};
    if (stat(path, &st) != 0) {
        // File missing: serve last known good cache if any, else empty.
        return cache.loaded ? cache.data
                            : std::unordered_map<std::string, std::string>{};
    }

    if (CacheFresh(cache, st)) {
        return cache.data;
    }

    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        return cache.loaded ? cache.data
                            : std::unordered_map<std::string, std::string>{};
    }

    std::unordered_map<std::string, std::string> parsed;
    parser(fd, parsed);
    close(fd);

    cache.data = std::move(parsed);
    cache.mtime = st.st_mtime;
    cache.size = st.st_size;
    cache.loaded = true;
    return cache.data;
}

bool SendPairs(int client,
               const std::unordered_map<std::string, std::string>& pairs) {
    if (!ds::WriteInt(client, (int32_t)pairs.size())) return false;
    for (const auto& kv : pairs) {
        if (!ds::WriteString(client, kv.first)) return false;
        if (!ds::WriteString(client, kv.second)) return false;
    }
    return true;
}

void CompanionHandler(int client) {
    int32_t request = 0;
    if (!ds::ReadInt(client, request)) return;

    switch (request) {
        case ds::REQ_PROFILE: {
            auto profile = LoadCached(ds::kProfilePath, g_profile_cache,
                                      &ParseKeyValueFile);
            SendPairs(client, profile);
            break;
        }
        case ds::REQ_SCOPE: {
            auto scope = LoadCached(ds::kScopePath, g_scope_cache,
                                    &ParseScopeFile);
            SendPairs(client, scope);
            break;
        }
        default:
            // Unknown request: reply with zero pairs so the client unblocks.
            ds::WriteInt(client, 0);
            break;
    }
}

}  // namespace

REGISTER_ZYGISK_COMPANION(CompanionHandler)
