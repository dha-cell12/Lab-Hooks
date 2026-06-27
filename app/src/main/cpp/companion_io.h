#pragma once

// Shared framed read/write helpers for the companion wire protocol described
// in companion_protocol.h. Used by both the module (client) and the companion
// (server) so the framing logic lives in exactly one place.

#include <cstdint>
#include <string>
#include <unordered_map>
#include <unistd.h>

namespace ds {

// Blocking read of exactly len bytes. Returns false on EOF or error.
inline bool ReadFull(int fd, void* buf, size_t len) {
    auto* p = reinterpret_cast<uint8_t*>(buf);
    size_t got = 0;
    while (got < len) {
        ssize_t n = read(fd, p + got, len - got);
        if (n <= 0) {
            if (n < 0 && (errno == EINTR)) continue;
            return false;
        }
        got += (size_t)n;
    }
    return true;
}

// Blocking write of exactly len bytes. Returns false on error.
inline bool WriteFull(int fd, const void* buf, size_t len) {
    const auto* p = reinterpret_cast<const uint8_t*>(buf);
    size_t put = 0;
    while (put < len) {
        ssize_t n = write(fd, p + put, len - put);
        if (n <= 0) {
            if (n < 0 && (errno == EINTR)) continue;
            return false;
        }
        put += (size_t)n;
    }
    return true;
}

inline bool WriteInt(int fd, int32_t v) {
    return WriteFull(fd, &v, sizeof(v));
}

inline bool ReadInt(int fd, int32_t& v) {
    return ReadFull(fd, &v, sizeof(v));
}

inline bool WriteString(int fd, const std::string& s) {
    int32_t len = (int32_t)s.size();
    if (!WriteInt(fd, len)) return false;
    if (len > 0 && !WriteFull(fd, s.data(), (size_t)len)) return false;
    return true;
}

inline bool ReadString(int fd, std::string& out) {
    int32_t len = 0;
    if (!ReadInt(fd, len)) return false;
    if (len < 0 || len > (1 << 20)) return false;  // 1 MiB sanity cap
    out.resize((size_t)len);
    if (len > 0 && !ReadFull(fd, &out[0], (size_t)len)) return false;
    return true;
}

// Client side: send request, read back count key/value pairs into out.
inline bool RequestPairs(int fd, int32_t request,
                         std::unordered_map<std::string, std::string>& out) {
    if (!WriteInt(fd, request)) return false;
    int32_t count = 0;
    if (!ReadInt(fd, count)) return false;
    if (count < 0 || count > (1 << 20)) return false;
    for (int32_t i = 0; i < count; i++) {
        std::string k, v;
        if (!ReadString(fd, k)) return false;
        if (!ReadString(fd, v)) return false;
        out.emplace(std::move(k), std::move(v));
    }
    return true;
}

}  // namespace ds
