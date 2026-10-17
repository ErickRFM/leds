#pragma once
// Host-only double for the ESP32 Preferences API, used to reproduce the
// first-install case: opening a nonexistent namespace read-only fails.
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <map>
#include <string>
#include <vector>

class Preferences {
 public:
  static bool failOpen;

  bool begin(const char* name, bool readOnly = false) {
    if (failOpen || name == nullptr) return false;
    const std::string namespaceName(name);
    auto& all = namespaces();
    if (readOnly && all.find(namespaceName) == all.end()) return false;
    if (!readOnly) all[namespaceName];
    namespaceName_ = namespaceName;
    opened_ = true;
    readOnly_ = readOnly;
    return true;
  }
  void end() { opened_ = false; }
  size_t getBytesLength(const char* key) {
    if (!opened_) return 0;
    const auto& records = namespaces()[namespaceName_];
    const auto it = records.find(key);
    return it == records.end() ? 0 : it->second.size();
  }
  size_t getBytes(const char* key, void* dest, size_t capacity) {
    if (!opened_ || dest == nullptr) return 0;
    const auto& records = namespaces()[namespaceName_];
    const auto it = records.find(key);
    if (it == records.end() || it->second.size() > capacity) return 0;
    std::memcpy(dest, it->second.data(), it->second.size());
    return it->second.size();
  }
  size_t putBytes(const char* key, const void* source, size_t count) {
    if (!opened_ || readOnly_ || source == nullptr) return 0;
    const auto* data = static_cast<const uint8_t*>(source);
    namespaces()[namespaceName_][key] = std::vector<uint8_t>(data, data + count);
    return count;
  }
  bool remove(const char* key) {
    return opened_ && !readOnly_ && namespaces()[namespaceName_].erase(key) != 0;
  }

  static void reset() { namespaces().clear(); failOpen = false; }
  static bool exists(const std::string& name) {
    return namespaces().find(name) != namespaces().end();
  }
  static void corrupt(const std::string& ns, const std::string& key) {
    namespaces()[ns][key] = {0x12, 0x34};
  }

 private:
  using RecordMap = std::map<std::string, std::vector<uint8_t>>;
  static std::map<std::string, RecordMap>& namespaces() {
    static std::map<std::string, RecordMap> all;
    return all;
  }
  std::string namespaceName_;
  bool opened_ = false;
  bool readOnly_ = false;
};
inline bool Preferences::failOpen = false;
