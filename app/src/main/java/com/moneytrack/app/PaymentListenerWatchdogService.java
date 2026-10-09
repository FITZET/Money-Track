package com.moneytrack.app;

import android.app.job.JobParameters;
import android.app.job.JobService;

public final class PaymentListenerWatchdogService extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        PaymentListenerReliability.ensureConnected(this);
        return false;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
