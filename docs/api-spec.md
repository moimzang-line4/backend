# API 명세 — 모임장

ERD(`docs/erd.md`)와 PRD(`docs/prd.md`)를 바탕으로 확정한 REST API 80개. 구현이 시작되면 springdoc이
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
| API-013 | POST | /api/schedules/{scheduleId}/agenda-links | 안건 연결(수동) | schedule_agenda_link | 불필요 |
| API-014 | POST | /api/schedules/{scheduleId}/agenda-links/auto | 연동할 회의록 찾기(자동) | schedule_agenda_link | 불필요 |
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
| schedule_category | API-011 | 없음(목록에서 충분) | API-010 | 없음(화면에 수정 버튼 없음) | 없음(화면에 삭제 버튼 없음) |
| schedule | API-005, API-009 | API-006 | API-004 | API-007 | API-008 |
| member_list | API-018 | API-019 | API-017 | 없음(화면에 수정 버튼 없음, 재생성만) | API-020 |
| member | API-023 | 없음(수정 화면이 상세 역할) | API-022(공개), API-024(운영진) | API-025 | API-026 |
| schedule_attendee | API-029(차수 출석 현황에 포함) | 없음 | API-030(현장), API-022/024(명단 등록 시 자동) | 없음 | API-032 |
| schedule_round | API-027 | API-033(영수증 중심) | API-028 | API-034(영수증 등록), API-043(계좌) | API-035(영수증 삭제 cascade) |
| round_attendee | API-029 | 없음 | API-030/API-022·024 등록 시 자동 | API-031 | API-032 cascade |
| settlement_target | API-037, API-042 | 없음 | API-038(명단 저장 시 일괄 생성) | API-040 | API-041 |
| operation_note | API-047 | 없음(목록 카드가 상세 역할) | API-046 | API-048, API-050 | API-049 |
| meeting | API-052 | API-053 | API-051 | API-054, API-059(종료), API-061(요약 저장) | API-055 |
| meeting_agenda | API-053 응답에 포함 | 없음 | API-056, API-051(생성 시 함께) | API-057 | API-058 |
| schedule_agenda_link | API-015 | 없음 | API-013, API-014 | 없음 | API-016 |
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
| 연결된 회의록 상세 | API-015, API-016 |
| 관련 안건 선택 | API-013, API-014 |
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
| AI 챗 | API-076, API-077, API-078, API-079, API-040(딥링크) |
| 자료 목록 / 문서 첨부 | API-064, API-063, API-065, API-080, API-066, API-067, API-068, API-062 |
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

### 정산 (SettlementErrorCode) — API-034~045
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| SETTLEMENT_400_AMOUNT_NOT_POSITIVE | 400 | 금액은 양의 정수여야 합니다. | API-034 |
| SETTLEMENT_400_SUBSIDY_OUT_OF_RANGE | 400 | 지원금은 0원 이상이며 영수증 금액 이하여야 합니다. | API-034 |
| SETTLEMENT_409_RECEIPT_ALREADY_REGISTERED | 409 | 이미 영수증이 등록된 차수입니다. | API-034 |
| SETTLEMENT_423_RECEIPT_LOCKED_BY_PAYMENT | 423 | 입금 내역이 있어 영수증을 수정할 수 없습니다. | API-034 |
| SETTLEMENT_423_PAID_CANNOT_DELETE | 423 | 입금 완료 상태는 삭제할 수 없습니다. | API-041 |
| SETTLEMENT_409_RECEIPT_NOT_REGISTERED | 409 | 영수증이 등록되지 않았습니다. | API-038, API-039 |
| SETTLEMENT_423_PAID_MEMBER_LOCKED | 423 | 입금 완료된 인원이 포함되어 있어 명단을 수정할 수 없습니다. | API-038 |
| SETTLEMENT_423_ROUND_LOCKED_BY_PAYMENT | 423 | 차수에 연결된 입금 내역이 있어 삭제할 수 없습니다. | API-035 |
| SETTLEMENT_409_UNREGISTERED_ROUND_INCLUDED | 409 | 영수증이 등록되지 않은 차수가 포함되어 있습니다. | API-044 |
| SETTLEMENT_409_ACCOUNT_NOT_SET | 409 | 입금 안내 계좌가 설정되지 않았습니다. | API-044 |
| SETTLEMENT_400_ROUND_SELECTION_REQUIRED | 400 | 포함할 차수를 1개 이상 선택해주세요. | API-044 |
| RECEIPT_502_RECOGNITION_FAILED | 502 | 영수증 인식에 실패했습니다. 다시 시도해 주세요. | API-036 |

### 출석 (AttendanceErrorCode) — API-030
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| ATTENDANCE_409_DUPLICATE_NAME | 409 | 같은 이름의 사람이 있어요. | API-030 |

### 운영 공간 (SpaceErrorCode) — API-013
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| SPACE_409_AGENDA_ALREADY_LINKED | 409 | 이미 연결된 안건입니다. | API-013 |

### 운영 메모 (NoteErrorCode) — API-050
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| NOTE_400_NOT_TODO_TYPE | 400 | 할 일 유형이 아닙니다. | API-050 |

### 회의 / 안건 (MeetingErrorCode, AgendaErrorCode) — API-051~061, 056~058
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| MEETING_409_ALREADY_ENDED | 409 | 이미 종료된 회의입니다. | API-056, API-057, API-058 |
| MEETING_502_SUMMARY_FAILED | 502 | AI 요약에 실패했습니다. 다시 시도해 주세요. | API-060 |
| MEETING_404_CATEGORY_NOT_FOUND | 404 | 존재하지 않는 회의 카테고리입니다. | API-051 |
| MEETING_404_GROUP_TERM_NOT_FOUND | 404 | 존재하지 않는 기수입니다. | API-051, API-052, API-081, API-082 |
| AGENDA_409_AT_LEAST_ONE_REQUIRED | 409 | 안건이 1개만 남아 삭제할 수 없습니다. | API-058 |

### 예산/장부 (LedgerErrorCode) — API-069~075
| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| LEDGER_409_AUTO_ENTRY_LOCKED | 409 | 자동 기록 내역은 삭제할 수 없습니다(원본이 살아있는 경우) / 수정은 예외 없이 항상 거부. | API-072, API-073 |

### 문서함 (FileErrorCode) — API-063~068
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
| FILE_404_NO_DOWNLOADABLE_FILES | 404 | 다운로드할 파일이 없습니다. | API-068 |

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
**에러**: COMMON_404(그룹 없음)

---

#### API-003 · PATCH /api/groups/{groupId} · 그룹 정보 수정(그룹명·기수)
인증: 불필요. 운영진 누구나 수정 가능(권한 분기 없음).

**Request**
| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| name | string | N | 그룹명 |
| termName | string | N | 새 기수명(지정 시 새 기수 생성) |

**Response data**: groupId, name, termId, termName

**에러**: COMMON_400, COMMON_404

### 일정

#### API-004 · POST /api/terms/{termId}/schedules · 일정 등록
인증: 불필요.

**Request**
| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| title | string | Y | 제목 |
| color | string | Y | 8종 중 하나(값 미확정 — §7 참고) |
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
**에러**: COMMON_400(필수 누락), SCHEDULE_400_END_BEFORE_START(종료가 시작보다 빠름), COMMON_404(카테고리 없음)

---

#### API-005 · GET /api/terms/{termId}/schedules · 일정 목록 조회(캘린더)
인증: 불필요.

**Query**: from(날짜, 필수), to(날짜, 필수), cursor(선택), size(선택, 기본 20)

**Response data**: schedules[].scheduleId/title/categoryName/color/isAllDay/startAt/endAt/location/hasOperationSpace, nextCursor

cursor 페이지네이션(정렬 기준: startAt 오름차순, tiebreaker id).

**에러**: COMMON_400(기간 파라미터 누락 시, 잘못된 cursor)

---

#### API-006 · GET /api/schedules/{scheduleId} · 일정 상세 조회
인증: 불필요.

**Response data**: scheduleId, title, color, categoryId, categoryName, isAllDay, startAt, endAt, location, memo, dDay, hasOperationSpace

**에러**: COMMON_404

---

#### API-007 · PATCH /api/schedules/{scheduleId} · 일정 수정
인증: 불필요. Request 필드는 API-004와 동일(createOperationSpace 제외). **Response**: scheduleId
**에러**: COMMON_400, SCHEDULE_400_END_BEFORE_START, COMMON_404

---

#### API-008 · DELETE /api/schedules/{scheduleId} · 일정 삭제
인증: 불필요.

**Logics**: 운영 메모, 차수, 참가자·출석, 영수증 정보, 정산 대상, 그 일정에서 올린 자료(file.schedule_id),
일정-안건 연결을 함께 삭제한다. 원본 회의록·부원 원본 명단은 유지한다. 이 일정에서 자동 생성된
`ledger_entry`(영수증 지출·완납 수입·추가 동아리 지원금 행)는 삭제하지 않고 보존하며,
`schedule_round_id` 참조만 NULL로 전환한다(decisions #4와 동일 원칙).

**Response**: 204 No Content. **에러**: COMMON_404

---

#### API-009 · GET /api/terms/{termId}/schedules/upcoming · 다가오는 운영 일정 조회
인증: 불필요. `has_operation_space = true`인 일정만 대상(운영 공간이 아닌 일정은 제외),
종료되지 않은 것만 시작 시간순.

**Query**: limit(선택, 기본 5)

**Response data**: schedules[].scheduleId/categoryName/startAt/title/location/operationMenuCount

**Logics**: `operationMenuCount`는 실제 데이터 유무와 무관하게 항상 4(메모장·출석·정산·자료)로
고정이다(decisions #12).

---

#### API-010 · POST /api/terms/{termId}/categories · 카테고리 추가
인증: 불필요. **Request**: name. **Response**: categoryId, name. **에러**: COMMON_400

#### API-011 · GET /api/terms/{termId}/categories · 카테고리 목록 조회
인증: 불필요. 일정 등록에서 쓰는 목록(회의 카테고리는 API-081/082로 분리 운영, decisions #21).
**Response data**: categories[].categoryId/name

#### API-081 · POST /api/terms/{termId}/meeting-categories · 회의 카테고리 생성
인증: 불필요. 일정 카테고리(API-010)와 별도 테이블(meeting_category)로 운영한다(decisions #21).
**Request**: name. **Response**: categoryId, name. **에러**: COMMON_400, MEETING_404_GROUP_TERM_NOT_FOUND

#### API-082 · GET /api/terms/{termId}/meeting-categories · 회의 카테고리 목록 조회
인증: 불필요. 회의 만들기·수정의 카테고리 선택 바텀시트에서 쓰는 목록.
**Response data**: categories[].categoryId/name. **에러**: MEETING_404_GROUP_TERM_NOT_FOUND

### 운영 공간

#### API-012 · GET /api/terms/{termId}/operation-spaces · 운영 공간 목록 조회
인증: 불필요.

**Logics**: SCHEDULE 타입은 `has_operation_space = true`인 일정만 포함한다 — 토글을 켜지 않은
일정은 운영 공간 목록에 나타나지 않는다(decisions #12). MEETING 타입은 토글 개념이 없어 전부 포함.

**Query**: status(ONGOING|PAST, 필수), type(ALL|SCHEDULE|MEETING, 기본 ALL), keyword(선택),
sort(UPCOMING|LATEST|OLDEST|NAME, 기본 LATEST), cursor(선택), size(선택, 기본 20)

**Response data**: items[].type/id/title/date/status, nextCursor

cursor 페이지네이션. `file`(API-064)과 같은 다중 정렬 패턴 — sort별로 cursor 키가 다르다:
UPCOMING/LATEST/OLDEST는 그 항목의 날짜(date, 즉 schedule.start_at 또는 meeting.meeting_date),
NAME은 title. 둘 다 id를 tiebreaker로 쓴다.

---

#### API-013 · POST /api/schedules/{scheduleId}/agenda-links · 안건 연결(수동)
인증: 불필요. **Request**: agendaIds(number[]). **Response data**: links[].linkId/agendaId
**에러**: COMMON_400(안건 미선택), COMMON_404, SPACE_409_AGENDA_ALREADY_LINKED(이미 연결된 안건)

---

#### API-014 · POST /api/schedules/{scheduleId}/agenda-links/auto · 연동할 회의록 찾기(자동)
인증: 불필요. 안건의 내용·논의 내용·결정 사항을 분석해 이 일정과 관련된 기존 안건만 자동 연결한다.
대상이 없으면 `linkedCount: 0`.

**Response data**: linkedCount, links[].linkId/agendaId. **에러**: COMMON_404

> **미확정**: 자동 매칭 알고리즘의 구체적 기준(날짜·카테고리·키워드 유사도 등 가중치)은
> 구현 시점에 정한다.

---

#### API-015 · GET /api/schedules/{scheduleId}/agenda-links · 연결된 회의록 조회
인증: 불필요. 연결은 안건 단위.

**Response data**: links[].linkId/meetingId/meetingTitle/meetingDate/agendaId/agendaOrderNo/agendaContent/
agendaStatus/discussionNote/decision/hasDecision

**에러**: COMMON_404

---

#### API-016 · DELETE /api/schedules/{scheduleId}/agenda-links/{linkId} · 회의록 연결 해제
인증: 불필요. `schedule_agenda_link` 행만 삭제, 원본 회의록·안건은 유지. **에러**: COMMON_404

### 부원 명단

#### API-017 · POST /api/terms/{termId}/member-lists · 명단방 생성
인증: 불필요.

**Request**: name, registerDeadline(선택), collectSchool/collectStudentId/collectPart/collectGrade/
collectPhone(boolean, 각 기본 true)

**Logics**: 이름·학과는 토글과 무관하게 항상 필수. 나머지 항목(학교·전화번호·학번·파트·학년)은
토글 ON이면 필수 입력, OFF면 입력 자체를 받지 않는다(수집 여부 자체를 토글이 결정, decisions #7).
서버가 `joinToken`을 생성한다.

**Response data**: memberListId, joinToken, joinUrl. **에러**: COMMON_400

---

#### API-018 · GET /api/terms/{termId}/member-lists · 명단방 목록 조회
인증: 불필요. **Response data**: memberLists[].memberListId/name/memberCount/status/registerDeadline
(status: RECRUITING|COMPLETED)

---

#### API-019 · GET /api/member-lists/{memberListId} · 명단방 상세(링크 관리) 조회
인증: 불필요.

**Response data**: memberListId, name, registerDeadline, status, memberCount, joinUrl, isLinkClosed,
fields.school/studentId/part/grade/phone(boolean)

**에러**: COMMON_404

---

#### API-020 · DELETE /api/member-lists/{memberListId} · 명단방 삭제
인증: 불필요. 명단방·부원 삭제, 링크 닫힘. 연결된 출석·정산 기록(그 부원이 속한 schedule_attendee와
그 round_attendee·settlement_target)도 함께 삭제한다. 장부(ledger_entry)의 수입 기록은 차수 단위라
이 삭제로 영향받지 않는다(decisions #2, #4 참고). **에러**: COMMON_404

---

#### API-021 · GET /api/public/member-lists/{joinToken} · 부원 등록 폼 조회(공개)
인증: 불필요(공개 API). **Response data**: groupName, memberListName, registerDeadline, fields(수집 항목)
**에러**: COMMON_404(링크 없음), 410(마감/닫힌 링크)

---

#### API-022 · POST /api/public/member-lists/{joinToken}/members · 부원 등록(공개)
인증: 불필요(공개 API).

**Request**: name, school(그 명단방 collectSchool=true일 때만), department, phone(collectPhone=true일
때만), studentId(선택), privacyConsent(boolean, 필수)

**Logics**: 필수 항목 = 이름·학과(고정) + 그 명단방의 collect_*=true 항목. 토글 OFF 항목은 폼에
노출되지 않아야 하며 보내도 무시한다. FE는 API-021의 `fields`로 노출·필수 여부를 동적으로 렌더링한다.

**Response data**: memberId. **에러**: COMMON_400(필수 누락, 개인정보 미동의), COMMON_404, 410(마감)

> **미확정**: 동일인 중복 등록 처리 기준(이름+연락처 일치 시 덮어쓸지 거부할지)은 구현 시점에 정한다.

---

#### API-023 · GET /api/member-lists/{memberListId}/members · 부원 목록 조회
인증: 불필요. **Query**: keyword(선택), cursor(선택), size(선택, 기본 20). **Response data**:
members[].memberId/name/department/phone/school/studentId/part/grade(수집 안 한 항목은 null),
nextCursor. cursor 페이지네이션(정렬 기준: createdAt). **에러**: COMMON_404, COMMON_400(잘못된 cursor)

---

#### API-024 · POST /api/member-lists/{memberListId}/members · 부원 추가(운영진)
인증: 불필요. **Request**: name, school, department, phone(API-022와 동일 동적 필수 규칙).
**Response data**: memberId. **에러**: COMMON_400, COMMON_404

---

#### API-025 · PATCH /api/members/{memberId} · 부원 수정
인증: 불필요. **Request**: name, school(선택), department, phone(선택) — 보낸 필드만 수정, 학번·파트·
학년은 이 화면에 없어 수정 불가. **Response data**: memberId. **에러**: COMMON_400, COMMON_404

#### API-026 · DELETE /api/members/{memberId} · 부원 삭제
인증: 불필요. 이 부원이 참여한 `schedule_attendee`는 유지하고 `member_id`만 NULL로 전환(스냅샷이라
원본 삭제와 무관). **에러**: COMMON_404

### 출석

#### API-027 · GET /api/schedules/{scheduleId}/rounds · 차수 목록 조회
인증: 불필요. **Response data**: rounds[].roundId/roundNo/label/hasReceipt. **에러**: COMMON_404

#### API-028 · POST /api/schedules/{scheduleId}/rounds · 차수 추가
인증: 불필요. **Response data**: roundId, roundNo, label. **에러**: COMMON_404

> **미확정**: 새 차수 생성 시 기존 일정 참가자를 자동으로 미출석 상태로 넣을지, 빈 차수로 시작할지는
> 구현 시점에 정한다.

---

#### API-029 · GET /api/rounds/{roundId}/attendees · 차수 출석 현황 조회
인증: 불필요. **Query**: status(UNCHECKED|CHECKED|ALL, 기본 UNCHECKED), keyword(이름·초성 검색, 선택),
cursor(선택), size(선택, 기본 20)

**Response data**: scheduleTitle, progress.checkedCount/totalCount/rate(이 progress.totalCount는
전체 참가자 수이지 페이지네이션 totalCount가 아니다 — 그대로 유지), attendees[].attendeeId/name/
affiliation/phoneLast4/isCheckedIn/checkedInAt, nextCursor

cursor 페이지네이션(정렬 기준: createdAt).

**에러**: COMMON_404, COMMON_400(잘못된 cursor)

---

#### API-030 · POST /api/rounds/{roundId}/attendees · 현장 인원 추가
인증: 불필요.

**Request**: name, affiliation, phoneLast4(전화번호 뒤 4자리, 숫자 4자리), allowSameName(boolean, 기본
false)

**Logics**: source=ON_SITE, 미출석으로 추가. 같은 이름이 이미 있으면 409(ATTENDANCE_409_DUPLICATE_NAME)로 응답하고
기존 인원 카드를 보여준다. "다른 사람으로 새로 추가"를 누르면 allowSameName=true로 재요청.

**Response data**: attendeeId, name, affiliation, phoneLast4, isCheckedIn
**에러**: COMMON_400, ATTENDANCE_409_DUPLICATE_NAME

---

#### API-031 · PATCH /api/rounds/{roundId}/attendees/{attendeeId} · 출석 처리·되돌리기
인증: 불필요. **Request**: isCheckedIn(boolean). **Response data**: attendeeId, name, isCheckedIn,
checkedInAt, progress. **에러**: COMMON_404

#### API-032 · DELETE /api/rounds/{roundId}/attendees/{attendeeId} · 차수에서 참가자 제외
인증: 불필요. **에러**: COMMON_404

### 정산

#### API-033 · GET /api/rounds/{roundId}/receipt · 영수증 조회
인증: 불필요. `isLocked`는 그 차수에 입금 완료가 1명이라도 있으면 true(수정·삭제 버튼 비활성).
`registeredAt`은 전용 컬럼이 없어 `schedule_round.updated_at`으로 대체한다.

**Response data**: roundId, label, storeName, transactedAt, totalAmount, clubSubsidyAmount, imageUrl,
registeredAt, isLocked. **에러**: COMMON_404

---

#### API-034 · PUT /api/rounds/{roundId}/receipt · 영수증 등록
인증: 불필요. 차수당 영수증 1개, schedule_round의 영수증 컬럼에 저장.

**Request**: storeName, transactedAt, totalAmount(양의 정수), clubSubsidyAmount(0 이상·총액 이하),
imageKey(사진 없이 등록 시 생략)

**Logics**: 금액(totalAmount/clubSubsidyAmount)이 바뀌면 그 차수에 이미 저장된 정산 명단의 1인
청구액도 API-038과 같은 공식으로 재계산한다. 입금 완료자가 있으면 SETTLEMENT_423_RECEIPT_LOCKED_BY_PAYMENT로 수정 자체가
막히므로 재계산은 아직 아무도 입금하지 않은 상태에서만 일어난다.

**Response data**: roundId, label, totalAmount, clubSubsidyAmount
**에러**: COMMON_400, SETTLEMENT_400_AMOUNT_NOT_POSITIVE, SETTLEMENT_400_SUBSIDY_OUT_OF_RANGE, COMMON_404, SETTLEMENT_409_RECEIPT_ALREADY_REGISTERED, SETTLEMENT_423_RECEIPT_LOCKED_BY_PAYMENT

---

#### API-035 · DELETE /api/rounds/{roundId}/receipt · 영수증 삭제
인증: 불필요.

**Logics**: 그 차수(schedule_round) 행 자체와 연결된 settlement_target(청구 내역)·round_attendee(출석
기록)를 함께 삭제한다. 다른 차수·원본 부원 명단·명단방은 영향받지 않는다. 이미 입금 완료 내역이
있으면 423으로 거부한다(실제로 삭제가 성공하는 시점은 그 차수의 입금 완료가 0명일 때뿐).
**장부(ledger_entry)는 영향받지 않는다** — 이미 생성된 내역·잔액은 유지되고 `schedule_round_id` 참조만
NULL로 전환된다(고아 상태). 상세 근거는 `docs/decisions.md` #4.

**Response**: 204. **에러**: COMMON_404, SETTLEMENT_423_ROUND_LOCKED_BY_PAYMENT

---

#### API-036 · POST /api/rounds/{roundId}/receipt/ocr · 영수증 AI 인식
인증: 불필요. 외부 AI(OCR)로 영수증 이미지를 분석해 인식 결과만 반환(저장 안 함). 인식 실패 항목은
null. "다시 촬영하기"는 이 API 재호출.

**Request**: image(multipart/form-data)
**Response data**: storeName, transactedAt, totalAmount, imageKey
**에러**: COMMON_400(이미지 없음/형식 오류), RECEIPT_502_RECOGNITION_FAILED

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
**에러**: COMMON_400(명단 비어있음), COMMON_404, SETTLEMENT_409_RECEIPT_NOT_REGISTERED, SETTLEMENT_423_PAID_MEMBER_LOCKED

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
**Response data**: attendeeId, name, paymentStatus, paidAt
**에러**: COMMON_404

---

#### API-041 · DELETE /api/schedules/{scheduleId}/payments/{attendeeId} · 입금 명단에서 삭제
인증: 불필요. 미입금 카드 스와이프 "삭제하기". URL에 roundId가 없으므로 scheduleId 기준으로 그
일정의 **모든 차수**에서 그 사람을 제외한다(특정 차수 1개만 빼는 게 아니다). 각 차수는 남은
인원으로 청구액 재계산.
**에러**: COMMON_404, SETTLEMENT_423_PAID_CANNOT_DELETE

#### API-042 · GET /api/schedules/{scheduleId}/payments · 입금 현황 조회
인증: 불필요. **Query**: status(UNPAID|PAID|ALL, 기본 UNPAID), keyword(선택), cursor(선택),
size(선택, 기본 20)

**Response data**: payments[].attendeeId/name/affiliation/phoneLast4/totalBilledAmount(여러
차수 합산)/paymentStatus, nextCursor

cursor 페이지네이션(정렬 기준: createdAt).

**에러**: COMMON_404, COMMON_400(잘못된 cursor)

---

#### API-043 · PUT /api/rounds/{roundId}/account · 입금 안내 계좌 설정
인증: 불필요. 은행 연동 없이 공지에 띄울 텍스트일 뿐(schedule_round에 저장).

**Request**: bankName, accountNumber, accountHolder
**Response data**: roundId, bankName, accountNumber, accountHolder
**에러**: COMMON_400, COMMON_404

---

#### API-044 · POST /api/schedules/{scheduleId}/settlement-notice · 정산 공지 생성
인증: 불필요. 선택된 차수 중 영수증 미등록/계좌 미설정이 있으면 409.

**Request**: roundIds(number[], 1개 이상)
**Response data**: scheduleTitle, rounds[].label/billedAmount, combinedAmount, account.bankName/
accountNumber/accountHolder, noticeText

**에러**: SETTLEMENT_400_ROUND_SELECTION_REQUIRED, COMMON_404, SETTLEMENT_409_UNREGISTERED_ROUND_INCLUDED, SETTLEMENT_409_ACCOUNT_NOT_SET

> **미확정**: 선택한 차수들의 계좌가 서로 다를 때 공지에 어느 계좌를 쓸지는 구현 시점에 정한다.

---

#### API-045 · GET /api/schedules/{scheduleId}/settlement · 정산 홈 조회
인증: 불필요. settlementStatus는 그 차수 전원이 입금완료면 "정산 완료", 아니면 "정산중".

**Response data**: scheduleTitle, status, progress.paidCount/totalCount/rate, receipts[].roundId/label/
storeName/totalAmount/registeredAt/settlementStatus, account.bankName/accountNumber/accountHolder

**에러**: COMMON_404

> **미확정**: `progress`의 단위가 "사람 수"인지 "차수 수"인지는 화면 예시만으로 확정하지 못했다.
> 선택된 차수들의 계좌가 서로 다를 때 `account`에 어느 계좌를 대표로 보여줄지도 함께 정해야
> 한다(API-044의 동일 미확정 항목 참고). 구현 시점에 결정한다.

### 운영 메모

#### API-046 · POST /api/schedules/{scheduleId}/notes · 운영 메모 작성
인증: 불필요. **Request**: type(GENERAL|TODO|DECISION|IDEA), title, assignee(선택), content(100자 이하)
**Response data**: noteId. **에러**: COMMON_400, COMMON_404

#### API-047 · GET /api/schedules/{scheduleId}/notes · 운영 메모 목록 조회
인증: 불필요. **Query**: type(ALL|GENERAL|TODO|DECISION|IDEA, 기본 ALL), keyword(선택), cursor(선택),
size(선택, 기본 20)

**Response data**: notes[].noteId/type/title/assignee/content/isTodoDone(type=TODO만 값)/
createdAt/updatedAt, nextCursor

cursor 페이지네이션(정렬 기준: createdAt).

**에러**: COMMON_404, COMMON_400(잘못된 cursor)

#### API-048 · PATCH /api/notes/{noteId} · 운영 메모 수정
인증: 불필요. Request는 API-046과 동일. **에러**: COMMON_400, COMMON_404

#### API-049 · DELETE /api/notes/{noteId} · 운영 메모 삭제
인증: 불필요. **에러**: COMMON_404

#### API-050 · PATCH /api/notes/{noteId}/todo · 할 일 완료 처리
인증: 불필요. **Request**: isTodoDone(boolean). **Response data**: noteId, isTodoDone
**에러**: COMMON_404, NOTE_400_NOT_TODO_TYPE(할일 유형이 아님)

### 회의

#### API-051 · POST /api/terms/{termId}/meetings · 회의 만들기
인증: 불필요. **Request**: title, meetingDate, categoryId(`meeting_category`, 일정 카테고리와
별도 운영 — decisions #21), attendees(자유 텍스트, 선택), listSummary(목록 한 줄 설명, 선택),
agendas(string[], 안건 내용 목록)

**Response data**: meetingId, agendaIds
**에러**: COMMON_400, MEETING_404_GROUP_TERM_NOT_FOUND(기수 없음), MEETING_404_CATEGORY_NOT_FOUND(카테고리 없음)

---

#### API-052 · GET /api/terms/{termId}/meetings · 회의 목록 조회
인증: 불필요.

**Query**: status(진행중|완료, 선택), categoryId(선택), keyword(선택), cursor(선택), size(선택, 기본 20)

**Response data**: meetings[].meetingId/title/meetingDate/categoryName/
listSummary/status(IN_PROGRESS|COMPLETED)/agendaCount/attendees/createdAt, nextCursor

cursor 페이지네이션(정렬 기준: meetingDate 내림차순 — 최근 회의가 먼저).

**에러**: COMMON_400(잘못된 cursor)

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

**Request**: title, meetingDate, categoryId, attendees, listSummary
**Response data**: meetingId
**에러**: COMMON_400, COMMON_404, MEETING_409_ALREADY_ENDED

---

#### API-055 · DELETE /api/meetings/{meetingId} · 회의 삭제
인증: 불필요. 회의 상태(IN_PROGRESS/COMPLETED)와 무관하게 삭제를 허용한다(decisions #15).
**에러**: COMMON_404

---

#### API-056 · POST /api/meetings/{meetingId}/agendas · 안건 추가
인증: 불필요. 기존 안건 목록 끝에 추가(orderNo=마지막+1), status=PENDING으로 생성.

**Request**: content(1~100자)
**Response data**: agendaId, orderNo
**에러**: COMMON_400, COMMON_404, MEETING_409_ALREADY_ENDED

---

#### API-057 · PATCH /api/agendas/{agendaId} · 안건 내용 저장
인증: 불필요. **Request**: discussionNote, decision, status(PENDING|IN_PROGRESS|COMPLETED)

**Logics**: 화면의 '다음 안건' 버튼은 이 API를 status='COMPLETED'로 호출하는 것이다. '회의
중단하기'는 같은 API를 status 없이(또는 바꾸지 않고) 호출해 내용만 저장하며 회의는 '진행중'을
유지한다.

**Response data**: agendaId, status, progress
**에러**: COMMON_404, MEETING_409_ALREADY_ENDED

#### API-058 · DELETE /api/agendas/{agendaId} · 안건 삭제
인증: 불필요. 삭제 후 남은 안건들의 진행률 재계산. 안건 1개만 남은 회의는 삭제 불가.
**에러**: COMMON_404, MEETING_409_ALREADY_ENDED, AGENDA_409_AT_LEAST_ONE_REQUIRED

---

#### API-059 · POST /api/meetings/{meetingId}/end · 회의 종료
인증: 불필요. **Response data**: meetingId, status, endedAt, doneAgendaCount, totalAgendaCount
**에러**: COMMON_404, MEETING_409_ALREADY_ENDED(이미 종료)

---

#### API-060 · POST /api/meetings/{meetingId}/summary · AI 요약 생성
인증: 불필요. 외부 AI로 안건의 논의 내용·결정 사항을 종합 요약하고 `summary_content`를 덮어쓴다.
원본(meeting_agenda)은 항상 보존. "다시 요약하기"도 이 API 재호출.

**Response data**: summarizedAgendaCount, summaryContent, summaryGeneratedAt
**에러**: COMMON_404, MEETING_502_SUMMARY_FAILED(502)

#### API-061 · PATCH /api/meetings/{meetingId}/summary · 회의록 저장(요약 수정)
인증: 불필요. **Request**: summaryContent. **Response data**: meetingId. **에러**: COMMON_404

### 문서함

#### API-062 · GET /api/files/policy · 파일 업로드 정책 조회
인증: 불필요. **Response data**: allowedExtensions[], maxSizeBytes

> **미확정**: 실제 허용 확장자·최대 용량 수치는 구현 시점에 정한다(현재 예시값: pdf/docx/xlsx/pptx/
> hwp/jpg/png, 20MB).

---

#### API-063 · POST /api/terms/{termId}/files · 파일 업로드
인증: 불필요. 한 번에 1개씩. 자료 이름은 파일명으로 자동 입력(수정 가능), 확장자는 서버가 고정.
scheduleId가 있으면 일정 자료로 연결되고 문서함에도 자동 노출.

**Request**: file(multipart/form-data), name, category(ACTIVITY_DOC|BUDGET_PLAN|PHOTO|LEDGER_FILE),
scheduleId(선택)

**Response data**: fileId, name
**에러**: FILE_400_REQUIRED_FIELD_MISSING(필수 누락), FILE_400_EXTENSION_NOT_ALLOWED, FILE_404_GROUP_TERM_NOT_FOUND(기수 없음), FILE_404_SCHEDULE_NOT_FOUND(일정 없음), FILE_413_SIZE_EXCEEDED(413), FILE_400_SCHEDULE_TERM_MISMATCH(scheduleId가 다른 기수 소속)

---

#### API-064 · GET /api/terms/{termId}/files · 파일 목록 조회
인증: 불필요. deleted_at IS NULL인 파일만.

**Query**: category(ALL|ACTIVITY_DOC|BUDGET_PLAN|PHOTO|LEDGER_FILE, 기본 ALL), keyword(선택),
sort(LATEST|OLDEST|NAME, 기본 LATEST), scheduleId(선택), cursor(선택), size(선택, 기본 20)

**Response data**: files[].fileId/name/extension/size/category/scheduleId/createdAt, nextCursor,
totalCount(cursor 없는 첫 페이지에서만 값이 있고, 이후 페이지는 null — API-029 progress.totalCount·
API-037 targetCount와 같은 패턴. 페이지네이션용 totalCount가 아니라 "총 N개" 표시용)

cursor 페이지네이션. sort별로 cursor 키가 다르다: LATEST/OLDEST는 createdAt, NAME은 name.
둘 다 id를 tiebreaker로 쓴다.

**에러**: FILE_404_GROUP_TERM_NOT_FOUND(기수 없음), FILE_400_INVALID_CURSOR(잘못된 cursor)

---

#### API-065 · GET /api/files/{fileId}/download-url · 파일 미리보기·다운로드 URL 발급
인증: 불필요. **Query**: mode(PREVIEW|DOWNLOAD, 기본 DOWNLOAD). **Response data**: url, expiresAt.
**에러**: FILE_404_NOT_FOUND

`url`은 외부 스토리지 주소가 아니라 이 서버 자신의 API-080을 가리킨다(`mode`를 그대로 실어서).
실제 파일 바이트가 나가는 동작은 API-080 쪽에서 일어난다.

---

#### API-080 · GET /api/files/{fileId}/content · 파일 콘텐츠 서빙
인증: 불필요. API-065가 발급하는 `url`이 실제로 가리키는 대상 — 프론트가 이 API를 JSON
클라이언트로 직접 호출하는 게 아니라, `<a href>`나 `<iframe src>`처럼 브라우저가 그 주소로
직접 요청을 보내는 방식으로 쓰인다(ZIP인 API-068과 같은 패턴).

**Query**: mode(PREVIEW|DOWNLOAD, 기본 DOWNLOAD)

**Response**: 파일 바이트 스트림(JSON 래퍼 없음). `Content-Type`은 파일 확장자로 추론,
`Content-Disposition`은 `mode=DOWNLOAD`면 `attachment`(브라우저가 저장 대화상자를 띄움),
`PREVIEW`면 `inline`(브라우저가 바로 열어서 보여줌)으로 서버가 강제한다.

**에러**: FILE_404_NOT_FOUND

#### API-066 · DELETE /api/files/{fileId} · 파일 삭제
인증: 불필요. 소프트 삭제(deleted_at). **에러**: FILE_404_NOT_FOUND

#### API-067 · POST /api/files/{fileId}/restore · 파일 삭제 되돌리기
인증: 불필요. **Response data**: fileId. **에러**: FILE_404_NOT_FOUND

> **미확정**: 되돌리기 가능 기간, 실제 스토리지 파일의 영구 삭제 시점은 구현 시점에 정한다.

---

#### API-068 · GET /api/terms/{termId}/files/zip · 파일 전체 다운로드(ZIP)
인증: 불필요. 현재 검색·분류 조건에 맞는 파일을 ZIP으로 스트리밍.

**Query**: category(ALL|ACTIVITY_DOC|BUDGET_PLAN|PHOTO|LEDGER_FILE, 기본 ALL), keyword(선택)
**Response**: `Content-Type: application/zip` 파일 스트림(JSON 래퍼 없음). 파일명 형식:
`{기수}자료{N}개.zip`.
**에러**: FILE_404_GROUP_TERM_NOT_FOUND(기수 없음), FILE_404_NO_DOWNLOADABLE_FILES(다운로드할 파일 없음)

### 예산

#### API-069 · GET /api/terms/{termId}/ledger · 예산 요약·내역 조회
인증: 불필요. currentBalance는 그룹의 모든 ledger_entry 합산(기수가 바뀌어도 이월), entries는 이
기수 내역만. source는 FK로 판단(schedule_round_id 있으면 RECEIPT, 둘 다 없으면 MANUAL).

**Query**: type(ALL|INCOME|EXPENSE, 기본 ALL), cursor(선택), size(선택, 기본 20)

**Response data**: ledgerFile.fileId/name, currentBalance, totalIncome, totalExpense,
entries[].entryId/type(INCOME|EXPENSE)/itemName/amount/entryDate/source(RECEIPT|MANUAL), nextCursor

cursor 페이지네이션(정렬 기준: entryDate 내림차순).

**에러**: COMMON_404, COMMON_400(잘못된 cursor)

> **미확정**: 내역의 분류(예: "· 공연" 같은 태그) 저장할 컬럼을 `ledger_entry`에 추가할지는 구현
> 시점에 정한다. 장부 초기 잔액(기초 총액) 자동 인식은 `docs/decisions.md` #10에 따라 보류.
> `totalIncome`/`totalExpense`("누적 수입·지출")가 `currentBalance`처럼 그룹 전체 기간 누적인지,
> 아니면 이 기수(`entries`)로 한정인지도 아직 확정하지 못했다 — 화면 예시만으로 판단 불가.

---

#### API-070 · POST /api/terms/{termId}/ledger · 장부 직접 입력
인증: 불필요. **Request**: type(INCOME|EXPENSE), itemName, amount, entryDate
**Response data**: entryId. **에러**: COMMON_400

#### API-071 · GET /api/ledger/{entryId} · 장부 내역 상세 조회
인증: 불필요. **Response data**: entryId, type, itemName, amount, entryDate, source, origin(자동기록
출처 상세, 직접 입력이면 null). **에러**: COMMON_404

#### API-072 · PATCH /api/ledger/{entryId} · 장부 내역 수정
인증: 불필요. 직접 입력 내역만 수정 가능. 자동 기록은 고아 여부와 무관하게 항상 수정 불가 — 삭제와
달리 예외 없음(decisions #5).

**Request**: type, itemName, amount, entryDate
**에러**: COMMON_400, COMMON_404, LEDGER_409_AUTO_ENTRY_LOCKED

---

#### API-073 · DELETE /api/ledger/{entryId} · 장부 내역 삭제
인증: 불필요. `schedule_round_id`가 아직 연결된 자동 기록은 LEDGER_409_AUTO_ENTRY_LOCKED로 삭제 불가(다음 "정산 내역
새로고침" 때 재계산과 충돌). 원본 차수가 삭제되어 고아가 된 자동 기록은 일반 항목처럼 삭제 가능.
직접 입력 내역은 항상 삭제 가능(decisions #5).

**에러**: COMMON_404, LEDGER_409_AUTO_ENTRY_LOCKED(원본이 살아있는 경우)

---

#### API-074 · POST /api/terms/{termId}/ledger/sync · 정산 내역 새로고침
인증: 불필요. 영수증 등록된 차수마다 지출 행 1개(schedule_round_id). 차수 전원 납부 완료 시에만:
실제 총 납부액 수입 행 1개 + 100원 버림 차액 "추가 동아리 지원금" 지출 행 1개를 자동 생성(둘 다
schedule_round_id 연결, decisions #2). 부분 납부 중에는 생성 안 함, 차액 0원이면 지원금 행 생성 안
함. 입금을 되돌리면 두 행 모두 취소, 재완료 시 중복 없이 재반영.

**Response data**: createdCount, updatedCount, deletedCount
**에러**: COMMON_404

---

#### API-075 · GET /api/terms/{termId}/ledger/export · 장부 엑셀 다운로드
인증: 불필요. 원본 업로드 엑셀과 별개로 서비스 장부(입출금 내역·누적 수입·누적 지출·잔액)를 엑셀로
다운로드.

**Response data**: downloadUrl, fileName, generatedAt
**에러**: COMMON_404

### AI 비서

#### API-076 · POST /api/terms/{termId}/ai/chat-rooms · AI 대화방 생성
인증: 불필요. **Request**: title. **Response data**: chatRoomId. 대화방은 기수당 여러 개 유지하며
단일화하지 않는다(결정 완료, 현행 구조 그대로 — `docs/decisions.md` #11).

#### API-077 · GET /api/terms/{termId}/ai/chat-rooms · AI 대화방 목록 조회
인증: 불필요. 기수 단위(로그인이 없어 그 기수 운영진 전체가 공유).

**Query**: cursor(선택), size(선택, 기본 20)

**Response data**: chatRooms[].chatRoomId/title/updatedAt, nextCursor

cursor 페이지네이션(정렬 기준: updatedAt 내림차순 — 최근 대화한 방이 먼저).

**에러**: COMMON_400(잘못된 cursor)

---

#### API-078 · POST /api/ai/chat-rooms/{chatRoomId}/messages · AI 메시지 전송
인증: 불필요. **Request**: content. **Response data**: messageId, role(USER|ASSISTANT), content,
targetScheduleId, cardType, card(결과 카드 — 예: 미입금자 조회)

**에러**: COMMON_400(내용 없음), COMMON_404

> **미확정**: 미입금자 조회 외 다른 카드 유형(공지 작성·일정 조회 등)의 `card` 포맷은 구현 시점에
> 화면이 나오는 대로 정한다.

---

#### API-079 · GET /api/ai/chat-rooms/{chatRoomId}/messages · AI 메시지 목록 조회
인증: 불필요. **Query**: cursor(선택), size(선택, 기본 20)

**Response data**: messages[].messageId/role/content/cardType/card/createdAt, nextCursor

cursor 페이지네이션(정렬 기준: createdAt 오름차순 — 대화 순서대로).

**에러**: COMMON_404, COMMON_400(잘못된 cursor)

`card`는 전송 시점의 스냅샷(`content_json`)이라 이후 상태(예: 입금 여부)가 바뀌어도 갱신되지
않는다. FE가 최신값이 필요하면 해당 조회 API(예: 입금 현황 조회)를 다시 호출하는 것을 권장한다.

---

## 7. 미확정(TBD) 항목 모음

구현 착수 전 들어가면 안 되는, 아직 정하지 않은 항목 14개(§6에서 각 엔드포인트 자리에도 표시):

1. API-004/007 `color` — 일정 색상 8종의 실제 값(영문 상수명) 미정, Figma엔 스와치만 있고 이름이 없음
2. API-009 `operationMenuCount` 산정 기준 — 해소됨: 4개 고정(decisions #12)
3. API-022 동일인 중복 등록 처리 기준
4. API-028 새 차수 생성 시 기존 참가자 자동 포함 여부
5. API-044 선택 차수들의 계좌가 다를 때 처리 방식(API-045 정산 홈의 대표 계좌 표시도 같은 문제)
6. API-045 `progress`가 사람 수 단위인지 차수 수 단위인지
7. API-050 (해당 없음 — 이미 해소, 목록에서 제외) — *초안에서 제거*
8. API-053 안건 진행률 — 전체 진행률(doneCount/totalCount)은 해소됨('다음 안건' 버튼이 API-057을
   status=COMPLETED로 호출). 다만 Figma의 개별 안건 진행 중 퍼센트(예: "논의중 45%")는 계산 기준이
   없어 여전히 미확정 — Figma 쪽 "진행률 바 수정 예정" 메모부터 확인 필요
9. API-055 진행 중인 회의 삭제 허용 여부 — 해소됨: 상태와 무관하게 삭제 허용(decisions #15)
10. API-062 허용 확장자·최대 용량 실제 수치 — 해소됨: 20MB, 문서/이미지/zip 화이트리스트(decisions #16)
11. API-067 파일 삭제 되돌리기 가능 기간 — 해소됨: 기간 제한 없음(decisions #16)
12. API-069 내역 분류 컬럼 추가 여부(ERD 변경 필요), 장부 초기 잔액 AI 인식(보류, decisions #10)
13. API-078 미입금자 조회 외 카드 포맷
14. API-014 자동 매칭 알고리즘 상세 기준
15. API-007 일정 수정 시 운영 공간 토글(`hasOperationSpace`) 변경 허용 여부 — 보류(decisions #12)
16. API-069 `totalIncome`/`totalExpense`(누적 수입·지출)가 기수 기준인지 그룹 전체 기간 기준인지
17. AI 연동 벤더/모델 — 영수증 인식(API-036), 회의 요약(API-060), AI 채팅 응답(API-078) 전부
    미지정. 현재 Gemini 무료 티어가 유력 후보로 검토 중이나 확정은 아니다.

이 항목들은 해당 엔드포인트를 실제로 구현하는 시점에 정하고, 결정 즉시 `docs/decisions.md`에 追記한다.
