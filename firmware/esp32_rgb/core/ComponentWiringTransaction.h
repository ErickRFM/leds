#pragma once
#include <cstdint>
#include <cstring>
#include "PinManager.h"

namespace esp32core {

// V17 laboratory reconfiguration is intentionally VOLATILE. A reboot restores
// build defaults; never claim persistence or physical sensor presence.
enum class WiringOutcome : uint8_t {
  Applied, AlreadyApplied, Stale, Invalid, Reserved, Unsupported,
  NotReady, RolledBack, RecoveryFailed
};

inline const char* wiringOutcomeName(WiringOutcome result) {
  switch (result) {
    case WiringOutcome::Applied: return "applied";
    case WiringOutcome::AlreadyApplied: return "already_applied";
    case WiringOutcome::Stale: return "stale";
    case WiringOutcome::Invalid: return "invalid_pin";
    case WiringOutcome::Reserved: return "pin_reserved";
    case WiringOutcome::Unsupported: return "unsupported";
    case WiringOutcome::NotReady: return "not_ready";
    case WiringOutcome::RolledBack: return "rolled_back";
    case WiringOutcome::RecoveryFailed: return "recovery_failed";
  }
  return "error";
}

inline bool wiringPinAllowed(const char* id, uint8_t pin) {
  if (!id || PinManager::reservedForRgb(pin)) return false;
  if (std::strcmp(id, "dht22") == 0) return PinManager::outputCapable(pin);
  if (std::strcmp(id, "soil_moisture") == 0)
    return PinManager::analogInputCapable(pin);
  // RGB and I2C are not hot-swappable through this V17 HTTP API.
  return false;
}

// Modules own their leases. A failed start must release its own claims; the
// transaction restores the original assignment and re-starts the OLD module.
// Never steals pins held by an unrelated owner or system reservation.
template <typename Module>
WiringOutcome rewireSingle(PinManager& manager, Module& module,
                           const char* catalogId, uint8_t expected,
                           uint8_t requested) {
  if (!wiringPinAllowed(catalogId, requested)) return WiringOutcome::Invalid;
  if (!module.ready()) return WiringOutcome::NotReady;
  if (module.pin() != expected) return WiringOutcome::Stale;
  if (requested == expected) return WiringOutcome::AlreadyApplied;
  if (manager.isSystemReserved(requested) || manager.lease(requested))
    return WiringOutcome::Reserved;
  const uint8_t previous = module.pin();
  module.stop(manager);
  if (module.setPin(requested) && module.begin(manager))
    return WiringOutcome::Applied;
  module.stop(manager);
  if (!module.setPin(previous) || !module.begin(manager))
    return WiringOutcome::RecoveryFailed;
  return WiringOutcome::RolledBack;
}
}  // namespace esp32core
