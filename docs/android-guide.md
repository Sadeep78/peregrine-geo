# Peregrine Android User & QA Guide

This guide details the installation, setup, permission configuration, and operational checklist for using the Peregrine Android mock location client (`dev.peregrinegeo.peregrine`).

---

## 1. System Requirements

- **Operating System**: Android 10 (API level 29) or higher.
- **Architecture**: `arm64-v8a`, `armeabi-v7a`, `x86_64` (emulators supported).
- **Google Play Services**: Optional (Fused Location Provider integration is supported when present).

---

## 2. Onboarding Checklist

When launching Peregrine for the first time, complete the 5-step checklist displayed on the setup screen:

### Step 1: Enable Developer Options
1. Open Android **Settings** > **About phone**.
2. Tap **Build number** 7 times until you see the message *"You are now a developer!"*.

### Step 2: Select Peregrine as Mock Location App
1. Go to **Settings** > **System** > **Developer options**.
2. Scroll to the **Debugging** section and tap **Select mock location app**.
3. Choose **Peregrine** from the list.

### Step 3: Disable Google Location Accuracy (Crucial)
1. Go to **Settings** > **Location** > **Location services** > **Google Location Accuracy**.
2. Toggle **Improve Location Accuracy** to **OFF**.
> [!IMPORTANT]
> If Google Location Accuracy remains enabled, Google Play Services Wi-Fi and cellular fusion can override simulated GPS mock fixes with physical radio measurements.

### Step 4: Grant Location & Notification Permissions
- Grant **Precise Location (Fine Location)** permission when prompted.
- Grant **Notifications** permission (Android 13+ / API 33+) so the foreground playback service can display notifications with Play/Pause/Stop actions.

### Step 5: (Optional) Floating Overlay Permission
- To use the floating joystick over third-party apps, enable **Display over other apps** (`SYSTEM_ALERT_WINDOW`).

---

## 3. Battery Optimization Exemption

To ensure long kinematic simulation routes do not get paused when the device screen turns off:
1. Go to **Settings** > **Apps** > **Peregrine** > **Battery**.
2. Select **Unrestricted** (disables Doze mode network and CPU throttling for Peregrine).

---

## 4. Key Features & Workflows

### 4.1 Mocking a Single Point (US-01)
1. Tap any point on the map, or paste decimal coordinates (`37.7749, -122.4194`), DMS coordinates (`37°46'29.6"N 122°25'09.9"W`), or a Google Maps URL into the search bar.
2. The point is displayed in **Teal (Preview Mode)**.
3. Tap **Start Mock** to activate. The marker pulses in **Emerald (Active Playing)**.

### 4.2 Building and Simulating a Route (US-02)
1. Tap the **Route Mode** icon.
2. Tap multiple waypoints on the map.
3. Choose a **Motion Profile**:
   - `Walk`: $1.4\text{ m/s}$ ($5.0\text{ km/h}$)
   - `Run`: $3.5\text{ m/s}$ ($12.6\text{ km/h}$)
   - `Bicycle`: $6.0\text{ m/s}$ ($21.6\text{ km/h}$)
   - `Drive`: $18.0\text{ m/s}$ ($65.0\text{ km/h}$)
4. Tap **Start Mock**. The device will accelerate smoothly, slow down for turns, and stop at the destination.

### 4.3 Replaying GPX / KML Tracks (US-03)
1. Tap **Import** in the Library tab.
2. Select any standard `.gpx`, `.kml`, or `.geojson` file.
3. Choose **Replay Timing**:
   - *Original Timestamps*: Preserves exact recorded timing.
   - *Constant Speed*: Re-simulates with configured kinematic profile.

---

## 5. Troubleshooting & Diagnostics

| Symptom | Cause | Solution |
|---|---|---|
| SecurityException on Start Mock | Peregrine is not selected as the mock app | Re-open Developer Options and select Peregrine as the mock location app. |
| Location jumps between mock and real GPS | Google Location Accuracy is turned on | Turn off *Improve Location Accuracy* in Android Location Services. |
| Mock stops when screen turns off | Android Doze mode killed background service | Set Peregrine battery usage to *Unrestricted*. |
| Fleet pairing fails | Incorrect pairing code or expired | Generate a fresh 8-character code from the Web Console and enter it within 10 minutes. |
