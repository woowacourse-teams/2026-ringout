# 큰 시스템 글자 크기 대응 검증 (#556)

검증일: 2026-10-21. 기준: 이미지 방향 보정 PR #555가 포함된 `32f89cc9`.

## 수정 범위와 원인

시스템이 `sp` 글자를 확대해도 기존 `dp` 고정 높이와 줄 수 제한은 그대로여서 텍스트가 잘렸다. 다음 영역을 우선 수정했다.

| 영역 | 변경 |
| --- | --- |
| 공통 확인 다이얼로그 | 제목·설명의 기본 줄 수 제한 제거, 전체 내용 스크롤, 실제 버튼 문구 폭에 따른 세로 배치 |
| 공통 메인 헤더·홈 알람 목록 | 제목·안내·요일·목적지 줄바꿈, 최소 높이, 시각·토글 및 진행 중인 알람 정보의 자동 재배치 |
| 알람 설정 | 요일 칩의 측정 폭 확보와 여러 행 배치, 목적지·알람음 높이 확장, 긴 안내 줄바꿈 |
| 저장 영역 | 저장 문구에 맞춰 버튼 높이 확장, 실제 하단 영역 높이를 제외한 본문 스크롤 공간 확보 |
| 시간 선택기 | 숫자와 오전·오후의 실제 측정 크기로 휠·셀·중앙 패딩 조정, 폭 부족 시 오전·오후 분리 및 시·분 세로 배치 |

앱의 시스템 글자 배율을 제한하거나 글자 크기를 자동 축소하지 않는다. Android 비선형 배율을 고려해 글자 폭·높이를 `TextMeasurer`로 구한다. 기본 크기의 일곱 요일 배치는 픽셀 반올림 누적까지 고려한다.

공통 헤더는 알람·기록·모임 화면에서 함께 사용한다. 그 외 개별 화면 전체에 대한 최대 글자 크기 QA는 이번 수정 범위에 포함하지 않는다.

## 자동 검증

Android API 35의 Robolectric Native Graphics에서 `RuntimeEnvironment.setFontScale`로 시스템 설정을 변경한다. `LocalDensity`를 덮어쓰는 방식은 사용하지 않는다.

- 320dp 폭, 480/568dp 높이의 기본 1.0·중간 1.3·최대 2.0 배율.
- 시간 선택기는 3.2 배율의 세로 배치도 추가 검증한다.
- 실제 텍스트 줄 폭·높이, 말줄임과 줄 수 제한, 버튼 위치 및 동작을 검사한다.
- 다이얼로그의 긴 설명·확인, 홈 토글·삭제, 일곱 요일, 목적지·알람음 선택, 비활성·저장 중 안내 및 하단 저장을 검사한다.
- 시 12→1, 분 59→0 순환과 오전·오후 변경을 검사한다.
- 기본 크기의 다이얼로그 버튼·홈 시각과 토글·일곱 요일 가로 배치를 검사한다.

```sh
./gradlew :shared:testAndroidHostTest :androidApp:lintRelease \
  :androidApp:assembleDebug :shared:iosSimulatorArm64Test \
  -PciVerification=true --no-configuration-cache
```

최종 실행 결과: Android 호스트 테스트 1,096개, iOS 공통·플랫폼 테스트 1,157개 통과. Android 린트와 Debug 앱 빌드도 통과했다. 추가된 큰 글자 레이아웃·동작 테스트는 15개다.

큰 글자 테스트만 실행할 때:

```sh
./gradlew :shared:testAndroidHostTest --tests '*LargeTextTest' \
  -PciVerification=true --no-configuration-cache
```

테스트 렌더링 이미지는 `shared/build/reports/large-text/`에 생성된다. 이는 OS 화면 캡처와 별도의 Robolectric 결과다.

## 실제 OS 렌더링 확인

서버 연결과 알람 예약 없이 실제 공통 컴포넌트를 표시하는 임시 QA 진입점으로 확인했다. 임시 진입점은 검증 후 제거했다. 글자 설정은 검증 후 원래 값으로 복원했다.

| 환경 | 화면·설정 | 결과 |
| --- | --- | --- |
| Android 에뮬레이터, API 37 | 720×1280px, 320dpi = 360×640dp, 표시 밀도 고정, 시스템 글자 100%·130%·200% | 기본·중간·최대 배치 확인. 200%의 홈 정보, 시간 선택기, 요일, 긴 비활성 안내, 다이얼로그 버튼 및 설정 끝까지 스크롤 확인 |
| iPhone 17e 시뮬레이터, iOS 26.5 | 390×844pt, `large`, `extra-extra-extra-large`, `accessibility-extra-extra-extra-large` | 기본 가로 배치, 큰 텍스트에서 오전·오후 분리, 접근성 최대에서 시·분 세로 배치 확인. 긴 비활성 안내, 홈, 다이얼로그 및 본문 끝과 저장 버튼 확인 |
| iOS 큰 텍스트 조작 | 일반 큰 텍스트에서 시간 선택 항목 직접 클릭 | 11→12→1시, 59→0분, 오후→오전 변경 후 접근성 값 확인 |

작은 화면 320dp 조합은 자동 검증으로 보완했다. 위 OS 확인은 변경한 컴포넌트의 표시·스크롤·선택 확인이며 앱 전체 네비게이션이나 실제 알람 예약 E2E 테스트는 아니다.

### Android 기본·200% 비교

[기본 크기 설정](images/large-font/android-setup-100.png) · [200% 설정 상단](images/large-font/android-setup-200.png) · [설정 끝과 저장](images/large-font/android-setup-end-200.png) · [다이얼로그 끝과 버튼](images/large-font/android-dialog-end-200.png)

### iOS 기본·접근성 최대 텍스트 비교

[기본 크기 설정](images/large-font/ios-setup-default.png) · [설정 상단과 시·분 세로 배치](images/large-font/ios-setup-max.png) · [설정 끝과 저장](images/large-font/ios-setup-end-max.png) · [확인 다이얼로그](images/large-font/ios-dialog-max.png)

검증 중 Android 에뮬레이터에서 글자 배율을 복원한 뒤 일부 글리프가 누락되는 현상이 한 차례 있었다. 앱을 다시 실행해도 남았지만 에뮬레이터 재시작 후 같은 바이너리에서 정상 표시됨을 확인했다. 위 기본 크기 이미지는 재시작 후 캡처다.
