package com.gill.soundboard;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.ClipData;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int PICK_AUDIO=41;
    private boolean openAfterPermission=false;
    private LinearLayout clipsArea;
    private TextView overlayStatus;
    private static final int BG=Color.rgb(16,17,23);
    private static final int CARD=Color.rgb(33,34,43);
    private static final int ORANGE=Color.rgb(255,124,48);
    private static final int WHITE=Color.rgb(244,243,241);
    private static final int MUTED=Color.rgb(174,177,185);

    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }
    private TextView label(String text,int size,int color){
        TextView v=new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return v;
    }
    private TextView button(String text,boolean accent){
        TextView b=label(text,13,accent?BG:WHITE);
        b.setGravity(android.view.Gravity.CENTER);
        b.setPadding(dp(12),dp(12),dp(12),dp(12));
        b.setBackground(bg(accent?ORANGE:CARD,11));
        b.setClickable(true);
        return b;
    }
    private void addSpace(LinearLayout root,int height){
        View v=new View(this);
        root.addView(v,new LinearLayout.LayoutParams(1,dp(height)));
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20),dp(28),dp(20),dp(36));
        scroll.addView(content);
        setContentView(scroll);

        TextView title=label("G I L L   /   S O U N D",24,WHITE);
        title.setTypeface(null,1);
        content.addView(title);
        addSpace(content,7);
        content.addView(label("NO ADS  |  FLOATING SOUNDBOARD",11,ORANGE));
        addSpace(content,13);
        TextView intro=label("Play sounds over games. Import your own MP3, WAV or other Android-supported audio clips.",14,MUTED);
        intro.setLineSpacing(dp(3),1f);
        content.addView(intro);
        addSpace(content,24);

        TextView start=button("OPEN FLOATING SOUNDBOARD",true);
        content.addView(start,new LinearLayout.LayoutParams(-1,dp(50)));
        start.setOnClickListener(v->startPanel());
        addSpace(content,8);

        TextView stop=button("CLOSE FLOATING SOUNDBOARD",false);
        content.addView(stop,new LinearLayout.LayoutParams(-1,dp(46)));
        stop.setOnClickListener(v->{
            startService(new Intent(this,OverlayService.class).setAction(OverlayService.STOP));
            overlayStatus.setText("Floating panel closed.");
        });
        addSpace(content,10);
        overlayStatus=label("Open the panel and allow Display over other apps.",12,MUTED);
        content.addView(overlayStatus);
        addSpace(content,26);

        TextView add=button("+ IMPORT AUDIO FILES",false);
        content.addView(add,new LinearLayout.LayoutParams(-1,dp(48)));
        add.setOnClickListener(v->pickFiles());
        addSpace(content,20);

        content.addView(label("VOLUME",11,ORANGE));
        SeekBar volume=new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(ClipStore.volume(this));
        content.addView(volume);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,boolean user){
                if(user)ClipStore.volume(MainActivity.this,value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });
        addSpace(content,16);

        TextView section=label("YOUR SOUND BUTTONS",15,WHITE);
        section.setTypeface(null,1);
        content.addView(section);
        addSpace(content,10);

        clipsArea=new LinearLayout(this);
        clipsArea.setOrientation(LinearLayout.VERTICAL);
        content.addView(clipsArea);
        addSpace(content,18);
        TextView note=label("Sound is played through Android media audio. Roblox microphone pickup is device-dependent; the app cannot force Roblox to transmit internal audio.",12,MUTED);
        note.setLineSpacing(dp(2),1f);
        content.addView(note);
        refreshClips();
    }
    private void refreshClips(){
        if(clipsArea==null)return;
        clipsArea.removeAllViews();
        List<ClipStore.Clip> all=ClipStore.get(this);
        for(ClipStore.Clip clip:all){
            LinearLayout line=new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lineP=new LinearLayout.LayoutParams(-1,dp(48));
            lineP.bottomMargin=dp(6);
            clipsArea.addView(line,lineP);
            TextView play=button(clip.name,false);
            play.setSingleLine(true);
            play.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams playP=new LinearLayout.LayoutParams(0,-1,1);
            line.addView(play,playP);
            play.setOnClickListener(v->AudioEngine.play(this,clip));
            if(!clip.isPreset()){
                TextView remove=button("REMOVE",false);
                remove.setTextColor(ORANGE);
                LinearLayout.LayoutParams removeP=new LinearLayout.LayoutParams(dp(85),-1);
                removeP.leftMargin=dp(6);
                line.addView(remove,removeP);
                remove.setOnClickListener(v->{
                    ClipStore.remove(this,clip.uri);
                    refreshClips();
                    refreshOverlay();
                });
            }
        }
        TextView stop=button("STOP SOUND",true);
        clipsArea.addView(stop,new LinearLayout.LayoutParams(-1,dp(45)));
        stop.setOnClickListener(v->AudioEngine.stop());
    }
    private void startPanel(){
        if(!Settings.canDrawOverlays(this)){
            openAfterPermission=true;
            Toast.makeText(this,"Allow GILL Soundboard to display over other apps",Toast.LENGTH_LONG).show();
            Intent intent=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:"+getPackageName()));
            startActivity(intent);
            return;
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},45);
        }
        Intent intent=new Intent(this,OverlayService.class).setAction(OverlayService.START);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);
        overlayStatus.setText("Floating panel requested. Open Roblox to see it.");
    }
    @Override protected void onResume(){
        super.onResume();
        if(openAfterPermission && Settings.canDrawOverlays(this)){
            openAfterPermission=false;
            startPanel();
        }
    }
    private void pickFiles(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        startActivityForResult(intent,PICK_AUDIO);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_AUDIO || result!=RESULT_OK || data==null)return;
        int added=0;
        ClipData many=data.getClipData();
        if(many!=null){
            for(int i=0;i<many.getItemCount();i++){
                if(importOne(many.getItemAt(i).getUri()))added++;
            }
        }else if(data.getData()!=null && importOne(data.getData()))added++;
        refreshClips();
        refreshOverlay();
        Toast.makeText(this,added+" sound(s) imported",Toast.LENGTH_SHORT).show();
    }
    private boolean importOne(Uri uri){
        try {
            getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }catch(SecurityException ignored){}
        String name="Custom sound";
        try(Cursor c=getContentResolver().query(uri,
            new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null && c.moveToFirst())name=c.getString(0);
        }catch(Exception ignored){}
        return ClipStore.add(this,name,uri);
    }
    private void refreshOverlay(){
        if(OverlayService.isRunning())
            startService(new Intent(this,OverlayService.class).setAction(OverlayService.REFRESH));
    }
}
