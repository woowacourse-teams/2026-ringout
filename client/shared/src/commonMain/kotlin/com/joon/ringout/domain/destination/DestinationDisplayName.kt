package com.joon.ringout.domain.destination

import kotlin.math.abs

/** 알람에 복사된 목적지는 유지하며, 현재 즐겨찾기에 없는 별명만 주소로 대체한다. */
internal fun resolveDestinationDisplayName(
    name: String,
    address: String,
    latitude: Double,
    longitude: Double,
    savedDestinations: List<SavedDestination>?,
): String {
    // 조회가 끝나기 전에는 빈 즐겨찾기 목록으로 판단하지 않는다.
    if (savedDestinations == null) return name
    val hasSavedAlias = savedDestinations.any { saved ->
        saved.name == name &&
            abs(saved.latitude - latitude) < DestinationAliasCoordinateTolerance &&
            abs(saved.longitude - longitude) < DestinationAliasCoordinateTolerance
    }
    if (hasSavedAlias) return name
    return address.trim().removePrefix("대한민국 ").trimStart().ifBlank { "주소 정보 없음" }
}

private const val DestinationAliasCoordinateTolerance = 0.00001
