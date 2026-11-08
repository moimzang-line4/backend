-- 모임장 초기 스키마. 18개 테이블을 FK 의존 순서대로 생성한다.

CREATE TABLE club_group (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE group_term (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    group_id   BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_group_term_group FOREIGN KEY (group_id) REFERENCES club_group (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE schedule_category (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    group_term_id BIGINT      NOT NULL,
    name          VARCHAR(50) NOT NULL,
    created_at    DATETIME    NOT NULL,
    updated_at    DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_category_group_term FOREIGN KEY (group_term_id) REFERENCES group_term (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE schedule (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    group_term_id         BIGINT       NOT NULL,
    schedule_category_id  BIGINT       NULL,
    title                 VARCHAR(100) NOT NULL,
    color                 VARCHAR(20)  NOT NULL,
    is_all_day            BOOLEAN      NOT NULL DEFAULT FALSE,
    start_at              DATETIME     NOT NULL,
    end_at                DATETIME     NOT NULL,
    location              VARCHAR(255) NULL,
    has_operation_space   BOOLEAN      NOT NULL DEFAULT FALSE,
    -- 과거 다른 테이블 상태값 기본값이 잘못 들어간 적이 있어 defaultValue를 비워둔다
    memo                  TEXT         NULL,
    created_at            DATETIME     NOT NULL,
    updated_at            DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_group_term FOREIGN KEY (group_term_id) REFERENCES group_term (id),
    CONSTRAINT fk_schedule_category FOREIGN KEY (schedule_category_id) REFERENCES schedule_category (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE member_list (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    group_term_id       BIGINT       NOT NULL,
    name                VARCHAR(100) NOT NULL,
    register_deadline   DATETIME     NULL,
    collect_school      BOOLEAN      NOT NULL DEFAULT TRUE,
    collect_department  BOOLEAN      NOT NULL DEFAULT TRUE,
    collect_phone       BOOLEAN      NOT NULL DEFAULT TRUE,
    collect_student_id  BOOLEAN      NOT NULL DEFAULT TRUE,
    collect_part        BOOLEAN      NOT NULL DEFAULT TRUE,
    collect_grade       BOOLEAN      NOT NULL DEFAULT TRUE,
    status              VARCHAR(20)  NOT NULL DEFAULT 'RECRUITING',
    join_token          VARCHAR(64)  NOT NULL,
    is_link_closed      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          DATETIME     NOT NULL,
    updated_at          DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_list_join_token (join_token),
    CONSTRAINT fk_member_list_group_term FOREIGN KEY (group_term_id) REFERENCES group_term (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE member (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    member_list_id   BIGINT       NOT NULL,
    name             VARCHAR(50)  NOT NULL,
    school           VARCHAR(100) NULL,
    department       VARCHAR(100) NOT NULL,
    phone            VARCHAR(20)  NULL,
    student_id       VARCHAR(30)  NULL,
    part             VARCHAR(50)  NULL,
    grade            VARCHAR(20)  NULL,
    privacy_consent  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_member_member_list FOREIGN KEY (member_list_id) REFERENCES member_list (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE schedule_attendee (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    schedule_id   BIGINT       NOT NULL,
    member_id     BIGINT       NULL,
    name          VARCHAR(50)  NOT NULL,
    affiliation   VARCHAR(100) NULL,
    phone_last4   VARCHAR(4)   NULL,
    source        VARCHAR(20)  NOT NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_attendee_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id),
    CONSTRAINT fk_schedule_attendee_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE schedule_round (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    schedule_id           BIGINT       NOT NULL,
    round_no              INT          NOT NULL,
    label                 VARCHAR(20)  NULL,
    store_name            VARCHAR(100) NULL,
    transacted_at         DATETIME     NULL,
    total_amount          INT          NULL,
    club_subsidy_amount   INT          NULL DEFAULT 0,
    image_url             VARCHAR(500) NULL,
    bank_name             VARCHAR(50)  NULL,
    account_number        VARCHAR(50)  NULL,
    account_holder        VARCHAR(50)  NULL,
    created_at            DATETIME     NOT NULL,
    updated_at            DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_round_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE round_attendee (
    id                     BIGINT   NOT NULL AUTO_INCREMENT,
    schedule_round_id      BIGINT   NOT NULL,
    schedule_attendee_id   BIGINT   NOT NULL,
    is_checked_in          BOOLEAN  NOT NULL DEFAULT FALSE,
    checked_in_at          DATETIME NULL,
    created_at             DATETIME NOT NULL,
    updated_at             DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_round_attendee_round_attendee (schedule_round_id, schedule_attendee_id),
    CONSTRAINT fk_round_attendee_schedule_round FOREIGN KEY (schedule_round_id) REFERENCES schedule_round (id),
    CONSTRAINT fk_round_attendee_schedule_attendee FOREIGN KEY (schedule_attendee_id) REFERENCES schedule_attendee (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE settlement_target (
    id                     BIGINT      NOT NULL AUTO_INCREMENT,
    schedule_round_id      BIGINT      NOT NULL,
    schedule_attendee_id   BIGINT      NOT NULL,
    billed_amount          INT         NOT NULL,
    payment_status         VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    paid_at                DATETIME    NULL,
    created_at             DATETIME    NOT NULL,
    updated_at             DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_settlement_target_schedule_round FOREIGN KEY (schedule_round_id) REFERENCES schedule_round (id),
    CONSTRAINT fk_settlement_target_schedule_attendee FOREIGN KEY (schedule_attendee_id) REFERENCES schedule_attendee (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE operation_note (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    schedule_id    BIGINT       NOT NULL,
    type           VARCHAR(20)  NOT NULL,
    title          VARCHAR(100) NOT NULL,
    assignee       VARCHAR(50)  NULL,
    content        VARCHAR(100) NOT NULL,
    is_todo_done   BOOLEAN      NULL,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_operation_note_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE meeting (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    group_term_id          BIGINT       NOT NULL,
    schedule_category_id   BIGINT       NOT NULL,
    title                  VARCHAR(100) NOT NULL,
    meeting_date           DATE         NULL,
    attendees              VARCHAR(500) NULL,
    summary_content        TEXT         NULL,
    summary_generated_at   DATETIME     NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
    ended_at               DATETIME     NULL,
    list_summary           VARCHAR(100) NULL,
    created_at             DATETIME     NOT NULL,
    updated_at             DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_meeting_group_term FOREIGN KEY (group_term_id) REFERENCES group_term (id),
    CONSTRAINT fk_meeting_schedule_category FOREIGN KEY (schedule_category_id) REFERENCES schedule_category (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE meeting_agenda (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    meeting_id        BIGINT       NOT NULL,
    content           VARCHAR(100) NOT NULL,
    discussion_note   TEXT         NULL,
    decision          TEXT         NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    order_no          INT          NOT NULL DEFAULT 0,
    created_at        DATETIME     NOT NULL,
    updated_at        DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_meeting_agenda_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- updated_at·소프트 삭제 컬럼 없음(연결 해제는 row 물리 삭제로 처리)
CREATE TABLE schedule_agenda_link (
    id                   BIGINT   NOT NULL AUTO_INCREMENT,
    schedule_id          BIGINT   NOT NULL,
    meeting_agenda_id    BIGINT   NOT NULL,
    created_at           DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_agenda_link_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id),
    CONSTRAINT fk_schedule_agenda_link_meeting_agenda FOREIGN KEY (meeting_agenda_id) REFERENCES meeting_agenda (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE file (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    group_term_id  BIGINT       NOT NULL,
    schedule_id    BIGINT       NULL,
    name           VARCHAR(255) NOT NULL,
    extension      VARCHAR(20)  NOT NULL,
    size           BIGINT       NOT NULL,
    category       VARCHAR(20)  NOT NULL,
    storage_key    VARCHAR(500) NOT NULL,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    deleted_at     DATETIME     NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_file_group_term FOREIGN KEY (group_term_id) REFERENCES group_term (id),
    CONSTRAINT fk_file_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_chat_room (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    group_term_id  BIGINT       NOT NULL,
    title          VARCHAR(100) NULL,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_chat_room_group_term FOREIGN KEY (group_term_id) REFERENCES group_term (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- updated_at 컬럼 없음(메시지는 생성 후 수정되지 않음)
CREATE TABLE ai_message (
    id                    BIGINT      NOT NULL AUTO_INCREMENT,
    ai_chat_room_id       BIGINT      NOT NULL,
    target_schedule_id    BIGINT      NULL,
    role                  VARCHAR(20) NOT NULL,
    content               TEXT        NOT NULL,
    card_type             VARCHAR(50) NULL,
    content_json          JSON        NULL,
    created_at            DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_message_ai_chat_room FOREIGN KEY (ai_chat_room_id) REFERENCES ai_chat_room (id),
    CONSTRAINT fk_ai_message_target_schedule FOREIGN KEY (target_schedule_id) REFERENCES schedule (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ledger_entry (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    group_term_id       BIGINT       NOT NULL,
    schedule_round_id   BIGINT       NULL,
    type                VARCHAR(10)  NOT NULL,
    item_name           VARCHAR(100) NOT NULL,
    amount              INT          NOT NULL,
    entry_date          DATE         NOT NULL,
    created_at          DATETIME     NOT NULL,
    updated_at          DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ledger_entry_group_term FOREIGN KEY (group_term_id) REFERENCES group_term (id),
    CONSTRAINT fk_ledger_entry_schedule_round FOREIGN KEY (schedule_round_id) REFERENCES schedule_round (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
