package com.qianwang.privatewealth;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Shared notebook materials; deterministic strokes never move between redraws. */
public final class SketchStyle {
    private static Typeface handwriting, numerals;
    public final boolean blue, night;
    public final int paper, surface, ink, muted, line, accent, pale;
    public SketchStyle(Context context) {
        blue=context.getSharedPreferences("appearance",0).getInt("sketch",0)==1;
        night=(context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        paper=Color.parseColor(night?"#202523":blue?"#F4F7FA":"#FCFAF4");
        surface=Color.parseColor(night?"#282F2C":blue?"#FCFDFF":"#FFFEF9");
        ink=Color.parseColor(night?"#F4F0E5":blue?"#203C52":"#30332D");
        muted=Color.parseColor(night?"#BABFB5":blue?"#526777":"#666B5F");
        line=Color.parseColor(night?"#515D53":blue?"#B8C9D5":"#C5CAB8");
        accent=Color.parseColor(night?"#A4CBE6":blue?"#275D86":"#316A94");
        pale=Color.parseColor(night?"#354944":blue?"#DFEAF3":"#E4EDD9");
    }
    public static Typeface handwriting(Context c){if(handwriting==null)handwriting=Typeface.createFromAsset(c.getAssets(),"fonts/WenKai.ttf");return handwriting;}
    public static Typeface numerals(Context c){if(numerals==null)numerals=Typeface.createFromAsset(c.getAssets(),"fonts/PatrickHand.ttf");return numerals;}
    public int[] colors(){return night?new int[]{0xFF96BCDD,0xFFB6CCA2,0xFFE3B782,0xFFCAB2DC,0xFF8CC9C2,0xFFE6AEAA}:blue?new int[]{0xFF3679AA,0xFF588DA1,0xFF6B78A4,0xFF428584,0xFF5B8BBA,0xFF7C91A3}:new int[]{0xFF397EB5,0xFF809A53,0xFFC08B42,0xFF9873B1,0xFF428F89,0xFFBD7269};}
    public Drawable paper(float d){return new Surface(paper,line,d,false,true);}
    public Drawable box(int fill,int border,float d){return new Surface(fill,border,d,true,false);}
    public Drawable rule(float d){return new Surface(Color.TRANSPARENT,line,d,false,false);}
    public static Path frame(RectF r,float d){
        float k=Math.min(8*d,Math.min(r.width(),r.height())*.18f),wave=Math.min(d,Math.min(r.width(),r.height())*.05f),cx=Math.min(20*d,r.width()*.18f),cy=Math.min(8*d,r.height()*.18f);Path p=new Path();
        p.moveTo(r.left+k,r.top+wave);p.cubicTo(r.centerX()-cx,r.top-wave,r.centerX()+cx,r.top+wave,r.right-k,r.top);
        p.quadTo(r.right+wave,r.top,r.right,r.top+k);p.cubicTo(r.right-wave,r.centerY()-cy,r.right+wave,r.centerY()+cy,r.right,r.bottom-k);
        p.quadTo(r.right,r.bottom+wave,r.right-k,r.bottom);p.cubicTo(r.centerX()+cx,r.bottom-wave,r.centerX()-cx,r.bottom+wave,r.left+k,r.bottom);
        p.quadTo(r.left-wave,r.bottom,r.left,r.bottom-k);p.cubicTo(r.left+wave,r.centerY()+cy,r.left-wave,r.centerY()-cy,r.left,r.top+k);p.quadTo(r.left,r.top,r.left+k,r.top+wave);p.close();return p;
    }
    public static void pencilFill(Canvas c,Path path,int color,float d,int alpha){
        Paint p=new Paint(3);p.setColor(color);p.setAlpha(alpha);c.drawPath(path,p);
        c.save();c.clipPath(path);RectF bounds=new RectF();path.computeBounds(bounds,true);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.55f*d);p.setAlpha(Math.min(40,alpha/2+5));
        float h=bounds.height();int i=0;for(float x=bounds.left-h;x<bounds.right+h;x+=2.3f*d){float drift=(float)Math.sin(++i*2.37f)*2*d;Path mark=new Path();mark.moveTo(x,bounds.bottom);mark.cubicTo(x+h*.25f+drift,bounds.bottom-h*.3f,x+h*.7f-drift,bounds.top+h*.3f,x+h+drift,bounds.top);c.drawPath(mark,p);}c.restore();
    }
    private static final class Surface extends Drawable {
        final int fill,border;final float d;final boolean framed,grain;final Paint p=new Paint(3);
        Surface(int fill,int border,float d,boolean framed,boolean grain){this.fill=fill;this.border=border;this.d=d;this.framed=framed;this.grain=grain;}
        public void draw(Canvas c){Rect b=getBounds();p.setStyle(Paint.Style.FILL);p.setColor(fill);
            if(framed){float inset=Math.min(2*d,Math.min(b.width(),b.height())*.12f);RectF r=new RectF(b.left+inset,b.top+inset,b.right-inset,b.bottom-inset);Path path=frame(r,d);c.drawPath(path,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.05f*d);p.setColor(border);c.drawPath(path,p);}
            else if(grain){c.drawRect(b,p);p.setColor(border);p.setAlpha(30);for(int y=b.top;y<b.bottom;y+=(int)(13*d))for(int x=b.left+(y%17);x<b.right;x+=(int)(19*d))c.drawPoint(x,y,p);p.setAlpha(255);}
            else{p.setColor(border);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d);Path path=new Path();path.moveTo(b.left,b.centerY());path.cubicTo(b.width()*.3f,b.centerY()-d,b.width()*.7f,b.centerY()+d,b.right,b.centerY());c.drawPath(path,p);}
        }
        public void setAlpha(int a){} public void setColorFilter(ColorFilter f){} public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
