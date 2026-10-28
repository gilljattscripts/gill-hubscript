"""Offline smoke tests: the Windows voice effects must stay finite and volume-limited."""
import sys
from pathlib import Path
import numpy as np

sys.path.insert(0,str(Path(__file__).parent))
from voice_fx import VoiceProcessor, VOICE_MODES

def main():
    rng=np.random.default_rng(2026)
    voice=VoiceProcessor()
    for mode in VOICE_MODES:
        voice.reset()
        outputs=[]
        for i in range(32):
            chunk=(np.sin(2*np.pi*230*(np.arange(256)+i*256)/48000) +
                   .08*rng.standard_normal(256)).astype("float32")
            y=voice.process(chunk,mode,.85)
            assert y.shape==(256,),f"Unexpected shape in {mode}"
            assert np.all(np.isfinite(y)),f"Invalid samples in {mode}"
            assert np.max(np.abs(y))<=.881,f"Clipping in {mode}"
            outputs.append(y)
        assert np.max(np.abs(np.concatenate(outputs)))>.00001,f"Silent mode {mode}"
    print("All",len(VOICE_MODES),"voice modes pass real-time 256-frame audio smoke tests.")

if __name__=="__main__":main()
