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

```bash
./gradlew :androidApp:assembleDebug
```

지도·로그인·Firebase 기능을 실제로 실행하려면 팀에서 받은 설정이 필요합니다.

| 설정 | 로컬 지정 방법 |
| --- | --- |
| Android SDK 경로 | `local.properties`의 `sdk.dir` |
| Maps API 키 | `local.properties` 또는 환경변수 `MAPS_API_KEY` |
| 카카오 네이티브 앱 키 | `local.properties` 또는 환경변수 `KAKAO_NATIVE_APP_KEY` |
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

현재 Android CI에는 이 태스크를 포함하지 않습니다. Android CI 성공이 iOS 테스트 성공을 의미하지 않습니다.

### Android Lint

```bash
./gradlew :androidApp:lintRelease -PciVerification=true
```

`lintRelease`는 **Android Lint**입니다. Android 앱의 release 변형을 대상으로 Manifest, 리소스, API 사용 등의 정적 문제를 검사합니다. Kotlin 코드 스타일 도구인 **ktlint가 아니며**, 이번 CI에서는 ktlint를 도입하지 않습니다. 공통 Kotlin 소스 전체의 코드 스타일 검사로 해석하지 않습니다.

보고서는 `androidApp/build/reports/lint-results-release.html`과 같은 디렉터리의 XML·TXT 파일에서 확인합니다. Lint 오류는 CI를 실패시키며, 경고는 보고서에 남깁니다.

## PR 검증 모드

PR은 서명 키와 실제 서비스 설정 없이 다음 검사를 수행합니다.

```bash
./gradlew :shared:testAndroidHostTest :androidApp:lintRelease \
  -PciVerification=true --no-daemon --no-configuration-cache
./gradlew :androidApp:bundleRelease \
  -PciVerification=true --no-daemon --no-configuration-cache
python3 ci/pipeline.py verify-r8
python3 ci/pipeline.py verify-unsigned
```

`-PciVerification=true`를 명시하면 다음과 같이 동작합니다.

- Maps·카카오 키를 검증용 값으로 고정합니다. 실제 로컬 설정이나 환경변수의 키를 앱 설정에 사용하지 않습니다.
- Firebase는 `ci/google-services.ci.json`을 사용합니다. 로컬의 실제 설정 파일을 덮어쓰지 않습니다.
- release의 R8 난독화·최적화·리소스 축소는 유지합니다.
- 업로드 서명을 적용하지 않고 Crashlytics mapping 업로드를 끕니다.
- 빌드 후 Firebase 검증 설정 적용 여부와 AAB에 서명이 없는지 확인합니다.

**검증 모드의 AAB는 QA·Play 업로드·운영 배포에 사용하지 않습니다.** PR에서는 AAB를 아티팩트로 제공하지 않습니다.

`ciVerification`의 기본값은 `false`입니다. 실제 release 빌드에서 설정이나 서명 정보가 누락되어도 검증 모드로 자동 전환하지 않고 실패합니다.

CI 보조 스크립트의 회귀 테스트는 임시 테스트 키로 서명 변조·미서명 항목 검출을 확인합니다. 실제 업로드 키는 사용하지 않습니다.

```bash
python3 -B -m unittest discover -s ci -p 'test_*.py' -v
```

## GitHub Actions 실행 흐름

| 시점 | 워크플로우 | 실행 내용 | 산출물 |
| --- | --- | --- | --- |
| 작업 브랜치 → `develop` PR 생성·수정·재오픈 | `Client CI` | 테스트, Android Lint, 서명 없는 release AAB 빌드, R8 검사 | 검증 보고서 |
| `develop`에 클라이언트 변경 병합 | `Build Signed Release AAB` | 해당 커밋의 테스트·Lint, 서명 AAB 빌드·검증 | 내부 테스트용 AAB |
| `develop` → `main` PR 생성·수정·재오픈 | `Client CI` | 같은 품질 검사와 출발 브랜치 검사 | 검증 보고서 |
| `main`에 클라이언트 변경 병합 | `Build Signed Release AAB` | 해당 커밋의 테스트·Lint, 서명 AAB 빌드·검증 | 릴리스용 AAB |

워크플로우는 저장소 루트의 [client-ci.yml](../.github/workflows/client-ci.yml)과 [build-release-aab.yml](../.github/workflows/build-release-aab.yml)에 있습니다.

### PR 검증과 병합 조건

- `develop` 대상 PR은 `feature`, `fix`, `chore` 등 작업 브랜치 이름으로 제한하지 않습니다.
- `main` 대상 PR은 **같은 저장소의 `develop`**에서만 허용합니다. fork의 동명 브랜치도 실패합니다.
- 워크플로우 전체에 경로 필터를 걸지 않습니다. 내부에서 `client/**`, 두 클라이언트 워크플로우, `.github/actions/**`의 변경을 확인합니다. 삭제나 `client` 밖으로의 이동도 검사합니다.
- 서버만 변경한 PR은 Android 검사를 건너뛰고 최종 `Client CI`를 통과합니다. 단, `main` PR의 출발 브랜치 규칙은 동일하게 적용합니다.
- 필요한 검사의 실패·취소·예상치 못한 건너뛰기는 최종 `Client CI` 실패로 이어집니다.
- 추가 커밋이 올라오면 이전 PR 실행을 취소하고 최신 PR 병합 커밋을 검사합니다.
- 업로드 키를 PR에 제공하지 않습니다. `pull_request_target`이나 PR 아티팩트 전달을 통한 서명도 사용하지 않습니다.

### 서명 AAB 생성

내부 테스트용도 운영용과 같은 `release` 빌드 타입, applicationId(`com.joon.ringout`), 승인된 업로드 키를 사용합니다. 별도 QA 앱이나 debug 빌드를 만들지 않습니다.

| 기준 브랜치 | GitHub Environment | 아티팩트 이름 | 보관 기간 |
| --- | --- | --- | --- |
| `develop` | `internal` | `ringout-internal-aab-<versionCode>-<SHA 12자리>-attempt<재실행 번호>` | 14일 |
| `main` | `production` | `ringout-release-aab-<versionCode>-<SHA 12자리>-attempt<재실행 번호>` | 90일 |

서명 빌드의 검사 순서는 다음과 같습니다.

1. 필수 설정과 Firebase의 Android package name 확인.
2. 복원한 키스토어의 인증서 SHA-256 및 개인 키 비밀번호 확인.
3. 병합 커밋의 `commonTest`·`androidHostTest`와 Android Lint 실행.
4. `bundleRelease` 실행 및 R8 산출물 확인.
5. `jarsigner -verify -strict`로 서명 검증. 승인 키스토어를 신뢰 기준으로 사용하며 미서명 항목과 변조도 거부.
6. AAB 인증서, 빌드된 Manifest의 applicationId·versionCode 확인 후 아티팩트 업로드.

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

- `internal`: 선택한 **브랜치 `develop`만** 허용.
- `production`: 선택한 **브랜치 `main`만** 허용.

두 환경에 다음 시크릿이 모두 필요합니다. 업로드 키는 저장소 공통 시크릿보다 브랜치가 제한된 Environment 시크릿으로 관리합니다.

| 이름 | 내용 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 승인 업로드 키스토어 파일의 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `ANDROID_KEY_ALIAS` | 업로드 키 별칭 |
| `ANDROID_KEY_PASSWORD` | 개인 키 비밀번호 |
| `MAPS_API_KEY` | 실제 Android Maps API 키 |
| `KAKAO_NATIVE_APP_KEY` | 실제 카카오 네이티브 앱 키 |
| `GOOGLE_SERVICES_JSON_BASE64` | 실제 `google-services.json` 파일의 Base64 |

환경 승인자를 설정하면 AAB 생성은 승인 대기 상태가 됩니다. 완전 자동 빌드를 원한다면 빌드와 향후 배포의 승인 정책을 구분합니다. PR에는 시크릿이 필요하지 않습니다.

### versionCode 발급

**Settings → Secrets and variables → Actions → Variables**에 저장소 변수 `APP_VERSION_CODE_BASE`를 등록합니다.

```text
versionCode = APP_VERSION_CODE_BASE + GITHUB_RUN_NUMBER
```

- 기준값은 **Play의 모든 트랙에서 이미 사용한 가장 큰 versionCode 이상**으로 설정합니다. 로컬 코드의 기본값만 보고 Play의 최신값이라고 가정하지 않습니다.
- 두 채널이 같은 AAB 워크플로우의 실행 번호를 공유합니다. 환경별로 서로 다른 기준값을 덮어쓰지 않습니다.
- 기준값이 없거나 결과가 `1..2100000000`을 벗어나면 빌드를 시작하지 않습니다.
- **Re-run jobs**는 같은 versionCode를 사용합니다. 아티팩트 이름에는 재실행 번호를 붙이지만 이미 Play에 업로드한 versionCode로 새 파일을 다시 업로드할 수는 없습니다.
- 새 업로드가 필요하면 해당 브랜치에서 **Run workflow**로 새 실행을 시작합니다.
- 기존 워크플로우 파일을 삭제·재생성하거나 발급 정책을 바꿀 때는 Play 최대값을 다시 확인합니다.
- 로컬 기본 versionCode와 versionName은 `androidApp/build.gradle.kts`에서 관리합니다. CI는 `APP_VERSION_CODE`로 versionCode만 주입합니다.

### 브랜치 보호와 최초 적용

1. 기존 리뷰·보호 규칙을 유지한 채 CI 변경 PR을 실행합니다.
2. 실제 PR에서 최종 **`Client CI`** 체크가 성공한 것을 확인합니다.
3. `develop`과 `main`의 Ruleset 또는 Branch protection에 `Client CI`를 필수 상태 검사로 등록합니다. 조건부 Android job 대신 최종 체크를 선택합니다.
4. PR을 통한 변경과 최신 기준 브랜치 반영을 요구하고, 직접 push·강제 push·삭제 및 우회 권한을 검토합니다. YAML 자체는 직접 push를 막지 못합니다.
5. `develop` 병합 후 내부 AAB, `main` 병합 후 릴리스 AAB 생성과 인증서·체크섬을 확인합니다.

체크가 최초 실행되기 전에 필수로 지정하면 진행 중인 PR이 대기할 수 있으므로 최초 성공 이후 연결합니다. 현재 merge queue는 지원하지 않습니다. 향후 도입 시 `merge_group` 이벤트와 변경 경로 판정을 함께 추가해야 합니다.

수동 실행은 복구용입니다. `develop`·`main` 이외 브랜치를 선택하면 서명 job은 실행하지 않습니다. 브랜치별 실행 중인 AAB 빌드는 취소하지 않으며, GitHub concurrency 정책상 여러 대기 실행은 최신 실행으로 대체될 수 있습니다.

## QA 및 배포 범위

- 현재 API 주소는 `shared/src/commonMain/kotlin/com/joon/ringout/data/network/ApiConfig.kt`에 고정되어 있습니다. **Environment를 나눠도 QA 서버가 분리되지는 않습니다.**
- AAB는 기기에 직접 설치하는 파일이 아닙니다. 실제 QA에는 Google Play 내부 테스트 등의 배포 경로가 필요합니다.
- CI는 AAB 생성·검증·보관까지 수행합니다. Play 자동 업로드·출시, iOS CI, 실기기 E2E 테스트, ktlint는 포함하지 않습니다. 기존 Crashlytics mapping 업로드는 실제 서명 빌드에서 유지합니다.
- `main` AAB는 `develop`에서 QA한 파일을 재사용하지 않고 새로 빌드합니다. 최종 릴리스 파일도 확인해야 합니다. 동일 바이너리 승격이나 별도 QA 서버·동시 설치 앱이 필요하면 배포 정책과 빌드 변형을 추가로 설계합니다.

## 참고 문서

- [Kotlin Multiplatform 테스트](https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html)
- [Android Lint](https://developer.android.com/studio/write/lint)
- [Android 앱 버전 관리](https://developer.android.com/studio/publish/versioning)
- [AAB 빌드와 테스트](https://developer.android.com/guide/app-bundle/test)
- [GitHub Actions 필수 상태 검사](https://docs.github.com/en/pull-requests/how-tos/merge-and-close-pull-requests/troubleshooting-required-status-checks)
