#!/usr/bin/env python3
"""Generate 48 original, stereo 48 kHz sounds for Android and Windows builds.
No downloads, external audio packages, or copyrighted samples.
The onsets begin within 2 ms; fade tails prevent speaker clicks.
"""
from pathlib import Path
import math
import random
import sys
import wave
from array import array

RATE = 48000
LENGTHS = [0.92,0.70,0.38,0.52,0.34,0.46,0.37,0.59,
    0.86,0.32,0.90,0.62,0.56,0.84,0.78,0.52,
    0.34,0.56,0.51,0.67,0.83,0.52,0.39,0.55,
    0.52,0.61,0.88,0.59,0.73,0.61,0.30,0.68,
    0.45,0.81,0.85,0.49,0.71,0.48,0.53,0.35,
    0.64,0.61,0.76,0.68,0.43,0.48,0.35,0.41]

def generate(index):
    n = round(RATE * LENGTHS[index])
    length = n / RATE
    rng = random.Random(90341 + index * 137)
    frames = array('h')
    phase = 0.0
    filtered_noise = 0.0
    delayed = [0.0] * 19
    waveform = []
    base = 90 + index * 31 % 660
    family = index % 8
    for i in range(n):
        t = i / RATE
        p = i / n
        # Sound begins immediately; short release removes hard cut/clicks.
        attack = min(1.0, t / .0015)
        release = min(1.0, (length - t) / .014)
        envelope = max(0.0, attack * release) * (1 - p) ** .25
        if family == 0: frequency = base * (1.8 - 1.3 * p) + 14 * math.sin(27 * t)
        elif family == 1: frequency = base + 200 * math.sin(12 * t)
        elif family == 2: frequency = base * (1 + 0.2 * math.cos(60 * t) * math.exp(-6 * t))
        elif family == 3: frequency = base + 740 * math.exp(-10 * p)
        elif family == 4: frequency = base + 900 * (1-p)**3
        elif family == 5: frequency = base + 280 * int(p * 5) / 5
        elif family == 6: frequency = base + 190 * abs(math.sin(28 * t))
        else: frequency = base + 80 * math.sin(2 * math.pi * 14 * t)
        frequency = max(30, min(11000, frequency))
        phase += 2 * math.pi * frequency / RATE
        tone = math.sin(phase) + .22 * math.sin(2 * phase) + .10 * math.sin(3 * phase)
        # Filtered noise / transient layer, blended only where useful.
        random_sample = rng.random() * 2 - 1
        filtered_noise += .13 * (random_sample - filtered_noise)
        if index in (2,6,17,19,28,36,37,45):
            tone += filtered_noise * 1.2
        if index in (5,13,20,22,35,42,46):
            # Rounded pulses, not harsh hard-edged gating.
            gate = 0.5 + 0.5 * math.tanh(4 * math.sin(2 * math.pi * 10 * t))
            tone *= gate
        if index in (0,8,14,26,33,37):
            tone += .33 * math.sin(phase*.50)
        if index in (9,17,29,30,39):
            envelope *= math.exp(-7.2 * p)
        if index in (1,10,20):
            tone += .26 * math.sin(phase * .94)
        # Gentle saturator rounds peaks without hard digital clipping.
        sample = math.tanh(tone * .62) * envelope
        waveform.append(sample)

    peak = max(0.01, max(abs(v) for v in waveform))
    scale = 0.77 / peak
    for i, value in enumerate(waveform):
        left = value * scale
        # Very small stereo widening; avoids phasey, muffled sound.
        right = (0.97 * value + 0.03 * delayed[i % len(delayed)]) * scale
        delayed[i % len(delayed)] = value
        frames.append(round(max(-1.0, min(1.0, left)) * 32767))
        frames.append(round(max(-1.0, min(1.0, right)) * 32767))
    return frames

def write_sound(index, folder, filename):
    folder.mkdir(parents=True, exist_ok=True)
    destination = folder / filename
    with wave.open(str(destination), 'wb') as audio:
        audio.setnchannels(2)
        audio.setsampwidth(2)
        audio.setframerate(RATE)
        audio.writeframes(generate(index).tobytes())

def main():
    if len(sys.argv) < 2:
        raise SystemExit('Usage: generate_sounds.py android|windows|both')
    mode = sys.argv[1].lower()
    root = Path(__file__).resolve().parent.parent
    for index in range(48):
        if mode in ('android','both'):
            write_sound(index, root / 'app/src/main/res/raw', f'gill_sfx_{index:02d}.wav')
        if mode in ('windows','both'):
            write_sound(index, root / 'windows/assets', f'gill_sfx_{index:02d}.wav')
    print(f'Generated 48 original 48 kHz stereo sounds for {mode}.')

if __name__ == '__main__':
    main()
