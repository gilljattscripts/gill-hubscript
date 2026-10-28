package com.gill.soundboard;

import android.content.Context;
import android.net.Uri;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** One-tap import of a ZIP of user-owned clips (no uploaded audio goes into GitHub).
 * Files stay in this application's private storage, available offline thereafter.
 */
final class ZipPackImporter {
    static final int MAX_CLIPS=250;
    static final int MAX_FILE_BYTES=16*1024*1024;
    static final long MAX_TOTAL_BYTES=220L*1024*1024;

    static int importPack(Context context, Uri zipUri) throws Exception {
        InputStream input=context.getContentResolver().openInputStream(zipUri);
        if(input==null)throw new Exception("Unable to open ZIP file");
        File folder=new File(context.getFilesDir(),"imported_memes");
        if(!folder.exists()&&!folder.mkdirs())throw new Exception("Could not create audio folder");
        int added=0,seen=0;
        long total=0;
        byte[] buffer=new byte[16*1024];
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(input))){
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                if(entry.isDirectory()){zip.closeEntry();continue;}
                String entryName=entry.getName();
                String base=entryName.substring(entryName.lastIndexOf('/')+1);
                String lower=base.toLowerCase(Locale.ROOT);
                String ext=lower.endsWith(".mp3")?".mp3":
                    lower.endsWith(".wav")?".wav":
                    lower.endsWith(".ogg")?".ogg":
                    lower.endsWith(".m4a")?".m4a":null;
                if(ext==null){zip.closeEntry();continue;}
                if(++seen>MAX_CLIPS)throw new Exception("Pack exceeds 250 sounds");
                if(entry.getSize()>MAX_FILE_BYTES)throw new Exception("A clip exceeds 16 MB");
                ByteArrayOutputStream content=new ByteArrayOutputStream();
                int n;
                while((n=zip.read(buffer))!=-1){
                    if(content.size()+n>MAX_FILE_BYTES)throw new Exception("A clip exceeds 16 MB");
                    content.write(buffer,0,n);
                }
                total+=content.size();
                if(total>MAX_TOTAL_BYTES)throw new Exception("Pack exceeds the 220 MB limit");
                if(content.size()<80){zip.closeEntry();continue;}
                MessageDigest digester=MessageDigest.getInstance("SHA-256");
                byte[] digest=digester.digest(content.toByteArray());
                StringBuilder hash=new StringBuilder();
                for(int i=0;i<16;i++)hash.append(String.format(Locale.US,"%02x",digest[i]&255));
                File target=new File(folder,hash.toString()+ext);
                if(!target.exists()){
                    File temporary=new File(folder,hash.toString()+".partial");
                    try(FileOutputStream out=new FileOutputStream(temporary)){
                        content.writeTo(out);
                    }
                    if(!temporary.renameTo(target)){
                        temporary.delete();
                        throw new Exception("Unable to save a clip");
                    }
                }
                String label=base.substring(0,base.length()-ext.length()).replace('_',' ').trim();
                if(label.isEmpty())label="Meme sound";
                if(ClipStore.addPacked(context,label,target))added++;
                zip.closeEntry();
            }
        }
        if(seen==0)throw new Exception("No MP3, WAV, OGG or M4A files in ZIP");
        return added;
    }
}
