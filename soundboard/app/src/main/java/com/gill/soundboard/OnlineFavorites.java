package com.gill.soundboard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Saved, user-selected links to audio on the original meme website.
 *  No scraped or generated audio is included in the APK.
 */
final class OnlineFavorites {
    static final String PREFIX="https://www.myinstants.com/media/sounds/";
    static final String PREFIX2="https://myinstants.com/media/sounds/";
    static final class Meme {
        final String name;
        final String url;
        final String page;
        Meme(String name,String url,String page){
            this.name=name;this.url=url;this.page=page;
        }
    }
    private static SharedPreferences prefs(Context c){
        return c.getSharedPreferences("gill_online_memes",Context.MODE_PRIVATE);
    }
    static boolean valid(String url){
        if(url==null)return false;
        if(!url.startsWith(PREFIX)&&!url.startsWith(PREFIX2))return false;
        if(url.length()>1000 || url.contains("\\") || url.contains(".."))return false;
        String lower=url.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg");
    }
    static List<Meme> all(Context c){
        ArrayList<Meme> out=new ArrayList<>();
        try{
            JSONArray array=new JSONArray(prefs(c).getString("saved","[]"));
            for(int i=0;i<array.length()&&i<300;i++){
                JSONObject item=array.optJSONObject(i);
                if(item==null)continue;
                String url=item.optString("url","");
                if(valid(url)){
                    out.add(new Meme(item.optString("name","Meme"),
                        url,item.optString("page","https://www.myinstants.com/")));
                }
            }
        }catch(Exception ignored){}
        return out;
    }
    static boolean has(Context c,String url){
        for(Meme item:all(c))if(item.url.equals(url))return true;
        return false;
    }
    static void toggle(Context c,Meme sound){
        List<Meme> old=all(c);
        JSONArray list=new JSONArray();
        boolean exists=has(c,sound.url);
        try{
            if(!exists&&valid(sound.url)){
                JSONObject first=new JSONObject();
                first.put("name",sound.name);
                first.put("url",sound.url);
                first.put("page",sound.page);
                list.put(first);
            }
            for(Meme item:old){
                if(item.url.equals(sound.url))continue;
                JSONObject j=new JSONObject();
                j.put("name",item.name);
                j.put("url",item.url);
                j.put("page",item.page);
                list.put(j);
            }
            prefs(c).edit().putString("saved",list.toString()).apply();
        }catch(Exception ignored){}
    }
}
