# Plus Key custom ROM integration guide

The OnePlus 15 in this tree is built as `infiniti`. It replaces the old alert
slider flow with a programmable Plus Key. Stock OOS exposes that key as a
first-class shortcut for actions such as camera, flashlight, screenshot,
recorder, and sound profile changes.

This custom ROM implementation has three independent pieces. Together they
replace the AOSP and LineageOS Assist-key plumbing with the PlusKey APK.

| # | Piece | Lives in | Role |
|---|-------|----------|------|
| 1 | PlusKey APK | `device/oneplus/infiniti/parts/PlusKey/` | User-facing UI and action runner |
| 2 | Settings integration | `patches/0009-settings-pluskey-category.patch` and `patches/0011-lineage-hide-assist-key.patch` | Adds the Settings homepage entry and hides LineageParts Assist controls |
| 3 | Framework key handler | `patches/0010-pwm-pluskey-handler.patch` | Routes `KEYCODE_ASSIST` to the APK through broadcasts |

The pieces are intentionally decoupled. If the APK is missing, the framework
broadcasts are harmless no-ops. If the framework patch is missing, the Plus Key
page is still reachable from Settings. This makes bring-up easier because each
piece can be verified on its own.

Hardware contract verified for this build: the physical Plus Key reports the
standard Android `KEYCODE_ASSIST`. No kernel-side special handling is required
for the integration described here.

## Piece 1 - PlusKey APK

`device/oneplus/infiniti/parts/PlusKey/` contains the in-tree, platform-signed
app installed to `/system_ext`. There are no prebuilts.

```text
parts/PlusKey/
|-- Android.bp
|-- AndroidManifest.xml
|-- com.oplus.pluskey.xml
|-- res/
`-- src/
```

The app is privileged and platform-signed because its actions need signature or
privileged permissions, including:

- `WRITE_SECURE_SETTINGS` / `WRITE_SETTINGS` for storing the selected actions
  in `Settings.System`.
- `ACCESS_NOTIFICATION_POLICY` for Do Not Disturb changes.
- `MODIFY_AUDIO_SETTINGS` for ringer-mode changes.
- `STATUS_BAR_SERVICE`, `CAPTURE_VIDEO_OUTPUT`, `MANAGE_INPUT_DEVICES`, and
  `MONITOR_INPUT` for actions such as screenshots, camera triggers, and input
  handling.

The privapp allowlist is `com.oplus.pluskey.xml`. Keeping the permission list in
the allowlist makes missing-permission regressions fail during build instead of
silently breaking at runtime.

The APK is included in the build from `device/oneplus/infiniti/device.mk`:

```make
PRODUCT_PACKAGES += \
    PlusKey
```

When porting, add `PlusKey` to the target device's `PRODUCT_PACKAGES` list, or
the module will build only when requested manually and will not be installed in
normal ROM images.

### Entry points

There is no launcher entry. The Settings app opens the Plus Key UI explicitly:

| Intent action | Target | Purpose |
|---------------|--------|---------|
| `com.oplus.pluskey.SETTINGS` | Settings activity | Opened from the Settings homepage Plus Key entry |
| `com.oplus.pluskey.SHORT_PRESS` | Broadcast receiver | Fired by `PhoneWindowManager` on short press |
| `com.oplus.pluskey.LONG_PRESS` | Broadcast receiver | Fired by `PhoneWindowManager` on long press |
| `com.oplus.pluskey.CAMERA_TRIGGER_DOWN` | Broadcast receiver | Fired when the Assist key goes down for camera-trigger handling |
| `com.oplus.pluskey.CAMERA_TRIGGER_UP` | Broadcast receiver | Fired when the Assist key is released for camera-trigger handling |

The receiver reads the current action from `Settings.System` and dispatches it.
Headless actions do not start an activity, so they can run from the lockscreen
where Android policy allows it.

On boot, the app clears the system `ASSISTANT` role. That prevents Gemini or any
other assistant role holder from competing with this key path.

## Piece 2 - Settings integration

There are two Settings-side changes.

### Add the Plus Key homepage entry

`patches/0009-settings-pluskey-category.patch` applies to
`packages/apps/Settings`.

It adds:

- `res/drawable/ic_settings_pluskey.xml`
- `top_level_pluskey_title`
- `top_level_pluskey_summary`
- A `HomepagePreference` for `com.oplus.pluskey.SETTINGS`

The homepage entry uses an explicit intent because the target UI lives in the
separate `com.oplus.pluskey` APK:

```xml
<com.android.settings.widget.HomepagePreference
    android:icon="@drawable/ic_settings_pluskey"
    android:key="top_level_pluskey"
    android:order="-45"
    android:title="@string/top_level_pluskey_title"
    android:summary="@string/top_level_pluskey_summary"
    settings:highlightableMenuKey="@string/top_level_pluskey_title">

    <intent
        android:action="com.oplus.pluskey.SETTINGS"
        android:targetPackage="com.oplus.pluskey" />
</com.android.settings.widget.HomepagePreference>
```

The same patch updates both `res/xml/top_level_settings.xml` and
`res/xml/top_level_settings_expressive.xml` so the entry is present in either
homepage layout.

### Hide legacy Assist key controls

`patches/0011-lineage-hide-assist-key.patch` applies to
`device/oneplus/infiniti`.

It adds the hardware-key bitfields to:

```text
overlay-lineage/lineage-sdk/lineage/res/res/values/config.xml
```

with only the Volume rocker bit enabled:

```xml
<integer name="config_deviceHardwareKeys">64</integer>
<integer name="config_deviceHardwareWakeKeys">64</integer>
```

LineageParts uses these bitfields to decide which categories are shown under
System -> Buttons. Dropping the Assist bit hides the old Assist key short-press
and long-press pickers, so users only see the Plus Key UI.

This is UI cleanup only. The framework still receives `KEYCODE_ASSIST`, and
piece 3 handles it before the old Assist action path is used.

## Piece 3 - Framework key handler

`patches/0010-pwm-pluskey-handler.patch` applies to `frameworks/base`.

It modifies:

```text
services/core/java/com/android/server/policy/PhoneWindowManager.java
```

and adds:

```text
services/core/java/com/android/server/policy/InfinitiPlusKey.java
```

`PhoneWindowManager` is where Android handles the Assist key. The patch gates
that behavior for Infiniti devices and forwards the key events to the APK.

Short press:

```java
private void assistPress() {
    if (InfinitiPlusKey.isInfiniti()) {
        cancelPreloadRecentApps();
        InfinitiPlusKey.fireShortPress(mContext);
        return;
    }

    // Existing AOSP/Lineage behavior continues below.
}
```

Long press:

```java
private void assistLongPress() {
    if (InfinitiPlusKey.isInfiniti()) {
        cancelPreloadRecentApps();
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                "Plus Key - Long Press");
        InfinitiPlusKey.fireLongPress(mContext);
        return;
    }

    // Existing AOSP/Lineage behavior continues below.
}
```

The patch also forces long-press detection on Infiniti:

```java
boolean supportLongPress() {
    return InfinitiPlusKey.isInfiniti()
            || mAssistLongPressAction != Action.NOTHING;
}
```

Without that, Android can skip long-press detection when the old Assist
long-press setting is configured as "do nothing", which would prevent the
PlusKey APK from receiving the long-press broadcast.

Finally, `handleKeyGesture()` fires camera-trigger down/up broadcasts for
`KEYCODE_ASSIST`. The APK uses those to support camera-style press/release
behavior.

### InfinitiPlusKey

`InfinitiPlusKey` keeps the device gate and broadcast plumbing out of
`PhoneWindowManager`.

The device check is cached and currently recognizes:

```java
"infiniti",
"OP60FFL1",
"OP611FL1",
```

It checks the common device properties:

- `ro.lineage.device`
- `ro.evolution.device`
- `ro.product.device`
- `ro.product.vendor.device`
- `ro.vendor.product.device`

The helper sends explicit package-targeted broadcasts to `com.oplus.pluskey`.
This keeps the IPC boundary simple: `PhoneWindowManager` runs in
`system_server`, while the PlusKey app runs as a separate privileged app in
`system_ext`.

## Applying the patches

From the Android workspace root:

```sh
git -C packages/apps/Settings apply ../../../patches/0009-settings-pluskey-category.patch
git -C frameworks/base apply ../../patches/0010-pwm-pluskey-handler.patch
git -C device/oneplus/infiniti apply ../../../patches/0011-lineage-hide-assist-key.patch
```

If your shell is already in `/home/koaan/android/INFIX`, using absolute paths is
less error-prone:

```sh
git -C packages/apps/Settings apply /home/koaan/android/INFIX/patches/0009-settings-pluskey-category.patch
git -C frameworks/base apply /home/koaan/android/INFIX/patches/0010-pwm-pluskey-handler.patch
git -C device/oneplus/infiniti apply /home/koaan/android/INFIX/patches/0011-lineage-hide-assist-key.patch
```

## Porting notes

For another OnePlus device with the same `KEYCODE_ASSIST` hardware behavior:

1. Copy `parts/PlusKey/` and make sure the APK is platform-signed and installed
   as privileged under `/system_ext`.
2. Add `PlusKey` to the target device's `PRODUCT_PACKAGES`.
3. Reuse `patches/0009-settings-pluskey-category.patch` for Settings unless the
   target ROM has a substantially different homepage layout.
4. Apply the Assist-bit removal to that device's own Lineage overlay. Do not
   assume Infiniti's overlay path exists on another tree.
5. Rename or extend `InfinitiPlusKey` and update the supported codename list.
   Keep the device gate. Removing it would change Assist-key behavior for every
   device sharing that `frameworks/base` build.

If the target device does not report `KEYCODE_ASSIST` for the side key, this
integration is not enough. That device first needs a keylayout or KeyHandler
mapping that turns the hardware key into `KEYCODE_ASSIST`.

## Smoke test

After flashing:

1. Check the APK:

```sh
adb shell pm list packages | grep pluskey
adb shell dumpsys package com.oplus.pluskey | grep flags
```

The package should exist and show system/privileged flags.

2. Check Settings:

- The Settings homepage shows a Plus Key entry.
- System -> Buttons no longer shows the legacy Assist key category.

3. Check framework dispatch:

```sh
adb shell input keyevent --longpress KEYCODE_ASSIST
adb logcat -s InfinitiPlusKey
```

The configured action should run, and logcat should show the relevant
`InfinitiPlusKey` broadcast log such as `fireLongPress`.
