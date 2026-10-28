package com.gill.soundboard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** On-demand meme search. Only actual media URLs retrieved from Myinstants play.
 *  Search requires an internet connection and may fail when the website changes.
 */
public final class MemeSearchActivity extends Activity {
    private static final String SITE="https://www.myinstants.com";
    private static final String[] QUICK={
        "I hate this game","man screaming","rage scream",
        "gen z meme","brainrot","hindi meme","arey bhai",
        "moye moye","vine boom","bruh","fart","goofy laugh"
    };
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private EditText queryInput;
    private LinearLayout results;
    private TextView status;
    private TextView loopButton;
    private int requestNumber=0;
    private int dp(float amount){return Math.round(amount*getResources().getDisplayMetrics().density);}
    private TextView label(String s,int size,int color,boolean strong){
        TextView t=new TextView(this);
        t.setText(s);t.setTextSize(size);t.setTextColor(color);
        if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private TextView button(String s,boolean primary){
        TextView b=label(s,12,primary?BrandStyle.BG:BrandStyle.WHITE,true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10),dp(9),dp(10),dp(9));
        b.setBackground(primary?BrandStyle.accent(dp(9)):
            BrandStyle.bg(BrandStyle.CARD,dp(9),true));
        return b;
    }
    private void gap(LinearLayout group,int height){
        group.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(BrandStyle.BG);
        getWindow().setNavigationBarColor(BrandStyle.BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(BrandStyle.BG);
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(17),dp(24),dp(17),dp(25));
        scroll.addView(page);
        setContentView(scroll);
        page.addView(label("GILL  /  REAL MEMES",22,BrandStyle.WHITE,true));
        gap(page,6);
        page.addView(label("Live sound search. No fake clips or AI sound effects. Internet needed.",
            12,BrandStyle.DIM,false));
        gap(page,17);

        LinearLayout searchBar=new LinearLayout(this);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(searchBar,new LinearLayout.LayoutParams(-1,dp(48)));
        queryInput=new EditText(this);
        queryInput.setSingleLine(true);
        queryInput.setTextSize(14);
        queryInput.setTextColor(BrandStyle.WHITE);
        queryInput.setHintTextColor(BrandStyle.DIM);
        queryInput.setHint("Search any meme...");
        queryInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        queryInput.setBackground(BrandStyle.bg(BrandStyle.CARD,dp(11),true));
        queryInput.setPadding(dp(12),0,dp(9),0);
        searchBar.addView(queryInput,new LinearLayout.LayoutParams(0,-1,1));
        TextView go=button("SEARCH",true);
        LinearLayout.LayoutParams goParams=new LinearLayout.LayoutParams(dp(87),-1);
        goParams.leftMargin=dp(8);
        searchBar.addView(go,goParams);
        go.setOnClickListener(v->search(queryInput.getText().toString()));
        queryInput.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH){search(queryInput.getText().toString());return true;}
            return false;
        });

        gap(page,11);
        HorizontalScrollView options=new HorizontalScrollView(this);
        options.setHorizontalScrollBarEnabled(false);
        page.addView(options);
        LinearLayout chips=new LinearLayout(this);
        options.addView(chips);
        for(String term:QUICK){
            TextView chip=button(term,false);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(37));
            p.rightMargin=dp(7);
            chips.addView(chip,p);
            chip.setOnClickListener(v->{queryInput.setText(term);search(term);});
        }
        gap(page,13);
        LinearLayout toolbar=new LinearLayout(this);
        page.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(42)));
        TextView fav=button("★ FAVORITES",false);
        toolbar.addView(fav,new LinearLayout.LayoutParams(0,-1,1));
        fav.setOnClickListener(v->render(OnlineFavorites.all(this),true));
        loopButton=button(ClipStore.loop(this)?"LOOP  /  ON":"LOOP  /  OFF",false);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
        lp.leftMargin=dp(7);
        toolbar.addView(loopButton,lp);
        loopButton.setOnClickListener(v->{
            ClipStore.loop(this,!ClipStore.loop(this));
            AudioEngine.setLoop(this,ClipStore.loop(this));
            loopButton.setText(ClipStore.loop(this)?"LOOP  /  ON":"LOOP  /  OFF");
        });
        TextView stop=button("STOP",false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(72),-1);
        sp.leftMargin=dp(7);
        toolbar.addView(stop,sp);
        stop.setOnClickListener(v->AudioEngine.stop());
        gap(page,10);

        LinearLayout external=new LinearLayout(this);
        page.addView(external,new LinearLayout.LayoutParams(-1,dp(39)));
        TextView site=button("OPEN MYINSTANTS",false);
        external.addView(site,new LinearLayout.LayoutParams(0,-1,1));
        site.setOnClickListener(v->open(SITE+"/en/search/?name="+
            Uri.encode(queryInput.getText().toString().isEmpty()?"meme":queryInput.getText().toString())));
        TextView youtube=button("YOUTUBE SEARCH",false);
        LinearLayout.LayoutParams yp=new LinearLayout.LayoutParams(0,-1,1);
        yp.leftMargin=dp(7);external.addView(youtube,yp);
        youtube.setOnClickListener(v->open("https://www.youtube.com/results?search_query="+
            Uri.encode(queryInput.getText().toString()+" meme sound")));
        gap(page,9);
        page.addView(label("YouTube opens separately; its videos cannot be injected directly into a Roblox microphone.",
            10,BrandStyle.DIM,false));
        gap(page,13);

        status=label("Search Myinstants or open your favorites.",12,BrandStyle.DIM,false);
        page.addView(status);
        gap(page,10);
        results=new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        page.addView(results);
        String preset=getIntent().getStringExtra("query");
        if(preset!=null&&!preset.isEmpty()){queryInput.setText(preset);search(preset);}
        else render(OnlineFavorites.all(this),true);
    }
    private void open(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception ex){Toast.makeText(this,"No browser available",Toast.LENGTH_SHORT).show();}
    }
    private static String fromEntities(String raw){
        return raw.replace("&amp;","&").replace("&quot;","\"")
            .replace("&#39;","'").replace("&lt;","<").replace("&gt;",">");
    }
    private static String titleFor(String url){
        try{
            String path=Uri.parse(url).getLastPathSegment();
            if(path==null)return "Meme sound";
            int dot=path.lastIndexOf('.');
            if(dot>0)path=path.substring(0,dot);
            path=java.net.URLDecoder.decode(path,"UTF-8");
            path=path.replace('_',' ').replace('-',' ').replaceAll("\\s+"," ").trim();
            return path.isEmpty()?"Meme sound":path;
        }catch(Exception ex){return "Meme sound";}
    }
    private List<OnlineFavorites.Meme> parse(String html){
        ArrayList<OnlineFavorites.Meme> sounds=new ArrayList<>();
        HashSet<String> seen=new HashSet<>();
        String source=fromEntities(html.replace("\\/","/"));
        Pattern expression=Pattern.compile(
            "(?:https?:)?//(?:www\\.)?myinstants\\.com/media/sounds/[A-Za-z0-9_%.\\-+/]+(?:mp3|wav|ogg)"+
            "|(?<![A-Za-z0-9./:])/media/sounds/[A-Za-z0-9_%.\\-+/]+(?:mp3|wav|ogg)",Pattern.CASE_INSENSITIVE);
        Matcher matcher=expression.matcher(source);
        while(matcher.find()&&sounds.size()<65){
            String path=matcher.group();
            String url=path.startsWith("//")?"https:"+path:
                path.startsWith("/")?SITE+path:path;
            url=url.replace("https://myinstants.com/","https://www.myinstants.com/");
            if(!OnlineFavorites.valid(url)||!seen.add(url))continue;
            String page=SITE;
            int around=Math.max(0,matcher.start()-1700);
            String local=source.substring(around,matcher.start());
            Pattern link=Pattern.compile("/(?:en/)?instant/[a-zA-Z0-9._%/-]+",
                Pattern.CASE_INSENSITIVE);
            Matcher lm=link.matcher(local);
            while(lm.find()){
                String href=lm.group(1);
                page=href.startsWith("http")?href:SITE+href;
            }
            sounds.add(new OnlineFavorites.Meme(titleFor(url),url,page));
        }
        return sounds;
    }
    private void search(String requested){
        String term=requested.trim();
        if(term.isEmpty()){render(OnlineFavorites.all(this),true);return;}
        if(term.length()>90){Toast.makeText(this,"Search must be under 90 characters",Toast.LENGTH_SHORT).show();return;}
        final int current=++requestNumber;
        status.setText("SEARCHING MYINSTANTS  /  "+term);
        results.removeAllViews();
        worker.execute(()->{
            List<OnlineFavorites.Meme> sounds=null;
            String error=null;
            try{
                String url=SITE+"/en/search/?name="+URLEncoder.encode(term,"UTF-8");
                HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
                connection.setConnectTimeout(9000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
                connection.setRequestProperty("Accept","text/html,application/xhtml+xml");
                try{
                    if(connection.getResponseCode()!=200)throw new Exception("Website unavailable");
                    ByteArrayOutputStream output=new ByteArrayOutputStream();
                    try(InputStream stream=connection.getInputStream()){
                        byte[] buf=new byte[8192];int n;
                        while((n=stream.read(buf))!=-1){
                            output.write(buf,0,n);
                            if(output.size()>2*1024*1024)break;
                        }
                    }
                    sounds=parse(output.toString("UTF-8"));
                }finally{connection.disconnect();}
            }catch(Exception ex){error="Search unavailable. Try OPEN MYINSTANTS or search again.";}
            final List<OnlineFavorites.Meme> ready=sounds;
            final String message=error;
            main.post(()->{
                if(current!=requestNumber||isFinishing())return;
                if(message!=null){status.setText(message);return;}
                if(ready==null||ready.isEmpty()){
                    status.setText("No playable MP3 addresses found. Open Myinstants for this search.");
                    return;
                }
                render(ready,false);
            });
        });
    }
    private void render(List<OnlineFavorites.Meme> sounds,boolean favorites){
        results.removeAllViews();
        status.setText((favorites?"SAVED FAVORITES":"REAL ONLINE RESULTS")+"  /  "+sounds.size()+" SOUNDS");
        if(sounds.isEmpty()){
            results.addView(label(favorites?
                "No favorites yet. Search a meme, tap ★ SAVE, then use it in your floating panel.":
                "No playable results. Try the website search button.",12,BrandStyle.DIM,false));
            return;
        }
        for(OnlineFavorites.Meme meme:sounds){
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12),dp(12),dp(12),dp(10));
            card.setBackground(BrandStyle.bg(BrandStyle.CARD,dp(13),true));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
            cp.bottomMargin=dp(9);
            results.addView(card,cp);
            TextView heading=label(meme.name,13,BrandStyle.WHITE,true);
            card.addView(heading);
            gap(card,9);
            LinearLayout buttons=new LinearLayout(this);
            card.addView(buttons,new LinearLayout.LayoutParams(-1,dp(39)));
            TextView play=button("▶ PLAY",true);
            buttons.addView(play,new LinearLayout.LayoutParams(0,-1,1));
            play.setOnClickListener(v->{
                AudioEngine.play(this,new ClipStore.Clip(-1,meme.name,meme.url));
                Toast.makeText(this,ClipStore.loop(this)?"Looping clip":"Loading audio...",Toast.LENGTH_SHORT).show();
            });
            TextView save=button(OnlineFavorites.has(this,meme.url)?"★ SAVED":"☆ SAVE",false);
            LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,-1,1);
            fp.leftMargin=dp(7);
            buttons.addView(save,fp);
            save.setOnClickListener(v->{
                OnlineFavorites.toggle(this,meme);
                save.setText(OnlineFavorites.has(this,meme.url)?"★ SAVED":"☆ SAVE");
                try{
                    startService(new Intent(this,OverlayService.class).setAction(OverlayService.REFRESH));
                }catch(Exception ignored){}
            });
            TextView source=button("SOURCE",false);
            LinearLayout.LayoutParams srcP=new LinearLayout.LayoutParams(0,-1,1);
            srcP.leftMargin=dp(7);
            buttons.addView(source,srcP);
            source.setOnClickListener(v->open(meme.page));
        }
    }
    @Override protected void onDestroy(){
        requestNumber++;
        worker.shutdownNow();
        super.onDestroy();
    }
}
