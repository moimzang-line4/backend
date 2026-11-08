# HARNESS — 자가 검증 가이드

AI 코딩 에이전트(Claude Code, Cursor, Windsurf 등)가 **스스로 빌드·실행·테스트해서 자기 작업을
검증**할 수 있도록 테스트 환경과 제약을 정의한다. 코드를 바꾼 뒤에는 이 문서 기준으로 반드시
검증하고, 실행하지 않은 명령을 실행했다고 보고하지 않는다.

## 전제 조건

- Java 21, Docker(Testcontainers가 MySQL 컨테이너를 띄운다 — 로컬에 MySQL을 직접 설치할 필요 없음)
- 최초 1회 `./gradlew` 가 없다면(아직 Gradle wrapper가 생성되지 않음) Gradle을 설치한 뒤
  `gradle wrapper --gradle-version <최신 안정 버전>` 을 실행해 wrapper를 만든다. 이후부터는
  반드시 `./gradlew`(또는 `gradlew.bat`)로만 빌드한다 — 로컬에 설치된 Gradle 버전에 의존하지 않는다

## 빌드 · 테스트

| 작업 | 명령 |
|---|---|
| 전체 빌드 | `./gradlew build` |
| 전체 테스트 | `./gradlew test` |
| 단일 테스트 클래스 | `./gradlew test --tests "com.moimzang.domain.settlement.ReceiptServiceTest"` |
| 단일 테스트 메서드 | `./gradlew test --tests "com.moimzang.domain.settlement.ReceiptServiceTest.메서드명"` |
| Q클래스 생성 확인 | `./gradlew clean compileJava` 후 `build/generated/sources/annotationProcessor/java/main`에 `QXxx.java` 존재 확인 |

**통과 기준**: `./gradlew build`가 0으로 종료하고(컴파일 + 테스트 전부 통과), 새로 추가/수정한
기능에 대응하는 테스트가 실제로 존재한다. 테스트 없이 "구현 완료"로 보고하지 않는다.

## 테스트 DB

Testcontainers로 MySQL을 띄운다(`spring-boot-testcontainers` + `org.testcontainers:mysql`).
별도의 `application-test.yml`을 두지 않고 `@ServiceConnection`으로 컨테이너를 연결한다 — 로컬 DB
설정과 완전히 분리되어, 실수로 로컬/운영 DB에 테스트 데이터가 섞이지 않는다.

컨테이너 재사용을 켜두면 테스트가 훨씬 빠르다. `~/.testcontainers.properties`에:

```properties
testcontainers.reuse.enable=true
```

이게 꺼져 있으면(기본값) 매 테스트 실행마다 컨테이너를 새로 띄우느라 느려서, 결국 테스트를
안 돌리게 된다 — 꼭 확인한다.

## 하면 안 되는 것 (IMPORTANT)

- `application.yml`의 `spring.jpa.hibernate.ddl-auto`를 `update`/`create`로 바꾸지 않는다.
  Flyway 마이그레이션(`src/main/resources/db/migration/`)이 스키마 정본이다. 엔티티를 바꿨으면
  마이그레이션 파일을 추가한다
- 테스트를 "일단 통과시키기" 위해 assertion을 느슨하게 바꾸거나 `@Disabled`로 건너뛰지 않는다.
  실패 이유를 먼저 보고한다
- git hook을 `--no-verify`로 우회하지 않는다
- `docs/erd.md`를 코드만 보고 추측해서 고치지 않는다 — 스키마를 바꿀 일이
  생기면 먼저 ERDCloud(`https://www.erdcloud.com/d/NzCidykN2L7iLABFh`)와 이 문서를 함께 갱신하고,
  그 결정을 `docs/decisions.md`에 남긴다
- `docs/api-spec.md`에 "미확정"으로 표시된 항목을 임의로 확정해서 구현하지 않는다 — 구현이
  필요해지면 먼저 사용자에게 확인하고, 결정을 `docs/decisions.md`에 추가한 뒤 "미확정" 표시를
  지운다

## 애플리케이션 기동 확인

```bash
./gradlew bootRun
curl -s localhost:8080/actuator/health   # actuator 추가 전까지는 아무 엔드포인트나 200 확인
```

Flyway가 `ddl-auto: validate`와 충돌 없이 마이그레이션을 끝내고 기동되는지 로그로 확인한다.
`NoClassDefFoundError`가 나면 대개 QueryDSL jakarta classifier 누락 또는 springdoc 버전 불일치다
— `project-init` 스킬의 "좌표는 외우지 말고 확인한다" 절차대로 BOM과 실제 해석 결과를 대조한다.

## 아직 비어 있는 것

- `domain/` 패키지 — 레퍼런스 도메인이 아직 없다. 첫 도메인을 구현하면 이후 도메인의 패턴이 된다
- Gradle wrapper(`gradlew`/`gradle-wrapper.jar`) — 이 저장소를 만든 에이전트가 로컬에 Gradle이
  없어서 생성하지 못했다. 위 "전제 조건"대로 최초 1회 생성이 필요하다
