#pragma once
#include <cstdint>

namespace esp32core {

// Compi V4 device contract. Values form a compact wire-safe capability bitmap.
// Never infer privileges from advertised capabilities or the public device ID.
enum class Capability : uint32_t {
  Rgb = 1u << 0,
  Effects = 1u << 1,
  Gpio = 1u << 2,
  Adc = 1u << 3,
  Sequences = 1u << 4,
  Diagnostics = 1u << 5,
  SecurePairing = 1u << 6,
  FirmwareUpdate = 1u << 7,
  Automation = 1u << 8,
  SerialMonitor = 1u << 9,
};

constexpr uint32_t capabilityBit(Capability capability) {
  return static_cast<uint32_t>(capability);
}
constexpr uint32_t kKnownCapabilities =
    capabilityBit(Capability::Rgb) | capabilityBit(Capability::Effects) |
    capabilityBit(Capability::Gpio) | capabilityBit(Capability::Adc) |
    capabilityBit(Capability::Sequences) | capabilityBit(Capability::Diagnostics) |
    capabilityBit(Capability::SecurePairing) | capabilityBit(Capability::FirmwareUpdate) |
    capabilityBit(Capability::Automation) | capabilityBit(Capability::SerialMonitor);

struct BoardProfile {
  uint16_t protocolMajor;
  uint16_t protocolMinor;
  uint32_t flashBytes;
  uint32_t advertisedCapabilities;
  uint32_t firmwareBuild;
};

enum class Permission : uint8_t { ReadState, ControlRgb, ControlGpio, Program, UpdateFirmware };
enum class Decision : uint8_t { Allow, Unsupported, RequiresEnrollment, RequiresPhysicalConfirmation };

// Protocol V1 has RGB, effects and READ/NOTIFY STATE only. A V1 client
// must not conclude GPIO, auth, flash or automation exist without negotiation.
constexpr BoardProfile legacyRgbV1() {
  return {1, 0, 0u, capabilityBit(Capability::Rgb) | capabilityBit(Capability::Effects), 0u};
}
constexpr bool supports(const BoardProfile& profile, Capability capability) {
  return (profile.advertisedCapabilities & capabilityBit(capability)) != 0;
}
constexpr bool protocolCompatible(const BoardProfile& profile, uint16_t clientMajor) {
  return profile.protocolMajor != 0 && profile.protocolMajor == clientMajor;
}
constexpr bool validProfile(const BoardProfile& profile) {
  return profile.protocolMajor != 0 &&
         (profile.advertisedCapabilities & ~kKnownCapabilities) == 0 &&
         // OTA is never an inferred capability based on partition layout alone.
         (!supports(profile, Capability::FirmwareUpdate) || profile.flashBytes != 0);
}

// Only a *trusted and cryptographically authenticated* owner session may
// set enrolled=true. This struct is NOT itself an authentication mechanism.
struct AccessContext {
  bool enrolled;
  bool physicalConfirmation;
};

constexpr Decision authorize(const BoardProfile& profile, Permission permission,
                             AccessContext context) {
  if (!validProfile(profile)) return Decision::Unsupported;
  switch (permission) {
    case Permission::ReadState:
      return supports(profile, Capability::Rgb) ? Decision::Allow : Decision::Unsupported;
    case Permission::ControlRgb:
      if (!supports(profile, Capability::Rgb)) return Decision::Unsupported;
      return context.enrolled ? Decision::Allow : Decision::RequiresEnrollment;
    case Permission::ControlGpio:
      if (!supports(profile, Capability::Gpio)) return Decision::Unsupported;
      return context.enrolled ? Decision::Allow : Decision::RequiresEnrollment;
    case Permission::Program:
      if (!supports(profile, Capability::Sequences)) return Decision::Unsupported;
      if (!context.enrolled) return Decision::RequiresEnrollment;
      return context.physicalConfirmation ? Decision::Allow : Decision::RequiresPhysicalConfirmation;
    case Permission::UpdateFirmware:
      if (!supports(profile, Capability::FirmwareUpdate)) return Decision::Unsupported;
      if (!context.enrolled) return Decision::RequiresEnrollment;
      return context.physicalConfirmation ? Decision::Allow : Decision::RequiresPhysicalConfirmation;
  }
  return Decision::Unsupported;
}
}  // namespace esp32core
