# 의사결정 로그

ERD(ERDCloud)·API 명세서(Notion) 작업 중 사용자와 합의한 결정을 시간순으로 남긴다.
각 항목은 "무엇을 왜 그렇게 정했는지"만 적고, 상세 스펙은 `docs/erd.md`·`docs/api-spec.md`에 있다.

## 1. 운영진 권한·로그인·회원가입은 범위 제외
데모 프로젝트라 인증 시스템을 만들지 않는다. 모임장/운영진 권한 분기, 로그인, 회원가입, 세션 관리
모두 이번 범위 밖. ERD에 사용자/인증 테이블이 없는 것도 이 결정 때문이다.

## 2. 정산 수입 자동기록은 "차수 단위 1건"
`ledger_entry`의 수입 자동기록은 개인(`settlement_target`) 단위가 아니라 **차수(`schedule_round`) 단위로
1건만** 생성한다. 그 차수 전원이 입금 완료됐을 때만: 실제 총 납부액 수입 1건 + 100원 버림 반올림 차액
"추가 동아리 지원금" 지출 1건을 자동 생성한다. 부분 납부 중에는 생성하지 않는다.

**이 결정 때문에** `ledger_entry.settlement_target_id` 컬럼은 더 이상 쓸 곳이 없어져서 삭제했다
(결정 6).

## 3. 참가자 청구액 반올림은 "100원 단위 버림"
`settlement_target.billed_amount` = ⌊(영수증 금액 − 지원금) ÷ 인원수⌋를 100원 단위로 버림(내림).

## 4. 영수증 삭제 = 차수 자체 cascade 삭제, 장부는 보존
"영수증 삭제" API는 영수증 필드만 지우는 게 아니라 **그 차수(`schedule_round`) 행 자체와 연결된
`settlement_target`(청구 내역)·`round_attendee`(출석 기록)를 함께 삭제**한다. 단, 이미 입금 완료 내역이
있으면 423으로 거부한다(이 규칙상 실제로 삭제가 성공하는 시점은 그 차수에 입금 완료가 0명일 때뿐이다).

**장부(`ledger_entry`)는 이 cascade에서 제외한다** — 이미 생성된 내역(영수증 등록 시 생기는 지출 1건)과
잔액은 그대로 유지하고, `schedule_round_id` 참조만 NULL로 전환한다(고아 상태). "장부 원본은 보관한다"는
PRD 원칙을 삭제 cascade보다 우선한다.

## 5. 장부 내역 삭제/수정 규칙
- **삭제**: 원본(`schedule_round`)이 아직 살아있는 자동기록은 항상 불가(`LEDGER_001`) — 다음
  "정산 내역 새로고침" 때 재계산과 충돌하기 때문. 원본이 삭제되어 고아가 된 자동기록은 일반 항목처럼
  수동 삭제 허용. 직접 입력 내역은 항상 삭제 가능.
- **수정**: 고아 여부와 무관하게 자동기록이면 항상 불가(예외 없음). 삭제보다 보수적인 규칙이다.

## 6. `ledger_entry.settlement_target_id` 컬럼 삭제
결정 2(수입 단위를 차수로 변경) 때문에 이 컬럼(개인 단위 수입 자동기록용 FK)의 용도가 사라져
ERD에서 컬럼·관계선을 완전히 삭제했다. 수입·지출 자동기록은 전부 `schedule_round_id` 하나로 연결된다.

## 7. 부원 수집 필드: 이름·학과 고정 필수, 나머지는 명단방 토글로 동적 필수
`member.name`·`department`는 토글과 무관하게 항상 필수(DB `notNull=true`). `school`·`phone`·
`student_id`·`part`·`grade`는 그 명단방(`member_list`)의 `collect_*` 토글이 **ON일 때만 수집·필수**이고
OFF면 입력 자체를 받지 않는다. 명단방마다 필수 여부가 달라서 DB 제약으로는 표현할 수 없으므로,
이 5개 컬럼은 `notNull=false`로 두고 **API(서비스 레이어)가 그 명단방의 설정을 조회해 동적으로 검증**한다.

## 8. enum 값 영문화 + 전용 Java 도메인, SQL 타입은 VARCHAR 유지
ERD의 상태/유형/분류 계열 컬럼(9개)을 한글 문자열 대신 영문 상수로 통일하고, 각각 전용 Java enum
도메인(`PaymentStatus`, `MeetingStatus`, `AgendaStatus`, `NoteType`, `MemberListStatus`,
`FileCategory`, `LedgerType`, `AttendeeSource`, `MessageRole`)으로 등록했다.
**SQL 타입은 네이티브 `ENUM(...)`이 아니라 VARCHAR를 그대로 쓴다** — `@Enumerated(EnumType.STRING)`이
기본적으로 VARCHAR에 매핑되고, 네이티브 ENUM은 값 추가 시마다 `ALTER TABLE` 마이그레이션이 필요해
Flyway 기반 프로젝트와 안 맞기 때문이다.

API 응답도 `{success, code, message, data}` 래퍼로 통일하고, 이 enum 값들을 영문 그대로 내려준다.

## 9. 파일 분류 4종 확정 (ACTIVITY_DOC / BUDGET_PLAN / PHOTO / LEDGER_FILE)
문서함 분류는 활동문서·예산안·활동사진 3종에 **장부(LEDGER_FILE)를 4번째로 추가**했다. 장부 엑셀은
이 분류로 업로드하며, "장부 초기 잔액 AI 인식"(결정 10) 기능이 이 분류의 파일만 대상으로 삼는다.

## 10. AI 기반 장부 초기 잔액 인식 — 설계는 논의, 구현은 보류
장부 엑셀을 업로드해도 "기초 잔액"을 저장할 곳이 없어 `currentBalance`가 0부터 시작하는 공백이 있다.
해결 방향(영수증 AI 인식과 동일한 2단계 패턴: AI 인식 → 사용자 확인 → 저장, `ledger_entry`에
`is_opening_balance` 플래그 추가)까지 논의했으나, **이번 범위에서는 구현을 보류**한다. 엑셀은 이미지가
아니라 구조화된 데이터라, 필요해지면 "엑셀 파싱 → 셀 내용을 LLM에 전달해 잔액 추출" 방식을 쓰고
LEDGER_FILE 분류는 xlsx만 허용하는 방향이 유력하다.

## 11. AI 채팅방은 기수당 여러 개 유지, 단일화하지 않음
AI 비서 대화방(`ai_chat_room`)을 기수당 1개로 통합할지 여러 개를 허용할지 논의했으나, **여러 개
유지로 결정**했다(현행 구조 그대로, ERD에 이미 반영됨). 로그인이 없어 그 기수 운영진 전체가
대화방을 공유하므로, 질문 주제·용건별로 대화방을 나눠 쓸 수 있게 둔다.

## 12. 운영 공간 노출 기준 — `has_operation_space = true`인 일정만, 운영 메뉴는 4개 고정
운영 공간 목록(API-012)의 SCHEDULE 타입 항목은 `has_operation_space = true`인 일정만 노출한다.
토글을 켜지 않은 일정(순수 캘린더 일정)은 운영 카드(메모·출석·정산·자료)가 없으므로 운영 공간
목록에 나타나지 않는다. MEETING 타입은 그런 토글이 없어 전부 노출한다.

운영 카드 수(`operationMenuCount`, API-009)는 실제 데이터 유무와 무관하게 항상 4(메모장·출석·
정산·자료)로 고정한다 — 운영 공간인 일정은 항상 이 4개 메뉴를 갖는다는 설계이기 때문이다.

**보류**: 토글을 켜지 않고 만든 일정에 나중에 운영 공간을 추가(토글 전환)하는 기능은 이번 결정에
포함하지 않는다. 일정 수정 API(API-007)에 그 필드를 추가할지는 추후 정한다.

## 13. `operationspace` 도메인을 `schedule`로 통합
안건 연결(`schedule_agenda_link`)과 운영 공간 목록 집계(API-012, 엔티티 없음)만 담당하던
`operationspace` 도메인을 `schedule` 도메인으로 완전히 흡수했다. 유일한 엔티티
`schedule_agenda_link`가 FK 2개뿐인 조인 테이블이라 별도 도메인을 유지할 만한 독립 책임이 없었다.

`docs/api-spec.md`는 화면/기능 단위 조직을 유지하기 위해 "운영 공간" 절(API-012~016)을 그대로
두지만, 실제 구현은 `domain/schedule/`의 Controller/Service/Mapper/ErrorCode/엔티티가 전부
담당한다. 새 도메인 패키지를 만들지 않는다.

## 14. 그 외 세부 규칙은 해당 API 개발 시점에 정한다
정산 공지 다중 계좌 처리, 차수 추가 시 기존 참가자 자동 포함 여부, 안건별 진행률(%) 계산식,
파일 업로드 정책(허용 확장자·용량 수치), 회의록 자동 매칭 알고리즘, AI 연동 벤더/모델 등
`docs/api-spec.md`의 "TBD" 섹션에 남아있는 세부 항목은 그 엔드포인트를 실제로 구현하는 시점에 정한다.

## 15. 진행 중인 회의도 삭제 가능
회의 상태가 IN_PROGRESS여도 DELETE(API-055)를 막지 않는다. 상태와 무관하게 삭제를 허용하고,
삭제 전 확인 모달 등 UX 경고는 프론트 책임으로 둔다.

## 16. 문서함(file) 구현 시점에 확정한 세부 정책
결정 14에서 "구현 시점에 정한다"고 남겨둔 항목 중 문서함 관련 4가지를 Figma 와이어프레임
정밀 확인을 거쳐 확정했다.

- **업로드 정책(API-062)**: 파일당 최대 20MB, 허용 확장자는 문서(pdf/doc/docx/ppt/pptx/xls/xlsx/
  txt/hwp/hwpx) + 이미지(jpg/jpeg/png/gif/webp) + zip.
- **다운로드 URL(API-065)**: presigned URL은 구현하지 않는다. `expiresAt`은 실제로 강제하지 않는
  형식상 값(발급 시점+24시간)이다. 와이어프레임 전체에 링크 만료·공유 UI가 없어 presigned URL을
  당장 만들 이유가 없다고 판단했다. (이후 실제 다운로드 동작까지 검증하며 세부 구현을 결정 18로
  한 번 더 바꿨다 — 아래 참고.)
- **파일 복구(API-067) 가능 기간**: 제한 없음. Figma에서 "복구" UI는 삭제 직후 뜨는 실행취소
  토스트(스낵바) 하나뿐이고 별도 휴지통 화면이 없어서, 서버가 기간을 강제할 이유가 없다고 판단했다.
  대신 소프트 삭제된 파일의 실제 스토리지 파일을 자동으로 영구 삭제하는 배치는 만들지 않는다.
- **장부 파일(LEDGER_FILE) 업로드는 교체(upsert)**: 문서함 화면의 "장부" 탭에서 업로드하면 기수당
  1개만 유지되고 기존 파일을 교체한다(기존 레코드는 소프트 삭제 후 새 레코드 생성). scheduleId는
  항상 무시하고 null로 저장한다 — 장부는 일정이 아니라 기수 단위이기 때문이다.

## 17. 목록 API를 cursor 기반 페이지네이션으로 전환
기존 §1 공통 규칙("목록 API는 전부 전체 반환, 데이터량이 커지면 그때 페이징 추가")을 뒤집고,
검색어·필터가 있거나 무한정 쌓일 수 있는 목록 API는 지금부터 cursor 기반으로 페이지네이션한다.
offset(`page`/`size`) 대신 cursor를 선택했다.

**왜 지금 바꿨는가**: file 도메인(API-064)만 구현이 끝난 시점이라 소급 비용이 가장 쌀 때다.
나머지 9개 도메인은 아직 Controller/Service가 빈 껍데기라, 지금 명세를 바꿔두면 그 담당자들이
처음부터 cursor 방식으로 짜게 된다.

**적용 범위**: 검색어·필터가 있거나 전형적으로 무한 스크롤 대상인 12개 API(API-005, 012, 023,
029, 037, 042, 047, 052, 064, 069, 077, 079)에 cursor를 적용한다. 반대로 드롭다운 선택지나
상위 리소스 하나에 달린 소량 고정 목록(API-009 다가오는 일정 미리보기, API-011 카테고리,
API-015 연결된 회의록, API-018 명단방 목록, API-027 차수 목록)은 그대로 전체 반환을 유지한다 —
페이지네이션하면 오히려 UI가 깨지거나(드롭다운은 전체가 필요) 데이터량 자체가 항상 작다.

**규약**: 쿼리 `cursor`(opaque 문자열, 생략 시 첫 페이지)·`size`(기본 20, 최대 100) 추가,
응답에서 `totalCount` 제거 후 `nextCursor` 추가(cursor 쪽은 카운트 쿼리가 이점을 없애서 뺀다).
cursor는 `(정렬 기준 컬럼, id)` 복합 키를 base64 인코딩한 값 — "최신순"이면 `createdAt`처럼
그 정렬이 실제로 의미하는 컬럼을 쓰고 id는 동률일 때 tiebreaker로만 쓴다(id가 IDENTITY라 지금은
생성순과 우연히 일치하지만 그 우연에 기대지 않는다). 방향은 다음 페이지만 지원(forward-only).
상세 규약은 `docs/api-spec.md` §1.

## 18. 단일 파일 다운로드도 서버가 직접 스트리밍하도록 변경
API-065(`download-url`)가 돌려주는 `url`이 스토리지(로컬/S3)의 원본 주소를 그대로 가리키던 걸,
이 서버 자신의 `GET /api/files/{fileId}/content`를 가리키도록 바꿨다. 실제로 PDF를 업로드해
다운로드까지 끝까지 검증하다가 발견한 문제다 — 원본 주소를 그대로 돌려주는 방식은
`Content-Disposition` 헤더가 전혀 안 붙어서, `mode=DOWNLOAD`로 요청해도 브라우저가 저장 대화상자
대신 그냥 파일을 새 탭에 열어버릴 수 있었다(PDF 등).

**왜 presigned URL(S3 `response-content-disposition`)이 아니라 이 방식인가**: presigned URL은
S3에만 적용되고 로컬 스토리지에는 못 쓰기 때문에, 결국 로컬 환경은 이 방식을 어차피 또 만들어야
한다 — 그러면 처음부터 스토리지 종류와 무관한 이 방식 하나로 통일하는 게 작업량도 적고
일관적이다. ZIP(API-068)에서 이미 `FileStorage.open()`으로 같은 패턴을 썼던 걸 그대로 재사용했다.
이 프로젝트 규모(인증 없음, 파일 수 적음)에서는 파일 트래픽이 앱 서버를 거치는 비용보다
구현 단순성·환경 일관성이 더 크다고 판단했다.

`/api/files/{fileId}/content?mode=PREVIEW|DOWNLOAD`가 실제로 파일 바이트를 스트리밍하며,
`DOWNLOAD`면 `Content-Disposition: attachment`, `PREVIEW`면 `inline`을 응답 헤더에 싣는다.
프론트가 axios 같은 API 클라이언트로 직접 부르는 게 아니라 `<a href>`/`<iframe src>`처럼
브라우저가 그 주소로 직접 요청을 보내는 방식으로 쓰이는 엔드포인트라, 기존 API-001~079 번호
구간을 그대로 두고 **API-080**으로 명세 맨 끝에 추가했다(`docs/api-spec.md` §2, §6).

## 19. 목록 API의 totalCount는 "첫 페이지 한정"으로 허용
#17에서 cursor 페이지네이션 응답에 `totalCount`를 빼기로 한 건 **스크롤마다 다시 세는** 비용
얘기였다. Figma 문서함 화면("총 N개")처럼 **목록 진입 시 한 번만** 보여줘야 하는 경우까지 막는
결정은 아니었다 — 그래서 `totalCount`를 cursor가 없는 첫 페이지 요청에서만 계산해 내려주고,
cursor가 있는 다음 페이지 요청에는 내려주지 않는 패턴을 허용한다. API-029 `progress.totalCount`,
API-037 `targetCount`와 같은 맥락(페이지네이션용이 아니라 그 도메인 의미의 total)이며,
API-064(file)에서 처음 적용했다. 비슷하게 "총 N개" UI가 필요한 다른 목록 API도 같은 패턴을
따르면 된다.

## 20. 안건 진행률(%)은 doneCount/totalCount 하나로 — 안건별 "논의중 N%"는 쓰지 않는다
회의 진행 화면의 진행률 바는 API-053 응답의 `progress.doneCount/totalCount`(완료 안건 수 ÷
전체 안건 수)만으로 계산한다. Figma 와이어프레임에 있던 "안건1 논의중 45%" 같은, 지금 보고 있는
안건 하나만의 진행중 퍼센트는 구현하지 않는다 — 어떤 값에서 그 퍼센트가 나오는지 명세에 없고,
안건 1이 아직 완료 전인 상태에서 doneCount/totalCount로 계산하면 0/3=0%라 Figma의 45%와도
맞지 않는다. `api-spec.md`에도 "Figma 자체에 진행률 바 수정 예정 메모가 있다"고 적혀 있던 걸로
보아 그 수치는 디자인 쪽에서도 확정 전 placeholder였다고 보고 폐기했다.

'다음 안건' 버튼(API-057, status=COMPLETED)을 누를 때마다 분자(doneCount)만 1씩 늘어나는
식으로 막대가 갱신된다. 안건은 항상 최소 1개 이상 존재하므로(API-058: 안건 1개만 남으면 삭제
불가) 분모가 0이 되는 경우는 없다.

**추가 확정(2026-11-11)**: `progress`에 `doneCount`/`totalCount`뿐 아니라 그 비율을 반올림한
`percentage`(정수, %)도 함께 내려준다. 개별 안건 퍼센트를 만든다는 뜻이 아니라, 지금도 하나뿐인
전체 진행률(doneCount/totalCount)을 프런트가 매번 나눗셈하지 않아도 되게 숫자로도 노출하는
것뿐이다 — 위 문단의 "개별 안건 % 미구현" 결론은 그대로 유효하다.

## 21. 회의 카테고리를 일정 카테고리와 분리 운영
`meeting.schedule_category_id`(일정과 공유)를 폐기하고 `meeting_category` 테이블을 새로
둔다. 일정 카테고리와 회의 카테고리는 운영상 서로 다른 분류 체계라 독립적으로 관리하기로
했다(사용자 결정, 2026-11-11). `schedule_category`는 그대로 유지하고 일정 전용으로 남는다.

회의 카테고리 추가·목록 조회 API는 기존에 일정과 공유하던 API-010/011을 그대로 쓰던 걸
그만두고, meeting 도메인이 자체 엔드포인트로 새로 갖는다. **API-081(생성)·API-082(목록 조회)로
확정(2026-11-11)**. 수정·삭제 API는 만들지 않는다 — 와이어프레임에 카테고리 추가만 있고
수정·삭제 UI가 없어 불필요하다고 판단(사용자 결정).

## 22. ErrorCode는 순번이 아니라 `{도메인}_{HTTP상태}_{의미}`로 짓는다
`MEETING_003`처럼 도메인+순번으로 짓던 걸 그만두고, `MEETING_404_CATEGORY_NOT_FOUND`처럼
도메인·HTTP 상태코드·무엇이 문제인지를 이름에 그대로 담는다(`my-spring-conventions` 스킬의
`USER_409_EMAIL` 예시와 같은 형식 — 순번 스타일은 file 도메인에서 생긴 지역적 관례였을 뿐
스킬이 원래 의도한 방식은 아니었다).

meeting·file 도메인은 코드까지 전부 이 형식으로 바꿨다. 다른 도메인(아직 구현 전)은
`docs/api-spec.md`에 있는 번호 표기만 전부 이 형식으로 미리 바꿔뒀다 — 실제 구현 시점에
코드도 이 이름을 그대로 따르면 된다. `CommonErrorCode`(`COMMON_400`/`COMMON_404` 등)는
대상이 아니다 — 이미 Java enum 상수 이름 자체가 `BAD_REQUEST`/`NOT_FOUND`처럼 서술적이고,
`COMMON_xxx`는 번호가 아니라 상태코드 그 자체라 바꿀 이유가 없다.

## 23. 회의 종료(API-059)는 안건이 전부 완료되지 않아도 허용한다
`doneAgendaCount`/`totalAgendaCount`를 응답에 따로 내려주는 것 자체가 둘이 다를 수 있다는
전제다 — 미완료 안건이 남아있어도 종료를 막지 않는다(사용자 결정, 2026-11-11). 안건별
`content`/`discussionNote`/`decision`은 원본 그대로 보존되므로, 나중에 "회의 정리" 화면에서
미완료 안건의 내용도 그대로 확인할 수 있다.

## 24. 안건은 `content` 단일 필드다 — "회의 수정" 화면에서 API-057로 고친다
피그마 "회의 만들기"(`54:944`)와 "회의 진행"(`60:1373`) 양쪽에서 서로 다르게 보였던 안건
텍스트(짧은 제목 vs 긴 설명)는 별도 필드가 아니라 와이어프레임 샘플 데이터 불일치였다 —
두 화면 다 안건엔 `content`(최대 100자) textarea 하나뿐임을 Figma·Notion PRD 양쪽에서
재확인했다(2026-11-11).

"회의 수정" 폼(`54:1019`, "수정하기"/"삭제하기" 버튼)은 기존 안건의 `content`를 textarea에
채워서 보여주고 그 자리에서 고치는 구조다. 이를 지원하기 위해 API-057(`PATCH
/api/agendas/{agendaId}`)이 `content`도 받도록 확장했다 — 기존 `discussionNote`/`decision`/
`status`와 동일한 부분 수정 규칙(보낸 필드만 반영)을 따른다. 안건 추가·삭제(API-056/058)와
회의 삭제(API-055)도 전부 이 "회의 수정" 화면에서 호출되는 것으로 확인됐고, 이미 구현돼
있어 추가 API 변경은 필요 없다.

## 25. AI 요약(API-060/061) — 벤더는 Gemini, 에러코드는 도메인 컨벤션을 따른다
**(2026-11-12 갱신: "벤더는 Gemini로 확정"은 더 이상 유효하지 않다 — #26 참고. 모델·
에러코드·종료체크·프롬프트 내용에 대한 아래 결정 자체는 그대로 유효하다.)**

AI 벤더는 Gemini로 확정(2026-11-11, 무료 티어 키 발급 완료). 모델은 `gemini-3.8-flash`
(preview가 아닌 가장 최신 stable flash — 무료 티어는 Flash/Flash-Lite/Gemma 계열만 해당하고
Pro는 2026-04-01부로 빠졌다)로 고정하되, `GEMINI_MODEL` 환경변수로 교체 가능하게 둔다.

`api-spec.md` 원문은 API-060/061의 404 에러를 "COMMON_404"로 적어뒀지만, meeting 도메인의
다른 모든 API(API-053~059)가 `MEETING_404_NOT_FOUND`를 쓰는 것과의 일관성을 위해
`MEETING_404_NOT_FOUND`로 구현한다 — `COMMON_404`는 오타/누락으로 보고 기존 도메인 컨벤션을
따르기로 했다(사용자 결정, 2026-11-11).

API-060/061은 "회의 종료 후" 화면(`64:1616`→`64:1789`)에서만 쓰이므로, 다른 meeting API와
달리 `MEETING_409_ALREADY_ENDED` 체크를 하지 않는다 — 오히려 종료된 회의에서 호출되는 게
정상 흐름이다. 프롬프트에는 안건의 `content`·`discussionNote`·`decision`만 담고 참석자 등
개인정보는 포함하지 않는다. AI 호출 실패는 재시도 없이 바로 `MEETING_502_SUMMARY_FAILED`를
반환하고, "다시 요약하기"는 사용자가 버튼을 다시 눌러 API-060을 재호출하는 것으로 처리한다.

## 26. AI 벤더는 하나로 고정하지 않는다 — Gemini/OpenAI를 `app.ai.provider`로 전환
#25에서 "Gemini로 확정"했지만, 실제 구현 뒤 무료 티어가 "This model is currently
experiencing high demand" 503과 read timeout을 반복적으로 반환하는 걸 세션 내내(2026-11-11~
12) 실측으로 확인했다. 마침 사용자가 OpenAI 계정에 남은 크레딧이 있어, "벤더 하나를 다시
고르는" 대신 **둘 다 코드에 유지하고 설정값 하나로 전환**하는 쪽으로 바꿨다(사용자 결정,
2026-11-12) — Gemini가 나중에 안정화되면 다시 돌아올 수 있게.

구조는 이 프로젝트에 이미 있던 `FileStorage`(local/s3) 패턴을 그대로 따른다
(`global/storage/local/LocalFileStorage.java`, `global/storage/s3/S3FileStorage.java`):
`global/ai/AiClient`(인터페이스) + `global/ai/gemini/GeminiClient`·`global/ai/openai/OpenAiClient`
(각각 `@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini|openai")`)
+ `AiClientException`(벤더 무관 공용 예외). `MeetingService`는 `AiClient` 인터페이스에만
의존하므로 벤더가 바뀌어도 도메인 코드는 손대지 않는다.

`GeminiProperties`/`OpenAiProperties`는 `app.ai.provider`와 무관하게 둘 다 항상 바인딩되므로
(`@ConfigurationPropertiesScan`이 전부 스캔), 여기에 `@NotBlank` 하드 검증을 걸면 **선택
안 한 벤더의 키가 없어도 기동이 막히는** 문제가 생긴다. 그래서 Properties 레코드 자체의
검증은 없애고, 각 Client의 생성자에서 자기 키만 수동으로 검사해 하드 실패시킨다 —
`@ConditionalOnProperty`가 걸려 있어 선택 안 된 Client는 빈 자체가 안 만들어지므로,
결과적으로 "선택된 벤더의 키만" 검증된다. 팀원이 자기가 안 쓰는 벤더의 키를 안 넣어도
로컬 실행에 지장이 없다.

모델은 OpenAI 쪽을 `gpt-4o-mini`로 기본값을 뒀다(환경변수 `OPENAI_MODEL`로 교체 가능).
OpenAI는 Chat Completions API(`POST {baseUrl}/v1/chat/completions`)를 쓰고, 키는 Gemini와
동일하게 쿼리파라미터가 아니라 `Authorization: Bearer` 헤더로 보낸다(로그에 키 노출 방지,
#25의 교훈을 그대로 적용).

**이 변경으로 TBD #17(AI 연동 벤더/모델 미정)을 해소 처리한다** — "벤더 하나를 확정"하는
대신 "전환 가능한 구조로 구현하고 설정값으로 결정"하는 것으로 결론 지었다. 단 영수증 인식
(API-036)·AI 채팅(API-078)이 같은 `AiClient`를 재사용할지는 그 도메인 구현 시점에 별도로
정한다.

## 27. API-029(차수 출석 현황 조회) 구현 시점에 정한 세부 사항
api-spec에 명시되지 않은 3가지를 구현 시점에 확정했다(사용자 결정, 2026-11-13).

- **keyword "초성 검색"은 이번 범위에서 보류한다**: `schedule_attendee.name`에 대한
  `containsIgnoreCase`(이름 포함검색)만 구현한다. 코드베이스에 초성 추출 유틸이 전혀 없고
  api-spec 전체에서 초성 언급이 있는 유일한 API라, 초성 매칭 자체는 별도 이슈로 미룬다.
- **`progress.rate`는 정수 퍼센트(0~100, 반올림)**: meeting 도메인의 `progress.percentage`
  (#20)와 같은 패턴이다.
- **attendees의 cursor 정렬 기준은 `round_attendee.createdAt` 오름차순, 동일 시 id 오름차순**
  (그 차수에 합류한 순서). `schedule_attendee.createdAt`(원래 참가자 등록 시점)이 아니다 —
  차수별 출석 현황이라는 API 성격상 "이 차수에 참가자가 추가된 순서"가 자연스럽다고 판단했다.
