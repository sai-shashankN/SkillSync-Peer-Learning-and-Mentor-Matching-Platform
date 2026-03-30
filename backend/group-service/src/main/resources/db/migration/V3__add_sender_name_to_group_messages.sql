ALTER TABLE group_messages
ADD COLUMN sender_name VARCHAR(100);

UPDATE group_messages
SET sender_name = 'User #' || user_id
WHERE sender_name IS NULL OR BTRIM(sender_name) = '';

ALTER TABLE group_messages
ALTER COLUMN sender_name SET NOT NULL;
