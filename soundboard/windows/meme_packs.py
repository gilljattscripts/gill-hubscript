"""Import a user's actual MP3/WAV/OGG clips from a ZIP into GILL Soundboard.

Only copies files selected by the user; never downloads or generates audio.
The pack manifest preserves the human-friendly corrected names and categories.
"""
from pathlib import Path
from zipfile import ZipFile,BadZipFile
from hashlib import sha256
from dataclasses import dataclass
from threading import Lock
import json
import os
import re
import sys

from meme_browser import app_folder, Meme

MAX_CLIPS=250
MAX_FILE=16*1024*1024
MAX_TOTAL=220*1024*1024
EXTENSIONS={".mp3",".wav",".ogg",".m4a"}
PACK_LOCK=Lock()

def _pack_dir():
    folder=app_folder()/"imported_memes"
    folder.mkdir(parents=True,exist_ok=True)
    return folder

def _registry_path():
    return app_folder()/"imported_pack.json"

def pack_registry():
    try:
        raw=json.loads(_registry_path().read_text(encoding="utf-8"))
        return [x for x in raw if isinstance(x,dict) and
            isinstance(x.get("sha256"),str) and
            isinstance(x.get("title"),str) and
            isinstance(x.get("file"),str) and
            (Path(x["file"]).parent==_pack_dir()) and
            Path(x["file"]).is_file()][:500]
    except Exception:return []

def imported_sounds():
    out=[]
    for data in pack_registry():
        out.append(Meme(data["title"],"file:"+data["file"],data.get("category","Imported Pack")))
    return out

def import_pack(filename):
    """Returns (new_file_count, existing_total). No path extraction from ZIP entries."""
    with PACK_LOCK:
        stored=pack_registry()
        known={x["sha256"] for x in stored}
        fresh=[]
        seen=0
        size_so_far=0
        folder=_pack_dir()
        try:
            with ZipFile(filename) as archive:
                metadata={}
                if "sound_manifest.json" in archive.namelist():
                    info=archive.getinfo("sound_manifest.json")
                    if info.file_size<400000:
                        raw=json.loads(archive.read(info).decode("utf-8"))
                        if isinstance(raw,list):
                            metadata={x["relative_path"]:x for x in raw if isinstance(x,dict) and
                                isinstance(x.get("relative_path"),str)}
                for member in archive.infolist():
                    if member.is_dir():continue
                    name=member.filename.replace("\\","/")
                    ext=Path(name).suffix.lower()
                    if ext not in EXTENSIONS:continue
                    seen+=1
                    if seen>MAX_CLIPS:raise ValueError("Too many clips (maximum 250)")
                    if member.file_size>MAX_FILE:raise ValueError("One clip exceeds 16 MB")
                    size_so_far+=member.file_size
                    if size_so_far>MAX_TOTAL:raise ValueError("Pack exceeds 220 MB")
                    audio=archive.read(member)
                    if not 80<=len(audio)<=MAX_FILE:continue
                    key=sha256(audio).hexdigest()
                    if key in known:continue
                    target=folder/(key[:32]+ext)
                    if not target.exists():
                        temporary=folder/(key[:32]+".partial")
                        temporary.write_bytes(audio)
                        temporary.replace(target)
                    meta=metadata.get(name,{})
                    title=str(meta.get("clean_filename") or Path(name).name)
                    title=re.sub(r"\.(mp3|wav|ogg|m4a)$","",title,flags=re.I).strip()
                    if not title:title="Imported Meme"
                    category=str(meta.get("category") or
                        (name.split("/")[0] if "/" in name else "My Clips"))[:70]
                    fresh.append({"sha256":key,"title":title[:95],
                        "category":category,"file":str(target)})
                    known.add(key)
        except BadZipFile as ex:
            raise ValueError("That file is not a valid ZIP") from ex
        if not seen:raise ValueError("No MP3, WAV, OGG or M4A files in this ZIP")
        full=(stored+fresh)[:500]
        temp=_registry_path().with_suffix(".partial")
        temp.write_text(json.dumps(full,ensure_ascii=False,indent=2),encoding="utf-8")
        temp.replace(_registry_path())
        return len(fresh),len(full)


def auto_import_embedded():
    """Load original audio carried inside the finished EXE once.
    
    Source builds ship a harmless placeholder, replaced locally when packaging
    the user's full 92-clip audio pack, so public GitHub never stores recordings.
    """
    folder=(Path(sys._MEIPASS)/"assets") if getattr(sys,"frozen",False) else (
        Path(__file__).resolve().parent/"assets")
    bundled=folder/"gill_soundpack.txt"
    from zipfile import is_zipfile
    # Final self-contained EXE carries the private sound pack as an appended ZIP.
    # No separate download, import dialog, or web fetch is necessary.
    if getattr(sys,"frozen",False) and is_zipfile(sys.executable):
        bundled=Path(sys.executable)
    elif not bundled.exists() or not is_zipfile(bundled):
        return 0
    version="gill-pack-92-gangnam-v1"
    stamp=app_folder()/"built_in_pack_version.txt"
    try:
        if stamp.read_text(encoding="utf-8").strip()==version and len(imported_sounds())>=92:
            return 0
    except Exception:pass
    added,total=import_pack(bundled)
    if total>=92:
        stamp.write_text(version,encoding="utf-8")
    return added
