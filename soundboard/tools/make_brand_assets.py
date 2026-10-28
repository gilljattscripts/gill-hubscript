#!/usr/bin/env python3
"""Create the original GILL Soundboard mark for Android launcher and Windows app.
Generates Android mipmaps/adaptive art and a multi-resolution Windows .ico.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT=Path(__file__).resolve().parents[1]

def logo(size=1024):
    scale=3
    w=size*scale
    im=Image.new("RGBA",(w,w),(0,0,0,0))
    d=ImageDraw.Draw(im)
    inset=int(.027*w)
    d.rounded_rectangle((inset,inset,w-inset,w-inset),radius=int(.225*w),fill=(16,12,30,255))
    d.rounded_rectangle((int(.040*w),int(.040*w),int(.960*w),int(.960*w)),
                        radius=int(.207*w),outline=(153,103,250,200),width=int(.010*w))
    glow=Image.new("RGBA",(w,w),(0,0,0,0))
    gd=ImageDraw.Draw(glow)
    center=(int(.73*w),int(.46*w))
    for r in range(int(.34*w),int(.05*w),-max(1,int(.006*w))):
        a=int(10*(1-r/(.34*w)))
        gd.ellipse((center[0]-r,center[1]-r,center[0]+r,center[1]+r),fill=(128,71,248,a))
    glow=glow.filter(ImageFilter.GaussianBlur(int(.025*w)))
    im=Image.alpha_composite(im,glow)
    d=ImageDraw.Draw(im)
    fonts=[
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "C:/Windows/Fonts/arialbd.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    ]
    font_path=next((p for p in fonts if Path(p).exists()),None)
    font=ImageFont.truetype(font_path,int(.68*w)) if font_path else ImageFont.load_default()
    bb=d.textbbox((0,0),"G",font=font)
    x=int(.131*w)-bb[0];y=int(.153*w)-bb[1]
    d.text((x+int(.009*w),y+int(.013*w)),"G",font=font,fill=(69,31,127,220))
    d.text((x,y),"G",font=font,fill=(246,240,255,255))
    widths=.045*w
    for i,(xx,hh) in enumerate(((.613,.17),(.69,.26),(.765,.37),(.84,.22))):
        x=int(xx*w);h=int(hh*w);top=int(.535*w-h/2)
        colors=((203,160,255),(160,99,251),(196,142,255),(140,77,242))
        d.rounded_rectangle((x,top,x+int(widths),top+h),radius=int(widths*.5),
                            fill=(*colors[i],255))
    d.ellipse((int(.725*w),int(.79*w),int(.757*w),int(.822*w)),
              fill=(194,144,255,255))
    return im.resize((size,size),Image.Resampling.LANCZOS)

def build():
    res=ROOT/"app/src/main/res"
    win=ROOT/"windows"
    (win/"assets").mkdir(parents=True,exist_ok=True)
    (res/"drawable-nodpi").mkdir(parents=True,exist_ok=True)
    mark=logo(1024)
    mark.resize((512,512),Image.Resampling.LANCZOS).save(res/"drawable-nodpi/gill_logo.png")
    mark.resize((64,64),Image.Resampling.LANCZOS).save(win/"assets/gill_logo_small.png")
    mark.resize((1024,1024),Image.Resampling.LANCZOS).save(win/"assets/gill_logo.png")
    mark.save(win/"gill_icon.ico",format="ICO",sizes=[
        (16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
    fg=Image.new("RGBA",(512,512),(0,0,0,0))
    fg.alpha_composite(mark.resize((365,365),Image.Resampling.LANCZOS),(73,73))
    fg.save(res/"drawable-nodpi/gill_foreground.png")
    for density,pixels in (("mdpi",48),("hdpi",72),("xhdpi",96),
                           ("xxhdpi",144),("xxxhdpi",192)):
        folder=res/("mipmap-"+density)
        folder.mkdir(parents=True,exist_ok=True)
        mark.resize((pixels,pixels),Image.Resampling.LANCZOS).save(folder/"ic_launcher.png")
    (res/"mipmap-anydpi-v26").mkdir(parents=True,exist_ok=True)
    (res/"mipmap-anydpi-v26/ic_launcher.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/launcher_bg"/>\n'
        '    <foreground android:drawable="@drawable/gill_foreground"/>\n'
        '</adaptive-icon>\n',encoding="utf-8")
    (res/"values").mkdir(parents=True,exist_ok=True)
    (res/"values/colors.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<resources><color name="launcher_bg">#100D1E</color></resources>\n',
        encoding="utf-8")
    print("Generated original icon, adaptive launcher, Windows .ico and brand art.")

if __name__=="__main__":
    build()
