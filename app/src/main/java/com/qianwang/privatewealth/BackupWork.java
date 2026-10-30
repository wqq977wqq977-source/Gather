package com.qianwang.privatewealth;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** One operation at a time; results survive Activity recreation while the process lives. */
final class BackupWork {
    interface Task { Result run() throws Exception; }
    static final class Result {
        final String kind, value; final int accounts, holdings;
        Result(String kind,String value,int accounts,int holdings){this.kind=kind;this.value=value;this.accounts=accounts;this.holdings=holdings;}
    }
    private final Context context;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean busy;
    private Result result;
    private Runnable listener;
    BackupWork(Context context){this.context=context.getApplicationContext();}
    File pendingFile(){return new File(context.getCacheDir(),"pending-export.guilong");}
    boolean busy(){return busy;}
    void listen(Runnable callback){listener=callback;if(callback!=null)callback.run();}
    Result peek(){return result;}
    Result take(){Result r=result;result=null;return r;}
    boolean start(Task task){
        if(busy||result!=null)return false;
        busy=true;notifyListener();
        executor.execute(()->{Result done;try{done=task.run();}catch(Exception e){done=new Result("error",e.getMessage()==null?"备份操作失败，请重试":e.getMessage(),0,0);}Result finished=done;main.post(()->{busy=false;result=finished;notifyListener();});});
        return true;
    }
    private void notifyListener(){if(listener!=null)listener.run();}
    void encrypt(WealthStore store,char[] password){
        if(!start(()->{try{String data=BackupCodec.encrypt(store.exportJson(),password);File target=pendingFile();File temp=new File(target.getPath()+".tmp");try{try(FileOutputStream out=new FileOutputStream(temp)){out.write(data.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}if(!temp.renameTo(target))throw new IOException("无法准备加密备份");}finally{temp.delete();}return new Result("choose",null,0,0);}finally{Arrays.fill(password,'\0');}}))Arrays.fill(password,'\0');
    }
    void save(Uri uri){start(()->{try{try(InputStream in=new FileInputStream(pendingFile());OutputStream out=context.getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return new Result("saved",null,0,0);}catch(Exception e){throw new IOException("保存失败，请重新导出");}finally{pendingFile().delete();}});}
    void restore(WealthStore store,String json){start(()->{try{store.importJson(json);return new Result("restored",null,0,0);}catch(Exception e){throw new IOException("恢复失败，原数据未修改");}});}
    void decrypt(Uri uri,char[] password){
        if(!start(()->{try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=context.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){bytes.write(b,0,n);if(bytes.size()>6*1024*1024)throw new IOException("备份文件过大");}}String json;try{json=BackupCodec.decrypt(new String(bytes.toByteArray(),StandardCharsets.UTF_8),password);}catch(Exception e){throw new IOException("密码不正确或文件已损坏，原数据未修改");}try{JSONObject parsed=new JSONObject(json);return new Result("confirm",json,parsed.getJSONArray("accounts").length(),parsed.getJSONArray("holdings").length());}catch(Exception e){throw new IOException("备份数据格式不正确");}}finally{Arrays.fill(password,'\0');}}))Arrays.fill(password,'\0');
    }
}
