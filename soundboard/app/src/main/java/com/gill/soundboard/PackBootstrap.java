package com.gill.soundboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Installs user-supplied meme recordings bundled directly into the shareable
 * APK at packaging time. The public source repository never stores the audio.
 * Original recordings remain byte-for-byte identical to the cleaned ZIP.
 */
final class PackBootstrap {
    private static final String MARKER="assets/gill_builtin_pack_marker.txt";
    private static final String PREFS="gill_bundled_pack";
    private PackBootstrap(){}

    static void schedule(Context context,Runnable refresh){
        final Context app=context.getApplicationContext();
        new Thread(()->{
            String token=null;
            File apk=new File(app.getApplicationInfo().sourceDir);
            try(ZipFile zip=new ZipFile(apk)){
                ZipEntry entry=zip.getEntry(MARKER);
                if(entry==null)return; // No built-in clips in source-only CI builds.
                if(entry.getSize()<4||entry.getSize()>200)return;
                try(InputStream in=zip.getInputStream(entry)){
                    byte[] bytes=new byte[(int)entry.getSize()];
                    int offset=0;
                    while(offset<bytes.length){
                        int read=in.read(bytes,offset,bytes.length-offset);
                        if(read<0)throw new IllegalStateException("Incomplete meme pack marker");
                        offset+=read;
                    }
                    token=new String(bytes,StandardCharsets.UTF_8).trim();
                }
            }catch(Exception e){return;}
            if(token==null||token.isEmpty())return;
            SharedPreferences prefs=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            if(token.equals(prefs.getString("completed","")))return;
            try{
                ZipPackImporter.importPack(app,Uri.fromFile(apk));
                prefs.edit().putString("completed",token).apply();
                if(refresh!=null){
                    android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
                    main.post(refresh);
                }
            }catch(Exception ignored){
                // Retry on next launch; never mark a partial/failed install complete.
            }
        },"GillBuiltInMemeInstall").start();
    }
}
