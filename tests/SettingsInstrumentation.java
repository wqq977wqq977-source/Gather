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
public final class SettingsInstrumentation extends Instrumentation {
    private MainActivity activity;
    private int checks;
    private String mode;
    @Override public void onCreate(Bundle args){super.onCreate(args);mode=args==null?null:args.getString("mode");start();}
    @Override public void onStart(){Bundle result=new Bundle();try{if(mode==null)test();else variant();result.putString("stream","PASS "+checks+" settings Android assertions\n");finish(Activity.RESULT_OK,result);}catch(Throwable error){result.putString("stream","FAIL "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}}
    private void test() throws Exception {
        check(getTargetContext().getPackageName().endsWith(".settingsqa"),"isolated package required");
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        click("设置与备份");check((Boolean)get("settingsPage"),"settings opens as page");
        check(find("显示与外观")!=null&&find("数据与备份")!=null&&find("帮助与说明")!=null,"three settings groups");capture("settings");
        ui(()->((ScrollView)get("viewport")).fullScroll(View.FOCUS_DOWN));capture("settings-bottom");
        click("底部导航");check((Boolean)get("navigationSettingsPage"),"navigation settings reachable");capture("navigation-default");
        click("将总览上移");check("0,4,3,1".equals(NavigationLayout.encode((int[])invokeResult("navOrder"))),"reorder persisted");
        click("更换总览图标");capture("icon-picker");accessibilityClick("星标");accessibilityClick("保存图标");
        check("star".equals(activity.getSharedPreferences("appearance",0).getString("navIcon_0","")),"icon persisted to page identity");capture("navigation-custom");
        click("将总览下移");check("star".equals(activity.getSharedPreferences("appearance",0).getString("navIcon_0","")),"moving page keeps its icon");click("将总览上移");
        click("返回");check(!(Boolean)get("navigationSettingsPage")&&(Boolean)get("settingsPage"),"back returns to settings");click("返回");check(!(Boolean)get("settingsPage"),"back returns to original page");
        LinearLayoutCheck();
        ui(()->activity.finish());activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        check((Integer)get("tab")==0,"fresh launch uses first customized tab");check("star".equals(activity.getSharedPreferences("appearance",0).getString("navIcon_0","")),"icon survives activity restart");
        click("设置与备份");click("底部导航");click("恢复默认导航");
        check("4,0,3,1".equals(NavigationLayout.encode((int[])invokeResult("navOrder"))),"reset order");check(!activity.getSharedPreferences("appearance",0).contains("navIcon_0"),"reset removes overrides");
        click("返回");ui(()->{activity.getSharedPreferences("appearance",0).edit().putInt("sketch",1).apply();invoke("palette");invoke("render");});capture("settings-blue");
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
        android.view.accessibility.AccessibilityNodeInfo node=found.get(0);
        while(node!=null&&!node.isClickable())node=node.getParent();
        check(node!=null&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK),"dialog clicked: "+text);waitForIdleSync();SystemClock.sleep(200);
    }
    private void variant() throws Exception {
        check(getTargetContext().getPackageName().endsWith(".settingsqa"),"isolated package required");
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        click("设置与备份");capture("settings-"+mode);click("底部导航");capture("navigation-"+mode);
    }
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();SystemClock.sleep(150);}
    private Object get(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private void set(String name,Object value){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}catch(Exception e){throw new RuntimeException(e);}}
    private void invoke(String name){try{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private View find(String value){return find(activity.getWindow().getDecorView(),value,false);}
    private View findPrefix(String value){return find(activity.getWindow().getDecorView(),value,true);}
    private View find(View view,String value,boolean prefix){String description=String.valueOf(view.getContentDescription());String text=view instanceof TextView?((TextView)view).getText().toString():"";if(prefix?(description.startsWith(value)||text.startsWith(value)):(description.equals(value)||text.equals(value)))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),value,prefix);if(result!=null)return result;}}return null;}
    private void click(String value){View view=find(value);check(view!=null,"control exists: "+value);while(!view.isClickable()&&view.getParent() instanceof View)view=(View)view.getParent();final View target=view;ui(target::performClick);}
    private void capture(String name)throws Exception{waitForIdleSync();SystemClock.sleep(250);Bitmap shot=getUiAutomation().takeScreenshot();check(shot!=null,"valid screenshot "+name);File dir=new File(getTargetContext().getFilesDir(),"settings-captures");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}
    private void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;}
}
