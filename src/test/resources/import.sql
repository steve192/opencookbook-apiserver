-- Run by Hibernate after it creates the schema from the entities. In production V38 inserts this row.
INSERT INTO instance_settings (id, signup_mode) VALUES (1, 'OPEN');
