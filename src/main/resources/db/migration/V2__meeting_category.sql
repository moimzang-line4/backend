-- 회의 카테고리를 일정 카테고리와 분리 운영하기로 한 결정(docs/decisions.md #21) 반영.
-- schedule_category는 그대로 두고 일정 전용으로 남긴다.
CREATE TABLE meeting_category (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    group_term_id BIGINT      NOT NULL,
    name          VARCHAR(50) NOT NULL,
    created_at    DATETIME    NOT NULL,
    updated_at    DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_meeting_category_group_term FOREIGN KEY (group_term_id) REFERENCES group_term (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE meeting
    DROP FOREIGN KEY fk_meeting_schedule_category,
    CHANGE COLUMN schedule_category_id meeting_category_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_meeting_category FOREIGN KEY (meeting_category_id) REFERENCES meeting_category (id);
