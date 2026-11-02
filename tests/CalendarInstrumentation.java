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
public final class CalendarInstrumentation extends Instrumentation {
    private MainActivity activity;
    private int checks;
    private String mode;
    @Override public void onCreate(Bundle args){super.onCreate(args);mode=args==null?null:args.getString("mode");start();}
    @Override public void onStart(){Bundle result=new Bundle();try{if(mode==null)test();else variant();result.putString("stream","PASS "+checks+" calendar Android assertions\n");finish(Activity.RESULT_OK,result);}catch(Throwable error){result.putString("stream","FAIL "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}}
    private void test() throws Exception {
        check(getTargetContext().getPackageName().endsWith(".calendarqa"),"isolated package required");
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();check((Integer)get("tab")==4,"launch opens calendar");capture("empty");
        check(find("日历")!=null&&find("账户")==null,"calendar first, no accounts tab");
        ui(()->invoke("createPreview"));
        ui(()->{set("calendarMonth",YearMonth.now().minusMonths(1));invoke("render");});
        check(find("本月资产变化")!=null,"prior month summary");capture("amount");
        click("涨跌幅");check((Boolean)get("calendarPercent"),"percent control works");capture("percent");
        YearMonth before=(YearMonth)get("calendarMonth");click("下个月");check(before.plusMonths(1).equals(get("calendarMonth")),"next month works");click("上个月");check(before.equals(get("calendarMonth")),"previous month works");
        View date=findPrefix(before+"-15，");check(date!=null,"calendar exposes date target");ui(date::performClick);check((before+"-15").equals(get("calendarSelected")),"day selects details");
        click("隐藏金额");check((Boolean)get("hidden"),"privacy enabled");check(find("••• / •••")!=null,"privacy hides rise and fall counts");capture("privacy");click("显示金额");
        ui(()->{activity.getSharedPreferences("appearance",0).edit().putBoolean("usd",true).putString("fxRate","7.25").putString("fxDate","测试参考汇率").apply();invoke("render");});click("金额");capture("usd");
        ui(()->{activity.getSharedPreferences("appearance",0).edit().putBoolean("usd",false).putInt("sketch",1).apply();invoke("palette");invoke("render");});capture("blue");
        click("总览");check((Integer)get("tab")==0,"overview accessible");
        ui(()->((ScrollView)get("viewport")).fullScroll(View.FOCUS_DOWN));check(find("添加资产")!=null&&find("记收入")!=null&&find("资金划转")!=null,"account actions retained in overview");capture("overview-actions");
        click("日历");check(before.equals(get("calendarMonth")),"navigation preserves selected month");
        ui(()->{
            invoke("exitPreview");WealthStore store=((WealthApp)activity.getApplication()).store;
            WealthStore.Account account=store.addAccount("图标测试账户","bank",10000);
            try{Method method=MainActivity.class.getDeclaredMethod("accountForm",WealthStore.Account.class);method.setAccessible(true);method.invoke(activity,account);}catch(Exception e){throw new RuntimeException(e);}
        });capture("icons-top");
        android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
        scroll(root);SystemClock.sleep(250);capture("icons-more");
        accessibilityClick("云端");accessibilityClick("保存");
        check("cloud".equals(((WealthApp)activity.getApplication()).store.accounts.get(0).icon),"new icon saved");
        ((WealthApp)activity.getApplication()).store.load();
        check("cloud".equals(((WealthApp)activity.getApplication()).store.accounts.get(0).icon),"new icon survives reload");
    }
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
        check(getTargetContext().getPackageName().endsWith(".calendarqa"),"isolated package required");
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        ui(()->{invoke("createPreview");set("calendarMonth",YearMonth.now().minusMonths(1));set("hidden",false);set("calendarPercent",true);invoke("render");});
        capture(mode);ui(()->((ScrollView)get("viewport")).fullScroll(View.FOCUS_DOWN));capture(mode+"-detail");
    }
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();SystemClock.sleep(150);}
    private Object get(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private void set(String name,Object value){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}catch(Exception e){throw new RuntimeException(e);}}
    private void invoke(String name){try{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private View find(String value){return find(activity.getWindow().getDecorView(),value,false);}
    private View findPrefix(String value){return find(activity.getWindow().getDecorView(),value,true);}
    private View find(View view,String value,boolean prefix){String description=String.valueOf(view.getContentDescription());String text=view instanceof TextView?((TextView)view).getText().toString():"";if(prefix?(description.startsWith(value)||text.startsWith(value)):(description.equals(value)||text.equals(value)))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),value,prefix);if(result!=null)return result;}}return null;}
    private void click(String value){View view=find(value);check(view!=null,"control exists: "+value);ui(view::performClick);}
    private void capture(String name)throws Exception{waitForIdleSync();SystemClock.sleep(250);Bitmap shot=getUiAutomation().takeScreenshot();check(shot!=null,"valid screenshot "+name);File dir=new File(getTargetContext().getFilesDir(),"calendar-captures");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}
    private void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;}
}
