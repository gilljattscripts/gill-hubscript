package com.gill.soundboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class ClipStore {
    static final String[] PRESETS = {
        "BASS BOOM", "AIR HORN", "BONK", "BOING", "LASER", "ERROR", "SCRATCH", "GLITCH",
        "DRAMATIC", "POP", "SIREN", "LEVEL UP", "FAIL", "PHONE RING", "SUSPENSE", "CARTOON ZIP",
        "CHIRP", "METAL HIT", "REWIND", "WHOOSH", "ALARM", "ARCADE", "BEEP SPAM", "SPACE BLIP",
        "RECORD STOP", "8-BIT HIT", "DARK PULSE", "CARTOON SLIDE", "RADIO STATIC", "BELL", "PING", "TRUMPET",
        "BUBBLE", "BASS DROP", "UFO", "CLOCK TICK", "VHS GLITCH", "SMASH", "DIZZY", "SPARK",
        "GAME OVER", "SUCCESS", "WARNING", "POWER UP", "SQUEAK", "SCREAM SYNTH", "KNOCK", "SCI-FI SHOT"
    };
    static final class Clip {
        final int preset;
        final String name;
        final String uri;
        Clip(int preset, String name, String uri) {
            this.preset=preset; this.name=name; this.uri=uri;
        }
        boolean isPreset(){ return preset>=0; }
    }
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("gill_soundboard",Context.MODE_PRIVATE);
    }
    static int volume(Context c){ return prefs(c).getInt("volume",85); }
    static void volume(Context c,int value){
        prefs(c).edit().putInt("volume",Math.max(0,Math.min(100,value))).apply();
    }
    static float size(Context c){ return prefs(c).getFloat("size",1f); }
    static void size(Context c,float value){
        prefs(c).edit().putFloat("size",Math.max(.7f,Math.min(1.4f,value))).apply();
    }
    static boolean locked(Context c){return prefs(c).getBoolean("locked",false);}
    static void locked(Context c,boolean value){prefs(c).edit().putBoolean("locked",value).apply();}
    static int x(Context c){return prefs(c).getInt("x",24);}
    static int y(Context c){return prefs(c).getInt("y",200);}
    static void position(Context c,int x,int y){
        prefs(c).edit().putInt("x",x).putInt("y",y).apply();
    }
    private static JSONArray imported(Context c) {
        try {return new JSONArray(prefs(c).getString("clips","[]"));}
        catch(Exception e){return new JSONArray();}
    }
    static List<Clip> get(Context c) {
        ArrayList<Clip> result=new ArrayList<>();
        for(int i=0;i<PRESETS.length;i++)result.add(new Clip(i,PRESETS[i],null));
        JSONArray array=imported(c);
        for(int i=0;i<array.length();i++){
            JSONObject object=array.optJSONObject(i);
            if(object==null)continue;
            String uri=object.optString("uri","");
            if(uri.startsWith("content://")){
                result.add(new Clip(-1,object.optString("name","Custom sound"),uri));
            }
        }
        return result;
    }
    static boolean add(Context c,String name,Uri uri) {
        String str=uri.toString();
        if(!str.startsWith("content://"))return false;
        JSONArray arr=imported(c);
        if(arr.length()>=60)return false;
        for(int i=0;i<arr.length();i++){
            JSONObject item=arr.optJSONObject(i);
            if(item!=null && str.equals(item.optString("uri")))return false;
        }
        try{
            JSONObject item=new JSONObject();
            item.put("name",name.length()>55?name.substring(0,55):name);
            item.put("uri",str);
            arr.put(item);
            prefs(c).edit().putString("clips",arr.toString()).apply();
            return true;
        }catch(Exception ignored){return false;}
    }
    static void remove(Context c,String uri){
        JSONArray source=imported(c),result=new JSONArray();
        for(int i=0;i<source.length();i++){
            JSONObject item=source.optJSONObject(i);
            if(item!=null && !uri.equals(item.optString("uri")))result.put(item);
        }
        prefs(c).edit().putString("clips",result.toString()).apply();
    }
}
