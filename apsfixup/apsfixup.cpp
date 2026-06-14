// libapsfixup.so — permanent native fix for the OnePlus(dodge) APS turbo soft/green/crash bug.
//
// Root cause: the port's gralloc reports a wrong plane layout for the 12.5MP P010 capture
// output, so the byte-identical ArcSoft/Algo blobs compute a garbage chroma plane pointer
// (align_up(luma,0) = 4GB), a zero chroma stride, and run the P010 LSB->MSB conversion with an
// uninitialized source-stride. This lib re-applies, at runtime, the exact corrections proven
// with Frida (op_chroma_repair.js):
//   (1) ARC_Turbo_RAW_Process: output struct plane[1] (UV) ptr = luma + Ysize (=2/3 of buffer);
//       plane[1] pitch = plane[0] (Y) pitch.
//   (2) p010LSB2MSBNeon: set w5 so the loop length (w4*w5*1.5) == buffer size  (full Y+UV, no
//       overrun).
//
// IMPORTANT: this version uses ONLY GOT redirection (writing data pointers) -- NO inline code
// patching -- so it needs NEITHER execmem NOR execmod (execmod is neverallow'd for app domains).
//   * p010LSB2MSBNeon is called via a PLT JUMP_SLOT in libAlgoProcess  -> overwrite that GOT slot.
//   * ARC_Turbo_RAW_Process is resolved by libAlgoInterface via dlsym() -> overwrite the dlsym
//     JUMP_SLOT in libAlgoInterface so our wrapper is what gets stored in the engine struct.
//
// Loaded into com.oplus.camera as a DT_NEEDED of /odm/lib64/libAlgoProcess.so. Offsets are
// pinned to the infiniti (OnePlus 15, SM8850, OOS 16.0.8.300) blobs, re-anchored via
// readelf JUMP_SLOT offsets (host static analysis; no runtime/frida discovery needed):
//   libAlgoProcess.so    BuildId 2217d555..  p010LSB2MSBNeon @ +0x4fc25c, its GOT slot @ +0x689ba8
//   libAlgoInterface.so  BuildId f76a8818..  dlsym GOT slot @ +0x1bb67c8
// A runtime NT_GNU_BUILD_ID check (build_id_matches) gates application — fail-safe across an OOS OTA
// (if the blob changes, the offsets are stale, so we refuse to redirect rather than crash).
//
#include <android/log.h>
#include <dlfcn.h>
#include <elf.h>
#include <inttypes.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

#define TAG "apsfixup"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// infiniti (SM8850, OOS 16.0.8.300) — re-anchored from the .300 blobs via readelf R_AARCH64_JUMP_SLOT offsets.
// v1.3 bump from .201: only the p010 FUNC vaddr moved (0x4fc094->0x4fc25c); both GOT slots are stable; both BuildIds changed.
static const uintptr_t P010_FUNC_OFF = 0x4fc25c;   // p010LSB2MSBNeon (_ZN22APSFormatConverterNeon15p010LSB2MSBNeonEPtS0_jjjj)
static const uintptr_t P010_GOT_OFF  = 0x689ba8;   // its JUMP_SLOT GOT entry in libAlgoProcess (unchanged .201->.300)
static const uintptr_t DLSYM_GOT_OFF = 0x1bb67c8;  // dlsym@LIBC JUMP_SLOT GOT entry in libAlgoInterface (unchanged .201->.300)
// BuildId guard: only apply if the loaded blob matches what these offsets were anchored against.
static const char* EXPECT_ALGOPROC_BUILDID  = "2217d555bacb9e8f9c2a81a609ca9f47";
static const char* EXPECT_ALGOIFACE_BUILDID = "f76a88188a00589db385183c025443fb";

static inline bool is_buf(uint64_t v)     { uint32_t hi=(uint32_t)(v>>32); return hi>=0x70 && hi<=0x7f && (uint32_t)v >= 0x100000u; }
static inline bool is_garbage(uint64_t v) { uint32_t hi=(uint32_t)(v>>32); return hi>=0x70 && hi<=0x7f && (uint32_t)v <  0x100000u; }

static bool range_of(uint64_t addr, uint64_t* out_base, uint64_t* out_size) {
    FILE* f = fopen("/proc/self/maps", "re");
    if (!f) return false;
    char line[512]; bool found = false;
    while (fgets(line, sizeof(line), f)) {
        uint64_t lo, hi;
        if (sscanf(line, "%" SCNx64 "-%" SCNx64, &lo, &hi) != 2) continue;
        if (addr >= lo && addr < hi) { *out_base = lo; *out_size = hi - lo; found = true; break; }
    }
    fclose(f);
    return found;
}
static bool module_base(const char* name, uint64_t* out_base) {
    FILE* f = fopen("/proc/self/maps", "re");
    if (!f) return false;
    char line[512]; uint64_t best = 0;
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, name)) { uint64_t lo;
            if (sscanf(line, "%" SCNx64, &lo) == 1) if (best == 0 || lo < best) best = lo; }
    }
    fclose(f);
    if (best) { *out_base = best; return true; }
    return false;
}

// Overwrite a relro GOT slot (data, not code) -> no execmem/execmod.
static bool got_redirect(uint64_t slot, void* newval, void** old) {
    void** got = (void**)slot;
    uintptr_t page = slot & ~(uintptr_t)0xfff;
    if (mprotect((void*)page, 0x1000, PROT_READ | PROT_WRITE) != 0) { LOGW("mprotect GOT %p failed", (void*)slot); return false; }
    if (old) *old = *got;
    *got = newval;
    mprotect((void*)page, 0x1000, PROT_READ);   // restore relro (BIND_NOW: nothing else writes it)
    return true;
}

// Read NT_GNU_BUILD_ID of the ELF mapped at `base` and compare its hex prefix to want_hex.
// Fail-safe: any parse failure / mismatch -> false -> the caller refuses to apply the fix.
static bool build_id_matches(uint64_t base, const char* want_hex) {
    const Elf64_Ehdr* eh = (const Elf64_Ehdr*)base;
    if (memcmp(eh->e_ident, ELFMAG, SELFMAG) != 0) return false;
    const Elf64_Phdr* ph = (const Elf64_Phdr*)(base + eh->e_phoff);
    for (int i = 0; i < eh->e_phnum; i++) {
        if (ph[i].p_type != PT_NOTE) continue;
        const uint8_t* p   = (const uint8_t*)(base + ph[i].p_vaddr);
        const uint8_t* end = p + ph[i].p_memsz;
        while (p + sizeof(Elf64_Nhdr) <= end) {
            const Elf64_Nhdr* n = (const Elf64_Nhdr*)p;
            const char* nm      = (const char*)(p + sizeof(Elf64_Nhdr));
            const uint8_t* desc = (const uint8_t*)nm + ((n->n_namesz + 3) & ~3u);
            if (n->n_type == NT_GNU_BUILD_ID && n->n_namesz == 4 && memcmp(nm, "GNU", 4) == 0) {
                char hex[48]; size_t k = 0;
                for (uint32_t j = 0; j < n->n_descsz && k + 2 < sizeof(hex); j++)
                    k += (size_t)snprintf(hex + k, sizeof(hex) - k, "%02x", desc[j]);
                hex[k < sizeof(hex) ? k : sizeof(hex) - 1] = 0;
                return strncmp(hex, want_hex, strlen(want_hex)) == 0;
            }
            p = desc + ((n->n_descsz + 3) & ~3u);
        }
    }
    return false;
}

// ---- (1) chroma struct repair (called from our ARC wrapper) ----
static void repair_struct(void* p) {
    if (!p) return;
    uint64_t mb, ms; if (!range_of((uint64_t)p, &mb, &ms)) return;
    uint8_t* b = (uint8_t*)p;
    for (int off = 0; off + 16 <= 0x80; off += 8) {
        uint64_t luma = *(uint64_t*)(b + off), chroma = *(uint64_t*)(b + off + 8);
        if (is_buf(luma) && is_garbage(chroma)) {
            uint64_t lb, ls; if (!range_of(luma, &lb, &ls)) continue;
            uint64_t avail = (lb + ls) - luma;
            uint64_t ysize = (avail * 2 / 3) & ~0xfffULL;        // Y-plane size (=0x1800000), page aligned
            *(uint64_t*)(b + off + 8) = luma + ysize;            // plane[1] (UV) ptr
            if (off == 0x40) {                                   // chroma pitch[1]@+0x64 = Y pitch[0]@+0x60
                uint32_t yp = *(uint32_t*)(b + 0x60);
                if (yp > 0 && *(uint32_t*)(b + 0x64) == 0) *(uint32_t*)(b + 0x64) = yp;
            }
            LOGI("chroma fix: luma=%p -> %p (ysize=0x%llx)", (void*)luma, (void*)(luma + ysize), (unsigned long long)ysize);
        }
    }
}
// ARC_Turbo_RAW_Process takes x0-x7 PLUS ~7 stack args, so we CANNOT use a C wrapper (it would
// drop the stack args). Instead: a naked asm trampoline that repairs the 3 output structs
// (x1/x2/x3) then tail-branches to the real function with the FULL register+stack frame intact.
extern "C" __attribute__((visibility("hidden"))) void* aps_real_arc = nullptr;
extern "C" __attribute__((visibility("hidden"))) void aps_repair_structs(void* a1, void* a2, void* a3) {
    repair_struct(a1); repair_struct(a2); repair_struct(a3);
}
extern "C" void wrap_arc();   // defined in asm below; what we hand back from dlsym
__asm__(
"    .text\n"
"    .balign 4\n"
"    .global wrap_arc\n"
"    .type wrap_arc, %function\n"
"wrap_arc:\n"
"    stp x29, x30, [sp, #-0x60]!\n"   // our frame; sp moves DOWN, caller's stack args stay above
"    mov x29, sp\n"
"    stp x0, x1, [sp, #0x10]\n"       // save arg regs x0..x7
"    stp x2, x3, [sp, #0x20]\n"
"    stp x4, x5, [sp, #0x30]\n"
"    stp x6, x7, [sp, #0x40]\n"
"    ldr x0, [sp, #0x18]\n"           // aps_repair_structs(orig x1, orig x2, orig x3)
"    ldr x1, [sp, #0x20]\n"
"    ldr x2, [sp, #0x28]\n"
"    bl  aps_repair_structs\n"
"    ldp x0, x1, [sp, #0x10]\n"       // restore arg regs
"    ldp x2, x3, [sp, #0x20]\n"
"    ldp x4, x5, [sp, #0x30]\n"
"    ldp x6, x7, [sp, #0x40]\n"
"    ldp x29, x30, [sp], #0x60\n"     // pop frame -> sp back to entry (stack args in place), x30 restored
"    adrp x16, aps_real_arc\n"
"    add  x16, x16, #:lo12:aps_real_arc\n"
"    ldr  x16, [x16]\n"
"    br   x16\n"                      // tail-call real ARC; it returns straight to the caller
);

// ---- dlsym interposer in libAlgoInterface: swap ARC_Turbo_RAW_Process for our wrapper ----
typedef void* (*dlsym_t)(void*, const char*);
static dlsym_t g_real_dlsym = nullptr;
static void* wrap_dlsym(void* handle, const char* symbol) {
    void* res = g_real_dlsym(handle, symbol);
    if (symbol && res && strcmp(symbol, "ARC_Turbo_RAW_Process") == 0) {
        aps_real_arc = res;
        LOGI("interposing ARC_Turbo_RAW_Process (real=%p)", res);
        return (void*)wrap_arc;
    }
    return res;
}

// ---- (2) p010LSB2MSBNeon length fix ----
typedef void (*p010_t)(uint16_t*, uint16_t*, uint32_t, uint32_t, uint32_t, uint32_t);
static p010_t g_real_p010 = nullptr;
static void wrap_p010(uint16_t* dst, uint16_t* src, uint32_t w2, uint32_t w3, uint32_t w4, uint32_t w5) {
    if (w4 > 0) {
        uint64_t sb, ss;
        if (range_of((uint64_t)src, &sb, &ss)) {
            uint64_t avail  = (sb + ss) - (uint64_t)src;
            uint32_t new_w5 = (uint32_t)((avail * 2 / 3) / w4);   // w4*w5*1.5 == buffer
            if (new_w5 > 0 && new_w5 != w5) {
                LOGI("p010 fix: avail=0x%llx w4=%u w5 %u->%u", (unsigned long long)avail, w4, w5, new_w5);
                w5 = new_w5;
            }
        }
    }
    g_real_p010(dst, src, w2, w3, w4, w5);
}

// ---- install ----
static bool g_p010_done = false, g_dlsym_done = false;
static void try_install() {
    uint64_t base;
    if (!g_p010_done && module_base("libAlgoProcess.so", &base)) {
        if (!build_id_matches(base, EXPECT_ALGOPROC_BUILDID)) {
            LOGW("libAlgoProcess BuildId != %s -- NOT applying p010 fix (blob changed; re-anchor offsets)", EXPECT_ALGOPROC_BUILDID);
            g_p010_done = true;   // fail-safe: stop retrying, never redirect a mismatched blob
        } else {
            void* old = nullptr;
            if (got_redirect(base + P010_GOT_OFF, (void*)wrap_p010, &old)) {
                g_real_p010 = (p010_t)old;
                void* expect = (void*)(base + P010_FUNC_OFF);
                if (old != expect) LOGW("GOT[p010]=%p expected %p (blob drift?)", old, expect);
                LOGI("GOT-hooked p010 (real=%p)", old);
                g_p010_done = true;
            }
        }
    }
    if (!g_dlsym_done && module_base("libAlgoInterface.so", &base)) {
        if (!build_id_matches(base, EXPECT_ALGOIFACE_BUILDID)) {
            LOGW("libAlgoInterface BuildId != %s -- NOT applying dlsym hook (blob changed)", EXPECT_ALGOIFACE_BUILDID);
            g_dlsym_done = true;
        } else {
            void* old = nullptr;
            if (got_redirect(base + DLSYM_GOT_OFF, (void*)wrap_dlsym, &old)) {
                g_real_dlsym = (dlsym_t)old;
                LOGI("GOT-hooked dlsym in libAlgoInterface (real=%p)", old);
                g_dlsym_done = true;
            }
        }
    }
}
static void* poller(void*) {
    // 25ms cadence; dlsym(ARC) happens at the first turbo capture (seconds after libAlgoInterface
    // loads), so this hooks well before it. ~10 min total budget.
    for (int i = 0; i < 24000 && !(g_p010_done && g_dlsym_done); i++) { try_install(); usleep(25 * 1000); }
    if (!(g_p010_done && g_dlsym_done)) LOGW("install incomplete: p010=%d dlsym=%d", g_p010_done, g_dlsym_done);
    return nullptr;
}

__attribute__((constructor))
static void apsfixup_init() {
    LOGI("libapsfixup loaded (pid %d)", getpid());
    try_install();                       // libAlgoProcess is loaded with us; libAlgoInterface may be too
    if (!(g_p010_done && g_dlsym_done)) {
        pthread_t t; pthread_create(&t, nullptr, poller, nullptr); pthread_detach(t);
    }
}
