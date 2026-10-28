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
    private static final int DARK=Color.rgb(16,17,23);
    private static final int PANEL=Color.rgb(27,29,38);
    private static final int TILE=Color.rgb(47,48,61);
    private static final int ORANGE=Color.rgb(255,124,48);
    private static final int LIGHT=Color.rgb(243,244,248);
    private static final int MUTED=Color.rgb(175,180,190);

    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable rounded(int color,int radius,boolean border){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if(border)g.setStroke(dp(1),ORANGE);
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
        b.setBackground(rounded(TILE,8,false));
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
            .setSmallIcon(android.R.drawable.ic_media_play)
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
            dp((expanded?310:154)*scale),
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
        TextView pill=text("GILL   |   SOUNDS",13,LIGHT);
        pill.setTypeface(null,1);
        pill.setPadding(dp(10),dp(12),dp(10),dp(12));
        pill.setBackground(rounded(DARK,12,true));
        pill.setOnTouchListener(dragTouch(()->{
            expanded=true;showFloating();
        }));
        floating=pill;
    }

    private void makeExpanded(){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8),dp(9),dp(8),dp(9));
        box.setBackground(rounded(DARK,14,true));
        box.setElevation(dp(9));
        floating=box;

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(header,new LinearLayout.LayoutParams(-1,dp(35)));
        TextView grip=text("GILL / SOUNDBOARD",13,ORANGE);
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
        TextView spamToggle=button(spamMode?"HOLD SPAM: ON":"HOLD SPAM: OFF");
        spamToggle.setTextColor(spamMode?ORANGE:LIGHT);
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

        ScrollView scroller=new ScrollView(this);
        scroller.setFillViewport(false);
        box.addView(scroller,new LinearLayout.LayoutParams(-1,dp(235)));
        LinearLayout clips=new LinearLayout(this);
        clips.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(clips);
        List<ClipStore.Clip> library=ClipStore.get(this);
        for(int i=0;i<library.size();i+=2){
            LinearLayout row=new LinearLayout(this);
            LinearLayout.LayoutParams rowP=new LinearLayout.LayoutParams(-1,dp(43));
            rowP.bottomMargin=dp(5);
            clips.addView(row,rowP);
            for(int k=i;k<Math.min(library.size(),i+2);k++){
                ClipStore.Clip clip=library.get(k);
                TextView b=button(clip.name);
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
        TextView stop=button("STOP SOUND");
        stop.setTextColor(ORANGE);
        box.addView(stop,new LinearLayout.LayoutParams(-1,dp(36)));
        stop.setOnClickListener(v->AudioEngine.stop());
        gap(box,6);
        TextView hint=text(ClipStore.locked(this)?"POSITION LOCKED":"HOLD TITLE TO MOVE",10,MUTED);
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
