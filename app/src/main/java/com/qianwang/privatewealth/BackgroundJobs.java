package com.qianwang.privatewealth;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

/** Persistent, idempotent scheduling: opening the app never postpones a pending run. */
public final class BackgroundJobs {
    public static final int QUOTES=721, SNAPSHOT=722;
    private static final long INTERVAL=60*60*1000L;
    public static void ensure(Context context){
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        ensure(scheduler,new JobInfo.Builder(QUOTES,new ComponentName(context,QuoteJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(INTERVAL).setPersisted(true).build());
        ensure(scheduler,new JobInfo.Builder(SNAPSHOT,new ComponentName(context,SnapshotJobService.class))
                .setPeriodic(INTERVAL).setPersisted(true).build());
    }
    private static void ensure(JobScheduler scheduler,JobInfo desired){
        JobInfo existing=scheduler.getPendingJob(desired.getId());
        if(existing!=null&&existing.isPersisted()&&existing.getIntervalMillis()==desired.getIntervalMillis()
                &&existing.getNetworkType()==desired.getNetworkType()&&existing.getService().equals(desired.getService()))return;
        if(scheduler.schedule(desired)!=JobScheduler.RESULT_SUCCESS)throw new IllegalStateException("无法安排后台更新");
    }
}
