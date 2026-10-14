#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>
#include "PinManager.h"

namespace esp32core {

// Controlled lifecycle contract, NOT process/memory isolation. A module must
// release every pin it acquired if begin() fails.
class IModule {
 public:
  virtual ~IModule() = default;
  virtual const char* id() const = 0;
  virtual uint16_t version() const = 0;
  virtual bool begin(PinManager& pins) = 0;
  virtual void tick(uint32_t nowMs) = 0;
  virtual void stop(PinManager& pins) = 0;
};

class ModuleRegistry final {
 public:
  static constexpr size_t kMaxModules = 8;

  bool add(IModule& module) {
    if (started_ || !module.id() || !module.id()[0] || size_ >= kMaxModules) return false;
    for (size_t i = 0; i < size_; ++i) {
      if (std::strcmp(modules_[i]->id(), module.id()) == 0) return false;
    }
    modules_[size_++] = &module;
    return true;
  }

  bool startAll(PinManager& pins) {
    if (started_ || size_ == 0) return false;
    for (size_t i = 0; i < size_; ++i) {
      if (!modules_[i]->begin(pins)) {
        // begin() is responsible for releasing its own partial claims.
        while (i > 0) modules_[--i]->stop(pins);
        return false;
      }
    }
    started_ = true;
    return true;
  }

  void tickAll(uint32_t nowMs) {
    if (!started_) return;
    for (size_t i = 0; i < size_; ++i) modules_[i]->tick(nowMs);
  }

  void stopAll(PinManager& pins) {
    if (!started_) return;
    for (size_t i = size_; i > 0; --i) modules_[i - 1]->stop(pins);
    started_ = false;
  }

  // Alias used by the sketch's declarative registration path. Returns false
  // for a null pointer or a duplicate id; never throws.
  bool registerModule(IModule* module) { return module != nullptr && add(*module); }

  const IModule* find(const char* id) const {
    if (!id) return nullptr;
    for (size_t i = 0; i < size_; ++i) {
      if (std::strcmp(modules_[i]->id(), id) == 0) return modules_[i];
    }
    return nullptr;
  }

  bool started() const { return started_; }
  size_t size() const { return size_; }
  const IModule* at(size_t index) const { return index < size_ ? modules_[index] : nullptr; }

 private:
  IModule* modules_[kMaxModules] = {};
  size_t size_ = 0;
  bool started_ = false;
};
}  // namespace esp32core
