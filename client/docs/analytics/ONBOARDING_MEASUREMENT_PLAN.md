# 첫 알람 온보딩 이탈 분석

## 목적과 범위

온보딩을 시작한 사용자가 어느 단계에서 진행을 멈추는지 측정한다. Android는 시간 → 요일 → 목적지 → 반복 간격 → 알람음의 5단계이며, iOS는 알람음을 제외한 4단계다. 플랫폼별 퍼널을 분리하고 공통 단계와 전체 완료율을 비교한다.

## 이벤트 계약 v1

| 이벤트 | 발생 시점 | 파라미터 |
| --- | --- | --- |
| `tutorial_begin` | 첫 알람 온보딩 단계가 최초 표시될 때. 앱 재실행 후 중복 기록하지 않는다. | `flow_name`, `flow_version`, `step_count` |
| `onboarding_step_viewed` | 실제 단계 진입. 뒤로 이동한 뒤 재진입 및 목적지 지도에서 가이드로 복귀도 포함한다. | 공통 파라미터 + `step_name`, `step_index` |
| `onboarding_submit` | 마지막 시작하기 요청이 검증을 통과하고 저장 요청으로 수락된 직후, 권한 확인 전에 기록한다. 완료 상태 저장만 재시도하는 경우도 포함한다. | 공통 파라미터 + 마지막 단계의 `step_name`, `step_index` |
| `tutorial_complete` | 알람 저장·예약 성공 뒤 온보딩 완료 상태의 로컬 저장까지 성공한 때. | `flow_name`, `flow_version`, `step_count` |

- `flow_name`: 문자열 `first_alarm`.
- `flow_version`: 정수 `1`. 이벤트 의미나 흐름이 바뀌면 계약 버전을 검토한다.
- `step_count`: Android 정수 `5`, iOS 정수 `4`.
- `step_name`: `time`, `weekdays`, `destination`, `interval`, `sound`.
- `step_index`: 해당 단계의 1부터 시작하는 순서. iOS의 submit은 `interval`/`4`, Android는 `sound`/`5`다.
- OS 구분은 Analytics의 기본 플랫폼 정보를 사용한다.

입력 조건을 만족하지 않은 다음 버튼, 처리 중 중복 탭, Compose 재구성, 같은 화면으로의 단순 포그라운드 복귀는 새 이벤트로 기록하지 않는다. 실제 실패 후 수락된 저장 재시도는 submit을 다시 기록한다.

기존 `destination_alarm_created` 이벤트는 유지한다. 중간 단계 통과는 다음 단계 진입으로 판정하므로 별도의 단계 완료 이벤트는 추가하지 않는다. 권한별 실패·버튼별 클릭·앱 종료 이벤트는 이번 범위에서 제외한다.

## 연결 위치와 중복 방지

- `OnboardingRoute`에서 실제 단계 표시를 `OnboardingViewModel.onStepVisible()`에 전달한다. ViewModel의 마지막 표시 단계로 재구성·Activity 재생성 중복을 막는다. 앱 프로세스 재시작 후에는 새 조회를 기록한다.
- 알람 저장 요청이 수락됐을 때 `onSubmitAccepted()`를 호출한다. 권한·저장 처리는 기존 `AlarmSetupCoordinator`를 사용한다.
- `AppBootstrapViewModel`은 `markOnboardingCompleted()` 성공 뒤 완료 이벤트를 기록한다. 온보딩 화면이 홈으로 전환되어 제거돼도 완료 기록을 화면 수명주기에 의존하지 않는다.
- `DefaultProductAnalyticsRecorder`에서 공통 이벤트와 파라미터를 만든다. Android Firebase 어댑터와 iOS Swift Firebase 어댑터에 기존 경로로 전달한다.
- 최초 시작·완료 claim은 Android SharedPreferences와 iOS NSUserDefaults에 보관한다. 키에 앱 버전이나 프로세스 ID를 포함하지 않아 재시작·업데이트로 최초 시작을 다시 배정하지 않는다.
- 기존 Analytics와 동일하게 로컬 claim 후 SDK에 전달하는 방식이다. 앱 강제 종료나 SDK 호출 오류 사이의 유실 가능성이 있으며 서버 수신의 exactly-once를 보장하지 않는다. Analytics 오류 때문에 온보딩·알람 저장을 실패시키지는 않는다.

## 지표와 관찰 기간

초기 관찰 기간은 최초 시작 이벤트부터 24시간이다. 시작이 관측되고 24시간이 지난 설치만 확정 지표에 포함한다. 앱 재실행으로 관찰 기간을 초기화하지 않는다.

| 지표 | 분모 | 분자 |
| --- | --- | --- |
| 단계별 미진행률 | 해당 단계에 순서대로 도달한 고유 설치 | 24시간 내 다음 구간에 도달하지 못한 설치 |
| 전체 완료율 | 시작한 고유 설치 | 24시간 내 완료한 설치 |
| 저장 요청 후 미완료율 | 저장 요청한 고유 설치 | 24시간 내 완료되지 않은 설치 |

마지막 입력 단계의 다음 구간은 submit이다. 반복 조회와 재시도는 구간별 설치당 1회로 집계한다. 24시간 이후 완료는 지연 완료로 별도 확인한다. 앱 종료 후 24시간 내 복귀·완료한 설치는 전체 이탈로 세지 않는다. 시작 이벤트가 없는 기존 사용자는 최초 시작 코호트에서 제외한다. 계정이 아닌 설치 단위이므로 재설치·데이터 초기화로 사용자 단위가 달라질 수 있다. 이벤트 미관측을 사용자의 의도적 포기로 단정하지 않는다.

## Firebase / GA4에서 별도로 할 작업

앱 코드 변경만으로 운영 콘솔의 설정이 생성되지는 않는다.

1. 이벤트 범위 맞춤 측정기준으로 `flow_name`, `flow_version`, `step_name`, `step_index`, `step_count`를 등록한다. 모두 분류/필터 용도로 사용하며 합계 지표로 해석하지 않는다.
2. Android·iOS별 퍼널을 만들고 `flow_name = first_alarm`, `flow_version = 1`로 필터링한다.
3. 정확한 최초 시작 기준 24시간 코호트 분석은 BigQuery 등에서 별도로 구성한다. 단순 단계 간 제한 시간과 혼동하지 않는다.
4. 디버그 기기에서 Firebase DebugView로 아래 시나리오를 확인하고 운영 분석에서 테스트 트래픽을 제외한다.

## 검증 시나리오

- 정상 Android 흐름: begin → time → weekdays → destination → interval → sound → submit → complete.
- 정상 iOS 흐름: begin → time → weekdays → destination → interval → submit → complete.
- 재구성·단순 앱 복귀에서는 같은 단계 조회가 늘어나지 않는다.
- 이전 단계로 이동하거나 목적지 지도에서 돌아오면 해당 단계 조회가 기록된다.
- 앱 재실행에서 begin은 늘어나지 않고 time 조회만 추가된다.
- 목적지 미설정 상태에서는 다음 단계 조회와 submit이 발생하지 않는다.
- 저장 중 중복 탭에서는 submit이 늘어나지 않는다. 실패 후 재시도에는 submit이 추가된다.
- 알람 예약·저장 성공만으로 complete가 발생하지 않는다. 온보딩 완료 상태 저장 실패 후 성공한 재시도에서만 complete가 기록된다.
- begin·complete는 기록기/저장소 인스턴스를 다시 만들어도 중복되지 않는다.
- 주소·좌표·이름·검색어·시각·알람음 URI·원시 DB 키와 자유 텍스트는 파라미터에 포함하지 않는다.

참고: [Firebase 이벤트 기록](https://firebase.google.com/docs/analytics/android/events), [Google Analytics 이벤트 명세](https://developers.google.com/analytics/devguides/collection/protocol/ga4/reference/events).
