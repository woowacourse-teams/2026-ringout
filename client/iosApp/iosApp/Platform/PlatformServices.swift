import Shared

@MainActor
final class PlatformServices: @preconcurrency IosNativeServices {
    private let mapsAdapter: MapsAdapter
    private let placesAdapter: PlacesAdapter
    private let destinationLocationAdapter: DestinationLocationAdapter
    private let alarmKitAdapter: AlarmKitAdapter
    private let missionLocationAdapter: MissionLocationAdapter
    private let firebaseAnalyticsAdapter: FirebaseAnalyticsAdapter
    // TODO(RINGOUT_ACCOUNT): 로그인 재도입 시 소셜 로그인 어댑터를 다시 연결한다.

    init(googleSdkConfiguration: GoogleSdkConfiguration) {
        mapsAdapter = MapsAdapter(configurationState: googleSdkConfiguration.maps)
        placesAdapter = PlacesAdapter(configurationState: googleSdkConfiguration.places)
        destinationLocationAdapter = DestinationLocationAdapter()
        alarmKitAdapter = AlarmKitAdapter()
        missionLocationAdapter = MissionLocationAdapter()
        firebaseAnalyticsAdapter = FirebaseAnalyticsAdapter()
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
        mapsAdapter.makeDestinationMapController(
            initialLatitude: initialLatitude,
            initialLongitude: initialLongitude,
            listener: listener
        )
    }

    func destinationSearchService() -> IosDestinationSearchService {
        placesAdapter
    }

    func createActiveMissionMapController(
        destinationLatitude: Double,
        destinationLongitude: Double
    ) -> IosActiveMissionMapController? {
        mapsAdapter.makeActiveMissionMapController(
            destinationLatitude: destinationLatitude,
            destinationLongitude: destinationLongitude
        )
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

    func alarmScheduler() -> IosAlarmScheduler {
        alarmKitAdapter
    }

    func alarmMissionEventInbox() -> IosAlarmMissionEventInbox {
        RingoutAlarmMissionEventInbox.shared
    }

    func missionLocationService() -> IosMissionLocationService {
        missionLocationAdapter
    }

    func analyticsTracker() -> IosAnalyticsTracker {
        firebaseAnalyticsAdapter
    }
}
