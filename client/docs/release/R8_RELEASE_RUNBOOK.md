# Ringout Android R8 릴리스 런북

이 문서는 `release` 빌드의 R8 코드 축소·최적화·난독화, 리소스 축소, Crashlytics 매핑 업로드를 검증하고 배포하는 절차다. 모든 명령은 `client/`에서 실행한다.

## 1. 기준과 사전 조건

- JDK 21을 사용한다. 릴리스 GitHub Actions도 Temurin 21을 사용한다.
- [버전 카탈로그](../../gradle/libs.versions.toml)의 기준은 AGP `9.0.0`, Kotlin `2.4.0`이다.
- [Gradle Wrapper](../../gradle/wrapper/gradle-wrapper.properties)의 기준은 Gradle `9.1.0`이다.
- Android Studio Panda 1과의 호환성을 위해 AGP는 `9.0.0`으로 유지하고, [설정 파일](../../settings.gradle.kts)에서 R8 `9.1.31`을 명시적으로 적용한다.
- Crashlytics Gradle plugin은 `3.0.7`, Firebase BoM은 `34.16.0`이다.
- Android 설정은 compile/target SDK 36, min SDK 26, JVM target 11이다.
- [앱 빌드 설정](../../androidApp/build.gradle.kts)의 `versionCode`와 `versionName`을 배포마다 올린다.
- `release`에는 `isMinifyEnabled = true`, `isShrinkResources = true`, 최적화 기본 규칙과 앱 규칙이 적용되어 있어야 한다.
- Room DB는 schema 4이며 schema JSON은 `shared/schemas/`에 보관된다. 업데이트 테스트에서는 기존 DB를 삭제하지 않는다.

확인 명령:

```bash
java -version
./gradlew --version
git rev-parse HEAD
git status --short
```

## 2. 환경변수와 시크릿

값은 문서, 로그, 아티팩트 이름에 남기지 않는다.

| 이름 | 용도 |
| --- | --- |
| `MAPS_API_KEY` | Maps SDK 및 Places API(New). 로컬은 `local.properties`에도 같은 이름으로 설정할 수 있다. |
| `ANDROID_KEYSTORE_PATH` | 로컬 릴리스 JKS 경로. CI에서는 복원 후 이 이름으로 전달한다. |
| `ANDROID_KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `ANDROID_KEY_ALIAS` | 업로드 키 alias |
| `ANDROID_KEY_PASSWORD` | 업로드 키 비밀번호 |
| `ANDROID_KEYSTORE_BASE64` | GitHub Actions에서 JKS를 복원하는 시크릿 |

`MAPS_API_KEY`가 비어 있으면 `RingoutApplication`이 시작 시 실패한다. 따라서 릴리스 워크플로에도 `MAPS_API_KEY` 시크릿을 등록하고 빌드 단계로 전달해야 한다. Firebase 설정은 `androidApp/google-services.json`과 Gradle 플러그인 구성을 사용하며, Crashlytics 매핑 업로드용 값을 별도 로그로 출력하지 않는다.

API 키는 APK에서 추출할 수 있으므로 시크릿 등록만으로 보호되지 않는다. Google Cloud Console에서 다음 제한을 함께 적용한다.

- 애플리케이션 제한: Android 앱, 패키지 `com.joon.ringout`
- 직접 설치·업로드 키 서명본: SHA-1 `30:AF:62:A9:77:82:6A:9B:76:6A:ED:63:DB:E9:DA:68:69:00:A8:B7`
- Google Play 배포본: Play Console의 **앱 서명 키 인증서** SHA-1도 별도 항목으로 등록
- API 제한: 실제 사용하는 Maps SDK for Android와 Places API/SDK만 허용

개발용 키와 프로덕션 키는 분리한다. 로컬 debug 키에는 debug 인증서만, GitHub `production` 환경의 키에는 릴리스 인증서만 허용한다. 제한을 적용한 뒤 지도 렌더링과 장소 검색을 두 서명 경로에서 각각 확인한다.

서명 확인:

```bash
./gradlew :androidApp:validateReleaseMapsApiKey
./gradlew :androidApp:signingReport
```

## 3. 릴리스 게이트

다음 순서에서 하나라도 실패하면 배포하지 않는다.

```bash
./gradlew :shared:testAndroidHostTest
./gradlew :androidApp:lintRelease
./gradlew :androidApp:assembleRelease :androidApp:bundleRelease
```

확인할 산출물:

- APK: `androidApp/build/outputs/apk/release/`
- AAB: `androidApp/build/outputs/bundle/release/`
- lint: `androidApp/build/reports/lint-results-release.html`
- R8: `androidApp/build/outputs/mapping/release/`

R8 디렉터리에서 실제 생성되는 `mapping.txt`, `configuration.txt`, `seeds.txt`, `usage.txt`, `resources.txt`를 확인한다. `mapping.txt`는 비어 있으면 안 되며 헤더에 `# compiler_version: 9.1.31`이 있어야 한다. 정상 빌드에서는 `missing_rules.txt`가 생성되지 않는다. 이 파일이 생기거나 누락 클래스 경고가 나오면 필요한 최소 keep 규칙만 추가한 뒤 다시 빌드한다. `mapping.prt`는 AGP 버전에 따라 생성될 때만 함께 보관한다.

## 4. R8 산출물 보관

각 배포는 `versionCode`와 40자리 Git commit으로 식별한다. 보안 릴리스 저장소 또는 접근 제한된 CI 아티팩트에 다음 구조로 보관한다.

```text
android/<versionCode>/<commit>/
├── androidApp-release.aab
├── androidApp-release.apk
├── sha256.txt
├── release-metadata.txt
└── r8/
    ├── mapping.txt
    ├── mapping.prt              # 생성된 경우
    ├── configuration.txt
    ├── seeds.txt
    ├── usage.txt
    ├── resources.txt
    └── missing_rules.txt        # 생성된 경우
```

`release-metadata.txt`에는 비밀값 없이 `versionCode`, `versionName`, commit, AGP·Gradle·Kotlin 버전, 빌드 시각을 기록한다. CI 아티팩트 이름도 `ringout-android-<versionCode>-<commit>`으로 맞춘다.

`mapping.txt`는 해당 버전의 Crashlytics 이벤트를 해석하는 데 필요하므로 앱 지원 수명 동안 삭제하지 않는다. 공개 저장소에 커밋하거나 일반 사용자에게 배포하지 않는다. AAB·APK와 매핑 파일의 버전이 섞이지 않도록 같은 디렉터리 단위로 보관한다.

현재 GitHub Actions는 APK와 AAB를 생성·서명 검증하지만, handoff 아티팩트에는 AAB만 넣고 1일간 보관한다. 원본 `mapping.txt`는 Crashlytics에만 자동 전송하며 GitHub 아티팩트로 별도 업로드하지 않는다. 접근 권한과 보존 기간이 승인된 저장소가 정해질 때까지는 릴리스 담당자가 러너 종료 전에 R8 디렉터리를 복사하거나, 1일 안에 AAB를 내려받아 보안 릴리스 저장소에 옮겨야 한다.

현재 AAB에는 같은 빌드의 매핑이 `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`으로 포함된다. 보관한 AAB에서 원본을 복구할 수 있다.

```bash
unzip -p androidApp-release.aab \
  BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map \
  > mapping.txt
```

추출한 파일의 해시를 릴리스 때 기록한 `mapping.txt`와 비교한 뒤 사용한다. GitHub의 1일 보관은 장기 보관 정책이 아니므로, 승인된 영구 저장소가 정해지지 않은 상태에서는 프로덕션 배포를 완료로 처리하지 않는다.

체크섬 생성 예시:

```bash
shasum -a 256 androidApp/build/outputs/bundle/release/*.aab
shasum -a 256 androidApp/build/outputs/apk/release/*.apk
```

## 5. Crashlytics 매핑 업로드 확인

`bundleRelease` 그래프에는 `injectCrashlyticsMappingFileIdRelease`와 `uploadCrashlyticsMappingFileRelease`가 자동 연결된다. 릴리스 빌드 로그에서 두 태스크, 특히 업로드 태스크가 성공했는지 확인한다. 자동 실행 결과가 불명확하면 업로드 태스크를 명시적으로 실행한다.

```bash
./gradlew :androidApp:uploadCrashlyticsMappingFileRelease --info
```

완료 기준은 다음과 같다.

1. 태스크가 `SUCCESS` 또는 이미 같은 입력으로 완료된 `UP-TO-DATE` 상태다.
2. 업로드한 `mapping.txt`의 경로가 현재 `release` 빌드 경로이며 `versionCode`와 commit이 보관 메타데이터와 일치한다.
3. 내부 테스트 트랙의 minified APK에서 테스트용 비정상 종료를 한 번 발생시킨다. 운영 사용자에게 노출되는 코드에는 테스트 크래시를 넣지 않는다.
4. Firebase Crashlytics 콘솔에서 해당 `versionName`/`versionCode` 이벤트가 수집되고, 앱 클래스와 메서드가 `mapping.txt` 기준으로 복원되는지 확인한다. 난독화된 `a.b.c` 형태만 보이면 배포를 중단한다.

## 6. 수동 retrace

원본 스택 트레이스를 `stacktrace.txt`로 저장하고 같은 `versionCode`/commit의 매핑으로 복원한다.

먼저 retrace 실행 파일을 확인한다.

```bash
RETRACE="$ANDROID_HOME/cmdline-tools/latest/bin/retrace"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
test -x "$RETRACE" || "$SDKMANAGER" "cmdline-tools;latest"
test -x "$RETRACE"
```

```bash
"$RETRACE" \
  androidApp/build/outputs/mapping/release/mapping.txt \
  stacktrace.txt \
  > stacktrace-retraced.txt
```

로컬 `mapping.txt`가 현재 빌드에서 사라졌다면 보관소의 `android/<versionCode>/<commit>/r8/mapping.txt`를 사용한다. 다른 버전의 매핑을 추정해서 사용하지 않는다.

## 7. APK/AAB 크기 비교

동일한 소스 commit과 리소스로 만든 R8 비적용 기준 산출물과 R8 적용 산출물을 별도 디렉터리에 둔다. 서명 방식과 빌드 타입도 동일해야 한다.

```bash
wc -c baseline/androidApp-release.apk r8/androidApp-release.apk
wc -c baseline/androidApp-release.aab r8/androidApp-release.aab
```

APK와 AAB 각각에 대해 아래 값을 릴리스 기록에 남긴다.

- 기준 바이트 수
- R8 적용 바이트 수
- 감소 바이트 수
- 감소율: `(기준 - R8 적용) / 기준 × 100`

크기 감소만으로 통과시키지 않는다. `usage.txt`와 `resources.txt`가 있다면 함께 검토하고, 아래 스모크 테스트를 모두 통과해야 한다.

## 8. 기기 스모크 테스트

현재 `minSdk = 26`이므로 API 25 에뮬레이터에는 설치할 수 없다. API 26 환경이 없으므로 API 27을 보유 환경의 하한 대리 기기로 사용한다. API 26 고유 회귀는 남은 위험이므로 가능하면 출시 전 기기 팜에서 한 번 추가 확인한다.

| 영역 | API 27 | API 36 |
| --- | --- | --- |
| 설치 | 새 설치, 이전 서명 APK 위에 `adb install -r` 업데이트 | 새 설치, 이전 서명 APK 위에 업데이트 |
| 시작 | 콜드 스타트, 온보딩, 약관, 홈 | 콜드 스타트, 알림·위치·정확한 알람·전체 화면 관련 권한 흐름 |
| 알람 CRUD | 생성, 수정, 활성/비활성, 삭제, 1회·요일 반복 | 동일 |
| 알람 수신 | 앱을 백그라운드로 보낸 뒤 실제 시각에 수신, 소리·진동·울림 화면·중지 | 동일, 포그라운드 서비스와 알림 표시 확인 |
| 목적지 미션 | 위치 추적 시작, 도착 성공, 제한시간 만료, 강제 종료 | 동일, 위치 포그라운드 서비스 지속 여부 확인 |
| 재예약 | 실제 재부팅, 시스템 시각 변경, 시간대 변경, 앱 업데이트 후 활성 알람 유지 | 동일 |
| Room | 이전 APK에서 알람·목적지·미션 기록 생성 후 업데이트, DB 유지 및 읽기·쓰기 | 동일 |
| Maps/Places | 지도 렌더링, 현재 위치, 장소 검색, 목적지 저장·재선택 | 동일 |
| Firebase | Analytics DebugView 이벤트와 Crashlytics 테스트 이벤트 확인 | 동일 |
| UI 리소스 | 라이트·다크 테마, Compose 이미지·폰트, 빈 화면과 주요 화면 | 동일 |

업데이트 테스트는 이전 릴리스와 새 릴리스가 같은 업로드 키로 서명되어야 한다. 앱 데이터 삭제나 `uninstall`은 Room 호환성 검증 전에 하지 않는다.

Firebase Analytics는 DebugView를 켜고 실제 흐름에서 최소한 다음 이벤트 이름이 유지되는지 확인한다.

- `destination_alarm_created`
- `destination_alarm_ringing_started`
- `destination_mission_started`
- `destination_mission_completed` 또는 실패·강제 종료 이벤트
- `force_end_hold_started`, `force_end_hold_cancelled`, `force_end_hold_completed`

```bash
adb shell setprop debug.firebase.analytics.app com.joon.ringout
# 테스트 완료 후
adb shell setprop debug.firebase.analytics.app .none.
```

## 9. 배포와 롤백

1. 내부 테스트 트랙에서 API 27/36 스모크와 Crashlytics 복원을 완료한다.
2. 비공개 테스트 트랙에서 실제 기기 알람, 재부팅, Maps/Places, Room 업데이트를 확인한다.
3. 프로덕션은 소규모 단계 배포로 시작해 Crash-free users/sessions, ANR, 앱 시작 실패, 알람·미션 Analytics 이벤트 급감을 관찰한다.
4. 이상이 없을 때만 단계 배포 비율을 높인다. R8 이후 최초 릴리스는 즉시 100%로 올리지 않는다.

심각한 회귀가 발생하면 단계 배포를 즉시 중지한다. Play에서는 낮은 `versionCode`로 되돌릴 수 없고 동일 AAB를 같은 `versionCode`로 다시 올릴 수도 없다. 다음 절차로 복구한다.

1. 원인을 현재 `versionCode`/commit의 Crashlytics 이벤트와 보관 매핑으로 확인한다.
2. 직전 안정 소스를 기준으로 하되, 현재 DB schema와 마이그레이션은 유지한다.
3. R8이 원인이라면 긴급 빌드에서 minify/resource shrink를 끄거나 좁은 keep 규칙을 추가한다.
4. 기존보다 큰 새 `versionCode`와 동일 application ID·업로드 키로 APK/AAB를 만든다.
5. 릴리스 게이트와 핵심 알람·Room 업데이트 스모크를 다시 통과시킨 뒤 복구 버전을 배포한다.
6. 실패 버전의 AAB와 `mapping.txt`도 삭제하지 않는다. 이미 설치된 사용자와 지연 수집된 크래시를 계속 해석해야 한다.
