# 모임장 (moimzang) — Backend

동아리/학회 운영진이 일정·부원 명단·출석·정산·회의·자료를 관리하는 서비스의 백엔드.

##  문서 안내

| 문서 | 설명 |
|---|---|
| [AGENTS.md](./AGENTS.md) | 모든 코딩 에이전트(Claude, Codex 등)가 작업 전에 읽는 공통 가이드 — 스택·빌드/테스트 명령·DB 규칙·패키지 구조·참조 문서 링크 |
| [CLAUDE.md](./CLAUDE.md) | Claude Code 전용 보충 문서 — AGENTS.md를 가리키고, 적용되는 Claude 스킬 목록만 짧게 얹음 |
| [HARNESS.md](./HARNESS.md) | AI 코딩 에이전트가 스스로 빌드·테스트해 변경 사항을 검증하는 방법과 제약 |
| [docs/prd.md](./docs/prd.md) | 제품 요구사항 정의서 — 도메인별 핵심 기능과 전역 정책(정산·삭제·회의 등) |
| [docs/erd.md](./docs/erd.md) | 데이터 모델 정본 — 18개 테이블 전체 컬럼·FK·enum 도메인 |
| [docs/api-spec.md](./docs/api-spec.md) | API 명세서 — 79개 엔드포인트의 ID·요청/응답·에러코드·화면 매핑 |
| [docs/decisions.md](./docs/decisions.md) | 설계 결정 로그 — ERD·API 설계 중 합의한 결정과 그 근거를 시간순으로 기록 |

## 기술 스택
Spring Boot(Java 21) · MySQL 8 · JPA(Hibernate) · QueryDSL · Flyway · MapStruct · springdoc-openapi

## 시작하기
1. `git clone https://github.com/moimzang-line4/backend.git`
2. `.env.example`을 `.env`로 복사하고 `DB_USERNAME`/`DB_PASSWORD`를 채운다
3. 로컬 MySQL에 `moimzang` 데이터베이스 생성(`utf8mb4`)
4. `./gradlew bootRun` — Flyway가 스키마를 자동 적용한다

빌드·테스트 명령과 검증 제약은 [AGENTS.md](./AGENTS.md), [HARNESS.md](./HARNESS.md) 참고.

## Git Convention

### 브랜치 전략

| 브랜치 | 용도 |
|---|---|
| `main` | 배포 가능한 최종 브랜치 |
| `develop` | 개발 통합 브랜치 |
| `feature/{issue-number}-{feature-name}` | 기능 개발 브랜치 |
| `fix/{issue-number}-{bug-name}` | 버그 수정 브랜치 |
| `refactor/{issue-number}-{target}` | 리팩토링 브랜치 |
| `docs/{issue-number}-{doc-name}` | 문서 수정 브랜치 |
| `chore/{issue-number}-{task-name}` | 설정, 빌드, 기타 작업 브랜치 |

### 브랜치 예시
```text
feature/12-group-create
feature/23-settlement-receipt
fix/31-attendance-duplicate-name
refactor/42-schedule-service
docs/50-api-spec
chore/61-swagger-config
```

### 작업 흐름
- GitHub Issue를 생성한다.
- `develop` 브랜치에서 작업 브랜치를 생성한다.
- 기능 구현 후 로컬에서 테스트와 빌드를 확인한다([HARNESS.md](./HARNESS.md) 참고).
- 작업 브랜치를 원격 저장소에 push한다.
- `develop` 브랜치로 Pull Request를 생성한다.
- 최소 1명 이상의 코드 리뷰 승인을 받는다.
- CI 또는 빌드가 성공하면 PR을 merge한다.
- 데모 또는 배포 시점에 `develop`에서 `main`으로 merge한다.

### 브랜치 삭제 규칙
- `main`, `develop` 브랜치는 삭제하지 않는다.
- `feature/*`, `fix/*`, `refactor/*`, `docs/*`, `chore/*` 브랜치는 PR이 `develop`에 merge된 후 삭제한다.
- merge가 완료된 원격 브랜치는 PR 작성자가 삭제한다.
- 로컬 브랜치는 각자 작업자가 정리한다.
```text
git branch -d feature/12-group-create
git fetch --prune
```
- 아직 배포 전 문제가 생길 가능성이 있는 작업은 PR과 커밋 기록으로 추적한다.
- 이미 merge된 브랜치에서 추가 수정하지 않고, 필요한 수정은 새 브랜치를 생성해서 작업한다.

## Commit Convention

### 커밋 메시지 형식
```text
type(scope): subject
```
`scope`는 도메인 패키지명(`group`/`schedule`/`memberlist`/`attendance`/`settlement`/`note`/
`meeting`/`file`/`budget`/`chat`) 또는 `global`/`config`/`api` 등 작업 범위를 쓴다.

### type 규칙

| type | 의미 |
|---|---|
| feat | 새로운 기능 추가 |
| fix | 버그 수정 |
| docs | 문서 수정 |
| style | 코드 포맷팅, 세미콜론, 공백 등 기능 변경 없는 수정 |
| refactor | 리팩토링 |
| test | 테스트 코드 추가 또는 수정 |
| chore | 빌드, 설정, 패키지 관리 등 기타 작업 |
| perf | 성능 개선 |
| ci | CI/CD 설정 수정 |
| rename | 파일명 또는 폴더명 변경 |
| remove | 파일 또는 코드 삭제 |

### 커밋 예시
```text
feat(group): 그룹 생성 API 추가
feat(settlement): 정산 명단 저장 API 추가
fix(attendance): 동명이인 출석 처리 중복 등록 버그 수정
docs(api): 운영 공간 노출 기준 API 명세 수정
refactor(schedule): 안건 연결 로직 서비스로 분리
test(settlement): 입금 완료 중복 반영 방지 테스트 추가
chore(config): Swagger 설정 추가
```

## Code Style Convention

### 네이밍 규칙

| 구분 | 규칙 | 예시 |
|---|---|---|
| 클래스명 | PascalCase | `ScheduleService`, `SettlementController` |
| 메서드명 | camelCase | `createSchedule`, `findUnpaidAttendees` |
| 변수명 | camelCase | `scheduleId`, `paymentStatus` |
| 상수명 | UPPER_SNAKE_CASE | `MAX_UPLOAD_SIZE` |
| 패키지명 | lowercase | `com.moimzang.domain.schedule` |
| DB 테이블명 | snake_case **단수형** | `member_list`, `schedule_round` |
| DB 컬럼명 | snake_case | `group_term_id`, `billed_amount` |
| API URL | 복수형 리소스 중심 | `/api/schedules/{scheduleId}` |

### 패키지 구조
```text
com.moimzang
├─ domain
│   ├─ group
│   ├─ schedule
│   ├─ memberlist
│   ├─ attendance
│   ├─ settlement
│   ├─ note
│   ├─ meeting
│   ├─ file
│   ├─ budget
│   └─ chat
└─ global
    ├─ config
    ├─ entity
    ├─ exception
    ├─ response
    └─ storage
```

### 계층별 역할

| 계층 | 역할 |
|---|---|
| Controller | 요청/응답 처리, 비즈니스 로직 작성 금지 |
| Service | 비즈니스 로직, 트랜잭션 처리 |
| Repository | DB 접근 |
| Entity | DB 테이블 매핑 |
| DTO | 요청/응답 데이터 전달 |
| Mapper | Entity와 DTO 변환 |

### 코드 작성 규칙
- Controller에서는 비즈니스 로직을 작성하지 않는다.
- Entity를 API 응답으로 직접 반환하지 않는다.
- Request DTO와 Response DTO를 분리한다.
- 상태값은 문자열 하드코딩 대신 Enum을 사용한다(`@Enumerated(EnumType.STRING)`).
- 상태 변경이 있는 기능(출석 처리, 입금 확인, 회의 종료 등)은 `@Transactional`을 사용한다.
- 예외는 `CustomException`과 도메인별 `ErrorCode`를 사용한다.
- 모든 API 응답은 `BaseResponse`로 감싼다.
- 이 프로젝트는 인증·로그인이 없다([decisions.md](./docs/decisions.md) #1) — 사용자 식별·권한
  검증 관련 규칙은 적용하지 않는다.

## Git Comment Convention

### PR 리뷰 코멘트 태그

| 태그 | 의미 |
|---|---|
| `[필수]` | 반드시 수정해야 하는 내용 |
| `[제안]` | 더 나은 방향을 제안하는 내용 |
| `[질문]` | 의도를 확인하기 위한 질문 |
| `[공유]` | 참고하면 좋은 정보 |
| `[칭찬]` | 좋은 구현에 대한 피드백 |

### 리뷰 코멘트 예시
```text
[필수] 입금 완료 후에는 정산 명단 수정이 막혀야 합니다. 잠금 검증이 빠져 있어요.
[제안] 100원 단위 버림 계산은 SettlementCalculator 같은 별도 메서드로 분리하면 좋을 것 같습니다.
[질문] 차수 삭제 시 장부 항목은 고아 상태로 남긴다고 했는데, 이 분기에서 처리되나요?
[공유] 이 로직은 추후 AI 비서 카드 액션에서도 재사용될 수 있어서 Service 계층에 두는 게 좋아 보입니다.
[칭찬] 영수증 삭제 cascade 범위가 명확하게 분리되어 있어서 읽기 좋습니다.
```

## PR Merge Rules

### PR 제목 규칙
```text
[type] 작업 내용 요약
```
예시
```text
[feat] 정산 명단 저장 API 구현
[fix] 출석 동명이인 처리 오류 수정
[docs] API 명세서 운영 공간 영역 수정
```

### PR 본문
저장소의 PR 템플릿(`.github/PULL_REQUEST_TEMPLATE.md`)을 그대로 채운다 — 관련 이슈, 작업 내용,
변경된 API, 테스트 결과, 확인 필요 사항, 체크리스트 순.
