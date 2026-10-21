#pragma once

#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>

namespace esp32core {

// An explicit client request sequence binds the reply to its request.
// Strict parser rejects suffixes, empty sequences and overflow.
inline bool parseBleSnapshotRequest(const char* input, char& kind, uint16_t& sequence) {
  if (!input || std::strncmp(input, "SNP,", 4) != 0 ||
      (input[4] != 'S' && input[4] != 'T') || input[5] != ',') return false;
  uint32_t value = 0;
  size_t digits = 0;
  const char* ptr = input + 6;
  while (*ptr) {
    if (*ptr < '0' || *ptr > '9' || ++digits > 5) return false;
    value = value * 10u + static_cast<uint32_t>(*ptr - '0');
    ++ptr;
  }
  if (digits == 0 || value == 0 || value > 65535u) return false;
  kind = input[4];
  sequence = static_cast<uint16_t>(value);
  return true;
}

// Read-only, bounded snapshots on the existing STATE notification characteristic.
// 38 ASCII JSON bytes + framing stay under an ATT payload of 61 (MTU 64).
// No dynamic allocations, delays or GPIO access in this transport.
class BleSnapshotFramer final {
 public:
  static constexpr size_t kPayloadMax = 1536;
  static constexpr size_t kChunk = 38;
  static constexpr size_t kMaxParts = (kPayloadMax + kChunk - 1) / kChunk;
  static constexpr size_t kFrameCapacity = 64;
  enum class Kind : char { Status = 'S', Telemetry = 'T' };
  using Output = void (*)(const char* frame, void* ctx);

  bool start(Kind kind, const char* json, uint16_t seq) {
    if (active_ || !json) return false;
    const size_t size = boundedLength(json, kPayloadMax);
    if (size == 0 || size > kPayloadMax) return false;
    for (size_t i = 0; i < size; ++i) {
      // Encoders emit ASCII JSON; no multiline data or delimiters are trusted.
      if (static_cast<unsigned char>(json[i]) < 32 ||
          static_cast<unsigned char>(json[i]) > 126) return false;
    }
    std::memcpy(payload_, json, size);
    payload_[size] = '\0';
    count_ = static_cast<uint8_t>((size + kChunk - 1) / kChunk);
    size_ = size;
    seq_ = seq;
    kind_ = static_cast<char>(kind);
    index_ = 0;
    stage_ = Stage::Begin;
    return true;
  }

  bool active() const { return stage_ != Stage::Idle; }
  void cancel() {
    stage_ = Stage::Idle;
    size_ = 0;
    count_ = 0;
    index_ = 0;
    payload_[0] = '\0';
  }

  // Exactly ONE bounded notification per tick; no blocking delays.
  bool tick(Output emit, void* ctx) {
    if (!active() || !emit) return false;
    char frame[kFrameCapacity] = {};
    int n = 0;
    switch (stage_) {
      case Stage::Begin:
        n = std::snprintf(frame, sizeof(frame), "B,%c,%u,%u",
                          kind_, static_cast<unsigned>(seq_),
                          static_cast<unsigned>(count_));
        stage_ = Stage::Data;
        break;
      case Stage::Data: {
        const size_t offset = static_cast<size_t>(index_) * kChunk;
        const size_t length = size_ - offset < kChunk
            ? size_ - offset : kChunk;
        n = std::snprintf(frame, sizeof(frame), "D,%c,%u,%u,%.*s",
                          kind_, static_cast<unsigned>(seq_),
                          static_cast<unsigned>(index_),
                          static_cast<int>(length), payload_ + offset);
        ++index_;
        if (index_ == count_) stage_ = Stage::End;
        break;
      }
      case Stage::End:
        n = std::snprintf(frame, sizeof(frame), "E,%c,%u",
                          kind_, static_cast<unsigned>(seq_));
        cancel();
        break;
      case Stage::Idle: return false;
    }
    if (n < 0 || static_cast<size_t>(n) >= sizeof(frame) ||
        static_cast<size_t>(n) > 61) {
      cancel();
      return false;
    }
    emit(frame, ctx);
    return true;
  }

 private:
  enum class Stage { Idle, Begin, Data, End };
  static size_t boundedLength(const char* input, size_t max) {
    size_t i = 0;
    while (i <= max && input[i]) ++i;
    return i;
  }
  char payload_[kPayloadMax + 1] = {};
  size_t size_ = 0;
  uint16_t seq_ = 0;
  uint8_t count_ = 0;
  uint8_t index_ = 0;
  char kind_ = 'S';
  Stage stage_ = Stage::Idle;
};

}  // namespace esp32core
