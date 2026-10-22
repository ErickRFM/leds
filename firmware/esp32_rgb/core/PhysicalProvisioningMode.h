#pragma once
// V4 opt-in: factory-personalized BLE Wi-Fi onboarding runs ONLY after physical
// BOOT-button gesture and a clean soft restart. Never shares a BLE controller
// with the legacy RGB GATT service. The normal firmware remains untouched.
#include <Arduino.h>
#include <esp_system.h>
#include <esp_attr.h>

#if __has_include("../config/FactoryProvisioning.h")
#define RGB_V4_PERSONALIZED 1
#include "../config/FactoryProvisioning.h"
#include <WiFi.h>
#include <WiFiProv.h>
#if defined(CORE_DEBUG_LEVEL) && CORE_DEBUG_LEVEL >= 3
#error "Provisioning firmware may print PoP via library info logs: use Core Debug Level None/Errors/Warnings"
#endif
namespace esp32core {
namespace factory {
constexpr uint32_t kMagic = 0xA9C14F72u;
RTC_NOINIT_ATTR uint32_t softBootMarker;
// Never expose a credential in Serial, QR response or normal BLE readback.
static volatile bool credentialsAccepted = false;
static bool active = false;
static uint32_t enteredAt = 0;
static uint32_t acceptedAt = 0;

inline void onProvisionEvent(arduino_event_t* event) {
  if (event->event_id == ARDUINO_EVENT_PROV_CRED_SUCCESS) {
    credentialsAccepted = true;
    acceptedAt = millis();
  }
  // Deliberately never print received credentials or proof of possession.
}

inline bool beginIfPhysicalRequest() {
  if (esp_reset_reason() != ESP_RST_SW || softBootMarker != kMagic) return false;
  softBootMarker = 0; // Single-use marker, even when provisioning fails.
  WiFi.onEvent(onProvisionEvent);
  WiFi.begin();
  // Reset only Wi-Fi STA provisioning state, and ONLY after a long physical
  // button gesture. Does not erase RGB, public device ID or V3 SoftAP NVS.
  // Security 1 with a UNIQUE 128-bit factory PoP. Never Security 0 or fixed PoP.
  WiFiProv.beginProvision(
      NETWORK_PROV_SCHEME_BLE, NETWORK_PROV_SCHEME_HANDLER_FREE_BLE,
      NETWORK_PROV_SECURITY_1, esp32factory::kProvisioningPop,
      esp32factory::kProvisioningName, nullptr, nullptr, true);
  active = true;
  enteredAt = millis();
  Serial.println("V4 physically authorized Wi-Fi provisioning active (no RGB BLE until restart)");
  return true;
}

inline bool tick() {
  if (!active) return false;
  const uint32_t now = millis();
  // After credential success, wait for STA connection confirmation before reboot.
  if (credentialsAccepted && WiFi.status() == WL_CONNECTED &&
      static_cast<uint32_t>(now - acceptedAt) >= 1500U) {
    Serial.println("V4 Wi-Fi credentials accepted; restarting normal RGB firmware");
    esp_restart();
  }
  if (static_cast<uint32_t>(now - enteredAt) >= 300000U) {
    Serial.println("V4 provisioning window expired; restarting normal RGB firmware");
    esp_restart();
  }
  return true;
}
inline void requestAfterPhysicalHold() {
  softBootMarker = kMagic;
  esp_restart();
}
} // namespace factory
} // namespace esp32core
#else
#define RGB_V4_PERSONALIZED 0
namespace esp32core {
namespace factory {
inline bool beginIfPhysicalRequest() { return false; }
inline bool tick() { return false; }
inline void requestAfterPhysicalHold() {}
} // namespace factory
} // namespace esp32core
#endif
