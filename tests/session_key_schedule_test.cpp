#include <cassert>
#include <cstdint>

#include "../firmware/esp32_rgb/core/SessionKeySchedule.h"
#include "support/TestSessionCrypto.h"

using esp32core::kSessionKeyBytes;
using esp32core::kSessionMacBytes;
using esp32core::kSessionNonceBytes;
using esp32core::SessionKeyPair;
using esp32core::SessionKeySchedule;
using esp32core_test::TestOnlySessionCrypto;

static void fillNonce(uint8_t nonce[kSessionNonceBytes], uint8_t seed) {
  for (size_t i = 0; i < kSessionNonceBytes; ++i) {
    nonce[i] = static_cast<uint8_t>(seed + i);
  }
}

int main() {
  TestOnlySessionCrypto crypto;

  SessionKeyPair device;
  SessionKeyPair owner;
  assert(crypto.generateKeyPair(device));
  assert(crypto.generateKeyPair(owner));

  uint8_t deviceNonce[kSessionNonceBytes];
  uint8_t clientNonce[kSessionNonceBytes];
  fillNonce(deviceNonce, 0x10);
  fillNonce(clientNonce, 0x40);

  uint8_t deviceKey[kSessionKeyBytes];
  uint8_t deviceProof[kSessionMacBytes];
  assert(SessionKeySchedule::derive(crypto, device.privateKey, owner.publicKey, deviceNonce,
                                    clientNonce, 7, deviceKey, deviceProof));

  // The client runs the mirror computation with its own private key and the
  // device public key; both sides must agree.
  uint8_t clientKey[kSessionKeyBytes];
  uint8_t clientProof[kSessionMacBytes];
  assert(SessionKeySchedule::derive(crypto, owner.privateKey, device.publicKey, deviceNonce,
                                    clientNonce, 7, clientKey, clientProof));
  assert(SessionKeySchedule::constantTimeEquals(deviceKey, clientKey, kSessionKeyBytes));
  assert(SessionKeySchedule::constantTimeEquals(deviceProof, clientProof, kSessionMacBytes));

  // Determinism.
  uint8_t againKey[kSessionKeyBytes];
  uint8_t againProof[kSessionMacBytes];
  assert(SessionKeySchedule::derive(crypto, device.privateKey, owner.publicKey, deviceNonce,
                                    clientNonce, 7, againKey, againProof));
  assert(SessionKeySchedule::constantTimeEquals(deviceKey, againKey, kSessionKeyBytes));
  assert(SessionKeySchedule::constantTimeEquals(deviceProof, againProof, kSessionMacBytes));

  // A different client nonce changes both outputs.
  uint8_t otherNonce[kSessionNonceBytes];
  fillNonce(otherNonce, 0x80);
  uint8_t altKey[kSessionKeyBytes];
  uint8_t altProof[kSessionMacBytes];
  assert(SessionKeySchedule::derive(crypto, device.privateKey, owner.publicKey, deviceNonce,
                                    otherNonce, 7, altKey, altProof));
  assert(!SessionKeySchedule::constantTimeEquals(deviceKey, altKey, kSessionKeyBytes));
  assert(!SessionKeySchedule::constantTimeEquals(deviceProof, altProof, kSessionMacBytes));

  // A different counter keeps the session key but changes the proof (replay
  // protection lives in the proof binding).
  uint8_t counterKey[kSessionKeyBytes];
  uint8_t counterProof[kSessionMacBytes];
  assert(SessionKeySchedule::derive(crypto, device.privateKey, owner.publicKey, deviceNonce,
                                    clientNonce, 8, counterKey, counterProof));
  assert(SessionKeySchedule::constantTimeEquals(deviceKey, counterKey, kSessionKeyBytes));
  assert(!SessionKeySchedule::constantTimeEquals(deviceProof, counterProof, kSessionMacBytes));
  assert(!SessionKeySchedule::constantTimeEquals(againProof, counterProof, kSessionMacBytes));

  // A different owner keypair yields a different shared secret.
  SessionKeyPair otherOwner;
  assert(crypto.generateKeyPair(otherOwner));
  uint8_t strangerKey[kSessionKeyBytes];
  uint8_t strangerProof[kSessionMacBytes];
  assert(SessionKeySchedule::derive(crypto, device.privateKey, otherOwner.publicKey, deviceNonce,
                                    clientNonce, 7, strangerKey, strangerProof));
  assert(!SessionKeySchedule::constantTimeEquals(deviceKey, strangerKey, kSessionKeyBytes));

  // Failure propagation: crypto failure => derive failure (fail-closed).
  TestOnlySessionCrypto broken;
  assert(broken.generateKeyPair(device));
  assert(broken.generateKeyPair(owner));
  broken.failSharedSecret = true;
  uint8_t deadKey[kSessionKeyBytes];
  uint8_t deadProof[kSessionMacBytes];
  assert(!SessionKeySchedule::derive(broken, device.privateKey, owner.publicKey, deviceNonce,
                                     clientNonce, 7, deadKey, deadProof));
  broken.failSharedSecret = false;
  broken.failHmac = true;
  assert(!SessionKeySchedule::derive(broken, device.privateKey, owner.publicKey, deviceNonce,
                                     clientNonce, 7, deadKey, deadProof));

  // constantTimeEquals rejects null and differing spans.
  assert(!SessionKeySchedule::constantTimeEquals(nullptr, deviceKey, kSessionKeyBytes));
  uint8_t flipped[kSessionKeyBytes];
  for (size_t i = 0; i < kSessionKeyBytes; ++i) flipped[i] = deviceKey[i];
  flipped[kSessionKeyBytes - 1] ^= 0x01;
  assert(!SessionKeySchedule::constantTimeEquals(deviceKey, flipped, kSessionKeyBytes));

  return 0;
}
