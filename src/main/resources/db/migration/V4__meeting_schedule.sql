-- 회의록이 일정과 엮이는 경로가 둘로 갈린다. 일정 상세에서 만든 회의록은 그 일정에 통째로 속하고,
-- 그 외 회의록의 안건은 schedule_agenda_link로 하나씩만 연결된다.
-- NULL이면 일정과 무관한 회의록, NOT NULL이면 그 일정에 속한 회의록(일정 삭제 시 함께 삭제).
ALTER TABLE meeting
    ADD COLUMN schedule_id BIGINT NULL AFTER group_term_id,
    ADD CONSTRAINT fk_meeting_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id);
