import CoreLocation
import GooglePlacesSwift
import Shared

final class PlacesAdapter: IosDestinationSearchService {
    private let configurationState: GoogleSdkComponentState
    private var activeRequestId: Int32?
    private var tasks: [Int32: Task<Void, Never>] = [:]

    init(configurationState: GoogleSdkComponentState) {
        self.configurationState = configurationState
    }

    var isAvailable: Bool {
        configurationState == .configured
    }

    @MainActor
    func makeClient() -> PlacesClient? {
        guard isAvailable else { return nil }
        return PlacesClient.shared
    }

    func search(
        query: String,
        requestId: Int32,
        callback: any IosDestinationSearchCallback
    ) {
        Task { @MainActor in
            startSearch(query: query, requestId: requestId, callback: callback)
        }
    }

    func cancel(requestId: Int32) {
        Task { @MainActor in
            guard activeRequestId == requestId else { return }
            tasks[requestId]?.cancel()
            tasks[requestId] = nil
            activeRequestId = nil
        }
    }

    @MainActor
    private func startSearch(
        query: String,
        requestId: Int32,
        callback: any IosDestinationSearchCallback
    ) {
        tasks.values.forEach { $0.cancel() }
        tasks.removeAll()
        activeRequestId = requestId

        let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedQuery.isEmpty else {
            activeRequestId = nil
            callback.onSuccess(requestId: requestId, places: [])
            return
        }

        guard let client = makeClient(), let koreaRegion = Self.koreaRegion else {
            activeRequestId = nil
            callback.onError(requestId: requestId, error_: .requestDenied)
            return
        }

        let request = SearchByTextRequest(
            textQuery: trimmedQuery,
            placeProperties: [.displayName, .formattedAddress, .coordinate],
            locationRestriction: koreaRegion,
            maxResultCount: 15,
            isOpenNow: false,
            rankPreference: .relevance,
            regionCode: "KR"
        )

        let task = Task { @MainActor in
            let result = await client.responseForSearchByText(with: request)
            tasks[requestId] = nil

            guard activeRequestId == requestId else { return }

            if Task.isCancelled {
                activeRequestId = nil
                callback.onCancelled(requestId: requestId)
                return
            }

            activeRequestId = nil
            switch result {
            case .success(let response):
                callback.onSuccess(
                    requestId: requestId,
                    places: Self.mapPlaces(response.places ?? [])
                )
            case .failure(let error):
                callback.onError(
                    requestId: requestId,
                    error_: Self.mapError(error)
                )
            }
        }
        tasks[requestId] = task
    }

    private static var koreaRegion: RectangularCoordinateRegion? {
        RectangularCoordinateRegion(
            northEast: CLLocationCoordinate2D(latitude: 38.7, longitude: 131.9),
            southWest: CLLocationCoordinate2D(latitude: 33.0, longitude: 124.5)
        )
    }

    private static func mapPlaces(_ places: [Place]) -> [IosDestinationPlace] {
        var seenKeys = Set<String>()
        return places.compactMap { place in
            let coordinate = place.location
            guard coordinate.isValidDestinationCoordinate else { return nil }

            let key = coordinate.destinationDeduplicationKey
            guard seenKeys.insert(key).inserted else { return nil }

            let address = place.formattedAddress.normalizedPlaceText
            let name = place.displayName.normalizedPlaceText
                ?? address
                ?? String(format: "%.5f, %.5f", coordinate.latitude, coordinate.longitude)

            return IosDestinationPlace(
                name: name,
                address: address ?? name,
                latitude: coordinate.latitude,
                longitude: coordinate.longitude
            )
        }
    }

    private static func mapError(_ error: PlacesError) -> IosDestinationSearchError {
        switch error {
        case .network, .server:
            return .network
        case .usageLimitExceeded, .rateLimitExceeded, .deviceRateLimitExceeded:
            return .quotaExceeded
        case .keyInvalid, .keyExpired, .accessNotConfigured, .incorrectBundleIdentifier:
            return .requestDenied
        case .invalidRequest:
            return .invalidRequest
        case .internal, .location:
            return .unknown
        @unknown default:
            return .unknown
        }
    }
}

private extension CLLocationCoordinate2D {
    var isValidDestinationCoordinate: Bool {
        CLLocationCoordinate2DIsValid(self)
            && latitude.isFinite
            && longitude.isFinite
            && abs(latitude) <= 90.0
            && abs(longitude) <= 180.0
            && !(latitude == 0.0 && longitude == 0.0)
    }

    var destinationDeduplicationKey: String {
        "\(Int((latitude / 0.00001).rounded())):\(Int((longitude / 0.00001).rounded()))"
    }
}

private extension Optional where Wrapped == String {
    var normalizedPlaceText: String? {
        guard let value = self?.trimmingCharacters(in: .whitespacesAndNewlines),
              !value.isEmpty
        else {
            return nil
        }
        return value
    }
}
