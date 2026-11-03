package com.qianwang.privatewealth;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.os.Bundle;

/** Dedicated QA package only; never edits the user's ledger or launches an Activity. */
public final class BackgroundInstrumentation extends Instrumentation {
    private int checks;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context context=getTargetContext();check(context.getPackageName().endsWith(".backgroundqa"),"isolated package");
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.schedule(new JobInfo.Builder(721,new ComponentName(context,QuoteJobService.class)).setPeriodic(3600000).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(false).build());
        BackgroundJobs.ensure(context);
        JobInfo quote=scheduler.getPendingJob(721),daily=scheduler.getPendingJob(722);
        check(quote.isPersisted(),"migrates legacy quote job");check(daily.isPersisted(),"snapshot persists");
        check(daily.getNetworkType()==JobInfo.NETWORK_TYPE_NONE,"snapshot requires no network");
        check(quote.getNetworkType()==JobInfo.NETWORK_TYPE_ANY,"quotes require connection");
        check(daily.getIntervalMillis()==3600000L,"hourly observation");
        BackgroundJobs.ensure(context);check(scheduler.getAllPendingJobs().size()==2,"no duplicate jobs");
        WealthApp app=(WealthApp)context.getApplicationContext();
        runOnMainSync(()->{if(app.store.accounts.isEmpty())app.store.addAccount("Background QA","bank",12345);});
        check(app.store.totalAssets()==12345,"seed balance");
        result.putString("stream","PASS "+checks+" background assertions, no Activity launched\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
}
