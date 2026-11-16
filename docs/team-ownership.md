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

## 진행 상황과 남은 작업 (2026-11-14 기준)

도메인별 구현 상태는 `HARNESS.md`의 "현재 구현 상태" 표를 본다. 여기에는 담당별로 **지금 열려
있는 작업**만 적는다. 작업이 끝나면 해당 줄을 지운다.

### A — `group`, `memberlist`, `schedule`, `note`

네 도메인 모두 아직 껍데기다(엔티티·레포지토리·틀만 있음). 등록된 이슈는 없다.
`schedule`은 운영 공간 집계(API-012~016)까지 담당한다(decisions #13).

- 회의록-안건-일정 연결 구조가 확정됐다(decisions #44). `schedule` 쪽에 남은 작업은 두 가지다:
  **API-008 일정 삭제** 시 그 일정에 속한 회의록(`meeting.schedule_id`)과 안건을 함께 지우는 처리,
  **API-013·015·016** 구현(연결 후보 규칙은 decisions #44에서 확정, 후보 목록 조회는 C가
  API-083으로 제공한다). `meeting.schedule_id` 컬럼과 Flyway `V4`는 C가 이미 넣었다.
  **일정 삭제는 API 한 번으로 끝나야 한다** — 사용자가 회의록을 먼저 지우는 2단계 조작이 아니다.
  다만 그 API 안에서는 지우는 순서가 정해져 있다: 일정-안건 연결 → 일정 소속 회의록의 안건 →
  회의록 → 일정. `fk_meeting_schedule`가 RESTRICT라 자식을 남긴 채 일정 행부터 지우면
  `ERROR 1451`이 난다(실측 확인).
  **API-014는 명세에서 삭제됐다** — 자동 매칭 화면이 없어서다(decisions #45). 안건 연결은
  모달에서 직접 고르는 경로 하나뿐이다. API-015 응답에는 `meetingStatus`가 들어가야 한다 —
  종료된 회의의 안건은 일정 상세에서 읽기 전용이라 화면이 그 값으로 입력란을 잠근다(decisions #46)

### B — `attendance`, `settlement`, `budget`

- 정산 API 5건 미구현 — #57(명단 조회·저장·불러오기), #58(입금 확인·삭제·현황),
  #59(입금 계좌·정산 공지), #60(정산 홈), #61(영수증 AI 인식)
- #78 `RoundAttendeeRepositoryImplTest` cursor 테스트 플래키 — `created_at`이 `DATETIME`이라
  소수점 초가 날아가는데 cursor로 넘기는 `@CreatedDate` 값은 나노초를 갖는다. **이것 하나 때문에
  `./gradlew build`가 실패해서, 머지 전 로컬 빌드로만 방어하는 이 프로젝트에선 우선도가 높다.**
  `DATETIME` + cursor 조합을 쓰는 다른 도메인도 같은 문제를 가질 수 있다
- `budget`은 아직 껍데기다. 영수증 지출 행 생성은 API-074(정산 내역 새로고침)가 담당한다(decisions #41)

### C — `meeting`, `file`, `chat`

세 도메인 모두 구현 완료이고, `file`은 결함 수정까지 끝났다(decisions #42·#43).
회의록-안건-일정 연결(decisions #44~#46)도 `meeting` 쪽은 끝났다 — `meeting.schedule_id`(Flyway `V4`),
API-051의 `scheduleId`, API-052의 `scheduleId` 필터, 연결 후보 조회 API-083, 회의·안건 삭제 시
`schedule_agenda_link` 선삭제까지.

- #76 로컬 정적 경로 `/uploads/**`가 소프트 삭제된 파일까지 200으로 서빙한다 —
  `/api/files/{id}/content`에서 막은 삭제 차단이 이 경로로 우회된다. **B 담당과 합의가 필요하다**:
  정산 영수증 `imageUrl`이 이 정적 URL을 쓰고 있어서(decisions #41) 핸들러를 그냥 떼면 영수증
  이미지가 깨진다. 영수증 이미지도 통제되는 서빙 경로로 모을지 결정한 뒤 진행한다
- 미확정 1건이 남아 `meeting`에서 추가 구현 여부가 열려 있다 — `docs/api-spec.md` §7의 19번
  (회의 목록 "참석자 수" 산정 기준). 18번(`listSummary` 입력 경로)은 필드를 폐기해 해소됐다
- HTTP Range(206) 미지원 — `/api/files/{id}/content`는 `Range` 헤더를 무시하고 전체 바이트를
  보낸다. 업로드 상한이 20MB라 지금은 체감되지 않아 이슈로 등록하지 않았다. S3를 실제 운영에
  쓰기로 하면 presigned URL 전환과 함께 다시 본다(decisions #18·#42)

