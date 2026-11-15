# HARNESS — 자가 검증 가이드

AI 코딩 에이전트(Claude Code, Cursor, Windsurf 등)가 **스스로 빌드·실행·테스트해서 자기 작업을
검증**할 수 있도록 테스트 환경과 제약을 정의한다. 코드를 바꾼 뒤에는 이 문서 기준으로 반드시
검증하고, 실행하지 않은 명령을 실행했다고 보고하지 않는다.

## 전제 조건

- Java 21, Docker(Testcontainers가 MySQL 컨테이너를 띄운다 — 테스트 전용. 로컬 실행은 직접
  설치한 MySQL 8을 쓴다)
- Gradle wrapper(`gradlew`/`gradlew.bat`/`gradle/wrapper/gradle-wrapper.jar`)가 저장소에
  포함돼 있다. 반드시 `./gradlew`(또는 `gradlew.bat`)로만 빌드한다 — 로컬에 설치된 Gradle
  버전에 의존하지 않는다
- 로컬 실행 전 `.env.example`을 `.env`로 복사해 `DB_USERNAME`/`DB_PASSWORD`를 채우고, MySQL에
  `moimzang` 데이터베이스(`utf8mb4`)를 미리 만들어 둔다. 테이블은 `./gradlew bootRun` 시 Flyway가
  `src/main/resources/db/migration/`의 마이그레이션을 순서대로 전부 적용해 만든다

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
curl -s localhost:8080/health   # HealthCheckController가 제공하는 간이 헬스체크
```

Flyway가 `ddl-auto: validate`와 충돌 없이 마이그레이션을 끝내고 기동되는지 로그로 확인한다.
`NoClassDefFoundError`가 나면 대개 QueryDSL jakarta classifier 누락 또는 springdoc 버전 불일치다
— `project-init` 스킬의 "좌표는 외우지 말고 확인한다" 절차대로 BOM과 실제 해석 결과를 대조한다.

## 현재 구현 상태 (2026-11-14 기준)

10개 도메인 전부 엔티티·레포지토리·Controller/Api/Service/Mapper/ErrorCode 틀은 갖추고 있고,
비즈니스 로직 진행도는 다르다. 담당자별 배정과 남은 작업은 `docs/team-ownership.md`를 본다.

| 도메인 | 상태 |
|---|---|
| `meeting`, `file`, `chat` | 구현 완료(DTO·Service·테스트 있음). `file`은 결함 수정까지 끝남(decisions #42·#43) |
| `attendance`, `settlement` | 진행 중 — 차수·출석·영수증까지 구현됨, 정산(API-037~045)은 미구현 |
| `group`, `schedule`, `memberlist`, `note`, `budget` | 아직 껍데기 — DTO·Service 로직 없음 |

새 도메인을 건드리기 전에 그 패키지에 `dto/request`·`dto/response`와 Service 구현이 있는지
먼저 확인한다(없으면 그 시점에 추가한다).

테스트는 구현된 도메인마다 존재한다 — Service 단위 테스트(Mockito)가 기본이고, QueryDSL
커스텀 쿼리나 JPQL은 `@DataJpaTest` + Testcontainers 슬라이스 테스트로 따로 검증한다.
컨트롤러 슬라이스 테스트(`@WebMvcTest`)는 아직 없다. 전역 예외 핸들러처럼 HTTP 경로에서만
드러나는 변경은 `./gradlew bootRun` 후 실제 요청으로 확인한다.
