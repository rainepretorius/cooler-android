# XTrailCooler Android App

This repository contains an Android application written in Java that controls the XTrailCooler ESP32-C6 firmware over Bluetooth Low Energy (BLE).

## Features

- Automatically scans for the `XTrailCooler` BLE peripheral and connects to it.
- Displays inside and hot-side temperature readings, output states and alarm indicators.
- Allows forcing the peltiers or fan, clearing alarms and updating control parameters.
- Sends Wi-Fi credentials to the controller for network configuration.

## Building

The project is structured for Android Studio/Gradle. To build it locally:

Because binary wrapper artifacts are not tracked, the `gradlew` helper will fall back to the
system `gradle` command when the wrapper JAR is absent. Install Gradle 8.4+ (Android Studio
ships with a compatible version) and run:

```bash
./gradlew assembleDebug
```

The minimum supported Android version is API level 26 (Android 8.0), and the target SDK is 34.
