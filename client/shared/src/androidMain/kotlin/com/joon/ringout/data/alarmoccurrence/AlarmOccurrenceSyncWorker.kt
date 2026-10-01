package com.joon.ringout.data.alarmoccurrence

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class AlarmOccurrenceSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val retryAt = AndroidAlarmOccurrenceRuntime.get(applicationContext).flush()
        if (retryAt == null) Result.success() else Result.retry()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.e("AlarmOccurrence", "백그라운드 알람 동기화 재시도", error)
        Result.retry()
    }

    internal companion object {
        fun enqueue(context: Context, recovery: Boolean = false) {
            val work = OneTimeWorkRequestBuilder<AlarmOccurrenceSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "alarm-occurrence-sync",
                if (recovery) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.APPEND_OR_REPLACE,
                work,
            )
        }
    }
}
