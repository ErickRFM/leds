#pragma once
#include <cstdint>
namespace esp32core {
enum class OtaDecision : uint8_t {
  AllowPreflight, InvalidMetadata, WrongBoard, ProtocolMismatch,
  TooLarge, MissingSignatureVerification, RollbackNotSupported
};
struct OtaCandidate {
  uint32_t boardId;
  uint16_t protocolMajor;
  uint32_t imageBytes;
  bool signatureVerified;
  bool rollbackRequired;
};
struct OtaTarget {
  uint32_t boardId;
  uint16_t protocolMajor;
  uint32_t nextSlotBytes;
  bool rollbackSupported;
};
// Pure preflight AFTER an independent cryptographic verifier completes.
// Does not verify signatures or write firmware, and never enables OTA.
constexpr OtaDecision checkOtaCandidate(OtaCandidate c, OtaTarget t) {
  if (!c.boardId || !t.boardId || !c.protocolMajor || !t.protocolMajor ||
      !c.imageBytes || !t.nextSlotBytes) return OtaDecision::InvalidMetadata;
  if (c.boardId != t.boardId) return OtaDecision::WrongBoard;
  if (c.protocolMajor != t.protocolMajor) return OtaDecision::ProtocolMismatch;
  if (c.imageBytes > t.nextSlotBytes) return OtaDecision::TooLarge;
  if (!c.signatureVerified) return OtaDecision::MissingSignatureVerification;
  if (c.rollbackRequired && !t.rollbackSupported) return OtaDecision::RollbackNotSupported;
  return OtaDecision::AllowPreflight;
}
} // namespace esp32core
