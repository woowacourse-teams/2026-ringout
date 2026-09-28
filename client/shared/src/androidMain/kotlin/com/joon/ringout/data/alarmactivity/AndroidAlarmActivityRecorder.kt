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

    fun recordRinging(alarmId: String, occurrenceId: String) {
        val now = currentAlarmActivityTimestamp()
        val event = JSONObject()
            .put("alarmId", alarmId)
            .put("occurrenceId", occurrenceId)
            .put("epochMillis", now.epochMillis)
            .put("localDate", now.localDate)
        if (!pending.edit().putString(occurrenceId, event.toString()).commit()) {
            Log.e("AlarmActivity", "Could not persist pending ringing event")
        }
        flush()
    }

    fun flush() {
        scope.launch {
            flushMutex.withLock {
                pending.all.forEach { (key, value) ->
                    runCatching {
                        val event = JSONObject(value as String)
                        dao.record(AlarmActivityEntity.rang(
                            alarmId = event.getString("alarmId"),
                            occurrenceId = event.getString("occurrenceId"),
                            timestamp = AlarmActivityTimestamp(event.getLong("epochMillis"), event.getString("localDate")),
                        ))
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
