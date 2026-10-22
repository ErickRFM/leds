#include "../firmware/esp32_rgb/core/CompiOtaPreflight.h"
#include <cassert>
using namespace esp32core;
int main() {
  constexpr OtaTarget target{32, 1, 1900000, true};
  static_assert(checkOtaCandidate({32,1,900000,true,true},target)==OtaDecision::AllowPreflight);
  assert(checkOtaCandidate({0,1,900000,true,true},target)==OtaDecision::InvalidMetadata);
  assert(checkOtaCandidate({3,1,900000,true,true},target)==OtaDecision::WrongBoard);
  assert(checkOtaCandidate({32,2,900000,true,true},target)==OtaDecision::ProtocolMismatch);
  assert(checkOtaCandidate({32,1,2000000,true,true},target)==OtaDecision::TooLarge);
  assert(checkOtaCandidate({32,1,900000,false,true},target)==OtaDecision::MissingSignatureVerification);
  assert(checkOtaCandidate({32,1,900000,true,true},{32,1,1900000,false})==OtaDecision::RollbackNotSupported);
}
