# GILL Soundboard - Windows laptop edition

**No ads. No accounts. No subscriptions. No file uploads.** 48 self-contained original stereo sounds, generated at **48 kHz** during the Windows build.

The app has instant triggering, up to 12 overlapping voices, a peak limiter, search, six categories, adjustable output level, a hold-to-repeat spam mode (75/100/130/200/300 ms), and optional microphone mixing.

## Install / open

Download the **GILL-Soundboard-Windows** artifact from GitHub Actions, extract the ZIP, and open **GILL-Soundboard.exe** on a Windows 10 or Windows 11 laptop. You do not need Python.

## Send effects and your real microphone to Roblox

Windows cannot transparently send another program's audio to your microphone input without a virtual device. Use the **VB-CABLE Virtual Audio Device** from its official publisher VB-Audio. After installing its driver and restarting Windows if asked:

1. In the GILL app choose **CABLE Input** as the **OUTPUT**.
2. Choose your real headset microphone as **MIC INPUT**.
3. Click **CONNECT** (status should say ACTIVE / MIC + SOUNDS).
4. In Roblox's voice settings, set **Microphone** to **CABLE Output**.
5. Click / hold any sound button. Your friend's voice and effects are mixed into the same cable.

Audio routing names are easy to confuse: applications **play into** CABLE Input; Roblox **records from** CABLE Output. The free virtual driver needs to be installed separately; this app cannot install audio drivers.

If your computer cannot open the mic and cable as a duplex stream, try matching sample rates to 48 kHz in Windows Sound settings; use a matching Windows audio host API for both devices; turn off exclusive mode in Windows sound device properties; or select a different device.

## Technical notes

The sound effects are original synthesized effects, **not** familiar copyrighted voice or meme recordings. The WAVs are bundled into the Windows EXE at build time; no uploads, downloads, or network access are needed after installation.

The app uses an already-open PortAudio stream with 256-sample blocks for low latency, 48 kHz stereo samples cached in RAM, up to 12 overlapping voices, and tanh soft limiting to reduce clipping. It cannot eliminate Bluetooth latency, noisy hardware, or Roblox's microphone compression and noise suppression.

### Build from source

Use Python 3.11+:
```sh
python soundboard/tools/generate_sounds.py windows
pip install numpy sounddevice pyinstaller
pyinstaller --noconfirm --onefile --windowed --name GILL-Soundboard --add-data "soundboard/windows/assets;assets" --collect-all _sounddevice_data soundboard/windows/gill_soundboard.py
```
