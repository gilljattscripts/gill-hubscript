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


def _bundled_exe_pack():
    """Find the private user pack preceding PyInstaller's PKG/CArchive.

    PyInstaller's bootloader needs its cookie at the very end of the EXE.
    The pack therefore sits before the CArchive (not after it), and the
    bootloader still finds and loads its original archive unchanged.
    """
    import struct
    import hashlib
    import sys
    if not getattr(sys,"frozen",False):return None
    exe=Path(sys.executable)
    marker=b"GILL_MEME_V1_END"  # fixed 16-byte trailer ID
    trailer_size=32+8+len(marker)
    with exe.open("rb") as f:
        f.seek(0,2)
        size=f.tell()
        if size<88+trailer_size:return None
        f.seek(size-88)
        cookie=f.read(88)
        if not cookie.startswith(b"MEI\x0c\x0b\x0a\x0b\x0e"):return None
        carchive_size=struct.unpack_from(">I",cookie,8)[0]
        start=size-carchive_size
        if start<trailer_size:return None
        f.seek(start-trailer_size)
        trailer=f.read(trailer_size)
        if len(trailer)!=trailer_size or trailer[-len(marker):]!=marker:return None
        expected=trailer[:32]
        pack_size=struct.unpack("<Q",trailer[32:40])[0]
        if not 100<pack_size<220*1024*1024 or pack_size>start-trailer_size:return None
        f.seek(start-trailer_size-pack_size)
        pack=f.read(pack_size)
        if len(pack)!=pack_size or hashlib.sha256(pack).digest()!=expected:
            raise ValueError("Bundled meme recordings failed their integrity check")
    result=app_folder()/"gill_bundled_original_memes.zip"
    if result.exists() and result.stat().st_size==len(pack):
        return result
    temporary=result.with_suffix(".partial")
    temporary.write_bytes(pack)
    temporary.replace(result)
    return result

def auto_import_embedded():
    """Load the 92 original recordings into the soundboard on first launch."""
    import sys
    from zipfile import is_zipfile
    embedded=_bundled_exe_pack()
    if embedded is None:
        folder=(Path(sys._MEIPASS)/"assets") if getattr(sys,"frozen",False) else (
            Path(__file__).resolve().parent/"assets")
        embedded=folder/"gill_soundpack.txt"
        if not embedded.exists() or not is_zipfile(embedded):
            return 0
    if not is_zipfile(embedded):
        raise ValueError("Invalid bundled meme pack")
    version="gill-pack-92-gangnam-v1"
    stamp=app_folder()/"built_in_pack_version.txt"
    try:
        if stamp.read_text(encoding="utf-8").strip()==version and len(imported_sounds())>=92:
            return 0
    except Exception:
        pass
    added,total=import_pack(embedded)
    if total>=92:
        stamp.write_text(version,encoding="utf-8")
    return added
