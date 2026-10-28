package com.gill.soundboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

/** Original, lightweight vector UI assets for the soundboard. No external icon libraries. */
final class BrandStyle {
    static final int BG=Color.rgb(13,12,23);
    static final int PANEL=Color.rgb(25,22,38);
    static final int CARD=Color.rgb(37,32,54);
    static final int ELEVATED=Color.rgb(46,39,67);
    static final int PURPLE=Color.rgb(166,99,255);
    static final int LAVENDER=Color.rgb(206,172,255);
    static final int WHITE=Color.rgb(245,241,255);
    static final int DIM=Color.rgb(171,166,190);
    static final int GREEN=Color.rgb(107,231,169);
    static final int BORDER=Color.rgb(69,57,94);
    private static final int[] CATEGORY={
        Color.rgb(207,135,255),Color.rgb(255,168,122),Color.rgb(169,142,255),
        Color.rgb(107,209,255),Color.rgb(148,237,205),Color.rgb(255,201,128),
        Color.rgb(243,151,183),Color.rgb(138,207,255),Color.rgb(181,145,245),
        Color.rgb(124,227,218),Color.rgb(238,176,112),Color.rgb(247,136,182),
        Color.rgb(176,172,205)
    };
    static int categoryColor(int index){
        return CATEGORY[Math.max(0,Math.min(index,CATEGORY.length-1))];
    }
    static int categoryOf(ClipStore.Clip clip){
        return clip.isPreset()?Math.min(11,clip.preset/8):12;
    }
    static int soundIconForCategory(int category){
        final int[] icons={0,1,2,3,4,5,1,3,2,4,6,5,6};
        return icons[Math.max(0,Math.min(category,icons.length-1))];
    }
    static GradientDrawable bg(int color,int radiusPx,boolean outline){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        if(outline)d.setStroke(Math.max(1,radiusPx/12),BORDER);
        return d;
    }
    static GradientDrawable accent(int radiusPx){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(190,119,255),Color.rgb(127,77,244)});
        d.setCornerRadius(radiusPx);
        return d;
    }
    static Drawable icon(int kind,int pixels,int color){
        Icon d=new Icon(kind,color);
        d.setBounds(0,0,pixels,pixels);
        return d;
    }
    static View graphic(Context c,int kind,int pixels,int color){
        return new GraphicView(c,kind,color);
    }
    private static final class GraphicView extends View{
        private final Icon icon;
        GraphicView(Context c,int type,int color){super(c);icon=new Icon(type,color);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            icon.setBounds(0,0,getWidth(),getHeight());
            icon.draw(canvas);
        }
    }
    static class Icon extends Drawable {
        private final int kind;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(int kind,int color){
            this.kind=kind;
            p.setColor(color);
            p.setStrokeWidth(2.2f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override public void draw(Canvas c){
            Rect b=getBounds();
            int save=c.save();
            c.translate(b.left,b.top);
            c.scale(b.width()/24f,b.height()/24f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.1f);
            switch(kind){
                case 0: // bass / equalizer
                    p.setStyle(Paint.Style.FILL);
                    c.drawRoundRect(3,8,6.3f,16,1.5f,1.5f,p);
                    c.drawRoundRect(9,4,12.3f,20,1.5f,1.5f,p);
                    c.drawRoundRect(15,6.5f,18.3f,17.5f,1.5f,1.5f,p);
                    c.drawRoundRect(21,10,23,14,1,1,p);
                    break;
                case 1: // funny - smile
                    c.drawCircle(12,12,8.8f,p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(8.7f,10.2f,1.1f,p);
                    c.drawCircle(15.3f,10.2f,1.1f,p);
                    p.setStyle(Paint.Style.STROKE);
                    c.drawArc(new RectF(7.5f,9f,16.5f,17.5f),15,150,false,p);
                    break;
                case 2: // dramatic lightning
                    p.setStyle(Paint.Style.FILL);
                    Path bolt=new Path();
                    bolt.moveTo(13,1);bolt.lineTo(5.5f,13);bolt.lineTo(11,13);
                    bolt.lineTo(9,23);bolt.lineTo(19.4f,9.7f);
                    bolt.lineTo(13.2f,9.7f);bolt.close();c.drawPath(bolt,p);
                    break;
                case 3: // arcade controls
                    c.drawRoundRect(new RectF(2,7,22,19),5,5,p);
                    c.drawLine(6,13,12,13,p);
                    c.drawLine(9,10,9,16,p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(16,11.5f,1.5f,p);c.drawCircle(19,14.5f,1.5f,p);
                    break;
                case 4: // effects sparkle
                    c.drawCircle(12,12,3.2f,p);
                    c.drawLine(12,1.5f,12,6,p);c.drawLine(12,18,12,22.5f,p);
                    c.drawLine(1.5f,12,6,12,p);c.drawLine(18,12,22.5f,12,p);
                    c.drawLine(4.5f,4.5f,7.5f,7.5f,p);
                    c.drawLine(16.5f,16.5f,19.5f,19.5f,p);
                    break;
                case 5: // alert triangle
                    Path warning=new Path();
                    warning.moveTo(12,3);warning.lineTo(22,20);
                    warning.lineTo(2,20);warning.close();
                    c.drawPath(warning,p);
                    c.drawLine(12,9,12,14.5f,p);
                    p.setStyle(Paint.Style.FILL);c.drawCircle(12,17.5f,1,p);
                    break;
                case 6: // custom clip / music
                    c.drawLine(9,5,9,17,p);c.drawLine(9,5,19,3,p);
                    c.drawLine(19,3,19,15,p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawOval(new RectF(3.1f,15.5f,10.5f,20.6f),p);
                    c.drawOval(new RectF(13.1f,13.5f,20.5f,18.6f),p);
                    break;
                case 7: // play
                    p.setStyle(Paint.Style.FILL);
                    Path triangle=new Path();
                    triangle.moveTo(7,3.5f);triangle.lineTo(21,12);
                    triangle.lineTo(7,20.5f);triangle.close();c.drawPath(triangle,p);
                    break;
                case 8: // stop
                    p.setStyle(Paint.Style.FILL);
                    c.drawRoundRect(5,5,19,19,3,3,p);break;
                case 9: // lock
                    c.drawRoundRect(new RectF(5,10,19,21),2,2,p);
                    c.drawArc(new RectF(8,3,16,16),180,180,false,p);break;
                case 10: // settings sliders
                    for(int i=0;i<3;i++){
                        float yy=6+i*6;
                        c.drawLine(3,yy,21,yy,p);
                        p.setStyle(Paint.Style.FILL);
                        c.drawCircle(i==1?15:9,yy,2.5f,p);
                        p.setStyle(Paint.Style.STROKE);
                    }break;
                case 11: // repeat/infinity
                    c.drawOval(new RectF(2.5f,7.5f,13,16.5f),p);
                    c.drawOval(new RectF(11,7.5f,21.5f,16.5f),p);
                    c.drawLine(9,10,15,14,p);
                    c.drawLine(9,14,15,10,p);
                    break;
                default:
                    c.drawCircle(12,12,9,p);break;
            }
            c.restoreToCount(save);
        }
        @Override public void setAlpha(int alpha){p.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
