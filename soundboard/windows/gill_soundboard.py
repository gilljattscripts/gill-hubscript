"""GILL Soundboard for Windows. Offline, original high-fidelity effects, VB-CABLE routing."""
import os
import sys
import threading
import wave
from pathlib import Path
import tkinter as tk
from tkinter import ttk, messagebox
import numpy as np
import sounddevice as sd
from voice_fx import VoiceProcessor, VOICE_MODES

NAMES = [
    "BASS BOOM","AIR HORN","BONK","BOING","LASER","ERROR","SCRATCH","GLITCH",
    "DRAMATIC","POP","SIREN","LEVEL UP","FAIL","PHONE RING","SUSPENSE","CARTOON ZIP",
    "CHIRP","METAL HIT","REWIND","WHOOSH","ALARM","ARCADE","BEEP SPAM","SPACE BLIP",
    "RECORD STOP","8-BIT HIT","DARK PULSE","CARTOON SLIDE","RADIO STATIC","BELL","PING","TRUMPET",
    "BUBBLE","BASS DROP","UFO","CLOCK TICK","VHS GLITCH","SMASH","DIZZY","SPARK",
    "GAME OVER","SUCCESS","WARNING","POWER UP","SQUEAK","SCREAM SYNTH","KNOCK","SCI-FI SHOT",
    "BRUH BASS","SHEESH WHISTLE","CLOWN HONK","RUBBER DUCK","AWKWARD BUZZ","VINE BOOM","SNORT","SPROING",
    "ROUND LOST","ROUND WON","ITEM FOUND","BOSS ARRIVES","NOPE BUZZER","CHECKPOINT","HIT CONFIRM","TIME OUT",
    "JUMPSCARE","CREEPY CHOIR","GHOST SWOOSH","HEARTBEAT","DOOR CREAK","DARK DRONE","WHISPER SYNTH","EVIL GIGGLE",
    "DATA CRASH","DIGITAL BURST","MODEM BLIP","CYBER SIREN","STATIC SWEEP","PIXEL BREAK","ROBOT BLIP","SIGNAL LOST",
    "VICTORY FANFARE","TENSION STING","8BIT BOUNCE","MYSTERY CHORD","SAD TROMBONE","RAPID DRUM","BATTLE INTRO","LAST SECOND",
    "CHAOS HORN","MIC DESTROYED","MEGA BOOM","HYPER BEEP","WOBBLE BASS","BRAIN FREEZE","ALIEN SQUEAL","OVERLOAD"
]
CATEGORIES=["LOUD / BASS","FUNNY / CARTOON","DRAMA / MEMES",
            "ARCADE / RETRO","EFFECTS / SPACE","ALERTS / EXTRA",
            "MEME REACTIONS","GAMING / FAILS","HORROR / HAUNTED",
            "GLITCH / TECH","MUSIC / STINGS","CHAOS / TROLL"]
BG="#0d0c17"; PANEL="#191626"; CARD="#292239"
ORANGE="#a663ff"; WHITE="#f5f0ff"; DIM="#b6accb"
COLORS=["#cf8cff","#ffc389","#bd9dff","#80d8ff","#a0f1d5","#ffd395",
        "#f39aba","#85cfff","#b68ff6","#7ce2d9","#eab078","#f58bb6"]

def asset_root():
    if getattr(sys,"frozen",False):
        return Path(sys._MEIPASS)/"assets"
    return Path(__file__).resolve().parent/"assets"

def load_sound(index):
    path=asset_root()/("gill_sfx_%02d.wav" % index)
    with wave.open(str(path),"rb") as wav:
        if (wav.getnchannels(),wav.getframerate(),wav.getsampwidth()) != (2,48000,2):
            raise ValueError("Unexpected audio format: "+str(path))
        data=wav.readframes(wav.getnframes())
    return np.frombuffer(data,dtype="<i2").reshape(-1,2).astype("float32")/32768.0

class Mixer:
    def __init__(self):
        self.lock=threading.Lock()
        self.samples=[load_sound(i) for i in range(len(NAMES))]
        self.chaos_samples=[self.make_destroyed_effect(x) for x in self.samples]
        self.voices=[]
        self.chaos_mode=False
        self.voice_mode="CLEAN"
        self.voice_strength=.65
        self.processor=VoiceProcessor()
        self.stream=None
        self.voice_volume=.92
        self.effect_volume=.90
        self.master=.84
        self.maximum_voices=12

    @staticmethod
    def make_destroyed_effect(sample):
        # Render once at startup; switching or spamming never reprocesses a clip.
        held=sample[(np.arange(len(sample))//24)*24]
        crushed=np.round(np.tanh(held*4.2)*10)/10.0
        result=np.tanh(crushed*2.6)*.70
        return np.ascontiguousarray(result.astype("float32"))

    def stop(self):
        if self.stream is not None:
            stream,self.stream=self.stream,None
            try:stream.stop()
            except Exception:pass
            try:stream.close()
            except Exception:pass
        self.clear()

    def start(self,out_id,mic_id):
        self.stop()
        self.processor.reset()
        stream=None
        try:
            if mic_id is None:
                stream=sd.OutputStream(
                    device=out_id,channels=2,samplerate=48000,dtype="float32",
                    blocksize=256,latency="low",callback=self.sound_callback)
            else:
                stream=sd.Stream(
                    device=(mic_id,out_id),channels=(1,2),samplerate=48000,
                    dtype="float32",blocksize=256,latency="low",
                    callback=self.duplex_callback)
            stream.start()
            self.stream=stream
        except Exception:
            try:stream.close()
            except Exception:pass
            raise

    def play(self,index):
        if self.stream is None:return
        with self.lock:
            if len(self.voices)>=self.maximum_voices:self.voices.pop(0)
            sound=self.chaos_samples[index] if self.chaos_mode else self.samples[index]
            self.voices.append([sound,0])

    def clear(self):
        with self.lock:self.voices.clear()

    def mix(self,outdata,frames,mic=None):
        # Allocation-free mix buffer (provided by PortAudio).
        outdata.fill(0)
        if mic is not None:
            processed=self.processor.process(mic[:,0],self.voice_mode,self.voice_strength)
            outdata[:,0]=processed*self.voice_volume
            outdata[:,1]=processed*self.voice_volume
        with self.lock:
            live=[]
            for sample,pos in self.voices:
                count=min(frames,len(sample)-pos)
                if count>0:
                    outdata[:count,:]+=sample[pos:pos+count,:]*self.effect_volume
                pos+=count
                if pos<len(sample):live.append([sample,pos])
            self.voices=live
        # Smooth peak limiting; prevents digital clipping under repeated taps.
        np.tanh(outdata*1.10,out=outdata)
        outdata*=self.master*.93

    def sound_callback(self,outdata,frames,time,status):
        try:self.mix(outdata,frames)
        except Exception:outdata.fill(0)

    def duplex_callback(self,indata,outdata,frames,time,status):
        try:self.mix(outdata,frames,indata)
        except Exception:outdata.fill(0)

class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("GILL Soundboard")
        self.geometry("930x785")
        self.minsize(710,570)
        self.configure(bg=BG)
        try:
            icon=Path(sys._MEIPASS)/"gill_icon.ico" if getattr(sys,"frozen",False) else Path(__file__).resolve().parent/"gill_icon.ico"
            if icon.exists():self.iconbitmap(str(icon))
        except Exception:pass
        self.mixer=Mixer()
        self.voice_effect=tk.StringVar(value="CLEAN")
        self.repeat=False
        self.repeat_id=None
        self.repeat_task=None
        self.interval=tk.StringVar(value="130")
        self.cat=tk.StringVar(value="ALL")
        self.search=tk.StringVar()
        self.out_list=[]
        self.in_list=[]
        self.topmost=True
        try:
            style=ttk.Style(self)
            style.theme_use("clam")
            style.configure("Gill.TCombobox",fieldbackground=CARD,background=PANEL,
                            foreground=WHITE,arrowcolor=ORANGE,padding=6,
                            borderwidth=0,relief="flat")
            style.map("Gill.TCombobox",fieldbackground=[("readonly",CARD)],
                      foreground=[("readonly",WHITE)])
        except Exception:pass
        self.make_ui()
        self.refresh_devices()
        self.after(350,self.start_audio)
        self.protocol("WM_DELETE_WINDOW",self.close)
        self.bind("<Escape>",lambda e:self.stop_sounds())
        for n in range(1,10):
            self.bind(str(n),lambda e,index=n-1:self.mixer.play(index))

    def text(self,parent,s,size=10,color=WHITE,bold=False):
        return tk.Label(parent,text=s,fg=color,bg=parent.cget("bg"),
                        font=("Segoe UI",size,"bold" if bold else "normal"))
    def button(self,parent,s,command=None,highlight=False):
        return tk.Button(parent,text=s,command=command,bg=ORANGE if highlight else CARD,
            fg=BG if highlight else WHITE,activebackground="#d3a5ff",
            activeforeground=BG,relief="flat",borderwidth=0,
            font=("Segoe UI",10,"bold"),padx=10,pady=7,cursor="hand2")

    def make_ui(self):
        top=tk.Frame(self,bg=BG);top.pack(fill="x",padx=22,pady=(18,14))
        self.brand_image=tk.PhotoImage(file=str(asset_root()/"gill_logo_small.png"))
        tk.Label(top,image=self.brand_image,bg=BG).pack(side="left",padx=(0,12))
        heading=tk.Frame(top,bg=BG)
        heading.pack(side="left",fill="x",expand=True)
        self.text(heading,"GILL SOUNDBOARD",23,WHITE,True).pack(anchor="w")
        self.text(heading,"96 BUILT-IN SOUNDS  /  VOICE FX  /  NO ADS",10,ORANGE,True).pack(anchor="w")
        self.text(heading,"Real-time microphone mixing and virtual-cable output",10,DIM).pack(anchor="w",pady=(5,0))

        settings=tk.Frame(self,bg=PANEL);settings.pack(fill="x",padx=22,pady=6,ipady=8)
        settings.grid_columnconfigure(1,weight=1)
        settings.grid_columnconfigure(3,weight=1)
        self.text(settings,"OUTPUT",9,ORANGE,True).grid(row=0,column=0,padx=9,pady=(11,3))
        self.text(settings,"MIC INPUT",9,ORANGE,True).grid(row=0,column=2,padx=9,pady=(11,3))
        self.out=tk.ttk.Combobox(settings,state="readonly",width=25,style="Gill.TCombobox")
        self.out.grid(row=0,column=1,padx=6,pady=(11,3),sticky="ew")
        self.mic=tk.ttk.Combobox(settings,state="readonly",width=25,style="Gill.TCombobox")
        self.mic.grid(row=0,column=3,padx=6,pady=(11,3),sticky="ew")
        buttonrow=tk.Frame(settings,bg=PANEL)
        buttonrow.grid(row=1,column=0,columnspan=4,sticky="w",padx=9,pady=(6,2))
        self.button(buttonrow,"CONNECT",self.start_audio,True).pack(side="left")
        self.button(buttonrow,"REFRESH",self.refresh_devices).pack(side="left",padx=6)
        self.button(buttonrow,"STOP SOUND",self.stop_sounds).pack(side="left")
        self.status=self.text(buttonrow,"Select output to begin",9,DIM)
        self.status.pack(side="left",padx=9)

        controls=tk.Frame(self,bg=PANEL);controls.pack(fill="x",padx=22,pady=8)
        self.spambtn=self.button(controls,"SPAM: OFF",self.toggle_spam)
        self.spambtn.pack(side="left",padx=7,pady=9)
        self.text(controls,"HOLD RATE",9,DIM).pack(side="left",padx=5)
        speed=tk.ttk.Combobox(controls,state="readonly",width=5,style="Gill.TCombobox",
                             values=["75","100","130","200","300"],textvariable=self.interval)
        speed.pack(side="left")
        self.topbtn=self.button(controls,"TOPMOST: ON",self.toggle_topmost)
        self.topbtn.pack(side="left",padx=9)
        self.text(controls,"VOLUME",9,DIM).pack(side="left")
        vol=tk.Scale(controls,orient="horizontal",from_=0,to=100,showvalue=False,
                     length=130,highlightthickness=0,bg=PANEL,activebackground=ORANGE,
                     troughcolor=CARD,command=lambda x:setattr(self.mixer,"master",float(x)/100))
        vol.set(84);vol.pack(side="left")
        self.attributes("-topmost",True)

        fxpanel=tk.Frame(self,bg=PANEL,highlightbackground="#40334e",highlightthickness=1)
        fxpanel.pack(fill="x",padx=22,pady=(5,8))
        head=tk.Frame(fxpanel,bg=PANEL)
        head.pack(fill="x",padx=12,pady=(12,4))
        self.text(head,"LIVE VOICE CHANGER",11,ORANGE,True).pack(side="left")
        self.text(head,"MIC INPUT + VIRTUAL CABLE REQUIRED",9,DIM).pack(side="right")
        settingsrow=tk.Frame(fxpanel,bg=PANEL)
        settingsrow.pack(fill="x",padx=12,pady=(5,5))
        self.text(settingsrow,"MIC EFFECT",9,DIM,True).pack(side="left",padx=(0,7))
        voice=tk.ttk.Combobox(settingsrow,values=VOICE_MODES,state="readonly",
            width=18,textvariable=self.voice_effect,style="Gill.TCombobox")
        voice.pack(side="left")
        voice.bind("<<ComboboxSelected>>",self.update_voice_fx)
        self.text(settingsrow,"STRENGTH",9,DIM,True).pack(side="left",padx=(16,5))
        strength=tk.Scale(settingsrow,orient="horizontal",from_=0,to=100,showvalue=True,
            length=118,highlightthickness=0,bg=PANEL,fg=WHITE,activebackground=ORANGE,
            troughcolor=CARD,command=self.update_voice_strength)
        strength.set(65)
        strength.pack(side="left")
        self.chaosbtn=self.button(settingsrow,"SOUND CHAOS: OFF",self.toggle_chaos)
        self.chaosbtn.pack(side="right",padx=(10,0))
        self.text(fxpanel,
            "DEEP / CHIPMUNK / ROBOT / RADIO / CAVE / DEMON / DESTROYED MIC  •  Sound Chaos is separate from microphone FX.",
            9,DIM).pack(anchor="w",padx=12,pady=(1,12))

        filterrow=tk.Frame(self,bg=BG);filterrow.pack(fill="x",padx=22,pady=(9,7))
        self.text(filterrow,"CATEGORY",9,ORANGE,True).pack(side="left",padx=(0,6))
        categories=tk.ttk.Combobox(filterrow,state="readonly",width=20,style="Gill.TCombobox",
                                  values=["ALL"]+CATEGORIES,textvariable=self.cat)
        categories.pack(side="left")
        categories.bind("<<ComboboxSelected>>",lambda e:self.render_sounds())
        self.text(filterrow,"SEARCH",9,ORANGE,True).pack(side="left",padx=(12,6))
        entry=tk.Entry(filterrow,textvariable=self.search,bg=PANEL,fg=WHITE,
                       insertbackground=WHITE,relief="flat")
        entry.pack(side="left",fill="x",expand=True,ipady=6)
        self.search.trace_add("write",lambda *_:self.render_sounds())

        area=tk.Frame(self,bg=PANEL,highlightbackground="#40334e",highlightthickness=1)
        area.pack(fill="both",expand=True,padx=22,pady=(0,9))
        self.canvas=tk.Canvas(area,bg=PANEL,highlightthickness=0)
        scroll=tk.Scrollbar(area,orient="vertical",command=self.canvas.yview)
        self.canvas.configure(yscrollcommand=scroll.set)
        scroll.pack(side="right",fill="y")
        self.canvas.pack(side="left",fill="both",expand=True)
        self.grid=tk.Frame(self.canvas,bg=PANEL)
        self.canvas.create_window((10,10),window=self.grid,anchor="nw",tags="inner")
        self.grid.bind("<Configure>",lambda e:self.canvas.configure(scrollregion=self.canvas.bbox("all")))
        self.canvas.bind("<Configure>",lambda e:self.canvas.itemconfigure("inner",width=max(80,e.width-20)))
        self.canvas.bind_all("<MouseWheel>",lambda e:self.canvas.yview_scroll(-int(e.delta/120),"units"))
        for col in range(4):self.grid.grid_columnconfigure(col,weight=1,uniform="gill")
        self.render_sounds()

        footer=tk.Frame(self,bg=BG);footer.pack(fill="x",padx=22,pady=(0,13))
        self.text(footer,"HOLD TO REPEAT IN SPAM MODE  •  ESC = STOP  •  CHAOS IS INTENTIONALLY DISTORTED BUT VOLUME-LIMITED",9,DIM).pack(anchor="w")
        self.text(footer,"VB-CABLE  /  SOUNDBOARD OUTPUT: CABLE INPUT  •  ROBLOX MIC: CABLE OUTPUT",9,ORANGE).pack(anchor="w",pady=(3,0))

    def render_sounds(self):
        self.cancel_spam()
        if not hasattr(self,"grid"):return
        for child in self.grid.winfo_children():child.destroy()
        ids=[i for i,name in enumerate(NAMES)
             if (self.cat.get()=="ALL" or CATEGORIES[i//8]==self.cat.get())
             and self.search.get().lower().strip() in name.lower()]
        for index,n in enumerate(ids):
            color=COLORS[n//8]
            card=tk.Frame(self.grid,bg=CARD,highlightthickness=1,
                          highlightbackground="#443459",cursor="hand2")
            card.grid(row=index//4,column=index%4,sticky="nsew",padx=6,pady=6)
            top=tk.Frame(card,bg=CARD)
            top.pack(fill="x",padx=11,pady=(10,2))
            icon=tk.Canvas(top,width=27,height=25,bg=CARD,highlightthickness=0)
            icon.pack(side="left")
            # Small custom vector equalizer mark, no emoji or external UI library.
            for j,bar in enumerate((9,18,13,7)):
                cx=2+j*6
                icon.create_rectangle(cx,21-bar,cx+4,21,fill=color,outline="")
            tk.Label(top,text="%02d"%(n+1),font=("Segoe UI",9),
                     bg=CARD,fg=DIM).pack(side="right")
            label=tk.Label(card,text=NAMES[n],bg=CARD,fg=WHITE,
                           anchor="w",font=("Segoe UI",10,"bold"),cursor="hand2")
            label.pack(fill="x",padx=11,pady=(2,3))
            hint=tk.Label(card,text="TAP TO PLAY",bg=CARD,fg=DIM,anchor="w",
                          font=("Segoe UI",8,"bold"),cursor="hand2")
            hint.pack(fill="x",padx=11,pady=(0,10))
            for widget in (card,top,icon,label,hint):
                widget.bind("<ButtonPress-1>",lambda e,k=n:self.press(k))
                widget.bind("<ButtonRelease-1>",lambda e:self.cancel_spam())
                widget.bind("<Leave>",lambda e:self.cancel_spam())
                widget.bind("<Enter>",lambda e,w=card:w.configure(highlightbackground=color))

    def press(self,n):
        self.cancel_spam()
        self.mixer.play(n)
        if self.repeat:
            self.repeat_id=n
            self.repeat_task=self.after(int(self.interval.get()),self.repeat_play)
    def repeat_play(self):
        if self.repeat_id is None or not self.repeat:return
        self.mixer.play(self.repeat_id)
        self.repeat_task=self.after(int(self.interval.get()),self.repeat_play)
    def cancel_spam(self):
        self.repeat_id=None
        if self.repeat_task is not None:
            try:self.after_cancel(self.repeat_task)
            except Exception:pass
            self.repeat_task=None
    def toggle_spam(self):
        self.repeat=not self.repeat
        self.cancel_spam()
        self.spambtn.configure(text="SPAM: ON" if self.repeat else "SPAM: OFF",
                               bg=ORANGE if self.repeat else CARD,fg=BG if self.repeat else WHITE)
    def update_voice_fx(self,event=None):
        self.mixer.voice_mode=self.voice_effect.get()
        if self.mixer.voice_mode!="CLEAN" and self.mic.current()==0:
            self.status.configure(text="CHOOSE A MICROPHONE FOR VOICE FX",fg="#e9b184")

    def update_voice_strength(self,value):
        self.mixer.voice_strength=float(value)/100.0

    def toggle_chaos(self):
        self.mixer.chaos_mode=not self.mixer.chaos_mode
        enabled=self.mixer.chaos_mode
        self.chaosbtn.configure(text="SOUND CHAOS: ON" if enabled else "SOUND CHAOS: OFF",
                                bg=ORANGE if enabled else CARD,fg=BG if enabled else WHITE)

    def toggle_topmost(self):
        self.topmost=not self.topmost
        self.attributes("-topmost",self.topmost)
        self.topbtn.configure(text="TOPMOST: ON" if self.topmost else "TOPMOST: OFF")
    def stop_sounds(self):
        self.cancel_spam()
        self.mixer.clear()
    def refresh_devices(self):
        self.out_list=[]
        self.in_list=[("No microphone (sounds only)",None)]
        try:
            for i,d in enumerate(sd.query_devices()):
                title="[%d] %s" %(i,d["name"])
                if d["max_output_channels"]>=2:self.out_list.append((title,i))
                if d["max_input_channels"]>=1:self.in_list.append((title,i))
            self.out["values"]=[a for a,b in self.out_list]
            self.mic["values"]=[a for a,b in self.in_list]
            if not self.out_list:raise RuntimeError("No audio output found")
            default=sd.default.device[1]
            cable=next((i for i,(name,_) in enumerate(self.out_list)
                        if "cable input" in name.lower()),None)
            current=cable if cable is not None else next((i for i,(_,dev) in enumerate(self.out_list)
                                                          if dev==default),0)
            self.out.current(current)
            self.mic.current(0)
            self.status.configure(text="Select devices + CONNECT")
        except Exception as ex:
            messagebox.showerror("Audio devices",str(ex))
    def start_audio(self):
        try:
            output=self.out_list[self.out.current()][1]
            microphone=self.in_list[self.mic.current()][1]
            self.mixer.start(output,microphone)
            self.status.configure(text="ACTIVE / MIC + SOUNDS" if microphone is not None else "ACTIVE / SOUNDS",
                                  fg="#72e4a0")
        except Exception as ex:
            self.status.configure(text="AUDIO ERROR",fg="#ff706b")
            messagebox.showerror("Audio setup",str(ex))
    def close(self):
        self.cancel_spam()
        self.mixer.stop()
        self.destroy()

if __name__=="__main__":
    try:App().mainloop()
    except Exception as ex:
        try:messagebox.showerror("GILL Soundboard",str(ex))
        except Exception:print(ex)
