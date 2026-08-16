import SwiftUI
import GoogleSignIn

@main
@MainActor
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self)
    private var appDelegate

    private let platformServices: PlatformServices

    init() {
        platformServices = PlatformServices(
            googleSdkConfiguration: GoogleSdkBootstrap.configure()
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView(nativeServices: platformServices)
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
