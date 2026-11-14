-- 정산 명단 저장(API-038)이 같은 차수·참가자 조합으로 중복 행을 만들 수 있어 DB 레벨로도 막는다.
ALTER TABLE settlement_target
    ADD CONSTRAINT uk_settlement_target_round_attendee UNIQUE (schedule_round_id, schedule_attendee_id);