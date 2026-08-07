package com.joon.ringout.presentation.destination

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

internal class AndroidDestinationReverseGeocoder(context: Context) {
    private val applicationContext = context.applicationContext

    suspend fun resolve(
        latitude: Double,
        longitude: Double,
    ): ResolvedDestinationAddress? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(applicationContext, Locale.KOREA)

        val result = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                resolveAsync(geocoder, latitude, longitude)
            } else {
                resolveBlocking(geocoder, latitude, longitude)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return null

        val address = result.fullAddress() ?: return null
        return ResolvedDestinationAddress(
            placeName = result.featureName?.takeIf(String::isNotBlank),
            address = address,
        )
    }

    private suspend fun resolveAsync(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): Address? = suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocation(
            latitude,
            longitude,
            1,
            object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (continuation.isActive) {
                        continuation.resume(addresses.firstOrNull())
                    }
                }

                override fun onError(errorMessage: String?) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            },
        )
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveBlocking(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): Address? = withContext(Dispatchers.IO) {
        geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
    }
}

private fun Address.fullAddress(): String? =
    getAddressLine(0)?.takeIf(String::isNotBlank)
        ?: listOfNotNull(
            adminArea,
            locality,
            subLocality,
            thoroughfare,
            subThoroughfare,
        ).distinct().joinToString(" ").takeIf(String::isNotBlank)

internal data class ResolvedDestinationAddress(
    val placeName: String?,
    val address: String,
)
