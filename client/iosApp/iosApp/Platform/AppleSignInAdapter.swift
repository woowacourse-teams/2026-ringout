import AuthenticationServices
import Foundation
@preconcurrency import Shared
import UIKit

@MainActor
final class AppleSignInAdapter: NSObject, @preconcurrency IosAppleSignInService {
    private var activeCallback: IosAppleSignInCallback?
    private var activeController: ASAuthorizationController?

    func signIn(callback: IosAppleSignInCallback) {
        guard activeController == nil else {
            callback.onFailure(message: "Apple 로그인이 이미 진행 중이에요.")
            return
        }

        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.email]

        let controller = ASAuthorizationController(authorizationRequests: [request])
        activeCallback = callback
        activeController = controller
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }
}

extension AppleSignInAdapter: ASAuthorizationControllerDelegate {
    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
            complete { callback in
                callback.onFailure(message: "Apple 인증 정보를 확인할 수 없어요.")
            }
            return
        }
        guard let identityToken = credential.identityToken,
              let idToken = String(data: identityToken, encoding: .utf8),
              !idToken.isEmpty else {
            complete { callback in
                callback.onFailure(message: "Apple ID Token을 받지 못했어요.")
            }
            return
        }
        complete { callback in
            callback.onSuccess(idToken: idToken)
        }
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        if let authorizationError = error as? ASAuthorizationError,
           authorizationError.code == .canceled {
            complete { callback in
                callback.onCancelled()
            }
        } else {
            complete { callback in
                callback.onFailure(message: error.localizedDescription)
            }
        }
    }
}

extension AppleSignInAdapter: ASAuthorizationControllerPresentationContextProviding {
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        guard let windowScene = (UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first) else {
            preconditionFailure("Apple 로그인 화면을 표시할 scene이 없어요.")
        }
        return windowScene.windows.first(where: { $0.isKeyWindow }) ?? UIWindow(windowScene: windowScene)
    }
}

private extension AppleSignInAdapter {
    func complete(_ completion: (IosAppleSignInCallback) -> Void) {
        guard let callback = activeCallback else { return }
        activeController = nil
        activeCallback = nil
        completion(callback)
    }
}
