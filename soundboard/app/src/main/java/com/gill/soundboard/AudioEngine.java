package com.gill.soundboard;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class AudioEngine {
    private static final int RATE=22050;
    private static final int MAX_STREAMS=20;
    private static SoundPool pool;
    private static final Map<Integer,Integer> presetIds=new HashMap<>();
    private static final HashSet<Integer> loadedIds=new HashSet<>();
    private static final ArrayDeque<Integer> recentStreams=new ArrayDeque<>();
    private static final List<MediaPlayer> activeFiles=new ArrayList<>();

    static synchronized void prepare(Context context){
        if(pool!=null)return;
        Context app=context.getApplicationContext();
        pool=new SoundPool.Builder().setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_MEDIA).build()).build();
        pool.setOnLoadCompleteListener((soundPool,sample,status)->{
            if(status==0){
                synchronized(AudioEngine.class){loadedIds.add(sample);}
            }
        });
        File dir=new File(app.getCacheDir(),"preset_sounds");
        if(!dir.exists())dir.mkdirs();
        for(int n=0;n<ClipStore.PRESETS.length;n++){
            File wav=new File(dir,"preset_"+n+".wav");
            try{
                if(!wav.exists() || wav.length()<100)writeWav(wav,n);
                int id=pool.load(wav.getAbsolutePath(),1);
                if(id!=0)presetIds.put(n,id);
            }catch(Exception ignored){}
        }
    }

    static synchronized void stop(){
        if(pool!=null){
            for(Integer stream:recentStreams){
                try{pool.stop(stream);}catch(Exception ignored){}
            }
            recentStreams.clear();
        }
        for(MediaPlayer player:new ArrayList<>(activeFiles)){
            try{player.stop();}catch(Exception ignored){}
            try{player.release();}catch(Exception ignored){}
        }
        activeFiles.clear();
    }

    static synchronized void play(Context context,ClipStore.Clip clip){
        if(clip.isPreset())playPreset(context,clip.preset);
        else if(clip.uri!=null)playFile(context,Uri.parse(clip.uri));
    }

    private static void playPreset(Context context,int index){
        prepare(context);
        Integer sample=presetIds.get(index);
        if(sample==null || !loadedIds.contains(sample))return;
        float gain=ClipStore.volume(context)/100f;
        int stream=pool.play(sample,gain,gain,1,0,1f);
        if(stream!=0){
            recentStreams.addLast(stream);
            while(recentStreams.size()>MAX_STREAMS*3)recentStreams.removeFirst();
        }
    }

    private static void playFile(Context context,Uri uri){
        if(activeFiles.size()>=8){
            MediaPlayer oldest=activeFiles.remove(0);
            try{oldest.stop();}catch(Exception ignored){}
            try{oldest.release();}catch(Exception ignored){}
        }
        MediaPlayer m=new MediaPlayer();
        activeFiles.add(m);
        try{
            m.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            m.setDataSource(context.getApplicationContext(),uri);
            m.setOnPreparedListener(player->{
                synchronized(AudioEngine.class){
                    if(activeFiles.contains(player)){
                        float gain=ClipStore.volume(context)/100f;
                        player.setVolume(gain,gain);
                        player.start();
                    }
                }
            });
            m.setOnCompletionListener(player->releaseFile(player));
            m.setOnErrorListener((player,what,extra)->{releaseFile(player);return true;});
            m.prepareAsync();
        }catch(Exception e){
            releaseFile(m);
            Toast.makeText(context,"Could not play this file",Toast.LENGTH_SHORT).show();
        }
    }

    private static synchronized void releaseFile(MediaPlayer player){
        activeFiles.remove(player);
        try{player.release();}catch(Exception ignored){}
    }

    private static void writeWav(File file,int preset)throws IOException{
        double length=duration(preset);
        int samples=(int)(RATE*length);
        int bytes=samples*2;
        byte[] wav=new byte[44+bytes];
        putAscii(wav,0,"RIFF");
        put32(wav,4,36+bytes);
        putAscii(wav,8,"WAVE");
        putAscii(wav,12,"fmt ");
        put32(wav,16,16);
        put16(wav,20,1);
        put16(wav,22,1);
        put32(wav,24,RATE);
        put32(wav,28,RATE*2);
        put16(wav,32,2);
        put16(wav,34,16);
        putAscii(wav,36,"data");
        put32(wav,40,bytes);
        Random noise=new Random(1009L+preset*117L);
        double phase=0;
        for(int i=0;i<samples;i++){
            double t=i/(double)RATE,progress=i/(double)samples;
            double fadeIn=Math.min(1,t/.0025);
            double envelope=fadeIn*Math.pow(Math.max(0,1-progress),.45);
            double pitch=440,wave;
            switch(preset){
                case 0: pitch=150-85*progress;wave=Math.sin(phase)+.55*Math.sin(phase*.5);envelope*=Math.exp(-progress*1.5);break;
                case 1: pitch=330+18*Math.sin(t*41);wave=Math.sin(phase)+.35*Math.sin(2*phase)+.2*Math.sin(3*phase);break;
                case 2: pitch=310-210*progress;wave=Math.sin(phase)+.6*(noise.nextDouble()*2-1)*Math.exp(-progress*12);break;
                case 3: pitch=380+220*Math.sin(2*Math.PI*6*t)*Math.exp(-t*3);wave=Math.sin(phase);break;
                case 4: pitch=1600*Math.pow(.13,progress);wave=Math.sin(phase)*Math.sin(phase*.17);break;
                case 5: pitch=650;wave=Math.sin(phase)*(Math.sin(t*33)>0?1:.1);break;
                case 6: pitch=1250-920*progress;wave=(noise.nextDouble()*2-1)*.8;break;
                case 7: pitch=160+650*Math.abs(Math.sin(t*12));wave=Math.sin(phase)*((i/90)%2==0?1:-.7);break;
                case 8: pitch=85+25*progress;wave=Math.sin(phase)+.4*Math.sin(phase*1.5);break;
                case 9: pitch=270*Math.exp(-progress*2);wave=Math.sin(phase);envelope*=Math.exp(-progress*8);break;
                case 10: pitch=360+110*Math.sin(t*9);wave=Math.sin(phase);break;
                case 11: pitch=450+220*Math.floor(progress*4);wave=Math.sin(phase)+.24*Math.sin(2*phase);break;
                case 12: pitch=430-350*progress;wave=Math.sin(phase)*Math.sin(phase*.28);break;
                case 13: pitch=750+150*(Math.sin(t*12)>0?1:0);wave=Math.sin(phase);break;
                case 14: pitch=125+55*progress;wave=Math.sin(phase)+.4*Math.sin(phase*2);break;
                case 15: pitch=1500-1200*progress;wave=Math.sin(phase);break;
                case 16: pitch=700+200*Math.sin(t*50);wave=Math.sin(phase);break;
                case 17: pitch=210;wave=Math.sin(phase)+.5*(noise.nextDouble()*2-1)*Math.exp(-progress*10);break;
                case 18: pitch=300+1000*progress;wave=Math.sin(phase)*Math.sin(2*Math.PI*24*t);break;
                case 19: pitch=140+700*progress;wave=(noise.nextDouble()*2-1)*.35+Math.sin(phase)*.6;break;
                case 20: pitch=880+180*Math.sin(t*16);wave=Math.sin(phase)*(Math.sin(t*24)>0?1:.1);break;
                case 21: pitch=300+360*Math.floor(progress*4)/4;wave=Math.sin(phase)+.3*Math.sin(2*phase);break;
                case 22: pitch=640;wave=Math.sin(phase)*(Math.sin(t*46)>0?1:0);break;
                case 23: pitch=480+300*Math.cos(t*22);wave=Math.sin(phase)+.18*Math.sin(phase*3);break;
                case 24: pitch=260*(1-progress)+70;wave=Math.sin(phase)*Math.cos(progress*25);break;
                case 25: pitch=220+110*Math.floor(progress*8);wave=Math.sin(phase)+.3*Math.sin(phase*4);break;
                case 26: pitch=65+50*Math.sin(progress*16);wave=Math.sin(phase)+.33*Math.sin(phase*.32);break;
                case 27: pitch=1100-700*progress;wave=Math.sin(phase+.7*Math.sin(phase*.22));break;
                case 28: pitch=250;wave=noise.nextDouble()*2-1;break;
                case 29: pitch=660;wave=Math.sin(phase)+.35*Math.sin(phase*2.01);break;
                case 30: pitch=900+500*Math.exp(-progress*9);wave=Math.sin(phase);envelope*=Math.exp(-progress*7);break;
                case 31: pitch=240+130*Math.sin(t*18);wave=Math.sin(phase)+.65*Math.sin(phase*2)+.3*Math.sin(phase*3);break;
                case 32: pitch=300+200*Math.sin(progress*14);wave=Math.sin(phase)*Math.exp(-progress*2);break;
                case 33: pitch=1200*Math.pow(.13,progress);wave=Math.sin(phase)+.2*Math.sin(phase*.2);break;
                case 34: pitch=500+90*Math.sin(progress*35);wave=Math.sin(phase)*Math.sin(progress*22);break;
                case 35: pitch=850;wave=Math.sin(phase)*(Math.sin(progress*43)>0?1:0);break;
                case 36: pitch=300+600*Math.abs(Math.sin(progress*34));wave=(noise.nextDouble()*2-1)*.3+Math.sin(phase)*.7;break;
                case 37: pitch=90+80*Math.sin(progress*12);wave=Math.sin(phase)+.5*(noise.nextDouble()*2-1)*Math.exp(-progress*10);break;
                case 38: pitch=700+200*Math.sin(progress*60);wave=Math.sin(phase);break;
                case 39: pitch=2200-1300*progress;wave=Math.sin(phase)+.22*Math.sin(phase*2);envelope*=Math.exp(-progress*7);break;
                case 40: pitch=500-360*progress;wave=Math.sin(phase)*Math.cos(progress*14);break;
                case 41: pitch=480+260*Math.floor(progress*4);wave=Math.sin(phase)+.26*Math.sin(phase*2);break;
                case 42: pitch=730+130*Math.sin(progress*70);wave=Math.sin(phase)*(Math.sin(progress*43)>0?1:.2);break;
                case 43: pitch=250+1200*progress;wave=Math.sin(phase)+.15*Math.sin(phase*3);break;
                case 44: pitch=500+250*Math.sin(progress*18);wave=Math.sin(phase)*Math.exp(-progress*5);break;
                case 45: pitch=900-500*progress;wave=Math.sin(phase)+.45*(noise.nextDouble()*2-1);break;
                case 46: pitch=200+180*Math.sin(progress*27);wave=Math.sin(phase)*((i/125)%2==0?1:0);break;
                case 47: pitch=2400*Math.pow(.2,progress);wave=Math.sin(phase)+.4*Math.sin(phase*1.35);break;
                default: pitch=440;wave=Math.sin(phase);break;
            }
            phase+=2*Math.PI*pitch/RATE;
            int amplitude=(int)(32767*Math.max(-1,Math.min(1,wave*.4*envelope)));
            put16(wav,44+i*2,amplitude);
        }
        try(FileOutputStream out=new FileOutputStream(file)){out.write(wav);}
    }

    private static double duration(int index){
        switch(index){
            case 0:case 1:case 6:case 8:case 10:case 14:case 20:return .95;
            case 4:case 9:case 12:case 16:case 22:case 30:case 39:case 46:return .35;
            default:return .6;
        }
    }
    private static void putAscii(byte[] bytes,int offset,String value){
        for(int i=0;i<value.length();i++)bytes[offset+i]=(byte)value.charAt(i);
    }
    private static void put16(byte[] a,int offset,int value){
        a[offset]=(byte)value;
        a[offset+1]=(byte)(value>>>8);
    }
    private static void put32(byte[] a,int offset,int value){
        a[offset]=(byte)value;
        a[offset+1]=(byte)(value>>>8);
        a[offset+2]=(byte)(value>>>16);
        a[offset+3]=(byte)(value>>>24);
    }
}
