#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils:../../../hardware/oplus/extract-utils-ext python3
#
# SPDX-FileCopyrightText: 2024 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

from extract_utils.fixups_blob import (
    blob_fixup,
    blob_fixups_user_type,
)
from extract_utils.fixups_lib import (
    lib_fixups,
    lib_fixups_user_type,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)
from extract_utils_oplus import (
    add_generated_thermal_engine_config,
)

namespace_imports = [
    'hardware/oplus',
    'hardware/qcom-caf/sm8850',
    'vendor/oneplus/sm8850-common',
    'vendor/qcom/opensource/commonsys-intf/display',
    'device/oneplus/infiniti',
]


def lib_fixup_vendor_suffix(lib: str, partition: str, *args, **kwargs):
    return f'{lib}_{partition}' if partition == 'vendor' else None


def lib_fixup_stock_charger(lib: str, partition: str, *args, **kwargs):
    # COS17 camera imports the real OEM V11 ABI. Our source AIDL is frozen at
    # V10; its generated development-V11 module is not the OEM V11 interface.
    return f'{lib}_stock'


lib_fixups: lib_fixups_user_type = {
    **lib_fixups,
    'vendor.oplus.hardware.charger-V11-ndk': lib_fixup_stock_charger,
    (
        'libhcsutils',
        'vendor.oplus.hardware.camera.aon-V1-ndk',
        'vendor.oplus.hardware.camera_rfi-V3-ndk',
        'vendor.oplus.hardware.cammidasservice-V1-ndk',
        'vendor.oplus.hardware.sendextcamcmd-V2-ndk',
    ): lib_fixup_vendor_suffix,
}

blob_fixups: blob_fixups_user_type = {
    # Keep COS17's OEM Charger V11 ABI separate from source-generated V11.
    (
        'odm/lib64/camera/components/com.oplus.node.mvgsat.so',
        'odm/lib64/camera.oemlayer.healthmonitor.so',
        'odm/lib64/camera.oemlayer.logger.so',
        'odm/lib64/camera.oemlayer.uilayer.so',
        'odm/lib64/com.oplus.mcx.linearmapper.so',
    ): blob_fixup()
        .replace_needed('vendor.oplus.hardware.charger-V11-ndk.so',
                        'vendor.oplus.hardware.charger-V11-ndk_stock.so'),
    'odm/etc/init/init.camera_process.rc': blob_fixup()
        .regex_replace('    delete_recursion', '    #delete_recursion'),
    'odm/vendor/etc/wifi/WCNSS_qcom_cfg.ini': blob_fixup()
        # Allow framework scans in ULL mode; also handle the original stock INI.
        .regex_replace(
            r'(?m)^([ \t]*wlm_latency_flags_ultralow[ \t]*=[ \t]*)0x180[dD]8[23]([ \t]*)$',
            r'\g<1>0x180c82\g<2>',
        ),
    (
        'odm/etc/libnfc-mtp-SN220.conf_24831',
        'odm/etc/libnfc-mtp-SN220.conf_24863',
    ): blob_fixup()
        .regex_replace('(NXPLOG_.*_LOGLEVEL)=0x03', '\\1=0x02')
        .regex_replace('NFC_DEBUG_ENABLED=1', 'NFC_DEBUG_ENABLED=0'),
    (
        'odm/lib64/libAlgoProcess.so',
        'odm/lib64/libEIS.so',
        'odm/lib64/libEISLive.so',
        'odm/lib64/libFaceBeautyJni.so',
        'odm/lib64/libFaceDistortionCorrection.so',
        'odm/lib64/libOPAlgoCamAiBeautyFaceRetouchCn.so',
        'odm/lib64/libOPAlgoCamAiUnifySkin.so',
        'odm/lib64/libOPAlgoCamFaceBeautyCap.so',
        'odm/lib64/libaiboost_te.so',
        'odm/lib64/libAncHumanSegFigureFusion.so',
    ): blob_fixup()
        .clear_symbol_version('AHardwareBuffer_acquire')
        .clear_symbol_version('AHardwareBuffer_allocate')
        .clear_symbol_version('AHardwareBuffer_describe')
        .clear_symbol_version('AHardwareBuffer_lock')
        .clear_symbol_version('AHardwareBuffer_lockPlanes')
        .clear_symbol_version('AHardwareBuffer_release')
        .clear_symbol_version('AHardwareBuffer_unlock'),
    'odm/lib64/libAlgoProcess.so': blob_fixup()
        .replace_needed('android.hardware.graphics.common-V5-ndk.so', 'android.hardware.graphics.common-V7-ndk.so'),
    'odm/lib64/libBasicTonePhoto.so': blob_fixup()
        .binary_regex_replace(
            b'vec4\\(dstYuv\\.r, dstYuv\\.b, dstYuv\\.g, 1\\.0\\)',
            b'vec4(dstYuv.r, dstYuv.g, dstYuv.b, 1.0)',
        ),
    'odm/lib64/liboprec_audrec.so': blob_fixup()
        .replace_needed('libstdc++.so', 'libstdc++_vendor.so'),
    'vendor/etc/libnfc-nci.conf': blob_fixup()
        .regex_replace('NFC_DEBUG_ENABLED=1', 'NFC_DEBUG_ENABLED=0'),
    'vendor/lib64/camera/components/com.qti.node.dewarp.so': blob_fixup()
        .replace_needed('android.hardware.graphics.allocator-V1-ndk.so', 'android.hardware.graphics.allocator-V2-ndk.so'),
    'vendor/lib64/vendor.qti.hardware.camera.offlinecamera-service-impl.so': blob_fixup()
        .replace_needed('android.hardware.graphics.allocator-V1-ndk.so', 'android.hardware.graphics.allocator-V2-ndk.so')
        # COS17 reads a legacy native-handle size at +0x1c. With source SnapAlloc
        # that is the flags word (512), truncating offline camera metadata.
        # Read SnapHandleProperties::size at +0x50; preserve the following FD load.
        .binary_regex_replace(
            b'\x1a\x1d\x40\xb9\x00\x0d\x40\xb9',
            b'\x1a\x51\x40\xb9\x00\x0d\x40\xb9',
        ),
    (
        'vendor/lib64/camera/components/com.qti.node.fd.so',
        'vendor/lib64/hw/camera.qcom.core.so',
        'vendor/lib64/libcamxdumpinforecorder.so',
    ): blob_fixup()
        .replace_needed('libtinyxml2.so', 'libtinyxml2-v36.so'),
    'odm/lib64/libsharebuffer_impl.so': blob_fixup()
        .replace_needed('libutils.so', 'libutils-stock.so')
        .replace_needed('libui.so', 'libui-stock.so'),
    # COS17 imports __cxa_init_primary_exception without declaring the C++
    # runtime. AOSP's libc++ already exports it; make the dependency explicit.
    'odm/lib64/libsaveshaderbin.so': blob_fixup()
        .add_needed('libc++.so'),
    'vendor/lib64/libui-stock.so': blob_fixup()
        # No dynamic imports from graphics.common-V6; libgralloctypes uses V7
        # on Android 17. Preserve the verified A17 compatibility fix.
        .remove_needed('android.hardware.graphics.common-V6-ndk.so'),
}  # fmt: skip

module = ExtractUtilsModule(
    'infiniti',
    'oneplus',
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
    namespace_imports=namespace_imports,
    add_firmware_proprietary_file=True,
)

add_generated_thermal_engine_config(module)

if __name__ == '__main__':
    utils = ExtractUtils.device_with_common(
        module, 'sm8850-common', module.vendor
    )
    utils.run()
