## 1. 목적

이 문서는 프로젝트의 디렉터리 구조와 Presentation 계층의 UI 작성 규칙을 정의한다.

새로운 화면이나 컴포넌트를 구현할 때 이 문서를 기준으로 파일 위치, 역할, 네이밍, Preview 및 테마 사용 여부를 확인한다.

---

## 2. 디렉터리 구조

### 2.1 기본 원칙

패키지는 화면 단위로 구성한다.

각 화면 패키지는 다음 요소를 포함한다.

- 화면을 구성하는 `Screen` 파일
- UI 상태를 관리하는 `ViewModel`
- 화면 내부에서 사용하는 재사용 가능한 컴포넌트 디렉터리

```
presentation/
└── home/
    ├── HomeScreen.kt
    ├── HomeViewModel.kt
    └── component/
        ├── AlarmCard.kt
        ├── NextAlarmCard.kt
        ├── EmptyAlarmView.kt
        └── HomeTopBar.kt
```

---

## 3. 계층별 책임

### 3.1 Presentation

사용자에게 화면을 표시하고 UI 상태 및 사용자 이벤트를 처리한다.

주요 책임은 다음과 같다.

- 화면 단위 패키지 구성
- 컴포넌트를 조합해 화면 구성
- UI 상태 표시
- 사용자 입력 및 이벤트 전달
- `ViewModel`을 통한 UI 상태 관리

Presentation 계층에 비즈니스 로직이나 서버 통신 로직을 직접 작성하지 않는다.

```
presentation/
└── {screen}/
    ├── {ScreenName}Screen.kt
    ├── {ScreenName}ViewModel.kt
    └── component/
        └── {ComponentName}.kt
```

### 3.2 Domain

애플리케이션의 핵심 비즈니스 로직을 담당한다.

주요 책임은 다음과 같다.

- 도메인 모델 정의
- 비즈니스 규칙과 정책 구현
- 유스케이스 정의
- Repository 인터페이스 정의

예를 들어 알람의 활성화 여부, 다음 알람 계산, 반복 요일 판단 등의 규칙은 Domain 계층에서 처리한다.

### 3.3 Data

외부 데이터와의 통신 및 데이터 제공을 담당한다.

주요 책임은 다음과 같다.

- 서버 API 호출
- 요청 및 응답 데이터 모델 정의
- Repository 구현
- 외부 데이터를 Domain 모델로 변환
- 원격 및 로컬 데이터 소스 관리

서버 응답 모델을 Presentation 계층에서 직접 사용하지 않는다.

---

## 4. Presentation 계층 코드 컨벤션

### 4.1 Screen 작성 규칙

화면마다 하나의 `Screen` 파일을 작성한다.

`Screen` 파일에서는 개별 UI를 직접 길게 구현하기보다 `component` 패키지에 정의된 컴포넌트를 조합하여 전체 화면을 구성한다.

```kotlin
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAlarmClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HomeTopBar()

        if (uiState.alarms.isEmpty()) {
            EmptyAlarmView()
        } else {
            NextAlarmCard(alarm = uiState.nextAlarm)

            uiState.alarms.forEach { alarm ->
                AlarmCard(
                    alarm = alarm,
                    onClick = { onAlarmClick(alarm.id) },
                )
            }
        }
    }
}
```

### 4.2 ViewModel 작성 규칙

각 화면 패키지는 해당 화면의 UI 상태 관리 책임을 갖는 `ViewModel`을 포함한다.

`ViewModel`은 다음 역할을 담당한다.

- 화면에 필요한 UI 상태 제공
- 사용자 이벤트 처리
- Domain 계층 호출
- 비동기 작업 수행
- 로딩, 성공, 빈 화면, 오류 상태 관리

`ViewModel`이 Composable 함수나 Compose UI 객체를 직접 참조하지 않도록 한다.

### 4.3 UI 상태 분리

화면의 상태는 명시적인 UI 상태 모델로 표현한다.

```
data class HomeUiState(
    val isLoading: Boolean = false,
    val alarms: List<Alarm> = emptyList(),
    val nextAlarm: Alarm? = null,
    val errorMessage: String? = null,
)
```

화면에서 필요한 데이터는 가능한 한 하나의 UI 상태 객체로 전달한다.

### 4.4 Component 작성 규칙

화면 내부에서 의미 있는 UI 영역은 별도의 컴포넌트로 분리한다.

다음과 같은 경우 컴포넌트 분리를 고려한다.

- 여러 화면에서 재사용할 수 있는 UI
- 하나의 독립적인 역할을 수행하는 UI
- 자체적인 상태나 사용자 이벤트를 갖는 UI
- Screen 파일의 가독성을 떨어뜨리는 복잡한 UI

화면 전용 컴포넌트는 해당 화면의 `component` 패키지에 배치한다.

```
presentation/home/component/AlarmCard.kt
```

여러 화면에서 공통으로 사용하는 컴포넌트는 프로젝트의 공통 UI 패키지에 배치한다.

```
presentation/common/component/
```

---

## 5. Composable 작성 규칙

### 5.1 함수 이름

Composable 함수는 PascalCase로 작성한다.

```
@Composable
fun AlarmCard()
```

Screen 컴포저블은 `{화면명}Screen` 형식으로 작성한다.

```
@Composable
fun HomeScreen()
```

Preview 함수는 `{대상명}Preview` 형식으로 작성한다.

```
@Preview
@Composable
private fun AlarmCardPreview()
```

### 5.2 Modifier

재사용 가능한 Composable은 가능한 한 `Modifier`를 파라미터로 제공한다.

`modifier`는 선택 파라미터 중 가장 앞에 배치하며 기본값으로 `Modifier`를 사용한다.

```
@Composable
fun AlarmCard(
    alarm: Alarm,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
)
```

전달받은 `modifier`는 컴포넌트의 최상위 레이아웃에 적용한다.

### 5.3 상태 호이스팅

재사용 가능한 컴포넌트는 상태를 직접 소유하기보다 필요한 값과 이벤트를 파라미터로 전달받는다.

```
@Composable
fun AlarmCard(
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
)
```

컴포넌트 내부에서 `ViewModel`을 직접 참조하지 않는다.

### 5.4 이벤트 콜백

사용자 이벤트는 `on` 접두사를 사용한다.

```
onClick
onAlarmClick
onEnabledChange
onRetry
```

---

## 6. Preview 작성 규칙

모든 Screen과 Component는 최소 하나 이상의 Preview 함수를 가져야 한다.

Preview는 다음 조건을 만족해야 한다.

- 실제 앱 실행 없이 렌더링할 수 있어야 한다.
- `ViewModel`이나 네트워크 연결에 의존하지 않아야 한다.
- 의미 있는 샘플 데이터를 사용해야 한다.
- 앱 테마를 적용해야 한다.

### Component Preview

```kotlin
@Preview(showBackground = true)
@Composable
private fun AlarmCardPreview() {
    AppTheme {
        AlarmCard(
            alarm = AlarmPreviewData,
            onClick = {},
        )
    }
}
```

### Screen Preview

```kotlin
@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    AppTheme {
        HomeScreen(
            uiState = HomeUiState(
                alarms = PreviewAlarms,
                nextAlarm = PreviewAlarms.first(),
            ),
            onAlarmClick = {},
        )
    }
}
```

화면에 빈 상태, 오류 상태, 로딩 상태가 존재한다면 각 상태의 Preview를 추가하는 것을 권장한다.

```kotlin
HomeScreenPreview()
HomeScreenEmptyPreview()
HomeScreenLoadingPreview()
HomeScreenErrorPreview()
```

---

## 7. 색상 및 테마 규칙

Composable 내부에서 색상 코드를 직접 하드코딩하지 않는다.

### 금지 예시

```
Text(
    text = "다음 알람",
    color = Color(0xFF222222),
)
```

### 권장 예시

```
Text(
    text = "다음 알람",
    color = AppTheme.colors.textPrimary,
)
```

프로젝트에서 사용하는 색상은 `AppTheme` 또는 테마 관련 파일에 정의한다.

```
data class AppColors(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val error: Color,
)
```

Material Theme를 사용하는 경우에도 컴포넌트에서 직접 색상 코드를 선언하지 않고 테마 값을 사용한다.

```
MaterialTheme.colorScheme.primary
MaterialTheme.colorScheme.surface
MaterialTheme.colorScheme.onSurface
```

Preview에도 반드시 앱 테마를 적용하여 실제 화면과 동일한 색상 및 스타일을 확인할 수 있도록 한다.

---

## 8. 네이밍 규칙

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| 화면 파일 | `{화면명}Screen.kt` | `HomeScreen.kt` |
| ViewModel 파일 | `{화면명}ViewModel.kt` | `HomeViewModel.kt` |
| UI 상태 | `{화면명}UiState` | `HomeUiState` |
| UI 이벤트 | `{화면명}UiEvent` | `HomeUiEvent` |
| 컴포넌트 파일 | `{컴포넌트명}.kt` | `AlarmCard.kt` |
| Preview 함수 | `{대상명}Preview` | `AlarmCardPreview` |
| 이벤트 콜백 | `on{이벤트명}` | `onAlarmClick` |

파일명과 파일 내부의 대표 Composable 이름은 동일하게 작성한다.

---

## 9. 의존 방향

각 계층의 의존 방향은 다음 규칙을 따른다.

```
Presentation → Domain
Data → Domain
```

- Presentation은 Domain의 모델과 유스케이스를 사용할 수 있다.
- Data는 Domain에 정의된 Repository 인터페이스를 구현할 수 있다.
- Domain은 Presentation이나 Data에 의존하지 않는다.
- Presentation은 서버 API 구현을 직접 참조하지 않는다.

---

## 10. 신규 화면 작성 체크리스트

새로운 화면을 구현할 때 다음 항목을 확인한다.

### 디렉터리

- `presentation/{화면명}` 패키지를 생성했는가?
- `{화면명}Screen.kt`를 생성했는가?
- `{화면명}ViewModel.kt`를 생성했는가?
- 화면 전용 컴포넌트를 `component` 패키지에 배치했는가?

### Screen

- Screen에서 컴포넌트를 조합해 화면을 구성했는가?
- Screen에 비즈니스 로직이 포함되지 않았는가?
- UI 상태와 이벤트를 파라미터로 전달받는가?
- Screen Preview를 작성했는가?

### Component

- 의미 있는 UI 단위로 컴포넌트를 분리했는가?
- 각 컴포넌트에 Preview가 있는가?
- 재사용 가능한 컴포넌트가 `Modifier`를 제공하는가?
- 컴포넌트가 `ViewModel`에 직접 의존하지 않는가?

### Theme

- 색상 코드를 Composable에 직접 작성하지 않았는가?
- 모든 색상이 `AppTheme` 또는 `MaterialTheme`에 정의되어 있는가?
- Preview에 앱 테마가 적용되어 있는가?

### Architecture

- 비즈니스 로직이 Domain 계층에 위치하는가?
- 서버 API 호출이 Data 계층에 위치하는가?
- 계층 간 의존 방향을 준수하는가?