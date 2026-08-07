package com.joon.ringout.presentation.destination

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.PlacesClient
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
        runCatching { GoogleDestinationSearcher(context.applicationContext) }.getOrNull()
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
            .addOnFailureListener {
                if (isActive.get()) {
                    currentOnLoadingChange.value(false)
                    currentOnError.value(SearchFailedError)
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

private class GoogleDestinationSearcher(context: Context) {
    private val placesClient: PlacesClient = Places.createClient(context)

    fun search(
        query: String,
        cancellationToken: CancellationToken,
    ) = placesClient.searchByText(
        SearchByTextRequest.builder(
            query,
            listOf(
                Place.Field.DISPLAY_NAME,
                Place.Field.FORMATTED_ADDRESS,
                Place.Field.LOCATION,
            ),
        )
            .setCancellationToken(cancellationToken)
            .setLocationRestriction(SearchRegionBounds)
            .setMaxResultCount(MaxSearchResultCount)
            .setRegionCode(SearchRegionCode)
            .build(),
    )
}

private const val MaxSearchResultCount = 15
private const val SearchRegionCode = "KR"
private const val SearchUnavailableError = "장소 검색을 시작하지 못했습니다. 잠시 후 다시 시도해 주세요."
private const val SearchFailedError = "검색 중 오류가 발생했습니다."

private val SearchRegionBounds = RectangularBounds.newInstance(
    LatLng(33.0, 124.5),
    LatLng(38.7, 131.9),
)
