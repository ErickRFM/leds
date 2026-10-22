// Compile-only V4 feasibility probe. NEVER upload to a user's device.
// Does not initialize WiFiProv or accept any pairing or Wi-Fi credentials.
#include <Arduino.h>
#include <WiFiProv.h>
#include <WiFi.h>
#include <BLEDevice.h>
#include "sdkconfig.h"

#if !defined(CONFIG_ESP_PROTOCOMM_SUPPORT_SECURITY_VERSION_2)
#error "Arduino-ESP32 core is missing provisioning Security 2; fail V4 feasibility gate"
#endif

// Verify API surface and BLE stack headers are available in pinned core.
// This is NOT proof of BLE coexistence or provisioning correctness on hardware.
namespace {
auto provisioningFunction = &WiFiProvClass::beginProvision;
void useSymbols() {
  (void)provisioningFunction;
  (void)NETWORK_PROV_SECURITY_2;
  (void)NETWORK_PROV_SCHEME_BLE;
}
}

void setup() {
  Serial.begin(115200);
  useSymbols();
  Serial.println("V4 Security2 header/link feasibility passed; no provisioning enabled");
}
void loop() {
  delay(1000);
}
