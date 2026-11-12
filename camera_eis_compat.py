# SPDX-License-Identifier: Apache-2.0
"""Adapt COS17 EIS native-handle format reads to source SnapAlloc.

The stock EIS library reads a legacy private_handle_t::format at +0x2c.
For the single-view SnapHandle used here, that is unaligned_width; format
is at +0x34. Passing the width as a pixel format makes EGL buffer imports
fail and produces empty, green time-lapse recordings.

Only the five native-handle format arguments to ShareBuffer are changed.
The complete .text hash guards the reviewed code and permits symbol-version
fixups elsewhere in the ELF. Stabilization and all buffer flags stay intact.
"""

from hashlib import sha256
from pathlib import Path
import struct


_TEXT_START = 0x12DF0
_TEXT_END = 0x5D33C
_TEXT_SHA256 = '2acfa4b4ff764ae7604009f1379e744bb611bc3fde612541b59c129288564ddb'
# File offsets equal virtual addresses within this ELF's first RX segment.
_FORMAT_LOADS = (
    (0x13524, 0xB9402C04),  # ldr w4, [x0, #0x2c]
    (0x2158C, 0xB9402E84),  # ldr w4, [x20, #0x2c]
    (0x21738, 0xB9402F04),  # ldr w4, [x24, #0x2c]
    (0x21C10, 0xB9402C04),  # ldr w4, [x0, #0x2c]
    (0x21D44, 0xB9402C04),  # ldr w4, [x0, #0x2c]
)
# LDR W's immediate is scaled by four: advance the field by eight bytes.
_OFFSET_DELTA = 2 << 10


def blob_fixup_camera_eis_snap_format(ctx, file, file_path, *args, **kwargs):
    path = Path(file_path)
    data = bytearray(path.read_bytes())
    if (len(data) < _TEXT_END or data[:6] != b'\x7fELF\x02\x01'
            or struct.unpack_from('<H', data, 18)[0] != 183):
        raise ValueError('Expected the reviewed AArch64 EIS ELF')

    original_text = bytearray(data[_TEXT_START:_TEXT_END])
    patched = []
    for at, old in _FORMAT_LOADS:
        word = struct.unpack_from('<I', data, at)[0]
        if word not in (old, old + _OFFSET_DELTA):
            raise ValueError(f'EIS format instruction changed at {at:#x}')
        patched.append(word != old)
        struct.pack_into('<I', original_text, at - _TEXT_START, old)

    if sha256(original_text).hexdigest() != _TEXT_SHA256:
        raise ValueError('EIS code changed; review the native-handle ABI first')
    if any(patched):
        if not all(patched):
            raise ValueError('Incomplete EIS native-handle format patch')
        return

    for at, old in _FORMAT_LOADS:
        struct.pack_into('<I', data, at, old + _OFFSET_DELTA)
    path.write_bytes(data)
