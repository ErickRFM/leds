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

  LocalWifiControl(PinManager& pins, RgbState state, RgbCommand command)
      : pins_(pins), rgbState_(state), rgbCommand_(command) {}

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
    server_.on("/v2/lab/stop", HTTP_POST, [this]() {
      if (!authorized()) return;
      experiment_.stop(pins_);
      server_.send(200, "application/json", "{\"status\":\"STOPPED\"}");
    });
    server_.on("/v2/led/command", HTTP_POST, [this]() {
      if (!authorized()) return;
      String cmd = server_.arg("command");
      if (cmd.isEmpty() || cmd.length() > 48 || !rgbCommand_(cmd)) {
        jsonError(400, "invalid_rgb_command"); return;
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
    experiment_.tick(pins_, nowMs);
    if (hasManual() && static_cast<uint32_t>(nowMs - manualTouched_) >= 30000U) {
      releaseManual();  // Fail-safe bench timeout for manually controlled pins.
    }
  }

 private:
  static constexpr const char* kManual = "gpio.manual";
  static constexpr const char* kRead = "gpio.read";
  static constexpr bool manualOutputAllowed(int pin) {
    return pin == 18 || pin == 19 || pin == 21 || pin == 22 || pin == 23;
  }
  PinManager& pins_;
  RgbState rgbState_;
  RgbCommand rgbCommand_;
  Preferences prefs_;
  WebServer server_{80};
  ExperimentRuntime experiment_;
  String accessKey_;
  bool manualPins_[PinManager::kPinCount] = {};
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
      }
    }
  }
};
} // namespace esp32core
