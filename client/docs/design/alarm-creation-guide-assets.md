# 알람 생성 가이드 에셋

피그마 파일 `AoRETtkXoDQFXgF544UFIo`의 다음 가이드 프레임을 참조한다.

| 단계 | 프레임 | 원본 SVG |
| --- | --- | --- |
| 알람 추가 | `852:6220` | `alarm_guide_add_arrow.svg` |
| 시간 | `852:6270` | `alarm_guide_time_arrow.svg` |
| 요일 | `852:6359` | `alarm_guide_time_arrow.svg` |
| 목적지 | `852:6448` | `alarm_guide_destination_arrow.svg` |
| 반복 간격 | `852:6537` | `alarm_guide_interval_arrow.svg` |
| 알람음 | `852:6626` | `alarm_guide_interval_arrow.svg` |
| 생성 | `852:6715` | `alarm_guide_create_arrow.svg` |

SVG 원본은 수정 없이 `shared/src/commonMain/composeResources/drawable/`에 저장한다.
원본의 루트 크기에는 선의 외곽 여백이 포함되어 있다. 앱은 해당 크기와 피그마의 17.56도 회전을 보존한다.
작은 화면에서 설명이 반대편으로 이동하면 화살표도 함께 회전하여 실제 버튼을 가리킨다.

## 서체

- 이름: 교보 손글씨 2025 이유빈 (`Kyobo Handwriting 2025 lyb`)
- 배포자: 교보문고
- 공식 안내와 라이선스: https://store.kyobobook.co.kr/handwriting/font
- 공식 원본 다운로드: https://store.kyobobook.co.kr/api/s3/download/hwrt-handwritingFont-font?fileName=KyoboHandwriting2025lyb.ttf
- 적용 파일: `shared/src/commonMain/composeResources/font/kyobo_handwriting_2025_lyb.ttf`
- 확인일: 2026-10-22

공식 안내는 개인·기업의 무료 사용, 모바일 기기 탑재, 애플리케이션 GUI 및 폰트 임베딩을 허용한다.
폰트 자체의 수정·포맷 변경·개작·개명·재배포·유료 양도·재판매는 금지한다.
앱에는 내려받은 원본을 변경 없이 임베딩하며, 별도 폰트 배포물로 제공하지 않는다.
