// SPDX-License-Identifier: Apache-2.0
// Reference for the format-only helper embedded by camera_bitmap_compat.py.
// Assemble at VA 0x6d0e00 with original_convert=0x6c2ae8 for COS17 17.0.0.102IN.
// The original converter and every other call site remain unchanged.
.text
.p2align 2
.global aps_bitmap_nv21
.type aps_bitmap_nv21,%function
aps_bitmap_nv21:
    paciasp
    stp x29, x30, [sp, #-32]!
    str x19, [sp, #16]
    mov x29, sp
    mov x19, x0
    ldr w16, [x0]
    cmp w16, #35
    b.ne unchanged
    mov w16, #17
    str w16, [x0]
    bl original_convert
    mov w16, #35
    str w16, [x19]
    ldr x19, [sp, #16]
    ldp x29, x30, [sp], #32
    autiasp
    ret
unchanged:
    ldr x19, [sp, #16]
    ldp x29, x30, [sp], #32
    autiasp
    b original_convert
.size aps_bitmap_nv21,.-aps_bitmap_nv21
