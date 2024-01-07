# Installation

## Prerequisites

1. A recent edition of [Android Studio](https://developer.android.com/studio). The author is using Android Studio Hedgehog 2023.1.1.
2. A DJI aircraft supported by the DJI Mobile SDK V5. You can find a list of supported products [on their website](https://developer.dji.com/mobile-sdk/).
3. The remote controller associated with your aircraft. You'll connect this to a tablet or phone via USB.
4. An Android device running Android 8.0 or later. I haven't tried connecting to DJI using the emulator, but it should be possible via [USB passthrough](https://source.android.com/docs/automotive/start/passthrough).

## Build

1. Open the project in Android Studio.
2. Synchronize the Gradle project.
3. Connect to your Android device over WiFi via [adb](https://developer.android.com/tools/adb). You need to connect via WiFi because you'll need to connect the device and the remote controller via USB.
4. Connect the Android device and the remote controller via USB.
5. Click the "run" icon.
