"""GILL Soundboard for Windows: real meme search, VB-CABLE and voice FX. No fake SFX."""
import os
import sys
import threading
import wave
import webbrowser
from pathlib import Path
import tkinter as tk
from tkinter import ttk, messagebox, simpledialog
from tts_engine import DEFAULT_VOICES, custom_profiles, save_custom_profile, remove_custom_profile, synthesize, process_tts
import numpy as np
import sounddevice as sd
from voice_fx import VoiceProcessor, VOICE_MODES
from meme_browser import Meme, website_search, favorites, set_favorite, cached_audio, decode_pcm, SITE, youtube_search_link

QUICK_SEARCHES=["I hate this game","man screaming","rage scream","gen z meme",
                "hindi meme","arey bhai","moye moye","bruh","goofy laugh","vine boom"]
BG="#0d0c17"; PANEL="#191626"; CARD="#292239"
ORANGE="#a663ff"; WHITE="#f5f0ff"; DIM="#b6accb"


def asset_root():
    if getattr(sys,"frozen",False):
        return Path(sys._MEIPASS)/"assets"
    return Path(__file__).resolve().parent/"assets"

class Mixer:
    def __init__(self):
        self.lock=threading.Lock()
        self.voices=[]
        self.loop_sample=None
        self.loop_position=0
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

    def set_loop(self, sample=None):
        with self.lock:
            self.loop_sample=np.ascontiguousarray(sample,dtype=np.float32) if sample is not None else None
            self.loop_position=0

    def play_audio(self,audio):
        # The synthesized voice goes into the same live PortAudio output as your sounds.
        if self.stream is None:return False
        sample=np.asarray(audio,dtype=np.float32)
        if sample.ndim!=2 or sample.shape[1]!=2 or len(sample)==0:return False
        with self.lock:
            while len(self.voices)>=self.maximum_voices:self.voices.pop(0)
            if self.chaos_mode:
                sample=self.make_destroyed_effect(sample)
            self.voices.append([np.ascontiguousarray(sample),0])
        return True

    def clear(self):
        with self.lock:
            self.voices.clear()
            self.loop_sample=None
            self.loop_position=0

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
            if self.loop_sample is not None and len(self.loop_sample):
                sample=self.loop_sample
                positions=(np.arange(frames,dtype=np.int64)+self.loop_position)%len(sample)
                outdata[:frames,:]+=sample[positions,:]*self.effect_volume
                self.loop_position=(self.loop_position+frames)%len(sample)
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
        self.geometry("1010x870")
        self.minsize(710,570)
        self.configure(bg=BG)
        try:
            icon=Path(sys._MEIPASS)/"gill_icon.ico" if getattr(sys,"frozen",False) else Path(__file__).resolve().parent/"gill_icon.ico"
            if icon.exists():self.iconbitmap(str(icon))
        except Exception:pass
        self.mixer=Mixer()
        self.voice_effect=tk.StringVar(value="CLEAN")
        self.tts_voice=tk.StringVar(value="Male • Guy (neural)")
        self.tts_status=tk.StringVar(value="READY TO SPEAK")
        self.tts_voice_map=dict(DEFAULT_VOICES)
        self.tts_voice_map.update(custom_profiles())
        self.tts_busy=False
        self.repeat=False
        self.repeat_id=None
        self.repeat_task=None
        self.interval=tk.StringVar(value="130")
        self.cat=tk.StringVar(value="SAVED FAVORITES")
        self.search=tk.StringVar()
        self.current_results=[]
        self.loaded_samples={}
        self.loop_mode=False
        self.online_busy=False
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
            self.bind(str(n),lambda e,index=n-1:self.play_favorite_hotkey(index))

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
        self.text(heading,"REAL MEMES  /  LIVE SEARCH  /  LOOP  /  VOICE FX",10,ORANGE,True).pack(anchor="w")
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

        speech=tk.Frame(self,bg=PANEL,highlightbackground="#40334e",highlightthickness=1)
        speech.pack(fill="x",padx=22,pady=(5,8))
        speechhead=tk.Frame(speech,bg=PANEL)
        speechhead.pack(fill="x",padx=12,pady=(10,4))
        self.text(speechhead,"TYPE TO SPEAK",11,ORANGE,True).pack(side="left")
        self.text(speechhead,"VOICE OUTPUT USES SAME VB-CABLE DEVICE",9,DIM).pack(side="right")
        speakrow=tk.Frame(speech,bg=PANEL)
        speakrow.pack(fill="x",padx=12,pady=(5,6))
        self.speech_text=tk.Entry(speakrow,bg=CARD,fg=WHITE,insertbackground=WHITE,
                                  relief="flat",font=("Segoe UI",11))
        self.speech_text.pack(side="left",fill="x",expand=True,ipady=9)
        self.speech_text.insert(0,"")
        self.speech_text.bind("<Return>",lambda e:self.speak_typed())
        self.speakbtn=self.button(speakrow,"SPEAK",self.speak_typed,True)
        self.speakbtn.pack(side="left",padx=(8,0))
        selectrow=tk.Frame(speech,bg=PANEL)
        selectrow.pack(fill="x",padx=12,pady=(0,7))
        self.text(selectrow,"VOICE",9,DIM,True).pack(side="left",padx=(0,7))
        self.tts_combo=tk.ttk.Combobox(selectrow,state="readonly",width=25,
            style="Gill.TCombobox",textvariable=self.tts_voice)
        self.tts_combo.pack(side="left",padx=(0,7))
        self.refresh_tts_choices()
        self.button(selectrow,"+ CUSTOM VOICE",self.add_tts_voice).pack(side="left",padx=(0,5))
        self.button(selectrow,"REMOVE CUSTOM",self.remove_tts_voice).pack(side="left",padx=(0,8))
        self.text(selectrow,"NEURAL ONLINE / WINDOWS OFFLINE BACKUP",9,DIM).pack(side="left")
        self.text(speech,"M/F neural voices when internet is available. Add custom voice IDs or select your installed Windows SAPI voice.",9,DIM).pack(anchor="w",padx=12,pady=(0,9))
        self.tts_status_label=self.text(speech,"READY TO SPEAK",9,ORANGE)
        self.tts_status_label.pack(anchor="w",padx=12,pady=(0,9))


        browser=tk.Frame(self,bg=BG)
        browser.pack(fill="x",padx=22,pady=(7,7))
        self.text(browser,"LIVE MEME SEARCH",10,ORANGE,True).pack(side="left",padx=(0,10))
        searchbox=tk.Entry(browser,textvariable=self.search,bg=CARD,fg=WHITE,
            insertbackground=WHITE,font=("Segoe UI",11),relief="flat")
        searchbox.pack(side="left",fill="x",expand=True,ipady=8)
        searchbox.bind("<Return>",lambda e:self.search_online())
        self.button(browser,"SEARCH",self.search_online,True).pack(side="left",padx=(7,0))
        self.button(browser,"MYINSTANTS",self.open_myinstants).pack(side="left",padx=(6,0))
        self.button(browser,"YOUTUBE",self.open_youtube).pack(side="left",padx=(6,0))

        quick=tk.Frame(self,bg=BG)
        quick.pack(fill="x",padx=22,pady=(0,5))
        for row in range(2):
            line=tk.Frame(quick,bg=BG)
            line.pack(fill="x",pady=2)
            for term in QUICK_SEARCHES[row*5:row*5+5]:
                self.button(line,term.upper(),
                    lambda x=term:self.search_online(x)).pack(side="left",padx=(0,5))

        bar=tk.Frame(self,bg=BG)
        bar.pack(fill="x",padx=22,pady=(0,6))
        self.text(bar,"SHOW",9,ORANGE,True).pack(side="left",padx=(0,8))
        selector=tk.ttk.Combobox(bar,state="readonly",width=19,
            values=["SAVED FAVORITES","SEARCH RESULTS"],style="Gill.TCombobox",
            textvariable=self.cat)
        selector.pack(side="left")
        selector.bind("<<ComboboxSelected>>",lambda e:self.render_sounds())
        self.loopbtn=self.button(bar,"LOOP: OFF",self.toggle_loop)
        self.loopbtn.pack(side="left",padx=8)
        self.button(bar,"STOP / CLEAR",self.stop_sounds).pack(side="left")
        self.sound_status=self.text(bar,"Search real meme clips or open favorites.",9,DIM)
        self.sound_status.pack(side="left",padx=10)

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
        for col in range(3):self.grid.grid_columnconfigure(col,weight=1,uniform="gill")
        self.render_sounds()

        footer=tk.Frame(self,bg=BG)
        footer.pack(fill="x",padx=22,pady=(0,12))
        self.text(footer,
            "REAL AUDIO FROM MYINSTANTS • NO GENERATED MEMES • SELECT TO SAVE • ESC = STOP",
            9,DIM).pack(anchor="w")
        self.text(footer,
            "FOR ROBLOX: APP OUTPUT = CABLE INPUT  •  ROBLOX MICROPHONE = CABLE OUTPUT",
            9,ORANGE).pack(anchor="w",pady=(3,0))

    def search_online(self,term=None):
        q=(term if term is not None else self.search.get()).strip()
        if not q:return
        self.search.set(q)
        if self.online_busy:
            self.sound_status.configure(text="FINISHING PREVIOUS SEARCH")
            return
        self.cat.set("SEARCH RESULTS")
        self.online_busy=True
        self.sound_status.configure(text="SEARCHING / "+q[:40],fg=ORANGE)
        def task():
            try:
                matches=website_search(q)
                err=""
            except Exception as ex:
                matches=[]
                err="Myinstants unavailable; use website button. "+str(ex)[:80]
            def ready():
                self.online_busy=False
                self.current_results=matches
                self.render_sounds()
                self.sound_status.configure(text=err if err else str(len(matches))+" LIVE RESULTS",
                    fg="#ff928d" if err else "#78e6ab")
            try:self.after(0,ready)
            except Exception:pass
        threading.Thread(target=task,daemon=True).start()

    def open_myinstants(self):
        from urllib.parse import urlencode
        webbrowser.open(SITE+"/en/search/?"+urlencode({
            "name":self.search.get().strip() or "meme"}))

    def open_youtube(self):
        webbrowser.open(youtube_search_link(self.search.get().strip() or "meme"))

    def toggle_loop(self):
        self.loop_mode=not self.loop_mode
        if not self.loop_mode:self.mixer.set_loop(None)
        self.loopbtn.configure(text="LOOP: ON" if self.loop_mode else "LOOP: OFF",
            bg=ORANGE if self.loop_mode else CARD,fg=BG if self.loop_mode else WHITE)

    def render_sounds(self):
        if not hasattr(self,"grid"):return
        for child in self.grid.winfo_children():child.destroy()
        entries=favorites() if self.cat.get()=="SAVED FAVORITES" else self.current_results
        if not entries:
            notice=self.text(self.grid,
                "NO SAVED MEMES YET • SEARCH ONLINE AND SAVE THE REAL CLIPS YOU LIKE",
                11,DIM)
            notice.grid(row=0,column=0,columnspan=3,padx=15,pady=28)
            return
        saved={x.url for x in favorites()}
        for index,item in enumerate(entries):
            col=index%3
            card=tk.Frame(self.grid,bg=CARD,highlightbackground="#45375e",
                highlightthickness=1)
            card.grid(row=index//3,column=col,sticky="nsew",padx=6,pady=6)
            heading=tk.Frame(card,bg=CARD)
            heading.pack(fill="x",padx=9,pady=(8,5))
            icon=tk.Canvas(heading,width=24,height=20,bg=CARD,highlightthickness=0)
            icon.pack(side="left")
            for j,bar in enumerate((7,17,11,5)):
                icon.create_rectangle(2+j*5,18-bar,5+j*5,18,fill=ORANGE,outline="")
            title=tk.Label(heading,text=item.title[:39],font=("Segoe UI",10,"bold"),
                bg=CARD,fg=WHITE,anchor="w")
            title.pack(side="left",fill="x",expand=True,padx=5)
            actions=tk.Frame(card,bg=CARD)
            actions.pack(fill="x",padx=8,pady=(1,8))
            self.button(actions,"▶ PLAY",lambda m=item:self.play_meme(m),True).pack(
                side="left",fill="x",expand=True)
            self.button(actions,"★" if item.url in saved else "☆",
                lambda m=item:self.toggle_favorite(m)).pack(side="left",padx=(5,0))
            self.button(actions,"SITE",lambda m=item:webbrowser.open(m.page)).pack(
                side="left",padx=(5,0))
            for widget in (card,heading,title,icon):
                widget.bind("<ButtonPress-1>",lambda e,m=item:self.press(m))
                widget.bind("<ButtonRelease-1>",lambda e:self.cancel_spam())
                widget.bind("<Leave>",lambda e:self.cancel_spam())
                widget.configure(cursor="hand2")

    def toggle_favorite(self,item):
        selected=not any(x.url==item.url for x in favorites())
        set_favorite(item,selected)
        self.render_sounds()
        self.sound_status.configure(text="FAVORITE SAVED" if selected else "FAVORITE REMOVED",
            fg="#77e7ab")

    def play_meme(self,item):
        if self.mixer.stream is None:
            messagebox.showinfo("Audio output","Select output and press CONNECT first.")
            return
        cached=self.loaded_samples.get(item.url)
        if cached is not None:
            if self.loop_mode:
                sound=self.mixer.make_destroyed_effect(cached) if self.mixer.chaos_mode else cached
                self.mixer.set_loop(sound)
            else:
                self.mixer.play_audio(cached)
            self.sound_status.configure(text="PLAYING / "+item.title[:40],fg="#74e6ab")
            return
        self.sound_status.configure(text="LOADING ACTUAL MEME / "+item.title[:30],fg=ORANGE)
        def fetch():
            try:
                audio=decode_pcm(cached_audio(item.url))
                failure=""
            except Exception as ex:
                audio=None
                failure=str(ex)
            def completed():
                if failure:
                    self.sound_status.configure(text="SOUND UNAVAILABLE",fg="#ff928d")
                    messagebox.showerror("Real meme sound",failure)
                    return
                if len(self.loaded_samples)>=25:
                    self.loaded_samples.pop(next(iter(self.loaded_samples)),None)
                self.loaded_samples[item.url]=audio
                self.play_meme(item)
            try:self.after(0,completed)
            except Exception:pass
        threading.Thread(target=fetch,daemon=True).start()

    def play_favorite_hotkey(self,index):
        saved=favorites()
        if index<len(saved):self.play_meme(saved[index])

    def press(self,item):
        self.cancel_spam()
        self.play_meme(item)
        if self.repeat and item.url in self.loaded_samples:
            self.repeat_id=item.url
            self.repeat_task=self.after(int(self.interval.get()),self.repeat_play)

    def repeat_play(self):
        if self.repeat_id is None or not self.repeat:return
        sample=self.loaded_samples.get(self.repeat_id)
        if sample is not None and not self.loop_mode:self.mixer.play_audio(sample)
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

    def refresh_tts_choices(self):
        self.tts_voice_map=dict(DEFAULT_VOICES)
        self.tts_voice_map.update(custom_profiles())
        if hasattr(self,"tts_combo"):
            self.tts_combo["values"]=list(self.tts_voice_map.keys())
        if self.tts_voice.get() not in self.tts_voice_map:
            self.tts_voice.set(next(iter(self.tts_voice_map)))

    def add_tts_voice(self):
        name=simpledialog.askstring("Add custom voice","Display name for the custom voice:",parent=self)
        if name is None:return
        voice=simpledialog.askstring("Add custom voice",
            "Microsoft neural voice ID (e.g. en-US-EricNeural) or SAPI:Installed voice name:",
            parent=self)
        if voice is None:return
        try:
            save_custom_profile(name,voice)
            self.refresh_tts_choices()
            self.tts_voice.set(name.strip()[:48])
        except Exception as error:
            messagebox.showerror("Custom voice",str(error))

    def remove_tts_voice(self):
        name=self.tts_voice.get()
        if name in DEFAULT_VOICES:
            messagebox.showinfo("Custom voices","The included voices are kept. Select a custom voice to remove.")
            return
        remove_custom_profile(name)
        self.refresh_tts_choices()

    def speak_typed(self):
        if self.tts_busy:return
        phrase=self.speech_text.get().strip()
        if not phrase:return
        if len(phrase)>350:
            messagebox.showwarning("Text length","Please use at most 350 characters per message.")
            return
        if self.mixer.stream is None:
            messagebox.showwarning("Audio output","Select an output device and press CONNECT first.")
            return
        voice_id=self.tts_voice_map.get(self.tts_voice.get(),"en-US-GuyNeural")
        chaos=self.mixer.chaos_mode
        voice_fx_mode=self.voice_effect.get()
        voice_strength=self.mixer.voice_strength
        self.tts_busy=True
        self.speakbtn.configure(state="disabled")
        self.tts_status_label.configure(text="GENERATING VOICE...",fg=ORANGE)
        def synth_worker():
            try:
                audio,neural=synthesize(phrase,voice_id)
                wave=process_tts(audio,chaos,voice_fx_mode,voice_strength)
                def completed():
                    self.tts_busy=False
                    self.speakbtn.configure(state="normal")
                    played=self.mixer.play_audio(wave)
                    self.tts_status_label.configure(
                        text=("SPEAKING • NEURAL" if neural else "SPEAKING • WINDOWS VOICE")
                            if played else "OUTPUT DISCONNECTED",
                        fg="#70e1a2" if played else "#ff928d")
                self.after(0,completed)
            except Exception as err:
                msg=str(err)
                def failed():
                    self.tts_busy=False
                    self.speakbtn.configure(state="normal")
                    self.tts_status_label.configure(text="VOICE ERROR",fg="#ff928d")
                    messagebox.showerror("Type to Speak",msg)
                try:self.after(0,failed)
                except Exception:pass
        threading.Thread(target=synth_worker,daemon=True).start()

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
