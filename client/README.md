# RingOut 클라이언트

Android와 iOS를 지원하는 Kotlin Multiplatform 프로젝트입니다. 공통 코드와 테스트는 `shared` 모듈에서 관리합니다.

## 프로젝트 구조

| 경로 | 역할 |
| --- | --- |
| `androidApp` | Android 앱 진입점, Manifest, 앱 설정 및 릴리스 서명 |
| `iosApp` | Xcode 프로젝트와 iOS 앱 진입점 |
| `shared/src/commonMain` | 플랫폼 공통 코드 |
| `shared/src/androidMain`, `shared/src/iosMain` | 플랫폼 전용 구현 |
| `shared/src/commonTest` | 플랫폼 공통 테스트 |
| `shared/src/androidHostTest` | Android 호스트 JVM 테스트 |
| `shared/src/iosTest` | iOS 테스트 |
| `ci` | CI 보조 스크립트, 회귀 테스트, 검증 전용 Firebase 설정, 승인 인증서 지문 |

화면과 컴포넌트 작성 규칙은 [AGENTS.md](./AGENTS.md)를 따릅니다.

## 개발 환경과 앱 실행

아래 명령은 별도 설명이 없으면 저장소의 `client` 디렉터리에서 실행합니다.

- JDK: **Amazon Corretto 21**. Actions와 `gradle/gradle-daemon-jvm.properties`가 같은 기준을 사용합니다.
- Gradle: 저장소의 `./gradlew`를 사용합니다.
- Android SDK: Android 36 플랫폼과 Build Tools 36.0.0.
- iOS: macOS, Xcode 및 호환되는 시뮬레이터. 현재 iOS 앱의 최소 버전은 26.0입니다.
- CI 보조 스크립트: Python 3 표준 라이브러리만 사용합니다.

### Android 앱

Android Studio에서 `client`를 열고 `androidApp` 실행 구성을 사용합니다. 로컬 디버그 APK 빌드 명령은 다음과 같습니다.

현재 앱 버전은 비로그인 전용이며 계정 또는 OAuth 설정이 필요하지 않습니다.

```bash
./gradlew :androidApp:assembleDebug
```

지도·Firebase 기능을 실제로 실행하려면 팀에서 받은 설정이 필요합니다.

| 설정 | 로컬 지정 방법 |
| --- | --- |
| Android SDK 경로 | `local.properties`의 `sdk.dir` |
| Maps API 키 | `local.properties` 또는 환경변수 `MAPS_API_KEY` |
| Firebase Android 설정 | `androidApp/google-services.json` 또는 환경변수 `GOOGLE_SERVICES_JSON_PATH`로 지정한 파일 |

`local.properties`와 실제 Firebase 설정 파일은 Git에 추가하지 않습니다. 검증용 설정으로 만든 앱은 실제 기능 확인이나 배포에 사용하지 않습니다.

### iOS 앱

`iosApp/iosApp.xcodeproj`를 Xcode에서 열어 실행합니다. 팀에서 제공하는 iOS 설정은 `iosApp/Configuration/RingoutSecrets.xcconfig`에 두며, 이 파일도 Git에 추가하지 않습니다.

## 테스트와 정적 분석의 실행 범위

### Android 호스트 테스트

```bash
./gradlew :shared:testAndroidHostTest
```

이 태스크는 **`commonTest`와 `androidHostTest`를 함께 실행**합니다. 이름에 Android가 포함되어 있지만 Android 전용 테스트만 실행하는 명령은 아닙니다. 공통 테스트를 별도 태스크로 분리하거나 같은 테스트를 두 번 실행하지 않습니다.

| 포함하는 범위 | 포함하지 않는 범위 |
| --- | --- |
| `commonTest`의 Android 호스트 JVM 실행 | iOS 런타임에서의 공통 코드 동작 |
| `androidHostTest`의 Android 호스트 테스트 | `iosTest` |
| 테스트가 호출하는 공통 코드와 Android 구현 | 실기기·에뮬레이터 계측 테스트, 앱 전체 UI/E2E 테스트 |

테스트가 통과해도 테스트 케이스가 없는 공통 코드까지 검증됐다는 뜻은 아닙니다. 이 명령은 `shared` 모듈 대상이며, 다른 모듈에 추가한 테스트가 자동으로 포함되지는 않습니다.

- HTML 보고서: `shared/build/reports/tests/testAndroidHostTest/index.html`
- JUnit XML: `shared/build/test-results/testAndroidHostTest/`

캐시 결과를 사용하지 않고 테스트를 다시 실행하려면 다음 명령을 사용합니다.

```bash
./gradlew :shared:cleanTestAndroidHostTest :shared:testAndroidHostTest --no-build-cache
```

### iOS 테스트

Apple Silicon Mac에서 공통 테스트와 iOS 테스트를 실행합니다.

```bash
./gradlew :shared:iosSimulatorArm64Test
```

Android CI에는 이 태스크를 포함하지 않습니다. `client/develop`, `client/release`, `main` 대상 클라이언트 PR과 해당 브랜치 push에서 별도의 iOS CI가 변경 감지와 최종 결과 판정을 수행합니다.

iOS CI는 macOS 26 러너에서 공유 Kotlin iOS 테스트와 Swift 알람 복구 저널 테스트를 실행합니다. 앱 빌드 검증은 별도 `iOS PR Build` 워크플로가 `client/release`·`main` 대상 PR에서 수행합니다. `client/develop` 대상 PR에는 빌드 워크플로와 빌드 체크가 생성되지 않습니다. iOS 앱·공유 Kotlin·공통 빌드 설정·iOS CI 및 PR Build 설정·Swift 저널 테스트 변경이 있을 때 실행합니다. 두 플랫폼 CI의 실행 여부와 결과를 제어하는 `ci/pipeline.py`·`ci/change_policy.py` 변경도 양쪽 플랫폼 테스트를 실행합니다. Android 전용 변경과 문서 변경은 iOS CI를 시작하지 않습니다. 실행된 워크플로에서는 변경 감지와 최종 `iOS CI` 판정을 수행합니다.

`client/release` 푸시에서는 별도 `iOS Internal TestFlight CD`가 해당 push 변경에 필요한 동일 SHA의 CI 성공을 확인한 뒤 개발 Firebase로 서명 아카이브를 만들고, Firebase·서명·버전을 검증한 다음 TestFlight 내부 테스트 전용 빌드로 업로드합니다. 필수 CI가 실패하거나 취소되면 배포하지 않습니다.

TestFlight CD 작업은 GitHub Environment `ios-internal`을 사용하며, 이 환경은 `client/release` 브랜치만 허용해야 합니다. CI 작업에는 Apple 배포 시크릿을 제공하지 않습니다. 다음 Environment secrets가 CD에 필요합니다.

| Secret | 내용 |
| --- | --- |
| `APPLE_CERTIFICATE_P12_BASE64` | Apple Distribution `.p12`의 Base64 |
| `APPLE_CERTIFICATE_PASSWORD` | `.p12` 암호 |
| `APPLE_PROVISIONING_PROFILE_BASE64` | App Store Connect 배포용 `.mobileprovision`의 Base64 |
| `APP_STORE_CONNECT_API_KEY_BASE64` | App Store Connect API `AuthKey_*.p8`의 Base64 |
| `APP_STORE_CONNECT_KEY_ID` | API Key ID |
| `APP_STORE_CONNECT_ISSUER_ID` | API Issuer ID |
| `RINGOUT_IOS_SECRETS_XCCONFIG_BASE64` | 개발 환경용 `iosApp/Configuration/RingoutSecrets.xcconfig`의 Base64 |

마지막 xcconfig Secret에는 로그인·지도 기능의 앱 설정이 들어갑니다. 누락되거나 값이 비어 있으면 배포 아카이브를 만들기 전에 실패합니다. TestFlight 내부 그룹의 자동 배포도 App Store Connect에서 켜야 팀원에게 빌드가 전달됩니다. iOS 버전명(`MARKETING_VERSION`)과 빌드 번호(`CURRENT_PROJECT_VERSION`)는 개발자가 `iosApp/iosApp.xcodeproj/project.pbxproj`에서 지정합니다. 내부 CD는 커밋된 버전을 사용하며, 운영 CD는 커밋된 빌드 번호 N에 1을 더한 번호를 준비합니다. Release 설정과 아카이브의 `Info.plist`가 일치하는지도 확인합니다. 업로드할 때는 App Store Connect에서 이미 사용한 빌드 번호보다 높게 지정해야 합니다. 같은 커밋을 재실행하거나 버전을 올리지 않고 다시 업로드하면 App Store Connect에서 중복 번호로 거절할 수 있습니다.

CD는 `client/release` 푸시 직후 별도 실행으로 시작해 GitHub Actions API에서 해당 push에 필요한 동일 커밋의 CI 성공만 기다립니다. iOS 전용 앱 변경은 `iOS CI`, 공통 앱 변경은 양쪽 플랫폼 CI가 필요하며, Python 입력도 변경된 경우에만 `Client Python CI`를 추가로 요구합니다. CD 실패 원인은 `iOS Internal TestFlight CD` 실행 기록에서 확인합니다. CI 실패 후 수정 없이 CI만 재실행해 성공했다면 CD 실행도 **Re-run all jobs**로 다시 시작할 수 있습니다. 업로드 단계 실패 후 재실행할 때는 App Store Connect의 Build Uploads에서 해당 버전·빌드 번호가 이미 접수되었는지 먼저 확인합니다.

### Android Lint

```bash
./gradlew :androidApp:lintRelease -PciVerification=true
```

`lintRelease`는 **Android Lint**입니다. Android 앱의 release 변형을 대상으로 Manifest, 리소스, API 사용 등의 정적 문제를 검사합니다. Kotlin 코드 스타일 도구인 **ktlint가 아니며**, 이번 CI에서는 ktlint를 도입하지 않습니다. 공통 Kotlin 소스 전체의 코드 스타일 검사로 해석하지 않습니다.

보고서는 `androidApp/build/reports/lint-results-release.html`과 같은 디렉터리의 XML·TXT 파일에서 확인합니다. Lint 오류는 CI를 실패시키며, 경고는 보고서에 남깁니다.

## PR 검증 모드

클라이언트 PR과 `client/develop`·`client/release`·`main` push의 Android CI는 서명 키와 실제 서비스 설정 없이 다음 검사를 수행합니다.

```bash
./gradlew :shared:testAndroidHostTest :androidApp:lintRelease \
  -PciVerification=true --no-daemon --no-configuration-cache --profile
```

`-PciVerification=true`는 Maps·카카오 키를 검증용 값으로 고정하고 Firebase에 `ci/google-services.ci.json`을 사용합니다. 업로드 서명과 Crashlytics mapping 업로드를 끕니다. 실제 로컬 설정 파일은 덮어쓰지 않습니다.

Android CI는 모든 대상 브랜치에서 위 경량 검사만 수행합니다. 별도 `Android PR Build`가 `client/develop` → `client/release`, `client/release` → `main` PR에서 같은 검증 모드로 `:androidApp:bundleRelease`를 추가 실행하고, 생성한 release AAB의 R8 산출물과 미서명 상태를 검증합니다. Android와 iOS 모두 PR 병합 커밋을 빌드하며, 빌드나 산출물 검증이 실패하면 해당 `Android PR Build`·`iOS PR Build` 체크가 실패합니다.

PR 산출물은 빌드 가능 여부를 병합 전에 확인하기 위한 것입니다. 업로드 키·Apple 배포 인증서·운영 서비스 설정은 사용하지 않습니다. 실제 서명·운영 Firebase·버전 검증과 스토어 업로드는 병합 후 CD에서 새로 빌드한 산출물을 대상으로 수행하며, PR 산출물을 배포에 재사용하지 않습니다.

`ciVerification`의 기본값은 `false`입니다. 실제 release 빌드에서 설정이나 서명 정보가 누락되어도 검증 모드로 자동 전환하지 않고 실패합니다.

`Client Python CI`의 `python-checks` job은 Ubuntu와 JDK 21 환경에서 아래 전체 Python 회귀 테스트를 관련 입력이 변경된 PR·push마다 한 번 실행합니다. 입력은 Python 보조 코드·검증 설정·테스트 fixture, Kotlin 프레임워크 빌드 스크립트, 검증 대상 클라이언트 워크플로와 공통 Actions입니다. 공유 Kotlin 앱 코드만 변경된 경우에는 실행하지 않습니다. Android CI·iOS CI·서명 AAB 빌드에서는 이 전체 테스트를 중복 실행하지 않습니다. 현재 Python 테스트는 모두 운영체제에 독립적입니다. Apple `security`와 Gradle 호출은 모의 구현으로 검증하고, 서명 검사는 JDK 도구를 사용하므로 별도의 macOS Python 테스트는 없습니다. Swift 저널 테스트와 Kotlin 플랫폼 테스트는 각 플랫폼 CI에 유지합니다.

CI 보조 스크립트의 회귀 테스트는 임시 테스트 키로 서명 변조·미서명 항목 검출을 확인합니다. 실제 업로드 키는 사용하지 않습니다. 배포 API를 모의 처리하는 테스트의 `GITHUB_STEP_SUMMARY`·`GITHUB_OUTPUT`은 임시 파일로 격리하므로, 테스트가 실제 Actions 실행 요약에 배포 완료 문구나 출력을 기록하지 않습니다.

```bash
python3 -B -m unittest discover -s ci -p 'test_*.py' -v
```

## GitHub Actions 실행 흐름

| 시점 | 워크플로우 | 실행 내용 | 산출물 |
| --- | --- | --- | --- |
| 작업 브랜치 → `client/develop` PR | `Client PR route`, `Client Python CI`, `Android CI`, `iOS CI` | Android 테스트·Lint, Kotlin iOS·Swift 저널 테스트, CI 보조·산출물 검사 회귀 테스트, 변경 감지·최종 판정 | 검증 보고서 |
| `client/develop` push | `Client Python CI`, `Android CI`, `iOS CI` | 같은 경량 CI와 기준 브랜치 캐시 저장 | 검증 보고서·캐시 |
| `client/develop` → `client/release` PR | `Client PR route`, `Client Python CI`, `Android CI`, `iOS CI`, `Android PR Build`, `iOS PR Build` | 품질·출발 브랜치 검사 + 미서명 release AAB의 R8·미서명 검증 + 미서명 iOS Debug 시뮬레이터 앱 빌드·개발 Firebase 검증 | 검증 보고서·빌드 산출물 |
| `client/release`에 클라이언트 변경 병합 | 변경에 필요한 CI, Android AAB 빌드·각 플랫폼 내부 CD | 변경에 필요한 동일 SHA의 CI 성공 후 내부 AAB·iOS Archive 빌드·검증, Play 내부 테스트·TestFlight 업로드 | 내부 테스트 빌드 |
| `client/release` → `main` PR | `Client PR route`, `Client Python CI`, `Android CI`, `iOS CI`, `Android PR Build`, `iOS PR Build` | 품질·출발 브랜치 검사 + 미서명 release AAB의 R8·미서명 검증 + 미서명 iOS Debug 시뮬레이터 앱 빌드·개발 Firebase 검증 | 검증 보고서·빌드 산출물 |
| `main`에 클라이언트 변경 병합 | 변경에 필요한 CI, Android AAB 빌드·각 플랫폼 운영 CD | 변경에 필요한 동일 SHA의 CI 성공 후 운영 AAB·iOS Archive 빌드·검증, Play production draft·App Store Connect 업로드 | 운영 빌드 |
| 클라이언트 PR 본문·제목 수정 | `Client PR route` | 브랜치 규칙 재검사 | 경로 검사 결과 |
| 클라이언트 PR 대상 브랜치 변경 | `Client PR route` | 경로·브랜치 규칙 재검사 | 경로 검사 결과 |

위 표의 CI·CD는 각 워크플로의 변경 경로 조건을 만족할 때 실행합니다. 앱 코드 변경만으로 공통 Python 검증이 실행되지는 않으며, 배포 대상 플랫폼에 영향을 주지 않는 변경은 해당 CD를 시작하지 않습니다.

플랫폼 CI와 승격 PR 빌드는 별도 워크플로로 구분합니다.

| 워크플로 | job | 역할 |
| --- | --- | --- |
| `Android CI`·`iOS CI` | `changes` → `quality` → `result` | 변경 감지, `Android tests and lint` 또는 `iOS Kotlin and Swift journal tests`, 최종 플랫폼 테스트 판정 |
| `Android PR Build` | `build` | 승격 PR 경로 확인, 미서명 release AAB 빌드·검증·업로드 |
| `iOS PR Build` | `build` | 승격 PR 경로 확인, 미서명 시뮬레이터 앱 빌드·검증·업로드 |

`Android PR Build`·`iOS PR Build`는 `pull_request`의 대상 브랜치가 `client/release`·`main`일 때만 시작합니다. `client/develop` 대상 PR과 기준 브랜치 push에는 빌드 워크플로가 실행되지 않으므로 생략된 빌드 체크도 표시되지 않습니다. 승격 PR에서는 플랫폼 테스트와 빌드 워크플로가 병렬로 실행될 수 있으며, 각각의 결과를 확인해야 합니다. 테스트의 최종 판정과 빌드 검증 결과는 독립된 체크입니다.

승격 PR의 빌드·검증 성공 후 Actions 실행 상세의 **Artifacts**에서 앱 파일도 다운로드할 수 있습니다. Android는 `android-pr-aab-<PR 번호>-<실행 ID>-attempt<재실행 번호>`에 미서명 release `.aab`를, iOS는 `ios-pr-simulator-app-<PR 번호>-<실행 ID>-attempt<재실행 번호>`에 `Ringout-simulator.app.zip`을 보관합니다. iOS ZIP은 앱 번들의 실행 권한과 심볼릭 링크를 보존하도록 압축합니다. 보관 기간은 14일이며, 파일 누락이나 업로드 실패도 해당 PR Build 체크를 실패시킵니다. 별도 `*-pr-build-reports-*`에는 검증 보고서를 보관합니다. 이 iOS 앱은 시뮬레이터용이며 기기 설치·TestFlight용 IPA가 아닙니다.


경량 PR 라우팅은 [client-pr-route.yml](../.github/workflows/client-pr-route.yml)에서 관리합니다. 공통 Python 검증은 [client-python-ci.yml](../.github/workflows/client-python-ci.yml), 플랫폼 품질 검사는 [android-ci.yml](../.github/workflows/android-ci.yml), [ios-ci.yml](../.github/workflows/ios-ci.yml)에 있습니다. 승격 PR 빌드는 [android-pr-build.yml](../.github/workflows/android-pr-build.yml), [ios-pr-build.yml](../.github/workflows/ios-pr-build.yml)에 있습니다. 배포는 [build-release-aab.yml](../.github/workflows/build-release-aab.yml), [android-internal-cd.yml](../.github/workflows/android-internal-cd.yml), [ios-internal-cd.yml](../.github/workflows/ios-internal-cd.yml), [android-production-cd.yml](../.github/workflows/android-production-cd.yml), [ios-production-cd.yml](../.github/workflows/ios-production-cd.yml)에서 수행합니다. 운영 설정과 재실행 절차는 [운영 CD 실행 안내](docs/release/PRODUCTION_CD_RUNBOOK.md)를 확인합니다.

### PR 검증과 병합 조건

- `client/develop` 대상 PR은 `feature`, `fix`, `chore` 등 작업 브랜치 이름으로 제한하지 않습니다.
- `client/release` 대상 클라이언트 PR은 **같은 저장소의 `client/develop`**, `main` 대상은 **같은 저장소의 `client/release`**에서만 허용합니다. fork의 동명 브랜치도 실패합니다.
- 변경 경로 정책은 `ci/change_policy.py`의 `CI_PATHS`·`PR_CI_PATHS`·`CD_PATHS`에서 관리하며 회귀 테스트가 YAML 필터와 일치하는지 확인합니다. 플랫폼 CI는 해당 플랫폼·공유 앱·빌드 설정과 해당 CI·PR Build 설정, 공통 실행 판정 코드(`ci/pipeline.py`·`ci/change_policy.py`)를 감지합니다. 공통 판정 코드 변경은 Python 회귀 테스트와 양쪽 플랫폼 테스트를 함께 실행합니다. 플랫폼 CI는 PR과 push 모두 `CI_PATHS`를 사용합니다. 별도 PR Build는 `PR_CI_PATHS`로 해당 플랫폼의 CI·CD 입력과 자체 워크플로 변경을 감지하므로, 배포 설정만 변경한 승격 PR도 앱 빌드·산출물 검증을 수행합니다. Python CI는 보조 코드와 검증 대상 워크플로를, CD는 해당 앱·빌드·배포 설정을 감지합니다. Android AAB producer와 배포 consumer는 같은 Android CD 경로를 사용합니다. 서버의 기존 브랜치 전략은 유지합니다.
- 서버나 루트 문서만 변경한 경우 클라이언트 워크플로의 경로 필터가 실행을 생략합니다. 클라이언트 브랜치 경로 규칙도 클라이언트 관련 변경에만 적용합니다.
- PR 경로 검사는 마지막 커밋이 아닌 PR 전체 변경을 기준으로 하며, 삭제와 `client` 밖으로의 이동도 확인합니다.
- 필요한 검사의 실패·취소·예상치 못한 건너뛰기는 최종 판정을 실패시킵니다. 플랫폼 테스트는 변경 감지가 명시적으로 불필요하다고 판단한 경우만 생략할 수 있습니다. PR Build 결과는 플랫폼 CI의 최종 판정에 합치지 않고 별도로 확인합니다.
- 추가 커밋이 올라오면 이전 PR 실행을 취소하고 최신 PR 병합 커밋을 검사합니다.
- 업로드 키를 PR에 제공하지 않습니다. `pull_request_target`이나 PR 아티팩트 전달을 통한 서명도 사용하지 않습니다.

### 캐시와 실행 범위

- `client/develop`·`client/release`·`main` push의 CI가 기준 브랜치 캐시를 저장합니다. 해당 브랜치 대상 PR은 키와 버전이 맞는 캐시를 복원할 수 있습니다.
- PR 실행이 저장한 캐시는 해당 PR의 후속 커밋·재실행용이며 다른 PR에서 직접 사용하지 않습니다. 외부 fork PR의 Gradle 캐시는 읽기 전용입니다.
- Android 전용 변경은 Android CI·CD만, iOS 전용 변경은 iOS CI·CD만 실행합니다. 공유 운영 코드·공통 빌드 설정은 양쪽 플랫폼 CI·CD를 실행합니다. `commonTest` 변경은 양쪽 CI만 실행하고, 문서·테스트 코드·CI 전용 설정 변경은 배포하지 않습니다. 배포는 `client/release`·`main`에서만 수행합니다.
- PR 본문·제목·대상 브랜치 수정은 별도 `Client PR route`에서 브랜치 규칙만 검사합니다. 공통 Python·두 플랫폼 CI는 `opened`, `synchronize`, `reopened`에서 실행하며 PR 편집으로는 재실행하지 않습니다.
- iOS는 Gradle 외에 `~/.konan`을 캐시합니다. 배포 빌드의 Swift 패키지는 `Package.resolved`에 기록된 버전을 사용합니다.

### 성능 보고서

- Actions 실행 요약에서 전체·job·step 시간을 확인합니다. 캐시 복원·저장 비용도 포함해 비교합니다.
- Android 아티팩트 `client-ci-reports-<run_id>-attempt<번호>`와 iOS 아티팩트 `ios-ci-profile-<run_id>-attempt<번호>`의 `profile-*.html`에서 Gradle 작업별 시간을 확인합니다.
- 경량 iOS CI는 Kotlin 테스트 프로파일을 수집합니다. `client/release`·`main` 대상 PR의 별도 `iOS PR Build`에서는 Xcode 시뮬레이터 앱을 빌드하며, 서명 Archive는 내부·운영 CD에서 생성합니다.
- 동일 커밋 재실행, 소스 변경, 신규 PR 첫 실행을 구분합니다. 단일 실행의 차이를 일반적인 평균 개선율로 해석하지 않습니다.

### 서명 AAB 생성

내부 테스트용도 운영용과 같은 `release` 빌드 타입, applicationId(`com.joon.ringout`), 승인된 업로드 키를 사용합니다. 별도 QA 앱이나 debug 빌드를 만들지 않습니다.

| 기준 브랜치 | GitHub Environment | 아티팩트 이름 | 보관 기간 |
| --- | --- | --- | --- |
| `client/release` | `internal` | `ringout-internal-aab-<versionCode>-<SHA 12자리>-attempt<재실행 번호>` | 14일 |
| `main` | `production` | `ringout-release-aab-<versionCode>-<SHA 12자리>-attempt<재실행 번호>` | 90일 |

서명 빌드는 해당 push 변경에 필요한 동일 SHA의 CI 성공을 확인한 뒤 시작합니다. gate는 전체 Git 이력을 checkout하고 실제 push의 `before`·`after` 범위를 검사하므로 여러 커밋, 삭제·이동과 강제 push를 반영합니다. 같은 저장소·브랜치·SHA의 필수 `push` CI가 누락되면 시간 초과로, 실패·취소되면 오류로 배포를 차단합니다. 불필요한 CI는 기다리지 않습니다. 검사 순서는 다음과 같습니다.

1. 필수 설정과 Firebase의 Android package name 확인.
   `client/release`는 `ringout-8abf2`, `main`은 `ringout-prod` 프로젝트만 허용하며,
   빌드 후 생성된 Firebase 리소스의 프로젝트 ID·앱 ID·프로젝트 번호를 다시 확인합니다.
   불일치하면 아티팩트 생성 전에 실패합니다.
2. 복원한 키스토어의 인증서 SHA-256 및 개인 키 비밀번호 확인.
3. 병합 커밋의 `commonTest`·`androidHostTest`와 Android Lint 실행.
4. `bundleRelease` 실행 및 R8 산출물 확인.
5. `jarsigner -verify -strict`로 서명 검증. 승인 키스토어를 신뢰 기준으로 사용하며 미서명 항목과 변조도 거부.
6. AAB 인증서, 빌드된 Manifest의 applicationId·versionCode·versionName과 Gradle 설정의 일치 여부를 확인한 후 아티팩트 업로드.

`assembleRelease`, APK 탐색, `apksigner`, APK 체크섬 단계는 없습니다. 필요해지면 APK용 CI를 별도로 설계합니다.

실제 서명 빌드는 Gradle configuration cache와 build cache, Actions의 Gradle 캐시 저장을 사용하지 않습니다. 키스토어와 Firebase 설정은 runner 임시 디렉터리에만 복원하고 실패한 경우에도 정리합니다. 빌드 폴더 전체를 업로드하지 않습니다.

### AAB와 보고서 다운로드

GitHub의 **Actions → 실행 선택 → Artifacts**에서 내려받습니다.

- 서명 AAB 아티팩트: `.aab`, `sha256.txt`, `build-metadata.json`, `r8/`.
- 메타데이터: 전체 커밋 SHA, 브랜치, versionCode·versionName, 실행 ID·재실행 번호, AAB 체크섬, 업로드 인증서 지문.
- 테스트·Lint 보고서: 빌드 성공 여부와 관계없이 생성된 파일을 별도로 보관합니다.

R8에서는 `mapping.txt`, `configuration.txt`, `seeds.txt`, `usage.txt`, `resources.txt`가 비어 있지 않은지 확인합니다. `mapping.txt`의 R8 버전은 `settings.gradle.kts`와 일치해야 합니다. `missing_rules.txt`는 없어도 되지만 **내용이 있으면 실패**합니다.

승인 인증서는 `ci/upload-certificate.sha256`에서 관리합니다. 업로드 키 교체 시에는 Play의 키 교체 절차를 완료하고 이 파일과 두 환경의 시크릿을 함께 검토합니다.

## 저장소 관리자가 설정할 항목

YAML을 병합하는 것만으로 GitHub 설정이 자동 적용되지는 않습니다. 실제 값은 문서·커밋·이슈·로그에 붙여 넣지 말고 GitHub 설정 화면에 등록합니다.

### Environment와 시크릿

**Settings → Environments**에서 Deployment branches and tags 규칙을 설정합니다.

- `internal`, `ios-internal`: 선택한 **브랜치 `client/release`만** 허용.
- `production`, `ios-production`: 선택한 **브랜치 `main`만** 허용.

Android의 `internal`·`production` 환경에는 다음 시크릿이 모두 필요합니다. 업로드 키는 저장소 공통 시크릿보다 브랜치가 제한된 Environment 시크릿으로 관리합니다.

| 이름 | 내용 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 승인 업로드 키스토어 파일의 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `ANDROID_KEY_ALIAS` | 업로드 키 별칭 |
| `ANDROID_KEY_PASSWORD` | 개인 키 비밀번호 |
| `MAPS_API_KEY` | 실제 Android Maps API 키 |
| `GOOGLE_SERVICES_JSON_BASE64` | `internal`에는 개발 Firebase(`ringout-8abf2`), `production`에는 운영 Firebase(`ringout-prod`)의 `google-services.json` Base64 |

환경 승인자를 설정하면 AAB 생성은 승인 대기 상태가 됩니다. 완전 자동 빌드를 원한다면 빌드와 향후 배포의 승인 정책을 구분합니다. PR에는 시크릿이 필요하지 않습니다.

### Android 버전 지정

개발자가 `androidApp/build.gradle.kts`의 `versionCode`와 `versionName`을 직접 수정합니다. 내부 CD는 커밋된 버전을 사용하고 운영 CD는 커밋된 versionCode N에 1을 더한 번호를 준비합니다. 빌드된 Manifest와 준비된 설정값이 같은지 확인합니다. `APP_VERSION_CODE_BASE` 저장소 변수는 더 이상 사용하지 않습니다.

- Play에 올릴 때 `versionCode`는 **모든 트랙에서 이미 사용한 가장 큰 값보다 높아야** 합니다.
- 같은 커밋을 재실행하면 같은 버전으로 AAB가 만들어집니다. 새 업로드에 다른 번호가 필요하면 개발자가 `versionCode`를 수정해 커밋합니다.
- `versionCode`는 `1..2100000000` 범위의 정수로 지정합니다.

### 브랜치 보호와 최초 적용

1. 저장소 관리자가 `client/develop`·`client/release` 브랜치와 Ruleset 또는 Branch protection을 준비합니다. YAML 변경만으로 원격 브랜치나 보호 규칙이 생성되지는 않습니다.
2. 작업 브랜치 → `client/develop` → `client/release` → `main` 경로에서 클라이언트 PR의 `Client PR route`와 변경에 필요한 `Client Python CI`·최종 `Android CI`·`iOS CI` 판정을 확인합니다. PR 편집 시 `Client PR route`가 경로 규칙을 재검사하는지도 확인합니다. 테스트·PR Build는 `opened`, `synchronize`, `reopened`에서 실행하므로 대상 브랜치를 바꾼 경우 닫았다 다시 열거나 새 커밋을 올려 다시 검사합니다.
3. 경로 필터로 실행하지 않는 클라이언트 체크를 서버 PR에도 일괄 필수 검사로 지정하면 `Pending`으로 남을 수 있습니다. 기존 필수 검사 규칙과 서버 PR을 함께 검토하여 적용합니다.
4. PR 병합과 최신 기준 브랜치 반영을 요구하고 직접 push·강제 push·삭제 및 우회 권한을 검토합니다. YAML 자체는 직접 push를 막지 못합니다.
5. `internal`·`ios-internal` 환경의 허용 브랜치를 `client/release`로 변경하고, 운영 환경은 `main`으로 제한합니다. 원격 설정은 관리자가 별도로 적용합니다.
6. `client/release`·`main` 대상 PR에서 변경에 필요한 플랫폼의 `Android PR Build`·`iOS PR Build` 체크와 앱 산출물을 별도로 확인합니다. 두 빌드 체크를 필수로 지정한다면 `client/release`·`main`에만 적용하고 `client/develop`에는 적용하지 않습니다. 빌드 워크플로에도 경로 필터가 있으므로 해당 플랫폼 변경이 없는 PR에서 필수 체크가 `Pending`으로 남는지 함께 검토합니다. `client/develop` 병합에서는 경량 CI만 실행되는지, `client/release` 병합에서는 내부 빌드·업로드가, `main` 병합에서는 운영 빌드·업로드가 실행되는지 확인합니다. 배포 SHA·버전·인증서·체크섬을 대조합니다.

모든 PR에 공통 Python·두 플랫폼 CI 필수 검사를 유지하려면 워크플로우 경로 필터를 제거하고 내부 변경 검사와 최종 체크를 항상 실행하도록 별도 설계해야 합니다. 현재 merge queue는 지원하지 않습니다. 향후 도입 시 `merge_group` 이벤트와 변경 경로 판정을 함께 추가해야 합니다.

서명 AAB의 수동 실행은 복구용입니다. `client/release`·`main` 이외 브랜치에서는 서명 job을 실행하지 않으며 선택한 SHA의 첫 부모와 비교해 필요한 push CI 성공 조건을 유지합니다. 수동 실행은 Android 빌드를 강제하지만 CI를 우회하지 않습니다. 해당 SHA의 필수 push CI가 없으면 복구도 차단됩니다. 브랜치별 실행 중인 AAB 빌드는 취소하지 않으며, GitHub concurrency 정책상 여러 대기 실행은 최신 실행으로 대체될 수 있습니다. 운영 업로드 복구는 원래 push 실행을 재실행합니다.

## QA 및 배포 범위

- API 주소는 빌드 입력 `RINGOUT_API_BASE_URL`로 지정합니다. 내부 환경은 개발 API, 운영 환경은 운영 API를 사용합니다. 설정·버전 예약과 필요한 시크릿은 [운영 CD 실행 안내](docs/release/PRODUCTION_CD_RUNBOOK.md)를 확인합니다.
- AAB는 기기에 직접 설치하는 파일이 아닙니다. 내부 CD는 검증한 AAB를 Google Play 내부 테스트에 업로드하고 iOS는 TestFlight 내부 테스트에 업로드합니다.
- 운영 CD는 Android의 Play `production` draft를 만들고 iOS 빌드를 App Store Connect에 업로드합니다. 심사 제출·공개와 실기기 E2E 테스트는 별도 절차입니다. 실제 서명 빌드의 Crashlytics mapping 업로드는 유지합니다.
- `main` 빌드는 `client/release`에서 QA한 바이너리를 재사용하지 않고 운영 설정과 예약된 다음 빌드 번호로 새로 만듭니다. 최종 운영 빌드도 확인해야 합니다.

## 참고 문서

- [Kotlin Multiplatform 테스트](https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html)
- [Android Lint](https://developer.android.com/studio/write/lint)
- [Android 앱 버전 관리](https://developer.android.com/studio/publish/versioning)
- [AAB 빌드와 테스트](https://developer.android.com/guide/app-bundle/test)
- [GitHub Actions 필수 상태 검사](https://docs.github.com/en/pull-requests/how-tos/merge-and-close-pull-requests/troubleshooting-required-status-checks)
