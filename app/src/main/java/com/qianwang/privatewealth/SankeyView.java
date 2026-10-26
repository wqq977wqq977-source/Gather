package com.qianwang.privatewealth;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/** One conserved total-assets source, split by category or account. */
public final class SankeyView extends View {
    public interface Listener { void selected(int index); }
    private final AssetTree.Node data;
    private final DisplayMoney money;
    private final Typeface figures,hand;
    private final SketchStyle style;
    private final int ink,muted,accent;
    private final int[] colors;
    private final boolean hidden;
    private final Paint paint=new Paint(3);
    private final TextPaint text=new TextPaint(3);
    private final List<Branch> branches=new ArrayList<>();
    private final RectF source=new RectF();
    private float downX,downY;
    private boolean moved,touchClick;
    private int selection=-1;
    private Listener listener;
    private static final class Branch {
        final int index; final long value; final String name; final int color;
        final RectF box=new RectF(); final Path path=new Path();final Region hit=new Region();
        Branch(int i,long v,String n,int c){index=i;value=v;name=n;color=c;}
    }
    public SankeyView(Context c,AssetTree.Node data,DisplayMoney money,int categoryColor,Typeface figures,int[] colors,int ink,int muted,int line,int accent,int pale,boolean hidden){
        super(c);this.data=data;this.money=money;this.figures=figures;hand=SketchStyle.handwriting(c);style=new SketchStyle(c);this.colors=colors.clone();this.ink=ink;this.muted=muted;this.accent=accent;this.hidden=hidden;
        List<AssetTree.Node> nodes=AssetTree.visible(data);for(int i=0;i<nodes.size();i++){AssetTree.Node n=nodes.get(i);branches.add(new Branch(i,n.value,n.name,colors[(categoryColor>=0?categoryColor:AssetTree.categoryColor(n.key)>=0?AssetTree.categoryColor(n.key):i)%colors.length]));}
        setFocusable(true);setClickable(!hidden&&data.value>0);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);describe();
    }
    public void setListener(Listener listener){this.listener=listener;}
    public void setSelection(int index){selection=index>=0&&index<branches.size()?index:-1;describe();invalidate();}
    public int desiredHeight(){return hidden||data.value==0?dp(180):dp(250)+Math.max(0,branches.size()-1)*gap();}
    private int gap(){return Math.max(dp(58),(int)sp(12)*4);}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(resolveSize(dp(360),w),resolveSize(desiredHeight(),h));}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){layoutGraph();}
    private void layoutGraph(){
        if(hidden||data.value==0)return;
        float labelWidth=Math.min(getWidth()*.34f,Math.max(dp(96),sp(12)*8));
        float x1=getWidth()-labelWidth-dp(3),x0=Math.min(dp(106),getWidth()*.30f);
        float top=dp(28)+sp(12),bottom=dp(28)+sp(12),available=getHeight()-top-bottom;
        float budget=available-gap()*Math.max(0,branches.size()-1);
        source.set(dp(3),top+(available-budget)/2,x0,top+(available+budget)/2);
        double used=0;float destY=top;
        for(int i=0;i<branches.size();i++){
            Branch b=branches.get(i);double next=used+b.value;
            // Cumulative boundaries share endpoints exactly, including the final source edge.
            float sy=source.top+(float)(used/data.value*budget);
            float ey=i==branches.size()-1?source.bottom:source.top+(float)(next/data.value*budget);
            float h=ey-sy,cy=destY+h/2,boxH=Math.max(h,Math.max(dp(52),sp(12)*3+dp(10)));
            b.box.set(x1,cy-boxH/2,getWidth()-dp(3),cy+boxH/2);
            float dx=(x1-x0)*.45f;b.path.reset();b.path.moveTo(x0-dp(2),sy);
            b.path.cubicTo(x0+dx,sy,x1-dx,destY,x1+dp(2),destY);b.path.lineTo(x1+dp(2),destY+h);
            b.path.cubicTo(x1-dx,destY+h,x0+dx,ey,x0-dp(2),ey);b.path.close();b.hit.setPath(b.path,new Region(0,0,getWidth(),getHeight()));
            used=next;destY+=h+gap();
        }
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(hidden||data.value==0){label(c,hidden?"金额已隐藏":"暂无可绘制的资产",getWidth()/2f,getHeight()/2f,12,muted,false);return;}
        int selected=selection;
        for(Branch b:branches){boolean active=selected<0||selected==b.index;SketchStyle.pencilFill(c,b.path,b.color,density(),active?55:12);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.15f));paint.setColor(b.color);paint.setAlpha(active?220:40);c.drawPath(b.path,paint);}
        paint.setAlpha(255);
        drawBox(c,source,style.surface,ink);
        float cy=source.centerY();text.setTextSize(sp(14));text.setTypeface(hand);String title=TextUtils.ellipsize(data.name,text,source.width()-dp(12),TextUtils.TruncateAt.END).toString();label(c,title,source.centerX(),cy-sp(21),14,ink,false);
        fitted(c,money.compact(data.value),source.centerX(),cy+sp(9),source.width()-dp(12),24,ink,true);
        paint.setColor(ink);paint.setStrokeWidth(dp(1.2f));c.drawLine(source.left+dp(14),cy+sp(17),source.right-dp(12),cy+sp(16),paint);
        label(c,"100%",source.centerX(),cy+sp(39),14,muted,true);
        for(Branch b:branches){boolean active=selected==b.index;drawBox(c,b.box,active?style.pale:style.surface,active?accent:ink);
            text.setTypeface(hand);text.setTextSize(sp(12));String name=TextUtils.ellipsize(b.name,text,b.box.width()-dp(12),TextUtils.TruncateAt.END).toString();
            label(c,name,b.box.centerX(),b.box.centerY()-dp(4),12,ink,false);
            fitted(c,money.compact(b.value),b.box.centerX(),b.box.centerY()+sp(16),b.box.width()-dp(12),17,muted,true);
            paint.setColor(b.color);paint.setStrokeWidth(dp(1.8f));c.drawLine(b.box.left+dp(15),b.box.centerY()+sp(21),b.box.right-dp(13),b.box.centerY()+sp(20),paint);
        }
        if(isFocused()){paint.setColor(accent);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));c.drawRect(0,0,getWidth(),getHeight(),paint);}
    }
    private void drawBox(Canvas c,RectF r,int fill,int border){Path p=SketchStyle.frame(r,density());paint.setStyle(Paint.Style.FILL);paint.setColor(fill);c.drawPath(p,paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(border);paint.setStrokeWidth(dp(1.3f));c.drawPath(p,paint);}
    private void fitted(Canvas c,String value,float x,float y,float width,float size,int color,boolean number){text.setTypeface(number?figures:hand);text.setTextSize(sp(size));if(text.measureText(value)>width)size*=width/text.measureText(value);label(c,value,x,y,size,color,number);}
    private void label(Canvas c,String value,float x,float y,float size,int color,boolean number){text.setStyle(Paint.Style.FILL);text.setTypeface(number?figures:hand);text.setTextSize(sp(size));text.setColor(color);text.setTextAlign(Paint.Align.CENTER);c.drawText(value,x,y,text);}
    private void choose(int index){setSelection(index);if(listener!=null)listener.selected(selection);}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!isClickable())return false;
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();moved=false;return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE){if(Math.abs(e.getX()-downX)+Math.abs(e.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop())moved=true;return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_UP){if(!moved){float x=e.getX(),y=e.getY();if(source.contains(x,y))choose(-1);else for(Branch b:branches){if(b.box.contains(x,y)||b.hit.contains((int)x,(int)y)){choose(b.index);break;}}touchClick=true;performClick();touchClick=false;}return true;}return e.getActionMasked()==MotionEvent.ACTION_CANCEL;
    }
    private void cycle(int delta){int next=(selection+1+delta+branches.size()+1)%(branches.size()+1)-1;choose(next);}
    @Override public boolean performClick(){super.performClick();if(isClickable()&&!touchClick)cycle(1);return true;}
    @Override public boolean onKeyDown(int code,KeyEvent e){if(isClickable()){if(code==KeyEvent.KEYCODE_DPAD_RIGHT||code==KeyEvent.KEYCODE_DPAD_DOWN){cycle(1);return true;}if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_UP){cycle(-1);return true;}}return super.onKeyDown(code,e);}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);if(isClickable()){info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}}
    @Override public boolean performAccessibilityAction(int action,Bundle b){if(isClickable()){if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD){cycle(1);return true;}if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){cycle(-1);return true;}}return super.performAccessibilityAction(action,b);}
    private void describe(){
        if(hidden){setContentDescription("资产桑基图，金额已隐藏");return;}if(data.value==0){setContentDescription("资产桑基图，暂无可绘制的资产");return;}
        String s="资产桑基图，当前起点 "+data.name+"，"+money.amount(data.value)+" "+money.code()+"。";
        for(Branch b:branches)if(b.index==selection)s+="已选 "+b.name+"，"+money.amount(b.value)+" "+money.code()+"，占本层 "+AssetTree.share(b.value,data.value)+"。";
        setContentDescription(s+"点按源节点清除选择；点分支后可展开或查看明细。");
    }

    private float density(){return getResources().getDisplayMetrics().density;}
    private int dp(float v){return (int)(v*density()+.5f);}
    private float sp(float v){return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,v,getResources().getDisplayMetrics());}
}
