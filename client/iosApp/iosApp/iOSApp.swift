import SwiftUI
import GoogleSignIn
import KakaoSDKAuth

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
                    if AuthApi.isKakaoTalkLoginUrl(url) {
                        _ = AuthController.handleOpenUrl(url: url)
                    } else {
                        GIDSignIn.sharedInstance.handle(url)
                    }
                }
        }
    }
}
