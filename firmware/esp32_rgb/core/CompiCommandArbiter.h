#pragma once
#include <cstdint>

namespace esp32core {

/**
 * Thread-safe, deterministic command arbiter for the ESP32 laboratory.
 * Coordinates execution priority across BLE GATT, Wi-Fi SoftAP, active RGB scripts,
 * and background automations to ensure unauthenticated channels cannot disrupt
 * active protected operations or alter hardware without authorization.
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
   * is allowed to execute given the current active mode and channel authorization.
   */
  ArbiterResult evaluate(CommandSource source, bool isEmergencyStop = false) const {
    if (isEmergencyStop) {
      if (source == CommandSource::WIFI_SOFTAP_LOCAL ||
          source == CommandSource::BLE_AUTHENTICATED) {
        return {true, "Emergency stop accepted"};
      }
    }

    // Unauthenticated BLE writes cannot alter hardware, scripts or automations.
    if (source == CommandSource::BLE_UNAUTHENTICATED) {
      return {false, "Operation rejected: unauthenticated BLE channel"};
    }

    if (currentMode_ == ActiveMode::SCRIPT_RUNNING || currentMode_ == ActiveMode::AUTOMATION_ACTIVE) {
      if (source == CommandSource::WIFI_SOFTAP_LOCAL || source == CommandSource::BLE_AUTHENTICATED) {
        return {true, "Authorized override accepted"};
      }
      return {false, "Operation conflict with active background task"};
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
