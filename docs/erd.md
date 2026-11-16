# ERD — 모임장

데이터 모델 정본. [ERDCloud](https://www.erdcloud.com/d/NzCidykN2L7iLABFh)와 동기화된 상태이며, 스키마를
바꿀 때는 이 파일과 ERDCloud 양쪽을 함께 갱신한다. enum 계열 컬럼의 `도메인`은
아래 "enum 도메인" 표의 값과 1:1로 매칭된다. DB는 MySQL 8(`docs/decisions.md` 참고).

## 엔티티 목록 (19개)

| 테이블(물리명) | 한글명 | 설명 |
|---|---|---|
| club_group | 그룹 | 동아리/학회 그룹 자체 |
| group_term | 기수 | 그룹의 기수(학기). 실질적인 멀티테넌시 경계 |
| schedule_category | 일정 카테고리 | 기수별 일정 분류(일정 전용, `docs/decisions.md` #21로 회의와 분리) |
| schedule | 일정 | 운영 일정. 운영공간은 별도 테이블 없이 `has_operation_space` 플래그로 표현 |
| member_list | 명단방 | 부원 명단을 모으는 등록 링크 단위 |
| member | 부원 | 명단방에 등록된 개인 |
| schedule_attendee | 일정 참가자 | 특정 일정에 참가한 사람(부원링크 등록 또는 현장추가) |
| schedule_round | 차수 | 일정 내 1차/2차 등 차수. 영수증·입금계좌 정보도 이 테이블에 내장 |
| round_attendee | 차수 참석자 | 차수별 출석 여부 |
| settlement_target | 정산 대상 | 차수별 개인 청구액·입금 상태 |
| operation_note | 운영 메모 | 일정에 속한 메모/할일/결정사항/아이디어 |
| meeting_category | 회의 카테고리 | 기수별 회의 분류(일정 카테고리와 별도 운영) |
| meeting | 회의 | 회의(회의록) 본체. `schedule_id`로 특정 일정에 속할 수 있다 |
| meeting_agenda | 안건 | 회의에 속한 안건 |
| schedule_agenda_link | 일정-안건 연결 | 일정과 안건의 N:M 연결(일정에 속하지 않은 회의록의 안건만 개별 연결) |
| file | 자료 | 기수별 자료실 파일(일정 첨부 겸용) |
| ai_chat_room | AI 채팅방 | 기수 단위 AI 비서 대화방(여러 개 허용) |
| ai_message | AI 메시지 | AI 채팅방 내 메시지(결과 카드 포함) |
| ledger_entry | 장부 항목 | 기수별 수입/지출 장부(자동기록 + 직접입력) |

## 테이블 상세

### club_group (그룹)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| name | VARCHAR(100) | | | ✅ | String | |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |

### group_term (기수)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| group_id | BIGINT | | → club_group.id | ✅ | long | |
| name | VARCHAR(100) | | | ✅ | String | 자유 텍스트, 예: 26기 |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |

### schedule_category (일정 카테고리)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| group_term_id | BIGINT | | → group_term.id | ✅ | long | 기수 소속, 기수가 바뀌면 카테고리 목록도 새로 관리 |
| name | VARCHAR(50) | | | ✅ | String | |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |

### schedule (일정)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| group_term_id | BIGINT | | → group_term.id | ✅ | long | |
| schedule_category_id | BIGINT | | → schedule_category.id | ❌ | long | 카테고리 기본값 없음, 미생성 시 NULL 가능 |
| title | VARCHAR(100) | | | ✅ | String | |
| color | VARCHAR(20) | | | ✅ | String | 8종 중 자유 선택, 카테고리와 무관. 실제 8개 값 미확정(Figma는 스와치만 제공) |
| is_all_day | BOOLEAN | | | ✅ | boolean | default FALSE |
| start_at | DATETIME | | | ✅ | LocalDateTime | |
| end_at | DATETIME | | | ✅ | LocalDateTime | |
| location | VARCHAR(255) | | | ❌ | String | |
| has_operation_space | BOOLEAN | | | ✅ | boolean | default FALSE. 운영공간 별도 테이블 없음, 하위(메모/차수/자료)는 schedule_id 직접 참조 |
| memo | TEXT | | | ❌ | String | **defaultValue: "진행중"** — 다른 테이블 상태값 기본값이 잘못 복사된 것으로 보이는 알려진 결함. 실제 기능에는 영향 없으나 마이그레이션 작성 시 defaultValue를 비우는 걸 권장 |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |

### member_list (명단방)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| group_term_id | BIGINT | | → group_term.id | ✅ | long | |
| name | VARCHAR(100) | | | ✅ | String | |
| register_deadline | DATETIME | | | ❌ | LocalDateTime | |
| collect_school | BOOLEAN | | | ✅ | boolean | default TRUE |
| collect_department | BOOLEAN | | | ✅ | boolean | default TRUE |
| collect_phone | BOOLEAN | | | ✅ | boolean | default TRUE |
| collect_student_id | BOOLEAN | | | ✅ | boolean | default TRUE |
| collect_part | BOOLEAN | | | ✅ | boolean | default TRUE |
| collect_grade | BOOLEAN | | | ✅ | boolean | default TRUE |
| status | VARCHAR(20) | | | ✅ | MemberListStatus | default 'RECRUITING'. RECRUITING(등록중)/COMPLETED(등록완료) |
| join_token | VARCHAR(64) | | | ✅ | — | UNIQUE |
| is_link_closed | BOOLEAN | | | ✅ | boolean | default FALSE |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |

### member (부원)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| member_list_id | BIGINT | | → member_list.id | ✅ | long | |
| name | VARCHAR(50) | | | ✅ | String | 토글과 무관하게 항상 필수 |
| school | VARCHAR(100) | | | ❌ | String | member_list.collect_school이 ON일 때만 수집·필수(API 동적 검증) |
| department | VARCHAR(100) | | | ✅ | String | name과 함께 토글과 무관하게 항상 필수 |
| phone | VARCHAR(20) | | | ❌ | String | collect_phone ON일 때만 수집·필수. 이름+전화번호로 동일인 판단 |
| student_id | VARCHAR(30) | | | ❌ | String | collect_student_id ON일 때만 수집·필수. 부원 수정 화면에는 없는 항목 |
| part | VARCHAR(50) | | | ❌ | String | collect_part ON일 때만 수집·필수. 부원 수정 화면에는 없는 항목 |
| grade | VARCHAR(20) | | | ❌ | String | collect_grade ON일 때만 수집·필수. 부원 수정 화면에는 없는 항목 |
| privacy_consent | BOOLEAN | | | ✅ | boolean | default FALSE |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |

> school/phone/student_id/part/grade는 명단방마다 필수 여부가 달라 DB `NOT NULL` 제약으로 표현할 수
> 없다. 서비스 레이어에서 그 명단방의 `collect_*` 값을 조회해 동적으로 검증한다(`docs/decisions.md` #7).

### schedule_attendee (일정 참가자)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| schedule_id | BIGINT | | → schedule.id | ✅ | long | |
| member_id | BIGINT | | → member.id | ❌ | long | 부원링크로 등록된 경우만 FK, 현장추가는 NULL |
| name | VARCHAR(50) | | | ✅ | String | 스냅샷(명단 원본 변경과 무관) |
| affiliation | VARCHAR(100) | | | ❌ | String | 학과/소속 스냅샷 |
| phone_last4 | VARCHAR(4) | | | ❌ | String | |
| source | VARCHAR(20) | | | ✅ | AttendeeSource | LINK(부원링크) / ON_SITE(현장추가) |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |

### schedule_round (차수)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| schedule_id | BIGINT | | → schedule.id | ✅ | long | |
| round_no | INT | | | ✅ | int | |
| label | VARCHAR(20) | | | ❌ | String | 예: 1차, 2차 |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |
| store_name | VARCHAR(100) | | | ❌ | String | 가게명(영수증) |
| transacted_at | DATETIME | | | ❌ | LocalDateTime | 거래일시 |
| total_amount | INT | | | ❌ | int | 금액(합계) |
| club_subsidy_amount | INT | | | ❌ | int | default 0. 회비 지원금 |
| image_url | VARCHAR(500) | | | ❌ | String | 영수증 이미지 |
| bank_name | VARCHAR(50) | | | ❌ | String | 입금계좌(차수에 속함). 계산한 사람이 직접 입력 |
| account_number | VARCHAR(50) | | | ❌ | String | |
| account_holder | VARCHAR(50) | | | ❌ | String | |

### round_attendee (차수 참석자)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | UNIQUE(schedule_round_id, schedule_attendee_id) |
| schedule_round_id | BIGINT | | → schedule_round.id | ✅ | long | |
| schedule_attendee_id | BIGINT | | → schedule_attendee.id | ✅ | long | |
| is_checked_in | BOOLEAN | | | ✅ | boolean | default FALSE |
| checked_in_at | DATETIME | | | ❌ | LocalDateTime | |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |

### settlement_target (정산 대상)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | UNIQUE(schedule_round_id, schedule_attendee_id) |
| schedule_round_id | BIGINT | | → schedule_round.id | ✅ | long | |
| schedule_attendee_id | BIGINT | | → schedule_attendee.id | ✅ | long | |
| billed_amount | INT | | | ✅ | int | ⌊(영수증 금액 − 지원금) ÷ 인원⌋을 100원 단위로 버림(내림) |
| payment_status | VARCHAR(20) | | | ✅ | PaymentStatus | default 'UNPAID'. UNPAID(미입금)/PAID(입금완료), 입금완료 후 잠금 |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |
| paid_at | DATETIME | | | ❌ | LocalDateTime | 입금 완료일시 |

> ⚠️ 과거 설계에는 `ledger_entry.settlement_target_id`(개인 단위 수입 자동기록용 FK)가 있었으나,
> 수입 자동기록을 차수 단위로 바꾸면서 삭제했다(`docs/decisions.md` #2, #6). `settlement_target`은
> 더 이상 `ledger_entry`에서 직접 참조되지 않는다.

### operation_note (운영 메모)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| schedule_id | BIGINT | | → schedule.id | ✅ | long | |
| type | VARCHAR(20) | | | ✅ | NoteType | GENERAL(일반메모)/TODO(할일)/DECISION(결정사항)/IDEA(아이디어) |
| title | VARCHAR(100) | | | ✅ | String | |
| assignee | VARCHAR(50) | | | ❌ | String | 자유 텍스트 |
| content | VARCHAR(100) | | | ✅ | String | 100자 이하 |
| is_todo_done | BOOLEAN | | | ❌ | boolean | type=TODO인 경우만 사용 |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |

### meeting_category (회의 카테고리)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| group_term_id | BIGINT | | → group_term.id | ✅ | long | |
| name | VARCHAR(50) | | | ✅ | String | |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |

### meeting (회의)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| group_term_id | BIGINT | | → group_term.id | ✅ | long | |
| schedule_id | BIGINT | | → schedule.id | ❌ | Long | NULL=일정과 무관한 회의록(운영 공간에서 생성), NOT NULL=그 일정에 속한 회의록(일정 상세에서 생성, 일정 삭제 시 함께 삭제). 소속은 생성 시점에 확정하고 이후 바꾸지 않는다(`docs/decisions.md` #44) |
| meeting_category_id | BIGINT | | → meeting_category.id | ✅ | long | 일정 카테고리와 분리 운영(`docs/decisions.md` #21) |
| title | VARCHAR(100) | | | ✅ | String | |
| meeting_date | DATE | | | ✅ | LocalDate | |
| attendees | VARCHAR(500) | | | ❌ | String | 미확정: 기본 텍스트 컬럼 |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |
| summary_content | TEXT | | | ❌ | String | AI 요약본(원본 discussion_note/decision과 별도, 직접 수정·재요약 가능) |
| summary_generated_at | DATETIME | | | ❌ | LocalDateTime | |
| status | VARCHAR(20) | | | ✅ | MeetingStatus | default 'IN_PROGRESS'. IN_PROGRESS(진행중)/COMPLETED(완료) |
| ended_at | DATETIME | | | ❌ | LocalDateTime | |

### meeting_agenda (안건)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| meeting_id | BIGINT | | → meeting.id | ✅ | long | |
| content | VARCHAR(100) | | | ✅ | String | 안건, 100자 이하 |
| discussion_note | TEXT | | | ❌ | String | 논의 내용 |
| decision | TEXT | | | ❌ | String | 미확정: 기본 텍스트 컬럼. 여러 개면 테이블 분리 필요 |
| status | VARCHAR(20) | | | ✅ | AgendaStatus | default 'PENDING'. PENDING(대기)/IN_PROGRESS(진행중)/COMPLETED(완료), 진행률 계산용 |
| order_no | INT | | | ✅ | int | default 0 |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |

### schedule_agenda_link (일정-안건 연결)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| schedule_id | BIGINT | | → schedule.id | ✅ | long | |
| meeting_agenda_id | BIGINT | | → meeting_agenda.id | ✅ | long | |
| created_at | DATETIME | | | ✅ | LocalDateTime | |

> `updated_at`·소프트 삭제 컬럼이 없다 — 연결 해제는 row 물리 삭제로 처리한다.

### file (자료)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| group_term_id | BIGINT | | → group_term.id | ✅ | long | 기수별 자료실 |
| schedule_id | BIGINT | | → schedule.id | ❌ | long | NULL=자료실 직접등록(일정무관), NOT NULL=일정 첨부(일정 삭제 시 함께 삭제) |
| name | VARCHAR(255) | | | ✅ | String | 자료명 |
| extension | VARCHAR(20) | | | ✅ | String | 확장자 |
| size | BIGINT | | | ✅ | long | 크기 |
| category | VARCHAR(20) | | | ✅ | FileCategory | ACTIVITY_DOC(활동문서)/BUDGET_PLAN(예산안)/PHOTO(활동사진)/LEDGER_FILE(장부파일, xlsx 첨부통합) |
| storage_key | VARCHAR(500) | | | ✅ | String | S3/R2 객체 키. 다운로드·미리보기는 이 키로 서버가 직접 파일을 서빙한다(presigned URL 아님 — decisions #18) |
| thumbnail_key | VARCHAR(500) | | | ❌ | String | 활동사진 그리드용 썸네일 객체 키(`{원본키}_thumb.{jpg\|png}`). 이미지가 아니거나(webp·문서) 생성에 실패하면 NULL — decisions #82 |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |
| deleted_at | DATETIME | | | ❌ | LocalDateTime | 소프트 삭제(되돌리기 지원) |

### ai_chat_room (AI 채팅방)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| title | VARCHAR(100) | | | ❌ | String | |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |
| group_term_id | BIGINT | | → group_term.id | ✅ | long | |

> 기수당 여러 개 허용(단일화하지 않기로 결정, `docs/decisions.md` 참고 — AI 비서 대화방 다중화 유지).

### ai_message (AI 메시지)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| ai_chat_room_id | BIGINT | | → ai_chat_room.id | ✅ | long | |
| navigation_type | VARCHAR(20) | | | ❌ | NavigationType | 답변 아래 이동 버튼이 가리킬 화면 |
| navigation_target_id | BIGINT | | | ❌ | long | 이동 대상 ID. type에 따라 일정 ID 또는 회의 ID, 기수 단위 화면이면 null. 다형 참조라 FK 없음(decisions #40) |
| role | VARCHAR(20) | | | ✅ | MessageRole | USER(사용자 질문)/ASSISTANT(AI 응답) |
| content | TEXT | | | ✅ | String | |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| card_type | VARCHAR(50) | | | ❌ | String | 예: 미입금자 조회 |
| content_json | JSON | | | ❌ | String | 카드 내용 스냅샷 |

### ledger_entry (장부 항목)
| 컬럼 | 타입 | PK | FK | NOT NULL | 도메인 | 설명 |
|---|---|---|---|---|---|---|
| id | BIGINT | ✅ | | ✅ | long | |
| group_term_id | BIGINT | | → group_term.id | ✅ | long | |
| schedule_round_id | BIGINT | | → schedule_round.id | ❌ | long | 차수 단위 자동기록 출처 — 영수증 지출 1건 / 차수 전원 납부 완료 시 수입 1건 / 반올림 차액 "추가 동아리 지원금" 지출 1건 |
| type | VARCHAR(10) | | | ✅ | LedgerType | INCOME(수입)/EXPENSE(지출) |
| item_name | VARCHAR(100) | | | ✅ | String | |
| amount | INT | | | ✅ | int | |
| entry_date | DATE | | | ✅ | LocalDate | |
| created_at | DATETIME | | | ✅ | LocalDateTime | |
| updated_at | DATETIME | | | ✅ | LocalDateTime | |

> 기초 잔액(장부 파일 업로드 시 초기 총액) 저장 컬럼은 아직 없다 — AI 인식 기능과 함께 보류 중
> (`docs/decisions.md` #10).

## 관계(FK) 전체 (26개)

| 자식.컬럼 | → 부모.컬럼 | NULL 허용 | 의미 |
|---|---|---|---|
| group_term.group_id | → club_group.id | ❌ | 그룹 1 : N 기수 |
| schedule_category.group_term_id | → group_term.id | ❌ | 기수 1 : N 카테고리 |
| schedule.group_term_id | → group_term.id | ❌ | 기수 1 : N 일정 |
| schedule.schedule_category_id | → schedule_category.id | ✅ | 카테고리 1 : N 일정 |
| member_list.group_term_id | → group_term.id | ❌ | 기수 1 : N 명단방 |
| member.member_list_id | → member_list.id | ❌ | 명단방 1 : N 부원 |
| schedule_attendee.schedule_id | → schedule.id | ❌ | 일정 1 : N 참가자 |
| schedule_attendee.member_id | → member.id | ✅ | 부원 1 : N 참가자(현장추가는 NULL) |
| schedule_round.schedule_id | → schedule.id | ❌ | 일정 1 : N 차수 |
| round_attendee.schedule_round_id | → schedule_round.id | ❌ | 차수 1 : N 출석기록 |
| round_attendee.schedule_attendee_id | → schedule_attendee.id | ❌ | 참가자 1 : N 출석기록 |
| settlement_target.schedule_round_id | → schedule_round.id | ❌ | 차수 1 : N 정산대상 |
| settlement_target.schedule_attendee_id | → schedule_attendee.id | ❌ | 참가자 1 : N 정산대상 |
| operation_note.schedule_id | → schedule.id | ❌ | 일정 1 : N 메모 |
| meeting_category.group_term_id | → group_term.id | ❌ | 기수 1 : N 회의카테고리 |
| meeting.group_term_id | → group_term.id | ❌ | 기수 1 : N 회의 |
| meeting.schedule_id | → schedule.id | ✅ | 일정 1 : N 회의(NULL=일정과 무관한 회의록) |
| meeting.meeting_category_id | → meeting_category.id | ❌ | 카테고리 1 : N 회의 |
| meeting_agenda.meeting_id | → meeting.id | ❌ | 회의 1 : N 안건 |
| schedule_agenda_link.schedule_id | → schedule.id | ❌ | 일정 1 : N 연결 |
| schedule_agenda_link.meeting_agenda_id | → meeting_agenda.id | ❌ | 안건 1 : N 연결(일정↔안건 N:M을 연결 테이블로 구현) |
| file.group_term_id | → group_term.id | ❌ | 기수 1 : N 자료 |
| file.schedule_id | → schedule.id | ✅ | 일정 1 : N 자료(NULL=자료실 직접등록) |
| ai_chat_room.group_term_id | → group_term.id | ❌ | 기수 1 : N 채팅방 |
| ai_message.ai_chat_room_id | → ai_chat_room.id | ❌ | 채팅방 1 : N 메시지 |
| ledger_entry.group_term_id | → group_term.id | ❌ | 기수 1 : N 장부항목 |
| ledger_entry.schedule_round_id | → schedule_round.id | ✅ | 차수 1 : N 장부항목(자동기록 출처) |

모든 관계는 `ZERO_OR_ONE_OR_MANY`(1:N). N:M은 `schedule_agenda_link` 연결 테이블로만 명시적으로 모델링.
사용자/운영진/권한/로그인 테이블은 없음(데모 범위 제외, `docs/decisions.md` #1).

## enum 도메인 (Java 타입)

SQL 타입은 전부 VARCHAR을 유지한다(`@Enumerated(EnumType.STRING)` 호환, 네이티브 ENUM 미사용 —
`docs/decisions.md` #8).

| 도메인명(Java enum) | SQL 타입 | 쓰이는 컬럼 | 값 |
|---|---|---|---|
| MemberListStatus | VARCHAR(20) | member_list.status | RECRUITING(등록중), COMPLETED(등록완료) |
| PaymentStatus | VARCHAR(20) | settlement_target.payment_status | UNPAID(미입금), PAID(입금완료) |
| NoteType | VARCHAR(20) | operation_note.type | GENERAL(일반메모), TODO(할일), DECISION(결정사항), IDEA(아이디어) |
| MeetingStatus | VARCHAR(20) | meeting.status | IN_PROGRESS(진행중), COMPLETED(완료) |
| AgendaStatus | VARCHAR(20) | meeting_agenda.status | PENDING(대기), IN_PROGRESS(진행중), COMPLETED(완료) |
| FileCategory | VARCHAR(20) | file.category | ACTIVITY_DOC(활동문서), BUDGET_PLAN(예산안), PHOTO(활동사진), LEDGER_FILE(장부파일) |
| LedgerType | VARCHAR(10) | ledger_entry.type | INCOME(수입), EXPENSE(지출) |
| AttendeeSource | VARCHAR(20) | schedule_attendee.source | LINK(부원링크), ON_SITE(현장추가) |
| MessageRole | VARCHAR(20) | ai_message.role | USER(사용자 질문), ASSISTANT(AI 응답) |
| NavigationType | VARCHAR(20) | ai_message.navigation_type | SETTLEMENT(정산·입금), ATTENDANCE(출석), NOTE(메모장), SCHEDULE(일정 운영 공간), MEETING(회의록), FILE(자료실), BUDGET(장부) |

`schedule.color`는 8종 자유 선택이지만 실제 값 목록이 디자인에서 아직 확정되지 않아 enum 도메인으로
묶지 않았다(일반 String 유지).

## 알려진 미확정 사항
- `schedule.memo`의 defaultValue "진행중" — 데이터 오류로 보임, 마이그레이션에서 비우는 것 권장
- `meeting.attendees`, `meeting_agenda.decision` — 자유 텍스트 컬럼으로 임시 처리, 구조화 필요성 재검토 가능
- `ledger_entry`의 기초 잔액(기수 시작 시점 총액) 저장 위치 — 보류(`docs/decisions.md` #10)
