import GoogleSignIn
@preconcurrency import Shared
import UIKit

@MainActor
final class GoogleSignInAdapter: @preconcurrency IosGoogleSignInService {
    private let configurationError: String?

    init(bundle: Bundle = .main) {
        let clientID = bundle.object(forInfoDictionaryKey: "GIDClientID") as? String
        let serverClientID = bundle.object(forInfoDictionaryKey: "GIDServerClientID") as? String

        if let clientID, !clientID.isEmpty,
           let serverClientID, !serverClientID.isEmpty {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(
                clientID: clientID,
                serverClientID: serverClientID
            )
            configurationError = nil
        } else {
            configurationError = "Google OAuth Client ID가 설정되지 않았어요."
        }
    }

    func signIn(callback_ callback: IosGoogleSignInCallback) {
        if let configurationError {
            callback.onFailure(message: configurationError)
            return
        }
        guard let presentingViewController = Self.presentingViewController() else {
            callback.onFailure(message: "Google 로그인 화면을 열 수 없어요.")
            return
        }

        GIDSignIn.sharedInstance.signIn(withPresenting: presentingViewController) {
            result,
            error in
            if let error = error as NSError? {
                if error.code == -5 {
                    callback.onCancelled()
                } else {
                    callback.onFailure(message: error.localizedDescription)
                }
                return
            }
            guard let accessToken = result?.user.accessToken.tokenString,
                  !accessToken.isEmpty else {
                callback.onFailure(message: "Google Access Token을 받지 못했어요.")
                return
            }
            callback.onSuccess(accessToken: accessToken)
        }
    }

    private static func presentingViewController() -> UIViewController? {
        let rootViewController = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .rootViewController
        return topViewController(from: rootViewController)
    }

    private static func topViewController(from viewController: UIViewController?) -> UIViewController? {
        if let navigationController = viewController as? UINavigationController {
            return topViewController(from: navigationController.visibleViewController)
        }
        if let tabBarController = viewController as? UITabBarController {
            return topViewController(from: tabBarController.selectedViewController)
        }
        if let presentedViewController = viewController?.presentedViewController {
            return topViewController(from: presentedViewController)
        }
        return viewController
    }
}
