package com.gill.soundboard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

public final class OverlayService extends Service {
    static final String START="com.gill.soundboard.START";
    static final String STOP="com.gill.soundboard.STOP";
    static final String REFRESH="com.gill.soundboard.REFRESH";
    private static final String CHANNEL="gill_soundboard_player";
    private static volatile boolean running=false;
    static boolean isRunning(){return running;}
    private WindowManager window;
    private WindowManager.LayoutParams params;
    private View floating;
    private boolean expanded=false;
    private boolean spamMode=false;
    private int spamIntervalMs=150;
    private final Handler spamHandler=new Handler(Looper.getMainLooper());
    private Runnable activeSpam;
    private static final int DARK=BrandStyle.BG;
    private static final int PANEL=BrandStyle.PANEL;
    private static final int TILE=BrandStyle.CARD;
    private static final int ORANGE=BrandStyle.PURPLE;
    private static final int LIGHT=BrandStyle.WHITE;
    private static final int MUTED=BrandStyle.DIM;

    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable rounded(int color,int radius,boolean border){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if(border)g.setStroke(dp(1),BrandStyle.BORDER);
        return g;
    }
    private TextView text(String s,int size,int color){
        TextView v=new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return v;
    }
    private TextView button(String s){
        TextView b=text(s,11,LIGHT);
        b.setPadding(dp(5),dp(7),dp(5),dp(7));
        b.setBackground(BrandStyle.bg(TILE,dp(9),true));
        b.setClickable(true);
        return b;
    }
    private void gap(LinearLayout parent,int h){
        parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));
    }
    @Override public void onCreate(){
        super.onCreate();
        window=(WindowManager)getSystemService(Context.WINDOW_SERVICE);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"GILL Soundboard",NotificationManager.IMPORTANCE_LOW));
        Intent app=new Intent(this,MainActivity.class);
        PendingIntent open=PendingIntent.getActivity(this,1,app,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,OverlayService.class).setAction(STOP);
        PendingIntent close=PendingIntent.getService(this,2,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.gill_notification)
            .setContentTitle("GILL Soundboard")
            .setContentText("Floating soundboard active")
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Stop",close)
            .setOngoing(true).build();
        startForeground(101,notification);
        running=true;
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && STOP.equals(intent.getAction())){
            stopSelf();return START_NOT_STICKY;
        }
        if(!Settings.canDrawOverlays(this)){
            stopSelf();return START_NOT_STICKY;
        }
        showFloating();
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent){return null;}

    private void showFloating(){
        stopSpam();
        if(window==null)return;
        if(floating!=null){
            try{window.removeView(floating);}catch(Exception ignored){}
            floating=null;
        }
        float scale=ClipStore.size(this);
        params=new WindowManager.LayoutParams(
            dp((expanded?324:154)*scale),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.START;
        params.x=ClipStore.x(this);
        params.y=ClipStore.y(this);
        if(expanded)makeExpanded();else makeMinimized();
        try{window.addView(floating,params);}
        catch(Exception e){stopSelf();}
    }

    private void makeMinimized(){
        TextView pill=text("GILL  /  SOUNDS",12,LIGHT);
        pill.setTypeface(null,1);
        pill.setPadding(dp(9),dp(12),dp(8),dp(12));
        pill.setBackground(BrandStyle.accent(dp(13)));
        pill.setTextColor(DARK);
        pill.setCompoundDrawables(BrandStyle.icon(0,dp(17),DARK),null,null,null);
        pill.setCompoundDrawablePadding(dp(8));
        pill.setOnTouchListener(dragTouch(()->{
            expanded=true;showFloating();
        }));
        floating=pill;
    }

    private void makeExpanded(){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10),dp(11),dp(10),dp(12));
        box.setBackground(BrandStyle.bg(DARK,dp(17),true));
        box.setElevation(dp(9));
        floating=box;

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(header,new LinearLayout.LayoutParams(-1,dp(42)));
        ImageView symbol=new ImageView(this);
        symbol.setImageResource(R.drawable.gill_logo);
        symbol.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams symbolParams=new LinearLayout.LayoutParams(dp(34),dp(34));
        symbolParams.rightMargin=dp(8);
        header.addView(symbol,symbolParams);
        TextView grip=text("GILL / SOUND",13,BrandStyle.LAVENDER);
        grip.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        grip.setTypeface(null,1);
        grip.setOnTouchListener(dragTouch(null));
        header.addView(grip,new LinearLayout.LayoutParams(0,-1,1));
        TextView minimize=button("−");
        header.addView(minimize,new LinearLayout.LayoutParams(dp(35),-1));
        minimize.setOnClickListener(v->{expanded=false;showFloating();});
        TextView close=button("×");
        LinearLayout.LayoutParams closeParams=new LinearLayout.LayoutParams(dp(35),-1);
        closeParams.leftMargin=dp(5);
        header.addView(close,closeParams);
        close.setOnClickListener(v->stopSelf());
        gap(box,8);

        LinearLayout tools=new LinearLayout(this);
        box.addView(tools,new LinearLayout.LayoutParams(-1,dp(31)));
        String lockText=ClipStore.locked(this)?"UNLOCK":"LOCK";
        TextView lock=button(lockText);
        lock.setCompoundDrawables(BrandStyle.icon(9,dp(14),BrandStyle.LAVENDER),null,null,null);
        lock.setCompoundDrawablePadding(dp(4));
        tools.addView(lock,new LinearLayout.LayoutParams(0,-1,1));
        lock.setOnClickListener(v->{ClipStore.locked(this,!ClipStore.locked(this));showFloating();});
        TextView smaller=button("SIZE −");
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-1,1);
        sp.leftMargin=dp(5);tools.addView(smaller,sp);
        smaller.setOnClickListener(v->resize(-.1f));
        TextView bigger=button("SIZE +");
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);
        bp.leftMargin=dp(5);tools.addView(bigger,bp);
        bigger.setOnClickListener(v->resize(.1f));
        gap(box,7);
        LinearLayout spamRow=new LinearLayout(this);
        box.addView(spamRow,new LinearLayout.LayoutParams(-1,dp(33)));
        TextView spamToggle=button(spamMode?"SPAM  /  ON":"SPAM  /  OFF");
        spamToggle.setTextColor(spamMode?DARK:LIGHT);
        spamToggle.setBackground(spamMode?BrandStyle.accent(dp(9)):BrandStyle.bg(TILE,dp(9),true));
        spamToggle.setCompoundDrawables(BrandStyle.icon(11,dp(16),spamMode?DARK:BrandStyle.LAVENDER),null,null,null);
        spamToggle.setCompoundDrawablePadding(dp(6));
        spamRow.addView(spamToggle,new LinearLayout.LayoutParams(0,-1,2));
        spamToggle.setOnClickListener(v->{stopSpam();spamMode=!spamMode;showFloating();});
        TextView spamRate=button("RATE "+spamIntervalMs+"ms");
        LinearLayout.LayoutParams rateParams=new LinearLayout.LayoutParams(0,-1,1);
        rateParams.leftMargin=dp(5);
        spamRow.addView(spamRate,rateParams);
        spamRate.setOnClickListener(v->{
            spamIntervalMs=(spamIntervalMs==150)?100:(spamIntervalMs==100)?250:150;
            showFloating();
        });
        gap(box,7);
        TextView typeToSpeak=button("TYPE TO SPEAK   /   VOICES");
        typeToSpeak.setTextColor(BrandStyle.LAVENDER);
        typeToSpeak.setCompoundDrawables(BrandStyle.icon(6,dp(17),BrandStyle.LAVENDER),null,null,null);
        typeToSpeak.setCompoundDrawablePadding(dp(7));
        box.addView(typeToSpeak,new LinearLayout.LayoutParams(-1,dp(38)));
        typeToSpeak.setOnClickListener(v->{
            try{
                Intent speech=new Intent(this,SpeechActivity.class);
                speech.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(speech);
            }catch(Exception ignored){}
        });
        gap(box,7);
        TextView chaos=button(ClipStore.chaos(this)?"DESTROYED MIC FX   /   ON":"DESTROYED MIC FX   /   OFF");
        chaos.setTextColor(ClipStore.chaos(this)?BrandStyle.GREEN:BrandStyle.LAVENDER);
        chaos.setCompoundDrawables(BrandStyle.icon(2,dp(16),
            ClipStore.chaos(this)?BrandStyle.GREEN:BrandStyle.LAVENDER),null,null,null);
        chaos.setCompoundDrawablePadding(dp(7));
        box.addView(chaos,new LinearLayout.LayoutParams(-1,dp(36)));
        chaos.setOnClickListener(v->{
            ClipStore.chaos(this,!ClipStore.chaos(this));
            showFloating();
        });
        gap(box,7);

        ScrollView scroller=new ScrollView(this);
        scroller.setFillViewport(false);
        box.addView(scroller,new LinearLayout.LayoutParams(-1,dp(270)));
        LinearLayout clips=new LinearLayout(this);
        clips.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(clips);
        List<ClipStore.Clip> library=ClipStore.get(this);
        final String[] categories={"LOUD / BASS","FUNNY / CARTOON","DRAMA / MEMES","ARCADE / RETRO",
            "EFFECTS / SPACE","ALERTS / EXTRA","MEME REACTIONS","GAMING / FAILS",
            "HORROR / HAUNTED","GLITCH / TECH","MUSIC / STINGS","CHAOS / TROLL",
            "VOICE REACTIONS","KEYBOARD / DESK","VOICE / FUNNY","CHAOS / EXTRA"};
        for(int i=0;i<library.size();i+=2){
            if(i<ClipStore.PRESETS.length && i%8==0){
                TextView section=text(categories[i/8],11,BrandStyle.LAVENDER);
                section.setTypeface(null,1);
                section.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
                section.setCompoundDrawables(BrandStyle.icon(BrandStyle.soundIconForCategory(i/8),dp(17),
                    BrandStyle.categoryColor(i/8)),null,null,null);
                section.setCompoundDrawablePadding(dp(9));
                LinearLayout.LayoutParams sectionParams=new LinearLayout.LayoutParams(-1,dp(29));
                clips.addView(section,sectionParams);
            }
            LinearLayout row=new LinearLayout(this);
            LinearLayout.LayoutParams rowP=new LinearLayout.LayoutParams(-1,dp(48));
            rowP.bottomMargin=dp(5);
            clips.addView(row,rowP);
            for(int k=i;k<Math.min(library.size(),i+2);k++){
                ClipStore.Clip clip=library.get(k);
                TextView b=button(clip.name);
                final int group=BrandStyle.categoryOf(clip);
                b.setTextSize(10);
                b.setPadding(dp(7),dp(6),dp(5),dp(6));
                b.setCompoundDrawables(BrandStyle.icon(BrandStyle.soundIconForCategory(group),dp(16),
                    BrandStyle.categoryColor(group)),null,null,null);
                b.setCompoundDrawablePadding(dp(5));
                LinearLayout.LayoutParams tile=new LinearLayout.LayoutParams(0,-1,1);
                if(k%2==1)tile.leftMargin=dp(5);
                row.addView(b,tile);
                bindSoundButton(b,clip);
            }
            if(i+1>=library.size()){
                View filler=new View(this);
                row.addView(filler,new LinearLayout.LayoutParams(0,-1,1));
            }
        }
        gap(box,8);
        TextView stop=button("STOP ALL SOUNDS");
        stop.setBackground(BrandStyle.bg(PANEL,dp(10),true));
        stop.setTextColor(BrandStyle.LAVENDER);
        stop.setCompoundDrawables(BrandStyle.icon(8,dp(16),BrandStyle.LAVENDER),null,null,null);
        stop.setCompoundDrawablePadding(dp(6));
        box.addView(stop,new LinearLayout.LayoutParams(-1,dp(36)));
        stop.setOnClickListener(v->AudioEngine.stop());
        gap(box,6);
        TextView hint=text(ClipStore.locked(this)?"POSITION LOCKED":"DRAG TITLE TO MOVE  /  HOLD SOUND TO REPEAT",9,MUTED);
        box.addView(hint,new LinearLayout.LayoutParams(-1,dp(20)));
    }

    private void stopSpam(){
        if(activeSpam!=null){
            spamHandler.removeCallbacks(activeSpam);
            activeSpam=null;
        }
    }
    private void bindSoundButton(TextView button,ClipStore.Clip clip){
        button.setOnClickListener(v->AudioEngine.play(this,clip));
        button.setOnTouchListener((view,event)->{
            if(!spamMode)return false;
            final int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                stopSpam();
                AudioEngine.play(this,clip);
                final Runnable[] task={null};
                task[0]=()->{
                    if(activeSpam!=task[0] || !spamMode)return;
                    AudioEngine.play(this,clip);
                    spamHandler.postDelayed(task[0],spamIntervalMs);
                };
                activeSpam=task[0];
                spamHandler.postDelayed(activeSpam,spamIntervalMs);
                return true;
            }
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){
                stopSpam();
                return true;
            }
            return true;
        });
    }
    private void resize(float delta){
        ClipStore.size(this,ClipStore.size(this)+delta);
        showFloating();
    }
    private View.OnTouchListener dragTouch(Runnable tap){
        final float[] start=new float[2];
        final int[] base=new int[2];
        final boolean[] dragged={false};
        return (view,event)->{
            int type=event.getActionMasked();
            if(type==MotionEvent.ACTION_DOWN){
                start[0]=event.getRawX();start[1]=event.getRawY();
                base[0]=params.x;base[1]=params.y;
                dragged[0]=false;
                return true;
            }
            if(type==MotionEvent.ACTION_MOVE){
                float dx=event.getRawX()-start[0];
                float dy=event.getRawY()-start[1];
                if(Math.abs(dx)>dp(7)||Math.abs(dy)>dp(7))dragged[0]=true;
                if(dragged[0] && !ClipStore.locked(this)){
                    params.x=Math.max(0,Math.min(getResources().getDisplayMetrics().widthPixels-dp(65),base[0]+Math.round(dx)));
                    params.y=Math.max(0,Math.min(getResources().getDisplayMetrics().heightPixels-dp(65),base[1]+Math.round(dy)));
                    try{window.updateViewLayout(floating,params);}catch(Exception ignored){}
                }
                return true;
            }
            if(type==MotionEvent.ACTION_UP){
                if(dragged[0])ClipStore.position(this,params.x,params.y);
                else if(tap!=null)tap.run();
                return true;
            }
            return true;
        };
    }
    @Override public void onDestroy(){
        stopSpam();
        if(floating!=null && window!=null){
            try{window.removeView(floating);}catch(Exception ignored){}
            floating=null;
        }
        AudioEngine.stop();
        running=false;
        super.onDestroy();
    }
}
