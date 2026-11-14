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
파일 업로드 정책(허용 확장자·용량 수치), AI 연동 벤더/모델 등
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
  형식상 값(발급 시점+24시간)이다. (결정 42에서 `expiresAt`을 응답에서 제거했다.) 와이어프레임 전체에 링크 만료·공유 UI가 없어 presigned URL을
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

## 28. API-032(참가자 제외)는 그 차수의 `round_attendee`만 지운다
`docs/api-spec.md` §3 CRUD 커버리지 표는 "schedule_attendee 삭제=API-032, round_attendee
삭제=API-032 cascade"로 적혀 있어 문자 그대로 읽으면 참가자 원본까지 지우는 것처럼 보이지만,
API-032 설명 문구("차수에서 참가자 제외")와 PRD 스와이프 동작("미출석 카드 → 삭제")은 이 차수
하나에서만 빼는 동작이라 **좁게 구현한다**(사용자 결정, 2026-11-13): `schedule_attendee`는
그대로 두고 그 차수의 `round_attendee` 행만 삭제한다. 다른 차수의 출석 기록과 참가자 원본은
영향받지 않는다.

`round_attendee.id`를 FK로 참조하는 테이블이 없음을 Flyway(`V1__init.sql`)로 확인했다 —
`settlement_target`은 `schedule_round_id`·`schedule_attendee_id`를 직접 참조하므로 이 좁은
범위 구현에서 FK 충돌 가능성은 없다.

## 29. API-030 에러 목록에 COMMON_404가 없는 건 문서 누락으로 간주한다
`docs/api-spec.md`의 API-030 에러 목록은 COMMON_400·ATTENDANCE_409_DUPLICATE_NAME만 적혀
있지만, roundId가 존재하지 않는 경우를 처리하지 않을 이유가 없고 다른 모든 차수 관련 API(027/
028/029/031/032)는 전부 COMMON_404를 포함한다. 문서 누락으로 보고 API-030도 roundId가
없으면 COMMON_404를 던지도록 구현한다(사용자 결정, 2026-11-13).

## 30. API-031 에러 목록에 COMMON_400이 없는 것도 문서 누락으로 간주하고, 동일 상태 재요청은 멱등 처리한다
`docs/api-spec.md`의 API-031 에러 목록은 COMMON_404만 적혀 있다. 하지만 Request
`isCheckedIn`이 누락된 요청을 조용히 `false`로 처리하면 출석 데이터가 의도치 않게 바뀔 수
있어, #29와 같은 이유로 문서 누락으로 간주한다(사용자 결정, 2026-11-13) — `isCheckedIn`을
`@NotNull Boolean`으로 검증하고, 누락 시 COMMON_400을 반환하도록 구현한다.

같은 상태로 재요청하는 경우(이미 체크인된 상태에 `isCheckedIn=true`, 이미 미체크인 상태에
`isCheckedIn=false`)는 멱등하게 처리한다 — 아무 것도 바꾸지 않고 기존 `checkedInAt`을 그대로
반환한다. 상태가 실제로 바뀌는 경우에만 `checkedInAt`을 갱신한다(체크인 전환 시 현재 시각,
되돌리기 전환 시 null).

## 31. 1인 청구액 계산기는 인원 0명이면 예외를 던진다 — 호출 측이 막는 게 정상 경로
`BilledAmountCalculator.calculate`의 인원수(headcount) 0 처리는 `docs/api-spec.md`에 없는
세부사항이라 구현 전 확인했다. 인원수가 0 이하면 조용히 0을 반환하지 않고
`IllegalArgumentException`을 던지기로 했다(사용자 결정, 2026-11-13) — 계산기 스스로 0을
특별 취급해 넘기는 대신, 호출하는 Service가 호출 전에 막는 것을 정상 경로로 삼는다.
API-041(입금 명단에서 삭제)로 어떤 차수의 남은 인원이 0명이 되면 그 차수는 재계산 대상에서
제외하고 계산기를 호출하지 않는다.

## 32. 1인 청구액 계산기는 billedAmount만 반환한다 — 버림 차액은 API-074가 기록한다
100원 단위로 버릴 때 생기는 차액(나머지)을 계산기가 함께 반환할지도 문서에 없어 구현 전
확인했다. 계산기는 100원 단위로 버린 `billedAmount` 하나만 반환하기로 했다(사용자 결정,
2026-11-13) — 그 차액은 계산기의 책임이 아니라, 전원 입금 완료 시 "추가 동아리 지원금"
지출 1건으로 자동기록되는 로직(결정 2)에서 API-074(정산 내역 새로고침) 시점에 별도로
계산해 `ledger_entry`에 반영한다.

## 33. AI 채팅(API-078)은 질문 분기가 아니라 도구 기반 라우터로 만든다
AI 비서는 "그 기수의 일정·정산·출석·예산·회의·메모·자료 전부에 대해 자연어로 물으면 답하는"
기능이라, 사용자가 어떤 문장을 던질지 미리 알 수 없다. 질문 유형별로 분기를 박는 방식은
예측하지 못한 질문에서 바로 무너지므로 쓰지 않기로 했다(2026-11-13).

채택한 구조는 OpenAI function calling 기반 라우터다. 모델에게 "쓸 수 있는 조회 도구 목록"을
선언해주고, 어떤 도구를 어떤 인자로 호출할지는 모델이 정한다. 실제 DB 조회는 백엔드가 하고
그 결과를 다시 모델에 돌려주면 모델이 답변 문장을 만든다. 한 번에 안 끝나는 질문("가을공연
2차 정산 안 한 부원")이 있어 단발 호출이 아니라 최대 5라운드 루프로 돌리고, 넘으면
`CHAT_502_AI_FAILED`로 끊는다.

**도구는 질문 종류가 아니라 도메인 데이터 범위 단위로 만든다.** "미입금자 조회 도구"가 아니라
"그 일정의 입금 현황 전체를 주는 도구"다. 그래야 "미입금자 중 제일 많이 낸 사람"처럼 미리
예상하지 못한 파생 질문도 같은 도구가 준 원본 row 안에서 모델이 추려내 답할 수 있다.
도구 10개(일정 검색·일정 상세·입금 현황·출석 현황·부원 검색·회의 검색·회의 상세·메모 검색·
자료 검색·장부 요약)로 ERD 18개 테이블을 전부 덮는다.

## 34. 집계는 백엔드가, 추론은 AI가 — 숫자는 모델이 계산하지 않는다
합계·인원수·잔액은 전부 백엔드가 확정해 도구 결과에 실어 보내고, 시스템 프롬프트로
"도구가 준 값만 쓰고 직접 더하거나 빼지 말 것"을 명시한다. 모델이 숫자를 직접 다루면 틀리기
때문이다. 반대로 "회의에서 누가 운영을 맡기로 했는지"처럼 텍스트를 읽어야 아는 것은 원문을
그대로 넘겨 모델이 읽게 한다(`get_meeting_detail`은 안건의 논의 내용·결정 사항을 요약 없이
원문 전체로 보낸다).

UI 버튼이 걸리는 식별자(`attendeeId` 등)는 모델의 답변 문장이 아니라 **백엔드가 만든 카드
JSON**으로 내려보낸다 — 카드는 조회 결과의 스냅샷이라 모델의 추론과 무관하게 항상 정확하다.

실측에서 이 분리가 실제로 필요하다는 게 드러났다. 이름이 비슷한 값을 둘 다 내려주면 모델이
엉뚱한 쪽을 고른다 — 같은 유형의 오답이 세 번 나왔다.

- "기획파트 몇 명"에 기수 전체 인원을 답함 → `matchedMemberCount`/`termTotalMemberCount`로 분리하고
  적용된 검색 조건도 결과에 함께 실었다
- "뒤풀이 1,2차 합해서 얼마"에 영수증 결제액(70만원) 대신 부원 청구액(58만원)을 답함 →
  두 금액의 성격(결제한 돈 vs 걷을 돈, 차이는 회비 지원금)을 도구 설명에 명시하고,
  일정 상세에 `totalReceiptAmount`를, 정산에 차수별 소계 `roundSummaries`를 추가했다
- "2차 참석 몇 명"에 "총 6명 참석, 그중 4명 출석"이라는 모순 문장 → 출석 대상 인원을
  `totalCount`에서 `targetCount`로 바꿔 참석 인원(`checkedInCount`)과 이름으로 갈랐다

**교훈 둘**: (1) 값의 의미가 다르면 이름만으로 구분되게 짓는다. (2) 결과 row에만 있는 필터 축은
파라미터로 내줘야 한다 — "안 끝난 할 일"에서 모델이 완료 여부를 검색어로 거르려다 0건을 받고
"없다"고 답한 적이 있어, 메모 검색에 `noteType`(MEMO/TODO)을 추가했다.

## 35. 도구 실행 실패는 "AI가 고칠 수 있는 실수"인지로 갈라 처리한다
모델은 없는 도구를 부르거나 인자 타입을 틀리게 넘기고, 존재하지 않는 ID를 지어내기도 한다.
여기서 전부 예외를 던지면 대화가 끊기고, 반대로 전부 삼키면 진짜 장애가 묻힌다. 그래서:

- 미등록 도구 이름, 인자 JSON 파싱 실패, 타입 불일치, 필수 인자 누락, 없는/다른 기수의 ID →
  `{"error", "hint"}` JSON을 도구 결과로 돌려줘 모델이 고쳐 부르게 한다. 이 라운드도 반복
  횟수에 포함시킨다(안 그러면 잘못된 호출을 무한 반복한다)
- 조회 결과 0건 → 에러가 아니라 빈 결과. 모델이 "찾지 못했다"고 답하게 둔다
- DB 장애·우리 코드 버그 → 삼키지 않고 그대로 올려 `CHAT_502_AI_FAILED` + `log.error`.
  재시도해도 안 고쳐지고, 묻히면 장애를 놓친다

## 36. AI 도구에는 전화번호를 뒤 4자리만 넘긴다
`search_members`·`get_schedule_detail`·`get_settlement_status`는 전화번호를 뒤 4자리만
반환한다. 전체 번호를 외부 AI 벤더로 보내지 않기 위해서이고, 미입금 카드가 이미 `phoneLast4`만
쓰고 있어 기존 규칙과도 맞는다. "연락처 알려줘" 류 질문에는 시스템 프롬프트 규칙에 따라
"부원 명단 화면에서 확인해 주세요"라고 안내한다. 자료의 다운로드 링크도 같은 이유로 넘기지
않는다 — 링크를 주지 않았더니 모델이 가짜 링크를 지어내서, 프롬프트에 "링크·URL은 만들어내지
않는다"를 명시했다.

## 37. 대화방 제목은 첫 질문 원문이 아니라 AI가 다듬은 제목으로 저장한다
명세(API-076)는 "제목을 비우면 서버가 첫 질문으로 정한다"로 돼 있고 API-077 응답 예시도 질문
원문(`"가을공연 미입금자 알려줘"`)이다. 하지만 Gemini·ChatGPT처럼 **첫 질문을 20자 이내
명사형 제목으로 다듬어 저장**하는 쪽이 목록에서 훨씬 읽기 좋아 그렇게 바꿨다(2026-11-13).
명세와 달라지는 지점이라 여기 남긴다.

방의 첫 질문에서 한 번만 호출하고, 도구가 필요 없으므로 기존 `AiClient.generateContent`를
그대로 재사용한다. LLM이 지시를 어길 수 있으니 프롬프트 제약과 서버 측 출력 정리(따옴표·
`제목:` 접두어·마침표 제거 후 100자 컷)를 둘 다 건다. 제목 생성이 실패해도 답변은 막지
않고 **AI 제목 → 질문 원문 100자 컷 → "새 대화"** 순으로 폴백한다 — "새 대화"가 여러 개
쌓이면 목록에서 구분이 안 되므로 원문 폴백을 중간에 둔다. 실패는 `log.warn`으로 남긴다.

## 38. Gemini에는 function calling을 구현하지 않는다
`AiClient.chat(messages, tools)`는 OpenAI 구현체에만 있고 `GeminiClient.chat()`은
`UnsupportedOperationException`을 던진다. #26에서 벤더 전환 구조를 만들었지만 운영은
`app.ai.provider=openai`로 고정돼 있고, Gemini의 함수 호출 스펙은 메시지·도구 표현이 달라
지금 맞춰두면 쓰지 않는 코드를 검증 없이 떠안게 된다. Gemini로 돌아갈 일이 생기면 그때
구현한다 — 회의 요약이 쓰는 `generateContent`는 양쪽 다 그대로 동작한다.

## 39. 미입금 카드의 금액은 "전체 청구액"이 아니라 "아직 안 낸 금액"이다
Notion API-078의 "기타"에 피그마 카드 금액이 무엇을 가리키는지 불명확하다는 지적이 있었다.
구현 시점에 **미납 차수만 합산한 금액**으로 확정한다(2026-11-13).

여러 차수에 참여한 사람이 1차는 내고 2차는 안 냈을 때, 전체 청구액(1차+2차)을 카드에 적으면
항목 금액의 합이 카드 총액(미입금 총액)과 맞지 않는다. 실제로 e2e에서 이 불일치가 나왔고
AI 답변 문장과도 어긋났다. 도구 결과에는 `totalBilledAmount`(전체 청구액)와
`unpaidAmount`(미납분)를 **둘 다** 내려주되 카드와 "아직 안 낸 금액"에는 후자만 쓰고,
이름이 헷갈리기 쉬워 시스템 프롬프트에도 어느 쪽을 쓸지 명시했다.

## 40. AI 답변의 화면 이동은 경로가 아니라 "화면 종류 + 대상 ID"로 준다
AI 챗 화면에는 답변 아래 "정산·입금 관리로 이동 →" 같은 버튼이 있다. 서버가 그 버튼의 경로를
문자열(`/schedules/10/settlement`)로 만들어 주지는 않는다(2026-11-13). FE 라우팅 규칙을 백엔드가
알게 되면 웹·앱이 갈라지거나 URL 구조가 바뀔 때마다 서버를 고쳐야 하기 때문이다. 대신
`navigation: { type, targetId }`만 내려주고 어느 화면인지는 화면을 가진 쪽이 정한다.

`type`은 7종이다 — SETTLEMENT·ATTENDANCE·NOTE·SCHEDULE(일정 하위 운영 메뉴라 targetId가 일정 ID),
MEETING(회의 ID), FILE·BUDGET(기수 단위 화면이라 targetId 없음). 어떤 도구가 실행됐는지로 정하고,
여러 도구가 연쇄로 돌면 정해진 우선순위에서 먼저 걸리는 하나를 쓴다.

**검색 도구(`search_schedules`·`search_meetings`)는 이동 대상에서 뺀다.** 결과가 여러 건일 수
있어 어느 일정·회의로 보낼지 정할 수 없다. 같은 이유로 **같은 도구가 서로 다른 인자로 두 번
이상 실행되면 버튼을 만들지 않는다** — 미입금 카드가 `singleRun`으로 쓰는 방어와 같은 기준이다.

**저장 위치**: 메시지 목록 조회(API-079)에서도 같은 버튼이 보여야 해서 응답에만 얹지 않고
`ai_message`에 저장한다(V3 마이그레이션). 기존 `target_schedule_id`는 일정만 가리킬 수 있어
회의록을 담지 못하므로 `navigation_type`·`navigation_target_id`로 대체했다. **명세(API-078)에
적혀 있던 `targetScheduleId` 응답 필드도 함께 없앴다** — 같은 정보가 두 군데 있으면 FE가 어느
쪽을 봐야 할지 헷갈리고, 아직 FE가 붙기 전이라 지금이 바꿀 적기였다.

`navigation_target_id`에는 **FK를 걸지 않는다**. `navigation_type`에 따라 `schedule`·`meeting` 중
서로 다른 테이블을 가리키는 다형 참조라 하나의 FK로 묶을 수 없다. 값은 도구가 실제로 조회에
성공한 ID만 들어가므로 저장 시점에 존재가 이미 검증돼 있다.

## 41. API-033/API-034(영수증 조회·등록) 구현 시점에 정한 세부 사항
`docs/api-spec.md`의 API-034 설명 자체가 서로 충돌하는 부분이 있어 구현 전 확인했다(사용자
결정, 2026-11-13).

- **PUT은 등록·수정 공용 upsert다**: 에러 목록의 `SETTLEMENT_409_RECEIPT_ALREADY_REGISTERED`와
  Logics 문단("금액이 바뀌면 재계산")이 서로 충돌한다 — 재계산이 일어나려면 이미 등록된
  차수에도 PUT이 다시 들어와야 하는데, 409로 막으면 그 경로 자체가 죽은 코드가 된다. CRUD
  커버리지 표가 `schedule_round`의 API-034를 "생성"이 아니라 "수정" 칸에 적어둔 것과도
  일치하므로, PUT은 차수가 존재하고 입금 완료자가 없으면 이미 영수증이 등록돼 있는지 여부와
  무관하게 항상 반영되는 upsert로 구현한다. `SettlementErrorCode.SETTLEMENT_409_RECEIPT_ALREADY_REGISTERED`는
  enum에는 남겨두지만(문서상 번호 유지 목적) 이번 구현에서는 어디서도 던지지 않는 미사용
  상수다. 수정을 막는 유일한 조건은 입금 완료자가 1명이라도 있을 때(`SETTLEMENT_423_RECEIPT_LOCKED_BY_PAYMENT`)뿐이다.
  `SETTLEMENT_409_RECEIPT_ALREADY_REGISTERED`는 api-spec 원문을 유지하기 위해
  `SettlementErrorCode`에 그대로 남겨두되 API-034에서는 사용하지 않는다 — API-033(영수증
  조회)의 `isLocked`가 입금 완료 전에는 false이고 그 동안은 화면의 수정 버튼이 활성화돼
  있으므로, 입금 완료 전 재등록은 곧 "수정"이지 "중복 등록"이 아니기 때문이다.
- **storeName/transactedAt은 선택값이다**: `schedule_round`의 두 컬럼 모두 DB에서도
  nullable이고, "가격만 등록"하는 경우(가게명·거래일시를 모르거나 생략하는 경우)를 지원한다.
  totalAmount/clubSubsidyAmount만 필수로 검증한다.
- **imageKey → image_url은 등록 시점에 변환해서 저장한다**: file 도메인은 "키를 저장하고
  화면에 보여줄 때 `FileStorage.toUrl()`로 변환"하는 패턴이지만, `schedule_round`에는 키를
  저장할 별도 컬럼이 없고 컬럼명·응답 필드명이 전부 `image_url`/`imageUrl`이다. 그래서 PUT
  처리 시점에 `FileStorage.toUrlOrNull(imageKey)`로 변환한 완성된 URL 문자열을 `image_url`에
  저장한다. `toUrl()`은 presigned가 아니라 만료되지 않는 고정 URL이다(local/S3 둘 다
  `baseUrl + "/" + key` 결합만 함 — `docs/decisions.md` #18과 동일한 전제). imageKey를
  생략한 요청(수정 중 사진은 그대로 두는 경우)은 기존 `image_url`을 그대로 유지하고, 새
  값으로 덮어쓰지 않는다.
- **API-034는 장부를 건드리지 않는다**: 영수증 지출 행은 API-074(정산 내역 새로고침)가
  영수증 등록된 차수마다 1건씩 생성한다. 따라서 API-034 구현에는 ledger_entry 생성을
  포함하지 않는다.

## 42. 삭제된 파일은 열리지 않게 막고, 강제되지 않는 expiresAt은 응답에서 뺀다
구현된 파일 미리보기·다운로드 흐름을 다시 점검하다 찾은 결함 3건을 고쳤다.

- **소프트 삭제된 파일 열람 차단**: API-065·API-080·API-066이 `findById`로 파일을 찾아
  `deleted_at`이 찍힌 파일도 URL 발급과 바이트 서빙이 그대로 동작했다. 목록 조회만 삭제 여부를
  걸러서, "삭제했다"는 의도가 URL 직접 호출로 무력화됐다. `FileRepository.findActiveById`를
  추가해 세 지점을 바꿨고 에러는 기존 `FILE_404_NOT_FOUND`를 재사용한다 — "없음"과 "삭제됨"을
  응답에서 구분해 알려줄 실익이 없다. API-067(되돌리기)은 삭제된 파일을 찾아야 하므로 `findById`를
  유지한다. API-066은 이미 삭제된 파일에 다시 들어오면 **200(멱등)**으로 통과시키되 `deleted_at`은
  덮어쓰지 않는다 — 404로 끊으면 중복 클릭이나 서버 커밋 후 타임아웃된 재시도에서, 실제로 성공한
  삭제가 프론트에 실패로 보인다.
- **`expiresAt` 제거**: 발급 시점+24시간을 응답에 실어 보내면서 어디서도 만료를 검증하지 않아,
  스펙만 보는 쪽이 만료·보안이 걸려 있다고 착각하게 되는 거짓 계약이었다. 이 프로젝트는 인증이
  없어 서명 URL이나 만료를 구현해도 보안이 추가되지 않는다(발급 API를 누구나 호출할 수 있다).
  그래서 만료를 구현하는 대신 필드를 없앴다. 프론트에 breaking change다.
- **스트리밍을 트랜잭션 밖으로**: `FileService`의 클래스 레벨 `@Transactional(readOnly = true)`가
  `writeFileContentTo`·`writeZipTo`에도 걸려 바이트 전송 전 구간에 트랜잭션이 열려 있었다. DB
  조회(헤더 확정)와 전송을 DTO로 분리한 설계 의도와 어긋나므로 `Propagation.NOT_SUPPORTED`를
  달았다. `ChatService.sendMessage`가 AI 호출 때문에 이미 같은 처리를 하고 있다.

서버가 직접 스트리밍하는 구조(결정 18) 자체는 유지한다. presigned URL 전환과 HTTP Range(206)
지원은 S3를 실제로 운영에 쓰기로 할 때 다시 본다 — 업로드 상한이 20MB라 지금은 체감되지 않는다.

## 43. 문서함 결함 수정 시점에 정한 세부 사항
구현된 file 도메인 API를 실제로 띄워 전수 점검하다 발견한 결함 4건을 고치면서 정한 것들
(이슈 #73·#74·#75·#77).

- **장부 복구는 활성 장부가 있으면 409로 막는다**: 교체로 밀려난 장부를 복구(API-067)하면 활성
  장부가 2개가 되고, 그 뒤의 장부 업로드가 기존 장부를 찾는 단계에서 결과 2건을 만나 500이 났다.
  "기수당 장부 1개"(결정 16)를 복구 경로에서도 지키도록 `FILE_409_LEDGER_ALREADY_EXISTS`를 추가해
  끊는다. 최신 1건만 보도록 조회를 고치는 방법도 있었지만, 그러면 화면에 장부가 2개 보이는 상태는
  그대로 남는다 — 불변식을 깨는 쪽을 막는 게 맞다고 판단했다.
- **업로드 실패 시 스토리지 파일을 정리하는 로직은 넣지 않는다**: 위 500이 스토리지 쓰기 다음
  단계에서 터져서 고아 파일이 1개 남았었다. 복구를 막아 그 경로 자체가 없어졌고, 나머지 검증은
  전부 스토리지 쓰기 전에 끝난다. 소프트 삭제된 파일의 실제 파일도 남겨두는 결정(16)과 같은 결로,
  스토리지 정리는 하지 않는다.
- **자료명 상한은 255자**: `file.name` 컬럼이 VARCHAR(255)인데 서비스에 길이 검증이 없어 256자에서
  `Data truncation` 500이 났다. 컬럼 길이를 그대로 `FileUploadPolicy.MAX_NAME_LENGTH`에 두고
  `FILE_400_NAME_TOO_LONG`으로 끊는다. API-062 응답에는 추가하지 않는다 — 화면에서 이름 입력
  글자수를 제한할 근거가 필요해지면 그때 넣는다.
- **멀티파트 파트 누락은 공통 400으로 받는다**: `file` 파라미터를 안 보내면
  `MissingServletRequestPartException`이 핸들러에 없어 500이 났다. 멀티파트를 받는 API가 더 생길
  수 있으므로 도메인 코드가 아니라 기존 공통 400 핸들러에 함께 받는다(`MaxUploadSizeExceededException`
  을 `COMMON_413`으로 받는 것과 같은 결).
- **용량 초과 응답은 `COMMON_413`이 정본이다**: `spring.servlet.multipart.max-file-size`가
  `FileUploadPolicy.MAX_SIZE_BYTES`와 같은 20MB라, 초과 요청은 서비스에 닿기 전에 요청 파싱
  단계에서 끊긴다. 즉 `FILE_413_SIZE_EXCEEDED`는 HTTP 경로에서 도달할 수 없다. multipart 상한을
  올려 서비스가 검증하게 만드는 선택지도 있었지만, 20MB를 넘는 바이트를 일단 받아들이는 비용을
  지면서 에러코드 이름만 바꾸는 셈이라 **명세를 실동작에 맞추는 쪽**을 택했다. `FILE_413_SIZE_EXCEEDED`
  는 서비스를 직접 호출하는 경로를 위한 방어로 남긴다.
- **`uploads/`는 git에서 제외한다**: 로컬 실행 시 업로드된 파일이 untracked로 잡혀 커밋에 섞일 수
  있었다.


## 44. 일정 소속 회의록은 `meeting.schedule_id`, 개별 안건 연결은 `schedule_agenda_link`
회의록이 일정과 엮이는 경로가 두 가지로 갈린다는 게 화면에서 확정됐다(2026-11-14, 피그마
"회의록 연동 가능하게 하는 방식" 섹션 + 사용자 결정).

| 경로 | 결과 |
|---|---|
| 운영 공간 → 회의록 생성 | 일정과 무관한 회의록 |
| 일정 상세 → "회의록 작성하기" | 그 일정에 속한 회의록(안건 전체가 함께 일정에 속한다) |
| 일정 상세 → "안건 연결하기" | 회의록이 아니라 **개별 안건만** 일정에 연결 |

**`meeting`에 nullable FK `schedule_id`를 추가한다.** NULL이면 일정과 무관한 회의록,
NOT NULL이면 그 일정에 속한 회의록이다 — `file.schedule_id`(NULL=자료실 직접등록)와 같은 패턴이다.
"일정에서 만든 회의록"을 `schedule_agenda_link`에 그 회의의 안건을 전부 넣는 방식으로 표현할 수도
있었지만, 그러면 안건을 추가·삭제할 때마다 링크를 맞춰줘야 하고, 일정 상세 화면에서 **회의록 카드**와
**개별 연결 안건 카드**를 구분해 그릴 수가 없다(화면은 둘을 `전체/회의록/안건` 탭으로 나눠 보여준다).

여기서 따라오는 규칙:

- **연결 후보는 "어느 일정에도 속하지 않은 안건"뿐이다**(API-013, API-083). 조건은
  `meeting.schedule_id IS NULL` **그리고** `schedule_agenda_link`에 그 안건의 행이 없음. 이미 어떤
  일정에 속한 회의록의 안건이 다른 일정으로 또 끌려가지 않게 막는다.
- **일정 삭제(API-008)는 그 일정에 속한 회의록을 안건·요약까지 함께 삭제한다.** 위 후보 규칙 덕에
  일정 소속 회의록의 안건은 다른 일정에 연결될 수 없어, 삭제가 다른 일정으로 번지지 않는다.
  일정과 무관한 회의록은 지금까지처럼 보존하고 `schedule_agenda_link` 행만 지운다.
- **회의록의 일정 소속은 생성 시점에 확정하고 이후 바꾸지 않는다.** 회의 정보 수정(API-054)은
  `scheduleId`를 받지 않는다 — 소속을 옮길 수 있으면 위 두 규칙의 전제가 깨진다.
- **회의 목록(API-052)·운영 공간 목록(API-012)은 일정 소속 회의록도 그대로 포함한다.** 일정 상세에
  카드로 보인다고 목록에서 숨기면 그 회의록을 찾을 경로가 사라진다. 대신 API-052에 `scheduleId`
  필터를 추가해 일정 상세 화면이 자기 일정의 회의록만 받아갈 수 있게 한다.
- **회의·안건 삭제 시 `schedule_agenda_link`를 먼저 지운다.** 연결된 안건이 생기는 순간
  FK 제약으로 삭제가 실패하는 결함이 있었다(API-013이 아직 미구현이라 드러나지 않았다).

## 45. API-014(연동할 회의록 찾기 — 자동 매칭)를 명세에서 삭제한다
`POST /api/schedules/{scheduleId}/agenda-links/auto`를 `docs/api-spec.md`에서 지웠다. 구현된 적은
없고 schedule 도메인이 아직 껍데기라 코드 변경은 없다.

**이유**: 그런 화면이 없다. 설계 초기에 피그마의 "연동할 회의록 찾기"/"연결할 안건 찾기" 버튼을
"AI가 관련 안건을 찾아 자동으로 연결해 주는 기능"으로 읽고 엔드포인트를 만들었는데, 2026-11-14에
해당 화면(피그마 `416:369`)을 다시 열어 확인한 결과 그 버튼은 **빈 상태에서 안건 선택 모달을 여는
버튼**이었다. "연결할 회의록이 없어요 / 일정과 관련된 회의를 작성한 뒤 이 일정에 연결할 수 있어요"
아래에 놓인, 목록이 비었을 때의 진입 버튼이다.

실제 연결 흐름은 처음부터 수동 선택 하나뿐이다:

```
안건 연결하기(빈 상태에서는 "연결할 안건 찾기")
  → 모달: 어느 일정에도 속하지 않은 안건 전체 목록(API-083)
  → 안건 선택 → "추가" → 연결(API-013)
```

**대체 경로**: API-083 + API-013. 사용자가 직접 고른다.

**파생**: `docs/api-spec.md` §7의 TBD #14(자동 매칭 알고리즘 기준)는 정할 대상 자체가 사라져
해소 처리했다. 나중에 "관련 안건 추천"이 필요해지면 그때 새 번호로 설계한다 — 쓰이지 않는 뼈대를
남겨두면 구현자가 없는 기능을 있다고 믿고 채우려 든다.

## 46. 종료된 회의의 안건은 일정에 연결할 수 있지만 거기서 수정은 못 한다
일정 상세의 "안건 연결하기" 후보(API-083)는 회의 상태를 거르지 않는다 — 종료된 회의의 안건도
연결할 수 있다. 지난 회의에서 결정된 사항을 이 일정에 참고로 달아두는 게 자연스러운 쓰임이기
때문이다.

다만 연결한 뒤 그 안건 카드의 논의 내용·결정 사항은 **읽기 전용**이다. 회의가 끝나면 안건
수정(API-057)이 `MEETING_409_ALREADY_ENDED`로 막히는데, 이 차단을 푸는 선택지도 있었지만
회의 진행 화면과 같은 API라 거기서도 함께 풀린다 — 회의를 종료해 회의록을 확정한다는 의미가
흐려지므로 막아둔 쪽을 유지한다(사용자 결정, 2026-11-14).

**따라오는 것**: API-015 응답에 `meetingStatus`를 추가한다. 화면이 입력란을 잠그려면 회의 상태가
필요한데, 안건 상태로는 판단할 수 없다 — 종료된 회의에도 PENDING·IN_PROGRESS 안건이 남아 있을 수
있다(실측: 회의 28이 COMPLETED인데 안건 53은 IN_PROGRESS).

## 47. settlement_target의 (schedule_round_id, schedule_attendee_id) 중복은 DB UNIQUE로 막는다
`settlement_target`에는 애초에 이 조합을 막는 제약이 없어, API-038(정산 명단 저장)이 같은
참가자를 두 번 저장하면 중복 행이 생길 수 있었다. 코드 레벨 검증만으로 막을지, DB 제약까지
추가할지 구현 전에 확인했다(사용자 결정, 2026-11-15) — **DB UNIQUE 제약을 추가**하기로 했다.
V4가 이미 `meeting_schedule`로 쓰여 있어 `V5__settlement_target_unique.sql`로
`uk_settlement_target_round_attendee` 유니크 키를 추가했고, `SettlementTarget` 엔티티의
`@Table(uniqueConstraints = ...)`에도 동일하게 반영했다.

API-038의 명단 교체는 "전체 삭제 후 재등록" 대신 **diff 방식**(기존-신규를 비교해 유지 대상은
금액만 갱신, 빠지는 대상만 삭제, 새 대상만 추가)으로 구현했다 — Hibernate가 flush 시 insert를
delete보다 먼저 실행해, 같은 (차수, 참가자) 조합을 지웠다가 바로 다시 넣으면 이 유니크 제약과
순간적으로 충돌할 수 있기 때문이다. diff 방식은 같은 조합을 지웠다가 다시 넣는 일이 없어 이
충돌에서 자유롭다.

## 48. API-032(참가자 제외)로 빠지는 사람의 settlement_target 처리
#28에서 API-032는 그 차수의 `round_attendee`만 지우기로 했는데, 정산 명단(API-037~039)이
구현되면서 그 사람의 `settlement_target`을 어떻게 할지가 새로 생겼다(구현 전 확인, 사용자 결정,
2026-11-15).

- 제외 대상이 **입금 완료(PAID)** 상태면 제외 자체를 `SETTLEMENT_423_PAID_MEMBER_LOCKED`로
  막는다. `round_attendee`도 지우지 않는다 — 이미 돈을 낸 사람의 출석 기록과 정산 기록이 함께
  사라지면 안 되기 때문이다.
- **미입금(UNPAID)** 상태면 `round_attendee`와 함께 `settlement_target`도 삭제하고, 남은
  인원으로 `BilledAmountCalculator`를 다시 호출해 1인 청구액을 재계산한다. 남은 인원이 0명이
  되면 계산기를 호출하지 않는다(#31과 같은 이유).

## 49. 정산 명단 저장(API-038) attendeeIds에 다른 일정 소속 참가자가 섞이면 COMMON_400
API-038의 attendeeIds는 `schedule_attendee.id`를 받는데, 존재하긴 하지만 그 차수가 속한
일정 소속이 아닌 id가 섞여 들어올 수 있다. 어떤 에러로 거부할지 구현 전에 확인했다(사용자 결정,
2026-11-15) — 전용 코드를 신설하지 않고 **COMMON_400**으로 처리한다. 존재하지 않는 id가 섞인
경우도 같은 COMMON_400으로 묶어, 크기 불일치·소속 불일치를 같은 검증 단계에서 함께 거른다.

## 50. API-040(입금 확인·되돌리기)은 같은 상태 재요청을 멱등 처리한다
이미 PAID인 차수에 `paid=true`(또는 이미 UNPAID인 차수에 `paid=false`)가 다시 오면 어떻게
처리할지 구현 전에 확인했다(사용자 결정, 2026-11-15) — `RoundAttendee.updateCheckedIn`(#30과
같은 기준)과 동일하게 **멱등 처리**한다. 상태가 실제로 바뀌지 않으면 에러 없이 200을 주고 그
차수의 `paidAt`은 건드리지 않는다. 상태가 실제로 바뀔 때만 `paidAt`을 갱신한다(입금 확인 시
현재 시각, 되돌리기 시 null).

한 사람이 일정 내 여러 차수에 걸쳐 있을 때 일부 차수는 이미 같은 상태(멱등), 일부는 실제로
바뀌는 혼재 상황도 생길 수 있어, `SettlementTarget.updatePaymentStatus(boolean)`를 차수별로
독립 적용한다. 응답의 대표 `paidAt`은(차수마다 값이 다를 수 있어) 입금 확인이면 그중 가장 최근
값을, 되돌리기면 전 차수가 null이라 자연히 null을 내려준다.

## 51. settlement_target이 하나도 없는 attendee는 CommonErrorCode.NOT_FOUND로 처리한다
API-040·API-041은 scheduleId·attendeeId로 그 사람의 일정 내 정산 대상을 찾는데, 그 일정에
정산 명단 자체가 없거나(API-038 저장 전) 그 사람이 명단에 없으면 조회 결과가 비어있다. 전용
에러 코드가 필요한지 구현 전에 확인했다(사용자 결정, 2026-11-15) — `SettlementErrorCode`의
기존 12개 코드 중에는 맞는 게 없고, 새 코드를 만들 이유도 없다(다른 모든 "존재하지 않는
리소스" 케이스와 같은 성격). 기존 `CommonErrorCode.NOT_FOUND`(COMMON_404)를 그대로 쓴다.

## 52. API-041(입금 명단 삭제)은 settlement_target만 지우고, 한 차수라도 PAID면 423으로 막는다
API-041은 "미입금 카드 스와이프 삭제하기"에서 온다. API-032(참가자 제외, #28)와 달리 출석
기록(`round_attendee`)은 건드리지 않고 **그 일정 내 모든 차수의 `settlement_target`만
삭제**한다(사용자 결정, 2026-11-15) — "입금 명단에서 뺀다"는 건 그 사람이 그 모임에 안 왔다는
뜻이 아니라 더 이상 돈을 걷지 않겠다는 뜻이라, 출석 사실과는 별개 결정이기 때문이다.

이미 `SettlementErrorCode.SETTLEMENT_423_PAID_CANNOT_DELETE`가 있어(#54 계산기 작업 때
함께 만든 12개 코드 중 하나, 구현 전 존재 확인함) 새 코드 없이 그대로 쓴다 — 한 차수라도 입금
완료 상태면 삭제 자체를 막는다. 통과하면 각 차수에서 그 사람의 `settlement_target`을 지우고
남은 인원으로 `BilledAmountCalculator`를 다시 호출한다. 어느 차수든 남은 인원이 0명이 되면
그 차수는 계산기를 호출하지 않는다(#31과 같은 이유). 응답은 기존 관례대로 200 +
`BaseResponse.success()`다(`DELETED`(204)는 쓰지 않는다).

## 53. API-042(입금 현황 조회)의 정렬·cursor 기준과 totalBilledAmount 집계 방식
API-042는 `settlement_target`이 아니라 **사람(schedule_attendee) 단위**로 묶어 보여주는
목록이라, 정렬·cursor 기준 컬럼을 구현 전에 확정했다(사용자 결정, 2026-11-15) —
`schedule_attendee.createdAt` 오름차순, 동일 시 `schedule_attendee.id` 오름차순을 쓴다.
`settlement_target.createdAt`은 같은 사람도 차수마다 달라 사람 단위 커서로 쓸 수 없고,
`schedule_attendee.createdAt`은 그 사람이 일정에 등록된 시점으로 모든 차수에서 값이 같아
안정적인 정렬·커서 기준이 된다.

`totalBilledAmount`는 그 사람의 일정 내 모든 차수 `billed_amount` 합산이고,
`paymentStatus`는 **모든 차수가 PAID일 때만 PAID**로 집계한다(한 차수라도 미납이면 UNPAID) —
AI 비서용 `SettlementStatusTool.aggregateByAttendee`가 이미 쓰고 있는 것과 같은 규칙이라
두 곳의 "입금 완료" 의미가 갈리지 않는다.

## 54. API-042는 `findByScheduleForAssistant`를 재사용하지 않고 전용 쿼리를 새로 만든다
구현 전에 `SettlementTargetRepositoryCustom.findByScheduleForAssistant`를 API-042가 재사용할
수 있는지 확인했다(사용자 결정, 2026-11-15) — **재사용하지 않는다.** 이유는 세 가지다.

1. 그 메서드는 AI 비서(`SettlementStatusTool`)가 이름순으로 그 일정 전체 차수를 한 번에
   불러와 메모리에서 집계하는 용도로 만들어졌다. 필터(status/keyword)·cursor 페이지네이션이
   전혀 없고, 시그니처를 바꾸면 이미 그 메서드에 의존하는 AI 도구의 동작이 깨진다.
2. API-042는 상태 필터(HAVING)·cursor를 DB 레벨에서 적용해야 커서가 안정적으로 동작하는데,
   이 메서드는 "전체를 가져와 메모리에서 거른다"는 전제라 그 구조에 맞지 않는다.
3. 정렬 기준도 다르다(`findByScheduleForAssistant`는 이름순·차수순, API-042는
   `schedule_attendee.createdAt`·id순 — #53).

대신 `SettlementTargetRepositoryCustom`에 `searchPaymentSummaries`를 새로 추가했다. QueryDSL
`groupBy(scheduleAttendee.id)` + `CaseBuilder` 기반 조건부 합산으로 사람 단위 미납 차수 수를
세어, `status=PAID`면 미납 차수 0건, `status=UNPAID`면 1건 이상을 `having`으로 거른다.
`findByScheduleForAssistant`는 전혀 수정하지 않았다.

## 55. API-044 선택 차수들의 계좌가 다를 때는 roundNo가 가장 작은 차수의 계좌를 대표로 쓴다
`docs/api-spec.md`에 "미확정"으로 남아 있던 항목이다(API-045 정산 홈의 대표 계좌 표시도 같은
문제로 묶여 있었다). 구현 전에 확인했다(사용자 결정, 2026-11-15) — 선택한 차수 중 **roundNo가
가장 작은 차수의 계좌**를 공지 전체의 대표 계좌로 쓴다.

요청 `roundIds` 배열에 온 순서를 그대로 쓰거나(배열 첫 번째) 계좌가 전부 같아야 한다고
강제하는(불일치 시 새 409) 대안도 검토했지만, 전자는 FE가 배열을 어떤 순서로 보내는지에 따라
결과가 달라져 결정적이지 않고, 후자는 지금 12개 코드 안에 맞는 게 없어 새 코드가 필요해 범위를
키운다. roundNo 기준은 요청 순서와 무관하게 항상 같은 결과가 나오고 기존 에러 코드만으로
충분하다. 구현은 선택한 차수를 `roundNo` 오름차순으로 정렬해 그 첫 번째 차수를 대표로 쓴다
— 이 정렬이 공지 문구의 차수별 나열 순서(#56)도 함께 고정한다.

## 56. noticeText 형식을 "차수별 나열 + 합산"으로 새로 정한다
`docs/prd.md`·`docs/api-spec.md` 어디에도 `noticeText`의 문구 형식이 없어 구현 전에 새로
정했다(사용자 결정, 2026-11-15).

```
[{scheduleTitle}] 정산 안내

{label1}: {billedAmount1}원
{label2}: {billedAmount2}원

총 입금액: {combinedAmount}원
{bankName} {accountNumber} ({accountHolder})

확인 후 입금해주세요!
```

차수는 roundNo 오름차순(#55)으로 나열하고, 금액은 천 단위 콤마를 넣어 표기한다(`String.format(
Locale.US, "%,d", amount)` — 서버 로케일에 따라 구분자가 달라지지 않도록 로케일을 명시로
고정했다). `billedAmount`·`combinedAmount` 등 구조화된 숫자 필드는 기존 관례대로 숫자 그대로
내려주고(§1 "금액은 숫자로 직렬화"), 콤마 포맷은 사람이 복사해 붙여넣는 `noticeText` 문자열
안에서만 적용한다 — 두 규칙은 서로 다른 필드를 가리켜 충돌하지 않는다.

## 57. 영수증은 등록됐지만 정산 명단이 없는 차수는 SETTLEMENT_409_UNREGISTERED_ROUND_INCLUDED로 거부한다
API-038(정산 명단 저장)을 아직 돌리지 않은 차수는 영수증(`hasReceipt()`)은 있어도
`settlement_target`이 하나도 없어 1인 청구액을 계산할 수 없다. 이런 차수를 API-044에 포함시키면
어떻게 처리할지 구현 전에 확인했다(사용자 결정, 2026-11-15) — **영수증 미등록과 같은
`SETTLEMENT_409_UNREGISTERED_ROUND_INCLUDED`로 거부**한다. 둘 다 "정산 준비가 안 끝난
차수"라는 같은 의미라 새 에러 코드를 만들지 않는다.

`billedAmount`를 0으로 채워 공지에 포함시키는 대안도 검토했지만, 실제로는 아직 금액이 확정되지
않은 차수를 "0원"으로 보여주면 오해를 줄 수 있어 거부 쪽을 택했다. 구현은
`SettlementTargetRepository.findFirstByScheduleRoundId`(API-037에서 쓰던 메서드, 그 차수
전원이 같은 금액이라 1건만 봐도 된다)가 비어있으면 이 코드로 던진다.

영수증은 등록됐지만 명단이 없는 경우에도 메시지가 "영수증이 등록되지 않은 차수"로 나가 상황과
다소 맞지 않음. FE가 서버 메시지를 그대로 노출하면 전용 코드 신설 검토.

## 58. API-044 roundIds는 decisions #49와 같은 기준으로 검증하고 중복을 제거한다
API-038(#49)에서 이미 정한 기준을 그대로 따른다(사용자 결정, 2026-11-15) — `roundIds`에 그
일정 소속이 아니거나 존재하지 않는 id가 섞이면 전용 코드 없이 `COMMON_400`으로 거부하고,
중복된 id는 `distinct()`로 정리한 뒤 처리한다. 같은 검증 로직이 두 API(API-038, API-044)에서
반복되지만, 검증 대상이 다른 엔티티(`schedule_attendee` vs `schedule_round`)라 공통 헬퍼로
묶지 않고 각 Service에 그대로 둔다.

## 59. API-045 progress는 사람 수 기준이고, 집계는 findByScheduleForAssistant를 재사용한다
`docs/api-spec.md`에 "미확정"으로 남아 있던 항목이다. 사람 수(입금 완료 인원/전체 인원) 단위와
차수 수(정산 완료 차수/전체 차수) 단위 중 구현 전에 확인했다(사용자 결정, 2026-11-15) —
**사람 수 기준**으로 정한다. 여러 차수에 걸친 사람은 그 사람의 일정 내 모든 차수가 PAID여야
`paidCount`에 포함한다(API-042의 사람 단위 집계, decisions #53과 같은 기준).

집계 쿼리는 `SettlementTargetRepositoryCustom.findByScheduleForAssistant(scheduleId, null)`을
그대로 재사용한다. #54에서 API-042가 이 메서드를 재사용하지 않기로 한 이유(필터·cursor
페이지네이션이 필요해 구조가 안 맞음)가 API-045에는 없다 — API-045의 progress는 필터·페이지
없이 그 일정 전체를 한 번에 집계하면 되는 값이라, 원래 AI 비서용으로 만들어진 "일정 전체를
한 번에 가져오는" 동작과 정확히 일치한다. 새 쿼리를 추가하지 않았다.

## 60. API-045 상위 status와 정산 명단이 없는 차수의 처리
상위 `status`(`"정산 완료"`/`"정산중"`) 판단 기준과, 정산 명단(`settlement_target`)이 0명인
차수의 `settlementStatus`를 구현 전에 확인했다(사용자 결정, 2026-11-15).

- **상위 status**: `progress.totalCount > 0 && progress.paidCount == progress.totalCount`면
  `"정산 완료"`, 그 외(전체 인원 0명 포함)는 `"정산중"`이다. 구현은 `rate == 100`이 아니라
  `paidCount == totalCount`로 직접 비교한다 — `rate`는 `Math.round`로 반올림하기 때문에
  (예: 199/200 = 99.5% → `Math.round`로 100%) 인원수 비교 없이 `rate`만 보면 실제로는 미납이
  남았는데도 100%로 반올림되는 경우를 "완료"로 잘못 판단할 수 있다.
- **명단 0명 차수**: `"정산중"`으로 처리한다. "그 차수 전원이 PAID"라는 규칙을 문자 그대로
  적용하면 명단이 빈 차수는 공집합이 "전원 PAID"를 공집합 특성상 자동으로 만족해 "정산
  완료"가 되는데, 이는 아직 정산 명단조차 만들지 않은(API-038 미실행) 차수를 완료로 보여주는
  역설이라 피한다.

## 61. API-045 receipts[]는 영수증이 등록된 차수만 포함하고, progress·status는 일정 전체 정산 대상 기준으로 계산한다
영수증 미등록 차수를 `receipts[]`에 포함할지 구현 전에 확인했다(사용자 결정, 2026-11-15) —
**영수증이 등록된 차수만** 포함한다. `storeName`·`totalAmount`가 전부 영수증 등록 시에만
채워지는 값이라, 미등록 차수를 섞으면 응답에 null 항목이 생겨 FE가 따로 분기해야 한다.

`progress`(#59)·상위 `status`(#60)는 `receipts[]`와 달리 영수증 등록 여부로 거르지 않고, 그
일정의 `settlement_target` 전체를 그대로 집계한다 — API-042의 `paymentStatus`와 같은
범위다(둘 다 영수증 등록 여부를 걸러내는 조건이 없다). `settlementTarget.scheduleAttendee.
schedule.id`(API-042가 쓰는 집계 조건)와 `settlementTarget.scheduleRound.schedule.id`
(차수 기준)는 조건식 자체는 다르지만, API-038이 저장 시점에 `attendee.schedule ==
round.schedule`을 항상 검증하므로 실제 데이터에서는 두 조건이 다른 결과를 내지 않는다.

## 62. API-045 대표 계좌는 decisions #55와 같은 기준이고, 계좌가 하나도 없으면 account는 null이다
API-044(#55)에서 정한 "계좌가 설정된 차수 중 roundNo가 가장 작은 차수의 계좌" 규칙을 그대로
적용한다(사용자 결정, 2026-11-15). API-044는 선택 차수 전부에 계좌가 있어야 409로 통과시키지만,
API-045는 조회 전용이라 그런 검증이 없다 — 그래서 후보를 "계좌가 설정된 차수"로 먼저 거른
뒤 그중 roundNo가 가장 작은 차수를 고르고, 계좌가 설정된 차수가 하나도 없으면 `account`를
`null`로 내려준다(API-043으로 계좌를 설정하기 전 기본 상태와 동일— 에러로 막지 않는다).

## 63. 장부 자동기록은 차수 단위가 아니라 일정 단위로 합산한다 — decisions #2를 대체한다
`ledger_entry`의 자동기록 기준을 구현 전에 다시 확인했다(사용자 결정, 2026-11-16) — **#2("차수
단위 1건")를 대체**하고, **일정(`schedule`) 단위로 최대 4줄**을 생성한다: `RECEIPT_EXPENSE`(영수증
지출, 지원금 제외), `CLUB_SUBSIDY_EXPENSE`(동아리 지원금 지출), `ROUNDING_SUBSIDY_EXPENSE`(100원
버림 차액 "추가 동아리 지원금" 지출), `PAYMENT_INCOME`(부원 납부 수입). 지출 3줄과 수입 1줄 모두
**API-074(정산 내역 새로고침) 시점에 생성**한다 — API-040(입금 확인) 등 다른 트리거는 두지 않는다.

같은 일정에서 이 4종이 각각 최대 1건만 존재해야 해서 `ledger_entry`에 `auto_category`(enum:
`LedgerAutoCategory`, 직접 입력이면 null) 컬럼을 추가하고 `UNIQUE(schedule_id, auto_category)`
제약을 건다. `type`(수입/지출)만으로는 같은 일정의 EXPENSE 3종을 구분할 수 없어 별도 컬럼이
필요했다. 마이그레이션은 `V6__ledger_entry_auto_category.sql`.

## 64. 자동기록의 보존 앵커가 schedule_round_id에서 schedule_id로 바뀐다 — decisions #4 메커니즘 변경
#4("영수증 삭제 시 schedule_round_id 참조만 NULL로 전환해 장부 원본을 보존한다")는 "차수 단위"
설계를 전제로 한 메커니즘이었다. #63으로 자동기록이 일정 단위가 되면서, 새 자동기록은 처음부터
`schedule_round_id`를 채우지 않고(항상 null) `schedule_id`로만 연결된다 — 그래서 **보존의
앵커가 `schedule_id`로 옮겨간다**(사용자 결정, 2026-11-16). "장부 원본은 보존한다"는 #4의
원칙 자체는 바뀌지 않는다.

한 일정에 차수가 여러 개 있고 그중 하나의 영수증만 삭제되는 경우, 이미 일정 단위로 합산해 만든
4줄의 금액이 더 이상 실제와 맞지 않게 된다. 이런 **사후 변경에 따른 장부 재동기화는 데모 범위에서
제외**한다(이슈 #120 1번 "기획 확정 사항"과 같은 결정) — 필요하면 운영진이 API-074를 다시 호출해
전체를 재계산하는 것으로 충분하다고 본다.

## 65. API-069 source 판별은 schedule_round_id 대신 auto_category로 한다
`docs/api-spec.md` API-069의 `source`(RECEIPT|MANUAL) 판별 기준("schedule_round_id 있으면
RECEIPT")은 #63 이후로 더 이상 성립하지 않는다 — 자동기록도 이 컬럼이 항상 null이기 때문이다.
**`auto_category IS NOT NULL`이면 자동기록(RECEIPT), null이면 MANUAL**로 판별하기로 확정했다
(사용자 결정, 2026-11-16). 이번 이슈는 스키마·enum 기반만 다루므로 실제 구현은 API-069를
구현하는 이슈에서 한다 — 여기서는 기준만 정해 둔다.

## 66. API-071 origin·API-072/073 "원본"의 기준이 일정으로 바뀌고, 일정 삭제 시 참조를 함께 끊는다
#63으로 자동기록이 일정 단위가 되면서, API-071의 `origin`(자동기록 출처 상세)과 API-072/073의
"원본이 살아있는지" 판단 기준이 **차수(`schedule_round`)에서 일정(`schedule`)으로 바뀐다**(사용자
결정, 2026-11-16). API-073의 기존 문구가 말하는 "원본 차수가 삭제되어 고아가 된 자동기록"이라는
시나리오는 더 이상 성립하지 않는다 — 차수 하나를 지워도 `schedule_id`는 영향받지 않기 때문이다.
고아가 되는 시점은 **그 일정 자체가 삭제될 때**로 바뀐다.

이 결정에 맞춰 `LedgerEntryRepository.clearScheduleReference`(일정 삭제 시 호출)가
`e.scheduleRound`만 null로 바꾸던 것을 **`e.schedule`도 함께 null로 바꾸도록** 수정했다. WHERE
조건도 `e.schedule.id = :scheduleId`를 추가했다 — 기존 조건(`e.scheduleRound.id in (그 일정의
차수들)`)만으로는 새 설계의 자동기록(처음부터 `scheduleRound`가 null)을 전혀 찾지 못해 수정이
공회전하기 때문이다. 기존 조건은 과거 "차수 단위" 설계에서 생겼을 수 있는 레거시 데이터를 대비해
그대로 남겼다. `ScheduleCascadeDeleter`(schedule 도메인) 등 다른 파트 파일은 수정하지 않았다 —
이미 `deleteChildren`이 `clearScheduleReference`를 호출하고 있어 그대로 두면 된다.

## 67. API-070~073 구현 전 미확정 사항 4건
`docs/api-spec.md`에 명시되지 않은 세부 사항을 구현 전에 확인했다(사용자 결정, 2026-11-15).

- **API-072 PATCH 방식**: 보낸 필드만 반영하는 **부분 수정**(null이면 기존 값 유지)이다.
  `Schedule.update()`와 같은 관례다. `type`(INCOME/EXPENSE) 변경도 **허용**한다 — 자동 기록
  보호는 이미 `LEDGER_409_AUTO_ENTRY_LOCKED`로 막혀 있어 직접 입력 내역의 `type` 변경을 추가로
  제한할 이유가 없다.
- **API-071 origin의 고아 표현**: 자동 기록이면서 출처 일정(`schedule`)이 살아있을 때만 `origin`
  (`scheduleId`, `title`)을 채우고, **직접 입력과 고아가 된 자동 기록은 둘 다 `origin = null`**
  이다. `source`(RECEIPT|MANUAL)로 이미 "자동 기록이었는지"를 구분할 수 있어, `origin`에 고아
  여부를 나타내는 별도 플래그를 두지 않았다.
- **필수값 누락·금액 0 이하 검증**: Service가 아니라 **요청 DTO의 Bean Validation**
  (`@NotNull`/`@NotBlank`/`@Positive`)으로 검증한다 — `settlement`의 `AccountSetRequestTest`와
  같은 관례로, Spring 컨텍스트 없이 `jakarta.validation.Validator`만으로 검증하는 별도 DTO
  테스트를 둔다. `LedgerEntryUpdateRequest`(API-072, 부분 수정)는 전부 선택 필드라 `@NotNull`을
  달지 않고, `amount`는 `@Positive`만(null은 Bean Validation 표준 동작대로 통과), `itemName`은
  null은 허용하되 공백·빈 문자열은 `@Pattern(regexp = ".*\\S.*")`로 막는다(`@NotBlank`는 null도
  위반으로 처리해 "생략" 의미를 낼 수 없어 쓰지 않았다).
- **존재하지 않는 termId·entryId**: 다른 도메인과 같은 관례로 `CommonErrorCode.NOT_FOUND`
  (COMMON_404)를 쓴다. 전용 코드를 만들지 않았다.

## 68. API-069 totalIncome·totalExpense는 그룹 전체 누적이고, cursor·테스트는 기존 관례를 그대로 쓴다
`docs/api-spec.md`가 미확정으로 남겨 둔 집계 범위를 구현 전에 확인했다(사용자 결정,
2026-11-15).

- **totalIncome·totalExpense 집계 범위**: `currentBalance`와 같은 **그룹 전체(모든 기수) 누적**
  이다. `LedgerEntryRepositoryCustom.aggregateByTypeForGroup(groupId)`(기존 `aggregateByType`의
  where 조건만 `groupTerm.id` 대신 `groupTerm.group.id`로 바꾼 버전, 내부 Tuple→
  `LedgerTypeTotal` 매핑은 `aggregateByTypeWhere` private 메서드로 공유)의 결과 하나로 세
  필드를 전부 만든다 — `currentBalance = totalIncome - totalExpense`라 별도 집계 쿼리가
  필요 없다.
- **cursor 정렬·동점 처리**: `entryDate desc, id desc` + `lt()` 복합 조건. 내림차순 cursor를
  쓰는 다른 도메인(`MeetingRepositoryImpl`, `FileRepositoryImpl`, `OperationNoteRepositoryImpl`
  등)과 동일한 패턴이라 새로 정하지 않고 그대로 따랐다. cursor 인코딩도 `CursorCodec`을
  그대로 재사용하고, sortValue는 `MeetingService`와 같이 `LocalDate.toString()`(ISO-8601)으로
  직렬화한다.
- **테스트 분담**: DB 집계·정렬·필터처럼 Mockito로는 검증할 수 없는 로직은
  `LedgerEntryRepositoryImplTest`(Testcontainers, 기존 관례)에 추가했다 — 그룹 전체 누적(다른
  기수 포함)·다른 그룹 미포함, `entries`의 다른 기수 미포함·type 필터·cursor 이후 항목만
  반환·같은 날짜 내역의 id 내림차순·limit 적용을 검증한다. `BudgetServiceTest`(Mockito)는
  존재하지 않는 기수(COMMON_404), 잘못된 cursor·type(COMMON_400), source 매핑, ledgerFile
  유무, nextCursor 생성 여부처럼 레포지토리를 모킹해도 되는 로직만 검증한다.
