package com.qianwang.privatewealth;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Records today's latest known valuation without waiting for a network connection. */
public final class SnapshotJobService extends JobService {
    @Override public boolean onStartJob(JobParameters params){
        WealthApp app=(WealthApp)getApplication();
        if(app.loadError==null&&app.store!=null&&!app.backup.busy()){
            try{app.store.recordToday();}
            catch(Exception error){android.util.Log.w("Gather","Daily snapshot save failed",error);}
        }
        // A busy backup or storage failure is retried by the next periodic run.
        return false;
    }
    @Override public boolean onStopJob(JobParameters params){return true;}
}
