package com.gill.soundboard;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int PICK_AUDIO=41;
    private static final String[] GROUP_NAMES={"ALL","BASS","FUNNY","DRAMA","ARCADE","EFFECTS","ALERTS",
        "REACTIONS","GAMING","HORROR","GLITCH","MUSIC","CHAOS",
        "VOICES","KEYBOARD","VOICE FX","EXTRAS","CUSTOM"};
    private boolean openAfterPermission=false;
    private LinearLayout soundGrid;
    private LinearLayout chipRow;
    private TextView overlayStatus;
    private String query="";
    private int selectedGroup=0;
    private final int BG=BrandStyle.BG, PANEL=BrandStyle.PANEL, CARD=BrandStyle.CARD,
        PURPLE=BrandStyle.PURPLE, WHITE=BrandStyle.WHITE, DIM=BrandStyle.DIM;

    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private TextView text(String content,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(content);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void space(LinearLayout parent,int height){
        parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));
    }
    private TextView button(String s,boolean filled,int icon){
        TextView b=text(s,13,filled?BG:WHITE,true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(13),dp(11),dp(13),dp(11));
        b.setBackground(filled?BrandStyle.accent(dp(13)):BrandStyle.bg(CARD,dp(13),true));
        if(icon>=0){
            android.graphics.drawable.Drawable d=BrandStyle.icon(icon,dp(19),filled?BG:BrandStyle.LAVENDER);
            b.setCompoundDrawables(d,null,null,null);
            b.setCompoundDrawablePadding(dp(9));
        }
        b.setClickable(true);
        return b;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        AudioEngine.prepare(this);
        SpeechEngine.prepare(this);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(19),dp(24),dp(19),dp(32));
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout brandRow=new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(brandRow,new LinearLayout.LayoutParams(-1,dp(68)));

        ImageView mark=new ImageView(this);
        mark.setImageResource(R.drawable.gill_logo);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        brandRow.addView(mark,new LinearLayout.LayoutParams(dp(64),dp(64)));
        LinearLayout brandText=new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams brandParams=new LinearLayout.LayoutParams(0,-2,1);
        brandParams.leftMargin=dp(10);
        brandRow.addView(brandText,brandParams);
        TextView title=text("GILL SOUNDBOARD",21,WHITE,true);
        title.setLetterSpacing(.025f);
        brandText.addView(title);
        space(brandText,2);
        TextView sub=text("INSTANT PLAYBACK  /  128 SOUNDS",10,BrandStyle.LAVENDER,true);
        sub.setLetterSpacing(.06f);
        brandText.addView(sub);
        space(root,21);

        LinearLayout hero=new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(16),dp(17),dp(16),dp(18));
        hero.setBackground(BrandStyle.bg(PANEL,dp(20),true));
        root.addView(hero,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout badges=new LinearLayout(this);
        badges.setGravity(Gravity.CENTER_VERTICAL);
        hero.addView(badges);
        TextView badge=text("●  READY TO PLAY",10,BrandStyle.GREEN,true);
        badge.setPadding(dp(9),dp(6),dp(9),dp(6));
        badge.setBackground(BrandStyle.bg(0xff20372f,dp(20),false));
        badges.addView(badge);
        TextView badge2=text("  NO ADS",10,BrandStyle.LAVENDER,true);
        LinearLayout.LayoutParams b2=new LinearLayout.LayoutParams(-2,-2);
        b2.leftMargin=dp(9);
        badges.addView(badge2,b2);
        space(hero,15);
        TextView intro=text("Sound on demand.",21,WHITE,true);
        hero.addView(intro);
        space(hero,4);
        TextView caption=text("128 ready-to-play effects, keyboard sounds and voice reactions. Spam instantly or try Destroyed Mic FX.",12,DIM,false);
        caption.setLineSpacing(dp(3),1f);
        hero.addView(caption);
        space(hero,16);
        TextView start=button("OPEN FLOATING PANEL",true,7);
        hero.addView(start,new LinearLayout.LayoutParams(-1,dp(51)));
        start.setOnClickListener(v->startPanel());
        space(hero,9);
        TextView close=button("CLOSE FLOATING PANEL",false,8);
        hero.addView(close,new LinearLayout.LayoutParams(-1,dp(42)));
        close.setOnClickListener(v->{
            startService(new Intent(this,OverlayService.class).setAction(OverlayService.STOP));
            overlayStatus.setText("Floating panel closed.");
        });
        space(hero,9);
        TextView chaos=button(ClipStore.chaos(this)?"DESTROYED MIC  /  ON":"DESTROYED MIC  /  OFF",false,2);
        chaos.setTextColor(ClipStore.chaos(this)?BrandStyle.GREEN:WHITE);
        hero.addView(chaos,new LinearLayout.LayoutParams(-1,dp(44)));
        chaos.setOnClickListener(v->{
            boolean enabled=!ClipStore.chaos(this);
            ClipStore.chaos(this,enabled);
            chaos.setText(enabled?"DESTROYED MIC  /  ON":"DESTROYED MIC  /  OFF");
            chaos.setTextColor(enabled?BrandStyle.GREEN:WHITE);
        });
        space(hero,8);
        TextView typed=button("TYPE TO SPEAK  /  MALE, FEMALE & CUSTOM",false,6);
        hero.addView(typed,new LinearLayout.LayoutParams(-1,dp(48)));
        typed.setOnClickListener(v->startActivity(new Intent(this,SpeechActivity.class)));
        space(hero,7);
        TextView caution=text("CHAOS MODE  /  Intentionally crunchy built-in clips, with a capped output level. Windows has live voice effects too.",11,DIM,false);
        caution.setLineSpacing(dp(2),1f);
        hero.addView(caution);
        space(hero,12);
        overlayStatus=text("Enable “Display over other apps” on first launch.",11,DIM,false);
        hero.addView(overlayStatus);
        space(root,23);

        LinearLayout line=new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(line);
        TextView libraryTitle=text("SOUND LIBRARY",17,WHITE,true);
        line.addView(libraryTitle,new LinearLayout.LayoutParams(0,-2,1));
        TextView included=text("128 INCLUDED",10,BrandStyle.LAVENDER,true);
        line.addView(included);
        space(root,12);

        LinearLayout searchWrap=new LinearLayout(this);
        searchWrap.setGravity(Gravity.CENTER_VERTICAL);
        searchWrap.setPadding(dp(12),0,dp(9),0);
        searchWrap.setBackground(BrandStyle.bg(CARD,dp(12),true));
        root.addView(searchWrap,new LinearLayout.LayoutParams(-1,dp(48)));
        searchWrap.addView(BrandStyle.graphic(this,10,dp(19),BrandStyle.DIM),
            new LinearLayout.LayoutParams(dp(22),dp(22)));
        EditText search=new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(13);
        search.setTextColor(WHITE);
        search.setHintTextColor(DIM);
        search.setHint("Search sounds...");
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setPadding(dp(8),0,0,0);
        searchWrap.addView(search,new LinearLayout.LayoutParams(0,-1,1));
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int a,int c,int f){}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){
                query=s.toString().toLowerCase(java.util.Locale.ROOT).trim();
                renderSounds();
            }
            @Override public void afterTextChanged(Editable s){}
        });
        space(root,12);

        HorizontalScrollView chips=new HorizontalScrollView(this);
        chips.setHorizontalScrollBarEnabled(false);
        root.addView(chips);
        chipRow=new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(chipRow);
        renderChips();
        space(root,12);

        soundGrid=new LinearLayout(this);
        soundGrid.setOrientation(LinearLayout.VERTICAL);
        root.addView(soundGrid);
        renderSounds();
        space(root,21);

        LinearLayout volumeCard=new LinearLayout(this);
        volumeCard.setOrientation(LinearLayout.VERTICAL);
        volumeCard.setPadding(dp(13),dp(14),dp(13),dp(13));
        volumeCard.setBackground(BrandStyle.bg(PANEL,dp(15),true));
        root.addView(volumeCard);
        TextView volLabel=text("PLAYBACK VOLUME",12,WHITE,true);
        volumeCard.addView(volLabel);
        SeekBar volume=new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(ClipStore.volume(this));
        volume.setProgressTintList(ColorStateList.valueOf(PURPLE));
        volume.setThumbTintList(ColorStateList.valueOf(BrandStyle.LAVENDER));
        volumeCard.addView(volume);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar s,int v,boolean fromUser){
                if(fromUser)ClipStore.volume(MainActivity.this,v);
            }
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){}
        });
        TextView mute=button("STOP ALL AUDIO",false,8);
        volumeCard.addView(mute,new LinearLayout.LayoutParams(-1,dp(44)));
        mute.setOnClickListener(v->AudioEngine.stop());
        space(root,16);

        TextView add=button("OPTIONAL  /  IMPORT YOUR OWN CLIPS",false,6);
        root.addView(add,new LinearLayout.LayoutParams(-1,dp(45)));
        add.setOnClickListener(v->pickFiles());
        space(root,15);
        TextView foot=text("Made for fast, ad-free playback. Android routes sounds as media audio; Roblox microphone transmission depends on the device.",11,DIM,false);
        foot.setLineSpacing(dp(3),1f);
        root.addView(foot);
    }

    private void renderChips(){
        if(chipRow==null)return;
        chipRow.removeAllViews();
        for(int i=0;i<GROUP_NAMES.length;i++){
            final int group=i;
            final boolean active=(i==selectedGroup);
            TextView chip=text(GROUP_NAMES[i],11,active?BG:DIM,true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(12),0,dp(12),0);
            chip.setBackground(active?BrandStyle.accent(dp(20)):
                BrandStyle.bg(CARD,dp(20),false));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(35));
            cp.rightMargin=dp(7);
            chipRow.addView(chip,cp);
            chip.setOnClickListener(v->{
                selectedGroup=group;
                renderChips();
                renderSounds();
            });
        }
    }
    private boolean visibleClip(ClipStore.Clip clip){
        if(selectedGroup==17 && clip.isPreset())return false;
        if(selectedGroup>=1 && selectedGroup<=16 &&
            (!clip.isPreset() || clip.preset/8!=selectedGroup-1))return false;
        if(selectedGroup==0 || selectedGroup==17 ||
            (selectedGroup>=1 && selectedGroup<=16)){
            return clip.name.toLowerCase(java.util.Locale.ROOT).contains(query);
        }
        return true;
    }
    private View tile(ClipStore.Clip clip){
        int category=BrandStyle.categoryOf(clip);
        int color=BrandStyle.categoryColor(category);
        LinearLayout tile=new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(13),dp(12),dp(9),dp(11));
        tile.setBackground(BrandStyle.bg(CARD,dp(15),true));
        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        tile.addView(top,new LinearLayout.LayoutParams(-1,dp(31)));
        TextView iconBG=text("",10,color,true);
        iconBG.setGravity(Gravity.CENTER);
        iconBG.setBackground(BrandStyle.bg(PANEL,dp(9),false));
        top.addView(iconBG,new LinearLayout.LayoutParams(dp(32),dp(32)));
        iconBG.setCompoundDrawables(null,null,null,null);
        iconBG.setPadding(dp(6),dp(6),dp(6),dp(6));
        iconBG.setBackground(BrandStyle.bg(PANEL,dp(9),false));
        iconBG.setCompoundDrawables(BrandStyle.icon(BrandStyle.soundIconForCategory(category),dp(18),color),null,null,null);
        TextView number=text(String.format(java.util.Locale.US,"%02d",clip.isPreset()?clip.preset+1:0),
            10,DIM,false);
        number.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        top.addView(number,new LinearLayout.LayoutParams(0,-1,1));
        space(tile,10);
        TextView name=text(clip.name,12,WHITE,true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(name);
        space(tile,4);
        TextView hint=text("TAP TO PLAY",9,DIM,true);
        tile.addView(hint);
        tile.setClickable(true);
        tile.setOnClickListener(v->AudioEngine.play(this,clip));
        return tile;
    }
    private void renderSounds(){
        if(soundGrid==null)return;
        soundGrid.removeAllViews();
        List<ClipStore.Clip> sounds=ClipStore.get(this);
        java.util.ArrayList<ClipStore.Clip> selected=new java.util.ArrayList<>();
        for(ClipStore.Clip c:sounds)if(visibleClip(c))selected.add(c);
        for(int i=0;i<selected.size();i+=2){
            LinearLayout row=new LinearLayout(this);
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(113));
            rp.bottomMargin=dp(8);
            soundGrid.addView(row,rp);
            for(int j=i;j<i+2;j++){
                if(j<selected.size()){
                    LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-1,1);
                    if(j%2==1)tp.leftMargin=dp(8);
                    row.addView(tile(selected.get(j)),tp);
                }else{
                    row.addView(new View(this),new LinearLayout.LayoutParams(0,-1,1));
                }
            }
        }
        if(selected.isEmpty()){
            TextView none=text("No sounds found. Try another category.",13,DIM,false);
            none.setPadding(dp(8),dp(15),dp(8),dp(15));
            soundGrid.addView(none);
        }
    }
    private void startPanel(){
        if(!Settings.canDrawOverlays(this)){
            openAfterPermission=true;
            Toast.makeText(this,"Allow GILL Soundboard to appear over other apps",Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:"+getPackageName())));
            return;
        }
        if(Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},45);
        }
        Intent service=new Intent(this,OverlayService.class).setAction(OverlayService.START);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);
        overlayStatus.setText("Floating panel activated. Switch to Roblox.");
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
        if(request!=PICK_AUDIO||result!=RESULT_OK||data==null)return;
        int added=0;
        ClipData multiple=data.getClipData();
        if(multiple!=null){
            for(int i=0;i<multiple.getItemCount();i++)
                if(importClip(multiple.getItemAt(i).getUri()))added++;
        }else if(data.getData()!=null && importClip(data.getData()))added++;
        renderSounds();
        if(OverlayService.isRunning())
            startService(new Intent(this,OverlayService.class).setAction(OverlayService.REFRESH));
        Toast.makeText(this,added+" sound(s) added",Toast.LENGTH_SHORT).show();
    }
    private boolean importClip(Uri uri){
        try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        catch(SecurityException ignored){}
        String name="Custom sound";
        try(Cursor c=getContentResolver().query(uri,
            new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null && c.moveToFirst())name=c.getString(0);
        }catch(Exception ignored){}
        return ClipStore.add(this,name,uri);
    }
}
