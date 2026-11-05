# Viyzo Media Hub Android demo

This isolated Android WebView shell packages the Media Studio dashboard as a debug APK. It does NOT bundle the Node.js owner API, video renderers, provider credentials, Meta OAuth, Facebook Page publishing or cloud storage. The dashboard therefore runs in local demo mode on the phone; real owner login and production workflows require a separately deployed, secured backend.

Build with Gradle 8.11.1 and JDK 17: copy `../index.html` to `app/src/main/assets/index.html`, then run `gradle assembleDebug`.

Do not use this debug APK as a production release. No Viyzo Worker Android source or services are included.
