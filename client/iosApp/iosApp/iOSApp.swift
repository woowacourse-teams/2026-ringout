import SwiftUI

@main
@MainActor
struct iOSApp: App {
    private let platformServices: PlatformServices

    init() {
        platformServices = PlatformServices(
            googleSdkConfiguration: GoogleSdkBootstrap.configure()
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView(nativeServices: platformServices)
        }
    }
}
