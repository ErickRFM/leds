# Android release gate — ESP32 Control Center V2/V3

**Status:** LABORATORY DEBUG ONLY / NOT PRODUCTION READY.

The Android `release` build variant is intentionally disabled by `androidComponents.beforeVariants`. Android debug functionality remains available for local supervised testing. This is a deliberate fail-closed decision because the current ESP32 RGB BLE GATT WRITE characteristic has no verified bonding/authorization and local HTTP is WPA2 SoftAP laboratory-only with no TLS.

## Evidence required before enabling release

- [ ] Firmware rejects unauthenticated BLE WRITE; verified from a second, unpaired Android phone.
- [ ] Proof-of-possession onboarding and bond revocation tested on the physical ESP32 and phone.
- [ ] Secure provisioning credentials and transport, if production Wi-Fi is enabled.
- [ ] Firmware version/partition consistency and safe physical USB recovery tested.
- [ ] Android unit tests, instrumentation, lint, debug APK, firmware host tests and board compile pass.
- [ ] Real hardware RGB/GPIO tests, reconnect after Wi-Fi/BLE/power outages, responsive UI and accessibility pass.
- [ ] Threat-model review, software dependencies and third-party licenses recorded.
- [ ] Only after these pass: enable release variant in PR with signed APK provenance/SHA256 and release notes.

**CI green is not evidence of hardware pairing or a secure release.**
