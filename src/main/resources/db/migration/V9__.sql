-- WeekplanDay.day -> WeekplanDay.planDate: `day` is a reserved word in H2, which the tests run against.
ALTER TABLE public.weekplan_day RENAME COLUMN day TO plan_date;

-- PasswordResetLink.validUntil: java.util.Date (TIMESTAMP) -> java.time.Instant (TIMESTAMPTZ).
-- Existing values were written in the JVM default timezone, which is UTC in the shipped container.
ALTER TABLE public.password_reset_link
    ALTER COLUMN valid_until TYPE timestamp(6) with time zone
    USING valid_until AT TIME ZONE 'UTC';
