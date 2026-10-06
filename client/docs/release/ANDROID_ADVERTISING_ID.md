# Android 광고 ID 설정

Ringout은 Firebase Analytics의 일반 이벤트를 수집하지만 광고 ID 수집과 광고 개인 최적화 신호는 비활성화한다. 앱 매니페스트의 AD_ID 제거 규칙은 의존 SDK가 추가하는 광고 ID 권한을 최종 매니페스트에서 제거한다.

## 빌드 검증

- Release 매니페스트 병합 보고서에서 measurement SDK 및 ads-identifier의 AD_ID 선언이 제거되는지 확인한다.
- 내부 테스트·운영 배포용 최종 AAB의 매니페스트에 `com.google.android.gms.permission.AD_ID`가 없는지 확인한다. 두 배포 모두 공통 main 매니페스트 설정을 사용한다.
- `google_analytics_adid_collection_enabled=false`와 `google_analytics_default_allow_ad_personalization_signals=false`를 유지한다.
- 실기기 DebugView에서 모임·미션 이벤트가 계속 수집되는지 확인한다.

## Play Console 적용

1. 권한 제거가 적용된 새 빌드를 업로드한다.
2. 다른 활성 트랙에도 광고 ID 권한이 남은 기존 빌드가 있는지 확인하고 필요한 경우 교체한다.
3. 앱과 의존 SDK의 광고 ID 미사용을 확인한 뒤 앱 콘텐츠의 광고 ID 사용 선언을 ‘아니요’로 저장한다.
4. 출시 검토 화면에서 광고 ID 선언과 빌드의 권한 불일치가 해소됐는지 확인한다.

매니페스트 변경은 이미 업로드한 빌드에 소급 적용되지 않는다. 이 변경만으로 운영 CD의 HTTP 400 원인이 해결됐다고 판단하지 않으며 API 상세 오류로 별도 확인한다.

참고: https://firebase.google.com/docs/analytics/android/configure-data-collection
