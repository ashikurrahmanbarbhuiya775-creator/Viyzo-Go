# Viyzo Media Hub Android demo

This isolated Android WebView shell packages the Media Studio dashboard as a debug APK. It does NOT bundle the Node.js owner API, video renderers, provider credentials, Meta OAuth, Facebook Page publishing or cloud storage. The dashboard therefore runs in local demo mode on the phone; real owner login and production workflows require a separately deployed, secured backend.

Build with Gradle 8.11.1 and JDK 17: copy `../index.html` to `app/src/main/assets/index.html`, then run `gradle assembleDebug`.

Do not use this debug APK as a production release. No Viyzo Worker Android source or services are included.

## Phone UI polish
The Android wrapper now uses a branded launcher icon, native dark system bars, a compact loading indicator and phone-first bottom navigation. Version 1.0.0 is a demo build; online API access is not configured by default.
