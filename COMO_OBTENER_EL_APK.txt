MOORTY VOICE ANDROID — BUILD 0.2.0
==================================

This project ports the main Android-side experience from the working PC version.

IMPLEMENTED IN THIS BUILD
- Native Android interface in dark/red style.
- YouTube Data API v3 configuration: API key and channel ID.
- Foreground voice service with Android TextToSpeech (Spanish Mexico locale).
- Finds a public active YouTube LIVE and its active live chat.
- Reads new chat comments, membership events, gifted memberships, Super Chats and Super Stickers when YouTube exposes them in the live chat.
- Checks concurrent viewer count approximately once per minute and announces increases when the API returns the count.
- Notification/status updates, stop control, and voice test.
- Event switches for chat and support events.

NOT YET PORTED / LIMITATIONS
- TikTok LIVE connection is NOT implemented in this Android build. The PC program uses the Python TikTokLive community library; it cannot simply be copied into a native Kotlin app. TikTok requires a separate compatible Android connector or a companion bridge.
- Normal YouTube subscriber alerts are NOT implemented here. The PC version uses Google OAuth and `myRecentSubscribers`; an API key and channel ID alone are insufficient. Public subscriber visibility and YouTube API permissions also limit what can be announced.
- OBS desktop audio routing is not included in the phone app. Android phone TTS plays through the phone's selected audio output.
- YouTube Data API quota is limited. `search.list` is expensive and chat polling also consumes quota. Restrict the API key to YouTube Data API v3 and monitor quota in Google Cloud.
- YouTube does not expose a reliable public API event for every ordinary subscriber or every viewer joining.
- TikTokLive in the PC version is an unofficial community integration and may break when TikTok changes its service.

BUILD
1. Upload this project to your GitHub repository.
2. Open Actions → Build Moorty Voice APK.
3. Run the workflow with Run workflow.
4. Download the MoortyVoice-debug-APK artifact and extract the APK.
5. Install it on Android and grant notification permission if prompted.

CONFIGURATION
- Enter the YouTube Data API v3 key and the channel ID (usually starts with UC).
- The API key is saved in this app's private local preferences; do not share the device or APK with a preconfigured key.
- This build does not ask for Google OAuth or a TikTok password/token.
- Keep network access enabled. A public active YouTube live stream with chat enabled is required.

Build requirements: Android Studio/Gradle, JDK 17, Android SDK 35.
