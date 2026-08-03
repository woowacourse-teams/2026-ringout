# RingOut

RingOut 팀 프로젝트 모노레포입니다.

## 프로젝트 구조

```text
.
└── client/    # Kotlin Multiplatform 클라이언트(Android, iOS)
```

클라이언트의 개발 및 실행 방법은 [`client/README.md`](./client/README.md)를 참고하세요.

## 클라이언트 빌드

```shell
cd client
./gradlew :androidApp:assembleDebug
```

iOS 앱은 `client/iosApp`을 Xcode에서 열어 실행합니다.
