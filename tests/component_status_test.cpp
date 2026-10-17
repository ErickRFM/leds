// Host tests for the authoritative runtime component status contract.
// Compiled with -std=c++17 -Wall -Wextra -Werror -pedantic; no ESP32 required.
#include <cassert>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <string>

#include "../firmware/esp32_rgb/core/ComponentContract.h"
#include "../firmware/esp32_rgb/core/ComponentStatus.h"
#include "../firmware/esp32_rgb/core/PinManager.h"

using namespace esp32core;

namespace {

void testEmptyListReportsFirmwareAuthority() {
  char buffer[192] = {};
  assert(encodeComponentStatus(nullptr, 0, buffer, sizeof(buffer)));
  assert(std::strstr(buffer, "\"version\":1") != nullptr);
  assert(std::strstr(buffer, "\"gpioAuthority\":\"firmware\"") != nullptr);
  assert(std::strstr(buffer, "\"components\":[]") != nullptr);
}

void testSingleRgbEntryReportsResolvedPins() {
  ComponentStatusEntry entry = {};
  entry.id = "led.rgb";
  entry.type = ComponentType::Actuator;
  entry.interfaceType = HardwareInterface::Pwm;
  entry.pins[0] = 27;
  entry.pins[1] = 25;
  entry.pins[2] = 26;
  entry.pinCount = 3;
  entry.active = true;
  entry.state = ComponentState::Active;

  char buffer[256] = {};
  assert(encodeComponentStatus(&entry, 1, buffer, sizeof(buffer)));
  const std::string json(buffer);
  assert(json.find("\"id\":\"led.rgb\"") != std::string::npos);
  assert(json.find("\"type\":\"actuator\"") != std::string::npos);
  assert(json.find("\"interface\":\"pwm\"") != std::string::npos);
  assert(json.find("\"state\":\"active\"") != std::string::npos);
  assert(json.find("\"active\":true") != std::string::npos);
  assert(json.find("\"pins\":[27,25,26]") != std::string::npos);
}

void testInactiveEntryNeverClaimsActive() {
  ComponentStatusEntry entry = {};
  entry.id = "sensor.soil_moisture";
  entry.type = ComponentType::Sensor;
  entry.interfaceType = HardwareInterface::Adc;
  entry.pins[0] = 34;
  entry.pinCount = 1;
  entry.active = false;
  entry.state = ComponentState::Error;

  char buffer[256] = {};
  assert(encodeComponentStatus(&entry, 1, buffer, sizeof(buffer)));
  const std::string json(buffer);
  assert(json.find("\"active\":false") != std::string::npos);
  assert(json.find("\"state\":\"error\"") != std::string::npos);
  // No lease means no owned GPIO, even if a default pin was configured.
  assert(json.find("\"pins\":[]") != std::string::npos);
  assert(json.find("\"pins\":[34]") == std::string::npos);
}

void testMultipleEntriesAreOrderedAndSeparated() {
  ComponentStatusEntry entries[2] = {};
  entries[0].id = "led.rgb";
  entries[0].type = ComponentType::Actuator;
  entries[0].interfaceType = HardwareInterface::Pwm;
  entries[0].pins[0] = 27;
  entries[0].pinCount = 1;
  entries[0].active = true;
  entries[0].state = ComponentState::Active;
  entries[1].id = "display.ssd1306";
  entries[1].type = ComponentType::Display;
  entries[1].interfaceType = HardwareInterface::I2c;
  entries[1].pins[0] = 21;
  entries[1].pins[1] = 22;
  entries[1].pinCount = 2;
  entries[1].active = true;
  entries[1].state = ComponentState::Active;

  char buffer[384] = {};
  assert(encodeComponentStatus(entries, 2, buffer, sizeof(buffer)));
  const std::string json(buffer);
  const size_t rgb = json.find("led.rgb");
  const size_t oled = json.find("display.ssd1306");
  assert(rgb != std::string::npos && oled != std::string::npos && rgb < oled);
  assert(json.find("\"pins\":[21,22]") != std::string::npos);
  assert(json.find("},") != std::string::npos);
}

void testPinCountIsClampedToThree() {
  ComponentStatusEntry entry = {};
  entry.id = "over.count";
  entry.type = ComponentType::Sensor;
  entry.interfaceType = HardwareInterface::Gpio;
  for (uint8_t i = 0; i < 3; ++i) entry.pins[i] = static_cast<uint8_t>(10 + i);
  entry.pinCount = 9;
  entry.active = true;
  entry.state = ComponentState::Active;

  char buffer[256] = {};
  assert(encodeComponentStatus(&entry, 1, buffer, sizeof(buffer)));
  assert(std::strstr(buffer, "\"pins\":[10,11,12]") != nullptr);
}

void testTruncationReturnsFalseNotPartialJson() {
  ComponentStatusEntry entry = {};
  entry.id = "sensor.soil_moisture";
  entry.type = ComponentType::Sensor;
  entry.interfaceType = HardwareInterface::Adc;
  entry.pins[0] = 34;
  entry.pinCount = 1;
  entry.active = true;
  entry.state = ComponentState::Active;

  char tooSmall[32] = {};
  assert(!encodeComponentStatus(&entry, 1, tooSmall, sizeof(tooSmall)));
  assert(!encodeComponentStatus(&entry, 1, nullptr, 128));
  assert(!encodeComponentStatus(&entry, 1, tooSmall, 8));
}

void testFirmwareIsFinalGpioAuthority() {
  PinManager pins;
  // A second owner cannot steal a leased pin.
  assert(pins.acquire(18, "owner.a", PinFunction::DigitalOutput));
  assert(!pins.acquire(18, "owner.b", PinFunction::DigitalOutput));
  assert(pins.belongsTo(18, "owner.a"));
  assert(!pins.belongsTo(18, "owner.b"));
  // Only the owner may release the lease.
  assert(!pins.release(18, "owner.b"));
  assert(pins.release(18, "owner.a"));

  // A module pin registered as a system reservation cannot be taken by another.
  assert(pins.registerSystemPin(25, "led.rgb"));
  assert(pins.acquire(25, "led.rgb", PinFunction::PwmOutput));
  assert(!pins.acquire(25, "sensor.dht22", PinFunction::DigitalOutput));
  assert(pins.belongsTo(25, "led.rgb"));
  assert(pins.leasedCount() == 1);
}

}  // namespace

int main() {
  testEmptyListReportsFirmwareAuthority();
  testSingleRgbEntryReportsResolvedPins();
  testInactiveEntryNeverClaimsActive();
  testMultipleEntriesAreOrderedAndSeparated();
  testPinCountIsClampedToThree();
  testTruncationReturnsFalseNotPartialJson();
  testFirmwareIsFinalGpioAuthority();

  std::cout << "component_status_test: all tests passed" << std::endl;
  return 0;
}
