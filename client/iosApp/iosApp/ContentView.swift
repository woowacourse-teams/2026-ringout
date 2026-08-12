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
    let nativeServices: IosNativeServices

    var body: some View {
        ComposeView(nativeServices: nativeServices)
            .ignoresSafeArea()
    }
}
