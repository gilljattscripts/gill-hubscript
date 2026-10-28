"""Offline ZIP-import tests; no audio generation, external network or copyrighted samples."""
import io
import json
import os
import tempfile
from pathlib import Path
from zipfile import ZipFile
from meme_packs import import_pack,imported_sounds

def main():
    with tempfile.TemporaryDirectory() as folder:
        os.environ["APPDATA"]=folder
        zipfile=Path(folder)/"cleaned.zip"
        content=b"ID3"+b"TEST-FIXTURE-NOT-AUDIO"*30
        with ZipFile(zipfile,"w") as archive:
            archive.writestr("01_GenZ_Memes/Test Original Meme.mp3",content)
            archive.writestr("02_Screams_Rage/duplicate-under-different-name.mp3",content)
            archive.writestr("README.txt","not a sound")
            archive.writestr("sound_manifest.json",json.dumps([{
                "relative_path":"01_GenZ_Memes/Test Original Meme.mp3",
                "clean_filename":"Test Original Meme.mp3",
                "category":"Gen Z Memes",
            }]))
        new,total=import_pack(zipfile)
        assert (new,total)==(1,1),(new,total)
        got=imported_sounds()
        assert len(got)==1 and got[0].title=="Test Original Meme",got
        assert Path(got[0].url[5:]).read_bytes()==content
        assert import_pack(zipfile)==(0,1)
        malicious=Path(folder)/"untrusted.zip"
        with ZipFile(malicious,"w") as archive:
            archive.writestr("../../outside.mp3",b"ID3"+b"separate"*30)
        new,total=import_pack(malicious)
        assert (new,total)==(1,2)
        assert not (Path(folder).parent/"outside.mp3").exists()
    print("PASS: original ZIP pack import, deduplication, naming and zip-slip isolation")

if __name__=="__main__":
    main()
