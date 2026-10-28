package com.gill.soundboard;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.Toast;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

final class AudioEngine {
    // Real meme recordings are streamed on demand, not synthesized or bundled.
    // SoundPool remains available for backwards compatibility with optional local samples.
    private static final int MAX_STREAMS=10;
    private static SoundPool pool;
    private static final Map<Integer,Integer> presetIds=new HashMap<>();
    private static final Map<Integer,Integer> chaosIds=new HashMap<>();
    private static final HashSet<Integer> ready=new HashSet<>();
    private static final ArrayDeque<Voice> voices=new ArrayDeque<>();
    private static final List<MediaPlayer> activeFiles=new ArrayList<>();
    private static final class Voice {
        int stream;
        long expiry;
        Voice(int stream,long expiry){this.stream=stream;this.expiry=expiry;}
    }

    static synchronized void prepare(Context context) {
        if(pool!=null)return;
        final Context app=context.getApplicationContext();
        pool=new SoundPool.Builder().setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA).build()).build();
        pool.setOnLoadCompleteListener((soundPool,sample,status)->{
            synchronized(AudioEngine.class){
                if(status==0)ready.add(sample);
            }
        });
        for(int n=0;n<ClipStore.PRESETS.length;n++){
            int resource=app.getResources().getIdentifier(
                String.format(java.util.Locale.US,"gill_sfx_%02d",n),
                "raw",app.getPackageName());
            if(resource==0)continue;
            int id=pool.load(app,resource,1);
            if(id!=0)presetIds.put(n,id);
            int chaosResource=app.getResources().getIdentifier(
                String.format(java.util.Locale.US,"gill_chaos_%02d",n),
                "raw",app.getPackageName());
            if(chaosResource!=0){
                int chaosId=pool.load(app,chaosResource,1);
                if(chaosId!=0)chaosIds.put(n,chaosId);
            }
        }
    }
    static synchronized void stop(){
        if(pool!=null){
            for(Voice voice:voices) {
                try{pool.stop(voice.stream);}catch(Exception ignored){}
            }
            voices.clear();
        }
        for(MediaPlayer mp:new ArrayList<>(activeFiles)){
            try{mp.stop();}catch(Exception ignored){}
            try{mp.release();}catch(Exception ignored){}
        }
        activeFiles.clear();
    }
    static synchronized void play(Context context,ClipStore.Clip clip){
        if(clip.isPreset())playPreset(context,clip.preset);
        else if(clip.uri!=null)playFile(context,Uri.parse(clip.uri));
    }
    private static void playPreset(Context context,int index){
        prepare(context);
        Integer id=ClipStore.chaos(context)?chaosIds.get(index):presetIds.get(index);
        if(id==null||!ready.contains(id))return;
        final long now=SystemClock.uptimeMillis();
        while(!voices.isEmpty() && voices.peekFirst().expiry<now){
            voices.removeFirst();
        }
        while(voices.size()>=MAX_STREAMS-1){
            Voice old=voices.removeFirst();
            pool.stop(old.stream);
        }
        float base=ClipStore.volume(context)/100f*(ClipStore.chaos(context)?0.78f:1.0f);
        int stream=pool.play(id,base*.70f,base*.70f,1,0,1f);
        if(stream==0)return;
        voices.addLast(new Voice(stream,now+1000));
        // Rebalance overlapping voices so rapid spam stays under a reasonable peak.
        final float gain=base*.79f/(1f+.55f*(voices.size()-1));
        for(Voice v:voices){
            pool.setVolume(v.stream,gain,gain);
        }
    }
    private static void playFile(Context context,Uri uri){
        if(activeFiles.size()>=4){
            MediaPlayer oldest=activeFiles.remove(0);
            try{oldest.stop();}catch(Exception ignored){}
            try{oldest.release();}catch(Exception ignored){}
        }
        MediaPlayer mp=new MediaPlayer();
        activeFiles.add(mp);
        try{
            mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            if("file".equals(uri.getScheme())){
                mp.setDataSource(uri.getPath());
            }else if(OnlineFavorites.valid(uri.toString())){
                mp.setDataSource(uri.toString());
            }else{
                mp.setDataSource(context.getApplicationContext(),uri);
            }
            mp.setOnPreparedListener(player->{
                synchronized(AudioEngine.class){
                    if(activeFiles.contains(player)){
                        float level=ClipStore.volume(context)/100f*.6f;
                        player.setVolume(level,level);
                        player.setLooping(ClipStore.loop(context));
                        player.start();
                    }
                }
            });
            mp.setOnCompletionListener(AudioEngine::releaseFile);
            mp.setOnErrorListener((player,what,extra)->{releaseFile(player);return true;});
            mp.prepareAsync();
        }catch(Exception ex){
            releaseFile(mp);
            Toast.makeText(context,"Clip unavailable. Check connection or source.",Toast.LENGTH_SHORT).show();
        }
    }
    static synchronized void setLoop(Context c,boolean enable){
        ClipStore.loop(c,enable);
        for(MediaPlayer player:activeFiles){
            try{player.setLooping(enable);}catch(Exception ignored){}
        }
    }
    private static synchronized void releaseFile(MediaPlayer mp){
        activeFiles.remove(mp);
        try{mp.release();}catch(Exception ignored){}
    }
}
