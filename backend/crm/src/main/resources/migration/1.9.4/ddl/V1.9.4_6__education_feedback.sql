CREATE TABLE edu_feedback (
    id VARCHAR(32) PRIMARY KEY, organization_id VARCHAR(32) NOT NULL, session_id VARCHAR(32) NOT NULL,
    enrollment_id VARCHAR(32) NOT NULL, request_key VARCHAR(64) NOT NULL, previous_id VARCHAR(32) NULL,
    revision INT NOT NULL, content TEXT NOT NULL, status VARCHAR(20) NOT NULL,
    confirmed_user VARCHAR(32) NULL, confirmed_time BIGINT NULL, recipient_contact_id VARCHAR(32) NULL,
    sent_time BIGINT NULL, send_evidence VARCHAR(1000) NULL, consent_reference VARCHAR(1000) NULL,
    create_user VARCHAR(32) NOT NULL, update_user VARCHAR(32) NOT NULL, create_time BIGINT NOT NULL, update_time BIGINT NOT NULL,
    UNIQUE KEY uk_edu_feedback_revision (organization_id,session_id,enrollment_id,revision),
    UNIQUE KEY uk_edu_feedback_request (organization_id,request_key),
    FOREIGN KEY (organization_id,session_id) REFERENCES edu_session (organization_id,id),
    FOREIGN KEY (organization_id,enrollment_id) REFERENCES edu_enrollment (organization_id,id),
    FOREIGN KEY (recipient_contact_id) REFERENCES customer_contact (id),
    CHECK (status IN ('DRAFT','CONFIRMED','MANUAL_REPORTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
