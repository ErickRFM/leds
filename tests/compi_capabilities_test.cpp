#include "../firmware/esp32_rgb/core/CompiCapabilities.h"
#include <cassert>
using namespace esp32core;

int main() {
  constexpr BoardProfile legacy = legacyRgbV1();
  static_assert(protocolCompatible(legacy, 1), "RGB v1 must remain compatible");
  static_assert(!protocolCompatible(legacy, 2), "major versions must not silently match");
  static_assert(supports(legacy, Capability::Rgb), "RGB is part of v1");
  static_assert(!supports(legacy, Capability::Gpio), "v1 cannot claim GPIO");
  static_assert(validProfile(legacy), "v1 must have a valid profile");
  assert(authorize(legacy, Permission::ReadState, {false, false}) == Decision::Allow);
  assert(authorize(legacy, Permission::ControlRgb, {false, false}) == Decision::RequiresEnrollment);
  assert(authorize(legacy, Permission::ControlRgb, {true, false}) == Decision::Allow);
  assert(authorize(legacy, Permission::ControlGpio, {true, true}) == Decision::Unsupported);
  assert(authorize(legacy, Permission::UpdateFirmware, {true, true}) == Decision::Unsupported);

  constexpr BoardProfile v4 = {1, 1, 4194304u,
    capabilityBit(Capability::Rgb) | capabilityBit(Capability::Sequences) |
    capabilityBit(Capability::FirmwareUpdate), 1u};
  static_assert(validProfile(v4), "v4 profile validates");
  assert(authorize(v4, Permission::Program, {false, true}) == Decision::RequiresEnrollment);
  assert(authorize(v4, Permission::Program, {true, false}) == Decision::RequiresPhysicalConfirmation);
  assert(authorize(v4, Permission::Program, {true, true}) == Decision::Allow);
  assert(authorize(v4, Permission::UpdateFirmware, {true, false}) == Decision::RequiresPhysicalConfirmation);
  assert(authorize(v4, Permission::UpdateFirmware, {true, true}) == Decision::Allow);

  constexpr BoardProfile invalidUnknownBits = {1, 1, 4194304u, 1u << 30, 1u};
  constexpr BoardProfile invalidNoFlash = {1, 1, 0u, capabilityBit(Capability::FirmwareUpdate), 1u};
  assert(!validProfile(invalidUnknownBits));
  assert(!validProfile(invalidNoFlash));
  assert(authorize(invalidUnknownBits, Permission::ReadState, {true, true}) == Decision::Unsupported);
}
