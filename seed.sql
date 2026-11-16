USE relaybook;
DELETE FROM contact_methods;
DELETE FROM contacts;

INSERT INTO contacts (id, contact_type, full_name, organization, relationship_label, preferred_channel, best_time, time_zone, notes, last_verified, share_consent)
VALUES
('demo-mira-sen', 'PERSON', 'Mira Sen', NULL, 'Friend', 'PHONE', 'Weekdays after 6 pm', 'Asia/Kolkata', 'Synthetic sample record.', CURDATE() - INTERVAL 42 DAY, FALSE),
('demo-dev-rao', 'EMERGENCY', 'Dev Rao', NULL, 'Emergency contact', 'PHONE', 'Any time', 'Asia/Kolkata', 'Synthetic emergency record.', CURDATE() - INTERVAL 30 DAY, FALSE),
('demo-studio', 'ORGANIZATION', 'Pune Repair Collective', 'Pune Repair Collective', 'Local service', 'EMAIL', 'Weekdays, 10 am to 5 pm', 'Asia/Kolkata', 'Synthetic organization record.', NULL, FALSE);

INSERT INTO contact_methods (contact_id, channel, `value`, last_verified, label)
VALUES
('demo-mira-sen', 'PHONE', '+91 00000 10001', CURDATE() - INTERVAL 42 DAY, 'Mobile'),
('demo-mira-sen', 'EMAIL', 'mira@example.test', CURDATE() - INTERVAL 120 DAY, NULL),
('demo-mira-sen', 'ALTERNATE_PHONE', '+91 00000 10002', NULL, 'Home'),
('demo-dev-rao', 'PHONE', '+91 00000 20001', CURDATE() - INTERVAL 240 DAY, 'Mobile'),
('demo-dev-rao', 'EMAIL', 'dev@example.test', CURDATE() - INTERVAL 30 DAY, NULL),
('demo-studio', 'EMAIL', 'hello@example.test', NULL, 'Front desk'),
('demo-studio', 'ALTERNATE_PHONE', '+91 00000 30001', NULL, 'Workshop');
