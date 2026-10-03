-- An activation link only belongs to an account that still waits for its confirmation. Instances
-- without mail activated accounts without deleting the link, and it would now be offered again.
DELETE FROM public.activation_link
WHERE user_user_id IN (SELECT user_id FROM public.cookpal_user WHERE activated);
