ALTER TABLE edu_enrollment ADD COLUMN renewal_from_id VARCHAR(32) NULL,
    ADD UNIQUE KEY uk_edu_enrollment_student (organization_id,student_id,id);
-- MySQL must see the referenced self-table index before adding the constraint.
ALTER TABLE edu_enrollment ADD CONSTRAINT fk_edu_renewal_student FOREIGN KEY (organization_id,student_id,renewal_from_id)
        REFERENCES edu_enrollment (organization_id,student_id,id);
