# 모임장 — 공통 에이전트 가이드

동아리/학회 운영진(모임장)이 일정·부원 명단·출석·정산·회의·자료를 관리하는 서비스의 백엔드.
Claude Code, Codex 등 어떤 AI 코딩 에이전트가 작업하든 이 문서를 먼저 읽는다.
도구별로 더 필요한 내용은 그 도구 전용 파일(`CLAUDE.md` 등)에 짧게 얹는다.

## 스택

Spring Boot(Java 21) / MySQL 8 / JPA(Hibernate) / QueryDSL / Flyway / MapStruct / springdoc-openapi

## 명령 (IMPORTANT)

| | 명령 |
|---|---|
| 빌드 | `./gradlew build` |
| 테스트 | `./gradlew test` |
| 단일 테스트 | `./gradlew test --tests "com.moimzang.domain.*.ServiceNameTest"` |
| 애플리케이션 실행 | `./gradlew bootRun` |

코드를 변경한 뒤에는 반드시 테스트를 실행하고 결과를 보고할 것. "통과할 것 같다"는 보고로 인정하지 않는다.
테스트·빌드 검증 방법과 제약은 `HARNESS.md`를 본다.

## DB

MySQL 8. 테이블명은 **단수형**(`club_group`, `schedule_round` 등 — ERD가 이미 이 규칙으로
작성돼 있음). 문자셋은 `utf8mb4`.

## 구조

```
domain/<도메인>/   controller, dto(request/response), entity, exception, mapper, repository, service
global/            config, exception, response
```

베이스 패키지: `com.moimzang`

아직 구현된 도메인이 없다. 첫 도메인을 만들 때 레퍼런스가 되므로, 구조는
`my-spring-conventions`(Claude Code 스킬) 또는 동급 컨벤션 문서를 따른다.

## 참조 문서

- 제품 요구사항: `docs/prd.md`
- 데이터 모델: `docs/erd.md`
- API 명세: `docs/api-spec.md`
- 설계 결정 로그: `docs/decisions.md`

새 기능을 구현하기 전에 위 네 문서에서 해당 도메인 부분을 먼저 읽는다. `docs/api-spec.md`에
"미확정"으로 표시된 항목은 구현 시점에 정하고 그 결정을 `docs/decisions.md`에 追記한다.

## 이 프로젝트 고유 결정

- 인증·로그인·회원가입 없음 — 운영진 구분 없이 전원이 같은 권한으로 접근하는 데모 범위
- API 응답은 전부 `{success, code, message, data}` 래퍼로 감싼다(`docs/api-spec.md` 참고)
- enum 계열 컬럼은 SQL에서도 VARCHAR(네이티브 ENUM 안 씀), Java에서는 전용 enum 타입으로 매핑
  (`@Enumerated(EnumType.STRING)`) — 근거는 `docs/decisions.md` #8
- `application.yml`은 로컬/운영 분리 없이 하나로 운영하고, 환경별 값은 환경변수로 주입한다
- 웹소켓 사용하지 않음 — 전부 REST 요청/응답으로 충분(실시간 멀티클라이언트 동기화 요구사항 없음)
- 로깅은 AOP로 자동화하지 않는다. Service에 `@Slf4j`를 달고 필요한 지점에 직접 `log.info`/`log.warn`
  등을 쓴다

## 코드 주석·로그 규칙 (IMPORTANT)

코드 주석과 로그 메시지는 **그 자리의 로직(무엇을 왜 그렇게 했는지)만** 설명한다.
`API-001`, `DEC-101` 같은 문서 ID나 `docs/api-spec.md §5` 같은 섹션 번호를 절대 넣지 않는다.
문서는 재배치·갱신되지만 주석은 코드에 그대로 남아, 시간이 지나면 가리키는 대상이 바뀌거나
사라진 거짓 참조가 된다. 이유가 필요하면 번호를 가리키지 말고 이유 자체를 짧게 적는다.

Javadoc 마크업(`{@code ...}`, `{@link ...}`, `<p>`, `<b>` 등 HTML 태그)도 달지 않는다. 전부
평문으로 쓴다 — 태그 유무로 렌더링이 갈리는 걸 신경 쓰지 않아도 되고, 어디서 읽어도 똑같이 읽힌다.

```java
// 하면 안 됨
// API-023, DEC-105 참고: 전원 납부 완료 시에만 수입 1건 생성

// 이렇게
// 부분 납부 중에는 수입 행을 만들지 않는다 — 전원 완료 전까지는 금액이 확정되지 않기 때문
```
