-- Households: a small private group with one shared cookbook and one shared weekplan. Sharing a
-- cookbook is a property of the membership, not of the recipe, so no recipe table is touched and
-- every recipe keeps exactly one user owner.

-- What fellow members are shown instead of the address. Null falls back to a masked address.
ALTER TABLE public.cookpal_user ADD COLUMN display_name character varying(64);

CREATE SEQUENCE IF NOT EXISTS public.household_membership_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;

CREATE TABLE public.household
(
    id character varying(36) NOT NULL,
    created_on timestamp(6) with time zone,
    last_change timestamp(6) with time zone,
    name character varying(64) NOT NULL,
    CONSTRAINT household_pkey PRIMARY KEY (id)
);

-- One member, and whether their whole cookbook is in this household. All of it or none of it.
CREATE TABLE public.household_membership
(
    id bigint NOT NULL,
    created_on timestamp(6) with time zone,
    last_change timestamp(6) with time zone,
    household_id character varying(36) NOT NULL,
    member_user_id bigint NOT NULL,
    share_recipes boolean NOT NULL DEFAULT false,
    CONSTRAINT household_membership_pkey PRIMARY KEY (id),
    CONSTRAINT household_membership_unique UNIQUE (household_id, member_user_id),
    CONSTRAINT household_membership_household_fkey FOREIGN KEY (household_id)
        REFERENCES public.household (id) ON DELETE CASCADE,
    CONSTRAINT household_membership_member_fkey FOREIGN KEY (member_user_id)
        REFERENCES public.cookpal_user (user_id) ON DELETE CASCADE
);

-- "Whose cookbooks may this viewer read" runs per request; by household is covered by the unique key.
CREATE INDEX household_membership_by_member ON public.household_membership (member_user_id);

-- An invite grants membership rather than access to a resource, which is why it is not a share.
CREATE TABLE public.household_invite
(
    id character varying(36) NOT NULL,
    created_on timestamp(6) with time zone,
    last_change timestamp(6) with time zone,
    household_id character varying(36) NOT NULL,
    -- Null once the member who created it has left; the invite outlives them.
    created_by_user_id bigint,
    expires_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT household_invite_pkey PRIMARY KEY (id),
    CONSTRAINT household_invite_household_fkey FOREIGN KEY (household_id)
        REFERENCES public.household (id) ON DELETE CASCADE,
    CONSTRAINT household_invite_creator_fkey FOREIGN KEY (created_by_user_id)
        REFERENCES public.cookpal_user (user_id) ON DELETE SET NULL
);

CREATE INDEX household_invite_by_household ON public.household_invite (household_id, expires_at);

-- ------------------------------------------------------------------- plans that a household owns
-- A weekplan day, a planning profile and a draft belong either to one person or to one household.
-- Both nullable with a check rather than one column and a discriminator: a foreign key per kind is
-- what makes the database able to cascade each of them correctly.

ALTER TABLE public.weekplan_day ALTER COLUMN owner_user_id DROP NOT NULL;
ALTER TABLE public.weekplan_day ADD COLUMN household_id character varying(36);
ALTER TABLE public.weekplan_day ADD CONSTRAINT weekplan_day_household_fkey
    FOREIGN KEY (household_id) REFERENCES public.household (id) ON DELETE CASCADE;
ALTER TABLE public.weekplan_day ADD CONSTRAINT weekplan_day_one_scope
    CHECK ((owner_user_id IS NULL) <> (household_id IS NULL));
-- One day per household and date: two members adding the first meal at once must not make two.
CREATE UNIQUE INDEX weekplan_day_by_household ON public.weekplan_day (household_id, plan_date);

ALTER TABLE public.planning_profile ALTER COLUMN owner_user_id DROP NOT NULL;
ALTER TABLE public.planning_profile ADD COLUMN household_id character varying(36);
ALTER TABLE public.planning_profile ADD CONSTRAINT planning_profile_household_fkey
    FOREIGN KEY (household_id) REFERENCES public.household (id) ON DELETE CASCADE;
ALTER TABLE public.planning_profile ADD CONSTRAINT planning_profile_one_scope
    CHECK ((owner_user_id IS NULL) <> (household_id IS NULL));
-- Whether a personal plan also draws on the household cookbooks its owner may read.
ALTER TABLE public.planning_profile ADD COLUMN include_household_recipes boolean NOT NULL DEFAULT false;

ALTER TABLE public.plan_draft ALTER COLUMN owner_user_id DROP NOT NULL;
ALTER TABLE public.plan_draft ADD COLUMN household_id character varying(36);
ALTER TABLE public.plan_draft ADD CONSTRAINT plan_draft_household_fkey
    FOREIGN KEY (household_id) REFERENCES public.household (id) ON DELETE CASCADE;
ALTER TABLE public.plan_draft ADD CONSTRAINT plan_draft_one_scope
    CHECK ((owner_user_id IS NULL) <> (household_id IS NULL));
