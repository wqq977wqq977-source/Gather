package com.qianwang.privatewealth;
import android.app.job.*;
public class QuoteJobService extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        ((WealthApp)getApplication()).refresh(()->jobFinished(params,false));return true;
    }
    @Override public boolean onStopJob(JobParameters params) { return true; }
}
