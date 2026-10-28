package com.gill.soundboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Android system TTS; selected voices depend on the phone's installed speech engine.
 *  No paid API, hidden network permission, or impersonation/voice cloning.
 */
final class SpeechEngine {
    static final String MALE="Male style  /  deeper";
    static final String FEMALE="Female style  /  brighter";
    private static TextToSpeech engine;
    private static boolean ready=false;
    private static boolean loading=false;
    private static final String PREF="gill_speech";
    private SpeechEngine(){}
    static synchronized void prepare(Context c) {
        if(engine!=null||loading)return;
        loading=true;
        final Context app=c.getApplicationContext();
        engine=new TextToSpeech(app,status->{
            synchronized(SpeechEngine.class){
                ready=status==TextToSpeech.SUCCESS;
                loading=false;
                if(ready){
                    try{engine.setLanguage(Locale.US);}catch(Exception ignored){}
                }
            }
        });
    }
    private static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    static String selected(Context c){return prefs(c).getString("selected",MALE);}
    static void select(Context c,String option){prefs(c).edit().putString("selected",option).apply();}
    private static Map<String,String> saved(Context c){
        LinkedHashMap<String,String> choices=new LinkedHashMap<>();
        try{
            JSONObject obj=new JSONObject(prefs(c).getString("custom","{}"));
            java.util.Iterator<String> iter=obj.keys();
            while(iter.hasNext()){
                String key=iter.next();
                String id=obj.optString(key,"");
                if(!id.isEmpty())choices.put("CUSTOM  /  "+key,id);
            }
        }catch(Exception ignored){}
        return choices;
    }
    static List<String> choices(Context c){
        prepare(c);
        ArrayList<String> result=new ArrayList<>();
        result.add(MALE);result.add(FEMALE);
        result.addAll(saved(c).keySet());
        if(ready && engine!=null){
            try{
                List<Voice> english=new ArrayList<>();
                for(Voice v:engine.getVoices()){
                    if(v!=null && v.getName()!=null &&
                        "en".equals(v.getLocale().getLanguage()))english.add(v);
                }
                english.sort(Comparator.comparing(Voice::getName));
                for(Voice voice:english){
                    if(result.size()>=65)break;
                    result.add("SYSTEM  /  "+voice.getName());
                }
            }catch(Exception ignored){}
        }
        return result;
    }
    static boolean saveCustom(Context c,String name,String source){
        if(name==null||name.trim().isEmpty()||source==null)return false;
        String id=null;
        if(source.startsWith("SYSTEM  /  "))id=source.substring(10);
        else if(source.startsWith("CUSTOM  /  "))id=saved(c).get(source);
        if(id==null||id.isEmpty())return false;
        try{
            JSONObject data=new JSONObject(prefs(c).getString("custom","{}"));
            data.put(name.trim().substring(0,Math.min(32,name.trim().length())),id);
            prefs(c).edit().putString("custom",data.toString()).apply();
            return true;
        }catch(Exception ignored){return false;}
    }
    static synchronized boolean speak(Context c,String text,boolean chaos){
        prepare(c);
        if(!ready||engine==null)return false;
        if(text==null||text.trim().isEmpty())return false;
        String words=text.trim();
        if(words.length()>350)words=words.substring(0,350);
        String voiceName=selected(c);
        float pitch=voiceName.equals(FEMALE)?1.22f:.86f;
        if(voiceName.startsWith("SYSTEM  /  ")||voiceName.startsWith("CUSTOM  /  ")){
            String id=voiceName.startsWith("SYSTEM  /  ")
                ?voiceName.substring(10):saved(c).get(voiceName);
            if(id!=null){
                try{
                    for(Voice voice:engine.getVoices()){
                        if(voice!=null && id.equals(voice.getName())){
                            engine.setVoice(voice);
                            pitch=1f;break;
                        }
                    }
                }catch(Exception ignored){}
            }
        }else{
            try{engine.setLanguage(Locale.US);}catch(Exception ignored){}
        }
        engine.setPitch(chaos?Math.max(.5f,pitch*.65f):pitch);
        engine.setSpeechRate(chaos?.91f:1f);
        Bundle settings=new Bundle();
        settings.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,chaos?.77f:.94f);
        return engine.speak(words,TextToSpeech.QUEUE_FLUSH,settings,
            "gill-"+System.currentTimeMillis())!=TextToSpeech.ERROR;
    }
    static synchronized void stop(){
        if(engine!=null)try{engine.stop();}catch(Exception ignored){}
    }
}
