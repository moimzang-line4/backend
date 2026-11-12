-- AI 답변 아래의 화면 이동 버튼이 가리킬 대상을 저장한다.
-- 메시지 목록 조회에서도 같은 버튼이 보여야 해서 응답에만 두지 않고 메시지에 함께 남긴다.
-- target_schedule_id는 일정만 가리킬 수 있어 회의록·자료를 담지 못하므로 아래 두 컬럼으로 대체한다.
ALTER TABLE ai_message DROP FOREIGN KEY fk_ai_message_target_schedule;
ALTER TABLE ai_message DROP COLUMN target_schedule_id;

-- navigation_target_id에는 FK를 걸지 않는다 — navigation_type에 따라 schedule·meeting 중
-- 서로 다른 테이블을 가리키는 다형 참조라 하나의 FK로 묶을 수 없다.
-- 값은 도구가 실제로 조회에 성공한 ID만 들어가므로 저장 시점에 존재가 이미 검증돼 있다.
ALTER TABLE ai_message ADD COLUMN navigation_type VARCHAR(20) NULL AFTER ai_chat_room_id;
ALTER TABLE ai_message ADD COLUMN navigation_target_id BIGINT NULL AFTER navigation_type;
