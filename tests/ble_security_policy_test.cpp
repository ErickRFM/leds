#include <cassert>
#include <iostream>
#include <string>

#include "../firmware/esp32_rgb/core/BleSecurityPolicy.h"
#include "../firmware/esp32_rgb/core/CompiCommandArbiter.h"

using esp32core::BleAuthState;
using esp32core::BleSecurityPolicy;
using esp32core::CommandSource;

int main() {
  const char* kOwnerKey = "0123456789abcdef";

  // Params: only "AUTH <key>" is an owner-authorization frame.
  assert(!BleSecurityPolicy::looksLikeAuth(nullptr));
  assert(!BleSecurityPolicy::looksLikeAuth("SET,1,2,3,4"));
  assert(!BleSecurityPolicy::looksLikeAuth("AUTH "));
  assert(!BleSecurityPolicy::looksLikeAuth("AUTH"));
  assert(BleSecurityPolicy::looksLikeAuth("AUTH 0123456789abcdef"));
  assert(std::string(BleSecurityPolicy::authKey("AUTH 0123456789abcdef")) == kOwnerKey);
  assert(BleSecurityPolicy::authKey("SET,1,2,3,4") == nullptr);

  // Bounded equality: empty, wrong length and wrong content all fail.
  assert(!BleSecurityPolicy::keyMatches(nullptr, kOwnerKey));
  assert(!BleSecurityPolicy::keyMatches(kOwnerKey, nullptr));
  assert(!BleSecurityPolicy::keyMatches("", kOwnerKey));
  assert(!BleSecurityPolicy::keyMatches("0123456789abcde", kOwnerKey));
  assert(!BleSecurityPolicy::keyMatches("0123456789abcdeg", kOwnerKey));
  assert(BleSecurityPolicy::keyMatches(kOwnerKey, kOwnerKey));

  // Fresh policy is disconnected and strictly fail-closed.
  BleSecurityPolicy policy;
  assert(policy.state() == BleAuthState::DISCONNECTED);
  assert(!policy.authorized());
  assert(!policy.encrypted());
  assert(policy.source() == CommandSource::BLE_UNAUTHENTICATED);

  // Connecting alone grants nothing.
  policy.onConnected();
  assert(policy.state() == BleAuthState::CONNECTED_UNAUTHENTICATED);
  assert(policy.source() == CommandSource::BLE_UNAUTHENTICATED);

  // The owner key over a plaintext link is always refused.
  assert(!policy.authorize(kOwnerKey, kOwnerKey));
  assert(std::string(policy.reason()) == "unencrypted_link");
  assert(!policy.authorized());
  assert(policy.source() == CommandSource::BLE_UNAUTHENTICATED);

  // Pairing is an intermediate state; it never authorizes.
  policy.onPairingStarted();
  assert(policy.state() == BleAuthState::PAIRING);
  assert(policy.source() == CommandSource::BLE_UNAUTHENTICATED);

  // Encryption without bonding is still unauthenticated.
  policy.onLinkSecured(false);
  assert(policy.state() == BleAuthState::ENCRYPTED);
  assert(policy.encrypted());
  assert(!policy.bonded());
  assert(policy.source() == CommandSource::BLE_UNAUTHENTICATED);

  // Bonding is NOT authorization; wrong key is rejected and grants nothing.
  policy.onLinkSecured(true);
  assert(policy.state() == BleAuthState::BONDED);
  assert(policy.bonded());
  assert(!policy.authorized());
  assert(!policy.authorize("ffffffffffffffff", kOwnerKey));
  assert(std::string(policy.reason()) == "invalid_owner_key");
  assert(policy.state() == BleAuthState::BONDED);
  assert(policy.source() == CommandSource::BLE_UNAUTHENTICATED);

  // Correct owner key over the encrypted link authorizes the session.
  assert(policy.authorize(kOwnerKey, kOwnerKey));
  assert(policy.state() == BleAuthState::AUTHORIZED);
  assert(policy.authorized());
  assert(policy.source() == CommandSource::BLE_AUTHENTICATED);

  // Manual revocation drops privileges but keeps the encrypted link usable.
  policy.revoke();
  assert(policy.state() == BleAuthState::BONDED);
  assert(!policy.authorized());
  assert(policy.source() == CommandSource::BLE_UNAUTHENTICATED);
  assert(policy.authorize(kOwnerKey, kOwnerKey));
  assert(policy.authorized());

  // Disconnect unconditionally revokes authorization.
  policy.onDisconnected();
  assert(policy.state() == BleAuthState::DISCONNECTED);
  assert(!policy.authorized());
  assert(!policy.encrypted());
  assert(!policy.bonded());
  assert(policy.source() == CommandSource::BLE_UNAUTHENTICATED);

  // A late/stale key presented with no active link must not authorize.
  assert(!policy.authorize(kOwnerKey, kOwnerKey));
  assert(std::string(policy.reason()) == "no_active_link");
  assert(policy.state() == BleAuthState::FAILED);
  assert(policy.source() == CommandSource::BLE_UNAUTHENTICATED);

  // A failed pairing never authorizes and can be recovered by reconnecting.
  BleSecurityPolicy failed;
  failed.onConnected();
  failed.onPairingFailed();
  assert(failed.state() == BleAuthState::FAILED);
  assert(!failed.authorize(kOwnerKey, kOwnerKey));
  assert(!failed.authorized());
  assert(failed.source() == CommandSource::BLE_UNAUTHENTICATED);

  // Revoke before any link is a no-op (stays disconnected).
  BleSecurityPolicy idle;
  idle.revoke();
  assert(idle.state() == BleAuthState::DISCONNECTED);

  std::cout << "BleSecurityPolicy host tests passed successfully." << std::endl;
  return 0;
}
