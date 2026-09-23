import AuthenticationServices
@preconcurrency import Shared
import UIKit

@MainActor
final class AppleSignInAdapter: NSObject, @preconcurrency IosAppleSignInService,
    ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var pendingCallback: IosAppleSignInCallback?
    private var authorizationController: ASAuthorizationController?
    private var presentationWindow: UIWindow?

    func signIn(appleCallback callback: IosAppleSignInCallback) {
        guard pendingCallback == nil else {
            callback.onFailure(message: "Apple 로그인이 이미 진행 중이에요.")
            return
        }
        guard let window = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .filter({ $0.activationState == .foregroundActive })
            .flatMap(\.windows)
            .first(where: \.isKeyWindow) else {
            callback.onFailure(message: "Apple 로그인 화면을 열 수 없어요.")
            return
        }

        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.email]
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        pendingCallback = callback
        presentationWindow = window
        authorizationController = controller
        controller.performRequests()
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        // Retained from signIn until the authorization request completes.
        presentationWindow!
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        guard controller === authorizationController, let callback = takeCallback() else { return }
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let data = credential.identityToken,
              let idToken = String(data: data, encoding: .utf8),
              !idToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            callback.onFailure(message: "Apple 인증 토큰을 받지 못했어요.")
            return
        }
        callback.onSuccess(idToken: idToken)
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        guard controller === authorizationController, let callback = takeCallback() else { return }
        if let authorizationError = error as? ASAuthorizationError,
           authorizationError.code == .canceled {
            callback.onCancelled()
        } else {
            callback.onFailure(message: "Apple 로그인에 실패했어요. 잠시 후 다시 시도해 주세요.")
        }
    }

    private func takeCallback() -> IosAppleSignInCallback? {
        let callback = pendingCallback
        pendingCallback = nil
        authorizationController = nil
        presentationWindow = nil
        return callback
    }
}
