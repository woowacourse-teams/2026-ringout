import Shared

final class PlatformServices: IosNativeServices {
    private let mapsAdapter: MapsAdapter
    private let placesAdapter: PlacesAdapter
    private let destinationLocationAdapter: DestinationLocationAdapter
    private let alarmKitAdapter: AlarmKitAdapter

    init(googleSdkConfiguration: GoogleSdkConfiguration) {
        mapsAdapter = MapsAdapter(configurationState: googleSdkConfiguration.maps)
        placesAdapter = PlacesAdapter(configurationState: googleSdkConfiguration.places)
        destinationLocationAdapter = DestinationLocationAdapter()
        alarmKitAdapter = AlarmKitAdapter()
    }

    func isMapsAvailable() -> Bool {
        mapsAdapter.isAvailable
    }

    func isPlacesAvailable() -> Bool {
        placesAdapter.isAvailable
    }

    func createDestinationMapController(
        initialLatitude: Double,
        initialLongitude: Double,
        listener: IosDestinationMapListener
    ) -> IosDestinationMapController? {
        MainActor.assumeIsolated {
            mapsAdapter.makeDestinationMapController(
                initialLatitude: initialLatitude,
                initialLongitude: initialLongitude,
                listener: listener
            )
        }
    }

    func destinationSearchService() -> IosDestinationSearchService {
        placesAdapter
    }

    func destinationLocationService() -> IosDestinationLocationService {
        destinationLocationAdapter
    }

    func alarmAuthorizationState() -> IosAlarmAuthorizationState {
        alarmKitAdapter.authorizationState()
    }

    func normalizeAlarmId(id: String) -> String? {
        alarmKitAdapter.normalizeAlarmId(id)
    }
}
