ALTER TABLE sales_order ADD UNIQUE KEY uk_edu_order_family (organization_id, customer_id, id);
ALTER TABLE contract_payment_record ADD UNIQUE KEY uk_edu_receipt_evidence (organization_id, id, contract_id, record_amount);
ALTER TABLE contract ADD UNIQUE KEY uk_edu_contract_family (organization_id,customer_id,id);
CREATE TABLE edu_enrollment (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(32) NOT NULL,
    customer_id VARCHAR(32) NOT NULL,
    student_id VARCHAR(32) NOT NULL,
    order_id VARCHAR(32) NOT NULL,
    sub_field_id VARCHAR(32) NOT NULL,
    order_row_id VARCHAR(32) NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    title VARCHAR(100) NOT NULL,
    mode VARCHAR(10) NOT NULL,
    total_units DECIMAL(10,2) NOT NULL,
    amount DECIMAL(16,2) NOT NULL,
    contract_id VARCHAR(32) NULL,
    seller_id VARCHAR(32) NOT NULL,
    row_snapshot LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    handoff_user VARCHAR(32) NULL,
    handoff_time BIGINT NULL,
    create_user VARCHAR(32) NOT NULL, update_user VARCHAR(32) NOT NULL,
    create_time BIGINT NOT NULL, update_time BIGINT NOT NULL,
    UNIQUE KEY uk_edu_enrollment_org (organization_id,id),
    UNIQUE KEY uk_edu_enrollment_request (organization_id,student_id,request_key),
    UNIQUE KEY uk_edu_enrollment_row (organization_id,order_id,sub_field_id,order_row_id),
    CONSTRAINT fk_edu_enrollment_student FOREIGN KEY (organization_id,customer_id,student_id) REFERENCES edu_student (organization_id,customer_id,id),
    CONSTRAINT fk_edu_enrollment_order FOREIGN KEY (organization_id,customer_id,order_id) REFERENCES sales_order (organization_id,customer_id,id),
    CONSTRAINT fk_edu_enrollment_contract FOREIGN KEY (organization_id,customer_id,contract_id) REFERENCES contract (organization_id,customer_id,id),
    CONSTRAINT ck_edu_enrollment_units CHECK ((mode='HOURS' AND total_units>0) OR (mode='EVENT' AND total_units=1)),
    CONSTRAINT ck_edu_enrollment_amount CHECK (amount>=0),
    CONSTRAINT ck_edu_enrollment_status CHECK (status IN ('PENDING','ACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE edu_payment_allocation (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(32) NOT NULL,
    enrollment_id VARCHAR(32) NOT NULL,
    receipt_id VARCHAR(32) NOT NULL,
    contract_id VARCHAR(32) NOT NULL,
    receipt_total DECIMAL(20,10) NOT NULL,
    amount DECIMAL(16,2) NOT NULL,
    create_user VARCHAR(32) NOT NULL, update_user VARCHAR(32) NOT NULL,
    create_time BIGINT NOT NULL, update_time BIGINT NOT NULL,
    UNIQUE KEY uk_edu_allocation (organization_id,enrollment_id,receipt_id),
    CONSTRAINT fk_edu_allocation_enrollment FOREIGN KEY (organization_id,enrollment_id) REFERENCES edu_enrollment (organization_id,id),
    -- An allocated receipt cannot be silently re-priced or moved to another contract by native CRM operations.
    CONSTRAINT fk_edu_allocation_receipt FOREIGN KEY (organization_id,receipt_id,contract_id,receipt_total)
        REFERENCES contract_payment_record (organization_id,id,contract_id,record_amount),
    CONSTRAINT ck_edu_allocation_amount CHECK (amount>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
