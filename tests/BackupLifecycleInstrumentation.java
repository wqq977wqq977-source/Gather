package com.qianwang.privatewealth;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.LinearLayout;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Runs only in an isolated QA package; all ledgers and documents are synthetic. */
public final class BackupLifecycleInstrumentation extends Instrumentation {
    private int assertions;
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);assertions++;}
    private Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private BackupWork.Result await(BackupWork work)throws Exception{
        long end=SystemClock.uptimeMillis()+15000;
        final BackupWork.Result[] found={null};
        while(SystemClock.uptimeMillis()<end){runOnMainSync(()->{if(!work.busy())found[0]=work.peek();});if(found[0]!=null){runOnMainSync(work::take);return found[0];}SystemClock.sleep(10);}
        throw new AssertionError("backup result timed out");
    }
    private String ledger(WealthStore store)throws Exception{org.json.JSONObject json=new org.json.JSONObject(store.exportJson());json.remove("exportedAt");return json.toString();}
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle report=new Bundle();
        try{runChecks();report.putString("stream","PASS "+assertions+" Android backup/lifecycle assertions\n");finish(Activity.RESULT_OK,report);}
        catch(Throwable error){report.putString("stream","FAIL "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,report);}
    }
    private void runChecks()throws Exception{
        check(getTargetContext().getPackageName().endsWith(".backupqa"),"requires isolated QA package");
        MainActivity activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
        WealthApp app=(WealthApp)getTargetContext().getApplicationContext();
        runOnMainSync(()->app.store.addAccount("Synthetic QA","bank",12345));
        BackupWork work=new BackupWork(getTargetContext());
        String original=ledger(app.store);
        char[] password="backup-test-123".toCharArray();
        runOnMainSync(()->work.encrypt(app.store,password));
        check("choose".equals(await(work).kind),"encrypt completes off main");
        for(char c:password)check(c=='\0',"password wiped");
        String ciphertext=new String(Files.readAllBytes(work.pendingFile().toPath()),StandardCharsets.UTF_8);
        check(!ciphertext.contains("Synthetic QA"),"temporary file is ciphertext");
        File document=new File(getTargetContext().getCacheDir(),"test-document.guilong");
        runOnMainSync(()->work.save(Uri.fromFile(document)));
        check("saved".equals(await(work).kind),"document export succeeds");
        check(!work.pendingFile().exists(),"temporary export deleted");
        runOnMainSync(()->work.decrypt(Uri.fromFile(document),"incorrect".toCharArray()));
        check("error".equals(await(work).kind),"wrong password rejected");
        check(original.equals(ledger(app.store)),"failed decrypt preserves ledger");
        runOnMainSync(()->work.decrypt(Uri.fromFile(document),"backup-test-123".toCharArray()));
        BackupWork.Result decrypted=await(work);
        check("confirm".equals(decrypted.kind)&&decrypted.accounts==1,"decrypt waits for confirmation");
        check(original.equals(ledger(app.store)),"checking backup does not restore it");
        runOnMainSync(()->work.restore(app.store,"{invalid"));
        check("error".equals(await(work).kind),"malformed import rejected");
        check(original.equals(ledger(app.store)),"failed restore preserves ledger");
        runOnMainSync(()->work.restore(app.store,decrypted.value));
        check("restored".equals(await(work).kind),"valid restore completes");
        check(original.equals(ledger(app.store)),"restored ledger matches original");
        CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);
        runOnMainSync(()->app.backup.start(()->{synchronized(app.store){locked.countDown();if(!release.await(12,TimeUnit.SECONDS))throw new AssertionError("UI blocked on ledger lock");}return new BackupWork.Result("saved",null,0,0);}));
        check(locked.await(2,TimeUnit.SECONDS),"worker holds ledger lock");
        runOnMainSync(()->check(!app.backup.start(()->null),"concurrent operation rejected"));
        try{
            Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);
            runOnMainSync(()->{try{render.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
            check((Boolean)field(activity,"backupDeferredRender"),"render deferred without waiting for store lock");
            ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
            runOnMainSync(activity::recreate);
            Activity recreated=waitForMonitorWithTimeout(monitor,5000);removeMonitor(monitor);waitForIdleSync();
            check(recreated!=null&&recreated!=activity,"activity recreated while backup busy");
            check((Boolean)field(recreated,"backupDeferredRender"),"recreated activity defers store reads");
            check(((LinearLayout)field(recreated,"root")).getChildCount()==0,"placeholder has no ledger views");
            check(field(recreated,"backupProgress")!=null,"progress reattached after recreation");
            release.countDown();
            long end=SystemClock.uptimeMillis()+5000;while(app.backup.busy()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(10);waitForIdleSync();
            check(!app.backup.busy(),"backup completed");
            check(!(Boolean)field(recreated,"backupDeferredRender"),"completion resumes rendering");
            check(((LinearLayout)field(recreated,"root")).getChildCount()>0,"full UI rendered after completion");
        }finally{release.countDown();document.delete();}
    }
}
