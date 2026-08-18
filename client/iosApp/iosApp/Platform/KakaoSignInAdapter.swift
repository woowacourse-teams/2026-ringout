import KakaoSDKCommon
import KakaoSDKAuth
import KakaoSDKUser
@preconcurrency import Shared

@MainActor
final class KakaoSignInAdapter: @preconcurrency IosKakaoSignInService {
    private let configurationError: String?

    init(bundle: Bundle = .main) {
        let nativeAppKey = bundle.object(forInfoDictionaryKey: "KakaoNativeAppKey") as? String
        if let nativeAppKey, !nativeAppKey.isEmpty {
            KakaoSDK.initSDK(appKey: nativeAppKey)
            configurationError = nil
        } else {
            configurationError = "카카오 네이티브 앱 키가 설정되지 않았어요."
        }
    }

    func signIn(callback_ callback: IosKakaoSignInCallback) {
        if let configurationError {
            callback.onFailure(message: configurationError)
            return
        }

        if UserApi.isKakaoTalkLoginAvailable() {
            UserApi.shared.loginWithKakaoTalk(launchMethod: .CustomScheme) { [weak self] token, error in
                guard let self else { return }
                if Self.isCancellation(error) {
                    callback.onCancelled()
                } else if error != nil {
                    self.signInWithKakaoAccount(callback: callback)
                } else {
                    Self.complete(token: token, error: error, callback: callback)
                }
            }
        } else {
            signInWithKakaoAccount(callback: callback)
        }
    }

    private func signInWithKakaoAccount(callback: IosKakaoSignInCallback) {
        UserApi.shared.loginWithKakaoAccount { token, error in
            Self.complete(token: token, error: error, callback: callback)
        }
    }

    private static func complete(
        token: OAuthToken?,
        error: Error?,
        callback: IosKakaoSignInCallback
    ) {
        if isCancellation(error) {
            callback.onCancelled()
        } else if let error {
            callback.onFailure(message: error.localizedDescription)
        } else if let accessToken = token?.accessToken, !accessToken.isEmpty {
            callback.onSuccess(accessToken: accessToken)
        } else {
            callback.onFailure(message: "카카오 Access Token을 받지 못했어요.")
        }
    }

    private static func isCancellation(_ error: Error?) -> Bool {
        guard let sdkError = error as? SdkError else { return false }
        switch sdkError {
        case .ClientFailed(reason: .Cancelled, errorMessage: _),
             .AuthFailed(reason: .AccessDenied, errorInfo: _):
            return true
        default:
            return false
        }
    }
}
