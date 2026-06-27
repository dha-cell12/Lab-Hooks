#include "ds_state.h"

#include <jni.h>
#include <atomic>
#include <mutex>

namespace ds {
std::unordered_map<std::string, std::string> g_props;
}  // namespace ds

namespace {

std::atomic<bool> g_installed{false};
std::mutex g_install_mutex;

bool ReadJavaMap(JNIEnv* env, jobject java_map,
                 std::unordered_map<std::string, std::string>& out) {
    if (java_map == nullptr) return false;

    jclass mapCls = env->FindClass("java/util/Map");
    if (mapCls == nullptr) return false;
    jmethodID entrySetMID =
        env->GetMethodID(mapCls, "entrySet", "()Ljava/util/Set;");
    if (entrySetMID == nullptr) return false;

    jobject entrySet = env->CallObjectMethod(java_map, entrySetMID);
    if (entrySet == nullptr) return false;

    jclass setCls = env->FindClass("java/util/Set");
    jmethodID iteratorMID =
        env->GetMethodID(setCls, "iterator", "()Ljava/util/Iterator;");
    jobject iterator = env->CallObjectMethod(entrySet, iteratorMID);

    jclass iterCls = env->FindClass("java/util/Iterator");
    jmethodID hasNextMID = env->GetMethodID(iterCls, "hasNext", "()Z");
    jmethodID nextMID =
        env->GetMethodID(iterCls, "next", "()Ljava/lang/Object;");

    jclass entryCls = env->FindClass("java/util/Map$Entry");
    jmethodID getKeyMID =
        env->GetMethodID(entryCls, "getKey", "()Ljava/lang/Object;");
    jmethodID getValueMID =
        env->GetMethodID(entryCls, "getValue", "()Ljava/lang/Object;");

    while (env->CallBooleanMethod(iterator, hasNextMID)) {
        jobject entry = env->CallObjectMethod(iterator, nextMID);
        if (entry == nullptr) continue;

        jstring keyJ = (jstring)env->CallObjectMethod(entry, getKeyMID);
        jstring valJ = (jstring)env->CallObjectMethod(entry, getValueMID);

        if (keyJ != nullptr && valJ != nullptr) {
            const char* keyC = env->GetStringUTFChars(keyJ, nullptr);
            const char* valC = env->GetStringUTFChars(valJ, nullptr);
            if (keyC != nullptr && valC != nullptr) {
                out.emplace(keyC, valC);
            }
            if (keyC) env->ReleaseStringUTFChars(keyJ, keyC);
            if (valC) env->ReleaseStringUTFChars(valJ, valC);
        }
        if (keyJ) env->DeleteLocalRef(keyJ);
        if (valJ) env->DeleteLocalRef(valJ);
        env->DeleteLocalRef(entry);
    }

    env->DeleteLocalRef(iterator);
    env->DeleteLocalRef(entrySet);
    return true;
}

}  // namespace

namespace ds {

bool LookupProperty(const char* name, std::string& out) {
    if (name == nullptr) return false;
    auto it = g_props.find(name);
    if (it == g_props.end()) return false;
    out = it->second;
    return true;
}

bool IsVerboseLoggingEnabled() {
    auto it = g_props.find("debug.verbose");
    if (it == g_props.end()) return false;
    const std::string& v = it->second;
    return v == "1" || v == "true" || v == "TRUE" || v == "yes"
            || v == "YES" || v == "on" || v == "ON";
}

}  // namespace ds

extern "C" JNIEXPORT jint JNICALL
Java_com_devicespooflab_hooks_NativeHooks_nativeInstall(
        JNIEnv* env, jclass /*cls*/, jobject java_map) {
    std::lock_guard<std::mutex> lock(g_install_mutex);
    if (g_installed.exchange(true)) {
        DS_LOGI("nativeInstall: already installed; ignoring");
        return 0;
    }

    std::unordered_map<std::string, std::string> tmp;
    if (!ReadJavaMap(env, java_map, tmp)) {
        DS_LOGE("nativeInstall: ReadJavaMap failed");
        g_installed = false;
        return -1;
    }
    ds::g_props = std::move(tmp);
    DS_LOGI("nativeInstall: parsed %zu entries from Java map",
            (size_t)ds::g_props.size());

    ds::InstallPropertyHooks();
    return (jint)ds::g_props.size();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_devicespooflab_hooks_NativeHooks_nativeQuery(
        JNIEnv* env, jclass /*cls*/, jstring keyJ) {
    if (keyJ == nullptr) return nullptr;
    const char* key = env->GetStringUTFChars(keyJ, nullptr);
    if (key == nullptr) return nullptr;

    std::string out;
    bool found = ds::LookupProperty(key, out);
    env->ReleaseStringUTFChars(keyJ, key);

    if (!found) return nullptr;
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_devicespooflab_hooks_NativeHooks_nativeGetAll(
        JNIEnv* env, jclass /*cls*/) {
    // Build a java.util.HashMap mirroring g_props so the Java layer can read
    // the companion-sourced profile as a single source of truth.
    jclass mapCls = env->FindClass("java/util/HashMap");
    if (mapCls == nullptr) return nullptr;
    jmethodID ctor = env->GetMethodID(mapCls, "<init>", "()V");
    jmethodID putMID = env->GetMethodID(
        mapCls, "put",
        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    if (ctor == nullptr || putMID == nullptr) return nullptr;

    jobject map = env->NewObject(mapCls, ctor);
    if (map == nullptr) return nullptr;

    for (const auto& kv : ds::g_props) {
        jstring k = env->NewStringUTF(kv.first.c_str());
        jstring v = env->NewStringUTF(kv.second.c_str());
        if (k != nullptr && v != nullptr) {
            env->CallObjectMethod(map, putMID, k, v);
        }
        if (k != nullptr) env->DeleteLocalRef(k);
        if (v != nullptr) env->DeleteLocalRef(v);
    }
    return map;
}
