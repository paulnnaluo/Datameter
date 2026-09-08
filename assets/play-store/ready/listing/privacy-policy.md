# Datameter Privacy Policy

Effective date: September 8, 2026

Datameter is a mobile data usage app developed by Chíjìọ́kẹ́ Paul. This Privacy Policy explains how Datameter accesses, uses, stores, and shares data.

Privacy contact: REPLACE_WITH_PUBLIC_SUPPORT_EMAIL

## Summary

Datameter helps you understand mobile data, Wi-Fi data, hotspot use, per-app usage, network deductions, local usage alerts, and optional app data controls.

Datameter does not require an account. Datameter does not upload your data usage details to our servers.

Datameter's optional Data Control feature uses Android VPN Service locally on your device to measure and block app network traffic according to rules you choose. Allowed traffic is forwarded to the destination requested by the app; it is not sent through Datameter servers.

## Data Datameter Accesses

Datameter may access the following data on your device:

- Network usage data, including mobile data, Wi-Fi data, hotspot/tethering usage, and usage totals over time.
- App usage attribution returned by Android, including app package identifiers, per-app data usage, app labels, and app icons.
- Installed app package information, used to show the full app list for Data Control rules.
- Local VPN traffic metadata for Data Control, including app UID, protocol, source and destination addresses, connection byte totals, and whether Datameter allowed or blocked the connection.
- Audit inputs you type into the app, such as network name and current data balance.
- Alert settings you choose, such as daily data limits and hourly spike thresholds.
- Data Control rules you choose, such as per-app mobile data blocks, Wi-Fi blocks, and app usage limits.
- Alert delivery state, such as whether Datameter already sent a specific local alert.

## How Datameter Uses Data

Datameter uses this data to:

- Show your data usage dashboard.
- Show where mobile data, Wi-Fi data, and hotspot usage went.
- Compare what your phone measured with what your network deducted during a data audit.
- Send optional local notifications for usage limits and unusual spikes.
- Apply optional Data Control rules, including per-app mobile data blocks, Wi-Fi blocks, and automatic blocking after app data limits.
- Show a persistent notification while Data Control is active.
- Keep enabled local alerts working after your phone restarts.

Datameter does not use this data for advertising, user profiling, or selling personal data.
Datameter does not upload your installed app list or app traffic details to Datameter servers.

## Permissions

Datameter uses Android permissions only for app functionality:

- Usage Access / PACKAGE_USAGE_STATS: used to read Android network usage history and show mobile data, Wi-Fi data, hotspot usage, and per-app usage.
- Android VPN Service: used only when you turn on Data Control, so Datameter can locally measure and block app network traffic on this phone.
- INTERNET: used by Data Control to forward allowed app traffic to the destination requested by the app. Datameter does not use Internet access to upload your usage details to Datameter servers.
- ACCESS_NETWORK_STATE: used by Data Control to detect whether the phone is currently on mobile data, Wi-Fi, or another network, and to keep the local VPN connected when networks change.
- QUERY_ALL_PACKAGES: used by Data Control to identify installed apps, show app names/icons, and apply app-level data rules across the device.
- FOREGROUND_SERVICE: used to keep Data Control running with a visible persistent notification while the local VPN is active.
- POST_NOTIFICATIONS: used only for optional local usage alerts.
- RECEIVE_BOOT_COMPLETED: used to reschedule enabled local usage alerts after the device restarts.

Datameter does not request location, contacts, camera, microphone, SMS, or call logs.

## Data Storage

Datameter stores app settings and audit state locally on your device using private Android app storage. This may include:

- Current audit session details.
- Last audit balance checks.
- Alert thresholds.
- Alert delivery markers.
- Data Control app rules.
- Daily per-app Data Control rollups.
- Recent app block events.

This data stays on your device unless you choose to share it outside the app using your own device tools.

## Data Sharing

Datameter does not sell your personal data.

Datameter does not share your usage data, app usage details, installed app list, audit inputs, alert settings, Data Control rules, or app block events with third parties.

Datameter currently does not send usage data to any server controlled by the developer.

## Data Retention and Deletion

Datameter keeps local app data for as long as the app remains installed or until you clear it.

You can delete Datameter's local data by:

- Uninstalling Datameter from your device.
- Clearing Datameter's app storage in Android system settings.

Because Datameter does not require user accounts, there is no Datameter account to delete.

## Children

Datameter is not designed for children and is not directed to children.

## Security

Datameter uses Android private app storage for local app data. Datameter does not transmit your usage data to the developer's servers. Datameter's Data Control feature does not inspect or store the content of your app traffic.

## Changes to This Policy

We may update this Privacy Policy when Datameter changes. The latest version will be published at the privacy policy link shown in Google Play and inside the app where available.

## Contact

For privacy questions, contact:

REPLACE_WITH_PUBLIC_SUPPORT_EMAIL
