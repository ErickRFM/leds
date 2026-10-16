#include <cassert>
#include <string>

#include "../firmware/esp32_rgb/core/CommandAuthorization.h"

using esp32core::AuthorizationDecision;
using esp32core::CommandAuthorization;
using esp32core::CommandSource;
using esp32core::EnrollmentResult;
using esp32core::OwnerEnrollment;
using esp32core::PrivilegeClass;

static const char* kPub =
    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
static const char* kDevNonce = "00112233445566778899aabbccddeeff";
static const char* kClientNonce = "ffeeddccbbaa99887766554433221100";
static const char* kCode = "deadbeef";

static bool allows(CommandSource source, bool session, PrivilegeClass privilege) {
  return CommandAuthorization::decide(source, session, privilege).allowed;
}

int main() {
  // Privilege naming is complete and stable.
  assert(std::string(CommandAuthorization::name(PrivilegeClass::READ_ONLY)) == "read_only");
  assert(std::string(CommandAuthorization::name(PrivilegeClass::HARDWARE_WRITE)) == "hardware_write");
  assert(std::string(CommandAuthorization::name(PrivilegeClass::SCRIPT_AUTOMATION)) == "script_automation");
  assert(std::string(CommandAuthorization::name(PrivilegeClass::NETWORK_CHANGE)) == "network_change");
  assert(std::string(CommandAuthorization::name(PrivilegeClass::PROJECT_INSTALL)) == "project_install");
  assert(std::string(CommandAuthorization::name(PrivilegeClass::FIRMWARE_UPDATE)) == "firmware_update");
  assert(std::string(CommandAuthorization::name(PrivilegeClass::ADMIN)) == "admin");

  assert(!CommandAuthorization::isPrivileged(PrivilegeClass::READ_ONLY));
  assert(CommandAuthorization::isPrivileged(PrivilegeClass::HARDWARE_WRITE));
  assert(CommandAuthorization::isPrivileged(PrivilegeClass::ADMIN));

  const CommandSource sources[] = {CommandSource::BLE_UNAUTHENTICATED,
                                   CommandSource::BLE_AUTHENTICATED,
                                   CommandSource::WIFI_SOFTAP_LOCAL,
                                   CommandSource::AUTOMATION_INTERNAL};

  // Read-only is the only thing an unauthenticated link may ever do.
  for (CommandSource source : sources) {
    assert(allows(source, false, PrivilegeClass::READ_ONLY));
    assert(allows(source, true, PrivilegeClass::READ_ONLY));
  }

  // Unauthenticated BLE never gets a privileged command.
  assert(!allows(CommandSource::BLE_UNAUTHENTICATED, false, PrivilegeClass::HARDWARE_WRITE));
  assert(!allows(CommandSource::BLE_UNAUTHENTICATED, true, PrivilegeClass::ADMIN));
  assert(std::string(CommandAuthorization::decide(CommandSource::BLE_UNAUTHENTICATED, false,
                                                  PrivilegeClass::HARDWARE_WRITE).reason) ==
         "unauthenticated_ble");

  // Authenticated BLE without a verified owner session is still denied.
  assert(!allows(CommandSource::BLE_AUTHENTICATED, false, PrivilegeClass::HARDWARE_WRITE));
  assert(std::string(CommandAuthorization::decide(CommandSource::BLE_AUTHENTICATED, false,
                                                  PrivilegeClass::HARDWARE_WRITE).reason) ==
         "no_owner_session");

  // Authenticated BLE with a verified owner session may run every privilege.
  const PrivilegeClass privileged[] = {
      PrivilegeClass::HARDWARE_WRITE,  PrivilegeClass::SCRIPT_AUTOMATION,
      PrivilegeClass::NETWORK_CHANGE,  PrivilegeClass::PROJECT_INSTALL,
      PrivilegeClass::FIRMWARE_UPDATE, PrivilegeClass::ADMIN};
  for (PrivilegeClass privilege : privileged) {
    assert(allows(CommandSource::BLE_AUTHENTICATED, true, privilege));
  }

  // The local SoftAP owner channel is privileged for every class.
  for (PrivilegeClass privilege : privileged) {
    assert(allows(CommandSource::WIFI_SOFTAP_LOCAL, false, privilege));
  }

  // Internal automations may only touch hardware/scripts, never the rest.
  assert(allows(CommandSource::AUTOMATION_INTERNAL, true, PrivilegeClass::HARDWARE_WRITE));
  assert(allows(CommandSource::AUTOMATION_INTERNAL, true, PrivilegeClass::SCRIPT_AUTOMATION));
  assert(!allows(CommandSource::AUTOMATION_INTERNAL, true, PrivilegeClass::NETWORK_CHANGE));
  assert(!allows(CommandSource::AUTOMATION_INTERNAL, true, PrivilegeClass::PROJECT_INSTALL));
  assert(!allows(CommandSource::AUTOMATION_INTERNAL, true, PrivilegeClass::FIRMWARE_UPDATE));
  assert(!allows(CommandSource::AUTOMATION_INTERNAL, true, PrivilegeClass::ADMIN));
  assert(std::string(CommandAuthorization::decide(CommandSource::AUTOMATION_INTERNAL, true,
                                                  PrivilegeClass::NETWORK_CHANGE).reason) ==
         "automation_scope");

  // Integration with the real SEG.1 owner session: authority only follows the
  // verified session, never a claimed link.
  OwnerEnrollment enrollment;
  assert(!CommandAuthorization::decide(enrollment, CommandSource::BLE_AUTHENTICATED,
                                       PrivilegeClass::HARDWARE_WRITE).allowed);
  assert(enrollment.openPairingWindow(1000, 120000, kCode) == EnrollmentResult::OK);
  assert(enrollment.enroll("owner-1", kPub, kCode, 1100) == EnrollmentResult::OK);
  assert(!CommandAuthorization::decide(enrollment, CommandSource::BLE_AUTHENTICATED,
                                       PrivilegeClass::HARDWARE_WRITE).allowed);
  assert(enrollment.beginSession(kDevNonce, 1200) == esp32core::SessionChallengeResult::OK);
  assert(enrollment.completeSession(kDevNonce, kClientNonce, 1, true) == EnrollmentResult::OK);
  assert(CommandAuthorization::decide(enrollment, CommandSource::BLE_AUTHENTICATED,
                                      PrivilegeClass::HARDWARE_WRITE).allowed);
  assert(CommandAuthorization::decide(enrollment, CommandSource::BLE_AUTHENTICATED,
                                      PrivilegeClass::ADMIN).allowed);

  // Disconnect removes authorization immediately.
  enrollment.onDisconnect();
  assert(!CommandAuthorization::decide(enrollment, CommandSource::BLE_AUTHENTICATED,
                                       PrivilegeClass::HARDWARE_WRITE).allowed);

  return 0;
}
