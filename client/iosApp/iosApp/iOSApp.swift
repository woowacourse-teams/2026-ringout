import SwiftUI

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
            // TODO(RINGOUT_ACCOUNT): 소셜 로그인 재도입 시 Google/Kakao URL 콜백을 복구한다.
        }
    }
}
