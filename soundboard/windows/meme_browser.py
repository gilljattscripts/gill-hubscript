"""Search real Myinstants meme sounds selected by the user; no fake audio.

Search depends on the website's publicly visible HTML. If its markup changes,
the app offers a button to open the source site instead.
"""
from dataclasses import dataclass,asdict
from pathlib import Path
from urllib.parse import urlparse,urljoin,urlencode,unquote
from urllib.request import urlopen,Request
import hashlib
import html
import json
import os
import re

SITE="https://www.myinstants.com"
MAX_DOWNLOAD=9*1024*1024
USER_AGENT="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36"

@dataclass(frozen=True)
class Meme:
    title:str
    url:str
    page:str=SITE

def app_folder():
    path=Path(os.environ.get("APPDATA") or Path.home())/"GILL Soundboard"
    path.mkdir(parents=True,exist_ok=True)
    return path

def is_audio_url(link):
    try:
        u=urlparse(link)
        return u.scheme=="https" and u.netloc in ("www.myinstants.com","myinstants.com") \
            and u.path.startswith("/media/sounds/") and u.path.lower().endswith(
            (".mp3",".wav",".ogg")) and len(link)<=1500
    except Exception:return False

def normalise_link(link):
    raw=html.unescape(link).replace("\\/","/")
    p=urlparse(urljoin(SITE,raw))
    if p.scheme not in ("https","http") or p.netloc not in (
        "www.myinstants.com","myinstants.com"):return ""
    if not p.path.startswith("/media/sounds/"):return ""
    return p._replace(scheme="https",netloc="www.myinstants.com",query="",fragment="").geturl()

def sound_name(link):
    name=unquote(Path(urlparse(link).path).stem)
    name=re.sub(r"[-_]+"," ",name)
    name=re.sub(r"\s+"," ",name).strip()
    return name[:85] or "Meme sound"

MEDIA_RE=re.compile(
    r"(?:https?:)?//(?:www\.)?myinstants\.com/media/sounds/[-_A-Za-z0-9%./+]+?\.(?:mp3|wav|ogg)"
    r"|(?<![A-Za-z0-9./:])/media/sounds/[-_A-Za-z0-9%./+]+?\.(?:mp3|wav|ogg)",re.I)
PAGE_RE=re.compile(r'href=["\u0027]([^"\u0027]*?/instant/[^"\u0027]+)["\u0027]',re.I)

def parse_html(markup,limit=60):
    if isinstance(markup,bytes):markup=markup.decode("utf-8","replace")
    page=html.unescape(markup.replace("\\/","/"))
    hits=[]
    seen=set()
    for hit in MEDIA_RE.finditer(page):
        url=normalise_link(hit.group(0))
        if not is_audio_url(url) or url in seen:continue
        seen.add(url)
        window=page[max(0,hit.start()-2200):hit.start()]
        pageurl=SITE
        for match in PAGE_RE.finditer(window):
            destination=urljoin(SITE,html.unescape(match.group(1)))
            if urlparse(destination).netloc in ("www.myinstants.com","myinstants.com"):
                pageurl=destination
        hits.append(Meme(sound_name(url),url,pageurl))
        if len(hits)>=limit:break
    return hits

def website_search(query):
    q=query.strip()
    if not q or len(q)>100:raise ValueError("Enter 1-100 characters")
    endpoint=SITE+"/en/search/?"+urlencode({"name":q})
    with urlopen(Request(endpoint,headers={"User-Agent":USER_AGENT}),timeout=14) as response:
        if urlparse(response.geturl()).netloc not in ("www.myinstants.com","myinstants.com"):
            raise ValueError("Unexpected search redirect")
        page=response.read(2*1024*1024)
    return parse_html(page)

def favorites():
    try:
        saved=json.loads((app_folder()/"meme_favorites.json").read_text(encoding="utf-8"))
        return [Meme(str(x["title"])[:95],x["url"],x.get("page",SITE))
                for x in saved if is_audio_url(x["url"])][:200]
    except Exception:return []

def set_favorite(item,selected):
    current=[x for x in favorites() if x.url!=item.url]
    if selected and is_audio_url(item.url):current.insert(0,item)
    (app_folder()/"meme_favorites.json").write_text(
        json.dumps([asdict(x) for x in current][:200],indent=2,ensure_ascii=False),
        encoding="utf-8")

def cached_audio(url):
    """Download a user-selected sound, not a website-wide scrape."""
    if not is_audio_url(url):raise ValueError("Invalid sound URL")
    extension=Path(urlparse(url).path).suffix.lower()
    folder=app_folder()/"selected_sounds"
    folder.mkdir(exist_ok=True)
    path=folder/(hashlib.sha256(url.encode("utf-8")).hexdigest()[:30]+extension)
    if path.exists() and 50<path.stat().st_size<=MAX_DOWNLOAD:return path
    with urlopen(Request(url,headers={"User-Agent":USER_AGENT}),timeout=17) as response:
        if urlparse(response.geturl()).netloc not in ("www.myinstants.com","myinstants.com"):
            raise ValueError("Audio redirected off Myinstants")
        if int(response.headers.get("Content-Length",0))>MAX_DOWNLOAD:
            raise ValueError("Clip over 9 MB")
        audio=response.read(MAX_DOWNLOAD+1)
    if not 50<len(audio)<=MAX_DOWNLOAD:raise ValueError("Audio file unavailable")
    tmp=path.with_suffix(path.suffix+".partial")
    tmp.write_bytes(audio)
    tmp.replace(path)
    return path

def decode_pcm(path):
    """Convert actual audio to 48 kHz stereo float32 for VB-CABLE."""
    import miniaudio
    import numpy as np
    decoded=miniaudio.decode_file(str(path),output_format=miniaudio.SampleFormat.SIGNED16,
        nchannels=2,sample_rate=48000)
    if decoded.num_frames<=0:raise ValueError("Sound could not be decoded")
    return np.ascontiguousarray(
        np.frombuffer(decoded.samples,dtype="<i2").reshape(-1,2).astype("float32")/32768.)

def youtube_search_link(query):
    return "https://www.youtube.com/results?"+urlencode({"search_query":query+" meme sound"})
