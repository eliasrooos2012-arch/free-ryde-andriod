# Ryde Controller — Android BLE diagnostics / configurable ON/OFF

**Status: prototype, NOT a verified scooter-unlocking APK.**

This Android application discovers nearby BLE devices, connects to a selected device, lists its GATT services and characteristics, copies a diagnostic report, and sends **explicitly configured** hexadecimal ON/OFF payloads to a chosen writable characteristic. It contains no guessed Ryde commands and does not interact with Ryde's fleet API or bypass authentication.

The official Ryde fleet uses IoT hardware, and it is **not known whether this particular decommissioned scooter supports Bluetooth control**. If no usable BLE device appears, a local Bluetooth-only app cannot control the scooter. The seller or Ryde may need to provide a documented local protocol or assistance replacing the controller for private use.

## Build an APK on Windows using Android Studio

1. Install Android Studio: <https://developer.android.com/studio>
2. Extract this ZIP, open the `RydeController` folder in Android Studio.
3. Accept requests to install Android SDK 35 and Gradle tooling; use JDK 17 if prompted.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. The APK should be located at `app/build/outputs/apk/debug/app-debug.apk`.
6. Copy the APK to your Android phone, allow installation from that source, and install it.

**Alternative (GitHub Actions)**: Upload the project files to a GitHub repository (including the hidden `.github` directory), open **Actions > Build Android APK > Run workflow** and, after a successful run, download the `RydeController-debug-apk` artifact. The workflow uses Gradle 8.10.2 and Android SDK 35.

## How to use

1. Leave the scooter stationary. Open the app, grant Bluetooth permissions, tap **SCAN FOR BLUETOOTH**.
2. Choose the likely device, then **COPY DIAGNOSTIC REPORT** and share its GATT UUID list with someone who knows your controller.
3. If the controller's genuine, authorized protocol is known, select the documented writable GATT characteristic, enter the proper ON/OFF payloads as hex, and save.
4. Only then do the ON/OFF buttons become available. Android reporting a successful GATT write **does not prove** that the scooter powered on/off.

## Important limitations

- Screenshots or the Ryde name do not establish the BLE service UUIDs, command bytes, authentication, or even whether Bluetooth is implemented.
- No Wi-Fi, internet/cloud connection, SIM, GNSS or Ryde app access is included.
- If the scooter has a proprietary cellular-only IoT module, this app cannot activate it.
- Do not guess GATT commands or use a BLE write console without documentation; unintended hardware behavior is possible.
- Test with the drive wheel clear of the ground, and never use this app to stop or brake a moving scooter.

## Privacy

The app does not use analytics, location tracking, internet access, or external dependencies. Android 8–11 requires location permission to scan for BLE due to Android platform rules; Android 12+ uses Nearby Devices permissions. Scan results and copied reports stay on the phone unless you share them.

This project is an independent hobby prototype and is not affiliated with Ryde Technology.
