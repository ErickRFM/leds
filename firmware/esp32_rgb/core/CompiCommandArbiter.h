#pragma once
#include <cstdint>

namespace esp32core {

/**
 * Thread-safe, deterministic command arbiter for the ESP32 laboratory.
 * Coordinates execution priority across BLE GATT, Wi-Fi SoftAP, active RGB scripts,
 * and background automations to ensure unauthenticated channels cannot disrupt
 * active protected operations.
 */
enum class CommandSource : uint8_t {
  BLE_UNAUTHENTICATED = 0,
  BLE_AUTHENTICATED = 1,
  WIFI_SOFTAP_LOCAL = 2,
  AUTOMATION_INTERNAL = 3
};

enum class ActiveMode : uint8_t {
  IDLE = 0,
  MANUAL_CONTROL = 1,
  SCRIPT_RUNNING = 2,
  AUTOMATION_ACTIVE = 3
};

struct ArbiterResult {
  bool allowed = false;
  const char* reason = "";
};

class CompiCommandArbiter final {
 public:
  CompiCommandArbiter() = default;

  void setActiveMode(ActiveMode mode, CommandSource owner) {
    currentMode_ = mode;
    currentOwner_ = owner;
  }

  ActiveMode currentMode() const { return currentMode_; }
  CommandSource currentOwner() const { return currentOwner_; }

  /**
   * Evaluates whether an incoming command request from a specific source
   * is allowed to execute given the current active mode.
   */
  ArbiterResult evaluate(CommandSource source, bool isEmergencyStop = false) const {
    if (isEmergencyStop) {
      // Emergency stop from any local route or authenticated BLE is always allowed.
      if (source == CommandSource::WIFI_SOFTAP_LOCAL ||
          source == CommandSource::BLE_AUTHENTICATED) {
        return {true, "Emergency stop accepted"};
      }
    }

    if (currentMode_ == ActiveMode::SCRIPT_RUNNING || currentMode_ == ActiveMode::AUTOMATION_ACTIVE) {
      // Unauthenticated BLE writes cannot interrupt or alter active scripts or automations.
      if (source == CommandSource::BLE_UNAUTHENTICATED) {
        return {false, "Operation rejected: active protected task running under Wi-Fi/Script authority"};
      }
      // Wi-Fi SoftAP local requests and authenticated BLE requests can override if explicitly authorized.
      if (source == CommandSource::WIFI_SOFTAP_LOCAL || source == CommandSource::BLE_AUTHENTICATED) {
        return {true, "Authorized override accepted"};
      }
      return {false, "Operation conflict with active background task"};
    }

    // When IDLE or in MANUAL_CONTROL, authenticated Wi-Fi and BLE operations are permitted.
    if (source == CommandSource::BLE_UNAUTHENTICATED && currentMode_ == ActiveMode::MANUAL_CONTROL &&
        currentOwner_ == CommandSource::WIFI_SOFTAP_LOCAL) {
      return {false, "Operation rejected: manual control locked by active Wi-Fi session"};
    }

    return {true, "Command permitted"};
  }

  void reset() {
    currentMode_ = ActiveMode::IDLE;
    currentOwner_ = CommandSource::BLE_UNAUTHENTICATED;
  }

 private:
  ActiveMode currentMode_ = ActiveMode::IDLE;
  CommandSource currentOwner_ = CommandSource::BLE_UNAUTHENTICATED;
};

}  // namespace esp32core
