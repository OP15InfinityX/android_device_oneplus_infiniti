/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <android/binder_manager.h>
#include <tinyxml2.h>

#include <aidl/vendor/oplus/hardware/displaypanelfeature/IDisplayPanelFeature.h>

#include <algorithm>
#include <charconv>
#include <chrono>
#include <cstdlib>
#include <cstdint>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

using aidl::vendor::oplus::hardware::displaypanelfeature::IDisplayPanelFeature;

namespace {

constexpr char kServiceName[] =
        "vendor.oplus.hardware.displaypanelfeature.IDisplayPanelFeature/default";
constexpr char kAdfrConfigPath[] = "/vendor/etc/multimedia_display_adfr2minfps_config.xml";
constexpr char kOplusMinFpsPath[] = "/sys/kernel/oplus_display/min_fps";
constexpr char kMeasuredFpsPath[] = "/sys/class/drm/card0-sde-crtc-0/measured_fps";
constexpr char kFpsPeriodicityPath[] = "/sys/class/drm/card0-sde-crtc-0/fps_periodicity_ms";
constexpr char kOplusRefreshRateProperty[] = "vendor.display.oplus_refresh_rate";
constexpr char kOplusLtpoMinFpsProperty[] = "vendor.display.oplus_ltpo_min_fps";
constexpr auto kMinFpsMirrorInterval = std::chrono::milliseconds(50);

constexpr int kFeatureAdfr2MinFpsEnable = 232;
constexpr int kFeatureAdfr2MinFpsState = 233;
constexpr int kFeatureRusUpdate = 234;
constexpr int kLowestUserMinFps = 30;

struct AdfrConfig {
    int version = 0;
    int enable = 0;
    int debugEnable = 0;
    int sensorEnable = 0;
    int panelNitEnable = 0;
    int grayEnable = 0;
    int trackingSwitch = 0;
    int grayCal = 0;
    int sampleInterval = 0;
    int sensorInLux = 0;
    int sensorOutLux = 0;
    int sensorGapTime = 0;
    int panelId = 0;
    int fullScreenAod = 0;
    int reserveMode = 0;
    std::vector<int> panelNitLevel;
    std::vector<int> yLLevel;
    std::vector<int> yHLevel;
    std::vector<int> sumLevel;
    std::vector<int> maxLevel;
    std::vector<int> minFps120Level;
    std::vector<int> minFps90Level;
    std::vector<int> minFps60Level;
    std::vector<int> aodPanelNitLevel;
    std::vector<int> aodYLLevel;
    std::vector<int> aodYHLevel;
    std::vector<int> aodSumLevel;
    std::vector<int> aodMaxLevel;
    std::vector<int> aodMinFps120Level;
    std::vector<int> aodMinFps90Level;
    std::vector<int> aodMinFps60Level;
    std::vector<int> reservePanelNitLevel;
    std::vector<int> reserveYLLevel;
    std::vector<int> reserveYHLevel;
    std::vector<int> reserveSumLevel;
    std::vector<int> reserveMaxLevel;
    std::vector<int> reserveMinFpsLevel1;
    std::vector<int> reserveMinFpsLevel2;
    std::vector<int> reserveMinFpsLevel3;
};

int parseInt(const char* text) {
    if (text == nullptr) {
        return 0;
    }

    std::string value(text);
    const char* begin = value.data();
    const char* end = begin + value.size();
    int parsed = 0;
    auto result = std::from_chars(begin, end, parsed);
    return result.ec == std::errc() ? parsed : 0;
}

std::vector<int> parseIntList(const char* text) {
    std::vector<int> values;
    if (text == nullptr) {
        return values;
    }

    std::istringstream stream(text);
    int value = 0;
    while (stream >> value) {
        values.push_back(value);
    }
    return values;
}

tinyxml2::XMLElement* firstChild(tinyxml2::XMLElement* parent, const char* name) {
    return parent == nullptr ? nullptr : parent->FirstChildElement(name);
}

int childInt(tinyxml2::XMLElement* parent, const char* name) {
    return parseInt(firstChild(parent, name) != nullptr ? firstChild(parent, name)->GetText() : nullptr);
}

std::vector<int> childList(tinyxml2::XMLElement* parent, const char* name) {
    return parseIntList(firstChild(parent, name) != nullptr ? firstChild(parent, name)->GetText() : nullptr);
}

bool parseAdfrConfig(AdfrConfig* config) {
    std::string xml;
    if (!android::base::ReadFileToString(kAdfrConfigPath, &xml)) {
        PLOG(ERROR) << "Failed to read " << kAdfrConfigPath;
        return false;
    }

    tinyxml2::XMLDocument document;
    if (document.Parse(xml.c_str(), xml.size()) != tinyxml2::XML_SUCCESS) {
        LOG(ERROR) << "Failed to parse " << kAdfrConfigPath << ": " << document.ErrorStr();
        return false;
    }

    auto* root = document.RootElement();
    auto* adfr = firstChild(root, "oplsadfrCfg");
    auto* mode = firstChild(adfr, "mode");
    if (adfr == nullptr || mode == nullptr) {
        LOG(ERROR) << "Missing oplsadfrCfg/mode in " << kAdfrConfigPath;
        return false;
    }

    config->version = childInt(adfr, "version");
    config->enable = childInt(mode, "enable");
    config->debugEnable = childInt(mode, "debug_enable");
    config->sensorEnable = childInt(mode, "sensor_enable");
    config->panelNitEnable = childInt(mode, "panelnit_enable");
    config->grayEnable = childInt(mode, "gray_enable");
    config->trackingSwitch = childInt(mode, "tracking_switch");
    config->grayCal = childInt(mode, "gray_cal");
    config->sampleInterval = childInt(mode, "sampleInterval");
    config->sensorInLux = childInt(mode, "sensor_inlux");
    config->sensorOutLux = childInt(mode, "sensor_outlux");
    config->sensorGapTime = childInt(mode, "sensor_gaptime");
    config->panelId = childInt(mode, "panelid");
    config->fullScreenAod = childInt(mode, "fullscreen_aod");
    config->reserveMode = childInt(mode, "reserve_mode");
    config->panelNitLevel = childList(mode, "panelnit_level");
    config->yLLevel = childList(mode, "Y_l_level");
    config->yHLevel = childList(mode, "Y_h_level");
    config->sumLevel = childList(mode, "sum_level");
    config->maxLevel = childList(mode, "max_level");
    config->minFps120Level = childList(mode, "minfps120_level");
    config->minFps90Level = childList(mode, "minfps90_level");
    config->minFps60Level = childList(mode, "minfps60_level");
    config->aodPanelNitLevel = childList(mode, "aod_panelnit_level");
    config->aodYLLevel = childList(mode, "aod_Y_l_level");
    config->aodYHLevel = childList(mode, "aod_Y_h_level");
    config->aodSumLevel = childList(mode, "aod_sum_level");
    config->aodMaxLevel = childList(mode, "aod_max_level");
    config->aodMinFps120Level = childList(mode, "aod_minfps120_level");
    config->aodMinFps90Level = childList(mode, "aod_minfps90_level");
    config->aodMinFps60Level = childList(mode, "aod_minfps60_level");
    config->reservePanelNitLevel = childList(mode, "reserve_panelnit_level");
    config->reserveYLLevel = childList(mode, "reserve_Y_l_level");
    config->reserveYHLevel = childList(mode, "reserve_Y_h_level");
    config->reserveSumLevel = childList(mode, "reserve_sum_level");
    config->reserveMaxLevel = childList(mode, "reserve_max_level");
    config->reserveMinFpsLevel1 = childList(mode, "reserve_minfps_level1");
    config->reserveMinFpsLevel2 = childList(mode, "reserve_minfps_level2");
    config->reserveMinFpsLevel3 = childList(mode, "reserve_minfps_level3");

    if (config->version <= 0) {
        LOG(ERROR) << "Invalid ADFR XML version " << config->version;
        return false;
    }

    return true;
}

std::vector<int> buildStockPayload(const AdfrConfig& config) {
    std::vector<int> modes(225, 0);

    auto copyList = [&modes](size_t offset, const std::vector<int>& values) {
        if (offset >= modes.size()) {
            return;
        }

        modes[offset] = static_cast<int>(values.size());
        size_t copyCount = std::min(values.size(), modes.size() - offset - 1);
        std::copy_n(values.begin(), copyCount, modes.begin() + offset + 1);
    };

    modes[0] = 1;
    modes[1] = config.version;
    modes[2] = config.enable;
    modes[3] = config.debugEnable;
    modes[4] = config.sensorEnable;
    modes[5] = config.panelNitEnable;
    modes[6] = config.grayEnable;
    modes[7] = config.trackingSwitch;
    modes[8] = config.grayCal;
    modes[9] = config.sampleInterval;
    modes[10] = config.sensorInLux;
    modes[11] = config.sensorOutLux;
    modes[12] = config.sensorGapTime;
    modes[13] = config.panelId;
    modes[14] = config.fullScreenAod;
    modes[15] = config.reserveMode;

    copyList(18, config.panelNitLevel);
    copyList(24, config.yLLevel);
    copyList(30, config.yHLevel);
    copyList(36, config.sumLevel);
    copyList(42, config.maxLevel);
    copyList(48, config.minFps120Level);
    copyList(61, config.minFps90Level);
    copyList(74, config.minFps60Level);
    copyList(87, config.aodPanelNitLevel);
    copyList(93, config.aodYLLevel);
    copyList(99, config.aodYHLevel);
    copyList(105, config.aodSumLevel);
    copyList(111, config.aodMaxLevel);
    copyList(117, config.aodMinFps120Level);
    copyList(130, config.aodMinFps90Level);
    copyList(143, config.aodMinFps60Level);
    copyList(156, config.reservePanelNitLevel);
    copyList(162, config.reserveYLLevel);
    copyList(168, config.reserveYHLevel);
    copyList(174, config.reserveSumLevel);
    copyList(180, config.reserveMaxLevel);
    copyList(186, config.reserveMinFpsLevel1);
    copyList(199, config.reserveMinFpsLevel2);
    copyList(212, config.reserveMinFpsLevel3);

    return modes;
}

int parseMeasuredFps(const std::string& text) {
    const std::string marker = "fps:";
    const size_t markerPos = text.find(marker);
    if (markerPos == std::string::npos) {
        return 0;
    }

    const size_t valueStart = text.find_first_not_of(" \t", markerPos + marker.size());
    if (valueStart == std::string::npos) {
        return 0;
    }

    const size_t valueEnd = text.find_first_of(" \t\r\n", valueStart);
    const std::string value = text.substr(valueStart, valueEnd - valueStart);
    char* end = nullptr;
    const float parsed = std::strtof(value.c_str(), &end);
    if (end == value.c_str() || parsed <= 0.0f) {
        return 0;
    }

    return static_cast<int>(parsed + 0.5f);
}

int readIntFile(const char* path) {
    std::string value;
    if (!android::base::ReadFileToString(path, &value)) {
        return 0;
    }

    value = android::base::Trim(value);
    int parsed = 0;
    auto result = std::from_chars(value.data(), value.data() + value.size(), parsed);
    return result.ec == std::errc() ? parsed : 0;
}

int readUserMinFpsFloor() {
    const int minFps = android::base::GetIntProperty(kOplusLtpoMinFpsProperty, 0);
    return minFps >= kLowestUserMinFps ? minFps : 0;
}

void enforceUserMinFpsFloor(int minFpsFloor) {
    if (minFpsFloor <= 0) {
        return;
    }

    const int currentMinFps = readIntFile(kOplusMinFpsPath);
    if (currentMinFps >= minFpsFloor) {
        return;
    }

    const std::string value = std::to_string(minFpsFloor);
    if (!android::base::WriteStringToFile(value, kOplusMinFpsPath)) {
        PLOG(WARNING) << "Failed to enforce Oplus min fps floor " << value;
    }
}

void mirrorMinFpsProperty() {
    android::base::WriteStringToFile("100", kFpsPeriodicityPath);

    std::string lastValue;
    while (true) {
        const int minFpsFloor = readUserMinFpsFloor();
        enforceUserMinFpsFloor(minFpsFloor);

        int refreshRate = 0;

        std::string measuredFps;
        if (android::base::ReadFileToString(kMeasuredFpsPath, &measuredFps)) {
            refreshRate = parseMeasuredFps(measuredFps);
        }

        if (refreshRate <= 0) {
            refreshRate = readIntFile(kOplusMinFpsPath);
        }

        if (minFpsFloor > 0 && refreshRate < minFpsFloor) {
            refreshRate = minFpsFloor;
        }

        if (refreshRate > 0) {
            const std::string value = std::to_string(refreshRate);
            if (value != lastValue) {
                if (android::base::SetProperty(kOplusRefreshRateProperty, value)) {
                    lastValue = value;
                } else {
                    PLOG(WARNING) << "Failed to set " << kOplusRefreshRateProperty;
                }
            }
        } else {
            PLOG(WARNING) << "Failed to read Oplus refresh rate";
        }

        std::this_thread::sleep_for(kMinFpsMirrorInterval);
    }
}

}  // namespace

int main() {
    android::base::InitLogging(nullptr, android::base::LogdLogger(android::base::SYSTEM));

    AdfrConfig config;
    if (!parseAdfrConfig(&config)) {
        return 1;
    }

    auto binder = ndk::SpAIBinder(AServiceManager_waitForService(kServiceName));
    if (binder.get() == nullptr) {
        LOG(ERROR) << "Display panel feature service is unavailable";
        return 1;
    }

    auto panelFeature = IDisplayPanelFeature::fromBinder(binder);
    if (panelFeature == nullptr) {
        LOG(ERROR) << "Failed to bind display panel feature service";
        return 1;
    }

    std::vector<int> state(1, 0);
    int status = 0;
    auto ret = panelFeature->getDisplayPanelFeatureValue(kFeatureAdfr2MinFpsState, &state, &status);
    if (!ret.isOk()) {
        LOG(ERROR) << "Failed to query ADFR state: " << ret.getDescription();
        return 1;
    }
    LOG(INFO) << "Panel ADFR state before init=" << (state.empty() ? -1 : state[0]);

    auto payload = buildStockPayload(config);
    ret = panelFeature->setDisplayPanelFeatureValue(kFeatureRusUpdate, payload, &status);
    if (!ret.isOk() || status != 0) {
        LOG(ERROR) << "Failed to send ADFR XML payload, binder=" << ret.getDescription()
                   << " status=" << status;
        return 1;
    }

    std::vector<int> enable = {0, 1};
    ret = panelFeature->setDisplayPanelFeatureValue(kFeatureAdfr2MinFpsEnable, enable, &status);
    if (!ret.isOk() || status != 0) {
        LOG(ERROR) << "Failed to enable ADFR minfps, binder=" << ret.getDescription()
                   << " status=" << status;
        return 1;
    }

    LOG(INFO) << "Initialized stock ADFR minfps config version " << config.version;
    mirrorMinFpsProperty();
    return 0;
}
