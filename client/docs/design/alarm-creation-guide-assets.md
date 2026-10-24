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

## 안내 동작

- 홈의 첫 안내는 강조된 알람 추가 버튼을 눌러야 넘어간다. 일반 알람 추가와 동일하게 초안을 만들고 알람 생성 화면으로 이동한다.
- 알람 생성 화면의 설정 안내에서는 강조 영역과 딤드 영역을 포함해 화면 어디든 터치하면 다음 안내로 이동한다.
- 생성 화면의 안내 중에는 시간·요일·반복 간격을 변경하거나 목적지·알람음 선택 화면을 열지 않는다.
- 마지막 안내에서는 기존 저장 버튼 위에 `시작하기` 버튼을 표시한다. 이 버튼을 눌러야 가이드 완료를 저장하며, 딤드나 설명을 눌러도 종료하지 않는다.
- `시작하기`는 알람을 생성하지 않으며, 가이드가 종료되면 기존 저장 버튼의 표시와 동작이 복원된다.
- 마지막 안내 종료 또는 건너뛰기 후에는 현재 화면과 초안을 유지하고 모든 설정을 정상적으로 조작할 수 있다.

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
