#pragma once
#include "BleSensorPinRequest.h"
#include "ComponentApplyReplayGuard.h"

namespace esp32core {
enum class BlePinApplyResult { Applied, Replayed, Busy, Rejected };

// Separate BLE nonce namespace; physical mutations still use the single
// firmware component callback shared with authenticated HTTP.
class BleSensorPinControl final {
 public:
  using Apply = bool (*)(const char*, const uint8_t* from, const uint8_t* to, void*);
  void setApply(Apply callback,void* context) {callback_=callback;context_=context;}
  BlePinApplyResult apply(const BleSensorPinRequest& input,bool busy) {
    if (!input.component) return BlePinApplyResult::Rejected;
    const uint8_t from[ComponentApplyReplayGuard::kComponentSignalCount] = {input.from};
    const uint8_t to[ComponentApplyReplayGuard::kComponentSignalCount] = {input.to};
    return apply(input.token,input.component,from,to,busy);
  }
  // robotics_crane and future multi-signal components ride the same
  // transaction: replay ledger, single mutation callback and CAS from-pins.
  BlePinApplyResult apply(const BleComponentPinRequest& input,bool busy) {
    if (!input.component) return BlePinApplyResult::Rejected;
    return apply(input.token,input.component,input.from,input.to,busy);
  }
 private:
  BlePinApplyResult apply(const char* token,const char* component,
                          const uint8_t* from,const uint8_t* to,bool busy) {
    if (!component) return BlePinApplyResult::Rejected;
    // Crane joins the whitelist now that its CAS transaction carries all
    // seven signals; sensors keep their single-signal frame format.
    const bool crane = !std::strcmp(component,"robotics_crane");
    if (!crane && std::strcmp(component,"dht11") &&
        std::strcmp(component,"ldr_light")) return BlePinApplyResult::Rejected;
    if (!ComponentApplyReplayGuard::validRequestId(token))
      return BlePinApplyResult::Rejected;
    ComponentApplyReplayGuard::Request request{token,component,
        from[0],to[0],from[1],to[1]};
    request.from3=from[2]; request.to3=to[2];
    request.from4=from[3]; request.to4=to[3];
    request.from5=from[4]; request.to5=to[4];
    request.from6=from[5]; request.to6=to[5];
    request.from7=from[6]; request.to7=to[6];
    const auto verdict=ledger_.inspect(request);
    if (verdict==ComponentApplyReplayGuard::Verdict::IdenticalRetry) return BlePinApplyResult::Replayed;
    if (verdict!=ComponentApplyReplayGuard::Verdict::New) return BlePinApplyResult::Rejected;
    if (busy || !callback_) return BlePinApplyResult::Busy;
    if (!callback_(component,from,to,context_)) return BlePinApplyResult::Rejected;
    return ledger_.commit(request) ? BlePinApplyResult::Applied : BlePinApplyResult::Rejected;
  }
  Apply callback_=nullptr;
  void* context_=nullptr;
  ComponentApplyReplayGuard ledger_;
};
} // namespace esp32core
