# main 운영 CD

## 실행 범위

- `main` push에서 Android 및 iOS 운영 업로드를 실행한다. main에는 develop PR만 병합하는 기존 정책을 유지하고 직접 push는 브랜치 보호로 제한한다.
- Android는 동일 SHA의 `Build Signed Release AAB` 성공 결과를 다운로드·검증하고 Google Play `production` **draft**를 만든다. 새 릴리스는 자동 공개하지 않는다. 앱 전체 변경 사항의 심사·게시 상태는 Play Console에서 확인한다.
- iOS는 동일 SHA의 `iOS CI` 성공 후 운영 Archive를 생성하여 App Store Connect에 업로드한다. 내부 TestFlight 전용 제한이 없는 빌드이며, Apple 처리 결과 확인·심사 제출·공개는 수동이다.
- Android는 client/ 및 관련 Android workflow 변경에 반응한다. producer/consumer 경로 필터를 함께 유지해야 gate 대기가 발생하지 않는다. iOS는 main/develop push마다 CI를 실행한다.
- develop의 기존 내부 Play / 내부 TestFlight 배포는 유지한다.

## 빌드 입력과 버전

API 주소는 빌드 환경변수 `RINGOUT_API_BASE_URL`로 전달한다. Gradle `:shared:generateApiConfig`가 공통 Kotlin 설정을 `shared/build/generated/apiConfig/`에 생성하고 Android/iOS가 함께 사용한다. `ApiConfig.kt` 소스를 CI에서 치환하지 않는다. 입력값 변경은 Gradle task 입력으로 추적하므로 재빌드 시 갱신된다.

GitHub Environment variables에 다음 값을 지정할 수 있다(Secret이 아닌 Variable). 등록하지 않으면 워크플로의 동일한 기본값을 사용한다.

| Environment | `RINGOUT_API_BASE_URL` |
| --- | --- |
| `internal`, `ios-internal` | `https://dev-api.ringout.my` |
| `production`, `ios-production` | `https://api.ringout.my` |

운영 준비 및 생성 설정 검증은 운영 주소 외의 값이나 누락을 거부한다. iOS의 Xcode → Gradle 실행에도 환경변수가 전달된다. 앱 실행 시 환경변수를 조회하는 방식이 아니므로 주소를 바꾸면 앱을 다시 빌드해야 한다.

로컬 빌드는 환경변수를 지정하지 않으면 개발 주소를 사용한다. 예:

```bash
RINGOUT_API_BASE_URL=https://api.ringout.my ./gradlew :androidApp:assembleDebug
RINGOUT_API_BASE_URL=https://api.ringout.my xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Xcode Run Scheme의 런타임 환경변수는 빌드 설정 주입을 대신하지 않는다. 위처럼 빌드 프로세스에 환경변수를 전달한다.

`ci/production_config.py`는 CI의 임시 checkout에서 커밋된 원본(`git show HEAD:...`)을 기준으로 운영 빌드 번호만 준비한다. 로컬 소스의 버전은 변경하지 않는다.

| 항목 | develop | main |
| --- | --- | --- |
| API | 기존 개발 설정 | `https://api.ringout.my` |
| Firebase | `ringout-8abf2` | `ringout-prod` |
| Android versionCode | 커밋된 값 N | N + 1 |
| iOS CURRENT_PROJECT_VERSION | 커밋된 값 N | N + 1 |
| 표시 버전 | 커밋된 versionName / MARKETING_VERSION | 같은 값 |

두 플랫폼이 같은 숫자를 쓸 필요는 없지만, 각 플랫폼은 내부 배포와 운영 배포에 번호 두 개를 예약한다. **다음 릴리스의 소스 번호는 이전 운영 번호보다 커야 한다(최소 N + 2).** 예: 100(내부) → 101(운영) → 102(다음 내부) → 103(다음 운영). 커밋 버전은 담당자가 릴리스 전에 올린다. 기존에 스토어에 올린 번호보다 반드시 커야 한다.

같은 커밋을 재실행하면 같은 번호를 사용한다. 준비 단계를 반복해도 두 번 증가하지 않는다. Android는 2,100,000,000 범위를 검사한다. 운영 준비가 적용된 빌드 설정과 실제 앱 버전이 일치하는지 확인한다.

## 사전 설정

### Android: GitHub Environment `production`

기존 서명 AAB용 secrets:

- `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`
- `GOOGLE_SERVICES_JSON_BASE64`: 운영 Firebase의 `com.joon.ringout` 설정
- `MAPS_API_KEY`, `KAKAO_NATIVE_APP_KEY`

Play 업로드용 variables(또는 secrets):

- `GCP_WORKLOAD_IDENTITY_PROVIDER`
- `GCP_SERVICE_ACCOUNT`

서비스 계정은 Play Console에서 이 앱의 운영 초안 작성에 필요한 권한을 가져야 한다. Google Cloud OIDC 신뢰 조건은 해당 저장소의 production Environment 실행만 허용하도록 운영자가 설정한다. 권한 확대 및 인증 자산 등록은 이 코드 변경으로 자동 수행하지 않는다.

### iOS: GitHub Environment `ios-production`

다음 secrets를 등록한다.

- `APPLE_CERTIFICATE_P12_BASE64`, `APPLE_CERTIFICATE_PASSWORD`
- `APPLE_PROVISIONING_PROFILE_BASE64`: `475GWW72JW.com.joon.ringout.Ringout`의 App Store 배포 프로파일
- `APP_STORE_CONNECT_API_KEY_BASE64`, `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID`
- `RINGOUT_IOS_SECRETS_XCCONFIG_BASE64`: 운영 Google 로그인·지도·Kakao 설정
- `GOOGLE_SERVICE_INFO_PLIST_BASE64`: `ringout-prod` 프로젝트의 해당 iOS 앱 설정

Firebase 설정은 빌드 전에 복원하고 프로젝트·번들 ID를 검사한다. Archive의 Firebase 필드가 복원한 설정과 같은지도 검사한다. xcconfig의 OAuth client 설정은 운영 서비스와 일치하게 준비해야 한다. 종료 시 임시 인증서·프로파일·키체인·API 키·xcconfig·Firebase 파일을 제거한다.

2026-10-06 저장소 확인 결과: production의 기존 Android 서명 secrets는 있으나 Play OIDC 두 항목은 없고, ios-production Environment는 아직 없다. 이름 존재만 확인했으며 secret 내용·스토어 권한은 검증하지 않았다.

## 실패 및 재실행

1. CI 또는 AAB 빌드 실패: 해당 main SHA의 선행 실행을 먼저 재실행하여 성공시킨 후 CD를 `Re-run jobs`로 재실행한다. 복구 시 SHA를 바꾸지 않는다.
2. Android 업로드 실패: 동일 AAB가 존재하면 versionCode와 SHA-256을 비교해 재사용한다. 다른 바이트의 동일 버전은 실패하며 자동 덮어쓰기하지 않는다. CD publish job만 재실행하면 원래 AAB를 재사용할 수 있다. AAB producer를 다시 빌드해 다른 바이트가 생성된 경우 새 소스 빌드 번호가 필요하다.
3. Android 기존 운영 초안/진행 중 출시: 다른 버전의 draft/inProgress/halted가 있으면 중단한다. Console에서 해당 릴리스를 처리한 후 재실행한다. 이미 해당 버전이 배포된 경우 상태를 draft로 되돌리지 않는다.
4. iOS 업로드 중 실패: 먼저 App Store Connect에서 해당 표시 버전·빌드 번호의 존재와 처리 상태를 확인한다. 이미 수신된 빌드는 재업로드하지 않고 후속 작업을 Console에서 수행한다. Apple에 없는 빌드만 재실행한다. 같은 번호가 이미 존재하면 Xcode 오류로 중단하며, 자동 성공으로 간주하거나 번호를 몰래 변경하지 않는다. 새 바이너리가 필요하면 소스 빌드 번호를 올린다.
5. 두 플랫폼은 별도 workflow이므로 실패한 플랫폼만 재실행한다. 운영 업로드는 동시 실행을 취소하지 않고 직렬화한다. 빠르게 여러 push가 발생하면 GitHub concurrency의 대기 실행 교체 정책이 적용될 수 있으므로 각 배포 SHA의 실행 여부를 확인한다.
6. 버전/설정이 잘못된 빌드는 배포하지 말고 수정 커밋으로 새 번호를 만든다. `workflow_dispatch`로 production을 우회하지 않고 원래 push 실행의 재실행을 사용한다.

## 검증

- `python3 -B -m unittest discover -s ci -p 'test_*.py' -v`
- `actionlint -shellcheck= -pyflakes= .github/workflows/{android-ci,android-internal-cd,android-production-cd,build-release-aab,ios-ci,ios-production-cd}.yml` (저장소 루트)
- main 병합 전 사전 설정을 완료한다. 병합 후 Actions summary의 SHA·버전과 Play Console / App Store Connect의 실제 업로드를 대조한다. 로컬 테스트만으로 실제 스토어 업로드 성공을 판단하지 않는다.

## 참고

- [Google Play draft releases](https://developers.google.com/android-publisher/tracks)
- [Google Play edits.commit](https://developers.google.com/android-publisher/api-ref/rest/v3/edits/commit)
- [Google Play bundle SHA-256](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.bundles)
- [Apple 빌드 업로드](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds/)

## Android commit 옵션

운영 draft 저장 시 `changesNotSentForReview`는 지정하지 않는다. 자동 심사 상태의 앱에서는 해당 옵션이 HTTP 400으로 거부된다. `changesInReviewBehavior=ERROR_IF_IN_REVIEW`를 지정하고 요청 본문은 생략한다.

진행 중인 심사가 있으면 CD는 실패하며, 보호 옵션을 제거해 재시도하거나 기존 심사를 취소하지 않는다. Play Console에서 심사 완료를 확인한 뒤 CD를 재실행한다. 새 릴리스의 `status=draft`는 유지하지만 앱 전체 변경 사항의 자동 심사 여부까지 차단하는 옵션은 아니므로, 업로드 후 심사·게시 상태를 확인한다.

## 클라이언트 변경 경로 필터

iOS CI의 develop/main push와 내부·운영 CD는 동일한 경로 목록을 사용한다. `client/iosApp/**`, `client/shared/**`, `client/ci/**`, Gradle 설정 및 세 iOS 워크플로 변경 시 실행한다. 서버 코드·서버 전용 워크플로만 변경한 push는 실행하지 않는다. PR의 기존 iOS 경로 필터도 유지한다.

경로 목록을 수정할 때는 세 워크플로를 함께 수정해야 한다. CI는 생략됐는데 CD만 시작되면 동일 SHA의 선행 CI를 기다리다 시간 초과될 수 있다. iOS 워크플로는 현재 저장소의 로컬 공통 action을 사용하지 않는다. 이후 로컬 action을 도입하면 해당 경로를 세 필터에 함께 추가한다.

Android는 기존 경로 필터를 유지한다. 서버·클라이언트 혼합 변경은 관련 클라이언트 파일을 포함하므로 필요한 파이프라인이 실행된다. main 적용에는 develop → main 병합이 필요하다. 추후 필수 상태 검사를 설정할 때는 경로 필터로 생략된 워크플로가 PR 병합을 차단하지 않는지 함께 확인한다.
