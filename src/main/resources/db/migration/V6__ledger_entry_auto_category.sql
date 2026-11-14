-- 자동기록은 차수(schedule_round) 단위가 아니라 일정(schedule) 단위로 합산해 생성한다. auto_category는
-- 같은 일정에서 생기는 최대 3종 지출(영수증 지출/동아리 지원금 지출/추가 지원금 지출) + 1종 수입
-- (부원 납부 수입) 자동기록을 구분한다. 직접 입력(API-070)은 schedule_id·auto_category 모두 NULL이다.
-- schedule_round_id는 과거 "차수 단위" 설계에서 쓰던 컬럼으로, 새 자동기록은 이 값을 채우지 않는다.
ALTER TABLE ledger_entry
    ADD COLUMN schedule_id BIGINT NULL AFTER group_term_id,
    ADD COLUMN auto_category VARCHAR(30) NULL AFTER type,
    ADD CONSTRAINT fk_ledger_entry_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id),
    ADD CONSTRAINT uk_ledger_entry_schedule_auto_category UNIQUE (schedule_id, auto_category);
