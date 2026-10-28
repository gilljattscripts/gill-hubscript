package com.gill.soundboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Small Type-to-Speak dialog you can invoke from the floating overlay in Roblox.
 *  Audio uses the system's TTS media output, not a virtual microphone driver.
 */
public final class SpeechActivity extends Activity {
    private LinearLayout root;
    private EditText input;
    private Spinner voices;
    private TextView notice;
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String t,int size,int color,boolean bold){
        TextView v=new TextView(this);
        v.setText(t);v.setTextSize(size);v.setTextColor(color);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    private TextView button(String t,boolean bright){
        TextView b=label(t,13,bright?BrandStyle.BG:BrandStyle.WHITE,true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(bright?BrandStyle.accent(dp(11)):
            BrandStyle.bg(BrandStyle.CARD,dp(11),true));
        b.setClickable(true);
        return b;
    }
    private void space(int h){root.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        SpeechEngine.prepare(this);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackground(BrandStyle.bg(BrandStyle.BG,dp(17),true));
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(20),dp(20),dp(20));
        scroll.addView(root);
        setContentView(scroll);
        getWindow().setLayout(
            Math.min(getResources().getDisplayMetrics().widthPixels-dp(28),dp(410)),-2);
        root.addView(label("GILL  /  TYPE TO SPEAK",18,BrandStyle.WHITE,true));
        space(6);
        root.addView(label("Choose a voice and send typed speech to Android audio.",
            12,BrandStyle.DIM,false));
        space(15);
        input=new EditText(this);
        input.setTextColor(BrandStyle.WHITE);
        input.setHintTextColor(BrandStyle.DIM);
        input.setHint("Type something funny...");
        input.setTextSize(14);
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setMinLines(2);
        input.setPadding(dp(12),dp(9),dp(12),dp(9));
        input.setBackground(BrandStyle.bg(BrandStyle.CARD,dp(11),true));
        root.addView(input,new LinearLayout.LayoutParams(-1,-2));
        space(12);
        root.addView(label("VOICE",11,BrandStyle.LAVENDER,true));
        space(5);
        voices=new Spinner(this);
        voices.setBackground(BrandStyle.bg(BrandStyle.CARD,dp(10),true));
        root.addView(voices,new LinearLayout.LayoutParams(-1,dp(48)));
        updateVoices();
        space(9);
        TextView save=button("+ SAVE INSTALLED VOICE AS CUSTOM",false);
        root.addView(save,new LinearLayout.LayoutParams(-1,dp(41)));
        save.setOnClickListener(v->saveVoice());
        space(9);
        TextView refresh=button("REFRESH AVAILABLE VOICES",false);
        root.addView(refresh,new LinearLayout.LayoutParams(-1,dp(39)));
        refresh.setOnClickListener(v->updateVoices());
        space(14);
        TextView speak=button("SPEAK & RETURN TO GAME",true);
        root.addView(speak,new LinearLayout.LayoutParams(-1,dp(49)));
        speak.setOnClickListener(v->{
            String phrase=input.getText().toString().trim();
            if(phrase.isEmpty()){
                Toast.makeText(this,"Type something first",Toast.LENGTH_SHORT).show();return;
            }
            if(phrase.length()>350){
                Toast.makeText(this,"Use 350 characters or fewer",Toast.LENGTH_SHORT).show();return;
            }
            String selected=(String)voices.getSelectedItem();
            if(selected!=null)SpeechEngine.select(this,selected);
            if(!SpeechEngine.speak(this,phrase,ClipStore.chaos(this))){
                Toast.makeText(this,"Speech engine loading. Try again.",Toast.LENGTH_LONG).show();
                return;
            }
            finish();
        });
        space(8);
        TextView stop=button("STOP SPEECH",false);
        root.addView(stop,new LinearLayout.LayoutParams(-1,dp(40)));
        stop.setOnClickListener(v->SpeechEngine.stop());
        space(12);
        notice=label("Male/Female styles adjust pitch. Natural voices depend on the installed Android speech engine; Chaos applies a robot-like tone, not microphone injection.",11,BrandStyle.DIM,false);
        root.addView(notice);
        input.requestFocus();
    }
    private void updateVoices(){
        List<String> available=SpeechEngine.choices(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_item,available){
            @Override public android.view.View getView(int position,android.view.View convert,
                      android.view.ViewGroup parent){
                TextView v=(TextView)super.getView(position,convert,parent);
                v.setTextColor(BrandStyle.WHITE);
                v.setTextSize(12);
                v.setPadding(dp(13),dp(9),dp(8),dp(9));
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voices.setAdapter(adapter);
        String stored=SpeechEngine.selected(this);
        int index=available.indexOf(stored);
        if(index>=0)voices.setSelection(index);
    }
    private void saveVoice(){
        String selected=(String)voices.getSelectedItem();
        if(selected==null||!(selected.startsWith("SYSTEM  /  ")||
                             selected.startsWith("CUSTOM  /  "))){
            Toast.makeText(this,"Choose an installed SYSTEM voice first",Toast.LENGTH_SHORT).show();
            return;
        }
        EditText field=new EditText(this);
        field.setSingleLine(true);
        field.setHint("Custom voice name");
        new AlertDialog.Builder(this)
            .setTitle("Save custom voice")
            .setMessage("Give this installed voice a name. It will appear in your voice picker.")
            .setView(field)
            .setPositiveButton("SAVE",(dialog,which)->{
                if(SpeechEngine.saveCustom(this,field.getText().toString(),selected)){
                    updateVoices();
                    Toast.makeText(this,"Custom voice saved",Toast.LENGTH_SHORT).show();
                }else{
                    Toast.makeText(this,"Could not save voice",Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("CANCEL",null)
            .show();
    }
}
