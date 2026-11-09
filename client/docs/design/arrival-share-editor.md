# 도착 공유 편집기

- 디자인: https://www.figma.com/design/AoRETtkXoDQFXgF544UFIo?node-id=911-10798
- `arrival_editor_photo.png` 및 `arrival_editor_*.svg`: Figma 원본 자산. Android XML은 같은 SVG 경로와 viewport를 보존한 벡터 리소스다. Material 아이콘 의존성을 추가하지 않는다.
- 카드는 정사각형 중앙 크롭을 사용한다. RINGOUT은 항상 표시하며 날짜와 기상 시간은 독립적으로 숨길 수 있다.
- 기상 시각은 해당 도착 결과의 실제 알람 해제 시각을 사용한다. 없는 경우 기상 시간 조작을 비활성화하고 도착 날짜를 표시한다.
- 편집 ViewModel은 부모 도착 회차에 묶여 공유 왕복과 새 미션 표시 중 유지된다. 확인 저장은 도착 화면에서만 수행한다.
- 카메라·갤러리·저장·OS 공유를 연결했다. 선택 사진은 앱 내부 파일로 보관하며, 편집 초안은 파일 참조·토글·표시 날짜/시각만 저장한다. Compose 이미지 객체와 PNG 바이트는 ViewModel에 보관하지 않는다.
- Android: Photo Picker / 외부 카메라 + 좁은 FileProvider 경로 / MediaStore. API 26–28만 저장소 쓰기 권한을 요청한다. 공유 PNG는 캐시에 생성해 읽기 권한과 함께 OS 공유 창으로 전달한다.
- iOS: PHPicker / 카메라 권한 + UIImagePickerController / PhotoKit 추가 전용 권한 / UIActivityViewController. PHPicker 임시 파일은 콜백 안에서 방향 보정·축소 후 앱 내부 파일로 복사한다.
- 카드의 Compose 그리기 명령을 1080×1080 GraphicsLayer에 기록해 PNG로 인코딩한다. 미리보기와 같은 그리기 명령을 사용하며 툴바와 버튼을 포함하지 않는다. 작업 중 사진·토글·뒤로가기를 막아 내보낼 상태를 유지한다.
- 선택 취소·저장 실패·외부 공유는 도착 결과 확인과 독립적이다. 파일을 읽을 수 없으면 내보내기를 막고 사진 재선택을 안내한다.
- 자동 검증: PNG 실제 크기·카드 영역, 사진 회전·축소, 토글·취소·실패·중복 실행·초안 복원. 실제 기기에서 카메라 촬영, OS 권한 설정, 사진 보관함 확인 및 외부 앱 수신은 별도 기기 검증 대상이다.

참고: [Compose GraphicsLayer](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayer), [PhotoKit](https://developer.apple.com/documentation/photos/phphotolibrary).

- 카드 영역을 누르면 로고·날짜·시간을 검정색(#000000)과 흰색(#FFFFFF)으로 왕복 전환한다. 선택 색상은 초안과 내보낸 PNG에 반영하고, 내보내기 중에는 변경을 막는다. 기존 초안은 흰색으로 복원한다.
