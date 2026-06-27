#pragma once

// Wire protocol shared between the Zygisk module (client, runs inside the
// target process) and the root companion (server, runs as root).
//
// The companion reads the spoof profile and scope list from the module app's
// sandbox (only root can traverse that path) and streams them back to the
// requesting process.
//
// Framing (all integers are host-endian int32; both ends share the same ABI
// because Zygisk connects a 32-bit process to a 32-bit companion and likewise
// for 64-bit):
//
//   client -> companion:  int32 request
//   companion -> client:  int32 count
//                         repeat count times:
//                             int32 key_len,   key bytes   (no NUL)
//                             int32 value_len, value bytes (no NUL)
//
// For REQ_SCOPE the "value" half is empty (value_len = 0); each entry's key is
// one package name.

#pragma once

namespace ds {

// Absolute paths in the module app's sandbox. Only the root companion reads
// these; the target process never touches them directly.
constexpr const char* kProfilePath =
    "/data/data/com.devicespooflab.hooks/files/device_profile.conf";
constexpr const char* kScopePath =
    "/data/data/com.devicespooflab.hooks/files/scope.list";

enum CompanionRequest : int {
    // Return the full key=value spoof profile.
    REQ_PROFILE = 1,
    // Return the scope list (one package name per entry, empty value).
    REQ_SCOPE = 2,
};

}  // namespace ds
