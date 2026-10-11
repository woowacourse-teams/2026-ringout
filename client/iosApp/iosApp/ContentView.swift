import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    let nativeServices: IosNativeServices

    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController(nativeServices: nativeServices)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    let nativeServices: IosNativeServices

    var body: some View {
        ComposeView(nativeServices: nativeServices)
            .ignoresSafeArea()
            .onChange(of: scenePhase) { _, phase in
                if phase == .active {
                    try? AlarmRecoveryDriver.journal.recordPresentation(
                        stage: "app_scene_active", mode: "foreground",
                        canContinueInForeground: false,
                        protectedDataAvailable: UIApplication.shared.isProtectedDataAvailable
                    )
                }
            }
    }
}
