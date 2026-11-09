# 모임장

@AGENTS.md 가 정본이다. 스택·명령·DB·구조·참조 문서는 거기에 있다.

## DB

MySQL 8. JPA 규칙은 `db-mysql` 스킬을 따른다. 테이블명 단수형(AGENTS.md 참고).

## 이 프로젝트에 적용되는 스킬

- `db-mysql` — ID 전략, 타입 매핑, 쿼리 문법, Flyway, Testcontainers
- `my-jpa-rules` — 엔티티 설계, 연관관계·페치 전략, 트랜잭션 경계
- `my-spring-conventions` — 계층 구조, DTO·Mapper, 예외 처리, 공통 응답
- `my-test-strategy` — 계층별 테스트 범위, Testcontainers, ErrorCode 검증
