-- Keep native reassignment/merge/delete operations from orphaning or silently moving child relationships.
ALTER TABLE customer ADD UNIQUE KEY uk_edu_customer_org (organization_id, id);
ALTER TABLE customer_contact ADD UNIQUE KEY uk_edu_contact_family (organization_id, customer_id, id);
ALTER TABLE edu_student ADD UNIQUE KEY uk_edu_student_family (organization_id, customer_id, id),
    ADD CONSTRAINT fk_edu_student_family FOREIGN KEY (organization_id, customer_id)
        REFERENCES customer (organization_id, id);
ALTER TABLE edu_guardian ADD COLUMN customer_id VARCHAR(32) NULL;
UPDATE edu_guardian g JOIN edu_student s ON s.id = g.student_id SET g.customer_id = s.customer_id;
ALTER TABLE edu_guardian MODIFY customer_id VARCHAR(32) NOT NULL,
    ADD CONSTRAINT fk_edu_guardian_family_student FOREIGN KEY (organization_id, customer_id, student_id)
        REFERENCES edu_student (organization_id, customer_id, id),
    ADD CONSTRAINT fk_edu_guardian_family_contact FOREIGN KEY (organization_id, customer_id, contact_id)
        REFERENCES customer_contact (organization_id, customer_id, id);
