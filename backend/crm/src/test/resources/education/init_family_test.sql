-- Synthetic fixtures only; these IDs are reserved for this test class.
INSERT INTO customer (id, name, owner, create_time, update_time, create_user, update_user, organization_id, in_shared_pool)
VALUES ('edu-test-family', 'Synthetic Family', 'admin', 1, 1, 'admin', 'admin', '100001', 0),
       ('edu-test-foreign', 'Synthetic Foreign Family', 'admin', 1, 1, 'admin', 'admin', 'edu-test-other-org', 0),
       ('edu-test-pool', 'Synthetic Pool Family', 'admin', 1, 1, 'admin', 'admin', '100001', 1);
INSERT INTO customer_contact (id, customer_id, name, owner, create_time, update_time, create_user, update_user, organization_id, enable)
VALUES ('edu-test-parent', 'edu-test-family', 'Synthetic Guardian', 'admin', 1, 1, 'admin', 'admin', '100001', 1);
