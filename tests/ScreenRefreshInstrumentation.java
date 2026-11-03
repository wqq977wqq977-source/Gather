package com.qianwang.privatewealth;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ScrollView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.YearMonth;

/** Isolated emulator checks. Never launches or changes the user's real package. */
public final class ScreenRefreshInstrumentation extends Instrumentation {
    private MainActivity activity;
    private int checks;
    private String mode;
    @Override public void onCreate(Bundle args){super.onCreate(args);mode=args==null?null:args.getString("mode");start();}
    @Override public void onStart(){Bundle result=new Bundle();try{if(mode==null)test();else variant();result.putString("stream","PASS "+checks+" refinement Android assertions\n");finish(Activity.RESULT_OK,result);}catch(Throwable error){result.putString("stream","FAIL "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}}
    private void test() throws Exception {
        check(getTargetContext().getPackageName().endsWith(".screenrefreshqa"),"isolated package");
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        ui(()->{invoke("createPreview");set("tab",3);invoke("render");});
        ScrollView scroll=(ScrollView)get("viewport");View root=(View)get("root");final int[] detach={0};
        ui(()->{root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){detach[0]++;}});scroll.scrollTo(0,600);});
        int position=scroll.getScrollY();
        for(String action:new String[]{"近 7 天","全部","近 30 天","隐藏金额","显示金额"}){
            click(action);check(detach[0]==0,"in-page action must not detach root: "+action);check(get("viewport")==scroll,"same viewport");check(((ViewGroup)root).getChildCount()==3,"no accumulating shell children");check(scroll.getScrollY()==position,"same-page scroll preserved");
        }
        click("桑基图");check(detach[0]==0,"Sankey navigation keeps shell");check(scroll.getScrollY()==0,"new page starts at top");
        ui(()->scroll.scrollTo(0,100));int sankeyY=scroll.getScrollY();
        click("按账户");check(detach[0]==0,"Sankey mode keeps root");check(scroll.getScrollY()==sankeyY,"Sankey scroll retained");click("按类别");
        click("返回");click("设置与备份");check(detach[0]==0,"settings keeps shell");click("底部导航");
        click("将总览上移");check(detach[0]==0,"reordering keeps shell");click("恢复默认导航");
        ui(()->{activity.getSharedPreferences("appearance",0).edit().putInt("sketch",1).apply();invoke("palette");invoke("render");});check(detach[0]==0,"theme keeps root");
        click("返回");click("返回");
        for(String page:new String[]{"日历","总览","持仓","统计"}){click(page);check(get("root")==root,"navigation stable shell");check(get("viewport")==scroll,"navigation stable viewport");}
        capture("screen-refresh-fixed");
    }
    private void LinearLayoutCheck(){android.widget.LinearLayout root=(android.widget.LinearLayout)get("root");ViewGroup nav=(ViewGroup)root.getChildAt(root.getChildCount()-1);check("总览".contentEquals(nav.getChildAt(0).getContentDescription()),"real bottom nav reorders");check("日历".contentEquals(nav.getChildAt(1).getContentDescription()),"real second destination");}
    private Object invokeResult(String name){try{Method method=MainActivity.class.getDeclaredMethod(name);method.setAccessible(true);return method.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private boolean scroll(android.view.accessibility.AccessibilityNodeInfo node){
        if(node==null)return false;
        if(node.isScrollable()&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true;
        for(int i=0;i<node.getChildCount();i++)if(scroll(node.getChild(i)))return true;
        return false;
    }
    private void accessibilityClick(String text){
        android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
        java.util.List<android.view.accessibility.AccessibilityNodeInfo> found=root.findAccessibilityNodeInfosByText(text);
        check(!found.isEmpty(),"dialog control exists: "+text);
        android.view.accessibility.AccessibilityNodeInfo node=found.get(found.size()-1);
        for(android.view.accessibility.AccessibilityNodeInfo candidate:found)if(text.contentEquals(candidate.getText()==null?"":candidate.getText())&&candidate.isClickable()){node=candidate;break;}
        while(node!=null&&!node.isClickable())node=node.getParent();
        check(node!=null&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK),"dialog clicked: "+text);waitForIdleSync();SystemClock.sleep(200);
    }
    private void variant() throws Exception {
        check(getTargetContext().getPackageName().endsWith(".screenrefreshqa"),"isolated package required");
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        ui(()->{invoke("createPreview");set("tab",1);invoke("render");});
        int green=(Integer)get("positive"),red=(Integer)get("red");
        check(((TextView)findPrefix("较昨收 +")).getCurrentTextColor()==green,"default green gains");
        ui(()->{activity.getSharedPreferences("appearance",0).edit().putBoolean("redGains",true).apply();invoke("render");});
        check(activity.getSharedPreferences("appearance",0).getBoolean("redGains",false),"choice persisted");
        check(((TextView)findPrefix("较昨收 +")).getCurrentTextColor()==red,"holding gains turn red");capture("red-gains");
        ui(()->activity.finish());activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        check(activity.getSharedPreferences("appearance",0).getBoolean("redGains",false),"choice survives restart");
        ui(()->activity.getSharedPreferences("appearance",0).edit().putBoolean("redGains",false).apply());check(!activity.getSharedPreferences("appearance",0).getBoolean("redGains",true),"green preference restored");
        ui(()->{set("settingsPage",false);invoke("createPreview");set("tab",1);invoke("render");});check(((TextView)findPrefix("较昨收 +")).getCurrentTextColor()==green,"holding gains turn green");

    }
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();SystemClock.sleep(150);}
    private Object get(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private void set(String name,Object value){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}catch(Exception e){throw new RuntimeException(e);}}
    private void invoke(String name){try{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private View find(String value){return find(activity.getWindow().getDecorView(),value,false);}
    private View findPrefix(String value){return find(activity.getWindow().getDecorView(),value,true);}
    private View find(View view,String value,boolean prefix){String description=String.valueOf(view.getContentDescription());String text=view instanceof TextView?((TextView)view).getText().toString():"";if(prefix?(description.startsWith(value)||text.startsWith(value)):(description.equals(value)||text.equals(value)))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),value,prefix);if(result!=null)return result;}}return null;}
    private void click(String value){View view=find(value);check(view!=null,"control exists: "+value);while(!view.isClickable()&&view.getParent() instanceof View)view=(View)view.getParent();final View target=view;ui(target::performClick);}
    private void capture(String name)throws Exception{waitForIdleSync();SystemClock.sleep(250);Bitmap shot=getUiAutomation().takeScreenshot();check(shot!=null,"valid screenshot "+name);File dir=new File(getTargetContext().getFilesDir(),"refinement-captures");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}
    private void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;}
}
