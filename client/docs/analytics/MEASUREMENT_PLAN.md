# Android 목적지 알람 분석 측정 계획

## 1. 목적과 범위

이 문서는 Android Firebase Analytics에서 목적지 알람의 첫 설정부터 미션 결과까지를 측정하는 계약을 정의한다. 핵심 질문은 다음과 같다.

1. 첫 목적지 알람을 만든 사용자가 첫 미션을 완료하는가?
2. 첫 완료 후 7일 안에 두 번째, 세 번째 이상 미션을 시작하는가?
3. 첫 완료 후 7일 안에 미션 만료 또는 강제 종료를 경험하는가?

Android에서 Analytics 수집이 허용된 모든 사용자의 해당 이벤트를 계측한다. 다만 아래 지표는 계측 릴리스 이후의 `first_open`을 관측할 수 있어 진짜 첫 사용임을 확인할 수 있는 사용자만 분석한다. 따라서 이 문서의 사용자 단위는 계정이 아니라 Firebase의 `user_pseudo_id`가 나타내는 앱 설치 단위다. 앱 재설치, 앱 데이터 삭제 또는 Analytics 식별자 초기화 이후에는 다른 사용자 단위로 관측될 수 있다.

## 2. 이벤트 계약

이벤트 이름과 파라미터는 배포 후 의미를 바꾸지 않는다. 같은 의미의 이벤트를 새로 정의해야 한다면 기존 파라미터를 재해석하지 말고 계약 버전을 별도로 올린다.

| 이벤트 | 발생 시점 | 필수 파라미터 | 중복 방지 단위 |
| --- | --- | --- | --- |
| `destination_alarm_created` | 새 목적지 알람이 저장되고 예약까지 성공한 직후. 기존 알람 수정은 제외한다. | `creation_index`, `schedule_type`, `repeat_day_count` | 한 번의 새 알람 생성 |
| `destination_alarm_ringing_started` | 실제 알람 울림이 시작된 직후 | `retry_attempt` | 한 번의 울림 시도 |
| `destination_mission_started` | 미션의 `Tracking` 상태가 영속 저장된 직후 | `use_index`, `retry_attempt` | 한 번의 미션 시작 시도 |
| `destination_mission_completed` | 도착 판정이 성공으로 확정된 직후 | `use_index`, `retry_attempt`, `elapsed_bucket` | 한 번의 성공 상태 전이 |
| `destination_mission_expired` | 제한 시간 안에 도착하지 못해 해당 시도가 만료된 직후 | `use_index`, `retry_attempt`, `elapsed_bucket` | 한 번의 만료 상태 전이 |
| `force_end_hold_started` | 진행 중인 미션에서 사용자가 강제 종료 홀드 제스처를 시작한 직후 | `use_index`, `retry_attempt` | 한 번의 홀드 제스처 시작 |
| `force_end_hold_cancelled` | 홀드 완료가 확정되기 전에 손을 떼거나 제스처·화면 수명주기가 중단된 직후 | `use_index`, `retry_attempt`, `hold_duration_ms` | 한 번의 홀드 제스처 취소 |
| `force_end_hold_completed` | 홀드 임계 시간을 충족해 강제 종료 확인 다이얼로그가 표시되기 직전 | `use_index`, `retry_attempt`, `hold_duration_ms` | 한 번의 홀드 제스처 완료 |
| `destination_mission_force_ended` | 사용자가 진행 중인 미션을 명시적으로 강제 종료한 직후 | `use_index`, `retry_attempt`, `elapsed_bucket` | 한 번의 강제 종료 상태 전이 |

프로세스 재시작, 중복 브로드캐스트, Compose 재구성으로 같은 논리 이벤트를 다시 보내지 않는다. 로컬에서는 원시 식별자를 Analytics에 전송하지 않고도 동일한 상태 전이를 한 번만 claim하도록 처리한다.

### 2.1 파라미터 정의

| 파라미터 | Firebase 형식 | 허용값 | 정의 |
| --- | --- | --- | --- |
| `creation_index` | 정수(`int_value`) | 1 이상의 정수 | 이 설치에서 성공한 **새 목적지 알람 생성**의 순서. 첫 생성은 1이며 수정은 증가시키지 않는다. 삭제 후 새로 만들면 증가한다. |
| `use_index` | 정수(`int_value`) | 1 이상의 정수 | 이 설치에서 실제로 시작된 **새 목적지 미션**의 순서. 새 미션 시작이 확정될 때만 원자적으로 증가한다. 울림만 발생하고 미션이 시작되지 않으면 배정하지 않는다. |
| `retry_attempt` | 정수(`int_value`) | 0 이상의 정수 | 같은 `use_index`의 시도 번호. 최초 시도는 0이고 다시 울림 후 재시작할 때마다 1씩 증가한다. 재시도는 새 사용으로 세지 않으며 기존 `use_index`를 유지한다. |
| `schedule_type` | 문자열(`string_value`) | `once`, `weekly` | 생성된 알람의 일정 유형 |
| `repeat_day_count` | 정수(`int_value`) | `once`는 0, `weekly`는 1~7 | 선택된 반복 요일 수 |
| `elapsed_bucket` | 문자열(`string_value`) | `under_5m`, `5_to_15m`, `15_to_30m`, `over_30m` | 해당 미션 시도가 시작된 뒤 결과 상태 전이까지의 경과 시간 구간. 경계는 각각 5분 미만, 5분 이상 15분 미만, 15분 이상 30분 미만, 30분 이상이다. |
| `hold_duration_ms` | 정수(`int_value`) | 0 이상의 정수 | 단조 시계로 측정한 현재 홀드 제스처의 시작부터 취소 또는 완료까지의 경과 밀리초. 벽시계 시각이나 미션 진행 시간을 뜻하지 않는다. |

`destination_alarm_ringing_started`에는 `use_index`를 보내지 않는다. 울림은 미션 시작을 보장하지 않으므로 이 시점에 인덱스를 선점하면 실제 사용 순서에 빈 번호가 생긴다.

### 2.2 강제 종료 홀드와 실제 결과의 경계

홀드 이벤트 3개는 UI 제스처의 마찰을 측정하며 미션 결과를 뜻하지 않는다.

- `force_end_hold_started`에는 `hold_duration_ms`를 보내지 않는다. 아직 종료 시점이 없기 때문이다.
- 한 번의 정상적인 홀드 시도는 `force_end_hold_started` 뒤 `force_end_hold_cancelled` 또는 `force_end_hold_completed` 중 하나로 끝난다. 사용자가 다시 시도하면 같은 `use_index`와 `retry_attempt`로 새 이벤트 묶음을 보낼 수 있다.
- 앱이 백그라운드로 전환되거나 화면이 사라져 홀드를 더 이상 확인할 수 없으면 경과 시간과 관계없이 `force_end_hold_cancelled`로 끝낸다.
- `force_end_hold_completed`는 사용자가 홀드 임계 시간을 충족했다는 뜻일 뿐, 미션 상태 전이가 성공했다는 뜻은 아니다. 상태 검증이나 저장이 실패하면 이후 `destination_mission_force_ended`가 없을 수 있다.
- `destination_mission_force_ended`만 실제 강제 종료 상태 전이가 성공했다는 결과 이벤트다. 따라서 강제 종료 경험률에는 이 이벤트만 사용한다.

정상 흐름은 `force_end_hold_started` → `force_end_hold_completed` → `destination_mission_force_ended`이며, 중도 이탈 흐름은 `force_end_hold_started` → `force_end_hold_cancelled`다. 프로세스 종료나 앱 비정상 종료 시에는 started 이벤트만 남을 수 있으므로 홀드 퍼널 분석에서 별도 미완료로 취급한다.

## 3. 개인정보 및 데이터 최소화

다음 값은 이벤트 이름, 이벤트 파라미터, 사용자 속성 어디에도 보내지 않는다.

- 목적지 이름, 별칭, 주소, 검색어
- 위도·경도, 이동 경로, 도착 거리, 위치 정확도
- 알람 시각, 정확한 미션 시작·종료 시각을 복제한 파라미터
- 알람음 이름 또는 URI, 알림 본문, 사용자가 입력한 자유 텍스트
- 원시 `alarmId`, `occurrenceId`, 로컬 데이터베이스 키 또는 이를 단순 해시한 값
- 이메일, 전화번호, 광고 식별자 등 직접·간접 식별자

분석에는 Firebase가 제공하는 `user_pseudo_id`, `event_timestamp`, 위에서 승인한 이벤트 파라미터만 사용한다. 미션 진행 시간은 정확한 값 대신 `elapsed_bucket`을 사용한다. `hold_duration_ms`는 위치나 벽시계 정보가 없는 짧은 UI 제스처 경과 시간에만 사용한다. `user_id`는 설정하지 않는다.

Android Manifest에서 Advertising ID 수집과 광고 개인화 신호를 기본 비활성화한다. 이 설정은 제품 사용 측정에는 영향을 주지 않으며, 향후 광고 기능이 필요해지더라도 개인정보 정책과 사용자 동의 흐름을 먼저 확정한 뒤 별도 변경한다.

## 4. 분석 모집단과 관찰 규칙

### 4.1 진짜 첫 사용을 관측할 수 있는 사용자

첫 사용 코호트에는 다음 조건을 모두 만족하는 설치만 포함한다.

1. Analytics 계측이 포함된 릴리스와 조회 기간 안에서 자동 수집 이벤트 `first_open`이 관측된다.
2. 그 이후 `destination_alarm_created`와 `creation_index = 1`이 관측된다.
3. 필수 인덱스가 없거나 1 미만인 이벤트는 지표 계산에서 제외한다.

계측 릴리스 이전부터 앱을 사용해 `first_open`을 조회할 수 없는 기존 설치는 이벤트 자체는 수집하되 첫 사용 지표에서는 제외한다. 분석 시작일 또는 허용 앱 버전을 바꿀 때 코호트 정의도 함께 기록한다.

### 4.2 첫 완료와 7일 윈도우

- **첫 완료**: 위 코호트 사용자가 첫 알람 생성 이후 `destination_mission_completed`와 `use_index = 1`을 처음 보낸 시점이다. 재시도 끝에 성공해도 `use_index`가 1이면 첫 완료다.
- **앵커**: 첫 완료의 `event_timestamp`다.
- **7일 윈도우**: `(앵커, 앵커 + 168시간]`의 rolling window다. 캘린더 기준 D7이 아니다.
- **성숙 코호트**: 데이터 조회 종료 시각이 앵커보다 최소 168시간 뒤여서 전체 윈도우를 관찰할 수 있는 사용자만 첫 완료 후 지표의 분모에 포함한다.
- 지연 업로드는 Firebase 일별 테이블 정정 기간이 지난 뒤 재산출한다. 스트리밍 당일 테이블만으로 최종 지표를 확정하지 않는다.

## 5. 지표 정의

모든 비율은 사용자 기준이다. 만료율과 강제 종료율도 시도 수 기준 결과 비율이 아니라, 성숙한 첫 완료 사용자 중 7일 안에 해당 사건을 한 번 이상 경험한 사용자의 비율이다. 한 사용자가 같은 사건을 여러 번 보내도 분자는 1명으로 센다.

| 지표 | 분모 | 분자 |
| --- | --- | --- |
| 첫 완료율 | 진짜 첫 사용을 관측했고 `creation_index = 1` 생성이 있는 고유 사용자 | 그중 첫 생성 이후 `destination_mission_completed`, `use_index = 1`이 있는 고유 사용자 |
| 7일 내 두 번째 사용률 | 7일 윈도우가 성숙한 첫 완료 고유 사용자 | 앵커 후 168시간 안에 `destination_mission_started`, `use_index = 2`가 있는 사용자 |
| 7일 내 3회 이상 사용률 | 7일 윈도우가 성숙한 첫 완료 고유 사용자 | 앵커 후 168시간 안에 `destination_mission_started`, `use_index >= 3`가 있는 사용자 |
| 7일 내 만료 경험률 | 7일 윈도우가 성숙한 첫 완료 고유 사용자 | 앵커 후 168시간 안에 `destination_mission_expired`가 한 번 이상 있는 사용자 |
| 7일 내 강제 종료 경험률 | 7일 윈도우가 성숙한 첫 완료 고유 사용자 | 앵커 후 168시간 안에 `destination_mission_force_ended`가 한 번 이상 있는 사용자 |

`force_end_hold_completed`는 강제 종료 경험률의 분자에 포함하지 않는다. 이 이벤트와 `destination_mission_force_ended` 사이의 이탈은 강제 종료 요청 후 실제 상태 전이가 실패한 별도 진단 신호다.

첫 완료율은 조회 종료일까지 누적된 전환율이며 7일 제한을 적용하지 않는다. 최근에 첫 알람을 만든 사용자가 아직 울림을 기다리는 오른쪽 절단 효과가 있으므로 주간 리포트에서는 `first_created_at` 코호트별 수치도 함께 확인한다.

`retry_attempt`가 달라도 같은 `use_index`는 동일 사용이다. 따라서 재시도는 두 번째 또는 세 번째 사용률을 부풀리지 않는다. 한 미션이 만료된 뒤 재시도에서 완료될 수 있으므로 완료 경험과 만료 경험은 상호 배타적이지 않다.

기준 SQL은 [retention_7d.sql](./retention_7d.sql)이다. 결과에는 지표명, 분모 사용자 수, 분자 사용자 수, `SAFE_DIVIDE`로 계산한 비율이 포함된다.

## 6. 가설 판단 기준

판단은 다음 순서로 한다.

1. 7일 윈도우가 성숙한 첫 완료 사용자가 15명 미만이면 비율 변동이 크므로 **판단 보류**하고 추가 관측한다.
2. 첫 완료율 70% 이상, 7일 내 두 번째 사용률 40% 이상, 7일 내 3회 이상 사용률 25% 이상을 모두 만족하면 **가설 지지**로 판단한다.
3. 두 번째 사용률이 20% 미만이거나 3회 이상 사용률이 10% 미만이면 **가설 기각**으로 판단한다.
4. 위 조건에 해당하지 않으면 **부분 지지**로 판단한다. 대표적으로 두 번째 사용률은 40% 이상이지만 3회 이상 사용률이 25% 미만인 경우, 두 번째 사용률이 20~39%인 경우, 첫 완료율은 높지만 반복 사용률이 낮은 경우가 여기에 해당한다.

만료 경험률과 강제 종료 경험률은 가설 지지 여부를 직접 결정하는 기준이 아니라 반복 사용이 낮은 원인을 해석하기 위한 진단 지표로 사용한다.

## 7. Firebase와 BigQuery 설정 작업

1. Android 앱을 Firebase 프로젝트에 등록하고 올바른 `google-services.json`을 빌드 변형에 연결한다.
2. Firebase Analytics 수집 및 개인정보 고지/동의 흐름을 제품 정책과 배포 지역에 맞게 설정한다.
3. Firebase 프로젝트를 BigQuery에 연결하고 Analytics 일별 내보내기를 활성화한다.
4. 계측 릴리스 날짜와 앱 버전을 기록한다. SQL의 프로젝트, 데이터셋, 시작·종료 날짜 placeholder를 실제 값으로 교체한다.
5. DebugView에서는 이벤트명과 파라미터 타입을 검증하되, 디버그 기기 트래픽은 운영 리포트에서 제외한다.
6. 첫 리포트는 가장 이른 첫 완료로부터 168시간이 지나고 일별 export가 확정된 뒤 생성한다.

## 8. 출시 전 데이터 품질 점검

- 각 이벤트의 필수 파라미터 누락률이 0%인지 확인한다.
- `creation_index`와 `use_index`가 1부터 단조 증가하고, `retry_attempt > 0`에서 `use_index`가 바뀌지 않는지 확인한다.
- 최초 울림만 있고 미션을 시작하지 않은 테스트에서 `use_index`가 증가하지 않는지 확인한다.
- 결과 이벤트가 대응하는 시작 이벤트보다 먼저 발생하지 않는지 확인한다.
- 같은 상태 전이를 중복 전달해도 Analytics 이벤트가 한 번만 기록되는지 확인한다.
- `force_end_hold_started`에는 `hold_duration_ms`가 없고, cancelled/completed에는 0 이상의 값이 있는지 확인한다.
- 정상적인 한 번의 홀드 시도에서 cancelled와 completed가 동시에 기록되지 않는지 확인한다.
- `force_end_hold_completed`만 있는 테스트 사용자가 강제 종료 경험률 분자에 포함되지 않는지 확인한다.
- 전송된 `event_params.key`에 주소, 좌표, 이름, 원시 ID 등 금지 키가 없는지 확인한다.
- SQL 결과에서 `3회 이상 사용률 <= 두 번째 사용률 <= 100%`인지 확인한다. 위반 시 인덱스 누락 또는 순서 손상을 조사한다.
