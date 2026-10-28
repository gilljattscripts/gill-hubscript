"""GILL Soundboard typed speech: high-quality online voices, with Windows SAPI fallback.

Presets: Guy (male) and Jenny (female). Custom Edge TTS IDs can be added in app.
Speech is generated off the UI/audio threads and injected into the same 48 kHz
audio mixer as the sound effects. No personal voice cloning or hidden recording.
"""
import asyncio
import base64
import json
import os
from pathlib import Path
import subprocess
import tempfile
import wave
import numpy as np

DEFAULT_VOICES={
    "Male • Guy (neural)":"en-US-GuyNeural",
    "Female • Jenny (neural)":"en-US-JennyNeural",
    "Male • Ryan (neural)":"en-GB-RyanNeural",
    "Female • Sonia (neural)":"en-GB-SoniaNeural",
    "Male • Christopher (neural)":"en-US-ChristopherNeural",
    "Female • Aria (neural)":"en-US-AriaNeural",
    "Windows • male (offline)":"SAPI:Male",
    "Windows • female (offline)":"SAPI:Female",
}

def profiles_path():
    base=Path(os.environ.get("APPDATA") or Path.home())/"GILL Soundboard"
    base.mkdir(parents=True,exist_ok=True)
    return base/"custom_voices.json"

def custom_profiles():
    try:
        data=json.loads(profiles_path().read_text(encoding="utf-8"))
        if not isinstance(data,dict):return {}
        return {str(k)[:48]:str(v)[:150] for k,v in data.items()
                if isinstance(k,str) and isinstance(v,str) and k and v
                and len(str(k))<=48 and len(str(v))<=150}
    except Exception:return {}

def save_custom_profile(display_name,voice_id):
    name=display_name.strip()[:48]
    voice=voice_id.strip()[:150]
    if not name or not voice:raise ValueError("Enter a name and a voice ID")
    if not (voice.startswith("SAPI:") or
            (voice.endswith("Neural") and len(voice.split("-"))>=3)):
        raise ValueError("Use a Microsoft neural voice ID like en-US-GuyNeural, or SAPI:<voice name>")
    data=custom_profiles()
    data[name]=voice
    profiles_path().write_text(json.dumps(data,indent=2,ensure_ascii=False),encoding="utf-8")
    return data

def remove_custom_profile(name):
    data=custom_profiles()
    data.pop(name,None)
    profiles_path().write_text(json.dumps(data,indent=2,ensure_ascii=False),encoding="utf-8")

def _sapi_to_wav(text,voice,outfile):
    def encoded(value):
        return base64.b64encode(value.encode("utf-8")).decode("ascii")
    content=encoded(text)
    requested=encoded(voice.removeprefix("SAPI:"))
    output=encoded(str(outfile))
    script=fr"""
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Speech
$speaker=New-Object System.Speech.Synthesis.SpeechSynthesizer
$msg=[System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('{content}'))
$choice=[System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('{requested}'))
$path=[System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('{output}'))
if ($choice -eq 'Male' -or $choice -eq 'Female') {{
    $gender=if ($choice -eq 'Male') {{[System.Speech.Synthesis.VoiceGender]::Male}} else {{[System.Speech.Synthesis.VoiceGender]::Female}}
    try {{$speaker.SelectVoiceByHints($gender)}} catch {{}}
}} elseif ($choice -ne '') {{
    $speaker.SelectVoice($choice)
}}
$speaker.SetOutputToWaveFile($path)
$speaker.Speak($msg)
$speaker.Dispose()
"""
    cmd=base64.b64encode(script.encode("utf-16le")).decode("ascii")
    done=subprocess.run(["powershell.exe","-NoProfile","-NonInteractive","-EncodedCommand",cmd],
        stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=45,check=False)
    if done.returncode or not outfile.exists():
        raise RuntimeError("Windows speech synthesis failed; check installed voices")

def _from_wav(path):
    with wave.open(str(path),"rb") as wav:
        data=wav.readframes(wav.getnframes())
        channels=wav.getnchannels()
        sample_rate=wav.getframerate()
        width=wav.getsampwidth()
    if width!=2:raise RuntimeError("Unsupported Windows speech audio format")
    x=np.frombuffer(data,dtype="<i2").astype("float32")/32768.0
    x=x.reshape(-1,channels)
    if channels==1:x=np.repeat(x,2,axis=1)
    elif channels>2:x=x[:,:2]
    if sample_rate!=48000:
        duration=len(x)/sample_rate
        new_count=max(1,round(duration*48000))
        t=np.arange(new_count,dtype=np.float32)*sample_rate/48000
        i=np.minimum(t.astype(np.int64),len(x)-1)
        j=np.minimum(i+1,len(x)-1)
        f=(t-i).reshape(-1,1)
        x=x[i]*(1-f)+x[j]*f
    return np.ascontiguousarray(x,dtype=np.float32)

def synthesize(text,voice="en-US-GuyNeural"):
    content=text.strip()
    if not content:raise ValueError("Type something first")
    if len(content)>350:raise ValueError("Use 350 characters or fewer per message")
    with tempfile.TemporaryDirectory(prefix="gill_tts_") as folder:
        root=Path(folder)
        if not voice.startswith("SAPI:"):
            try:
                import edge_tts
                import miniaudio
                mp3=root/"speech.mp3"
                asyncio.run(edge_tts.Communicate(content,voice=voice).save(str(mp3)))
                audio=miniaudio.decode_file(str(mp3),
                    output_format=miniaudio.SampleFormat.SIGNED16,
                    nchannels=2,sample_rate=48000)
                if audio.num_frames<=0:raise RuntimeError("No audio returned")
                output=np.frombuffer(audio.samples,dtype="<i2").reshape(-1,2)
                return np.ascontiguousarray(output.astype("float32")/32768.0),True
            except Exception:
                # Windows locally installed voice is always available even without internet,
                # provided Windows Speech is installed.
                voice="SAPI:Female" if any(f in voice for f in ("Jenny","Sonia","Aria","Female")) else "SAPI:Male"
        wav=root/"speech.wav"
        _sapi_to_wav(content,voice,wav)
        return _from_wav(wav),False

def process_tts(audio,chaos=False,effect="CLEAN",strength=.6):
    if audio.size==0:return audio
    out=audio.astype("float32",copy=True)
    if effect and effect!="CLEAN":
        from voice_fx import VoiceProcessor
        processor=VoiceProcessor()
        mono=np.mean(out,axis=1)
        chunks=[processor.process(mono[i:i+2048],effect,strength)
                for i in range(0,len(mono),2048)]
        voiced=np.concatenate(chunks)
        out=np.repeat(voiced.reshape(-1,1),2,axis=1)
    if chaos:
        indices=(np.arange(len(out))//22)*22
        held=out[indices]
        out=np.round(np.tanh(held*4)*11)/11
        out=np.tanh(out*3.4)*.65
    # Keep a short, natural start and tail; no extra silence at the front.
    count=min(200,len(out))
    out[:count]*=np.linspace(.06,1.0,count).reshape(-1,1)
    count=min(480,len(out))
    out[-count:]*=np.linspace(1.0,0.0,count).reshape(-1,1)
    return np.clip(out,-.85,.85).astype("float32")
