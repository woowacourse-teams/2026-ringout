package com.joon.ringout.presentation.forest

import com.joon.ringout.domain.forest.ForestState
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

internal object ForestProtocol {
    const val Version = 1
    fun snapshot(state: ForestState): JsonObject = buildJsonObject {
        putJsonObject("growth") {
            with(state.growth) {
                put("successfulDayCount", successfulDayCount)
                put("growthDayCount", growthDayCount)
                put("completedTreeCount", completedTreeCount)
                put("isComplete", isComplete)
                put("hasGrowingSeed", hasGrowingSeed)
                put("filledSlots", filledSlots)
                put("remainingDays", remainingDays)
            }
        }
        put("onboardingCompletedAtEpochMillis", state.onboardingCompletedAtEpochMillis?.let(::JsonPrimitive) ?: JsonNull)
        putJsonArray("trees") {
            state.trees.sortedBy { it.growthCycle }.forEach { tree ->
                addJsonObject {
                    put("growthCycle", tree.growthCycle)
                    put("kind", tree.kind.persistedValue)
                    put("slotIndex", tree.slotIndex)
                    put("plantedAtEpochMillis", tree.plantedAtEpochMillis)
                    put("acknowledgedAtEpochMillis", tree.acknowledgedAtEpochMillis?.let(::JsonPrimitive) ?: JsonNull)
                }
            }
        }
    }
    fun message(type: String, requestId: String, body: JsonObjectBuilder.() -> Unit = {}): String =
        buildJsonObject {
            put("version", Version)
            put("type", type)
            put("requestId", requestId)
            body()
        }.toString()

    // Encode the entire message as a JSON string literal; never interpolate web input as code.
    fun deliveryScript(message: String): String =
        "window.RingoutForestBridge?.receive(JSON.parse(${Json.encodeToString(message)}));"
}

/** Only the configured document can navigate or invoke the bridge. HTTP is loopback-only. */
internal class ForestEndpoint private constructor(val url: String, val origin: String, val isLocalHttp: Boolean) {
    fun allowsDocument(candidate: String?): Boolean = candidate != null && runCatching {
        val parsed = Url(candidate)
        parsed.user == null && parsed.password == null && parsed.fragment.isEmpty() &&
            URLBuilder(parsed).buildString() == url
    }.getOrDefault(false)

    companion object {
        fun parse(raw: String): ForestEndpoint? = runCatching {
            require(raw.isNotBlank())
            val parsed = Url(raw.trim())
            require(parsed.user == null && parsed.password == null && parsed.fragment.isEmpty())
            val local = parsed.protocol.name == "http" && parsed.host in setOf("localhost", "127.0.0.1", "::1")
            require(parsed.protocol.name == "https" || local)
            require(parsed.host.isNotBlank() && parsed.parameters.isEmpty())
            val builder = URLBuilder(parsed).apply { parameters.append("view", "app") }
            val origin = URLBuilder(parsed).apply { encodedPath = ""; parameters.clear() }.buildString().trimEnd('/')
            ForestEndpoint(builder.buildString(), origin, local)
        }.getOrNull()
    }
}
