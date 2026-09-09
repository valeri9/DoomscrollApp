# On-device setup — Samsung S24 Ultra (One UI)

Four things to grant, none of which use a normal runtime permission dialog. The Setup screen in
the app shows the live status of each and deep-links to the right settings page.

## 1. Developer options + USB debugging

Needed only for installing from the Mac and reading logs.

- Settings → About phone → Software information → tap **Build number** seven times
- Settings → Developer options → **USB debugging** → on
- Plug in over USB, accept the RSA fingerprint prompt on the phone

## 2. Accessibility service — required

This is how the app tells a Reels feed apart from a DM thread.

- Settings → Accessibility → Installed apps → **Doomscroll** → on

One UI shows a broad "full control of your device" warning. That warning is inherent to every
accessibility service and cannot be narrowed; what this app actually does with the access is read
view ids on the apps you monitor, and nothing else leaves the device.

**If the toggle is greyed out**, that is Android 13+ *Restricted Settings*, which blocks
sideloaded apps from accessibility until you allow it explicitly:

- Settings → Apps → Doomscroll → ⋮ (top right) → **Allow restricted settings**

## 3. Display over other apps — required

Draws the pause screen on top of the app you're scrolling.

- Settings → Apps → Doomscroll → **Appear on top** → Allow

## 4. Usage access — needed for the stats dashboard

Imports your existing whole-phone screen time so the dashboard isn't empty on day one.

- Settings → Apps → ⋮ → Special access → **Usage data access** → Doomscroll → Allow

## 5. Battery — recommended

One UI aggressively sleeps background work. The accessibility service is kept alive by the system
regardless, but the periodic usage-stats sync needs this.

- Settings → Apps → Doomscroll → Battery → **Unrestricted**
- Settings → Battery → Background usage limits → confirm Doomscroll is **not** listed under
  *Sleeping apps* or *Deep sleeping apps*

## Granting from the Mac instead

With USB debugging on, all of the above except the accessibility toggle's restricted-settings
gate can be granted without touching the phone:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell appops set com.valeri.doomscroll SYSTEM_ALERT_WINDOW allow
adb shell appops set com.valeri.doomscroll GET_USAGE_STATS allow
adb shell settings put secure enabled_accessibility_services \
  com.valeri.doomscroll/com.valeri.doomscroll.service.DoomscrollAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

## After reinstalling

Installing a new build silently **unbinds the accessibility service**, and writing the same
value back to the setting does not rebind it — the system only reacts to a change. Delete it
first:

```bash
adb shell settings delete secure enabled_accessibility_services
adb shell settings put secure accessibility_enabled 0
adb shell settings put secure enabled_accessibility_services \
  com.valeri.doomscroll/com.valeri.doomscroll.service.DoomscrollAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

Confirm it came back with `adb logcat -d | grep "Doomscroll: connected"`. If you skip this,
the app looks installed and enabled but receives no events at all.

## Watching it work

```bash
adb logcat -s Doomscroll:V DoomscrollClass:V DoomscrollOverlay:V
```

Every classification decision is logged as
`com.instagram.android [ClassName] -> DOOMSCROLL via VIEW_ID:clips_viewer_view_pager | armed=true scrolls=2`,
which is the fastest way to see whether the shipped view ids match your build of the app.
