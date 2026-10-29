"""Regression tests for single-instance playback, loop switching and Chaos processing."""
import sys
import types
import threading
from pathlib import Path
import numpy as np

sys.path.insert(0,str(Path(__file__).parent))

# Stub device/network services: these tests exercise the real Mixer without hardware.
sd=types.ModuleType("sounddevice")
sd.query_devices=lambda:[]
sd.default=types.SimpleNamespace(device=(None,None))
sys.modules["sounddevice"]=sd

tts=types.ModuleType("tts_engine")
tts.DEFAULT_VOICES={"Test voice":"en-US-GuyNeural"}
tts.custom_profiles=lambda:{}
tts.save_custom_profile=lambda *a:None
tts.remove_custom_profile=lambda *a:None
tts.synthesize=lambda *a:(np.zeros((100,2),dtype=np.float32),False)
tts.process_tts=lambda audio,*a:audio
sys.modules["tts_engine"]=tts

browser=types.ModuleType("meme_browser")
class Meme:
    def __init__(self,title,url,page=""):self.title,self.url,self.page=title,url,page
browser.Meme=Meme
browser.website_search=lambda q:[]
browser.favorites=lambda:[]
browser.set_favorite=lambda *a:None
browser.cached_audio=lambda u:None
browser.decode_pcm=lambda p:np.zeros((100,2),dtype=np.float32)
browser.SITE="https://www.myinstants.com"
browser.youtube_search_link=lambda q:q
sys.modules["meme_browser"]=browser

packs=types.ModuleType("meme_packs")
packs.import_pack=lambda p:(0,0)
packs.imported_sounds=lambda:[]
packs.auto_import_embedded=lambda:0
sys.modules["meme_packs"]=packs

from gill_soundboard import Mixer
from voice_fx import VoiceProcessor

def make_mixer():
    mixer=Mixer.__new__(Mixer)
    mixer.lock=threading.Lock()
    mixer.voices=[]
    mixer.loop_sample=None
    mixer.loop_source=None
    mixer.loop_position=0
    mixer.chaos_mode=False
    mixer.voice_mode="CLEAN"
    mixer.voice_strength=.65
    mixer.processor=VoiceProcessor()
    mixer.stream=object()
    mixer.voice_volume=.92
    mixer.effect_volume=.90
    mixer.master=.84
    mixer.maximum_voices=1
    return mixer

def main():
    mixer=make_mixer()
    first=np.full((1200,2),.25,dtype=np.float32)
    second=np.full((1200,2),-.40,dtype=np.float32)
    assert mixer.play_audio(first)
    assert len(mixer.voices)==1
    assert mixer.play_audio(second)
    assert len(mixer.voices)==1,"rapid second tap must replace, not overlap, the first sound"
    active=mixer.voices[0][0]
    assert active.shape==second.shape
    assert active[-1,0]<-.35,"second tap should become the active sound"
    assert mixer.loop_sample is None and mixer.loop_source is None

    mixer.set_loop(first)
    assert mixer.loop_sample is not None and mixer.loop_source is not None
    assert not mixer.voices,"starting a loop must stop one-shot playback"
    mixer.set_loop(second)
    assert len(mixer.voices)==0 and mixer.loop_sample is not None
    assert mixer.loop_source[-1,0]<0,"selecting another loop must replace the previous loop"

    mixer.chaos_mode=True
    mixer.set_loop(first)
    assert mixer.loop_sample is not None
    assert not np.allclose(mixer.loop_sample,mixer.loop_source),"Chaos must alter looped audio"
    mixer.play_audio(second)
    assert mixer.loop_sample is None and len(mixer.voices)==1
    assert np.all(np.isfinite(mixer.voices[0][0]))
    assert mixer.voices[0][0].shape==second.shape

    out=np.zeros((256,2),dtype=np.float32)
    mixer.mix(out,256)
    assert np.all(np.isfinite(out)) and np.max(np.abs(out))<=.85
    mixer.clear()
    assert not mixer.voices and mixer.loop_sample is None and mixer.loop_source is None
    print("PASS: single playback instance, no overlap, loop replacement/seam, Chaos, finite output and STOP")

if __name__=="__main__":main()
