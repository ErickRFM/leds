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
    if (!input.component ||
        (std::strcmp(input.component,"dht11") && std::strcmp(input.component,"ldr_light")) ||
        input.from > 39 || input.to > 39) return BlePinApplyResult::Rejected;
    const uint8_t from[ComponentApplyReplayGuard::kComponentSignalCount] = {input.from};
    const uint8_t to[ComponentApplyReplayGuard::kComponentSignalCount] = {input.to};
    const ComponentApplyReplayGuard::Request request = {
        input.token,input.component,input.from,0,input.to,0};
    const auto verdict=ledger_.inspect(request);
    if (verdict==ComponentApplyReplayGuard::Verdict::IdenticalRetry) return BlePinApplyResult::Replayed;
    if (verdict!=ComponentApplyReplayGuard::Verdict::New) return BlePinApplyResult::Rejected;
    if (busy || !callback_) return BlePinApplyResult::Busy;
    if (!callback_(input.component,from,to,context_)) return BlePinApplyResult::Rejected;
    return ledger_.commit(request) ? BlePinApplyResult::Applied : BlePinApplyResult::Rejected;
  }
 private:
  Apply callback_=nullptr;
  void* context_=nullptr;
  ComponentApplyReplayGuard ledger_;
};
} // namespace esp32core
