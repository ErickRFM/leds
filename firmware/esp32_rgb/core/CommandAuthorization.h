#pragma once
#include <cstdint>

#include "CompiCommandArbiter.h"
#include "OwnerEnrollment.h"

namespace esp32core {

/**
 * Unified per-command authorization (SEG.2).
 *
 * The arbiter (CompiCommandArbiter) decides *whether a channel may act at all*
 * under the current active mode. This module answers a different, narrower
 * question: may this specific command, given its privilege class, run from this
 * source right now?
 *
 * Rules (all fail-closed):
 *  - Read-only commands may run from any source.
 *  - Every other class is "privileged" and requires a privileged source.
 *  - A BLE source must additionally have an AUTHENTICATED owner session; the
 *    BLE link state alone (bonded/encrypted) never authorizes.
 *  - Internal automations may only touch hardware writes and script actions;
 *    they may never change the network, install projects, update firmware or
 *    perform admin operations.
 *  - Unknown/unspecified privilege is denied.
 */
enum class PrivilegeClass : uint8_t {
  READ_ONLY = 0,
  HARDWARE_WRITE = 1,
  SCRIPT_AUTOMATION = 2,
  NETWORK_CHANGE = 3,
  PROJECT_INSTALL = 4,
  FIRMWARE_UPDATE = 5,
  ADMIN = 6
};

struct AuthorizationDecision {
  bool allowed = false;
  const char* reason = "denied";
};

class CommandAuthorization final {
 public:
  /** READ_ONLY is the only class that is not privileged by itself. */
  static bool isPrivileged(PrivilegeClass privilege) {
    return privilege != PrivilegeClass::READ_ONLY;
  }

  static const char* name(PrivilegeClass privilege) {
    switch (privilege) {
      case PrivilegeClass::READ_ONLY: return "read_only";
      case PrivilegeClass::HARDWARE_WRITE: return "hardware_write";
      case PrivilegeClass::SCRIPT_AUTOMATION: return "script_automation";
      case PrivilegeClass::NETWORK_CHANGE: return "network_change";
      case PrivilegeClass::PROJECT_INSTALL: return "project_install";
      case PrivilegeClass::FIRMWARE_UPDATE: return "firmware_update";
      case PrivilegeClass::ADMIN: return "admin";
    }
    return "unknown";
  }

  /**
   * `ownerSessionAuthenticated` must be the *verified* session state (from
   * OwnerEnrollment), never a local Android claim. It is only consulted for the
   * BLE source: the firmware's own Wi-Fi SoftAP link is already a privileged
   * local owner channel, and internal automations are firmware-originated.
   */
  static AuthorizationDecision decide(CommandSource source,
                                      bool ownerSessionAuthenticated,
                                      PrivilegeClass privilege) {
    if (!isPrivileged(privilege)) {
      return {true, "read_only"};
    }

    switch (source) {
      case CommandSource::BLE_UNAUTHENTICATED:
        return {false, "unauthenticated_ble"};

      case CommandSource::BLE_AUTHENTICATED:
        if (!ownerSessionAuthenticated) {
          return {false, "no_owner_session"};
        }
        return {true, "owner_session"};

      case CommandSource::WIFI_SOFTAP_LOCAL:
        return {true, "local_owner"};

      case CommandSource::AUTOMATION_INTERNAL:
        if (privilege == PrivilegeClass::HARDWARE_WRITE ||
            privilege == PrivilegeClass::SCRIPT_AUTOMATION) {
          return {true, "automation_scope"};
        }
        return {false, "automation_out_of_scope"};
    }
    return {false, "unknown_source"};
  }

  /** Convenience overload bound to the live owner-enrollment state machine. */
  static AuthorizationDecision decide(const OwnerEnrollment& enrollment,
                                      CommandSource source, PrivilegeClass privilege) {
    return decide(source, enrollment.authenticated(), privilege);
  }
};

}  // namespace esp32core
