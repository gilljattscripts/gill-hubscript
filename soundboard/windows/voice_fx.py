"""Lightweight real-time microphone effects for GILL Soundboard (48 kHz mono).

All effects preserve the audio stream timing. Pitch modes use two cross-faded
variable-delay grain readers; they are approximate voice effects, not a
studio-quality formant shifter. No online services or microphone uploads.
"""
import numpy as np

VOICE_MODES=(
    "CLEAN","DEEP","CHIPMUNK","ROBOT",
    "RADIO","CAVE ECHO","DEMON","DESTROYED MIC"
)

class VoiceProcessor:
    def __init__(self,sample_rate=48000):
        self.rate=sample_rate
        self.buffer=np.zeros(32768,dtype=np.float32)
        self.echo=np.zeros(32768,dtype=np.float32)
        self.absolute=0
        self.prev_low=0.0
        self.prev_high=0.0
        self.prev_input=0.0

    def reset(self):
        self.buffer.fill(0)
        self.echo.fill(0)
        self.absolute=0
        self.prev_low=0.0
        self.prev_high=0.0
        self.prev_input=0.0

    def _pitch(self,samples,ratio):
        count=len(samples)
        if count==0:return samples
        length=len(self.buffer)
        t=np.arange(count,dtype=np.float64)+self.absolute
        self.buffer[(t.astype(np.int64)%length)] = samples
        delay=6100.0
        grain=2400.0
        direction=1.0 if ratio>1 else -1.0
        phase=np.mod(t*abs(ratio-1)/grain,1.0)
        phase2=np.mod(phase+.5,1.0)
        one=t-delay+direction*grain*phase
        two=t-delay+direction*grain*phase2
        def sample_at(index):
            position=np.floor(index).astype(np.int64)
            frac=(index-position).astype(np.float32)
            s0=self.buffer[position % length]
            s1=self.buffer[(position+1)%length]
            return s0+(s1-s0)*frac
        weight=(.5-.5*np.cos(phase*2*np.pi)).astype(np.float32)
        return sample_at(one)*weight+sample_at(two)*(1-weight)

    def process(self,input_chunk,mode="CLEAN",strength=.6):
        x=np.asarray(input_chunk,dtype=np.float32).reshape(-1)
        if len(x)==0:return x
        x=np.nan_to_num(x,nan=0,posinf=0,neginf=0)
        x=np.clip(x,-1.0,1.0)
        strength=float(np.clip(strength,0.05,1))
        time=np.arange(len(x),dtype=np.float32)+self.absolute

        # Keep the history valid across mode changes.
        if mode not in ("DEEP","CHIPMUNK","DEMON"):
            self.buffer[(time.astype(np.int64))%len(self.buffer)]=x
        if mode=="DEEP":
            wet=self._pitch(x,0.78)
            y=x*(1-strength)+wet*strength
        elif mode=="CHIPMUNK":
            wet=self._pitch(x,1.37)
            y=x*(1-strength)+wet*strength
        elif mode=="DEMON":
            pitched=self._pitch(x,.66)
            wet=np.tanh(pitched*2.9)*.69
            y=x*(1-strength)+wet*strength
        elif mode=="ROBOT":
            carrier=.55+.45*np.cos(2*np.pi*86*time/self.rate)
            wet=np.tanh(x*2.1)*carrier
            y=x*(1-strength)+wet*strength
        elif mode=="RADIO":
            # Narrower phone/radio timbre with controlled saturation.
            smooth=np.convolve(x,np.ones(12,dtype=np.float32)/12,mode="same")
            high=smooth-np.convolve(smooth,np.ones(160,dtype=np.float32)/160,mode="same")
            wet=np.tanh(high*3)*.72
            y=x*(1-strength)+wet*strength
        elif mode=="CAVE ECHO":
            delay=int(self.rate*.225)
            indices=time.astype(np.int64)
            wet=x.copy()
            wet+=.44*self.echo[(indices-delay)%len(self.echo)]
            wet+=.23*self.echo[(indices-delay*2)%len(self.echo)]
            self.echo[indices%len(self.echo)]=x
            y=x*(1-strength)+wet*strength
        elif mode=="DESTROYED MIC":
            # Intentionally dirty: crushed, gated and saturated, never full-scale clipped.
            crushed=np.round(x*10)/10
            held=crushed.copy()
            held[:]=crushed[(np.arange(len(x))//12)*12]
            wet=np.tanh(held*6.2)*.70
            wet*=.83+.17*np.sin(2*np.pi*39*time/self.rate)
            y=x*(1-strength)+wet*strength
        else:
            y=x.copy()
        self.absolute+=len(x)
        # Final built-in safety limiter; extreme settings should not produce digital clipping.
        return np.clip(np.tanh(y*1.05)*.88,-.88,.88).astype(np.float32)
