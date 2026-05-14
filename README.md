# EveningLock

EveningLock is a personal digital wellbeing utility for Android that acts as a strict "set and forget" app locker. By leveraging Android's Device Owner (DevicePolicyManager) API, it aggressively blocks access to distracting applications during evening hours to help enforce better sleep hygiene. 

Because it operates as a Device Owner, it is much more powerful than standard accessibility-based app blockers and cannot be easily bypassed on the device.

## Features

- **Automated Scheduling**: Automatically locks designated apps at 9:00 PM and unlocks them at 6:00 AM every day using Android's battery-efficient `WorkManager`.
- **Auto-Sync State**: Automatically self-heals and synchronizes your app lock states based on the current time whenever you open the app, ensuring you are never permanently locked out due to missed background jobs.
- **App Suspension**: Greys out distracting applications (e.g., Reddit, YouTube, Netflix, Disney+, Prime Video) so they cannot be launched. 
- **App Hiding (Entry Points)**: Completely hides "escape route" applications like Google Chrome and the Google Play Store from the launcher, preventing you from installing workarounds.
- **Relapse Prevention**: The app actively blocks its own uninstallation. The only way to remove it or bypass the lock is to connect the phone back to a computer and use ADB.
- **Adaptive Icon Support**: Designed to blend perfectly into the modern Android launcher interface using native circular adaptive icons.

## Installation & Setup

Because this app requires Device Owner privileges, it cannot be installed normally through an app store. It must be side-loaded and granted permissions via ADB.

1. **Build the APK**:
   ```bash
   ./gradlew assembleDebug
   ```

2. **Install the App**:
   Note: The `android:testOnly="true"` flag is used in the manifest to allow setting the Device Owner without a factory reset.
   ```bash
   adb install -r -t app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Set as Device Owner**:
   Run the following command to grant the app Device Owner privileges. 
   *(Note: This command will fail if you have active secondary users or work profiles on the device).*
   ```bash
   adb shell dpm set-device-owner --user 0 com.example.eveninglock/.MyDeviceAdminReceiver
   ```

4. **Initialize the Schedule**:
   Open the EveningLock app manually once. Opening the app initializes the background `WorkManager` schedules and locks the uninstallation mechanism.

## Customization

To change which apps are blocked, edit the arrays located in `app/src/main/java/com/example/eveninglock/LockWorker.kt` and `MainActivity.kt`:

- `distractions`: Apps in this list will be "suspended" (greyed out).
- `entryPoints`: Apps in this list will be "hidden" (removed entirely from the UI).

To adjust the schedule times, edit the target hours in `MainActivity.kt` inside the `scheduleEveningLock()` function.

## How to Uninstall

Since the app prevents its own uninstallation as a relapse guardrail, you must use ADB from a computer to remove it:

```bash
# First, remove the device owner privilege
adb shell dpm remove-active-admin com.example.eveninglock/.MyDeviceAdminReceiver

# Then uninstall the package
adb uninstall com.example.eveninglock
```

## License
MIT License. Feel free to fork and customize to build your own personal distraction blockers.
