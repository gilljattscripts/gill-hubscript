# GILL Soundboard (Android)

A small, original Android soundboard with **no ads, analytics, accounts, tracking or network permissions**.

## Features
- Floating, draggable soundboard over other apps via Android's Display Over Other Apps permission.
- Minimize to a small movable pill; lock position; resize using SIZE - and SIZE +.
- Eight original synthesized sound effects, playable without downloading audio.
- Import MP3, WAV, OGG, M4A or other Android-supported audio files using the system file picker.
- Imported clips are stored as Android document references, not duplicated into app storage.
- Stop sound button and global volume slider.
- Foreground media-playback service with a notification and a stop action.

## Install
Download the **gill-soundboard-debug** GitHub Actions artifact, extract the ZIP and install app-debug.apk on Android.
On first launch, tap OPEN FLOATING SOUNDBOARD and allow Display Over Other Apps.
Then return to the app, open the panel and switch to Roblox.
Install only APKs you generated from a project you trust.

## Build
Requires JDK 17, Android SDK 35 and Gradle 8.7.
From this directory: `gradle assembleDebug`

## Important audio limitation
This app plays sounds over Android's normal media audio output. It does not access, redirect, spoof or inject microphone audio.
Whether sounds are transmitted in Roblox voice chat depends on the phone, Android audio path, other apps, and Roblox.
Using a floating overlay does not bypass Roblox restrictions.

## Other notes
Built with Android platform APIs; no third-party ad SDKs, dependencies or copyrighted sounds.
Android 8.0+; targets Android 13 for compatibility when sideloaded.
The app source lives in this folder and does not change the MM2 Lua scripts elsewhere in the repository.
