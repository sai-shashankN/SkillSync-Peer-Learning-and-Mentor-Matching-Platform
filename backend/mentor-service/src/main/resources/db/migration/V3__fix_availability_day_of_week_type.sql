ALTER TABLE mentor_availability
ALTER COLUMN day_of_week TYPE INTEGER
USING day_of_week::INTEGER;
