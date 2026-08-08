package com.joon.ringout.presentation.destination

import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.PlacesStatusCodes
import kotlin.test.Test
import kotlin.test.assertEquals

class GooglePlacesDestinationSearchTest {
    @Test
    fun textSearchRequestUsesGooglePlacesForKoreanDestinationResults() {
        val request = buildGooglePlacesDestinationSearchRequest(
            query = "수원역",
            cancellationToken = CancellationTokenSource().token,
        )

        assertEquals("수원역", request.textQuery)
        assertEquals(
            listOf(
                Place.Field.DISPLAY_NAME,
                Place.Field.FORMATTED_ADDRESS,
                Place.Field.LOCATION,
            ),
            request.placeFields,
        )
        assertEquals("KR", request.regionCode)
        assertEquals(15, request.maxResultCount)

        val bounds = request.locationRestriction as RectangularBounds
        assertEquals(33.0, bounds.southwest.latitude)
        assertEquals(124.5, bounds.southwest.longitude)
        assertEquals(38.7, bounds.northeast.latitude)
        assertEquals(131.9, bounds.northeast.longitude)
    }

    @Test
    fun googlePlacesStatusCodesAreMappedToActionableFailures() {
        assertEquals(
            GooglePlacesSearchFailure.Network,
            googlePlacesSearchFailure(CommonStatusCodes.NETWORK_ERROR),
        )
        assertEquals(
            GooglePlacesSearchFailure.QuotaExceeded,
            googlePlacesSearchFailure(PlacesStatusCodes.OVER_QUERY_LIMIT),
        )
        assertEquals(
            GooglePlacesSearchFailure.RequestDenied,
            googlePlacesSearchFailure(PlacesStatusCodes.REQUEST_DENIED),
        )
        assertEquals(
            GooglePlacesSearchFailure.InvalidRequest,
            googlePlacesSearchFailure(PlacesStatusCodes.INVALID_REQUEST),
        )
        assertEquals(
            GooglePlacesSearchFailure.Unknown,
            googlePlacesSearchFailure(null),
        )
    }
}
