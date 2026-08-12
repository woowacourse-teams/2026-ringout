import Shared

final class PlatformServices: IosNativeServices {
    private let mapsAdapter: MapsAdapter
    private let placesAdapter: PlacesAdapter
    private let alarmKitAdapter: AlarmKitAdapter

    init(googleSdkConfiguration: GoogleSdkConfiguration) {
        mapsAdapter = MapsAdapter(configurationState: googleSdkConfiguration.maps)
        placesAdapter = PlacesAdapter(configurationState: googleSdkConfiguration.places)
        alarmKitAdapter = AlarmKitAdapter()
    }

    func isMapsAvailable() -> Bool {
        mapsAdapter.isAvailable
    }

    func isPlacesAvailable() -> Bool {
        placesAdapter.isAvailable
    }

    func alarmAuthorizationState() -> IosAlarmAuthorizationState {
        alarmKitAdapter.authorizationState()
    }

    func normalizeAlarmId(id: String) -> String? {
        alarmKitAdapter.normalizeAlarmId(id)
    }
}
