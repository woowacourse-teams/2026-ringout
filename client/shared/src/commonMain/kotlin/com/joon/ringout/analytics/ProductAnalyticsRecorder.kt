package com.joon.ringout.analytics

enum class AnalyticsLoginState(
    internal val wireName: String,
) {
    LoggedIn("logged_in"),
    LoggedOut("logged_out"),
}

enum class DestinationSelectionSource(
    internal val wireName: String,
) {
    MapShortcut("map_shortcut"),
    SearchShortcut("search_shortcut"),
}

enum class StampMonthChangeDirection(
    internal val wireName: String,
) {
    Previous("previous"),
    Next("next"),
}

interface ProductAnalyticsRecorder {
    fun recordDestinationCreated(
        destinationId: Long,
        loginState: AnalyticsLoginState,
    )

    fun recordDestinationSelected(
        source: DestinationSelectionSource,
        loginState: AnalyticsLoginState,
    )

    fun recordStampCalendarViewed(
        year: Int,
        month: Int,
        loginState: AnalyticsLoginState,
    )

    fun recordStampMonthChanged(
        direction: StampMonthChangeDirection,
        year: Int,
        month: Int,
        loginState: AnalyticsLoginState,
    )

    fun recordAccountWithdrawalCompleted()
}

internal fun interface ProductAnalyticsUsageStore {
    fun claimDestinationCreation(destinationKey: String): Long?
}

internal class DefaultProductAnalyticsRecorder(
    private val tracker: AnalyticsTracker,
    private val usageStore: ProductAnalyticsUsageStore,
) : ProductAnalyticsRecorder {
    override fun recordDestinationCreated(
        destinationId: Long,
        loginState: AnalyticsLoginState,
    ) = safelyRecord {
        if (destinationId <= 0L) return@safelyRecord
        val creationIndex = usageStore.claimDestinationCreation(
            destinationKey = "${loginState.wireName}:$destinationId",
        ) ?: return@safelyRecord
        tracker.log(
            AnalyticsEvent(
                name = AnalyticsEventName.DestinationCreated,
                parameters = mapOf(
                    AnalyticsParameterName.LoginState to
                        AnalyticsParameterValue.Text(loginState.wireName),
                    AnalyticsParameterName.CreationIndex to
                        AnalyticsParameterValue.Number(creationIndex),
                ),
            ),
        )
    }

    override fun recordDestinationSelected(
        source: DestinationSelectionSource,
        loginState: AnalyticsLoginState,
    ) = safelyRecord {
        tracker.log(
            AnalyticsEvent(
                name = AnalyticsEventName.DestinationSelected,
                parameters = mapOf(
                    AnalyticsParameterName.Source to
                        AnalyticsParameterValue.Text(source.wireName),
                    AnalyticsParameterName.LoginState to
                        AnalyticsParameterValue.Text(loginState.wireName),
                ),
            ),
        )
    }

    override fun recordStampCalendarViewed(
        year: Int,
        month: Int,
        loginState: AnalyticsLoginState,
    ) = safelyRecord {
        tracker.log(
            AnalyticsEvent(
                name = AnalyticsEventName.StampCalendarViewed,
                parameters = mapOf(
                    AnalyticsParameterName.Year to AnalyticsParameterValue.Number(year.toLong()),
                    AnalyticsParameterName.Month to AnalyticsParameterValue.Number(month.toLong()),
                    AnalyticsParameterName.LoginState to
                        AnalyticsParameterValue.Text(loginState.wireName),
                ),
            ),
        )
    }

    override fun recordStampMonthChanged(
        direction: StampMonthChangeDirection,
        year: Int,
        month: Int,
        loginState: AnalyticsLoginState,
    ) = safelyRecord {
        tracker.log(
            AnalyticsEvent(
                name = AnalyticsEventName.StampMonthChanged,
                parameters = mapOf(
                    AnalyticsParameterName.Direction to
                        AnalyticsParameterValue.Text(direction.wireName),
                    AnalyticsParameterName.Year to AnalyticsParameterValue.Number(year.toLong()),
                    AnalyticsParameterName.Month to AnalyticsParameterValue.Number(month.toLong()),
                    AnalyticsParameterName.LoginState to
                        AnalyticsParameterValue.Text(loginState.wireName),
                ),
            ),
        )
    }

    override fun recordAccountWithdrawalCompleted() = safelyRecord {
        tracker.log(
            AnalyticsEvent(
                name = AnalyticsEventName.AccountWithdrawalCompleted,
                parameters = emptyMap(),
            ),
        )
    }

    private inline fun safelyRecord(action: () -> Unit) {
        runCatching(action)
    }
}
