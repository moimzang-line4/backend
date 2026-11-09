# 팀 작업 분배

백엔드 3명이 API를 본격적으로 구현하기 전에 정한 도메인 담당 배정과 작업 경계 규칙.
설계 결정이 아니라 팀 운영 규칙이라 `docs/decisions.md`와 분리해 관리한다.

## 담당 배정

10개 도메인 패키지가 처음부터 서로 독립적으로 분리돼 있어서(entity/repository/
controller/service/mapper가 각자 패키지 안에 있음), 도메인 패키지를 통째로 한 명이
맡는다. 패키지를 쪼개서 두 명이 나눠 갖지 않는다.

| 담당 | 도메인(한글) | 패키지 |
|---|---|---|
| A | 그룹, 명단방, 일정, 운영메모 | `group`, `memberlist`, `schedule`, `note` |
| B | 차수·영수증·출석, 정산, 장부 | `attendance`, `settlement`, `budget` |
| C | 회의, 자료, AI채팅 | `meeting`, `file`, `chat` |

배정 기준:
- A: 그룹/기수/명단 등 다른 도메인의 기반이 되는 데이터와, 돈과 무관한 일정·메모
- B: 영수증 입력 → 정산 → 장부 기록으로 이어지는 money flow를 한 사람이 끝까지 담당.
  `attendance` 패키지에 차수(영수증)와 출석 체크가 한 파일(`AttendanceController`/
  `AttendanceService`)에 같이 있어서, 어차피 패키지 단위로만 나눌 수 있음
- C: 서로 FK로 연결되지 않는 독립적인 부가 기능

## 작업 경계 규칙

**원칙: 자기 도메인 패키지 외에는 건드리지 않는다.**

- Entity: ERD 필드가 이미 다 들어가 있어 스키마를 바꾸지 않는 한 남의 엔티티에 필드를
  추가할 일이 없다
- Service: 다른 도메인의 연관 엔티티가 필요하면 그 도메인의 Service를 호출하지 않고
  **Repository에서 직접 조회**한다(`my-spring-conventions` 컨벤션)
- Controller/DTO/Mapper: 전부 자기 도메인 API 표면이라 남의 것을 가져다 쓰지 않는다

**유일한 예외 — 다른 담당자의 Repository에 조회(또는 캐스케이드 삭제용) 메서드가
필요하면 자유롭게 추가한다.** `findBy*`/`@Query` 메서드 추가는 단순 쿼리 선언이라
위험이 적다. Service/Controller/Entity는 여전히 담당자만 수정한다.

**스키마·마이그레이션을 바꿔야 하는 경우는 이 규칙 밖이다.** 단톡방에 먼저 공지하고
진행한다.
