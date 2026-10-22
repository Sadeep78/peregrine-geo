# Peregrine Manual Smoke Testing Guide & Hardware Verification

This document details the exact hardware and emulator verification procedures for tests that require a physical Android device, active Android Emulator (API 29+), or a local Docker daemon.

---

## 1. Environment Requirements

- **Android SDK**: `adb` configured in PATH.
- **Test Devices**:
  - Android Emulator running API 29 (Android 10) or API 34+ (Android 14+).
  - Physical Android test device (for overlay and TalkBack accessibility testing).
- **Docker**: Docker Desktop / Engine running locally (for `compose.dev.yaml` services).

---

## 2. Android Mock Location Engine Hardware Verification (Section 3.1)

### Step 2.1: Grant Mock Location Permission
Connect device via USB and verify ADB connection:
```bash
adb devices
```

Grant mock location appops permission to Peregrine:
```bash
adb shell appops set dev.peregrinegeo.peregrine android:mock_location allow
```

Verify permission is granted:
```bash
adb shell appops get dev.peregrinegeo.peregrine android:mock_location
# Expected output: android:mock_location: allow
```

### Step 2.2: Launch App and Start Mocking
1. Launch Peregrine on device.
2. Select a point or paste coordinates (e.g. `37.7749, -122.4194`).
3. Press **Start Mock**.
4. Confirm ongoing foreground service notification appears with "Peregrine Mock Active", showing current coordinates, speed, and **Pause** / **Stop** action buttons.

### Step 2.3: Verify Location Injection via System Location Subsystem
Query Android LocationManager dumpsys:
```bash
adb shell dumpsys location
```
**Expected Verification**:
- `Last Known Locations:` shows `gps` and `network` providers updated with `37.7749, -122.4194`.
- Provider flags confirm mock provider enabled.
- Verify through Google Maps or any location-consumer test application that the blue dot appears at the mock coordinate.

### Step 2.4: 5-Minute Background Stability Test
1. Press **Home** button on the device to send Peregrine to background.
2. Keep app in background for 5 minutes while kinematic route replay or point mock is running.
3. Confirm mock location updates continue uninterrupted.
4. Check logcat for any ANR or foreground service timeout:
```bash
adb logcat -d -s ActivityManager:W PeregrineMock:D
```

### Step 2.5: Stop Mock and Confirm Provider Cleanup
1. Tap **Stop** in the notification or open the app and tap **Stop Mock**.
2. Query `dumpsys location` again:
```bash
adb shell dumpsys location
```
**Expected Verification**:
- Test providers `gps` and `network` are completely removed.
- System returns to real GNSS sensor provider with zero leftover test providers.

---

## 3. Floating Overlay and Joystick Testing (Section 3.8)

### Step 3.1: Grant Overlay Permission
```bash
adb shell pm grant dev.peregrinegeo.peregrine android.permission.SYSTEM_ALERT_WINDOW
```

### Step 3.2: Joystick Interaction
1. Toggle Floating Overlay on in settings.
2. Deflect joystick in various directions:
   - Deflect slightly: verify low-speed displacement ($\sim 1\text{ m/s}$).
   - Deflect fully: verify speed ramps smoothly up to profile limit ($18\text{ m/s}$).
   - Release joystick: verify instantaneous halt (or coasting deceleration depending on settings).
3. Tap minimize bubble: HUD collapses to compact floating circle.
4. Drag floating bubble to screen edge: snaps cleanly without occluding essential navigation.

---

## 4. Accessibility & TalkBack Verification

1. Enable TalkBack in Android Settings > Accessibility > TalkBack.
2. Navigate through Peregrine screens:
   - Onboarding checklist
   - Map controls (recenter, layer toggle, search)
   - Route Studio drawer and profile selector
   - Active mock HUD and notification actions
3. Verify all touch targets measure at least $48\times 48\text{ dp}$.
4. Verify mock state is announced with text and spoken feedback, never indicated by color alone.

---

## 5. Dev Cloud Services Verification (Section 2 & 3.5)

On a host machine with Docker installed:
```bash
# 1. Start dev services
just cloud-up
# Alternatively: docker compose -f compose.dev.yaml up -d postgres redis

# 2. Verify healthchecks
docker compose -f compose.dev.yaml ps
# Expected: peregrine-postgres (healthy), peregrine-redis (healthy)

# 3. Test migrations
npx --prefix backend prisma migrate deploy

# 4. Stop containers
just cloud-down
```
