-- WeekplanDay: rename reserved-word column `day` -> `plan_date` to match LocalDate-based entity mapping.
-- Guarded with IF EXISTS so this is a no-op on freshly created schemas.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'weekplan_day'
          AND column_name = 'day'
    ) THEN
        ALTER TABLE public.weekplan_day RENAME COLUMN day TO plan_date;
    END IF;
END $$;

-- PasswordResetLink.validUntil: java.util.Date (TIMESTAMP) -> java.time.Instant (TIMESTAMPTZ).
-- Existing rows are treated as UTC, matching how Date was serialized.
ALTER TABLE public.password_reset_link
    ALTER COLUMN valid_until TYPE timestamp(6) with time zone
    USING valid_until AT TIME ZONE 'UTC';
