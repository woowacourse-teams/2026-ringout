package com.joon.ringout.presentation.destination

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.PlacesStatusCodes
import com.google.android.libraries.places.api.net.SearchByTextRequest
import java.util.concurrent.atomic.AtomicBoolean

@Composable
actual fun PlatformDestinationSearchEffect(
    query: String?,
    requestId: Int,
    onLoadingChange: (Boolean) -> Unit,
    onResults: (List<DestinationSelection>) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val currentOnLoadingChange = rememberUpdatedState(onLoadingChange)
    val currentOnResults = rememberUpdatedState(onResults)
    val currentOnError = rememberUpdatedState(onError)
    val searcher = remember(context) {
        runCatching { GooglePlacesDestinationSearcher(context.applicationContext) }.getOrNull()
    }

    DisposableEffect(query, requestId, searcher) {
        if (query.isNullOrBlank()) {
            currentOnLoadingChange.value(false)
            return@DisposableEffect onDispose { }
        }
        if (searcher == null) {
            currentOnLoadingChange.value(false)
            currentOnError.value(SearchUnavailableError)
            return@DisposableEffect onDispose { }
        }

        val isActive = AtomicBoolean(true)
        val cancellationTokenSource = CancellationTokenSource()
        currentOnLoadingChange.value(true)
        searcher.search(query, cancellationTokenSource.token)
            .addOnSuccessListener { response ->
                if (isActive.get()) {
                    currentOnLoadingChange.value(false)
                    currentOnResults.value(response.places.toDestinationSelections())
                }
            }
            .addOnFailureListener { error ->
                if (isActive.get()) {
                    val failure = error.toGooglePlacesSearchFailure()
                    Log.w(
                        GooglePlacesSearchLogTag,
                        "Text Search (New) failed: $failure",
                    )
                    currentOnLoadingChange.value(false)
                    currentOnError.value(failure.userMessage)
                }
            }
            .addOnCanceledListener {
                if (isActive.get()) {
                    currentOnLoadingChange.value(false)
                }
            }

        onDispose {
            isActive.set(false)
            cancellationTokenSource.cancel()
        }
    }
}

private class GooglePlacesDestinationSearcher(context: Context) {
    private val placesClient: PlacesClient = Places.createClient(context)

    fun search(
        query: String,
        cancellationToken: CancellationToken,
    ) = placesClient.searchByText(
        buildGooglePlacesDestinationSearchRequest(
            query = query,
            cancellationToken = cancellationToken,
        ),
    )
}

internal fun buildGooglePlacesDestinationSearchRequest(
    query: String,
    cancellationToken: CancellationToken,
): SearchByTextRequest = SearchByTextRequest.builder(
    query,
    GooglePlacesDestinationFields,
)
    .setCancellationToken(cancellationToken)
    .setLocationRestriction(GooglePlacesSearchRegionBounds)
    .setMaxResultCount(MaxSearchResultCount)
    .setRegionCode(SearchRegionCode)
    .build()

internal enum class GooglePlacesSearchFailure {
    Network,
    QuotaExceeded,
    RequestDenied,
    InvalidRequest,
    Unknown,
}

internal fun Throwable.toGooglePlacesSearchFailure(): GooglePlacesSearchFailure =
    googlePlacesSearchFailure((this as? ApiException)?.statusCode)

internal fun googlePlacesSearchFailure(statusCode: Int?): GooglePlacesSearchFailure =
    when (statusCode) {
        CommonStatusCodes.NETWORK_ERROR,
        CommonStatusCodes.API_NOT_CONNECTED,
        CommonStatusCodes.TIMEOUT,
        -> GooglePlacesSearchFailure.Network

        PlacesStatusCodes.OVER_QUERY_LIMIT -> GooglePlacesSearchFailure.QuotaExceeded

        PlacesStatusCodes.REQUEST_DENIED,
        CommonStatusCodes.DEVELOPER_ERROR,
        -> GooglePlacesSearchFailure.RequestDenied

        PlacesStatusCodes.INVALID_REQUEST -> GooglePlacesSearchFailure.InvalidRequest
        else -> GooglePlacesSearchFailure.Unknown
    }

private val GooglePlacesSearchFailure.userMessage: String
    get() = when (this) {
        GooglePlacesSearchFailure.Network ->
            "네트워크 연결을 확인한 뒤 다시 검색해 주세요."

        GooglePlacesSearchFailure.QuotaExceeded ->
            "Google Places 검색 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."

        GooglePlacesSearchFailure.RequestDenied ->
            "Google Places API(New) 설정을 확인해 주세요."

        GooglePlacesSearchFailure.InvalidRequest,
        GooglePlacesSearchFailure.Unknown,
        -> SearchFailedError
    }

private const val MaxSearchResultCount = 15
private const val SearchRegionCode = "KR"
private const val SearchUnavailableError =
    "Google Places 검색을 시작하지 못했습니다. API 설정을 확인해 주세요."
private const val SearchFailedError = "검색 중 오류가 발생했습니다."
private const val GooglePlacesSearchLogTag = "GooglePlacesSearch"

private val GooglePlacesDestinationFields = listOf(
    Place.Field.DISPLAY_NAME,
    Place.Field.FORMATTED_ADDRESS,
    Place.Field.LOCATION,
)

private val GooglePlacesSearchRegionBounds = RectangularBounds.newInstance(
    LatLng(33.0, 124.5),
    LatLng(38.7, 131.9),
)
