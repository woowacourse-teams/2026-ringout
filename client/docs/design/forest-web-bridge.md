# 숲 WebView·Room 연결 (4단계)

홈 우측 상단의 기존 씨앗 애니메이션 버튼으로 숲 화면에 진입한다. Android는 WebView, iOS는 WKWebView를 사용한다. 성장 계산·나무 종류·배치·온보딩 및 나무 확인 상태의 원본은 앱 Room이며 이번 단계에서 테이블이나 마이그레이션은 변경하지 않았다.

## URL 설정

빌드 시 Gradle 속성 `ringoutForestWebUrl` 또는 환경 변수 `RINGOUT_FOREST_WEB_URL`로 웹 문서 주소를 전달한다. Gradle 속성이 우선한다. 기본값은 빈 문자열이며 미설정 시 안내와 홈 이동 버튼을 표시한다.

주소에 쿼리·fragment·인증 정보를 넣지 않는다. 앱이 `?view=app`을 붙인다. 배포 시에는 리다이렉트가 없는 최종 HTTPS 문서 주소를 사용한다. HTTP는 디버그 빌드의 loopback 주소에만 허용한다.

### Android 로컬 실행

저장소의 `web` 디렉터리에서:

```sh
npm ci
npm run dev
```

`client` 디렉터리에서 연결된 에뮬레이터/기기에 실행한다. 여러 기기가 연결되어 있다면 adb에 `-s <serial>`을 추가한다.

```sh
adb reverse tcp:5173 tcp:5173
./gradlew :androidApp:installDebug -PringoutForestWebUrl=http://127.0.0.1:5173/
```

앱을 열고 홈 우측 상단 씨앗을 누른다. Android Studio 실행에서도 같은 주소를 유지하려면 로컬 Gradle 속성으로 지정한다. 배포 주소가 정해지면 HTTPS 주소를 주입하여 다시 빌드한다.

### iOS 시뮬레이터 빌드

웹 서버 실행 후 `client`에서:

```sh
RINGOUT_FOREST_WEB_URL=http://127.0.0.1:5173/ xcodebuild \
  -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /private/tmp/ringout-forest-stage4-xcode CODE_SIGNING_ALLOWED=NO build
```

환경 변수는 Kotlin 프레임워크를 빌드하는 프로세스에 전달해야 한다. iOS 실기기의 loopback은 Mac 개발 서버를 가리키지 않으므로 실기기에서는 HTTPS 개발 주소가 필요하다.

## 데이터 흐름

- 웹 `forest.ready` → 앱이 Room 조회 → 버전 1 스냅샷 전달 및 변경 관찰 시작.
- `forest.completeOnboarding` / `forest.acknowledgeTree` → Domain Repository 저장 성공 후 최신 스냅샷 응답.
- 웹은 나무 종류나 칸을 선정하지 않는다. 앱이 정한 값을 표시하고 확인할 성장 회차만 전송한다.
- 요청 ID로 응답을 연결하고 중복 저장을 방지한다. 스냅샷 revision으로 오래된 응답이 최신 화면을 되돌리지 않도록 한다.
- 페이지 재시작·화면 해제 시 연결 작업을 취소한다. 백그라운드에서는 Room 관찰을 중지하고 복귀 시 최신 상태를 전달한다.
- 시스템 뒤로가기는 웹에 전달하여 열린 시트를 먼저 닫는다. 일반 숲 화면에서는 웹의 뒤로가기 요청을 받아 앱 홈으로 돌아간다.
- 로딩 오류·연결 시간 초과에는 재시도와 홈 이동을 제공한다. 저장 오류에는 현재 화면을 유지한다.

브릿지는 설정한 origin과 정확한 문서 URL의 최상위 프레임만 허용한다. 외부 페이지 이동과 임의 파일 접근은 허용하지 않는다. 웹 단독 `?view=app` 접속은 앱에서 열라는 안내를 표시하며 데모 저장소를 사용하지 않는다. 기존 `?view=forest&scenario=...`는 독립적인 개발용 미리보기다.

## 검증 결과 (2026-11-09)

| 검증 | 결과 |
| --- | --- |
| Android Host 테스트 | 1,449개 통과, 실패·건너뜀 0 |
| iOS Simulator Arm64 테스트 | 1,387개 통과, 실패·건너뜀 0 |
| 웹 Vitest | 34개 통과 |
| Android Debug 빌드·Release lint | 통과 |
| iOS Kotlin 컴파일·Xcode 시뮬레이터 앱 빌드 | 통과 |
| 웹 TypeScript 검사·프로덕션 빌드 | 통과; 기존 Three.js 약 637KB 청크 경고 유지 |

새 회귀 테스트는 연결 준비·Room 갱신·중복 요청·실패 후 재시도·잘못된 명령·페이지 재시작·백그라운드 복귀·뒤로가기·URL 제한을 다룬다. 실제 Room에 도착 5일을 기록한 뒤 브릿지로 온보딩과 나무 확인을 저장하고 DB를 다시 열어 종류·위치·확인 상태가 유지되는 통합 테스트도 Android/iOS에서 실행했다.

Android 에뮬레이터에서 홈 씨앗 버튼 → 온보딩 2장 → Room 성장 상태 → 성장 시트 → 시스템 뒤로가기로 시트 닫기 → 홈 복귀 → 재진입 시 온보딩 생략을 직접 확인했다. 검증 중 WebView에서 `100dvh` 높이가 0이 되어 흰 화면이 나타나는 문제를 발견하여 호스트의 실제 높이를 사용하도록 수정했다.

iOS는 자동 테스트와 앱 빌드까지 검증했으며 WKWebView의 수동 터치 흐름은 아직 확인하지 않았다. 실제 나무 완성·확인 저장은 자동 통합 테스트로 검증했으며 에뮬레이터의 기존 도착 기록을 임의로 늘리지 않았다. 배포 URL 연결은 주소가 결정된 후 확인해야 한다.
