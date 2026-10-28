#!/usr/bin/env python3
"""Build 96 original, immediately-triggered 48 kHz stereo sound effects.
No copyrighted samples, remote downloads, or external Python dependencies.
Includes an alternate 'destroyed mic' effect bank for Android playback.
"""
from pathlib import Path
import math
import random
import sys
import wave
from array import array

RATE=48000
CATALOG=[
    "BASS BOOM",
    "AIR HORN",
    "BONK",
    "BOING",
    "LASER",
    "ERROR",
    "SCRATCH",
    "GLITCH",
    "DRAMATIC",
    "POP",
    "SIREN",
    "LEVEL UP",
    "FAIL",
    "PHONE RING",
    "SUSPENSE",
    "CARTOON ZIP",
    "CHIRP",
    "METAL HIT",
    "REWIND",
    "WHOOSH",
    "ALARM",
    "ARCADE",
    "BEEP SPAM",
    "SPACE BLIP",
    "RECORD STOP",
    "8-BIT HIT",
    "DARK PULSE",
    "CARTOON SLIDE",
    "RADIO STATIC",
    "BELL",
    "PING",
    "TRUMPET",
    "BUBBLE",
    "BASS DROP",
    "UFO",
    "CLOCK TICK",
    "VHS GLITCH",
    "SMASH",
    "DIZZY",
    "SPARK",
    "GAME OVER",
    "SUCCESS",
    "WARNING",
    "POWER UP",
    "SQUEAK",
    "SCREAM SYNTH",
    "KNOCK",
    "SCI-FI SHOT",
    "BRUH BASS",
    "SHEESH WHISTLE",
    "CLOWN HONK",
    "RUBBER DUCK",
    "AWKWARD BUZZ",
    "VINE BOOM",
    "SNORT",
    "SPROING",
    "ROUND LOST",
    "ROUND WON",
    "ITEM FOUND",
    "BOSS ARRIVES",
    "NOPE BUZZER",
    "CHECKPOINT",
    "HIT CONFIRM",
    "TIME OUT",
    "JUMPSCARE",
    "CREEPY CHOIR",
    "GHOST SWOOSH",
    "HEARTBEAT",
    "DOOR CREAK",
    "DARK DRONE",
    "WHISPER SYNTH",
    "EVIL GIGGLE",
    "DATA CRASH",
    "DIGITAL BURST",
    "MODEM BLIP",
    "CYBER SIREN",
    "STATIC SWEEP",
    "PIXEL BREAK",
    "ROBOT BLIP",
    "SIGNAL LOST",
    "VICTORY FANFARE",
    "TENSION STING",
    "8BIT BOUNCE",
    "MYSTERY CHORD",
    "SAD TROMBONE",
    "RAPID DRUM",
    "BATTLE INTRO",
    "LAST SECOND",
    "CHAOS HORN",
    "MIC DESTROYED",
    "MEGA BOOM",
    "HYPER BEEP",
    "WOBBLE BASS",
    "BRAIN FREEZE",
    "ALIEN SQUEAL",
    "OVERLOAD"
]
CATEGORIES=["LOUD / BASS","FUNNY / CARTOON","DRAMA / MEMES","ARCADE / RETRO","EFFECTS / SPACE","ALERTS / EXTRA","MEME REACTIONS","GAMING / FAILS","HORROR / HAUNTED","GLITCH / TECH","MUSIC / STINGS","CHAOS / TROLL"]
assert len(CATALOG)==96 and len(CATEGORIES)==12

def duration(index):
    if index in (4,9,16,22,30,39,46,52,60,62,73,78,91):return 0.34
    if index in (0,1,8,10,14,20,26,33,37,40,53,59,64,65,69,80,81,88,89,90):return 0.86
    return 0.43 + (index*11%20)/100.0

def sample_waves(index):
    seconds=duration(index)
    count=round(RATE*seconds)
    rng=random.Random(77411+index*29)
    phase=0.0
    phase2=0.0
    state=0.0
    prev=0.0
    group=index//8
    variant=index%8
    bass=70+(index*41)%700
    samples=[]
    for i in range(count):
        t=i/RATE
        pos=i/count
        attack=min(1.0, t/0.001)
        release=min(1.0,max(0.0,(seconds-t)/0.012))
        envelope=attack*release*(1-pos)**(0.20+(variant%3)*.22)
        # Category-specific modulation keeps the catalog from sounding identical.
        if group in (0,6,11):
            frequency=bass*(1.65-.95*pos)+30*math.sin(24*t)
        elif group in (1,7):
            frequency=bass*(1+.19*math.cos(2*math.pi*(7+variant)*t)*math.exp(-t*3))
        elif group in (2,8):
            frequency=bass*.74+(130 if variant%2==0 else 300)*(1-pos)
        elif group in (3,9):
            frequency=bass+(variant+2)*75*math.floor(pos*6)/6
        else:
            frequency=bass+300*math.sin(t*(9+variant)*2*math.pi)*(1-.5*pos)
        frequency=max(40,min(9000,frequency))
        phase+=2*math.pi*frequency/RATE
        phase2+=2*math.pi*(frequency*(.49+0.015*variant))/RATE
        pure=math.sin(phase)
        sub=math.sin(phase2)
        noise=rng.random()*2-1
        state+=.12*(noise-state)
        if variant==0:
            tone=pure+.48*sub+.15*math.sin(2*phase)
        elif variant==1:
            tone=pure+.29*math.sin(3*phase)+.19*state
        elif variant==2:
            tone=pure*.8+state*.7*math.exp(-pos*7)
        elif variant==3:
            tone=pure+.44*math.sin(phase+.6*math.sin(phase2))
        elif variant==4:
            tone=pure*.82+.22*math.sin(4*phase)
        elif variant==5:
            tone=(pure+.3*sub)*(0.6+.4*math.sin(2*math.pi*(9+group)*t))
        elif variant==6:
            tone=pure*.6+.55*state
        else:
            tone=pure+.25*math.sin(phase*1.4)+.24*math.sin(2*phase)
        if group in (5,9,11):
            tone*=.55+.45*math.tanh(5*math.sin(2*math.pi*(6+variant)*t))
        if group in (0,8,11):
            tone+=.22*sub
        if index in (9,16,17,30,38,46,62,78):
            envelope*=math.exp(-pos*5)
        # Soft peak rounding: avoids clipping on any single sound.
        value=math.tanh(tone*.7)*envelope
        samples.append(value)
    peak=max(.0001,max(abs(x) for x in samples))
    return [x*.79/peak for x in samples]

def write_wav(destination,samples,chaos=False):
    destination.parent.mkdir(parents=True,exist_ok=True)
    raw=array('h')
    prev=0.0
    hold=0.0
    # Chaos is *deliberately* crunchy, yet capped to avoid full-scale clipping.
    for i,v in enumerate(samples):
        if chaos:
            if i%19==0:hold=v
            crushed=round(math.tanh(hold*4.0)*9)/9
            value=max(-.68,min(.68,crushed*.69+.18*math.tanh(v*3)))
        else:
            value=v
        # Stereo is phase-safe so mono voice-chat downmix won't disappear.
        right=.97*value+.03*prev
        prev=value
        raw.append(round(max(-.90,min(.90,value))*32767))
        raw.append(round(max(-.90,min(.90,right))*32767))
    with wave.open(str(destination),"wb") as wav:
        wav.setnchannels(2)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(raw.tobytes())

def main():
    if len(sys.argv)<2 or sys.argv[1] not in ("android","windows","both"):
        raise SystemExit("Usage: generate_sounds.py android|windows|both")
    mode=sys.argv[1]
    root=Path(__file__).resolve().parent.parent
    for index in range(len(CATALOG)):
        samples=sample_waves(index)
        if mode in ("android","both"):
            raw=root/"app/src/main/res/raw"
            write_wav(raw/f"gill_sfx_{index:02d}.wav",samples)
            write_wav(raw/f"gill_chaos_{index:02d}.wav",samples,chaos=True)
        if mode in ("windows","both"):
            write_wav(root/"windows/assets"/f"gill_sfx_{index:02d}.wav",samples)
    print(f"Generated {len(CATALOG)} effects for {mode} (Android includes Chaos variants).")

if __name__=="__main__":
    main()
