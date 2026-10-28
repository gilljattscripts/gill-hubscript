package com.gill.soundboard;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

final class AudioEngine {
    private static MediaPlayer player;
    private static AudioTrack track;
    private static final Handler handler=new Handler(Looper.getMainLooper());
    private static final int RATE=22050;

    static synchronized void stop(){
        if(player!=null){
            try{player.stop();}catch(Exception ignored){}
            try{player.release();}catch(Exception ignored){}
            player=null;
        }
        if(track!=null){
            try{track.stop();}catch(Exception ignored){}
            try{track.release();}catch(Exception ignored){}
            track=null;
        }
    }

    static synchronized void play(Context c,ClipStore.Clip clip){
        if(clip.isPreset()){ playSynth(c,clip.preset);return; }
        playFile(c,Uri.parse(clip.uri));
    }

    private static synchronized void playFile(Context c,Uri uri){
        stop();
        final MediaPlayer m=new MediaPlayer();
        player=m;
        try {
            m.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            m.setDataSource(c.getApplicationContext(),uri);
            m.setOnPreparedListener(mp -> {
                synchronized(AudioEngine.class){
                    if(player==mp){
                        mp.setVolume(ClipStore.volume(c)/100f,ClipStore.volume(c)/100f);
                        mp.start();
                    }
                }
            });
            m.setOnCompletionListener(mp -> {
                synchronized(AudioEngine.class){
                    if(player==mp){mp.release();player=null;}
                }
            });
            m.setOnErrorListener((mp,what,extra)->{
                synchronized(AudioEngine.class){
                    if(player==mp){try{mp.release();}catch(Exception ignored){}player=null;}
                }
                return true;
            });
            m.prepareAsync();
        }catch(Exception e){
            stop();
            Toast.makeText(c,"Could not play this file",Toast.LENGTH_SHORT).show();
        }
    }

    // Original synthesized effects: no copyrighted clips or external downloads.
    private static synchronized void playSynth(Context c,int preset){
        stop();
        final double length=(preset==0 ? .75 : preset==6 ? .9 : .55);
        int count=(int)(RATE*length);
        short[] pcm=new short[count];
        for(int i=0;i<count;i++){
            double t=i/(double)RATE, phase=t/length;
            double env=Math.pow(Math.sin(Math.PI*phase),.35);
            double freq=440, wave;
            switch(preset){
                case 0: freq=300+30*Math.sin(t*25);wave=Math.sin(2*Math.PI*freq*t)+.4*Math.sin(2*Math.PI*freq*1.5*t);break;
                case 1: freq=1300*Math.pow(.12,phase);wave=Math.sin(2*Math.PI*(1300*t-572*t*t));break;
                case 2: freq=310+150*Math.cos(2*Math.PI*8*t)*Math.exp(-t*4);wave=Math.sin(2*Math.PI*freq*t);break;
                case 3: freq=460-260*phase;wave=Math.sin(2*Math.PI*(460*t-130*t*t));break;
                case 4: freq=200*Math.exp(-phase*3);wave=Math.sin(2*Math.PI*freq*t);env*=Math.exp(-phase*7);break;
                case 5: freq=880;wave=Math.sin(2*Math.PI*freq*t)*(Math.sin(t*10*Math.PI)>0?1:.05);break;
                case 6: freq=120+((i/180)%5)*190;wave=Math.sin(2*Math.PI*freq*t)*(i%13<7?1:-.8);break;
                default: freq=680+(Math.floor(t*9)%2)*180;wave=Math.sin(2*Math.PI*freq*t);break;
            }
            pcm[i]=(short)(Math.max(-1,Math.min(1,wave*.4*env))*32767);
        }
        try {
            AudioTrack out=new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(pcm.length*2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
            track=out;
            out.write(pcm,0,pcm.length);
            out.setVolume(ClipStore.volume(c)/100f);
            out.play();
            handler.postDelayed(()->{
                synchronized(AudioEngine.class){
                    if(track==out){
                        try{out.stop();}catch(Exception ignored){}
                        out.release();
                        track=null;
                    }
                }
            },(long)(length*1000)+180);
        }catch(Exception e){stop();}
    }
}
