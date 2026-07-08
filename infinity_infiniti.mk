#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# Inherit from infiniti device
$(call inherit-product, device/oneplus/infiniti/device.mk)

# Inherit some common Lineage stuff.
INFINITY_MAINTAINER := koaaN
TARGET_HAS_UDFPS := true

$(call inherit-product, vendor/infinity/config/common_full_phone.mk)

PRODUCT_NAME := lineage_infiniti
PRODUCT_DEVICE := infiniti
PRODUCT_MANUFACTURER := OnePlus
PRODUCT_BRAND := OnePlus
PRODUCT_MODEL := CPH2747

PRODUCT_GMS_CLIENTID_BASE := android-oneplus

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="qssi_64-user 16 BP2A.250605.015 1779937534743 release-keys" \
    BuildFingerprint=OnePlus/CPH2747/OP611FL1:16/BP2A.250605.015/B.R4T3.202606012318:user/release-keys \
    DeviceName=OP611FL1 \
    DeviceProduct=CPH2747 \
    SystemDevice=OP611FL1 \
    SystemName=CPH2747
