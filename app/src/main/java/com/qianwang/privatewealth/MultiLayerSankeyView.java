package com.qianwang.privatewealth;

import android.content.Context;
import android.graphics.*;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Bundle;
import java.util.*;

/** All tree depths coexist on one canvas. A single value scale conserves every ribbon. */
public final class MultiLayerSankeyView extends View {
    public interface Listener { void selected(AssetTree.Node node); }
    private static final class Item {
        final ArrayList<Item> children=new ArrayList<>(); AssetTree.Node node; Item parent; int depth,color; float center,flow,span; RectF box=new RectF();
        Item(AssetTree.Node n,Item p,int d,int c){node=n;parent=p;depth=d;color=c;}
    }
    private final ArrayList<Item> items=new ArrayList<>();
    private final AssetTree.Node root;
    private final DisplayMoney money;
    private final SketchStyle style;
    private final Typeface hand,figures;
    private final Paint paint=new Paint(3);
    private final TextPaint text=new TextPaint(3);
    private final boolean hidden,byAccount;
    private final int[] colors;
    private final float unit;
    private int levels=1,selected=-1;
    private float graphHeight,downX,downY;
    private boolean moved,touchClick;
    private Listener listener;
    public MultiLayerSankeyView(Context c,AssetTree.Node root,DisplayMoney money,Typeface figures,boolean hidden,boolean byAccount){
        super(c);this.root=root;this.money=money;this.figures=figures;this.hidden=hidden;this.byAccount=byAccount;style=new SketchStyle(c);hand=SketchStyle.handwriting(c);colors=style.colors();unit=root.value==0?0:dp(290)/(float)root.value;
        if(!hidden&&root.value>0){Item first=build(root,null,0,0);measureSpan(first);place(first,dp(48));graphHeight=first.span+dp(70);}
        setFocusable(true);setClickable(!hidden&&root.value>0);describe();
    }
    private Item build(AssetTree.Node n,Item parent,int depth,int color){int category=AssetTree.categoryColor(n.key);if(category>=0)color=category;Item item=new Item(n,parent,depth,color);items.add(item);if(parent!=null)parent.children.add(item);levels=Math.max(levels,depth+1);int rank=0;for(AssetTree.Node child:n.children)build(child,item,depth+1,byAccount&&depth==0?rank++:color);return item;}
    private float measureSpan(Item item){item.flow=item.node.value*unit;float childSpan=0;for(Item c:item.children)childSpan+=measureSpan(c);item.span=Math.max(item.flow+dp(24),Math.max(Math.max(dp(70),sp(11)*3.6f+dp(24)),childSpan));return item.span;}
    private void place(Item item,float top){item.center=top+item.span/2;float children=0;for(Item c:item.children)children+=c.span;float y=top+(item.span-children)/2;for(Item c:item.children){place(c,y);y+=c.span;}}
    public int desiredHeight(){return hidden||root.value==0?(int)dp(180):(int)Math.ceil(graphHeight);}
    public int detailWidth(){return (int)dp(levels*150+(levels-1)*90+16);}
    public void setListener(Listener l){listener=l;}
    @Override protected void onMeasure(int w,int h){int requested=getLayoutParams()==null?-1:getLayoutParams().width;setMeasuredDimension(requested>0?requested:resolveSize(getSuggestedMinimumWidth(),w),resolveSize(desiredHeight(),h));}
    private void boxes(){float width=(getWidth()-dp(16))/(levels+(levels-1)*.65f),step=width*1.65f;for(Item i:items){float h=Math.max(i.flow,Math.max(dp(48),sp(11)*3.6f));i.box.set(dp(8)+i.depth*step,i.center-h/2,dp(8)+i.depth*step+width,i.center+h/2);}}
    @Override protected void onDraw(Canvas c){super.onDraw(c);if(hidden||root.value==0){label(c,hidden?"金额已隐藏":"暂无可绘制的资产",getWidth()/2f,dp(90),13,style.ink,false);return;}boxes();
        String[] headings=byAccount?new String[]{"总资产","账户","现金 / 持仓"}:new String[]{"总资产","资产类别","账户","现金 / 持仓"};
        for(int d=0;d<levels;d++)for(Item i:items)if(i.depth==d){label(c,headings[Math.min(d,headings.length-1)],i.box.centerX(),dp(25),10,style.muted,false);break;}
        for(Item parent:items){float used=0;for(Item child:parent.children){float start=parent.center-parent.flow/2+used,end=child.center-child.flow/2;used+=child.flow;Path path=new Path();float x=parent.box.right-1,y=child.box.left+1,dx=(y-x)*.48f;path.moveTo(x,start);path.cubicTo(x+dx,start,y-dx,end,y,end);path.lineTo(y,end+child.flow);path.cubicTo(y-dx,end+child.flow,x+dx,start+child.flow,x,start+child.flow);path.close();boolean active=selected<0||related(child,items.get(selected));SketchStyle.pencilFill(c,path,colors[child.color%colors.length],density(),active?65:15);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(.9f));paint.setColor(colors[child.color%colors.length]);paint.setAlpha(active?210:45);c.drawPath(path,paint);}}
        paint.setAlpha(255);for(int index=0;index<items.size();index++){Item i=items.get(index);Path frame=SketchStyle.frame(i.box,density());paint.setStyle(Paint.Style.FILL);paint.setColor(index==selected?style.pale:style.surface);c.drawPath(frame,paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(style.ink);paint.setStrokeWidth(dp(1));c.drawPath(frame,paint);text.setTypeface(hand);text.setTextSize(sp(11));String name=TextUtils.ellipsize(i.node.name,text,i.box.width()-dp(8),TextUtils.TruncateAt.END).toString();label(c,name,i.box.centerX(),i.center-sp(3),11,style.ink,false);String value=money.compact(i.node.value);text.setTypeface(figures);text.setTextSize(sp(13));float size=13*Math.min(1,(i.box.width()-dp(8))/text.measureText(value));label(c,value,i.box.centerX(),i.center+sp(14),size,style.muted,true);}
    }
    private boolean related(Item a,Item b){for(Item p=a;p!=null;p=p.parent)if(p==b)return true;for(Item p=b;p!=null;p=p.parent)if(p==a)return true;return false;}
    private boolean hit(RectF box,float x,float y){RectF target=new RectF(box);target.inset(-Math.max(0,(dp(48)-box.width())/2),-Math.max(0,(dp(48)-box.height())/2));return target.contains(x,y);}
    private void choose(int index){selected=index;describe();invalidate();if(listener!=null)listener.selected(index<0?null:items.get(index).node);}
    public String selectedKey(){return selected<0?null:items.get(selected).node.key;}
    public void selectKey(String key){
        if(key!=null)for(int i=0;i<items.size();i++)if(items.get(i).node.key.equals(key)){choose(i);return;}
        choose(-1);
    }
    public void clearSelection(){choose(-1);}
    private void cycle(int step){choose((selected+1+step+items.size()+1)%(items.size()+1)-1);}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){if(!isClickable())return false;switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:downX=e.getX();downY=e.getY();moved=false;return true;case MotionEvent.ACTION_MOVE:if(Math.abs(e.getX()-downX)+Math.abs(e.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop())moved=true;return true;case MotionEvent.ACTION_UP:if(!moved){boxes();int hit=-1;for(int i=0;i<items.size();i++)if(hit(items.get(i).box,e.getX(),e.getY()))hit=i;choose(hit);touchClick=true;performClick();touchClick=false;}return true;case MotionEvent.ACTION_CANCEL:moved=true;return true;default:return true;}}
    @Override public boolean performClick(){super.performClick();if(isClickable()&&!touchClick)cycle(1);return true;}
    @Override public boolean onKeyDown(int key,android.view.KeyEvent e){if(isClickable()&&(key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_DOWN)){cycle(1);return true;}if(isClickable()&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_UP)){cycle(-1);return true;}return super.onKeyDown(key,e);}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);if(isClickable()){info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}}
    @Override public boolean performAccessibilityAction(int action,Bundle args){if(isClickable()&&(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)){cycle(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1);return true;}return super.performAccessibilityAction(action,args);}
    private void describe(){String s=hidden?"多层桑基图，金额已隐藏":"多层桑基图，全部 "+levels+" 层同时展开，总资产 "+money.amount(root.value)+" "+money.code();if(selected>=0){Item i=items.get(selected);s+="。已选 "+i.node.name+"，"+money.amount(i.node.value)+"，占总资产 "+AssetTree.share(i.node.value,root.value);}setContentDescription(s);}
    private void label(Canvas c,String s,float x,float y,float size,int color,boolean number){text.setStyle(Paint.Style.FILL);text.setTypeface(number?figures:hand);text.setTextSize(sp(size));text.setTextAlign(Paint.Align.CENTER);text.setColor(color);c.drawText(s,x,y,text);}
    private float density(){return getResources().getDisplayMetrics().density;}
    private float dp(float x){return x*density();}
    private float sp(float x){return x*getResources().getDisplayMetrics().scaledDensity;}
}
