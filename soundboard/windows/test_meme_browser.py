"""Offline unit tests for parsing real audio links; these do not generate sounds."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
import meme_browser as m
import tempfile
import os

def main():
    html="""
    <div class="instant">
      <a href="/en/instant/man-screaming/">Man Screaming</a>
      <button onclick="play('/media/sounds/man-screaming-ahhhh.mp3')">Play</button>
      <audio src="https://www.myinstants.com/media/sounds/i-hate-this-game.mp3"></audio>
      <audio src="/media/sounds/i-hate-this-game.mp3"></audio>
      <audio src="https://evil.example/media/sounds/fake.mp3"></audio>
    </div>
    """
    result=m.parse_html(html)
    assert len(result)==2,result
    assert all(m.is_audio_url(x.url) for x in result)
    assert result[0].title=="man screaming ahhhh"
    assert result[1].title=="i hate this game"
    assert not m.is_audio_url("https://evil.example/media/sounds/a.mp3")
    assert not m.is_audio_url("http://www.myinstants.com/media/sounds/a.mp3")
    assert m.normalise_link("/media/sounds/meme.mp3")=="https://www.myinstants.com/media/sounds/meme.mp3"
    with tempfile.TemporaryDirectory() as directory:
        os.environ["APPDATA"]=directory
        test=result[0]
        m.set_favorite(test,True)
        assert m.favorites()[0].url==test.url
        m.set_favorite(test,False)
        assert not m.favorites()
    print("PASS: real media parser, URL restrictions, dedupe and favorites")

if __name__=="__main__":main()
