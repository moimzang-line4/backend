# API 명세 — 모임장

ERD(`docs/erd.md`)와 PRD(`docs/prd.md`)를 바탕으로 확정한 REST API 82개. 구현이 시작되면 springdoc이
생성하는 OpenAPI가 정본이 되고, 이 문서는 API ID·화면 매핑·설계 근거를 남기는 역할로 남는다.

## 1. 개요

- **베이스 URL**: `/api` (버전 프리픽스 없음)
- **인증**: 없음. 전체 엔드포인트 인증 불필요(`docs/decisions.md` #1 — 데모 범위, 로그인 없음)
- **공통 응답 포맷**: 모든 응답을 `{success, code, message, data}`로 감싼다.
  ```json
  { "success": true, "code": "COMMON_200", "message": "요청에 성공했습니다.", "data": { } }
  { "success": false, "code": "SETTLEMENT_409_RECEIPT_ALREADY_REGISTERED", "message": "이미 영수증이 등록된 차수입니다.", "data": null }
  ```
  HTTP 상태 코드는 해당 ErrorCode가 결정한다(§5).
- **페이징**: 검색어·필터가 있거나 데이터가 무한정 쌓일 수 있는 목록 API(§6에 "cursor 페이지네이션"
  표시)는 cursor 기반으로 페이지네이션한다. 쿼리 파라미터 `cursor`(선택, opaque 문자열, 생략하면
  첫 페이지), `size`(선택, 기본 20, 최대 100 — 초과분은 100으로 clamp)를 쓰고, 응답에서 기존
  배열 필드는 그대로 두되 `totalCount`는 빼고 `nextCursor`(다음 페이지 없으면 null)를 추가한다.
  cursor는 `(그 목록이 의미하는 정렬 기준 컬럼, id)` 복합 키를 base64로 인코딩한 값이다 —
  "최신순"이면 `createdAt`처럼 실제로 그 의미를 담은 컬럼을 쓰고, id는 동률일 때의 tiebreaker로만
  쓴다. 잘못된 cursor는 `COMMON_400`. 드롭다운 선택지나 상위 리소스 하나에 달린 소량 고정 목록
  (카테고리, 차수 목록 등)은 지금처럼 전체를 한 번에 반환한다 — `docs/decisions.md` #17.
  **예외**: "총 N개" 같은 UI 표시용 total은 페이지네이션 totalCount와 다르다 — cursor 없는 첫
  페이지 요청에서만 그 값을 계산해 내려주고 이후 페이지에는 안 내려주는 패턴은 허용한다(예:
  API-064 `totalCount`) — `docs/decisions.md` #19.
- **필드 표기**: JSON은 camelCase, DB는 snake_case. `id`/`createdAt`/`updatedAt`은 응답에만 있고
  요청 본문에는 없다. 금액은 숫자(number)로 직렬화한다(기존 Notion 명세 관행 유지 — 소수점이 없는
  정수 원화라 부동소수점 오차 위험이 낮음).

## 2. 전체 API 목록

| ID | Method | Path | 설명 | 관련 엔티티 | 인증 |
|---|---|---|---|---|---|
| API-001 | POST | /api/groups | 그룹 생성 | club_group, group_term | 불필요 |
| API-002 | GET | /api/groups/{groupId} | 그룹 정보 조회 | club_group, group_term | 불필요 |
| API-003 | PATCH | /api/groups/{groupId} | 그룹 정보 수정(그룹명·기수) | club_group, group_term | 불필요 |
| API-004 | POST | /api/terms/{termId}/schedules | 일정 등록 | schedule | 불필요 |
| API-005 | GET | /api/terms/{termId}/schedules | 일정 목록 조회(캘린더) | schedule | 불필요 |
| API-006 | GET | /api/schedules/{scheduleId} | 일정 상세 조회 | schedule | 불필요 |
| API-007 | PATCH | /api/schedules/{scheduleId} | 일정 수정 | schedule | 불필요 |
| API-008 | DELETE | /api/schedules/{scheduleId} | 일정 삭제 | schedule | 불필요 |
| API-009 | GET | /api/terms/{termId}/schedules/upcoming | 다가오는 운영 일정 조회 | schedule | 불필요 |
| API-010 | POST | /api/terms/{termId}/categories | 카테고리 추가 | schedule_category | 불필요 |
| API-011 | GET | /api/terms/{termId}/categories | 카테고리 목록 조회 | schedule_category | 불필요 |
| API-012 | GET | /api/terms/{termId}/operation-spaces | 운영 공간 목록 조회 | schedule, meeting | 불필요 |
| API-013 | POST | /api/schedules/{scheduleId}/agenda-links | 안건 연결 | schedule_agenda_link | 불필요 |
| API-015 | GET | /api/schedules/{scheduleId}/agenda-links | 연결된 회의록 조회 | schedule_agenda_link | 불필요 |
| API-016 | DELETE | /api/schedules/{scheduleId}/agenda-links/{linkId} | 회의록 연결 해제 | schedule_agenda_link | 불필요 |
| API-017 | POST | /api/terms/{termId}/member-lists | 명단방 생성 | member_list | 불필요 |
| API-018 | GET | /api/terms/{termId}/member-lists | 명단방 목록 조회 | member_list | 불필요 |
| API-019 | GET | /api/member-lists/{memberListId} | 명단방 상세(링크 관리) 조회 | member_list | 불필요 |
| API-020 | DELETE | /api/member-lists/{memberListId} | 명단방 삭제 | member_list | 불필요 |
| API-021 | GET | /api/public/member-lists/{joinToken} | 부원 등록 폼 조회(공개) | member_list | 불필요 |
| API-022 | POST | /api/public/member-lists/{joinToken}/members | 부원 등록(공개) | member | 불필요 |
| API-023 | GET | /api/member-lists/{memberListId}/members | 부원 목록 조회 | member | 불필요 |
| API-024 | POST | /api/member-lists/{memberListId}/members | 부원 추가(운영진) | member | 불필요 |
| API-025 | PATCH | /api/members/{memberId} | 부원 수정 | member | 불필요 |
| API-026 | DELETE | /api/members/{memberId} | 부원 삭제 | member | 불필요 |
| API-027 | GET | /api/schedules/{scheduleId}/rounds | 차수 목록 조회 | schedule_round | 불필요 |
| API-028 | POST | /api/schedules/{scheduleId}/rounds | 차수 추가 | schedule_round | 불필요 |
| API-029 | GET | /api/rounds/{roundId}/attendees | 차수 출석 현황 조회 | schedule_attendee, round_attendee | 불필요 |
| API-030 | POST | /api/rounds/{roundId}/attendees | 현장 인원 추가 | schedule_attendee | 불필요 |
| API-031 | PATCH | /api/rounds/{roundId}/attendees/{attendeeId} | 출석 처리·되돌리기 | round_attendee | 불필요 |
| API-032 | DELETE | /api/rounds/{roundId}/attendees/{attendeeId} | 차수에서 참가자 제외 | round_attendee | 불필요 |
| API-084 | POST | /api/rounds/{roundId}/attendees/import | 명단방 불러오기 | schedule_attendee, round_attendee | 불필요 |
| API-085 | PATCH | /api/categories/{categoryId} | 일정 카테고리 수정 | schedule_category | 불필요 |
| API-086 | PATCH | /api/meeting-categories/{categoryId} | 회의 카테고리 수정 | meeting_category | 불필요 |
| API-033 | GET | /api/rounds/{roundId}/receipt | 영수증 조회 | schedule_round | 불필요 |
| API-034 | PUT | /api/rounds/{roundId}/receipt | 영수증 등록 | schedule_round | 불필요 |
| API-035 | DELETE | /api/rounds/{roundId}/receipt | 영수증 삭제 | schedule_round, settlement_target, round_attendee | 불필요 |
| API-036 | POST | /api/rounds/{roundId}/receipt/ocr | 영수증 AI 인식 | — (저장 없음) | 불필요 |
| API-037 | GET | /api/rounds/{roundId}/settlement-targets | 정산 명단 조회 | settlement_target | 불필요 |
| API-038 | PUT | /api/rounds/{roundId}/settlement-targets | 정산 명단 저장 | settlement_target | 불필요 |
| API-039 | GET | /api/rounds/{roundId}/settlement-targets/import | 행사 명단 불러오기 | round_attendee | 불필요 |
| API-040 | PATCH | /api/schedules/{scheduleId}/payments/{attendeeId} | 입금 확인·되돌리기 | settlement_target | 불필요 |
| API-041 | DELETE | /api/schedules/{scheduleId}/payments/{attendeeId} | 입금 명단에서 삭제 | settlement_target | 불필요 |
| API-042 | GET | /api/schedules/{scheduleId}/payments | 입금 현황 조회 | settlement_target | 불필요 |
| API-043 | PUT | /api/rounds/{roundId}/account | 입금 안내 계좌 설정 | schedule_round | 불필요 |
| API-044 | POST | /api/schedules/{scheduleId}/settlement-notice | 정산 공지 생성 | schedule_round | 불필요 |
| API-045 | GET | /api/schedules/{scheduleId}/settlement | 정산 홈 조회 | schedule_round, settlement_target | 불필요 |
| API-046 | POST | /api/schedules/{scheduleId}/notes | 운영 메모 작성 | operation_note | 불필요 |
| API-047 | GET | /api/schedules/{scheduleId}/notes | 운영 메모 목록 조회 | operation_note | 불필요 |
| API-048 | PATCH | /api/notes/{noteId} | 운영 메모 수정 | operation_note | 불필요 |
| API-049 | DELETE | /api/notes/{noteId} | 운영 메모 삭제 | operation_note | 불필요 |
| API-050 | PATCH | /api/notes/{noteId}/todo | 할 일 완료 처리 | operation_note | 불필요 |
| API-051 | POST | /api/terms/{termId}/meetings | 회의 만들기 | meeting, meeting_agenda | 불필요 |
| API-052 | GET | /api/terms/{termId}/meetings | 회의 목록 조회 | meeting | 불필요 |
| API-053 | GET | /api/meetings/{meetingId} | 회의 상세 조회(진행·회의록) | meeting, meeting_agenda | 불필요 |
| API-054 | PATCH | /api/meetings/{meetingId} | 회의 정보 수정 | meeting | 불필요 |
| API-055 | DELETE | /api/meetings/{meetingId} | 회의 삭제 | meeting | 불필요 |
| API-056 | POST | /api/meetings/{meetingId}/agendas | 안건 추가 | meeting_agenda | 불필요 |
| API-057 | PATCH | /api/agendas/{agendaId} | 안건 내용 저장 | meeting_agenda | 불필요 |
| API-058 | DELETE | /api/agendas/{agendaId} | 안건 삭제 | meeting_agenda | 불필요 |
| API-059 | POST | /api/meetings/{meetingId}/end | 회의 종료 | meeting | 불필요 |
| API-060 | POST | /api/meetings/{meetingId}/summary | AI 요약 생성 | meeting | 불필요 |
| API-061 | PATCH | /api/meetings/{meetingId}/summary | 회의록 저장(요약 수정) | meeting | 불필요 |
| API-081 | POST | /api/terms/{termId}/meeting-categories | 회의 카테고리 생성 | meeting_category | 불필요 |
| API-082 | GET | /api/terms/{termId}/meeting-categories | 회의 카테고리 목록 조회 | meeting_category | 불필요 |
| API-083 | GET | /api/terms/{termId}/agendas/linkable | 연결 가능한 안건 목록 조회 | meeting_agenda, schedule_agenda_link | 불필요 |
| API-062 | GET | /api/files/policy | 파일 업로드 정책 조회 | — | 불필요 |
| API-063 | POST | /api/terms/{termId}/files | 파일 업로드 | file | 불필요 |
| API-064 | GET | /api/terms/{termId}/files | 파일 목록 조회 | file | 불필요 |
| API-065 | GET | /api/files/{fileId}/download-url | 파일 미리보기·다운로드 URL 발급 | file | 불필요 |
| API-066 | DELETE | /api/files/{fileId} | 파일 삭제 | file | 불필요 |
| API-067 | POST | /api/files/{fileId}/restore | 파일 삭제 되돌리기 | file | 불필요 |
| API-068 | GET | /api/terms/{termId}/files/zip | 파일 전체 다운로드(ZIP) | file | 불필요 |
| API-069 | GET | /api/terms/{termId}/ledger | 예산 요약·내역 조회 | ledger_entry, file | 불필요 |
| API-070 | POST | /api/terms/{termId}/ledger | 장부 직접 입력 | ledger_entry | 불필요 |
| API-071 | GET | /api/ledger/{entryId} | 장부 내역 상세 조회 | ledger_entry | 불필요 |
| API-072 | PATCH | /api/ledger/{entryId} | 장부 내역 수정 | ledger_entry | 불필요 |
| API-073 | DELETE | /api/ledger/{entryId} | 장부 내역 삭제 | ledger_entry | 불필요 |
| API-074 | POST | /api/terms/{termId}/ledger/sync | 정산 내역 새로고침 | ledger_entry | 불필요 |
| API-075 | GET | /api/terms/{termId}/ledger/export | 장부 엑셀 다운로드 | ledger_entry | 불필요 |
| API-076 | POST | /api/terms/{termId}/ai/chat-rooms | AI 대화방 생성 | ai_chat_room | 불필요 |
| API-077 | GET | /api/terms/{termId}/ai/chat-rooms | AI 대화방 목록 조회 | ai_chat_room | 불필요 |
| API-078 | POST | /api/ai/chat-rooms/{chatRoomId}/messages | AI 메시지 전송 | ai_message | 불필요 |
| API-079 | GET | /api/ai/chat-rooms/{chatRoomId}/messages | AI 메시지 목록 조회 | ai_message | 불필요 |
| API-080 | GET | /api/files/{fileId}/content | 파일 콘텐츠 서빙(다운로드·미리보기) | file | 불필요 |

## 3. CRUD 커버리지

| 엔티티 | 목록 | 상세 | 생성 | 수정 | 삭제 |
|---|---|---|---|---|---|
| club_group | 없음(그룹 1개만 다룸) | API-002 | API-001 | API-003 | 없음(탈퇴 기능 보류, decisions #1) |
| group_term | API-002 응답에 포함 | API-002 응답에 포함 | API-003(그룹 수정 시 생성) | 없음 | 없음 |
| schedule_category | API-011 | 없음(목록에서 충분) | API-010 | API-085 | 없음(화면에 삭제 버튼 없음) |
| schedule | API-005, API-009 | API-006 | API-004 | API-007 | API-008 |
| member_list | API-018 | API-019 | API-017 | 없음(화면에 수정 버튼 없음, 재생성만) | API-020 |
| member | API-023 | 없음(수정 화면이 상세 역할) | API-022(공개), API-024(운영진) | API-025 | API-026 |
| schedule_attendee | API-029(차수 출석 현황에 포함) | 없음 | API-030(현장), API-022/024(명단 등록 시 자동) | 없음 | API-032 |
| schedule_round | API-027 | API-033(영수증 중심) | API-028 | API-034(영수증 등록), API-043(계좌) | API-035(영수증 삭제 cascade) |
| round_attendee | API-029 | 없음 | API-030/API-022·024 등록 시 자동 | API-031 | API-032 cascade |
| settlement_target | API-037, API-042 | 없음 | API-038(명단 저장 시 일괄 생성) | API-040 | API-041 |
| operation_note | API-047 | 없음(목록 카드가 상세 역할) | API-046 | API-048, API-050 | API-049 |
| meeting | API-052 | API-053 | API-051 | API-054, API-059(종료), API-061(요약 저장) | API-055 |
| meeting_agenda | API-053 응답에 포함, API-083(연결 후보) | 없음 | API-056, API-051(생성 시 함께) | API-057 | API-058 |
| schedule_agenda_link | API-015 | 없음 | API-013 | 없음 | API-016, API-055·API-058 cascade |
| file | API-064 | 없음(목록 카드가 상세 역할) | API-063 | 없음(파일은 재업로드만) | API-066, API-067(복구) |
| ai_chat_room | API-077 | 없음(메시지 목록이 상세 역할) | API-076 | 없음 | 없음(화면에 삭제 기능 없음) |
| ai_message | API-079 | 없음 | API-078 | 없음(수정 불필요한 대화 로그) | 없음 |
| ledger_entry | API-069 | API-071 | API-070, API-074(자동) | API-072 | API-073 |

## 4. 화면 ↔ API 매핑

Figma 와이어프레임(`4호선톤_모임장`) 분석 기준. 화면명은 분석 당시 기준.

| 화면 | 호출 API |
|---|---|
| 그룹 만들기 | API-001 |
| 설정 메인 / 그룹 관리 | API-002, API-003 |
| 일정 등록 | API-004, API-011, API-010 |
| 홈 화면(선택 일정 카드, 다가오는 일정) | API-009, API-006 |
| 운영 공간 목록 | API-012 |
| 운영 공간 만들기 바텀시트 | API-004(일정 생성) / API-051(회의 생성) 분기 |
| 일정 수정 / 일정 삭제 | API-007, API-008 |
| 일정 상세 · 회의록 & 안건 섹션 | API-052(scheduleId, 회의록 카드), API-015(안건 카드), API-016 |
| 일정 상세 · 회의록 작성하기 | API-051(scheduleId 포함) |
| 안건 연결하기 모달 | API-083(후보 목록), API-013 |
| 부원 홈(명단 카드 목록) | API-018 |
| 명단방 생성 | API-017 |
| 링크 관리 | API-019 |
| 명단 조회 | API-023 |
| 부원 정보 수정 | API-025 |
| 부원 정보 직접 추가 | API-024 |
| 부원 등록 공유 폼 | API-021, API-022 |
| 참석·출석 메인 | API-027, API-029, API-031 |
| 현장 인원 추가 바텀시트 / 동명이인 확인 | API-030 |
| 정산·입금 메인 | API-045, API-027 |
| 영수증 인식 결과 등록 | API-036, API-034 |
| 영수증 직접 등록 | API-034 |
| 정산 명단 전체 인원 | API-037, API-040 |
| 정산 명단 차수 추가 | API-039, API-038 |
| 정산 공지 생성 | API-044 |
| 입금 안내 설정 | API-043 |
| 새 메모 작성 / 메모 목록 | API-046, API-047, API-048, API-050 |
| 회의 진행 | API-053, API-057 |
| 회의 종료 | API-059 |
| AI 회의 요약 | API-060, API-061 |
| AI 챗 | API-076, API-077, API-078, API-079, API-040(카드의 입금 확인 버튼), `navigation`으로 정산·출석·메모·일정·회의록·자료실·장부 화면 이동 |
| 자료 목록 / 문서 첨부 | API-064, API-063, API-065, API-080, API-066, API-067, API-068, API-062 |
| 자료 목록 · 활동사진 그리드 뷰 | API-064(`thumbnailUrl`), API-080(`variant=THUMBNAIL`) |
| 예산안 | API-069, API-070, API-071, API-072, API-073, API-074, API-075 |
| 설정 · 운영진 관리 · 모임장 변경 | 범위 제외(decisions #1) — 매핑 API 없음 |

## 5. ErrorCode 목록

### 공통 (CommonCode)
| 코드 | HTTP | 메시지 |
|---|---|---|
| COMMON_200 | 200 | 요청에 성공했습니다. |
| COMMON_201 | 201 | 생성에 성공했습니다. |
| COMMON_204 | 204 | 삭제에 성공했습니다. |
| COMMON_400 | 400 | 잘못된 요청입니다. |
| COMMON_404 | 404 | 정보를 찾을 수 없습니다. |
| COMMON_413 | 413 | 요청 용량이 허용 범위를 초과했습니다. |
| COMMON_500 | 500 | 서버 오류가 발생했습니다. |

### 그룹 (GroupErrorCode) — API-002, API-003
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| GROUP_404_NOT_FOUND | 404 | 존재하지 않는 그룹입니다. | API-002, API-003 |

### 일정 (ScheduleErrorCode) — API-004~011
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| SCHEDULE_400_INVALID_CURSOR | 400 | cursor 형식이 올바르지 않습니다. | API-005 |
| SCHEDULE_400_END_BEFORE_START | 400 | 종료 시간이 시작 시간보다 빠릅니다. | API-004, API-007 |
| SCHEDULE_400_OPERATION_SPACE_CANNOT_DISABLE | 400 | 운영 공간은 끌 수 없습니다. | API-007 |
| SCHEDULE_404_NOT_FOUND | 404 | 존재하지 않는 일정입니다. | API-006, API-007, API-008 |
| SCHEDULE_404_CATEGORY_NOT_FOUND | 404 | 존재하지 않는 일정 카테고리입니다. | API-004, API-007, API-085 |
| SCHEDULE_404_GROUP_TERM_NOT_FOUND | 404 | 존재하지 않는 기수입니다. | API-004, API-005, API-009, API-010, API-011 |
| SCHEDULE_409_CATEGORY_DUPLICATED | 409 | 이미 있는 카테고리입니다. | API-010, API-085 |

### 정산 (SettlementErrorCode) — API-034~045
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| SETTLEMENT_400_AMOUNT_NOT_POSITIVE | 400 | 금액은 양의 정수여야 합니다. | API-034 |
| SETTLEMENT_400_SUBSIDY_OUT_OF_RANGE | 400 | 지원금은 0원 이상이며 영수증 금액 이하여야 합니다. | API-034 |
| SETTLEMENT_409_RECEIPT_ALREADY_REGISTERED | 409 | 이미 영수증이 등록된 차수입니다. | (미사용, decisions #41) |
| SETTLEMENT_423_RECEIPT_LOCKED_BY_PAYMENT | 423 | 입금 내역이 있어 영수증을 수정할 수 없습니다. | API-034 |
| SETTLEMENT_423_PAID_CANNOT_DELETE | 423 | 입금 완료 상태는 삭제할 수 없습니다. | API-041 |
| SETTLEMENT_409_RECEIPT_NOT_REGISTERED | 409 | 영수증이 등록되지 않았습니다. | API-038, API-039 |
| SETTLEMENT_423_PAID_MEMBER_LOCKED | 423 | 입금 완료된 인원이 포함되어 있어 명단을 수정할 수 없습니다. | API-032, API-038 |
| SETTLEMENT_423_ROUND_LOCKED_BY_PAYMENT | 423 | 차수에 연결된 입금 내역이 있어 삭제할 수 없습니다. | API-035 |
| SETTLEMENT_409_UNREGISTERED_ROUND_INCLUDED | 409 | 영수증이 등록되지 않은 차수가 포함되어 있습니다. | API-044 |
| SETTLEMENT_409_ACCOUNT_NOT_SET | 409 | 입금 안내 계좌가 설정되지 않았습니다. | API-044 |
| SETTLEMENT_400_ROUND_SELECTION_REQUIRED | 400 | 포함할 차수를 1개 이상 선택해주세요. | API-044 |
| RECEIPT_502_RECOGNITION_FAILED | 502 | 영수증 인식에 실패했습니다. 다시 시도해 주세요. | API-036 |

### 출석 (AttendanceErrorCode) — API-030
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| ATTENDANCE_409_DUPLICATE_NAME | 409 | 같은 이름의 사람이 있어요. | API-030 |

### 부원 명단 (MemberListErrorCode) — API-017~026
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| MEMBER_LIST_404_NOT_FOUND | 404 | 존재하지 않는 명단방입니다. | API-019, API-020, 명단방 수정, API-023, API-024 |
| MEMBER_LIST_404_GROUP_TERM_NOT_FOUND | 404 | 존재하지 않는 기수입니다. | API-017, API-018 |
| MEMBER_404_JOIN_LINK_NOT_FOUND | 404 | 존재하지 않는 등록 링크입니다. | API-021, API-022 |
| MEMBER_410_JOIN_CLOSED | 410 | 등록이 마감된 명단입니다. | API-021, API-022 |
| MEMBER_400_PRIVACY_CONSENT_REQUIRED | 400 | 개인정보 수집·이용에 동의해 주세요. | API-022 |
| MEMBER_400_REQUIRED_FIELD_MISSING | 400 | 필수 항목을 입력해 주세요. | API-022 |
| MEMBER_409_ALREADY_REGISTERED | 409 | 이미 등록된 부원입니다. | API-022 |
| MEMBER_400_INVALID_CURSOR | 400 | 잘못된 cursor입니다. | API-023 |
| MEMBER_404_NOT_FOUND | 404 | 존재하지 않는 부원입니다. | API-025, API-026 |

### 운영 공간 (ScheduleErrorCode의 SPACE_*) — API-012~016
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| SPACE_400_INVALID_CURSOR | 400 | cursor 형식이 올바르지 않습니다. | API-012 |
| SPACE_404_AGENDA_NOT_FOUND | 404 | 존재하지 않는 안건입니다. | API-013 |
| SPACE_404_LINK_NOT_FOUND | 404 | 존재하지 않는 안건 연결입니다. | API-016 |
| SPACE_409_AGENDA_ALREADY_LINKED | 409 | 이미 연결된 안건입니다. | API-013 |

### 운영 메모 (NoteErrorCode) — API-046~050
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| NOTE_400_INVALID_CURSOR | 400 | cursor 형식이 올바르지 않습니다. | API-047 |
| NOTE_400_NOT_TODO_TYPE | 400 | 할 일 유형이 아닙니다. | API-050 |
| NOTE_404_NOT_FOUND | 404 | 존재하지 않는 메모입니다. | API-048, API-049, API-050 |
| NOTE_404_SCHEDULE_NOT_FOUND | 404 | 존재하지 않는 일정입니다. | API-046, API-047 |

### 회의 / 안건 (MeetingErrorCode, AgendaErrorCode) — API-051~061, 056~058, 081~083
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| MEETING_400_INVALID_CURSOR | 400 | cursor 형식이 올바르지 않습니다. | API-052, API-083 |
| MEETING_409_ALREADY_ENDED | 409 | 이미 종료된 회의입니다. | API-054, API-056, API-057, API-058, API-059 |
| MEETING_502_SUMMARY_FAILED | 502 | AI 요약에 실패했습니다. 다시 시도해 주세요. | API-060 |
| MEETING_404_CATEGORY_NOT_FOUND | 404 | 존재하지 않는 회의 카테고리입니다. | API-051, API-086 |
| MEETING_404_GROUP_TERM_NOT_FOUND | 404 | 존재하지 않는 기수입니다. | API-051, API-052, API-081, API-082, API-083 |
| MEETING_404_SCHEDULE_NOT_FOUND | 404 | 존재하지 않는 일정입니다. | API-051 |
| MEETING_404_NOT_FOUND | 404 | 존재하지 않는 회의입니다. | API-053, API-054, API-055, API-056, API-059 |
| AGENDA_404_NOT_FOUND | 404 | 존재하지 않는 안건입니다. | API-057, API-058 |
| AGENDA_409_AT_LEAST_ONE_REQUIRED | 409 | 안건이 1개만 남아 삭제할 수 없습니다. | API-058 |
| AGENDA_409_ALREADY_COMPLETED | 409 | 이미 완료된 안건은 상태를 되돌릴 수 없습니다. | API-057 |

### 예산/장부 (BudgetErrorCode) — API-069~075
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| LEDGER_409_AUTO_ENTRY_LOCKED | 409 | 자동 기록 내역은 수정할 수 없고, 원본이 살아있으면 삭제도 할 수 없습니다. | API-072, API-073 |

### 문서함 (FileErrorCode) — API-063~068, API-080
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| FILE_413_SIZE_EXCEEDED | 413 | 파일 용량이 허용 범위를 초과했습니다. | API-063 |
| FILE_400_EXTENSION_NOT_ALLOWED | 400 | 허용하지 않는 형식입니다. | API-063 |
| FILE_400_SCHEDULE_TERM_MISMATCH | 400 | 일정이 해당 기수에 속하지 않습니다. | API-063 |
| FILE_400_REQUIRED_FIELD_MISSING | 400 | 파일과 이름은 필수입니다. | API-063 |
| FILE_400_INVALID_CURSOR | 400 | cursor 형식이 올바르지 않습니다. | API-064 |
| FILE_404_GROUP_TERM_NOT_FOUND | 404 | 존재하지 않는 기수입니다. | API-063, API-064, API-068 |
| FILE_404_SCHEDULE_NOT_FOUND | 404 | 존재하지 않는 일정입니다. | API-063 |
| FILE_404_NOT_FOUND | 404 | 존재하지 않는 파일입니다. | API-065, API-080, API-066, API-067 |
| FILE_404_THUMBNAIL_NOT_FOUND | 404 | 썸네일이 없는 자료입니다. | API-080(variant=THUMBNAIL) |
| FILE_404_NO_DOWNLOADABLE_FILES | 404 | 다운로드할 파일이 없습니다. | API-068 |

### AI 채팅 (ChatErrorCode) — API-076~079
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| CHAT_400_INVALID_CURSOR | 400 | cursor 형식이 올바르지 않습니다. | API-077, API-079 |
| CHAT_404_GROUP_TERM_NOT_FOUND | 404 | 존재하지 않는 기수입니다. | API-076, API-077 |
| CHAT_404_NOT_FOUND | 404 | 존재하지 않는 대화방입니다. | API-078, API-079 |
| CHAT_502_AI_FAILED | 502 | 처리하지 못했어요. 다시 시도해 주세요. | API-078 |

---

## 6. 엔드포인트 상세

### 그룹

#### API-001 · POST /api/groups · 그룹 생성
인증: 불필요

**Request**
| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| name | string | Y | 그룹명 |
| termName | string | Y | 기수명(예: "26기 (2026)") |

**Response data**
| 필드 | 타입 | 설명 |
|---|---|---|
| groupId | number | 그룹 ID |
| termId | number | 생성된 기수 ID |

```json
{ "success": true, "code": "COMMON_201", "message": "생성에 성공했습니다.", "data": { "groupId": 1, "termId": 1 } }
```
**에러**: COMMON_400(필수 항목 누락)

---

#### API-002 · GET /api/groups/{groupId} · 그룹 정보 조회
인증: 불필요. 설정 화면·기수 선택 드롭다운에 사용.

**Response data**
| 필드 | 타입 | 설명 |
|---|---|---|
| groupId | number | 그룹 ID |
| name | string | 그룹명 |
| terms[].termId | number | 기수 ID |
| terms[].name | string | 기수명 |

```json
{ "success": true, "code": "COMMON_200", "message": "요청에 성공했습니다.", "data": { "groupId": 1, "name": "음샘", "terms": [ { "termId": 1, "name": "26기 (2026)" } ] } }
```
**에러**: GROUP_404_NOT_FOUND(그룹 없음)

---

#### API-003 · PATCH /api/groups/{groupId} · 그룹 정보 수정(그룹명·기수)
인증: 불필요. 운영진 누구나 수정 가능(권한 분기 없음).

**Request**
| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| name | string | N | 그룹명 |
| termName | string | N | 새 기수명(지정 시 새 기수 생성) |

**Response data**: groupId, name, termId, termName(termName을 보내지 않으면 가장 최근 기수)

**에러**: COMMON_400, GROUP_404_NOT_FOUND

### 일정

#### API-004 · POST /api/terms/{termId}/schedules · 일정 등록
인증: 불필요.

**Request**
| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| title | string | Y | 제목 |
| color | string | Y | RED·ORANGE·YELLOW·GREEN·TEAL·BLUE·PURPLE·PINK 중 하나(피그마 스와치 순서, 팀 확정 전 — §7 참고) |
| categoryId | number | N | 카테고리 ID |
| isAllDay | boolean | Y | 종일 여부 |
| startAt | datetime | Y | 시작 일시 |
| endAt | datetime | Y | 종료 일시(start보다 늦어야 함) |
| location | string | N | 장소 |
| memo | string | N | 메모 |
| createOperationSpace | boolean | Y | 운영 공간 함께 만들기 |

**Response data**: scheduleId, hasOperationSpace

```json
{ "success": true, "code": "COMMON_201", "message": "생성에 성공했습니다.", "data": { "scheduleId": 10, "hasOperationSpace": true } }
```
**Logics**: createOperationSpace=true면 운영 공간과 함께 1차 차수(roundNo=1, label "1차")를 만든다 — 출석·정산이 차수
단위로 동작하기 때문이다. 종료가 시작과 같은 시각은 허용한다(종일 일정).

**에러**: COMMON_400(필수 누락·색상 값 오류), SCHEDULE_400_END_BEFORE_START(종료가 시작보다 빠름),
SCHEDULE_404_GROUP_TERM_NOT_FOUND, SCHEDULE_404_CATEGORY_NOT_FOUND(없거나 다른 기수의 카테고리)

---

#### API-005 · GET /api/terms/{termId}/schedules · 일정 목록 조회(캘린더)
인증: 불필요.

**Query**: from(날짜, 필수), to(날짜, 필수), cursor(선택), size(선택, 기본 20)

**Response data**: schedules[].scheduleId/title/categoryName/color/isAllDay/startAt/endAt/location/hasOperationSpace, nextCursor

cursor 페이지네이션(정렬 기준: startAt 오름차순, tiebreaker id).

**Logics**: 시작 시각이 아니라 from~to 기간과 **겹치는** 일정을 내려준다(startAt ≤ to 23:59:59 그리고 endAt ≥ from
00:00) — 여러 날에 걸친 일정도 캘린더 칸마다 보여야 하기 때문이다.

**에러**: COMMON_400(기간 누락·from이 to보다 늦음), SCHEDULE_400_INVALID_CURSOR, SCHEDULE_404_GROUP_TERM_NOT_FOUND

---

#### API-006 · GET /api/schedules/{scheduleId} · 일정 상세 조회
인증: 불필요.

**Response data**: scheduleId, title, color, categoryId, categoryName, isAllDay, startAt, endAt, location, memo, dDay, hasOperationSpace

dDay는 한국 날짜 기준 시작일까지 남은 일수(당일 0, 지난 일정은 음수).

**에러**: SCHEDULE_404_NOT_FOUND

---

#### API-007 · PATCH /api/schedules/{scheduleId} · 일정 수정
인증: 불필요. Request 필드는 API-004와 동일하고 전부 선택값 — 보낸 필드만 수정한다.
location·memo에 빈 문자열을 보내면 지운다. 시작·종료 중 하나만 바꿔도 바뀐 결과 기준으로 순서를 확인한다.

**운영 공간 토글**: 꺼진 운영 공간을 켜는 것만 된다. `createOperationSpace=true`면 운영 공간을 켜고 일정 등록과 같이
1차 차수를 만든다(이미 차수가 있으면 새로 만들지 않음). 켜진 운영 공간에 `false`를 보내면 400 — 끄면 그 안의
메모·출석·정산 기록 처리를 함께 정해야 해서 끄는 동작은 지원하지 않는다. 꺼진 상태에서 `false`는 그대로 둔다.

**Response**: scheduleId
**에러**: COMMON_400, SCHEDULE_400_END_BEFORE_START, SCHEDULE_400_OPERATION_SPACE_CANNOT_DISABLE,
SCHEDULE_404_NOT_FOUND, SCHEDULE_404_CATEGORY_NOT_FOUND

---

#### API-008 · DELETE /api/schedules/{scheduleId} · 일정 삭제
인증: 불필요.

**Logics**: 운영 메모, 차수, 참가자·출석, 영수증 정보, 정산 대상, 그 일정에서 올린 자료(file.schedule_id),
일정-안건 연결, **그 일정에 속한 회의록(meeting.schedule_id)과 그 회의의 안건·요약**을 함께 삭제한다
(decisions #44 — 일정 소속 회의록의 안건은 다른 일정에 연결될 수 없어 삭제가 번지지 않는다).
일정-안건 연결(`schedule_agenda_link`)은 **연결만 끊는다** — 그 안건과 안건이 속한 회의록은 원래대로
남는다. 이 일정에서 만든 회의록이 아니기 때문이다. 일정과 무관한 회의록(meeting.schedule_id IS NULL)과
부원 원본 명단도 유지한다. 이 일정에서 자동 생성된
`ledger_entry`(영수증 지출·완납 수입·추가 동아리 지원금 행)는 삭제하지 않고 보존하며,
`schedule_round_id` 참조만 NULL로 전환한다(decisions #4와 동일 원칙).

지우는 순서(FK가 전부 RESTRICT): 일정-안건 연결(이 일정의 연결 + 이 일정 회의록 안건에 걸린 연결) → 일정 소속
회의록의 안건 → 회의록 → 운영 메모 → 장부 차수 참조 해제 → 출석(round_attendee) → 정산 대상 → 차수 → 참가자 →
자료(소프트 삭제된 행 포함) → 일정. 자료는 DB 행만 지우고 스토리지 파일은 남는다(영구 삭제 배치를 두지 않는 것과 같은
원칙, decisions #16).

**Response**: 200(BaseResponse, data 없음). **에러**: SCHEDULE_404_NOT_FOUND

---

#### API-009 · GET /api/terms/{termId}/schedules/upcoming · 다가오는 운영 일정 조회
인증: 불필요. `has_operation_space = true`인 일정만 대상(운영 공간이 아닌 일정은 제외),
종료되지 않은 것만 시작 시간순.

**Query**: limit(선택, 기본 5, 최대 20)

종료되지 않은 것 = endAt이 현재(한국 시간)보다 뒤 — 진행 중인 일정도 포함한다.

**Response data**: schedules[].scheduleId/categoryName/startAt/title/location/operationMenuCount

**Logics**: `operationMenuCount`는 실제 데이터 유무와 무관하게 항상 4(메모장·출석·정산·자료)로
고정이다(decisions #12).

---

#### API-010 · POST /api/terms/{termId}/categories · 카테고리 추가
인증: 불필요. **Request**: name. **Response**: categoryId, name. 같은 기수에 같은 이름(앞뒤 공백 제거 후)이 있으면 거부한다.
**에러**: COMMON_400, SCHEDULE_404_GROUP_TERM_NOT_FOUND, SCHEDULE_409_CATEGORY_DUPLICATED

#### API-085 · PATCH /api/categories/{categoryId} · 일정 카테고리 수정
인증: 불필요. 일정 등록 화면 카테고리 선택의 연필 버튼. **Request**: name. **Response**: categoryId, name.
이름만 바꾼다. 같은 기수에 같은 이름(앞뒤 공백 제거 후, 자기 자신 제외)이 있으면 거부한다. 이 카테고리를 쓰는
일정에는 바뀐 이름이 그대로 보인다.
**에러**: COMMON_400, SCHEDULE_404_CATEGORY_NOT_FOUND, SCHEDULE_409_CATEGORY_DUPLICATED

#### API-011 · GET /api/terms/{termId}/categories · 카테고리 목록 조회
인증: 불필요. 일정 등록에서 쓰는 목록(회의 카테고리는 API-081/082로 분리 운영, decisions #21).
**Response data**: categories[].categoryId/name(만든 순서). **에러**: SCHEDULE_404_GROUP_TERM_NOT_FOUND

#### API-081 · POST /api/terms/{termId}/meeting-categories · 회의 카테고리 생성
인증: 불필요. 일정 카테고리(API-010)와 별도 테이블(meeting_category)로 운영한다(decisions #21).
**Request**: name. **Response**: categoryId, name. **에러**: COMMON_400, MEETING_404_GROUP_TERM_NOT_FOUND

`name`은 배지로 한 줄 렌더되므로 저장 전에 한 줄로 정규화된다(decisions #84).

#### API-082 · GET /api/terms/{termId}/meeting-categories · 회의 카테고리 목록 조회
인증: 불필요. 회의 만들기·수정의 카테고리 선택 바텀시트에서 쓰는 목록.
**Response data**: categories[].categoryId/name. **에러**: MEETING_404_GROUP_TERM_NOT_FOUND

#### API-086 · PATCH /api/meeting-categories/{categoryId} · 회의 카테고리 수정
인증: 불필요. 회의 만들기 화면 카테고리 선택의 연필 버튼. **Request**: name. **Response**: categoryId, name.
이름만 바꾸고, 생성(API-081)과 같이 줄바꿈·연속 공백을 한 줄로 편다. 이 카테고리를 쓰는 회의에는 바뀐 이름이 그대로 보인다.
**에러**: COMMON_400, MEETING_404_CATEGORY_NOT_FOUND

#### API-083 · GET /api/terms/{termId}/agendas/linkable · 연결 가능한 안건 목록 조회
인증: 불필요. 일정 상세 → "안건 연결하기" 모달의 후보 목록. 선택한 안건을 실제로 연결하는 건 API-013.

**Logics**: 후보는 **어느 일정에도 속하지 않은 안건**이다 — `meeting.schedule_id IS NULL`이고
`schedule_agenda_link`에 행이 없는 안건만 내려준다(decisions #44). 조건이 특정 일정과 무관하므로
`scheduleId`를 받지 않는다. 회의 상태는 거르지 않는다 — 종료된 회의의 안건도 후보에 포함된다
(연결한 뒤 읽기 전용으로 보이는 것은 API-015가 내려주는 `meetingStatus`로 판단한다, decisions #46).

**Query**: keyword(선택, 안건 내용 검색), cursor(선택), size(선택, 기본 20)

`keyword`는 다른 목록 API와 달리 **정규화하지 않고 그대로** 검색한다 — 안건 내용은 여러 줄이
정상이라 저장할 때도 펴지 않기 때문이다. 검색어만 펴면 오히려 안 걸린다(decisions #84).

**Response data**: agendas[].agendaId/meetingId/meetingTitle/meetingDate/orderNo/
content/status(PENDING|IN_PROGRESS|COMPLETED)/discussionNote/decision/hasDecision, nextCursor,
totalCount(cursor 없는 첫 페이지에서만 값이 있고, 이후 페이지는 null — API-064와 같은 패턴.
모달 상단의 "안건 N개" 표시용)

cursor 페이지네이션(정렬 기준: 회의 ID 내림차순 + 안건 ID 오름차순 — 최근 회의가 먼저 오고 한 회의
안에서는 "안건1 → 안건2" 순서가 유지된다).

**에러**: MEETING_400_INVALID_CURSOR(잘못된 cursor), MEETING_404_GROUP_TERM_NOT_FOUND(기수 없음)

### 운영 공간

#### API-012 · GET /api/terms/{termId}/operation-spaces · 운영 공간 목록 조회
인증: 불필요.

**Logics**: SCHEDULE 타입은 `has_operation_space = true`인 일정만 포함한다 — 토글을 켜지 않은
일정은 운영 공간 목록에 나타나지 않는다(decisions #12). MEETING 타입은 토글 개념이 없어 전부 포함.

**Query**: status(ONGOING|PAST, 필수), type(ALL|SCHEDULE|MEETING, 기본 ALL), keyword(선택),
sort(UPCOMING|LATEST|OLDEST|NAME, 기본 LATEST), cursor(선택), size(선택, 기본 20)

**Response data**: totalCount(status·type·keyword 조건에 맞는 일정·회의록 전체 수, 화면의 '운영 기록 N개'),
items[].type/id/title/date/status, nextCursor

cursor 페이지네이션. `file`(API-064)과 같은 다중 정렬 패턴 — sort별로 cursor 키가 다르다:
UPCOMING/LATEST/OLDEST는 그 항목의 날짜(date, 즉 schedule.start_at 또는 meeting.meeting_date),
NAME은 title. 둘 다 id를 tiebreaker로 쓴다.

구현 세부: 일정·회의록을 테이블별로 (size+1)건씩 조회해 같은 기준으로 합친다. 정렬 키는
(날짜 또는 제목, 유형 순서, id)이고 유형 순서는 SCHEDULE 0·MEETING 1(두 테이블의 id가 겹칠 때 순서를 가름).
날짜 키는 일정은 start_at, 회의는 meeting_date 0시. UPCOMING·OLDEST는 오름차순, LATEST는 내림차순, NAME은
오름차순. 지난 기록 = 일정은 end_at ≤ 현재(한국 시간), 회의는 status=COMPLETED.
`date`는 일정은 시작일, 회의는 회의 날짜. keyword는 제목 검색.

**에러**: COMMON_400(status 누락·잘못된 enum), SPACE_400_INVALID_CURSOR, SCHEDULE_404_GROUP_TERM_NOT_FOUND

---

#### API-013 · POST /api/schedules/{scheduleId}/agenda-links · 안건 연결
인증: 불필요. 회의록이 아니라 **안건 하나하나**를 연결한다. 선택 목록은 API-083으로 받는다.

**Logics**: 연결할 수 있는 안건은 **어느 일정에도 속하지 않은 안건**뿐이다 —
`meeting.schedule_id IS NULL`이고 `schedule_agenda_link`에 행이 없어야 한다(decisions #44).
일정 상세에서 만든 회의록의 안건은 이미 그 일정에 속하므로 후보에서 빠진다.

**Request**: agendaIds(number[], 중복은 한 번만 연결). **Response data**: links[].linkId/agendaId
**에러**: COMMON_400(안건 미선택), SCHEDULE_404_NOT_FOUND, SPACE_404_AGENDA_NOT_FOUND(없거나 다른 기수의 안건),
SPACE_409_AGENDA_ALREADY_LINKED(이미 연결됐거나 다른 일정에 속한 회의록의 안건)

---

#### API-015 · GET /api/schedules/{scheduleId}/agenda-links · 연결된 회의록 조회
인증: 불필요. 연결은 안건 단위 — 이 응답은 "회의록 & 안건" 섹션의 **안건 카드**만 담는다.
같은 섹션의 회의록 카드(그 일정에 속한 회의록)는 API-052에 `scheduleId`를 줘서 받는다(decisions #44).

**Logics**: 종료된 회의(meetingStatus=COMPLETED)의 안건도 연결할 수 있다. 다만 그 안건의
논의 내용·결정 사항은 **읽기 전용**이다 — 회의가 끝나면 안건 수정(API-057)이
MEETING_409_ALREADY_ENDED로 거부되기 때문이다(decisions #46). 화면이 입력란을 잠글 수 있도록
`meetingStatus`를 함께 내려준다. 안건 상태(agendaStatus)로는 판단할 수 없다 — 종료된 회의에도
PENDING·IN_PROGRESS인 안건이 남아 있을 수 있다.

**Response data**: links[].linkId/meetingId/meetingTitle/meetingDate/meetingStatus(IN_PROGRESS|COMPLETED)/
agendaId/agendaOrderNo/agendaContent/agendaStatus/discussionNote/decision/hasDecision

agendaOrderNo는 meeting_agenda.order_no 원래 값(회의 상세 API-053과 같은 값). 정렬은 회의 날짜 최신순, 회의 안에서는
안건 순서. hasDecision은 공백만 있으면 false.

**에러**: SCHEDULE_404_NOT_FOUND

---

#### API-016 · DELETE /api/schedules/{scheduleId}/agenda-links/{linkId} · 회의록 연결 해제
인증: 불필요. `schedule_agenda_link` 행만 삭제, 원본 회의록·안건은 유지. 다른 일정의 linkId면 404.
**에러**: SPACE_404_LINK_NOT_FOUND

### 부원 명단

#### API-017 · POST /api/terms/{termId}/member-lists · 명단방 생성
인증: 불필요.

**Request**: name(필수, 100자 이하), registerDeadline(필수, `yyyy-MM-ddTHH:mm:ss`),
collectSchool/collectStudentId/collectPart/collectGrade/collectPhone(boolean, 선택, 안 보내면 true)

**Logics**: 이름·학과는 토글과 무관하게 항상 필수. 나머지 항목(학교·전화번호·학번·파트·학년)은
토글 ON이면 필수 입력, OFF면 입력 자체를 받지 않는다(수집 여부 자체를 토글이 결정, decisions #7).
등록 마감 일시(한국 시간)까지 등록을 받는다.
서버가 추측하기 어려운 난수로 `joinToken`을 생성하고, 이미 쓰이는 토큰이면 다시 만든다.
`joinUrl`은 `moimjang.app/join/{joinToken}` 형태로 내려준다(앞부분은 설정값 `moimzang.join-base-url`).

**Response data**: memberListId, joinToken, joinUrl.
**에러**: COMMON_400(이름·마감일 누락, 길이 초과), MEMBER_LIST_404_GROUP_TERM_NOT_FOUND

---

#### API-018 · GET /api/terms/{termId}/member-lists · 명단방 목록 조회
인증: 불필요. 최근에 만든 명단방부터 내려준다. **Response data**: memberLists[].memberListId/name/memberCount/
status/registerDeadline(`yyyy-MM-ddTHH:mm:ss`)

**Logics**: status는 RECRUITING|COMPLETED. 링크를 닫았거나 마감일이 지나면 COMPLETED로 계산해서 내려준다.
부원이 없는 명단방의 memberCount는 0.

**에러**: MEMBER_LIST_404_GROUP_TERM_NOT_FOUND

---

#### API-019 · GET /api/member-lists/{memberListId} · 명단방 상세(링크 관리) 조회
인증: 불필요.

**Response data**: memberListId, name, registerDeadline(`yyyy-MM-ddTHH:mm:ss`), status, memberCount, joinUrl,
isLinkClosed, fields.school/studentId/part/grade/phone(boolean). status 계산은 API-018과 같다.

**에러**: MEMBER_LIST_404_NOT_FOUND

---

#### PATCH /api/member-lists/{memberListId} · 명단방 수정
인증: 불필요.

**Request**: name, registerDeadline(`yyyy-MM-ddTHH:mm:ss`), collectSchool/collectStudentId/collectPart/collectGrade/
collectPhone — 전부 선택, 보낸 필드만 수정한다. name은 보낼 경우 공백 불가·100자 이하.

**Logics**: 같은 명단방과 등록 링크(joinToken)를 그대로 유지한다. 수집 항목을 바꾸면 이후 등록하는
사람부터 적용되고, 이미 등록된 부원 정보는 바뀌지 않는다. 마감일을 미래로 늘리면 다시 RECRUITING이 된다.

**Response data**: memberListId. **에러**: COMMON_400, MEMBER_LIST_404_NOT_FOUND

---

#### API-020 · DELETE /api/member-lists/{memberListId} · 명단방 삭제
인증: 불필요. 명단방·부원 삭제, 링크 닫힘. 연결된 출석·정산 기록(그 부원이 속한 schedule_attendee와
그 round_attendee·settlement_target)도 함께 삭제한다. 부원과 연결되지 않은 현장 참가자는 남는다.
FK가 RESTRICT라 round_attendee → settlement_target → schedule_attendee → member → member_list 순서로
지운다. 장부(ledger_entry)의 수입 기록은 차수 단위라 이 삭제로 영향받지 않는다(decisions #2, #4 참고).
**에러**: MEMBER_LIST_404_NOT_FOUND

---

#### API-021 · GET /api/public/member-lists/{joinToken} · 부원 등록 폼 조회(공개)
인증: 불필요(공개 API). **Response data**: groupName, memberListName, registerDeadline(`yyyy-MM-ddTHH:mm:ss`),
fields.school/studentId/part/grade/phone(boolean, true면 폼에 노출하고 필수)

**Logics**: 링크를 닫았거나 마감일이 지났으면 410.

**에러**: MEMBER_404_JOIN_LINK_NOT_FOUND(없는 링크), MEMBER_410_JOIN_CLOSED(닫힌 링크 또는 마감된 명단)

---

#### API-022 · POST /api/public/member-lists/{joinToken}/members · 부원 등록(공개)
인증: 불필요(공개 API).

**Request**: name(필수), department(필수), school, phone, studentId, part, grade, privacyConsent(boolean, 필수)

**Logics**: 필수 항목 = 이름·학과(고정) + 그 명단방에서 켠 항목(학교·전화번호·학번·파트·학년).
토글 OFF 항목은 폼에 노출되지 않아야 하며 보내도 저장하지 않는다. FE는 API-021의 `fields`로 노출·필수 여부를
동적으로 렌더링한다. 링크 확인(404·410) → 개인정보 동의 확인 → 필수 항목 확인 순서로 검사한다.

**Response data**: memberId.
**에러**: COMMON_400(이름·학과 누락, 길이 초과), MEMBER_400_PRIVACY_CONSENT_REQUIRED(개인정보 미동의),
MEMBER_400_REQUIRED_FIELD_MISSING(켜진 항목 누락), MEMBER_409_ALREADY_REGISTERED(중복 등록),
MEMBER_404_JOIN_LINK_NOT_FOUND, MEMBER_410_JOIN_CLOSED

**중복 등록**: 같은 명단방에 이름·전화번호가 모두 같은 부원이 있으면 409로 거부한다(덮어쓰지 않음).
이름만 같은 건 동명이인일 수 있어 막지 않고, 전화번호를 받지 않는 명단방은 검사하지 않는다.
운영진 직접 추가(API-024)는 검사하지 않는다.

---

#### API-023 · GET /api/member-lists/{memberListId}/members · 부원 목록 조회
인증: 불필요. **Query**: keyword(선택, 이름·학과·전화번호 부분 일치), cursor(선택), size(선택, 기본 20, 최대 100).

**Response data**: totalCount(검색 조건에 맞는 전체 인원), members[].memberId/name/department/phone/school/
studentId/part/grade(수집 안 한 항목은 null), nextCursor. 등록한 순서(createdAt, id 오름차순) cursor 페이지네이션.

**에러**: MEMBER_LIST_404_NOT_FOUND, MEMBER_400_INVALID_CURSOR

---

#### API-024 · POST /api/member-lists/{memberListId}/members · 부원 추가(운영진)
인증: 불필요. **Request**: name(필수), department(필수), school(선택), phone(선택).

**Logics**: 추가 화면에는 이름·학교·학과·전화번호만 있어 이름·학과만 필수로 받는다. 마감된 명단방에도 추가할 수 있다.
폼 동의를 거치지 않았으므로 privacy_consent는 FALSE로 저장한다.

**Response data**: memberId. **에러**: COMMON_400(이름·학과 누락), MEMBER_LIST_404_NOT_FOUND

---

#### API-025 · PATCH /api/members/{memberId} · 부원 수정
인증: 불필요. **Request**: name, school, department, phone — 전부 선택, 보낸 필드만 수정. 이름·학과는 공백 불가,
학교·전화번호는 빈 문자열을 보내면 비운다. 학번·파트·학년은 수정 화면에 없어 수정 불가.
일정 참가자 정보는 스냅샷이라 함께 바뀌지 않는다.

**Response data**: memberId. **에러**: COMMON_400, MEMBER_404_NOT_FOUND

#### API-026 · DELETE /api/members/{memberId} · 부원 삭제
인증: 불필요. 이 부원이 참여한 `schedule_attendee`는 유지하고 `member_id`만 NULL로 전환한 뒤 부원을 삭제한다
(스냅샷이라 원본 삭제와 무관, 출석·정산 기록도 유지). **에러**: MEMBER_404_NOT_FOUND

### 출석

#### API-027 · GET /api/schedules/{scheduleId}/rounds · 차수 목록 조회
인증: 불필요. **Response data**: rounds[].roundId/roundNo/label/hasReceipt. **에러**: COMMON_404

#### API-028 · POST /api/schedules/{scheduleId}/rounds · 차수 추가
인증: 불필요. **Response data**: roundId, roundNo, label. **에러**: COMMON_404

**Logics**: 그 일정의 기존 참가자 전원을 이 차수에 미출석 상태로 함께 포함한다.

---

#### API-029 · GET /api/rounds/{roundId}/attendees · 차수 출석 현황 조회
인증: 불필요. **Query**: status(UNCHECKED|CHECKED|ALL, 기본 UNCHECKED), keyword(이름 검색, 선택 —
초성 검색은 이번 범위 보류), cursor(선택), size(선택, 기본 20)

**Response data**: scheduleTitle, progress.checkedCount/totalCount/rate(이 progress.totalCount는
전체 참가자 수이지 페이지네이션 totalCount가 아니다 — 그대로 유지), attendees[].attendeeId/name/
affiliation/phoneLast4/isCheckedIn/checkedInAt, nextCursor

cursor 페이지네이션(정렬 기준: round_attendee.createdAt — 그 차수에 합류한 순서).

**에러**: COMMON_404, COMMON_400(잘못된 cursor)

---

#### API-030 · POST /api/rounds/{roundId}/attendees · 현장 인원 추가
인증: 불필요.

**Request**: name, affiliation, phoneLast4(전화번호 뒤 4자리, 숫자 4자리), allowSameName(boolean, 기본
false)

**Logics**: source=ON_SITE, 미출석으로 추가. 같은 이름이 이미 있으면 409(ATTENDANCE_409_DUPLICATE_NAME)로 응답하고
기존 인원 카드를 보여준다. "다른 사람으로 새로 추가"를 누르면 allowSameName=true로 재요청.

**Response data**: attendeeId, name, affiliation, phoneLast4, isCheckedIn
**에러**: COMMON_400, COMMON_404, ATTENDANCE_409_DUPLICATE_NAME

---

#### API-031 · PATCH /api/rounds/{roundId}/attendees/{attendeeId} · 출석 처리·되돌리기
인증: 불필요. **Request**: isCheckedIn(boolean). **Response data**: attendeeId, name, isCheckedIn,
checkedInAt, progress. **에러**: COMMON_400(isCheckedIn 누락), COMMON_404

**Logics**: 토글이 아니라 isCheckedIn 값으로 상태를 지정한다. 이미 같은 상태로 재요청하면 멱등하게
처리하고 기존 checkedInAt을 그대로 반환한다(상태가 실제로 바뀔 때만 checkedInAt을 갱신).

#### API-032 · DELETE /api/rounds/{roundId}/attendees/{attendeeId} · 차수에서 참가자 제외
인증: 불필요. **에러**: COMMON_404, SETTLEMENT_423_PAID_MEMBER_LOCKED

**Logics**: 제외 대상이 입금 완료(PAID) 상태면 제외 자체를 423으로 거부한다(round_attendee도 지우지
않는다). 미입금 상태면 round_attendee와 함께 settlement_target도 삭제하고, 남은 인원으로 1인
청구액을 재계산한다.

**Response**: 200 + `BaseResponse.success()`(COMMON_200).

---

#### API-084 · POST /api/rounds/{roundId}/attendees/import · 명단방 불러오기
인증: 불필요.

**Request**: memberListId

**Logics**: 요청한 명단방(memberListId)의 부원 전체를 그 차수에 미출석 상태로 불러온다. 부원의
이름·학과·전화번호를 참가자 정보로 스냅샷해 source=LINK로 저장한다(전화번호는 숫자만 남겨 뒤
4자리를 쓰고, 번호가 없거나 4자리가 안 되면 null). 같은 일정에 그 부원으로 연결된 참가자가
이미 있으면 새로 만들지 않고 재사용하고(중복 판별은 member_id 기준), 그 참가자가 이미 이
차수에 있으면 건너뛴다(skippedCount). 다른 차수에서 제외됐던 부원도 이 차수에는 없으므로
다시 추가된다. 현장 인원 추가(API-030)와 달리 이름 중복은 검사하지 않는다 — 명단방 전체를
한 번에 불러오는 일괄 처리라 사람마다 확인받는 흐름이 없기 때문이다. 명단방의 부원이 0명이면
addedCount 0으로 성공 처리한다(에러 아님). 그 일정과 명단방의 기수가 다르면 거부한다.

**Response data**: addedCount, skippedCount, attendees[].attendeeId/name/affiliation/
phoneLast4/isCheckedIn(새로 추가된 참가자만, 건너뛴 인원은 포함하지 않는다), progress.
checkedCount/totalCount/rate(API-029와 같은 패턴 — 이 호출 이후 그 차수 전체 기준)

**에러**: COMMON_400(검증 실패, 그 일정과 다른 기수의 명단방), COMMON_404(존재하지 않는
차수·명단방)

### 정산

#### API-033 · GET /api/rounds/{roundId}/receipt · 영수증 조회
인증: 불필요. `isLocked`는 그 차수에 입금 완료가 1명이라도 있으면 true(수정·삭제 버튼 비활성).
`registeredAt`은 전용 컬럼이 없어 `schedule_round.updated_at`으로 대체한다.

**Response data**: roundId, label, storeName, transactedAt, totalAmount, clubSubsidyAmount, imageUrl,
registeredAt, isLocked. **에러**: COMMON_404

---

#### API-034 · PUT /api/rounds/{roundId}/receipt · 영수증 등록
인증: 불필요. 차수당 영수증 1개, schedule_round의 영수증 컬럼에 저장.

**Request**: storeName(선택), transactedAt(선택), totalAmount(양의 정수), clubSubsidyAmount(0 이상·총액 이하),
imageKey(사진 없이 등록 시 생략)

**Logics**: 금액(totalAmount/clubSubsidyAmount)이 바뀌면 그 차수에 이미 저장된 정산 명단의 1인
청구액도 API-038과 같은 공식으로 재계산한다. 입금 완료자가 있으면 SETTLEMENT_423_RECEIPT_LOCKED_BY_PAYMENT로 수정 자체가
막히므로 재계산은 아직 아무도 입금하지 않은 상태에서만 일어난다. imageKey를 생략하면 기존에 저장된
이미지 URL을 그대로 유지한다.

**Response data**: roundId, label, totalAmount, clubSubsidyAmount
**에러**: COMMON_400, SETTLEMENT_400_AMOUNT_NOT_POSITIVE, SETTLEMENT_400_SUBSIDY_OUT_OF_RANGE, COMMON_404, SETTLEMENT_423_RECEIPT_LOCKED_BY_PAYMENT

---

#### API-035 · DELETE /api/rounds/{roundId}/receipt · 영수증 삭제
인증: 불필요.

**Logics**: 그 차수(schedule_round) 행 자체와 연결된 settlement_target(청구 내역)·round_attendee(출석
기록)를 함께 삭제한다. 다른 차수·원본 부원 명단·명단방은 영향받지 않는다. 이미 입금 완료 내역이
있으면 423으로 거부한다(실제로 삭제가 성공하는 시점은 그 차수의 입금 완료가 0명일 때뿐).
**장부(ledger_entry)는 영향받지 않는다** — 이미 생성된 내역·잔액은 유지되고 `schedule_round_id` 참조만
NULL로 전환된다(고아 상태). 상세 근거는 `docs/decisions.md` #4.

**Response**: 200 + `BaseResponse.success()`(COMMON_200). **에러**: COMMON_404, SETTLEMENT_423_ROUND_LOCKED_BY_PAYMENT

---

#### API-036 · POST /api/rounds/{roundId}/receipt/ocr · 영수증 AI 인식
인증: 불필요. Gemini 멀티모달로 영수증 이미지를 직접 분석해 인식 결과만 반환한다. "저장 안 함"은
영수증 레코드(schedule_round의 영수증 컬럼)를 저장하지 않는다는 뜻이고, 이미지 파일 자체는 인식이
성공했을 때만 저장해 그 스토리지 키를 `imageKey`로 돌려준다(API-034 등록 시 그대로 전달). 인식 실패
항목은 null. "다시 촬영하기"는 이 API 재호출.

**Request**: image(multipart/form-data, jpg/jpeg/png/webp만 허용)
**Response data**: storeName, transactedAt, totalAmount, imageKey(저장된 이미지의 스토리지 키)

**Logics**: 502는 호출 실패(네트워크·타임아웃·HTTP 오류)·응답 파싱 실패·API 키 미설정에만 던진다.
storeName/transactedAt/totalAmount 세 필드가 전부 null이어도 인식 자체는 성공으로 보고 200과 함께
null 필드를 내려준다.

**에러**: COMMON_400(이미지 없음·빈 파일·허용되지 않는 확장자), COMMON_404(존재하지 않는 roundId —
api-spec 원문에는 없었으나 다른 차수 API와의 일관성을 위해 추가, 문서 누락으로 간주), RECEIPT_502_RECOGNITION_FAILED

---

#### API-037 · GET /api/rounds/{roundId}/settlement-targets · 정산 명단 조회
인증: 불필요. **Query**: keyword(선택), cursor(선택), size(선택, 기본 20)

**Response data**: label, targetCount(전체 인원 수, 페이지네이션 totalCount 아님 — 그대로 유지),
billedAmount, targets[].settlementTargetId/attendeeId/name/affiliation/phoneLast4/billedAmount/
paymentStatus(UNPAID|PAID), nextCursor

cursor 페이지네이션(정렬 기준: createdAt).

**에러**: COMMON_404, COMMON_400(잘못된 cursor)

---

#### API-038 · PUT /api/rounds/{roundId}/settlement-targets · 정산 명단 저장
인증: 불필요. 보낸 attendeeIds로 그 차수 정산 명단을 통째로 교체.

**Logics**: 1인 청구액 = ⌊(영수증 금액 − 지원금) ÷ 인원수⌋를 100원 단위로 버림(decisions #3)해
전원 재계산. 영수증 미등록이면 409, 이미 입금 완료된 사람을 제외하면 423.

**Request**: attendeeIds(number[])
**Response data**: label, targetCount, billedAmount
**에러**: COMMON_400(명단 비어있음, 그 차수가 속한 일정 소속이 아니거나 존재하지 않는 attendeeId 포함),
COMMON_404, SETTLEMENT_409_RECEIPT_NOT_REGISTERED, SETTLEMENT_423_PAID_MEMBER_LOCKED

---

#### API-039 · GET /api/rounds/{roundId}/settlement-targets/import · 행사 명단 불러오기
인증: 불필요. 그 차수의 출석 완료자를 미리보기로 반환(저장 안 함) — API-038로 확정.

**Response data**: attendees[].attendeeId/name/affiliation/phoneLast4, expectedBilledAmount
**에러**: COMMON_404, SETTLEMENT_409_RECEIPT_NOT_REGISTERED

---

#### API-040 · PATCH /api/schedules/{scheduleId}/payments/{attendeeId} · 입금 확인·되돌리기
인증: 불필요. 수동 처리, 은행 연동 없음. 그 사람의 해당 일정 내 모든 차수 settlement_target을 함께
갱신. AI 비서 결과 카드의 "입금 확인"도 이 API 호출. 장부 반영은 API-074(정산 내역 새로고침)에서 처리.

**Request**: paid(boolean)

**Logics**: 토글이 아니라 paid 값으로 상태를 지정한다. 이미 같은 상태로 재요청하면 멱등하게 처리하고
그 차수의 기존 paidAt을 그대로 유지한다(상태가 실제로 바뀔 때만 paidAt을 갱신). 한 사람이 일정 내
여러 차수에 걸쳐 있으면 차수별로 독립적으로 적용하고, 응답의 대표 paidAt은 입금 확인이면 그중 가장
최근 값을, 되돌리기면 null을 내려준다.

**Response data**: attendeeId, name, paymentStatus, paidAt
**에러**: COMMON_400(paid 누락), COMMON_404(그 일정에 정산 대상이 없는 참가자)

---

#### API-041 · DELETE /api/schedules/{scheduleId}/payments/{attendeeId} · 입금 명단에서 삭제
인증: 불필요. 미입금 카드 스와이프 "삭제하기". URL에 roundId가 없으므로 scheduleId 기준으로 그
일정의 **모든 차수**에서 그 사람의 settlement_target만 삭제한다(특정 차수 1개만 빼는 게 아니고,
API-032와 달리 round_attendee는 건드리지 않는다 — "입금 명단에서 뺀다"는 출석 사실과는 별개
결정이다). 각 차수는 남은 인원으로 청구액 재계산(남은 인원이 0명이면 재계산을 건너뛴다).

**Response**: 200 + `BaseResponse.success()`(COMMON_200).
**에러**: COMMON_404(그 일정에 정산 대상이 없는 참가자), SETTLEMENT_423_PAID_CANNOT_DELETE

#### API-042 · GET /api/schedules/{scheduleId}/payments · 입금 현황 조회
인증: 불필요. **Query**: status(UNPAID|PAID|ALL, 기본 UNPAID), keyword(선택), cursor(선택),
size(선택, 기본 20)

**Response data**: payments[].attendeeId/name/affiliation/phoneLast4/totalBilledAmount(여러
차수 합산)/paymentStatus(그 사람의 일정 내 모든 차수가 PAID여야 PAID, 한 차수라도 미납이면 UNPAID),
nextCursor

cursor 페이지네이션(정렬 기준: `schedule_attendee.createdAt` — 사람 단위로 묶은 목록이라
`settlement_target.createdAt`은 쓸 수 없다).

**에러**: COMMON_404, COMMON_400(잘못된 cursor)

---

#### API-043 · PUT /api/rounds/{roundId}/account · 입금 안내 계좌 설정
인증: 불필요. 은행 연동 없이 공지에 띄울 텍스트일 뿐(schedule_round에 저장).

**Request**: bankName, accountNumber, accountHolder
**Response data**: roundId, bankName, accountNumber, accountHolder
**에러**: COMMON_400, COMMON_404

---

#### API-044 · POST /api/schedules/{scheduleId}/settlement-notice · 정산 공지 생성
인증: 불필요. 선택된 차수 중 영수증 미등록(또는 영수증은 등록됐지만 정산 명단을 아직 저장하지
않은 차수 포함, 둘 다 같은 409)/계좌 미설정이 있으면 409.

**Request**: roundIds(number[], 1개 이상)

**Logics**: 선택한 차수를 roundNo 오름차순으로 정렬해 공지 문구에 나열하고, 그중 roundNo가 가장
작은 차수의 계좌를 공지 전체의 대표 계좌로 쓴다(요청 roundIds 배열 순서와 무관하게 항상 같은
결과). noticeText는 다음 형식으로 고정 생성한다(금액은 천 단위 콤마 표기):

```
[{scheduleTitle}] 정산 안내

{label1}: {billedAmount1}원
{label2}: {billedAmount2}원

총 입금액: {combinedAmount}원
{bankName} {accountNumber} ({accountHolder})

확인 후 입금해주세요!
```

**Response data**: scheduleTitle, rounds[].label/billedAmount, combinedAmount, account.bankName/
accountNumber/accountHolder, noticeText

**에러**: SETTLEMENT_400_ROUND_SELECTION_REQUIRED, COMMON_400(그 일정 소속이 아니거나 존재하지
않는 roundId 포함), COMMON_404, SETTLEMENT_409_UNREGISTERED_ROUND_INCLUDED, SETTLEMENT_409_ACCOUNT_NOT_SET

---

#### API-045 · GET /api/schedules/{scheduleId}/settlement · 정산 홈 조회
인증: 불필요. 조회 전용이라 아무것도 저장하지 않는다.

**Logics**:
- `progress`는 **사람 수** 기준이다(차수 수 아님). 그 사람의 일정 내 모든 차수가 PAID여야
  `paidCount`에 포함한다(API-042의 사람 단위 집계와 같은 기준).
- 최상위 `status`는 `progress.totalCount > 0 && progress.paidCount == progress.totalCount`면
  "정산 완료", 그 외(전체 인원 0명 포함)는 "정산중"이다 — `rate`가 반올림으로 100%가 될 수 있어
  `rate`가 아니라 인원수를 직접 비교한다. 이 `status`는 일정 전체 정산 대상 기준으로 계산하고,
  `receipts[]` 필터(아래)와는 범위가 다르다.
- `receipts[]`는 **영수증이 등록된 차수만** 포함한다(미등록 차수는 storeName/totalAmount가 없어
  섞으면 null 항목이 생기기 때문). 각 항목의 `settlementStatus`는 그 차수 단위로 따로 계산한다 —
  정산 명단(settlement_target)이 하나도 없으면(API-038 미실행) "정산중", 명단이 있으면 미입금이
  하나도 없어야 "정산 완료"다.
- `account`는 계좌가 설정된 차수 중 roundNo가 가장 작은 차수의 계좌를 대표로 쓴다(API-044와 같은
  기준). 계좌가 설정된 차수가 하나도 없으면 `account`는 null이다(에러로 막지 않는다).

**Response data**: scheduleTitle, status(일정 전체 정산 상태), progress.paidCount/totalCount/rate,
receipts[].roundId/label/storeName/totalAmount/registeredAt/settlementStatus(차수별 정산 상태),
account.bankName/accountNumber/accountHolder(설정된 계좌가 없으면 account 자체가 null)

**에러**: COMMON_404

### 운영 메모

#### API-046 · POST /api/schedules/{scheduleId}/notes · 운영 메모 작성
인증: 불필요. **Request**: type(GENERAL|TODO|DECISION|IDEA), title, assignee(선택), content(100자 이하)
type=TODO면 isTodoDone=false, 다른 유형은 null로 저장한다.
**Response data**: noteId. **에러**: COMMON_400, NOTE_404_SCHEDULE_NOT_FOUND

#### API-047 · GET /api/schedules/{scheduleId}/notes · 운영 메모 목록 조회
인증: 불필요. **Query**: type(ALL|GENERAL|TODO|DECISION|IDEA, 기본 ALL), keyword(선택), cursor(선택),
size(선택, 기본 20)

**Response data**: totalCount(type·keyword 조건에 맞는 전체 메모 수, 화면의 '메모 목록 N'),
notes[].noteId/type/title/assignee/content/isTodoDone(type=TODO만 값)/createdAt/updatedAt, nextCursor

cursor 페이지네이션(정렬 기준: createdAt 내림차순 — 최근에 작성한 메모가 위, tiebreaker id).

**에러**: NOTE_404_SCHEDULE_NOT_FOUND, NOTE_400_INVALID_CURSOR, COMMON_400(지원하지 않는 type)

#### API-048 · PATCH /api/notes/{noteId} · 운영 메모 수정
인증: 불필요. Request는 API-046과 같은 필드를 전부 선택값으로 받아 보낸 필드만 수정한다. assignee에 빈 문자열을
보내면 담당자를 지운다. 유형을 TODO로 바꾸면 isTodoDone=false, TODO에서 다른 유형으로 바꾸면 null.
**Response data**: noteId. **에러**: COMMON_400, NOTE_404_NOT_FOUND

#### API-049 · DELETE /api/notes/{noteId} · 운영 메모 삭제
인증: 불필요. **에러**: NOTE_404_NOT_FOUND

#### API-050 · PATCH /api/notes/{noteId}/todo · 할 일 완료 처리
인증: 불필요. **Request**: isTodoDone(boolean). **Response data**: noteId, isTodoDone
**에러**: COMMON_400(isTodoDone 누락), NOTE_404_NOT_FOUND, NOTE_400_NOT_TODO_TYPE(할일 유형이 아님)

### 회의

#### API-051 · POST /api/terms/{termId}/meetings · 회의 만들기
인증: 불필요. **Request**: title, meetingDate, categoryId(`meeting_category`, 일정 카테고리와
별도 운영 — decisions #21), attendees(자유 텍스트, 선택),
agendas(string[], 안건 내용 목록), scheduleId(선택)

**Logics**: `title`·`attendees`는 저장 전에 한 줄로 정규화된다 — 줄바꿈·탭을 공백으로 바꾸고
연속 공백을 한 칸으로 접는다. 거부하지 않고 조용히 편다(decisions #84). 안건 `content`는
여러 줄이 정상이라 그대로 둔다.

`scheduleId`는 화면 경로를 가른다 — 운영 공간에서 만든 회의록은 보내지 않고(일정과 무관),
일정 상세의 "회의록 작성하기"로 만든 회의록은 그 일정 ID를 보낸다. 보낸 경우 그 회의의 안건 전체가
일정에 속하고, 안건 연결(API-013) 후보에서도 빠진다(decisions #44). 소속은 생성 시점에 확정되며
이후 바꿀 수 없다.

**Response data**: meetingId, agendaIds
**에러**: COMMON_400, MEETING_404_GROUP_TERM_NOT_FOUND(기수 없음), MEETING_404_CATEGORY_NOT_FOUND(카테고리 없음),
MEETING_404_SCHEDULE_NOT_FOUND(일정 없음)

---

#### API-052 · GET /api/terms/{termId}/meetings · 회의 목록 조회
인증: 불필요.

**Query**: status(진행중|완료, 선택), keyword(선택 — **제목·참석자** 부분 일치, 대소문자 무시),
categoryId(선택), scheduleId(선택), cursor(선택), size(선택, 기본 20)

`keyword`는 저장값과 같은 규칙으로 정규화해서 비교한다 — 공백을 몇 칸 치든, 앞뒤에 공백이
붙든 같은 결과가 나온다(decisions #84).

**Response data**: meetings[].meetingId/title/meetingDate/categoryName/
status(IN_PROGRESS|COMPLETED)/agendaCount/attendees/createdAt, nextCursor,
totalCount(cursor 없는 첫 페이지에서만 값이 있고, 이후 페이지는 null — API-064와 같은 패턴.
목록 상단의 "회의 목록 N개" 표시용, `docs/decisions.md` #19)

cursor 페이지네이션(정렬 기준: meetingDate 내림차순 — 최근 회의가 먼저).

**Logics**: `scheduleId`를 주면 그 일정에 속한 회의록만 내려준다 — 일정 상세의 "회의록 & 안건"
섹션에서 회의록 카드를 그릴 때 쓴다(같은 섹션의 개별 연결 안건은 API-015). 주지 않으면 일정 소속
여부와 무관하게 전부 포함한다(decisions #44).

**에러**: MEETING_400_INVALID_CURSOR(잘못된 cursor), MEETING_404_GROUP_TERM_NOT_FOUND(기수 없음)

---

#### API-053 · GET /api/meetings/{meetingId} · 회의 상세 조회(진행·회의록)
인증: 불필요. 회의 진행 화면과 회의 정리 화면 공통 사용.

**Response data**: meetingId, title, meetingDate, categoryId, attendees, status, endedAt,
progress.doneCount/totalCount, agendas[].agendaId/orderNo/content/discussionNote/decision/
status(PENDING|IN_PROGRESS|COMPLETED), summaryContent, summaryGeneratedAt

**에러**: COMMON_404

진행률 바는 `progress.doneCount/totalCount`(안건 상태가 COMPLETED인 개수 ÷ 전체 안건 수)
하나로만 계산한다. '다음 안건' 버튼(API-057)이 그 안건의 status를 COMPLETED로 저장하는
트리거이고, 이때마다 doneCount가 1씩 늘어난다. Figma에 있던 "안건1 논의중 45%" 같은 개별 안건
진행중 퍼센트는 구현하지 않는다(decisions #20).

---

#### API-054 · PATCH /api/meetings/{meetingId} · 회의 정보 수정
인증: 불필요. 제목·날짜·카테고리·참석자만 수정(안건은 API-056/057/058로 별도 처리). 보낸 필드만 수정.
일정 소속(`scheduleId`)은 받지 않는다 — 생성 시점에 확정하고 옮기지 않는다(decisions #44).

**Request**: title, meetingDate, categoryId, attendees

`title`·`attendees`는 API-051과 같게 저장 전에 한 줄로 정규화된다(decisions #84).

**Response data**: meetingId
**에러**: COMMON_400, MEETING_404_NOT_FOUND, MEETING_404_CATEGORY_NOT_FOUND, MEETING_409_ALREADY_ENDED

---

#### API-055 · DELETE /api/meetings/{meetingId} · 회의 삭제
인증: 불필요. 회의 상태(IN_PROGRESS/COMPLETED)와 무관하게 삭제를 허용한다(decisions #15).
안건과 그 안건의 일정-안건 연결(`schedule_agenda_link`)도 함께 지운다. 안건 삭제(API-058)도 같다.
**에러**: MEETING_404_NOT_FOUND

---

#### API-056 · POST /api/meetings/{meetingId}/agendas · 안건 추가
인증: 불필요. 기존 안건 목록 끝에 추가(orderNo=마지막+1), status=PENDING으로 생성.

**Request**: content(1~100자)
**Response data**: agendaId, orderNo
**에러**: COMMON_400, MEETING_404_NOT_FOUND, MEETING_409_ALREADY_ENDED

---

#### API-057 · PATCH /api/agendas/{agendaId} · 안건 내용 저장
인증: 불필요. **Request**: content, discussionNote, decision, status(PENDING|IN_PROGRESS|COMPLETED) —
전부 선택값(보낸 필드만 수정). content는 "회의 수정" 화면에서 기존 안건 원문을 고칠 때 쓴다

**Logics**: 화면의 '다음 안건' 버튼은 이 API를 status='COMPLETED'로 호출하는 것이다. '회의
중단하기'는 같은 API를 status 없이(또는 바꾸지 않고) 호출해 내용만 저장하며 회의는 '진행중'을
유지한다. 이미 COMPLETED인 안건을 다른 상태로 되돌리는 요청(예: status=PENDING)은 거부한다 —
status 없이 내용(content/discussionNote/decision)만 고치는 건 완료된 안건이어도 허용한다.

**Response data**: agendaId, status, progress
**에러**: AGENDA_404_NOT_FOUND, MEETING_409_ALREADY_ENDED, AGENDA_409_ALREADY_COMPLETED

#### API-058 · DELETE /api/agendas/{agendaId} · 안건 삭제
인증: 불필요. 삭제 후 남은 안건들의 진행률 재계산. 안건 1개만 남은 회의는 삭제 불가.
**에러**: AGENDA_404_NOT_FOUND, MEETING_409_ALREADY_ENDED, AGENDA_409_AT_LEAST_ONE_REQUIRED

---

#### API-059 · POST /api/meetings/{meetingId}/end · 회의 종료
인증: 불필요. 안건이 전부 완료되지 않았어도 종료를 허용한다.
**Response data**: meetingId, status, endedAt, doneAgendaCount, totalAgendaCount
**에러**: MEETING_404_NOT_FOUND, MEETING_409_ALREADY_ENDED(이미 종료)

---

#### API-060 · POST /api/meetings/{meetingId}/summary · AI 요약 생성
인증: 불필요. 외부 AI로 안건의 논의 내용·결정 사항을 종합 요약하고 `summary_content`를 덮어쓴다.
원본(meeting_agenda)은 항상 보존. "다시 요약하기"도 이 API 재호출.

**Response data**: summarizedAgendaCount, summaryContent, summaryGeneratedAt
**에러**: MEETING_404_NOT_FOUND, MEETING_502_SUMMARY_FAILED(502)

> 원문엔 404가 COMMON_404로 적혀 있었으나, meeting 도메인 다른 API와의 일관성을 위해
> MEETING_404_NOT_FOUND로 구현했다(decisions #25).

#### API-061 · PATCH /api/meetings/{meetingId}/summary · 회의록 저장(요약 수정)
인증: 불필요. **Request**: summaryContent. **Response data**: meetingId. **에러**: MEETING_404_NOT_FOUND

### 문서함

#### API-062 · GET /api/files/policy · 파일 업로드 정책 조회
인증: 불필요. **Response data**: allowedExtensions[], maxSizeBytes

수치는 `FileUploadPolicy` 상수가 정본이고 이 API가 그대로 내려준다 — 최대 20MB, 문서(pdf/doc/docx/
ppt/pptx/xls/xlsx/txt/hwp/hwpx)·이미지(jpg/jpeg/png/gif/webp)·zip(decisions #16).

---

#### API-063 · POST /api/terms/{termId}/files · 파일 업로드
인증: 불필요. 한 번에 1개씩. 자료 이름은 파일명으로 자동 입력(수정 가능), 확장자는 서버가 고정.
scheduleId가 있으면 일정 자료로 연결되고 문서함에도 자동 노출.

**Request**: file(multipart/form-data), name, category(ACTIVITY_DOC|BUDGET_PLAN|PHOTO|LEDGER_FILE),
scheduleId(선택)

자료명은 255자까지다(`file.name` 컬럼 길이). 길이 검증을 통과한 뒤 한 줄로 정규화해 저장한다
(줄바꿈·탭 → 공백, 연속 공백 → 한 칸. decisions #84).

**Logics**: 확장자가 jpg/jpeg/png/gif면 업로드와 함께 **썸네일을 만들어 저장**한다(긴 변 512px,
원본이 더 작으면 확대하지 않음). webp는 JDK ImageIO에 디코더가 없어 대상이 아니다. 생성에
실패해도 **업로드는 그대로 성공**하고 썸네일만 없는 상태(`thumbnailUrl: null`)가 된다 —
그리드 표시를 위한 최적화일 뿐이라 이것 때문에 업로드가 깨지면 안 된다(decisions #82).

**Response data**: fileId, name
**에러**: FILE_400_REQUIRED_FIELD_MISSING(필수 누락), FILE_400_NAME_TOO_LONG(자료명 256자 이상),
FILE_400_EXTENSION_NOT_ALLOWED, FILE_404_GROUP_TERM_NOT_FOUND(기수 없음),
FILE_404_SCHEDULE_NOT_FOUND(일정 없음), FILE_400_SCHEDULE_TERM_MISMATCH(scheduleId가 다른 기수 소속),
COMMON_400(file 파트 누락), COMMON_413(용량 초과)

용량 초과가 `FILE_413_SIZE_EXCEEDED`가 아니라 `COMMON_413`인 이유는, 서버의 multipart 상한이
`FileUploadPolicy`의 상한과 같아서 서비스 검증 전에 요청 파싱 단계에서 끊기기 때문이다.
`FILE_413_SIZE_EXCEEDED`는 서비스를 직접 호출하는 경로를 위한 방어로만 남아 있다(decisions #43).

---

#### API-064 · GET /api/terms/{termId}/files · 파일 목록 조회
인증: 불필요. deleted_at IS NULL인 파일만.

**Query**: category(ALL|ACTIVITY_DOC|BUDGET_PLAN|PHOTO|LEDGER_FILE, 기본 ALL),
keyword(선택, 자료명 부분 일치), sort(LATEST|OLDEST|NAME, 기본 LATEST), scheduleId(선택),
cursor(선택), size(선택, 기본 20)

`keyword`는 저장값과 같은 규칙으로 정규화해서 비교한다(decisions #84).

**Response data**: files[].fileId/name/extension/size/category/scheduleId/thumbnailUrl/createdAt,
nextCursor, totalCount(cursor 없는 첫 페이지에서만 값이 있고, 이후 페이지는 null — API-029
progress.totalCount·API-037 targetCount와 같은 패턴. 페이지네이션용 totalCount가 아니라
"총 N개" 표시용)

`thumbnailUrl`은 활동사진 그리드 뷰가 쓰는 축소본 주소다(API-080의 `variant=THUMBNAIL`을 가리킨다).
**썸네일이 없으면 null** — 이미지가 아닌 자료, webp, 생성에 실패한 자료가 여기 해당하고, 그때
프론트는 리스트 뷰와 같은 파일 타입 아이콘을 그린다(decisions #82).

cursor 페이지네이션. sort별로 cursor 키가 다르다: LATEST/OLDEST는 createdAt, NAME은 name.
둘 다 id를 tiebreaker로 쓴다.

**에러**: FILE_404_GROUP_TERM_NOT_FOUND(기수 없음), FILE_400_INVALID_CURSOR(잘못된 cursor)

---

#### API-065 · GET /api/files/{fileId}/download-url · 파일 미리보기·다운로드 URL 발급
인증: 불필요. **Query**: mode(PREVIEW|DOWNLOAD, 기본 DOWNLOAD). **Response data**: url.
**에러**: FILE_404_NOT_FOUND(없는 파일, 소프트 삭제된 파일)

`url`은 외부 스토리지 주소가 아니라 이 서버 자신의 API-080을 가리킨다(`mode`를 그대로 실어서).
실제 파일 바이트가 나가는 동작은 API-080 쪽에서 일어난다. 만료 시각은 응답에 담지 않는다 —
만료를 강제하는 구현이 없다(결정 42).

---

#### API-080 · GET /api/files/{fileId}/content · 파일 콘텐츠 서빙
인증: 불필요. API-065가 발급하는 `url`이 실제로 가리키는 대상 — 프론트가 이 API를 JSON
클라이언트로 직접 호출하는 게 아니라, `<a href>`나 `<iframe src>`처럼 브라우저가 그 주소로
직접 요청을 보내는 방식으로 쓰인다(ZIP인 API-068과 같은 패턴).

**Query**: mode(PREVIEW|DOWNLOAD, 기본 DOWNLOAD), variant(ORIGINAL|THUMBNAIL, 기본 ORIGINAL)

두 파라미터는 축이 다르다 — `mode`는 **어떻게 열지**(Content-Disposition)를, `variant`는
**무엇을 내보낼지**(원본 바이트 / 축소본 바이트)를 정한다.

**Response**: 파일 바이트 스트림(JSON 래퍼 없음). `Content-Type`은 **실제로 나가는 바이트의**
확장자로 추론한다 — gif 원본의 썸네일은 png라 `image/png`가 되며, 원본 확장자를 쓰면 어긋난다.
`Content-Disposition`은 `mode=DOWNLOAD`면 `attachment`(브라우저가 저장 대화상자를 띄움),
`PREVIEW`면 `inline`(브라우저가 바로 열어서 보여줌)으로 서버가 강제한다.

**에러**: FILE_404_NOT_FOUND(없는 파일, 소프트 삭제된 파일 — URL을 알고 있어도 열리지 않는다.
썸네일도 같이 막힌다), FILE_404_THUMBNAIL_NOT_FOUND(`variant=THUMBNAIL`인데 썸네일이 없는 자료),
COMMON_400(enum에 없는 mode·variant 값)

썸네일이 없을 때 원본으로 폴백하지 않는 건 의도다 — pdf에 썸네일을 요청했는데 20MB가 그대로
나가는 쪽이 더 나쁘다(decisions #82).

#### API-066 · DELETE /api/files/{fileId} · 파일 삭제
인증: 불필요. 소프트 삭제(deleted_at). **에러**: FILE_404_NOT_FOUND(없는 파일)

이미 삭제된 파일에 다시 호출해도 200이다(멱등). `deleted_at`은 처음 삭제한 시점을 유지한다.

#### API-067 · POST /api/files/{fileId}/restore · 파일 삭제 되돌리기
인증: 불필요. **Response data**: fileId.
**에러**: FILE_404_NOT_FOUND, FILE_409_LEDGER_ALREADY_EXISTS(장부를 복구하려는데 그 기수에 활성
장부가 이미 있음)

되돌리기 기간 제한은 없고 스토리지 파일을 영구 삭제하는 배치도 두지 않는다(decisions #16).
이미 복구된 파일에 다시 호출해도 200이다. 단 장부(LEDGER_FILE)는 기수당 1개만 유지해야 하므로,
교체로 밀려난 장부를 되살리려면 현재 활성 장부를 먼저 삭제해야 한다(decisions #43).

---

#### API-068 · GET /api/terms/{termId}/files/zip · 파일 전체 다운로드(ZIP)
인증: 불필요. 현재 검색·분류 조건에 맞는 파일을 ZIP으로 스트리밍.

**Query**: category(ALL|ACTIVITY_DOC|BUDGET_PLAN|PHOTO|LEDGER_FILE, 기본 ALL), keyword(선택)

`keyword`는 API-064와 **같은 조건 빌더**를 타므로 목록에 보이는 자료 집합이 그대로 ZIP에 담긴다
(검색어 정규화도 동일 — decisions #84).

**Response**: `Content-Type: application/zip` 파일 스트림(JSON 래퍼 없음). 파일명 형식:
`{기수}자료{N}개.zip`.
**에러**: FILE_404_GROUP_TERM_NOT_FOUND(기수 없음), FILE_404_NO_DOWNLOADABLE_FILES(다운로드할 파일 없음)

### 예산

#### API-069 · GET /api/terms/{termId}/ledger · 예산 요약·내역 조회
인증: 불필요. currentBalance·totalIncome·totalExpense는 **그룹 전체(모든 기수) 누적**이다(기수가
바뀌어도 이월, currentBalance = totalIncome − totalExpense). entries는 이 기수 내역만. source는
`auto_category` 유무로 판단한다(null이 아니면 자동 기록 RECEIPT, null이면 직접 입력 MANUAL —
`schedule_round_id`로 판단하지 않는다. 자동기록이 일정 단위로 바뀌어 이 컬럼이 항상 null이기
때문이다).

**Query**: type(ALL|INCOME|EXPENSE, 기본 ALL), cursor(선택), size(선택, 기본 20)

**Response data**: ledgerFile.fileId/name, currentBalance, totalIncome, totalExpense,
entries[].entryId/type(INCOME|EXPENSE)/itemName/amount/entryDate/source(RECEIPT|MANUAL), nextCursor

cursor 페이지네이션(정렬 기준: entryDate 내림차순, 동일 시 id 내림차순).

**에러**: COMMON_404, COMMON_400(잘못된 cursor)

> **미확정**: 내역의 분류(예: "· 공연" 같은 태그) 저장할 컬럼을 `ledger_entry`에 추가할지는 구현
> 시점에 정한다. 장부 초기 잔액(기초 총액) 자동 인식은 `docs/decisions.md` #10에 따라 보류.

---

#### API-070 · POST /api/terms/{termId}/ledger · 장부 직접 입력
인증: 불필요. auto_category·schedule·schedule_round는 전부 null로 생성한다(자동 기록과 구분).

**Request**: type(INCOME|EXPENSE), itemName, amount, entryDate
**Response data**: entryId. **에러**: COMMON_400, COMMON_404(존재하지 않는 termId)

#### API-071 · GET /api/ledger/{entryId} · 장부 내역 상세 조회
인증: 불필요. source는 API-069와 같은 기준(auto_category 유무)으로 판별한다. origin(자동기록
출처 일정: scheduleId, title)은 **자동 기록이면서 출처 일정(schedule)이 살아있을 때만** 채우고,
직접 입력과 **출처 일정이 삭제되어 고아가 된 자동 기록**은 둘 다 null이다 — source로 이미 "자동
기록이었는지"를 구분할 수 있어 고아 여부를 나타내는 별도 플래그를 두지 않는다.

**Response data**: entryId, type, itemName, amount, entryDate, source, origin(scheduleId/title,
없으면 null). **에러**: COMMON_404

#### API-072 · PATCH /api/ledger/{entryId} · 장부 내역 수정
인증: 불필요. 직접 입력 내역만 수정 가능. 자동 기록은 고아 여부와 무관하게 항상 수정 불가 — 삭제와
달리 예외 없음(decisions #5).

**Logics**: 보낸 필드만 반영하는 부분 수정이다 — 생략(null)한 필드는 기존 값을 그대로 유지한다.
`type`(INCOME/EXPENSE) 변경도 허용한다.

**Request**: type(선택), itemName(선택), amount(선택), entryDate(선택)
**에러**: COMMON_400, COMMON_404, LEDGER_409_AUTO_ENTRY_LOCKED

---

#### API-073 · DELETE /api/ledger/{entryId} · 장부 내역 삭제
인증: 불필요. 자동 기록은 **출처 일정(schedule)이 살아있으면** LEDGER_409_AUTO_ENTRY_LOCKED로 삭제
불가(다음 "정산 내역 새로고침" 때 재계산과 충돌). **그 일정 자체가 삭제되어 고아가 된** 자동 기록은
일반 항목처럼 삭제 가능. 직접 입력 내역은 항상 삭제 가능(decisions #5).

**Response**: 200 + `BaseResponse.success()`(COMMON_200).
**에러**: COMMON_404, LEDGER_409_AUTO_ENTRY_LOCKED(출처 일정이 살아있는 경우)

---

#### API-074 · POST /api/terms/{termId}/ledger/sync · 정산 내역 새로고침
인증: 불필요. **일정(schedule) 단위로 최대 4종 자동 기록**을 계산해 반영한다(차수 단위가 아니다):
`RECEIPT_EXPENSE`(영수증 지출, 지원금·버림 차액 제외한 billedAmount 합), `CLUB_SUBSIDY_EXPENSE`
(동아리 지원금 합), `ROUNDING_SUBSIDY_EXPENSE`(100원 버림 차액 합, "추가 지원금"),
`PAYMENT_INCOME`(부원 납부 수입). **지출 3줄의 합은 그 일정의 영수증 총액 합과 항상 같다.**

**Logics**:
- `PAYMENT_INCOME`은 그 일정 전체 정산 대상이 1명 이상이고 전원 PAID일 때만 billedAmount 합이다.
  일부만 납부했거나 대상이 0명이면 0원(=생성 안 함)이다.
- 정산 명단이 없거나(API-038 미실행) 영수증이 없는 차수는 그 차수 전체를 계산에서 제외한다.
- 계산 결과가 0원(이하)인 항목은 생성하지 않고, 이미 있던 자동 기록은 조건이 깨진 것으로 보고
  **그 항목만 삭제**한다(예: 입금 되돌리기로 전원 납부가 깨짐, 영수증·지원금이 0원이 됨). 삭제
  대상은 그 일정의 자동 기록(`auto_category IS NOT NULL`)뿐이고, 직접 입력 내역과 다른 일정의
  자동 기록은 건드리지 않는다.
- 금액·항목명(`일정 제목 + 라벨`)·거래일(그 일정 `schedule.start_at`의 날짜 부분) 중 하나라도
  기존 값과 실제로 다를 때만 갱신하고 `updatedCount`에 포함한다. 같은 값으로 재호출하면
  `updatedCount`는 0이다.

**Response data**: createdCount, updatedCount, deletedCount
**에러**: COMMON_404(존재하지 않는 termId)

---

#### API-075 · GET /api/terms/{termId}/ledger/export · 장부 엑셀 다운로드
인증: 불필요. 원본 업로드 엑셀(API-063 LEDGER_FILE)과 완전히 별개로, 서버가 `ledger_entry`로 매번
새 엑셀을 생성한다(File 엔티티·문서함에 등록하지 않음).

**Logics**:
- 행 범위는 **이 기수**(API-069의 entries와 같은 범위), 요약(누적 수입·누적 지출·잔액)은 **그룹
  전체 누적**(API-069의 currentBalance/totalIncome/totalExpense와 같은 범위) — 범위가 서로 다르다.
  요약 라벨에 "(전체 기수)"처럼 범위를 명시하고, 이와 별도로 "이번 기수 수입 합계"/"이번 기수 지출
  합계" 한 행을 더 보여준다(해당 기수 entries 합).
- 행 정렬은 entryDate 오름차순, 동일 시 id 오름차순이다(API-069의 내림차순과 방향만 반대).
- 내역이 0건이어도 에러 없이 200으로 헤더만 있는 엑셀을 생성한다.
- 행별 누적 잔액 열은 없다(기수 중간부터 시작하는 행의 기초 잔액을 정의할 수 없어서, decisions #10
  미해결 영역과 연결).
- 프론트는 응답 `fileName`을 다운로드 파일명으로 지정한다(`downloadUrl` 자체의 저장 키는 사람이
  읽는 이름이 아니다).

**Response data**: downloadUrl, fileName("{그룹명}_{기수명}_장부_{yyyyMMdd}.xlsx"), generatedAt
**에러**: COMMON_404(존재하지 않는 termId)

### AI 비서

#### API-076 · POST /api/terms/{termId}/ai/chat-rooms · AI 대화방 생성
인증: 불필요. **Request**: title. **Response data**: chatRoomId. 대화방은 기수당 여러 개 유지하며
단일화하지 않는다(결정 완료, 현행 구조 그대로 — `docs/decisions.md` #11).

`title`은 저장 전에 한 줄로 정규화된다(decisions #84). 보내지 않으면 null로 두고, 첫 질문을
보낼 때 그 질문으로 지은 제목이 채워진다.

#### API-077 · GET /api/terms/{termId}/ai/chat-rooms · AI 대화방 목록 조회
인증: 불필요. 기수 단위(로그인이 없어 그 기수 운영진 전체가 공유).

**Query**: keyword(선택, 대화방 제목 부분 일치), cursor(선택), size(선택, 기본 20)

`keyword`는 저장값과 같은 규칙으로 정규화해서 비교한다(decisions #84).

**Response data**: chatRooms[].chatRoomId/title/updatedAt, nextCursor,
totalCount(cursor 없는 첫 페이지에서만 값이 있고, 이후 페이지는 null — API-064와 같은 패턴.
대화 기록 시트 상단의 "총 N개" 표시용, `docs/decisions.md` #19)

cursor 페이지네이션(정렬 기준: updatedAt 내림차순 — 최근 대화한 방이 먼저).

**Logics**: `keyword`는 대화방 **제목**에만 건다(부분 일치, 대소문자 무시). 대화방을 만들고 아직
첫 질문을 보내지 않으면 제목이 null이라, 그런 방은 `keyword`를 준 검색 결과에서 빠진다 —
첫 질문을 보내는 순간 제목이 채워지므로(실패해도 질문 원문으로 폴백) 실제로 사라지는 건
"아직 아무것도 묻지 않은 빈 방"뿐이다.

**에러**: CHAT_400_INVALID_CURSOR(잘못된 cursor), CHAT_404_GROUP_TERM_NOT_FOUND(기수 없음)

---

#### API-078 · POST /api/ai/chat-rooms/{chatRoomId}/messages · AI 메시지 전송
인증: 불필요. **Request**: content. **Response data**: messageId, role, content, navigation,
cardType, card, createdAt

AI가 자유롭게 쓰는 건 `content` 하나뿐이다. `navigation`·`cardType`·`card`는 모델이 고르지 않고
백엔드가 "어떤 도구가 실행됐는지"로 정하므로, FE가 받을 수 있는 값의 범위가 아래 표로 닫혀 있다.

| 필드 | 타입 | 설명 |
|---|---|---|
| messageId | number | 저장된 AI 답변 메시지 ID |
| role | string | 항상 ASSISTANT — 보낸 질문이 아니라 AI 답변만 돌려준다 |
| content | string | 답변 텍스트. 형식을 강제하지 않는다(구조화된 값은 navigation·card로 내려간다) |
| navigation | object | 답변 아래 "…로 이동 →" 버튼. 이동할 곳이 없으면 null |
| navigation.type | string | SETTLEMENT / ATTENDANCE / NOTE / SCHEDULE / MEETING / FILE / BUDGET |
| navigation.targetId | number | SETTLEMENT·ATTENDANCE·NOTE·SCHEDULE은 일정 ID, MEETING은 회의 ID, 기수 단위 화면인 FILE·BUDGET은 null |
| cardType | string | 결과 카드 유형. 현재 "미입금자 조회" 하나뿐이고, 카드가 없으면 null |
| card | object | 카드 상세. cardType별 스키마는 아래 참고. 카드가 없으면 null |
| createdAt | string | 답변 생성 시각 |

`navigation`은 경로(URL)를 내려주지 않고 화면 종류와 대상 ID만 준다(decisions #40).
다음 세 경우에 null이다 — ① 화면을 가리키는 도구가 실행되지 않았을 때(일정·회의 검색만 돌았거나
어느 일정인지 되묻는 답변일 때), ② 같은 도구가 서로 다른 대상으로 두 번 이상 실행돼 어느 일정·회의로
보낼지 정할 수 없을 때, ③ 대상 ID가 필요한 화면인데 도구 인자에 그 ID가 없을 때(예: 일정을 고르지
않은 메모 검색).

**cardType이 "미입금자 조회"일 때 `card` 스키마**
| 필드 | 타입 | 설명 |
|---|---|---|
| title | string | 카드 제목(예: "가을공연·미입금") |
| totalAmount | number | 미입금 총액(대상자별 미납 금액의 합) |
| count | number | 미입금 인원 |
| items[].attendeeId | number | 참가자 ID. 카드의 '입금 확인' 버튼이 API-040 호출에 쓴다 |
| items[].name | string | 이름 |
| items[].affiliation | string | 학과·소속 |
| items[].phoneLast4 | string | 전화번호 뒤 4자리 |
| items[].amount | number | 아직 내지 않은 금액(이미 납부한 차수는 제외한 미납분) |

```json
{
  "success": true, "code": "COMMON_200", "message": "요청에 성공했습니다.",
  "data": {
    "messageId": 201,
    "role": "ASSISTANT",
    "content": "가을공연 미입금자는 2명이고, 아직 100,000원이 남았어요.",
    "navigation": { "type": "SETTLEMENT", "targetId": 10 },
    "cardType": "미입금자 조회",
    "card": {
      "title": "가을공연·미입금", "totalAmount": 100000, "count": 2,
      "items": [
        { "attendeeId": 401, "name": "강민준", "affiliation": "광고홍보학과", "phoneLast4": "1140", "amount": 70000 },
        { "attendeeId": 402, "name": "서윤호", "affiliation": "경영학과", "phoneLast4": "2087", "amount": 30000 }
      ]
    },
    "createdAt": "2026-11-13T21:01:00"
  }
}
```

카드는 미입금자 조회 하나만 만든다 — 공지 작성·일정 조회 같은 나머지 의도는 카드 없이 텍스트로만
답한다. 포맷이 확정된 카드가 그것뿐이기 때문이다(decisions #33, §7 13번). 입금 현황 도구가 돌았어도
전원 납부 완료라 보여줄 대상이 없으면 카드를 만들지 않는다.

**에러**: COMMON_400(내용 없음), CHAT_404_NOT_FOUND(없는 대화방), CHAT_502_AI_FAILED(AI 호출 실패)

---

#### API-079 · GET /api/ai/chat-rooms/{chatRoomId}/messages · AI 메시지 목록 조회
인증: 불필요. **Query**: cursor(선택), size(선택, 기본 20)

**Response data**: messages[].messageId/role/content/navigation/cardType/card/createdAt, nextCursor

cursor 페이지네이션(정렬 기준: createdAt 오름차순 — 대화 순서대로).

**에러**: CHAT_404_NOT_FOUND(없는 대화방), CHAT_400_INVALID_CURSOR(잘못된 cursor)

`navigation`은 API-078과 같은 형식이다. 대화를 다시 열어도 "…로 이동 →" 버튼이 보여야 하므로
메시지에 함께 저장해 두고 목록에서도 그대로 내려준다.

`cardType`·`card`도 API-078과 같은 형식이다. 단 `card`는 전송 시점의 스냅샷(`content_json`)이라
이후 상태(예: 입금 여부)가 바뀌어도 갱신되지 않는다. FE가 최신값이 필요하면 해당 조회 API(예: 입금
현황 조회)를 다시 호출하는 것을 권장한다.

`role`은 이 API에서만 USER와 ASSISTANT 둘 다 나온다 — 보낸 질문과 받은 답변을 번갈아 내려주기
때문이다. USER 메시지는 navigation·cardType·card가 항상 null이다.

---

## 7. 미확정(TBD) 항목 모음

아래 19개 항목 중 **아직 정하지 않은 것은 3개**(1·12·19번)다. 나머지는
해소돼 각 항목에 결정 번호를 적어 뒀다. 번호는 한 번 붙이면 바꾸지 않는다 — 다른 문서·이슈가
이 번호를 가리키기 때문에, 해소된 항목도 지우지 않고 "해소됨"으로 남긴다.
구현 착수 전 미해소 항목에 들어가면 안 된다(§6에서 각 엔드포인트 자리에도 표시):

1. API-004/007 `color` — 일정 색상 8종의 실제 값(영문 상수명) 미정, Figma엔 스와치만 있고 이름이 없음
2. API-009 `operationMenuCount` 산정 기준 — 해소됨: 4개 고정(decisions #12)
3. ~~API-022 동일인 중복 등록 처리 기준~~ — **해소됨**: 같은 명단방에 이름·전화번호가 모두 같으면
   409로 거부한다. 전화번호를 받지 않는 명단방과 운영진 직접 추가는 검사하지 않는다.
4. ~~API-028 새 차수 생성 시 기존 참가자 자동 포함 여부~~ — **해소됨(decisions #72)**: 그 일정의
   기존 참가자 전원을 미출석 상태로 함께 포함한다.
5. ~~API-044 선택 차수들의 계좌가 다를 때 처리 방식(API-045 정산 홈의 대표 계좌 표시도 같은
   문제)~~ — **해소됨(decisions #55)**: 선택한 차수 중 roundNo가 가장 작은 차수의 계좌를
   대표로 쓴다.
6. ~~API-045 `progress`가 사람 수 단위인지 차수 수 단위인지~~ — **해소됨(decisions #59)**: 사람 수
   기준이다.
7. API-050 (해당 없음 — 이미 해소, 목록에서 제외) — *초안에서 제거*
8. API-053 안건 진행률 — 해소됨: 전체 진행률(doneCount/totalCount, 퍼센트 숫자 포함)만 계산하고
   개별 안건 퍼센트("논의중 45%")는 만들지 않기로 확정(decisions #20, 2026-11-11 Notion PRD
   재확인으로 최종 결론)
9. API-055 진행 중인 회의 삭제 허용 여부 — 해소됨: 상태와 무관하게 삭제 허용(decisions #15)
10. API-062 허용 확장자·최대 용량 실제 수치 — 해소됨: 20MB, 문서/이미지/zip 화이트리스트(decisions #16)
11. API-067 파일 삭제 되돌리기 가능 기간·스토리지 파일 영구 삭제 시점 — 해소됨: 기간 제한 없고
    영구 삭제 배치도 두지 않는다(decisions #16). 단 장부는 기수당 1개 제약 때문에 활성 장부가
    있으면 복구가 409로 막힌다(decisions #43)
12. API-069 내역 분류 컬럼 추가 여부(ERD 변경 필요), 장부 초기 잔액 AI 인식(보류, decisions #10)
13. ~~API-078 미입금자 조회 외 카드 포맷~~ — **해소됨(2026-11-13)**: 카드는 미입금자 조회
    하나만 만들고, 나머지 의도(공지 작성·일정 조회 등)는 카드 없이 텍스트로만 답한다.
    PRD·Figma에서 포맷이 확정된 카드가 미입금자 조회뿐이기 때문이다(decisions #33).
14. ~~API-014 자동 매칭 알고리즘 상세 기준~~ — **해소됨(2026-11-14)**: API-014 자체를 명세에서
    삭제해 정할 기준이 없어졌다. 자동으로 안건을 연결해 주는 화면이 처음부터 없었다(decisions #45).
15. ~~API-007 일정 수정 시 운영 공간 토글(`hasOperationSpace`) 변경 허용 여부~~ — **해소됨**: 꺼진 운영 공간을
   켜는 것만 허용하고(1차 차수 함께 생성) 켜진 운영 공간은 끌 수 없다.
16. ~~API-069 `totalIncome`/`totalExpense`(누적 수입·지출)가 기수 기준인지 그룹 전체 기간 기준인지~~
    — **해소됨(decisions #68)**: `currentBalance`와 같은 그룹 전체(모든 기수) 누적이다.
17. ~~AI 연동 벤더/모델 — 영수증 인식(API-036), 회의 요약(API-060), AI 채팅 응답(API-078)~~ —
    **해소됨(2026-11-12)**: meeting 쪽은 벤더 하나로 확정하지 않고 Gemini/OpenAI를
    `app.ai.provider` 설정값으로 전환하는 구조로 구현(decisions #26). 영수증 인식·AI 채팅이
    같은 구조를 재사용할지는 그 도메인 구현 시점에 별도 결정.
18. ~~API-051/052/053/054 `listSummary`(회의 목록 카드 한 줄 요약) 입력 경로~~ —
    **해소됨(2026-11-16, decisions #83)**: 입력 경로를 정하는 대신 **필드 자체를 폐기**했다.
    끝내 입력 UI가 생기지 않았고 AI 자동 생성도 택하지 않았다. `meeting.list_summary` 컬럼까지
    지웠고(Flyway V8), API-052의 keyword 검색 대상도 제목·참석자 2컬럼으로 줄었다.
19. API-052 `attendees` 자유 텍스트 vs 목록 화면 "참석자 수" 표시 요구 — Notion PRD(MEET-004)는
    회의 목록에서 "참석자 수"를 확인한다고 명시하는데, `attendees`는 "김가은, 홍보팀"처럼
    자유 텍스트라 콤마 개수로 세면 실제 인원수와 안 맞을 수 있다(팀은 사람 수가 아님).
    인원수를 어떻게 셀지(콤마 파싱으로 근사치만 낼지, 구조화된 참석자 필드를 별도로 둘지)
    미정(2026-11-11, 피그마·Notion PRD 교차검증으로 발견).

이 항목들은 해당 엔드포인트를 실제로 구현하는 시점에 정하고, 결정 즉시 `docs/decisions.md`에 追記한다.
