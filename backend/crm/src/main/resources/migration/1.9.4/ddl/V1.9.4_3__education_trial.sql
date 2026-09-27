CREATE TABLE edu_trial (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(32) NOT NULL,
    student_id VARCHAR(32) NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    starts_at BIGINT NOT NULL,
    ends_at BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    outcome VARCHAR(2000) NULL,
    create_user VARCHAR(32) NOT NULL,
    update_user VARCHAR(32) NOT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    UNIQUE KEY uk_edu_trial_request (organization_id, student_id, request_key),
    CONSTRAINT fk_edu_trial_student FOREIGN KEY (organization_id, student_id) REFERENCES edu_student (organization_id, id),
    CONSTRAINT ck_edu_trial_time CHECK (ends_at > starts_at),
    CONSTRAINT ck_edu_trial_status CHECK (status IN ('BOOKED', 'ATTENDED', 'NO_SHOW', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
