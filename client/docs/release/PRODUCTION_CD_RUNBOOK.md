# main 운영 CD

## 실행 범위

- 클라이언트 경로는 작업 브랜치 → `client/develop` → `client/release` → `main`이다. `client/release` PR은 같은 저장소의 `client/develop`에서, `main` PR은 같은 저장소의 `client/release`에서만 허용한다. 서버의 기존 `develop`/`main` 흐름은 유지한다.
- 각 단계 PR과 세 기준 브랜치 push에서 변경 경로에 해당하는 플랫폼 테스트·Lint·Swift 저널 테스트 또는 Python 보조·산출물 검사 회귀 테스트를 수행한다. 플랫폼 CI는 변경 감지·최종 판정을 포함한다. 경량 CI에서는 서명 없는 AAB나 Xcode 시뮬레이터 앱을 빌드하지 않는다.
- `client/develop` push는 경량 CI만 실행한다. `client/release` push는 변경에 필요한 동일 SHA의 CI가 성공한 후 해당 플랫폼의 내부 AAB·iOS Archive를 만들고 Play 내부 테스트·TestFlight에 업로드한다.
- `main` push의 운영 빌드도 변경에 필요한 동일 SHA의 CI 성공만 요구한다. Android는 `Build Signed Release AAB` 성공 결과를 다운로드·검증하고 Google Play `production` **draft**를 만든다. 새 릴리스는 자동 공개하지 않는다. 앱 전체 변경 사항의 심사·게시 상태는 Play Console에서 확인한다.
- iOS는 운영 Archive를 생성하여 App Store Connect에 업로드한다. 내부 TestFlight 전용 제한이 없는 빌드이며, Apple 처리 결과 확인·심사 제출·공개는 수동이다.
- PR 병합, 직접 push 제한, 원격 브랜치 생성과 보호 규칙은 저장소 관리자가 설정한다. 워크플로 변경이 이러한 GitHub 설정을 자동 적용하지 않는다.

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

| 항목 | client/release | main |
| --- | --- | --- |
| API | 기존 개발 설정 | `https://api.ringout.my` |
| Firebase | `ringout-8abf2` | `ringout-prod` |
| Android versionCode | 커밋된 값 N | N + 1 |
| iOS CURRENT_PROJECT_VERSION | 커밋된 값 N | N + 1 |
| 표시 버전 | 커밋된 versionName / MARKETING_VERSION | 같은 값 |

두 플랫폼이 같은 숫자를 쓸 필요는 없지만, 각 플랫폼은 내부 배포와 운영 배포에 번호 두 개를 예약한다. **다음 릴리스의 소스 번호는 이전 운영 번호보다 커야 한다(최소 N + 2).** 예: 100(내부) → 101(운영) → 102(다음 내부) → 103(다음 운영). 커밋 버전은 담당자가 릴리스 전에 올린다. 기존에 스토어에 올린 번호보다 반드시 커야 한다.

같은 커밋을 재실행하면 같은 번호를 사용한다. 준비 단계를 반복해도 두 번 증가하지 않는다. Android는 2,100,000,000 범위를 검사한다. 운영 준비가 적용된 빌드 설정과 실제 앱 버전이 일치하는지 확인한다.

## 사전 설정

GitHub Environment의 Deployment branches and tags는 `internal`·`ios-internal`에 `client/release`만, `production`·`ios-production`에 `main`만 허용한다. 기존 내부 환경이 `develop`으로 제한되어 있다면 저장소 관리자가 `client/release`로 변경해야 한다. OIDC의 ref 제한을 별도로 사용하는 경우에도 같은 브랜치 경로를 반영한다. 클라이언트 브랜치와 보호 규칙, 환경 및 인증 설정은 이 변경으로 원격에 자동 적용되지 않는다.

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
- `actionlint -shellcheck= -pyflakes= .github/workflows/{client-pr-route,client-python-ci,android-ci,android-internal-cd,android-production-cd,build-release-aab,ios-ci,ios-internal-cd,ios-production-cd}.yml` (저장소 루트)
- main 병합 전 사전 설정을 완료한다. 병합 후 Actions summary의 SHA·버전과 Play Console / App Store Connect의 실제 업로드를 대조한다. 로컬 테스트만으로 실제 스토어 업로드 성공을 판단하지 않는다.

## 참고

- [Google Play draft releases](https://developers.google.com/android-publisher/tracks)
- [Google Play edits.commit](https://developers.google.com/android-publisher/api-ref/rest/v3/edits/commit)
- [Google Play bundle SHA-256](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.bundles)
- [Apple 빌드 업로드](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds/)

## Android commit 옵션

운영 draft 저장 시 `changesNotSentForReview`는 지정하지 않는다. 자동 심사 상태의 앱에서는 해당 옵션이 HTTP 400으로 거부된다. `changesInReviewBehavior=ERROR_IF_IN_REVIEW`를 지정하고 요청 본문은 생략한다.

진행 중인 심사가 있으면 CD는 실패하며, 보호 옵션을 제거해 재시도하거나 기존 심사를 취소하지 않는다. Play Console에서 심사 완료를 확인한 뒤 CD를 재실행한다. 새 릴리스의 `status=draft`는 유지하지만 앱 전체 변경 사항의 자동 심사 여부까지 차단하는 옵션은 아니므로, 업로드 후 심사·게시 상태를 확인한다.

## 공통 Python 검증

`Client Python CI` (`client-python-ci.yml`)의 `python-checks` job이 Ubuntu·JDK 21 환경에서 `ci/test_*.py` 전체를 관련 입력이 변경된 PR·push마다 한 번 실행한다. 입력은 Python 보조 코드·검증 설정·테스트 fixture, Kotlin 프레임워크 빌드 스크립트, 검증 대상 클라이언트 워크플로와 공통 Actions이다. 공유 Kotlin 앱 코드만 바뀌면 실행하지 않는다. Android CI·iOS CI·서명 AAB 빌드는 전체 Python 테스트를 다시 실행하지 않는다. 현재 모든 Python 테스트는 이 환경에서 실행 가능하며, Apple `security`와 Gradle 호출은 모의 구현으로 검증하므로 macOS 전용 Python 테스트는 없다. Swift 알람 복구 저널과 Kotlin 플랫폼 테스트는 각 플랫폼 CI에서 실행한다. 서명 빌드와 업로드는 해당 push에 Python 검증 대상 변경이 있을 때만 같은 SHA의 공통 검증 성공도 요구한다.

## 클라이언트 변경 경로 필터

변경 경로 정책의 기준은 `ci/change_policy.py`의 `CI_PATHS`·`CD_PATHS`이다. 회귀 테스트가 정책과 YAML 필터의 일치를 검사한다. Android AAB 생성과 내부·운영 배포는 같은 Android CD 경로를 사용한다.

| 변경 | 필요한 CI | `client/release`·`main` 배포 |
| --- | --- | --- |
| Android 전용 앱 코드 | Android | Android |
| iOS 전용 앱 코드 | iOS | iOS |
| 공유 운영 Kotlin·공통 빌드 설정 | Android + iOS | 양쪽 플랫폼 |
| 공유 `commonTest` | Android + iOS | 없음 |
| Python 테스트·fixture | Python | 없음 |
| 배포 보조 Python 코드 | Python | 영향을 받는 플랫폼 |
| Android/iOS CI 워크플로 | 해당 플랫폼 + Python | 없음 |
| CD 워크플로 | Python | 해당 플랫폼·현재 브랜치의 배포 설정인 경우 |
| `ci/google-services.ci.json` | Android + Python | 없음 |
| 문서·서버 코드·서버 전용 워크플로 | 클라이언트 CI 없음 | 없음 |

여러 종류의 파일을 함께 변경하면 필요한 검사를 합친다. 공통 앱 변경도 Python 입력이 함께 바뀌지 않으면 Python CI를 요구하지 않는다. 테스트 전용 변경·CI 전용 설정·문서 변경만으로는 앱을 배포하지 않는다. Swift 저널 테스트 변경은 iOS CI만 실행한다. 플랫폼 CI는 해당 플랫폼 앱·공유 앱·빌드 설정·관련 CI 설정을 감지하며 반대 플랫폼 소스는 제외한다.

CD gate는 전체 Git 이력을 checkout하여 실제 push의 `before`·`after` 범위를 검사한다. 여러 커밋, 삭제·이동 및 강제 push도 이 범위로 판단하며 마지막 커밋만 검사하지 않는다. 최초 브랜치 push는 push된 가장 오래된 커밋의 첫 부모를 기준으로 한다. 필요한 검사만 같은 저장소·브랜치·SHA의 `push` 실행 성공을 확인한다. 불필요한 검사는 기다리지 않으며, 필수 실행의 누락은 시간 초과로, 실패·취소는 오류로 배포를 막는다. 현재 브랜치에 해당하지 않는 내부·운영 CD 설정만 바뀌면 gate가 배포를 생략할 수 있다.

서명 AAB의 수동 실행은 복구용이며 빌드를 강제한다. 변경 범위는 선택한 SHA와 첫 부모 사이이고, 필요한 동일 SHA의 push CI가 없는 경우에도 배포를 허용하지 않는다. 운영 업로드 복구는 원래 push 실행을 재실행한다.

PR 대상 브랜치를 수정하면 경로 규칙과 변경 경로에 해당하는 CI를 다시 검사한다. 제목·본문만 수정하면 `Client PR route`에서 브랜치 규칙을 검사하고 세 CI의 검증 job은 생략한다. 해당 실행은 별도 concurrency 그룹을 사용하므로 진행 중인 검사를 취소하지 않는다. 보호 규칙에 경로 필터가 있는 워크플로를 일괄 필수 검사로 지정할 때는 서버 PR에서 생략된 체크가 `Pending`으로 남아 병합을 막지 않는지 확인한다. 원격 보호 규칙 설정은 저장소 관리자의 적용 작업이다.
