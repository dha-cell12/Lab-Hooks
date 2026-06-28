#include "ds_state.h"

#include <lsplant.hpp>
#include <dobby.h>

#include <android/log.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <elf.h>

#include <cstdint>
#include <cstring>
#include <fstream>
#include <mutex>
#include <string>
#include <unordered_map>

namespace ds {
namespace {

constexpr const char* kLibArtPath = "libart.so";

// ---------------------------------------------------------------------------
// Minimal ELF symbol resolver for libart.so.
//
// LSPlant needs absolute in-process addresses of a handful of ART symbols
// (e.g. art::ArtMethod fields, JNI entry points). LSPlt only does PLT hooking
// by dev/inode/symbol and cannot resolve internal (non-PLT) symbols, so we
// parse libart.so ourselves: read both .dynsym and .symtab from the on-disk
// ELF, then add the in-memory load base taken from /proc/self/maps to produce
// an absolute runtime address.
//
// Supports both ELF64 and ELF32 so the same module works on 64- and 32-bit
// processes.
// ---------------------------------------------------------------------------
class ElfImg {
public:
    explicit ElfImg(const char* libNameHint) {
        if (!FindLoadedModule(libNameHint)) {
            DS_LOGW("ElfImg: %s not found in maps", libNameHint);
            return;
        }
        if (!MapFile()) {
            DS_LOGW("ElfImg: failed to mmap %s", path_.c_str());
            return;
        }
        Parse();
    }

    ~ElfImg() {
        if (map_start_ && map_start_ != MAP_FAILED) {
            munmap(map_start_, map_size_);
        }
    }

    bool Valid() const { return valid_; }

    // Returns the absolute runtime address of `name`, or nullptr if not found.
    // Runtime addr = load_base - min_vaddr + st_value.
    void* GetSymbAddress(const std::string& name) const {
        auto offset = GetSymbOffset(name);
        if (offset == 0) return nullptr;
        return reinterpret_cast<void*>(load_base_ - min_vaddr_ + offset);
    }

    // Returns the absolute runtime address of the first symbol whose name starts
    // with `prefix`, or nullptr if none match.
    void* GetSymbPrefixAddress(const std::string& prefix) const {
        for (const auto& [name, offset] : symbols_) {
            if (offset != 0 && name.compare(0, prefix.size(), prefix) == 0) {
                return reinterpret_cast<void*>(load_base_ - min_vaddr_ + offset);
            }
        }
        return nullptr;
    }

private:
    // Locate the mapped module: capture both its on-disk path and the lowest
    // mapped address (load base) from /proc/self/maps.
    bool FindLoadedModule(const char* hint) {
        std::ifstream maps("/proc/self/maps");
        if (!maps.is_open()) return false;

        std::string line;
        uintptr_t lowest = UINTPTR_MAX;
        std::string found_path;
        while (std::getline(maps, line)) {
            if (line.find(hint) == std::string::npos) continue;
            // Format: start-end perms offset dev inode pathname
            uintptr_t start = 0, end = 0;
            char perms[5] = {0};
            unsigned long file_off = 0;
            char path_buf[512] = {0};
            // %n not used; parse the leading fields then the trailing path.
            if (sscanf(line.c_str(), "%lx-%lx %4s %lx %*s %*s %511[^\n]",
                       &start, &end, perms, &file_off, path_buf) < 5) {
                continue;
            }
            std::string p(path_buf);
            // Trim leading spaces left by the scan set.
            auto pos = p.find_first_not_of(' ');
            if (pos != std::string::npos) p = p.substr(pos);
            if (p.empty()) continue;
            // Only consider real files (skip anon, [stack], etc.).
            if (p[0] != '/') continue;
            if (start < lowest) {
                lowest = start;
                found_path = p;
            }
        }
        if (found_path.empty() || lowest == UINTPTR_MAX) return false;
        path_ = found_path;
        load_base_ = lowest;
        return true;
    }

    bool MapFile() {
        int fd = open(path_.c_str(), O_RDONLY | O_CLOEXEC);
        if (fd < 0) return false;
        struct stat st{};
        if (fstat(fd, &st) != 0 || st.st_size <= 0) {
            close(fd);
            return false;
        }
        map_size_ = static_cast<size_t>(st.st_size);
        map_start_ = mmap(nullptr, map_size_, PROT_READ, MAP_PRIVATE, fd, 0);
        close(fd);
        return map_start_ != MAP_FAILED;
    }

    void Parse() {
        auto* base = reinterpret_cast<uint8_t*>(map_start_);
        if (map_size_ < sizeof(Elf_Ehdr)) return;
        auto* ehdr = reinterpret_cast<Elf_Ehdr*>(base);
        if (memcmp(ehdr->e_ident, ELFMAG, SELFMAG) != 0) return;

        // Symbol st_value is a virtual address in the ELF's own vaddr space.
        // Runtime addr = load_base - min_p_vaddr + st_value, where min_p_vaddr
        // is the vaddr of the first PT_LOAD. Assuming 0 breaks on libraries
        // whose first PT_LOAD is not vaddr 0.
        if (ehdr->e_phoff != 0 && ehdr->e_phnum != 0) {
            auto* phdr = reinterpret_cast<Elf_Phdr*>(base + ehdr->e_phoff);
            uintptr_t lowest = UINTPTR_MAX;
            for (size_t i = 0; i < ehdr->e_phnum; ++i) {
                if (phdr[i].p_type == PT_LOAD && phdr[i].p_vaddr < lowest) {
                    lowest = phdr[i].p_vaddr;
                }
            }
            if (lowest != UINTPTR_MAX) min_vaddr_ = lowest;
        }

        auto* shdr = reinterpret_cast<Elf_Shdr*>(base + ehdr->e_shoff);
        size_t shnum = ehdr->e_shnum;
        if (ehdr->e_shoff == 0 || shnum == 0) return;

        for (size_t i = 0; i < shnum; ++i) {
            const Elf_Shdr& sh = shdr[i];
            if (sh.sh_type == SHT_DYNSYM || sh.sh_type == SHT_SYMTAB) {
                auto* syms = reinterpret_cast<Elf_Sym*>(base + sh.sh_offset);
                size_t count = sh.sh_entsize ? sh.sh_size / sh.sh_entsize : 0;
                const char* strs =
                    reinterpret_cast<const char*>(base + shdr[sh.sh_link].sh_offset);
                for (size_t s = 0; s < count; ++s) {
                    const Elf_Sym& sym = syms[s];
                    if (sym.st_value == 0 || sym.st_name == 0) continue;
                    const char* nm = strs + sym.st_name;
                    // First definition wins; .dynsym is parsed before .symtab
                    // only if it appears first, so keep existing entries.
                    symbols_.emplace(nm, sym.st_value);
                }
            }
        }
        valid_ = !symbols_.empty();
    }

    uintptr_t GetSymbOffset(const std::string& name) const {
        auto it = symbols_.find(name);
        return it == symbols_.end() ? 0 : it->second;
    }

#if defined(__LP64__)
    using Elf_Ehdr = Elf64_Ehdr;
    using Elf_Shdr = Elf64_Shdr;
    using Elf_Sym  = Elf64_Sym;
    using Elf_Phdr = Elf64_Phdr;
#else
    using Elf_Ehdr = Elf32_Ehdr;
    using Elf_Shdr = Elf32_Shdr;
    using Elf_Sym  = Elf32_Sym;
    using Elf_Phdr = Elf32_Phdr;
#endif

    std::string path_;
    uintptr_t load_base_ = 0;
    uintptr_t min_vaddr_ = 0;
    void* map_start_ = nullptr;
    size_t map_size_ = 0;
    bool valid_ = false;
    std::unordered_map<std::string, uintptr_t> symbols_;
};

// ---------------------------------------------------------------------------
// Dobby <-> LSPlant glue
// ---------------------------------------------------------------------------

std::once_flag g_init_flag;
bool g_init_result = false;

// LSPlant calls this to install an inline hook. Returns the original (trampoline)
// function pointer, or nullptr on failure.
void* InlineHooker(void* target, void* replace) {
    void* origin = nullptr;
    if (DobbyHook(target, reinterpret_cast<dobby_dummy_func_t>(replace), reinterpret_cast<dobby_dummy_func_t*>(&origin)) == 0) {
        return origin;
    }
    return nullptr;
}

// LSPlant calls this to remove a previously installed inline hook.
bool InlineUnhooker(void* func) {
    return DobbyDestroy(func) == 0;
}

// Shared ELF image of libart.so, parsed once on first use. Both resolvers below
// query it.
ElfImg& ArtImage() {
    static ElfImg img(kLibArtPath);
    return img;
}

// LSPlant calls this to resolve an absolute address for an ART symbol.
void* ArtSymbolResolver(std::string_view symbol) {
    ElfImg& img = ArtImage();
    if (!img.Valid()) return nullptr;
    return img.GetSymbAddress(std::string(symbol));
}

// LSPlant calls this to resolve the first symbol matching a prefix. Needed for
// ART symbols whose suffix varies across Android versions.
void* ArtSymbolPrefixResolver(std::string_view prefix) {
    ElfImg& img = ArtImage();
    if (!img.Valid()) return nullptr;
    return img.GetSymbPrefixAddress(std::string(prefix));
}

}  // namespace

bool InitLSPlant(JNIEnv* env) {
    std::call_once(g_init_flag, [env]() {
        lsplant::InitInfo info{
            .inline_hooker = InlineHooker,
            .inline_unhooker = InlineUnhooker,
            .art_symbol_resolver = ArtSymbolResolver,
            .art_symbol_prefix_resolver = ArtSymbolPrefixResolver,
        };
        g_init_result = lsplant::Init(env, info);
        if (g_init_result) {
            DS_LOGI("LSPlant initialized");
        } else {
            DS_LOGE("LSPlant init failed");
        }
    });
    return g_init_result;
}

}  // namespace ds
