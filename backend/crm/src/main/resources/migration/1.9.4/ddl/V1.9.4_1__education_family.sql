-- Education records extend existing CRM customers/contacts; names are never relational keys.
CREATE TABLE edu_student (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(32) NOT NULL,
    customer_id VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    grade VARCHAR(50) NULL,
    create_user VARCHAR(32) NOT NULL,
    update_user VARCHAR(32) NOT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    UNIQUE KEY uk_edu_student_org_id (organization_id, id),
    KEY idx_edu_student_family (organization_id, customer_id),
    CONSTRAINT fk_edu_student_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE edu_guardian (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(32) NOT NULL,
    student_id VARCHAR(32) NOT NULL,
    contact_id VARCHAR(32) NOT NULL,
    relationship VARCHAR(30) NOT NULL,
    create_user VARCHAR(32) NOT NULL,
    update_user VARCHAR(32) NOT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    UNIQUE KEY uk_edu_guardian_link (organization_id, student_id, contact_id),
    CONSTRAINT fk_edu_guardian_student FOREIGN KEY (organization_id, student_id)
        REFERENCES edu_student (organization_id, id),
    CONSTRAINT fk_edu_guardian_contact FOREIGN KEY (contact_id) REFERENCES customer_contact (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
