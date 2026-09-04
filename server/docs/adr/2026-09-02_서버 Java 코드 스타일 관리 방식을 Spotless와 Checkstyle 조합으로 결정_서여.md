# 서버 Java 코드 스타일 관리 방식을 Spotless와 Checkstyle 조합으로 결정

- 작성일: 2026-09-02
- 작성자: 서여
- 결정 대상: 링아웃 서버의 Java 코드 스타일 자동 정리와 CI 검증 방식

## 배경

서버 코드가 늘어나면서 import 순서, 들여쓰기, 줄 길이, text block 작성 방식 같은 기계적인 스타일 차이가 PR 리뷰에 섞일 수 있다. 이런 차이는 기능 동작과 직접 관련이 없지만, 리뷰 시간을 늘리고 파일마다 코드 모양이 달라지는 원인이 된다.

처음에는 Checkstyle만 적용하는 방식을 검토했다. 그러나 Google Java Style 기반 룰셋을 그대로 적용하면 기존 서버 코드에서 많은 위반이 발생한다. 특히 들여쓰기, Javadoc 강제, import 정렬, text block 포맷, 줄 길이 위반이 한 번에 잡힌다. Checkstyle은 위반을 찾아 CI에서 실패시키는 데 적합하지만, 대량의 기존 포맷 위반을 자동으로 고치지는 못한다.

따라서 코드 스타일 관리는 자동 수정 도구와 검증 도구의 역할을 나누어 결정해야 한다.

## 결정 요인

- 팀원이 로컬에서 기계적인 포맷 위반을 쉽게 고칠 수 있어야 한다.
- CI는 포맷과 스타일 규칙 위반을 안정적으로 감지해야 한다.
- Google Java Style을 기준으로 삼되, 서버 팀 컨벤션인 4-space 들여쓰기를 반영해야 한다.
- 모든 public class와 method에 Javadoc을 강제해 형식적인 주석이 늘어나는 상황은 피해야 한다.
- 서버 애플리케이션 코드와 테스트 코드 모두 같은 방식으로 검사되어야 한다.
- Swagger 문서 인터페이스는 Java 코드이지만 API 문서 선언의 성격이 강하므로, annotation과 text block의 가독성이 유지되어야 한다.
- 추후 PMD, SpotBugs 같은 정적 분석 도구를 추가할 수 있도록 스타일 검사와 품질 검사의 책임을 분리해야 한다.

## 고려한 대안

### 대안 1: Checkstyle만 사용

## 장점

- Gradle의 `check` 태스크에 자연스럽게 연결된다.
- Google Java Style 룰셋을 기반으로 많은 스타일 규칙을 CI에서 검증할 수 있다.
- XML 설정으로 팀 컨벤션에 맞게 일부 규칙을 조정할 수 있다.

## 단점

- 자동 수정 기능이 거의 없어 기존 코드 위반을 사람이 직접 고쳐야 한다.
- Google Style 원본 룰셋을 그대로 적용하면 Javadoc 강제, 100자 줄 길이, text block 포맷 등으로 대량의 위반이 발생한다.
- PR 작성자는 어떤 명령으로 포맷을 고쳐야 하는지 별도 도구가 없으면 알기 어렵다.

### 대안 2: Spotless와 google-java-format만 사용

## 장점

- `spotlessApply`로 import 정렬, 들여쓰기, 줄바꿈 같은 기계적 스타일을 자동 수정할 수 있다.
- google-java-format을 사용하면 팀원이 각자 IDE 설정을 맞추지 않아도 같은 결과를 얻을 수 있다.
- AOSP 옵션을 사용하면 4-space 계열 포맷을 적용할 수 있다.

## 단점

- 포맷터가 다루지 않는 스타일 정책을 세밀하게 검증하기 어렵다.
- Javadoc 강제 여부, 특정 코드 패턴 금지, text block 세부 규칙 같은 항목은 Checkstyle보다 표현력이 낮다.
- 자동 포맷을 통과해도 팀이 금지하고 싶은 스타일이 남을 수 있다.

### 대안 3: Spotless와 Checkstyle을 함께 사용

## 장점

- Spotless가 기계적인 포맷 위반을 자동으로 정리한다.
- Checkstyle이 CI에서 팀 컨벤션 위반을 검증한다.
- 자동 수정 가능한 문제와 사람이 판단해야 하는 문제를 분리할 수 있다.
- Google Java Style을 기준으로 삼되, 서버 팀 컨벤션에 맞는 예외를 명시적으로 남길 수 있다.
- Swagger 문서 인터페이스처럼 formatter 적용 시 가독성이 떨어지는 파일은 제한적으로 예외 처리할 수 있다.

## 단점

- Gradle 설정과 CI 검증 단계가 Checkstyle 단독보다 늘어난다.
- Spotless와 Checkstyle의 규칙이 맞지 않으면 포매팅 후에도 Checkstyle이 실패할 수 있다.
- 두 도구의 역할과 실행 명령을 팀원이 이해해야 한다.
- 예외 대상 파일이 생기므로 일반 Java 코드와 Swagger 문서 코드의 관리 기준을 구분해야 한다.

### 대안 4: PMD 또는 SpotBugs를 스타일 도구로 사용

## 장점

- PMD는 코드 품질 규칙과 중복 코드 탐지에 강하다.
- SpotBugs는 런타임 버그 가능성, null 처리, equals/hashCode 실수 같은 문제를 찾는 데 유용하다.
- 스타일 정착 이후 서버 품질 게이트로 확장할 수 있다.

## 단점

- 두 도구는 주 목적이 코드 포맷이 아니므로 스타일 관리의 1차 도구로는 맞지 않는다.
- Google Java Style 기반 포맷 자동 수정이나 import 정렬 문제를 해결하지 못한다.
- 초기 적용 시 스타일 문제와 품질 문제가 한꺼번에 섞여 도입 범위가 커진다.

## 결정

서버 Java 코드 스타일 관리는 Spotless와 Checkstyle을 함께 사용한다.

Spotless는 자동 포맷 도구로 사용한다. `google-java-format`의 AOSP 옵션을 사용해 4-space 계열 포맷을 적용하고, import 정렬, 사용하지 않는 import 제거, annotation 포맷 정리를 수행한다. 팀원은 스타일 위반을 수동으로 고치기 전에 `./gradlew spotlessApply`를 먼저 실행한다.

다만 Swagger 문서 인터페이스(`**/*ControllerApi.java`)는 Spotless 적용 대상에서 제외한다. 현재 서버에는 `controller/docs` 아래의 문서 인터페이스뿐 아니라 `AuthControllerApi`, `TestControllerApi`처럼 다른 위치의 문서 인터페이스도 존재한다. 이 파일들은 annotation 중첩과 JSON text block이 많아 `google-java-format` 적용 시 문서 구조가 지나치게 깊어지고, API 예시를 한눈에 읽기 어려워진다. 따라서 API 문서 선언의 가독성을 우선해 수동 포맷을 허용한다.

Checkstyle은 CI 검증 도구로 사용한다. Google Java Style 기반 룰셋을 사용하되, 들여쓰기는 서버 팀 컨벤션에 맞춰 4-space 기준으로 조정한다. 실제 tab character는 허용하지 않고, 서버 Gradle 검증에서 4-space 기준을 강제한다.

Checkstyle은 전체 서버 Java 코드에 적용하되, Swagger 문서 인터페이스에서 Spotless 제외 정책과 충돌하는 annotation indentation, text block 포맷 규칙은 suppression으로 조정한다. Swagger 문서 인터페이스도 import 정렬, star import 금지, 탭 문자 금지처럼 문서 가독성과 충돌하지 않는 기본 규칙은 유지한다.

Google Java Style 원본 룰셋 중 Javadoc 강제 규칙은 서버 애플리케이션 코드에는 적용하지 않는다. 컨트롤러, 서비스, 도메인 클래스의 모든 public/protected 타입과 메서드에 Javadoc을 요구하면 설명 가치가 낮은 형식적 주석이 늘어날 수 있기 때문이다. API 설명은 Swagger 문서와 테스트에서 검증하고, 코드에서 반드시 설명이 필요한 복잡한 정책이나 예외적인 판단에만 주석을 남긴다.

PMD와 SpotBugs는 이번 결정의 1차 적용 대상에서 제외한다. 두 도구는 코드 스타일 관리가 안정화된 뒤 코드 품질과 버그 탐지 목적의 별도 ADR 또는 이슈에서 검토한다.

## 긍정적 결과

- PR 리뷰에서 import 정렬, 들여쓰기, 줄바꿈 같은 기계적인 지적이 줄어든다.
- 팀원은 `spotlessApply`로 대부분의 포맷 위반을 자동 수정할 수 있다.
- CI는 Checkstyle을 통해 자동 포맷으로 해결되지 않는 스타일 위반을 감지할 수 있다.
- Google Java Style을 기준으로 삼으면서도 서버 팀의 4-space 들여쓰기 컨벤션을 유지할 수 있다.
- Javadoc 강제를 제외해 형식적인 주석 증가를 막고, 의미 있는 문서화에 집중할 수 있다.
- Swagger 문서 인터페이스의 annotation과 예시 JSON을 한 파일에서 읽기 쉬운 형태로 유지할 수 있다.
- 스타일 검사와 품질 검사의 책임이 분리되어 PMD, SpotBugs 도입 시 범위를 명확히 할 수 있다.

## 부정적 결과와 트레이드오프

- Spotless와 Checkstyle 두 도구를 함께 관리해야 하므로 Gradle 설정이 늘어난다.
- 두 도구의 포맷 규칙이 충돌하면 `spotlessApply` 이후에도 Checkstyle 위반이 남을 수 있다.
- Google Java Style 원본과 다르게 Javadoc 강제 규칙을 제외하므로, 문서화 누락을 도구가 일괄적으로 막지는 못한다.
- `google-java-format`은 포맷 선택지를 거의 제공하지 않으므로 팀 선호와 다른 줄바꿈 결과가 나와도 수용해야 한다.
- Swagger 문서 인터페이스는 Spotless 자동 포맷 대상이 아니므로, 해당 파일의 포맷 일관성은 작성자와 리뷰어가 더 직접적으로 확인해야 한다.
- Swagger 문서 인터페이스에 대한 Checkstyle suppression 범위가 넓어지면 실제 스타일 문제를 놓칠 수 있으므로, suppression은 annotation indentation과 text block 포맷처럼 충돌이 확인된 규칙에 한정한다.
- 기존 서버 코드 전체가 새 규칙을 통과하려면 초기 포맷 정리 작업이 필요하다.
