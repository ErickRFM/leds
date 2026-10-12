#pragma once
#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <Preferences.h>
#include <esp_system.h>
#include <esp_ota_ops.h>
#include <esp_partition.h>
#include "PinManager.h"
#include "ExperimentRuntime.h"
#include "RgbScriptRuntime.h"
#include "RgbCommandCodec.h"
#include "GpioTelemetryCodec.h"
#include "CompiCapabilities.h"

namespace esp32core {

// Laboratory-only local control. NOT a production security design:
// HTTP is allowed ONLY on a randomly password-protected ESP32 SoftAP,
// NEVER through the station interface or Internet. An independent admin
// secret is required for all endpoints. Wi-Fi credentials still transit HTTP
// over the WPA2 protected AP; do not use on an untrusted/shared network.
class LocalWifiControl final {
 public:
  using RgbState = String (*)();
  using RgbCommand = bool (*)(const String&);
  using DeviceId = String (*)();

  LocalWifiControl(PinManager& pins, RgbState state, RgbCommand command,
                   DeviceId deviceId)
      : pins_(pins), rgbState_(state), rgbCommand_(command), deviceId_(deviceId) {}

  void begin() {
    prefs_.begin("rgb-lab-v2", false);
    String password = prefs_.getString("ap", "");
    String secret = prefs_.getString("key", "");
    if (password.length() != 16) {
      password = randomKey();
      prefs_.putString("ap", password);
    }
    if (secret.length() != 16) {
      secret = randomKey();
      prefs_.putString("key", secret);
    }
    accessKey_ = secret;
    // AP+STA leaves the provisioning Wi-Fi active while connecting to a
    // user's router; all HTTP traffic is still rejected on STA interfaces.
    WiFi.mode(WIFI_AP_STA);
    WiFi.persistent(true); // Wi-Fi STA credentials remain in NVS across reboot.
    WiFi.setAutoReconnect(true);
    if (!WiFi.softAP("ESP32-Control-Lab", password.c_str())) {
      Serial.println("ERROR: protected lab SoftAP failed; HTTP disabled");
      return;
    }
    Serial.println("=== ESP32 LAB ACCESS (PHYSICAL SERIAL ONLY) ===");
    Serial.print("SoftAP: ESP32-Control-Lab / password: ");
    Serial.println(password);
    Serial.print("App pairing key (not Wi-Fi password): ");
    Serial.println(accessKey_);
    Serial.println("Use only for local bench testing; no TLS / no WAN.");
    // Try any previously provisioned station credentials. This must not
    // block the protected SoftAP or legacy BLE initialization.
    WiFi.begin();
    server_.on("/v2/status", HTTP_GET, [this]() {
      if (!authorized()) return;
      String json = "{\"version\":2,\"uptimeMs\":" + String(millis()) +
        ",\"freeHeap\":" + String(ESP.getFreeHeap()) +
        ",\"wifiConnected\":" + String(WiFi.status() == WL_CONNECTED ? "true":"false") +
        ",\"ssid\":\"" + escape(WiFi.SSID()) +
        "\",\"stationIp\":\"" + WiFi.localIP().toString() +
        "\",\"rgb\":\"" + escape(rgbState_()) +
        "\",\"leasedPins\":" + String(pins_.leasedCount()) +
        ",\"experimentActive\":" + String(experiment_.active() ? "true":"false") + "}";
      server_.send(200, "application/json", json);
    });
    // Read-only, authenticated and bound to the local lab SoftAP interface.
    // No MAC, SSID, keys or credentials are included in this diagnostic payload.
    server_.on("/v2/diagnostics", HTTP_GET, [this]() {
      if (!authorized()) return;
      const int32_t signal = WiFi.status() == WL_CONNECTED ? WiFi.RSSI() : 0;
      String body = "{\"version\":2,\"chip\":\"" + escape(ESP.getChipModel()) + "\"";
      body += ",\"chipRevision\":" + String(ESP.getChipRevision());
      body += ",\"flashBytes\":" + String(ESP.getFlashChipSize());
      body += ",\"freeHeap\":" + String(ESP.getFreeHeap());
      body += ",\"minFreeHeap\":" + String(ESP.getMinFreeHeap());
      body += ",\"uptimeMs\":" + String(millis());
      body += ",\"resetReason\":" + String(static_cast<int>(esp_reset_reason()));
      body += ",\"wifiRssi\":" + String(signal);
      body += ",\"leasedPins\":" + String(pins_.leasedCount()) + "}";
      server_.send(200, "application/json", body);
    });
    // A non-secret NVS-stable identifier for compatibility/migration diagnostics.
    // This endpoint is behind existing SoftAP-only lab authorization. It NEVER
    // grants ownership or announces that BLE control is protected.
    server_.on("/v2/identity", HTTP_GET, [this]() {
      if (!authorized()) return;
      String body = "{\"schemaVersion\":4,\"deviceId\":\"" +
        deviceId_() + "\",\"secureProvisioning\":false," +
        "\"legacyBleWriteUnauthenticated\":true," +
        "\"ownerBound\":false}";
      server_.send(200, "application/json", body);
    });
    // Versioned, truthful feature negotiation. This is diagnostic metadata,
    // NOT evidence of ownership or secure BLE authorization.
    server_.on("/v2/capabilities", HTTP_GET, [this]() {
      if (!authorized()) return;
      const uint32_t features =
          capabilityBit(Capability::Rgb) |
          capabilityBit(Capability::Effects) |
          capabilityBit(Capability::Gpio) |
          capabilityBit(Capability::Adc) |
          capabilityBit(Capability::Sequences) |
          capabilityBit(Capability::Diagnostics);
      // In particular: do not advertise SecurePairing, FirmwareUpdate,
      // Automation or SerialMonitor before the corresponding gates pass.
      const BoardProfile profile = {1, 1, ESP.getFlashChipSize(), features, 0};
      if (!validProfile(profile)) { jsonError(500, "invalid_capability_profile"); return; }
      String body = "{\"protocolMajor\":" + String(profile.protocolMajor);
      body += ",\"protocolMinor\":" + String(profile.protocolMinor);
      body += ",\"flashBytes\":" + String(profile.flashBytes);
      body += ",\"capabilityBits\":" + String(profile.advertisedCapabilities);
      body += ",\"secureBleWrites\":false,\"otaUploadEnabled\":false";
      body += ",\"runtime\":\"lab\"}";
      server_.send(200, "application/json", body);
    });
    server_.on("/v2/firmware", HTTP_GET, [this]() {
      if (!authorized()) return;
      const esp_partition_t* current = esp_ota_get_running_partition();
      const esp_partition_t* next = esp_ota_get_next_update_partition(nullptr);
      String body = "{\"firmwareVersion\":\"1.2.0-lab\",";
      body += "\"activePartition\":\"";
      body += current ? escape(String(current->label)) : "unknown";
      body += "\",\"activeSize\":";
      body += current ? String(current->size) : "0";
      body += ",\"nextPartition\":\"";
      body += next ? escape(String(next->label)) : "none";
      body += "\",\"nextSize\":";
      body += next ? String(next->size) : "0";
      body += ",\"otaSlotPresent\":";
      body += next ? "true" : "false";
      body += ",\"otaUploadEnabled\":false}";
      server_.send(200, "application/json", body);
    });
    server_.on("/v2/wifi/scan", HTTP_GET, [this]() {
      if (!authorized()) return;
      int count = WiFi.scanNetworks(false, true);
      if (count < 0) { jsonError(503, "scan_failed"); return; }
      String body = "{\"networks\":[";
      for (int i = 0; i < count && i < 24; ++i) {
        if (i) body += ",";
        body += "{\"ssid\":\"" + escape(WiFi.SSID(i)) + "\",\"rssi\":" +
          String(WiFi.RSSI(i)) + ",\"secure\":" +
          String(WiFi.encryptionType(i) == WIFI_AUTH_OPEN ? "false" : "true") + "}";
      }
      body += "]}";
      WiFi.scanDelete();
      server_.send(200, "application/json", body);
    });
    server_.on("/v2/wifi/connect", HTTP_POST, [this]() {
      if (!authorized()) return;
      String ssid = server_.arg("ssid");
      String password = server_.arg("password");
      if (ssid.isEmpty() || ssid.length() > 32 ||
          (!password.isEmpty() && (password.length() < 8 || password.length() > 63))) {
        jsonError(400, "invalid_wifi_credentials"); return;
      }
      // Never log or echo submitted router credentials.
      WiFi.begin(ssid.c_str(), password.c_str());
      server_.send(202, "application/json", "{\"status\":\"connecting\"}");
    });
    // Live firmware resource ownership; this does NOT validate physical wiring.
    // Authenticated laboratory SoftAP only. Do not expose unsafe BLE GPIO writes.
    server_.on("/v2/gpio/status", HTTP_GET, [this]() {
      if (!authorized()) return;
      static constexpr uint8_t reported[] = {18,19,21,22,23,25,26,27,32,33,34,35,36,39};
      String body = "{\"version\":1,\"source\":\"firmware-runtime\",\"uptimeMs\":" +
                    String(millis()) + ",\"pins\":[";
      for (size_t i = 0; i < sizeof(reported); ++i) {
        const uint8_t pin = reported[i];
        const PinLease* lease = pins_.lease(pin);
        const char* owner = "FREE";
        const char* mode = "NONE";
        if (lease) {
          if (std::strcmp(lease->owner, "led.rgb") == 0) owner = "RGB";
          else if (std::strcmp(lease->owner, kManual) == 0) owner = "MANUAL";
          else if (std::strcmp(lease->owner, "lab.experiment") == 0) owner = "EXPERIMENT";
          else if (std::strcmp(lease->owner, kRead) == 0) owner = "READ";
          else owner = "OTHER";
          switch (lease->function) {
            case PinFunction::DigitalInput: mode = "INPUT"; break;
            case PinFunction::DigitalOutput: mode = "OUTPUT"; break;
            case PinFunction::PwmOutput: mode = "PWM"; break;
            case PinFunction::AnalogInput: mode = "ADC"; break;
          }
        }
        if (i) body += ",";
        body += "{\"pin\":" + String(pin) + ",\"owner\":\"" + owner +
                "\",\"mode\":\"" + mode + "\",\"level\":";
        if (lease && std::strcmp(lease->owner, kManual) == 0) {
          body += String(manualLevels_[pin] ? 1 : 0);
        } else body += "null";
        body += "}";
      }
      body += "]}";
      server_.send(200, "application/json", body);
    });
    server_.on("/v2/gpio/write", HTTP_POST, [this]() {
      if (!authorized()) return;
      int pin = -1, value = -1;
      if (!parseInt("pin", 0, 39, pin) || !parseInt("value", 0, 1, value) ||
          !PinManager::canUse(pin, PinFunction::DigitalOutput)) {
        jsonError(400, "invalid_gpio"); return;
      }
      // Restrict experimental outputs further until the exact ESP32 module
      // and PSRAM/peripheral pin assignments are confirmed on the bench.
      if (!manualOutputAllowed(pin)) {
        jsonError(403, "gpio_not_approved"); return;
      }
      if (!pins_.belongsTo(pin, kManual)) {
        if (!pins_.acquire(pin, kManual, PinFunction::DigitalOutput)) {
          jsonError(409, "pin_reserved"); return;
        }
        pinMode(pin, OUTPUT);
      }
      digitalWrite(pin, value ? HIGH : LOW);
      manualTouched_ = millis();
      manualPins_[pin] = true;
      manualLevels_[pin] = value != 0;
      server_.send(200, "application/json", "{\"status\":\"APPLIED\"}");
    });
    server_.on("/v2/gpio/read", HTTP_GET, [this]() {
      if (!authorized()) return;
      int pin = -1;
      if (!parseInt("pin", 0, 39, pin) ||
          !PinManager::canUse(pin, PinFunction::DigitalInput)) {
        jsonError(400, "invalid_gpio"); return;
      }
      bool temporarilyOwned = pins_.acquire(pin, kRead, PinFunction::DigitalInput);
      if (!temporarilyOwned && !pins_.belongsTo(pin, kManual)) {
        jsonError(409, "pin_reserved"); return;
      }
      if (temporarilyOwned) pinMode(pin, INPUT);
      int value = digitalRead(pin);
      if (temporarilyOwned) pins_.release(pin, kRead);
      server_.send(200, "application/json", "{\"pin\":" + String(pin) +
                   ",\"value\":" + String(value) + "}");
    });
    server_.on("/v2/adc/read", HTTP_GET, [this]() {
      if (!authorized()) return;
      int pin = -1;
      if (!parseInt("pin", 0, 39, pin) ||
          !PinManager::canUse(pin, PinFunction::AnalogInput)) {
        jsonError(400, "invalid_adc"); return;
      }
      if (!pins_.acquire(pin, kRead, PinFunction::AnalogInput)) {
        jsonError(409, "pin_reserved"); return;
      }
      int raw = analogRead(pin);
      pins_.release(pin, kRead);
      server_.send(200, "application/json", "{\"pin\":" + String(pin) +
                   ",\"raw\":" + String(raw) + "}");
    });
    server_.on("/v2/gpio/reset", HTTP_POST, [this]() {
      if (!authorized()) return;
      releaseManual();
      server_.send(200, "application/json", "{\"status\":\"RESET\"}");
    });
    server_.on("/v2/lab/blink", HTTP_POST, [this]() {
      if (!authorized()) return;
      int pin = -1, interval = -1, count = -1;
      if (!parseInt("pin", 0, 39, pin) ||
          !parseInt("intervalMs", 100, 2000, interval) ||
          !parseInt("cycles", 1, 20, count)) {
        jsonError(400, "invalid_experiment"); return;
      }
      if (!manualOutputAllowed(pin) || !experiment_.start(pins_, pin, interval, count, millis())) {
        jsonError(409, "experiment_conflict"); return;
      }
      server_.send(202, "application/json", "{\"status\":\"RUNNING\"}");
    });
    server_.on("/v2/lab/sequence", HTTP_POST, [this]() {
      if (!authorized()) return;
      int pin = -1;
      if (!parseInt("pin", 0, 39, pin) || !manualOutputAllowed(pin)) {
        jsonError(400, "invalid_lab_pin"); return;
      }
      String input = server_.arg("steps");
      if (input.isEmpty() || input.length() > 120 || input.endsWith(",")) {
        jsonError(400, "invalid_lab_steps"); return;
      }
      ExperimentRuntime::Step steps[ExperimentRuntime::kMaxSequenceSteps] = {};
      size_t count = 0;
      size_t cursor = 0;
      bool valid = true;
      while (cursor < input.length()) {
        if (count == ExperimentRuntime::kMaxSequenceSteps) { valid = false; break; }
        int comma = input.indexOf(',', cursor);
        if (comma < 0) comma = static_cast<int>(input.length());
        String part = input.substring(cursor, comma);
        if (part.length() < 5 || part.length() > 6 || part[1] != ':' ||
            (part[0] != '1' && part[0] != '0')) { valid = false; break; }
        String durationText = part.substring(2);
        int duration = 0;
        if (!decimal(durationText, 100, 2000, duration)) { valid = false; break; }
        steps[count++] = {part[0] == '1', static_cast<uint16_t>(duration)};
        cursor = static_cast<size_t>(comma) + 1;
      }
      if (!valid || count == 0) { jsonError(400, "invalid_lab_steps"); return; }
      if (!experiment_.startSequence(pins_, pin, steps, count, millis())) {
        jsonError(409, "lab_resource_or_time_limit"); return;
      }
      server_.send(202, "application/json", "{\"status\":\"RUNNING\"}");
    });
    server_.on("/v2/lab/stop", HTTP_POST, [this]() {
      if (!authorized()) return;
      experiment_.stop(pins_);
      server_.send(200, "application/json", "{\"status\":\"STOPPED\"}");
    });
    // V4 bounded RGB scripts: no native code, direct GPIO or firmware writes.
    // Lab SoftAP only. A physical/manual RGB command interrupts the script.
    server_.on("/v2/script/status", HTTP_GET, [this]() {
      if (!authorized()) return;
      server_.send(200, "application/json",
        "{\"active\":" + String(script_.active() ? "true" : "false") +
        ",\"step\":" + String(script_.instructionIndex()) +
        ",\"total\":" + String(script_.instructionCount()) + "}");
    });
    server_.on("/v2/script/run", HTTP_POST, [this]() {
      if (!authorized()) return;
      if (script_.active() || experiment_.active()) {
        jsonError(409, "script_or_gpio_experiment_active"); return;
      }
      const String source = server_.arg("source");
      if (source.isEmpty() || source.length() > RgbScriptRuntime::kMaxSourceBytes ||
          !script_.start(source.c_str(), millis())) {
        jsonError(400, "invalid_bounded_rgb_script"); return;
      }
      const bool applied = script_.tick(millis(), sendScriptRgb, this);
      if (!applied) {
        jsonError(409, "script_command_rejected"); return;
      }
      server_.send(202, "application/json", "{\"status\":\"ACCEPTED\"}");
    });
    server_.on("/v2/script/stop", HTTP_POST, [this]() {
      if (!authorized()) return;
      script_.stop(); // Leave the last RGB state, never stop BLE or Wi-Fi.
      server_.send(200, "application/json", "{\"status\":\"STOPPED\"}");
    });
    server_.on("/v2/led/command", HTTP_POST, [this]() {
      if (!authorized()) return;
      String cmd = server_.arg("command");
      if (cmd.isEmpty() || cmd.length() > 48) {
        jsonError(400, "invalid_rgb_command"); return;
      }
      esp32core::RgbCommand parsed{};
      if (!esp32core::parseRgbCommand(cmd.c_str(), parsed)) {
        jsonError(400, "invalid_rgb_command"); return;
      }
      // Invalid commands, including malformed FX/SET, must not cancel scripts.
      if (parsed.action != esp32core::RgbAction::Get) {
        script_.stop(); // A VALID manual RGB operation takes priority.
      }
      if (!rgbCommand_(cmd)) {
        jsonError(409, "rgb_command_not_applied"); return;
      }
      server_.send(200, "application/json", "{\"status\":\"APPLIED\"}");
    });
    server_.onNotFound([this]() {
      if (!authorized()) return;
      jsonError(404, "not_found");
    });
    const char* acceptedHeaders[] = {"X-Device-Key"};
    server_.collectHeaders(acceptedHeaders, 1);
    server_.begin();
    active_ = true;
    Serial.println("ESP32 local lab API ready at http://192.168.4.1");
  }

  void tick(uint32_t nowMs) {
    if (!active_) return;
    server_.handleClient();
    script_.tick(nowMs, sendScriptRgb, this);
    experiment_.tick(pins_, nowMs);
    if (hasManual() && static_cast<uint32_t>(nowMs - manualTouched_) >= 30000U) {
      releaseManual();  // Fail-safe bench timeout for manually controlled pins.
    }
  }

  void stopScriptFromBle() { script_.stop(); }

  /** Compact read-only BLE GPIO snapshot, computed on Arduino loop only. */
  bool writeBlePinSnapshot(char (&output)[96]) const {
    return GpioTelemetryCodec::encode(pins_, manualLevels_, output, sizeof(output));
  }

 private:
  static constexpr const char* kManual = "gpio.manual";
  static constexpr const char* kRead = "gpio.read";
  static bool sendScriptRgb(const char* command, void* context) {
    auto* self = static_cast<LocalWifiControl*>(context);
    return self != nullptr && self->rgbCommand_(String(command));
  }

  static constexpr bool manualOutputAllowed(int pin) {
    return pin == 18 || pin == 19 || pin == 21 || pin == 22 || pin == 23;
  }
  PinManager& pins_;
  RgbState rgbState_;
  RgbCommand rgbCommand_;
  RgbScriptRuntime script_;
  DeviceId deviceId_;
  Preferences prefs_;
  WebServer server_{80};
  ExperimentRuntime experiment_;
  String accessKey_;
  bool manualPins_[PinManager::kPinCount] = {};
  bool manualLevels_[PinManager::kPinCount] = {};
  uint32_t manualTouched_ = 0;
  bool active_ = false;

  static String randomKey() {
    char result[17] = {};
    snprintf(result, sizeof(result), "%08lx%08lx",
             static_cast<unsigned long>(esp_random()),
             static_cast<unsigned long>(esp_random()));
    return String(result);
  }
  static String escape(const String& s) {
    String out;
    for (size_t i = 0; i < s.length() && i < 128; ++i) {
      unsigned char c = static_cast<unsigned char>(s[i]);
      if (c == '"' || c == '\\') { out += '\\'; out += char(c); }
      else if (c < 0x20) out += ' ';
      else out += char(c);
    }
    return out;
  }
  bool authorized() {
    // Both remote subnet AND local interface must be AP, not station.
    if (server_.client().localIP() != WiFi.softAPIP() ||
        server_.header("X-Device-Key") != accessKey_) {
      jsonError(403, "forbidden");
      return false;
    }
    return true;
  }
  void jsonError(int status, const char* code) {
    server_.send(status, "application/json",
                 String("{\"error\":\"") + code + "\"}");
  }
  static bool decimal(const String& text, int min, int max, int& out) {
    if (text.isEmpty() || text.length() > 4) return false;
    for (size_t i = 0; i < text.length(); ++i) {
      if (text[i] < '0' || text[i] > '9') return false;
    }
    int value = text.toInt();
    if (value < min || value > max) return false;
    out = value;
    return true;
  }

  bool parseInt(const char* name, int min, int max, int& out) {
    String s = server_.arg(name);
    if (s.isEmpty() || s.length() > 4) return false;
    for (size_t i = 0; i < s.length(); ++i) {
      if (s[i] < '0' || s[i] > '9') return false;
    }
    int value = s.toInt();
    if (value < min || value > max) return false;
    out = value;
    return true;
  }
  bool hasManual() const {
    for (bool claimed : manualPins_) if (claimed) return true;
    return false;
  }
  void releaseManual() {
    for (uint8_t pin = 0; pin < PinManager::kPinCount; ++pin) {
      if (manualPins_[pin]) {
        digitalWrite(pin, LOW);
        pinMode(pin, INPUT);
        pins_.release(pin, kManual);
        manualPins_[pin] = false;
        manualLevels_[pin] = false;
      }
    }
  }
};
} // namespace esp32core
