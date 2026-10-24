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
#include "RgbAutomationClock.h"
#include "ComponentCatalog.h"
#include "ComponentApplyReplayGuard.h"
#include "MediaControlBackend.h"
#include "BleSensorPinControl.h"
#include "EnvironmentalRgbPolicy.h"

namespace esp32core {

struct LoopDiagnostics {
  uint32_t loopDurationUs = 0;
  uint32_t loopMaxUs = 0;
};
using LoopDiagnosticsProvider = LoopDiagnostics (*)(void* context);

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
  // Fills a bounded JSON payload with the freshest telemetry from active
  // components. Returning false means "no component currently provides data".
  using ComponentTelemetry = bool (*)(char* output, size_t capacity, void* context);
  // Fills a bounded JSON payload with the authoritative runtime component
  // status (resolved pins + lifecycle). The firmware is the final GPIO
  // authority; clients never infer pin ownership from their own config.
  using ComponentStatus = bool (*)(char* output, size_t capacity, void* context);
  // Authorized, runtime-only rewire. Arguments include expected old pins to
  // prevent overwriting a concurrent edit; callback must rollback on failure.
  using ComponentApply = bool (*)(const char* id, uint8_t from1, uint8_t from2,
                                   uint8_t to1, uint8_t to2, uint8_t from3,
                                   uint8_t to3, void* context);

  using LdrCalibration = bool (*)(uint16_t darkRaw, uint16_t brightRaw, void* context);

  using OledDisplay = bool (*)(const char* mode, const char* value, void* context);

  using AudioCommand = bool (*)(const char* verb, int param, void* context);
  using AudioStatus = AudioSnapshot (*)(void* context);
  using OledStatus = OledSnapshot (*)(void* context);
  using EnvironmentalRgbSnapshotProvider = EnvironmentalRgbSnapshot (*)(void* context);

  LocalWifiControl(PinManager& pins, RgbState state, RgbCommand manualCommand,
                   RgbCommand runtimeCommand, DeviceId deviceId)
      : pins_(pins), rgbState_(state), rgbCommand_(manualCommand),
        rgbRuntime_(runtimeCommand), deviceId_(deviceId) {}

  void setComponentTelemetry(ComponentTelemetry source, void* context) {
    telemetry_ = source;
    telemetryContext_ = context;
  }

  void setComponentStatus(ComponentStatus source, void* context) {
    status_ = source;
    statusContext_ = context;
  }

  void setComponentApply(ComponentApply apply, void* context) {
    apply_ = apply;
    applyContext_ = context;
    bleSensorPins_.setApply(apply,context);
  }

  void setLdrCalibration(LdrCalibration calibrate, void* context) {
    ldrCalibration_ = calibrate;
    ldrCalibrationContext_ = context;
  }

  void setOledDisplay(OledDisplay display,void* context) {media_.setOledDisplay(display,context);}
  void setAudioControl(AudioCommand audio,void* context) {media_.setAudioControl(audio,context);}
  void setAudioStatus(AudioStatus source) {media_.setAudioStatus(source);}
  void setOledStatus(OledStatus source) {media_.setOledStatus(source);}
  AudioSnapshot audioSnapshot() const {return media_.audioSnapshot();}
  OledSnapshot oledSnapshot() const {return media_.oledSnapshot();}
  bool applyAudio(const char* verb,int parameter) {return media_.applyAudio(verb,parameter);}
  bool applyOled(const char* mode,const char* value) {return media_.applyOled(mode,value);}
  bool mediaReply(const MediaCommand& command,char* output,size_t capacity) {return media_.mediaReply(command,output,capacity);}

  BlePinApplyResult applySingleSensorPinBle(const BleSensorPinRequest& request) {
    return bleSensorPins_.apply(request,script_.active() || experiment_.active() ||
        automation_.active() || environmentalRgbEnabled_ || hasManual());
  }

  // One authority for both authorized transports. No duplicate GPIO rules.
  bool calibrateLdr(uint16_t dark, uint16_t bright) {
    if (ldrCalibration_ == nullptr || script_.active() ||
        experiment_.active() || automation_.active() ||
        environmentalRgbEnabled_ || hasManual() ||
        bright <= dark || bright - dark < 32u || bright > 4095u) return false;
    return ldrCalibration_(dark, bright, ldrCalibrationContext_);
  }

  void setEnvironmentalRgbSnapshot(EnvironmentalRgbSnapshotProvider provider, void* context) {
    envSnapshotProvider_ = provider;
    envSnapshotContext_ = context;
  }

  void setLoopDiagnostics(LoopDiagnosticsProvider provider, void* context) {
    loopDiagProvider_ = provider;
    loopDiagContext_ = context;
  }

  bool scriptActive() const { return script_.active(); }
  bool automationActive() const { return automation_.active(); }
  bool experimentActive() const { return experiment_.active(); }
  bool hasManualOverride() const { return hasManual(); }

  EnvironmentalRgbSnapshot environmentalRgbSnapshot() const {
    if (envSnapshotProvider_ != nullptr) {
      return envSnapshotProvider_(envSnapshotContext_);
    }
    EnvironmentalRgbSnapshot snapshot{};
    snapshot.enabled = environmentalRgbEnabled_;
    if (!snapshot.enabled) {
      snapshot.state = EnvironmentalRgbState::Disabled;
      snapshot.reason = "DISABLED";
    } else if (hasManual()) {
      snapshot.state = EnvironmentalRgbState::Conflict;
      snapshot.reason = "MANUAL_OVERRIDE";
    } else if (script_.active()) {
      snapshot.state = EnvironmentalRgbState::Conflict;
      snapshot.reason = "SCRIPT_ACTIVE";
    } else if (automation_.active()) {
      snapshot.state = EnvironmentalRgbState::Conflict;
      snapshot.reason = "AUTOMATION_ACTIVE";
    } else if (experiment_.active()) {
      snapshot.state = EnvironmentalRgbState::Conflict;
      snapshot.reason = "EXPERIMENT_ACTIVE";
    } else {
      snapshot.state = EnvironmentalRgbState::Suspended;
      snapshot.reason = "STALE_TEMPERATURE";
    }
    return snapshot;
  }

  // No extra RGB driver: the loop reads this opt-in mode and uses the existing
  // authenticated RGB command path. Never persists a mode on reboot.
  bool environmentalRgbEnabled() const { return environmentalRgbEnabled_; }
  bool environmentalRgbMayRun() const {
    return environmentalRgbEnabled_ && !script_.active() &&
        !automation_.active() && !experiment_.active() && !hasManual();
  }
  // Shared operational authority for BLE owner and authenticated local HTTP.
  // The client transport never directly mutates automation state.
  bool startEnvironmentalRgb() {
    if (script_.active() || automation_.active() ||
        experiment_.active() || hasManual()) return false;
    environmentalRgbEnabled_ = true;
    return true;
  }
  void disableEnvironmentalRgb() { environmentalRgbEnabled_ = false; }

  // Independent LAB HTTP access key, printed only to physical serial.
  // This is NOT the BLE owner's X25519 enrollment identity or session key.
  String accessKey() const { return accessKey_; }

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
    apPassword_ = password;
    // Recover stored STA credentials BEFORE starting the SoftAP. Starting STA
    // after softAP() could reconfigure the radio and make discovery unreliable.
    // Preserve both interfaces so the lab API remains AP-only.
    WiFi.mode(WIFI_AP_STA);
    WiFi.persistent(true); // STA credentials remain in NVS across reboot.
    WiFi.setAutoReconnect(true);
    WiFi.begin();
    WiFi.mode(WIFI_AP_STA);
    bool apReady = false;
    for (uint8_t attempt = 0; attempt < 3 && !apReady; ++attempt) {
      apReady = WiFi.softAP("ESP32-Control-Lab", apPassword_.c_str());
      if (!apReady) delay(250);
    }
    if (!apReady) {
      Serial.println("ERROR: protected lab SoftAP failed after 3 attempts; HTTP disabled");
      return;
    }
    Serial.print("SoftAP IP: ");
    Serial.println(WiFi.softAPIP());
    Serial.println("=== ESP32 LAB ACCESS (PHYSICAL SERIAL ONLY) ===");
    Serial.print("SoftAP: ESP32-Control-Lab / password: ");
    Serial.println(password);
    Serial.print("App pairing key (not Wi-Fi password): ");
    Serial.println(accessKey_);
    Serial.println("Use only for local bench testing; no TLS / no WAN.");
    // STA was started before softAP(). Do not restart it here: the additional
    // WiFi.begin() can reconfigure the shared radio after AP initialization.

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
      body += ",\"leasedPins\":" + String(pins_.leasedCount());
      if (loopDiagProvider_ != nullptr) {
        LoopDiagnostics diag = loopDiagProvider_(loopDiagContext_);
        body += ",\"loopDurationUs\":" + String(diag.loopDurationUs);
        body += ",\"loopMaxUs\":" + String(diag.loopMaxUs);
      }
      body += "}";
      server_.send(200, "application/json", body);
    });

    server_.on("/v2/identity", HTTP_GET, [this]() {
      if (!authorized()) return;
      String body = "{\"schemaVersion\":4,\"deviceId\":\"" +
        deviceId_() + "\",\"secureProvisioning\":false," +
        "\"legacyBleWriteUnauthenticated\":true," +
        "\"ownerBound\":false}";
      server_.send(200, "application/json", body);
    });

    server_.on("/v2/capabilities", HTTP_GET, [this]() {
      if (!authorized()) return;
      const uint32_t features =
          capabilityBit(Capability::Rgb) |
          capabilityBit(Capability::Effects) |
          capabilityBit(Capability::Gpio) |
          capabilityBit(Capability::Adc) |
          capabilityBit(Capability::Sequences) |
          capabilityBit(Capability::Diagnostics);
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

    server_.on("/v2/components/catalog", HTTP_GET, [this]() {
      if (!authorized()) return;
      char body[1024];
      if (!encodeCatalog(body, sizeof(body))) {
        jsonError(500, "catalog_encode_failed");
        return;
      }
      server_.send(200, "application/json", body);
    });

    server_.on("/v2/display/oled/status", HTTP_GET, [this]() {
      if (!authorized()) return;
      char body[160];
      if (!encodeOledStatus(oledSnapshot(),body,sizeof(body))) {jsonError(500,"oled_encode_failed");return;}
      server_.send(200,"application/json",body);
    });
    server_.on("/v2/audio/status", HTTP_GET, [this]() {
      if (!authorized()) return;
      char body[240];
      if (!encodeAudioStatus(audioSnapshot(),body,sizeof(body))) {jsonError(500,"audio_encode_failed");return;}
      server_.send(200,"application/json",body);
    });
    server_.on("/v2/display/oled", HTTP_POST, [this]() {
      if (!authorized()) return;
      if (!media_.oledAvailable()) { jsonError(409, "oled_unavailable"); return; }
      const String mode = server_.arg("mode");
      const String value = server_.arg("value");
      const bool face = mode == "face";
      const bool contrast = mode == "contrast";
      if ((!face && mode != "text" && !contrast && mode != "invert" && mode != "pause") ||
          value.isEmpty() || value.length() > 39) {
        jsonError(400, "invalid_oled_command");
        return;
      }
      if (face && value != "happy" && value != "sleepy" &&
          value != "angry" && value != "surprised" &&
          value != "pumpkin" && value != "cat" && value != "dance") {
        jsonError(400, "invalid_oled_face");
        return;
      }
      if (contrast) {
        unsigned int level = 0;
        for (size_t i = 0; i < value.length(); ++i) {
          const char digit = value[i];
          if (digit < '0' || digit > '9') {
            jsonError(400, "invalid_oled_contrast");
            return;
          }
          level = level * 10u + static_cast<unsigned int>(digit - '0');
          if (level > 255u) {
            jsonError(400, "invalid_oled_contrast");
            return;
          }
        }
        if (level < 8u) {
          jsonError(400, "invalid_oled_contrast");
          return;
        }
      }
      for (size_t i = 0; i < value.length(); ++i) {
        const char ch = value[i];
        if (ch < 32 || ch > 126) {
          jsonError(400, "oled_ascii_only");
          return;
        }
      }
      if (!applyOled(mode.c_str(), value.c_str())) {
        jsonError(409, "oled_not_ready");
        return;
      }
      server_.send(200, "application/json",
                   "{\"status\":\"DISPLAYED\",\"runtimeOnly\":true}");
    });

    server_.on("/v2/components/telemetry", HTTP_GET, [this]() {
      if (!authorized()) return;
      char body[1024];
      if (telemetry_ == nullptr || !telemetry_(body, sizeof(body), telemetryContext_)) {
        jsonError(503, "telemetry_unavailable");
        return;
      }
      server_.send(200, "application/json", body);
    });

    server_.on("/v2/components/ldr/calibrate", HTTP_POST, [this]() {
      if (!authorized()) return;
      int dark = -1, bright = -1;
      if (!parseInt("darkRaw", 0, 4095, dark) ||
          !parseInt("brightRaw", 0, 4095, bright) ||
          dark >= bright || bright - dark < 32) {
        jsonError(400, "invalid_ldr_calibration");
        return;
      }
      if (!calibrateLdr(static_cast<uint16_t>(dark),
                        static_cast<uint16_t>(bright))) {
        jsonError(409, "ldr_calibration_rejected");
        return;
      }
      server_.send(200, "application/json",
                   "{\"status\":\"CALIBRATED\",\"runtimeOnly\":true}");
    });

    server_.on("/v2/components/status", HTTP_GET, [this]() {
      if (!authorized()) return;
      // Include per-sensor read diagnostics without truncating the JSON
      // when RGB, DHT11, DHT22, soil and OLED drivers are all compiled.
      char body[1024];
      if (status_ == nullptr || !status_(body, sizeof(body), statusContext_)) {
        jsonError(503, "status_unavailable");
        return;
      }
      server_.send(200, "application/json", body);
    });

    server_.on("/v2/components/apply", HTTP_POST, [this]() {
      if (!authorized()) return;
      if (apply_ == nullptr || script_.active() || experiment_.active() ||
          automation_.active() || environmentalRgbEnabled_ || hasManual()) {
        jsonError(409, "component_apply_unavailable_or_busy"); return;
      }
      const String componentId = server_.arg("id");
      if (componentId != "dht11" && componentId != "dht22" && componentId != "soil_moisture" &&
          componentId != "ssd1306_oled" && componentId != "ldr_light" &&
          componentId != "led_rgb") {
        jsonError(400, "unsupported_component"); return;
      }
      int from1 = -1, from2 = -1, to1 = -1, to2 = -1;
      if (!parseInt("from1", 0, 39, from1) ||
          !parseInt("to1", 0, 39, to1) ||
          !parseInt("from2", 0, 39, from2) ||
          !parseInt("to2", 0, 39, to2)) {
        jsonError(400, "invalid_gpio_assignment"); return;
      }
      const bool rgb = componentId == "led_rgb";
      int from3 = 0, to3 = 0;
      if ((rgb && (!parseInt("from3", 0, 39, from3) ||
                   !parseInt("to3", 0, 39, to3))) ||
          (!rgb && (!server_.arg("from3").isEmpty() ||
                    !server_.arg("to3").isEmpty()))) {
        jsonError(400, "invalid_gpio_assignment"); return;
      }
      const String requestId = server_.arg("requestId");
      const ComponentApplyReplayGuard::Request request = {
          requestId.c_str(), componentId.c_str(),
          static_cast<uint8_t>(from1), static_cast<uint8_t>(from2),
          static_cast<uint8_t>(to1), static_cast<uint8_t>(to2),
          static_cast<uint8_t>(from3), static_cast<uint8_t>(to3)};
      const auto replayVerdict = replayGuard_.inspect(request);
      if (replayVerdict == ComponentApplyReplayGuard::Verdict::Invalid) {
        jsonError(400, "invalid_gpio_request_id"); return;
      }
      if (replayVerdict == ComponentApplyReplayGuard::Verdict::ConflictingReuse) {
        jsonError(409, "gpio_request_id_reused_with_different_assignment"); return;
      }
      // A committed duplicate is never applied again. Android still verifies
      // current pin ownership independently; a later change can invalidate it.
      if (replayVerdict == ComponentApplyReplayGuard::Verdict::IdenticalRetry) {
        server_.send(200, "application/json",
            String("{\"status\":\"APPLIED\",\"runtimeOnly\":true,") +
            "\"replayed\":true,\"requestId\":\"" + requestId + "\"}");
        return;
      }
      const bool oled = componentId == "ssd1306_oled";
      if ((rgb && (to1 == to2 || to1 == to3 || to2 == to3 ||
                   !PinManager::outputCapable(to1) ||
                   !PinManager::outputCapable(to2) ||
                   !PinManager::outputCapable(to3))) ||
          (!rgb && ((!oled && (from2 != 0 || to2 != 0)) ||
           (oled && (from1 == from2 || to1 == to2)) ||
           PinManager::reservedForRgb(static_cast<uint8_t>(to1)) ||
           (oled && PinManager::reservedForRgb(static_cast<uint8_t>(to2)))))) {
        jsonError(400, "invalid_gpio_assignment"); return;
      }
      if (!apply_(componentId.c_str(), static_cast<uint8_t>(from1),
                  static_cast<uint8_t>(from2), static_cast<uint8_t>(to1),
                  static_cast<uint8_t>(to2), static_cast<uint8_t>(from3),
                  static_cast<uint8_t>(to3), applyContext_)) {
        jsonError(409, "component_apply_rejected_or_rolled_back"); return;
      }
      // Cache success only; a rejected request can be retried with the same ID.
      if (!replayGuard_.commit(request)) {
        jsonError(500, "gpio_transaction_cache_error"); return;
      }
      // The Android client must GET /v2/components/status and verify leases.
      // This receipt does not prove physical wiring or successful sensor reads.
      server_.send(200, "application/json",
          String("{\"status\":\"APPLIED\",\"runtimeOnly\":true,") +
          "\"replayed\":false,\"requestId\":\"" + requestId + "\"}");
    });

    server_.on("/v2/firmware", HTTP_GET, [this]() {
      if (!authorized()) return;
      const esp_partition_t* current = esp_ota_get_running_partition();
      const esp_partition_t* next = esp_ota_get_next_update_partition(nullptr);
      String body = "{\"firmwareVersion\":\"1.3.0-lab\",";
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

    // Non-blocking asynchronous Wi-Fi scan state machine
    server_.on("/v2/wifi/scan", HTTP_GET, [this]() {
      if (!authorized()) return;
      int status = WiFi.scanComplete();
      if (status == WIFI_SCAN_RUNNING) {
        server_.send(202, "application/json", "{\"status\":\"SCANNING\"}");
        return;
      }
      if (status == WIFI_SCAN_FAILED) {
        WiFi.scanDelete();
        WiFi.scanNetworks(true, true); // Initiate new async scan
        server_.send(202, "application/json", "{\"status\":\"SCANNING\"}");
        return;
      }
      if (status >= 0) {
        String body = "{\"status\":\"READY\",\"networks\":[";
        for (int i = 0; i < status && i < 24; ++i) {
          if (i) body += ",";
          body += "{\"ssid\":\"" + escape(WiFi.SSID(i)) + "\",\"rssi\":" +
            String(WiFi.RSSI(i)) + ",\"secure\":" +
            String(WiFi.encryptionType(i) == WIFI_AUTH_OPEN ? "false" : "true") + "}";
        }
        body += "]}";
        WiFi.scanDelete();
        server_.send(200, "application/json", body);
        return;
      }
      // Start new async scan if IDLE
      WiFi.scanNetworks(true, true);
      server_.send(202, "application/json", "{\"status\":\"SCANNING\"}");
    });

    server_.on("/v2/wifi/connect", HTTP_POST, [this]() {
      if (!authorized()) return;
      String ssid = server_.arg("ssid");
      String password = server_.arg("password");
      if (ssid.isEmpty() || ssid.length() > 32 ||
          (!password.isEmpty() && (password.length() < 8 || password.length() > 63))) {
        jsonError(400, "invalid_wifi_credentials"); return;
      }
      WiFi.begin(ssid.c_str(), password.c_str());
      server_.send(202, "application/json", "{\"status\":\"connecting\"}");
    });

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

    server_.on("/v2/environment/rgb/status", HTTP_GET, [this]() {
      if (!authorized()) return;
      EnvironmentalRgbSnapshot snapshot = environmentalRgbSnapshot();
      String body = "{\"version\":1,\"enabled\":" +
          String(snapshot.enabled ? "true" : "false") +
          ",\"state\":\"" + environmentalRgbStateName(snapshot.state) +
          "\",\"reason\":\"" + (snapshot.reason != nullptr ? snapshot.reason : "UNKNOWN") +
          "\",\"band\":\"" + environmentalRgbBandName(snapshot.band) +
          "\",\"red\":" + String(snapshot.red) +
          ",\"green\":" + String(snapshot.green) +
          ",\"blue\":" + String(snapshot.blue) +
          ",\"brightness\":" + String(snapshot.brightness) +
          ",\"humidityUsed\":" + String(snapshot.humidityUsed ? "true" : "false") +
          ",\"lightUsed\":" + String(snapshot.lightUsed ? "true" : "false") +
          ",\"runtimeOnly\":true}";
      server_.send(200, "application/json", body);
    });
    server_.on("/v2/environment/rgb/start", HTTP_POST, [this]() {
      if (!authorized()) return;
      if (!startEnvironmentalRgb()) {
        jsonError(409, "environmental_rgb_busy");
        return;
      }
      server_.send(200, "application/json",
                   "{\"status\":\"ENABLED\",\"runtimeOnly\":true}");
    });
    server_.on("/v2/environment/rgb/stop", HTTP_POST, [this]() {
      if (!authorized()) return;
      environmentalRgbEnabled_ = false;
      server_.send(200, "application/json",
                   "{\"status\":\"STOPPED\",\"runtimeOnly\":true}");
    });

    server_.on("/v2/automation/status", HTTP_GET, [this]() {
      if (!authorized()) return;
      String body = "{\"version\":1,\"active\":" +
        String(automation_.active() ? "true" : "false") +
        ",\"intervalMs\":" + String(automation_.intervalMs()) +
        ",\"remainingTransitions\":" + String(automationEdges_) + "}";
      server_.send(200, "application/json", body);
    });

    server_.on("/v2/automation/start", HTTP_POST, [this]() {
      if (!authorized()) return;
      int seconds = 0, cycles = 0, startOn = 0;
      if (!parseInt("intervalSeconds", 1, 60, seconds) ||
          !parseInt("cycles", 1, 10, cycles) ||
          !parseInt("startOn", 0, 1, startOn)) {
        jsonError(400, "invalid_automation"); return;
      }
      if (script_.active() || experiment_.active() || hasManual() ||
          automation_.active() || environmentalRgbEnabled_) {
        jsonError(409, "another_operation_active"); return;
      }
      if (!automation_.configure(static_cast<uint32_t>(seconds) * 1000U,
                                 millis(), startOn == 1)) {
        jsonError(400, "invalid_automation"); return;
      }
      automationEdges_ = static_cast<uint8_t>(cycles * 2);
      server_.send(202, "application/json", "{\"status\":\"RUNNING\"}");
    });

    server_.on("/v2/automation/stop", HTTP_POST, [this]() {
      if (!authorized()) return;
      automation_.stop();
      automationEdges_ = 0;
      server_.send(200, "application/json", "{\"status\":\"STOPPED\"}");
    });

    server_.on("/v2/script/status", HTTP_GET, [this]() {
      if (!authorized()) return;
      server_.send(200, "application/json",
        "{\"active\":" + String(script_.active() ? "true" : "false") +
        ",\"step\":" + String(script_.instructionIndex()) +
        ",\"total\":" + String(script_.instructionCount()) + "}");
    });

    server_.on("/v2/script/run", HTTP_POST, [this]() {
      if (!authorized()) return;
      if (script_.active() || experiment_.active() || automation_.active() ||
          environmentalRgbEnabled_) {
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
      script_.stop();
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
      if (parsed.action != esp32core::RgbAction::Get) {
        script_.stop();
        automation_.stop();
        automationEdges_ = 0;
      }
      if (!rgbCommand_(cmd)) {
        jsonError(409, "rgb_command_not_applied"); return;
      }
      server_.send(200, "application/json", "{\"status\":\"APPLIED\"}");
    });

    server_.on("/v2/audio/play", HTTP_POST, [this]() {
      if (!authorized()) return;
      int track = -1;
      if (!parseInt("track", 1, 2999, track) || !applyAudio("play", track)) {
        jsonError(400, "audio_play_failed"); return;
      }
      server_.send(202, "application/json", "{\"status\":\"ACCEPTED\",\"requestedTrack\":" + String(track) + "}");
    });

    server_.on("/v2/audio/volume", HTTP_POST, [this]() {
      if (!authorized()) return;
      int vol = -1;
      if (!parseInt("volume", 0, 30, vol) || !applyAudio("volume", vol)) {
        jsonError(400, "audio_volume_failed"); return;
      }
      server_.send(202, "application/json", "{\"status\":\"ACCEPTED\",\"requestedVolume\":" + String(vol) + "}");
    });

    server_.on("/v2/audio/stop", HTTP_POST, [this]() {
      if (!authorized()) return;
      if (!applyAudio("stop", 0)) {
        jsonError(409, "audio_stop_failed"); return;
      }
      server_.send(202, "application/json", "{\"status\":\"ACCEPTED\"}");
    });

    server_.on("/v2/audio/pause", HTTP_POST, [this]() {
      if (!authorized()) return;
      if (!applyAudio("pause", 0)) {
        jsonError(409, "audio_pause_failed"); return;
      }
      server_.send(202, "application/json", "{\"status\":\"ACCEPTED\"}");
    });

    server_.on("/v2/audio/resume", HTTP_POST, [this]() {
      if (!authorized()) return;
      if (!applyAudio("resume", 0)) {
        jsonError(409, "audio_resume_failed"); return;
      }
      server_.send(202, "application/json", "{\"status\":\"ACCEPTED\"}");
    });

    server_.on("/v2/audio/voice", HTTP_POST, [this]() {
      if (!authorized()) return;
      int enabled = -1;
      if (!parseInt("enabled", 0, 1, enabled) || !applyAudio("voice", enabled)) {
        jsonError(400, "audio_voice_failed"); return;
      }
      server_.send(200, "application/json", "{\"status\":\"APPLIED\",\"enabled\":" + String(enabled) + "}");
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
    // The ESP32 can remain booted even when the AP interface is dropped.
    // Check infrequently to avoid disturbing clients or wearing flash.
    if (static_cast<uint32_t>(nowMs - lastApCheckMs_) >= 15000U) {
      lastApCheckMs_ = nowMs;
      if ((WiFi.getMode() & WIFI_AP) == 0 ||
          WiFi.softAPSSID() != "ESP32-Control-Lab" ||
          WiFi.softAPIP() != IPAddress(192, 168, 4, 1)) {
        Serial.println("WARN: lab SoftAP unavailable; attempting AP+STA recovery");
        WiFi.mode(WIFI_AP_STA);
        if (WiFi.softAP("ESP32-Control-Lab", apPassword_.c_str())) {
          Serial.print("INFO: lab SoftAP restored at ");
          Serial.println(WiFi.softAPIP());
        } else {
          Serial.println("ERROR: lab SoftAP recovery failed; will retry in 15 s");
        }
      }
    }
    server_.handleClient();
    script_.tick(nowMs, sendScriptRgb, this);
    if (automation_.active()) {
      const auto action = automation_.tick(nowMs);
      if (action != RgbAutomationClock::Result::None) {
        const char* command = action == RgbAutomationClock::Result::TurnOn ? "ON" : "OFF";
        const uint32_t consumed = automation_.advancedIntervals();
        if (consumed >= automationEdges_) {
          automation_.stop();
          automationEdges_ = 0;
          rgbRuntime_(String("OFF"));
        } else if (!rgbRuntime_(String(command))) {
          automation_.stop();
          automationEdges_ = 0;
          rgbRuntime_(String("OFF"));
        } else {
          automationEdges_ -= static_cast<uint8_t>(consumed);
        }
      }
    }
    experiment_.tick(pins_, nowMs);
    if (hasManual() && static_cast<uint32_t>(nowMs - manualTouched_) >= 30000U) {
      releaseManual();
    }
  }

  void stopScriptFromBle() { script_.stop(); automation_.stop(); automationEdges_ = 0;
    environmentalRgbEnabled_ = false; }

  bool writeBlePinSnapshot(char (&output)[96]) const {
    return GpioTelemetryCodec::encode(pins_, manualLevels_, output, sizeof(output));
  }

 private:
  static constexpr const char* kManual = "gpio.manual";
  static constexpr const char* kRead = "gpio.read";
  static bool sendScriptRgb(const char* command, void* context) {
    auto* self = static_cast<LocalWifiControl*>(context);
    return self != nullptr && self->rgbRuntime_(String(command));
  }

  static constexpr bool manualOutputAllowed(int pin) {
    return pin == 18 || pin == 19 || pin == 21 || pin == 22 || pin == 23;
  }
  PinManager& pins_;
  RgbState rgbState_;
  RgbCommand rgbCommand_;
  RgbCommand rgbRuntime_;
  RgbScriptRuntime script_;
  RgbAutomationClock automation_;
  uint8_t automationEdges_ = 0;
  DeviceId deviceId_;
  ComponentTelemetry telemetry_ = nullptr;
  void* telemetryContext_ = nullptr;
  ComponentStatus status_ = nullptr;
  void* statusContext_ = nullptr;
  ComponentApply apply_ = nullptr;
  void* applyContext_ = nullptr;
  MediaControlBackend media_;
  BleSensorPinControl bleSensorPins_;
  LdrCalibration ldrCalibration_ = nullptr;
  void* ldrCalibrationContext_ = nullptr;
  EnvironmentalRgbSnapshotProvider envSnapshotProvider_ = nullptr;
  void* envSnapshotContext_ = nullptr;
  LoopDiagnosticsProvider loopDiagProvider_ = nullptr;
  void* loopDiagContext_ = nullptr;
  bool environmentalRgbEnabled_ = false;
  ComponentApplyReplayGuard replayGuard_;
  Preferences prefs_;
  WebServer server_{80};
  ExperimentRuntime experiment_;
  String accessKey_;
  String apPassword_; // NVS-backed, never sent over HTTP or diagnostics.
  uint32_t lastApCheckMs_ = 0;
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
  // Compare all key bytes without early mismatch exit. Length is public and
  // restricted to the fixed 16-hex-character lab credential format.
  static bool keyMatches(const String& presented, const String& expected) {
    if (presented.length() != 16 || expected.length() != 16) return false;
    uint8_t mismatch = 0;
    for (size_t i = 0; i < 16; ++i) {
      mismatch |= static_cast<uint8_t>(presented[i] ^ expected[i]);
    }
    return mismatch == 0;
  }
  bool authorized() {
    if (server_.client().localIP() != WiFi.softAPIP() ||
        !keyMatches(server_.header("X-Device-Key"), accessKey_)) {
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
