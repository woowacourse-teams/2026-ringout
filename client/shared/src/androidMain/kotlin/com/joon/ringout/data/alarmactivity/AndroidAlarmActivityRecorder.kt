package com.joon.ringout.data.alarmactivity

import android.content.Context
import android.util.Log
import com.joon.ringout.data.database.getRingoutDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Persist before dispatching to Room so stopping the service cannot discard a ring. */
internal class AndroidAlarmActivityRecorder private constructor(context: Context) {
    private val pending = context.getSharedPreferences("alarm_activity_pending", Context.MODE_PRIVATE)
    private val dao = getRingoutDatabase(context).alarmActivityDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushMutex = Mutex()

    fun recordRinging(alarmId: String, occurrenceId: String, scheduleVersion: Long) =
        enqueue("RANG", occurrenceId, alarmId, scheduleVersion)

    fun recordRingingStopped(occurrenceId: String) =
        enqueue("STOPPED", occurrenceId)

    private fun enqueue(type: String, occurrenceId: String, alarmId: String = "", scheduleVersion: Long = 1) {
        val now = currentAlarmActivityTimestamp()
        val event = JSONObject()
            .put("type", type)
            .put("alarmId", alarmId)
            .put("scheduleVersion", scheduleVersion)
            .put("occurrenceId", occurrenceId)
            .put("epochMillis", now.epochMillis)
            .put("localDate", now.localDate)
        val key = "$type:$occurrenceId"
        synchronized(pending) {
            if (!pending.contains(key) && !pending.edit().putString(key, event.toString()).commit()) {
                Log.e("AlarmActivity", "Could not persist pending ringing event")
            }
        }
        flush()
    }

    fun flush() {
        scope.launch {
            flushMutex.withLock {
                pending.all.forEach { (key, value) ->
                    runCatching {
                        val event = JSONObject(value as String)
                        val occurrenceId = event.getString("occurrenceId")
                        if (event.optString("type", "RANG") == "STOPPED") {
                            dao.mergeOccurrenceTimes(AlarmOccurrenceTimesEntity(
                                occurrenceId = occurrenceId,
                                ringingStoppedAtEpochMillis = event.getLong("epochMillis"),
                            ))
                        } else {
                            dao.record(AlarmActivityEntity.rang(
                                alarmId = event.getString("alarmId"),
                                occurrenceId = occurrenceId,
                                timestamp = AlarmActivityTimestamp(event.getLong("epochMillis"), event.getString("localDate")),
                                scheduleVersion = event.optLong("scheduleVersion", 1),
                            ))
                        }
                        pending.edit().remove(key).commit()
                    }.onFailure { Log.e("AlarmActivity", "Could not store ringing event; retained for retry", it) }
                }
            }
        }
    }

    companion object {
        @Volatile private var instance: AndroidAlarmActivityRecorder? = null

        fun get(context: Context): AndroidAlarmActivityRecorder = instance ?: synchronized(this) {
            instance ?: AndroidAlarmActivityRecorder(context.applicationContext).also { instance = it }
        }
    }
}
