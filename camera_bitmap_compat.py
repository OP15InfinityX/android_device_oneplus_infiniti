# SPDX-License-Identifier: Apache-2.0
"""Correct COS17 APS bitmap callbacks for infiniti's NV21 camera buffers.

Only fillRefBitmap and fillFinalBitmap are redirected. Other callers of the
shared converter keep their original behavior. The source descriptor is local
on each callback's stack; its format is restored immediately after conversion.
"""
from hashlib import sha256
from pathlib import Path
import struct

_RX_START = 0x140000
_RX_END = 0x6D0D68
_RX_SHA256 = '0f585612bcefa29826f3f385ca9314c70d60696fc35d1acbb85637b409df3583'
_CALLS = (0x31DF14, 0x313040)  # fillRefBitmap / fillFinalBitmap
_CONVERTER = 0x6C2AE8  # existing yuv420sp2rgbaNeon PLT entry
_STUB = 0x6D0E00  # verified zero padding after RX, clear of the TLS area
# Assembled from camera-bitmap-compat.S; no new DT_NEEDED libraries or symbols.
_WORDS = (
    0xD503233F, 0xA9BE7BFD, 0xF9000BF3, 0x910003FD, 0xAA0003F3,
    0xB9400010, 0x71008E1F, 0x54000141, 0x52800230, 0xB9000010,
    0x97FFC730, 0x52800470, 0xB9000270, 0xF9400BF3, 0xA8C27BFD,
    0xD50323BF, 0xD65F03C0, 0xF9400BF3, 0xA8C27BFD, 0xD50323BF,
    0x17FFC726,
)
_CODE = struct.pack('<' + 'I' * len(_WORDS), *_WORDS)


def _bl(pc, target):
    delta = target - pc
    if delta % 4 or not -(1 << 27) <= delta < (1 << 27):
        raise ValueError('APS bitmap branch is out of range')
    return struct.pack('<I', 0x94000000 | ((delta // 4) & 0x3FFFFFF))


def blob_fixup_camera_bitmap_nv21(ctx, file, file_path, *args, **kwargs):
    path = Path(file_path)
    data = bytearray(path.read_bytes())
    if data[:6] != b'\x7fELF\x02\x01' or struct.unpack_from('<H', data, 18)[0] != 183:
        raise ValueError('Expected an AArch64 ELF library')
    phoff = struct.unpack_from('<Q', data, 32)[0]
    phsize, phcount = struct.unpack_from('<HH', data, 54)
    loads = []
    for i in range(phcount):
        at = phoff + i * phsize
        kind, flags, off, va, _, filesz, memsz, _ = struct.unpack_from('<IIQQQQQQ', data, at)
        if kind == 1:
            loads.append((at, flags, off, va, filesz, memsz))
    rx = [p for p in loads if p[1] == 5 and p[3] == _RX_START]
    if len(rx) != 1:
        raise ValueError('APS executable segment changed')
    at, _, off, va, filesz, memsz = rx[0]

    def offset(address):
        return off + address - va

    end = _STUB + len(_CODE)
    call_offsets = [offset(pc) for pc in _CALLS]
    already = [data[p:p+4] == _bl(pc, _STUB) for p, pc in zip(call_offsets, _CALLS)]
    if any(already):
        if (not all(already) or data[offset(_STUB):offset(end)] != _CODE
                or filesz != end - va or memsz != filesz):
            raise ValueError('Incomplete or unexpected APS bitmap patch')
        return
    if filesz != _RX_END - va or memsz != filesz:
        raise ValueError('APS executable segment size changed')
    if sha256(data[offset(_RX_START):offset(_RX_END)]).hexdigest() != _RX_SHA256:
        raise ValueError('APS code changed; review the bitmap callback ABI before patching')
    next_load = min(p[2] for p in loads if p[2] >= off + filesz)
    if offset(end) > next_load or any(data[offset(_RX_END):offset(end)]):
        raise ValueError('APS bitmap patch padding is not available')
    for p, pc in zip(call_offsets, _CALLS):
        if data[p:p+4] != _bl(pc, _CONVERTER):
            raise ValueError('APS bitmap callback changed')
        data[p:p+4] = _bl(pc, _STUB)
    data[offset(_STUB):offset(end)] = _CODE
    # Include the padding containing the helper in the existing RX mapping.
    struct.pack_into('<QQ', data, at + 32, end - va, end - va)
    path.write_bytes(data)
