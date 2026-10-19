# Rate limits

Every endpoint that needs a limit says so with one annotation, `@RateLimited(RateLimitTier.X)`, on the method or on
its controller. One interceptor (`RateLimitInterceptor`) reads it and spends the tier's budget. There is no limiter
per feature: a new endpoint picks a tier, nothing else.

## Tiers

| Tier | For | Without an account, per address and hour | Signed in, per account and hour | Settings (`opencookbook.rate-limits.`) |
|---|---|---|---|---|
| `CHEAP` | Cheap reads anybody may make: kitchen tool data | 120 | 600 | `cheap.anonymous-per-hour`, `cheap.signed-in-per-hour` |
| `GUESSABLE` | Endpoints that could be used to guess a password, a token or an id: signing up and in, activation, password reset and change, setup, invites | 60 | 60 | `guessable.anonymous-per-hour`, `guessable.signed-in-per-hour` |
| `EXPENSIVE` | Costly work: importing from a link (other servers, Instagram among them, see every read as coming from this one) and recipe suggestions | 10 | 60 | `expensive.anonymous-per-hour`, `expensive.signed-in-per-hour` |

Each of these tiers has one budget per caller, shared by all its endpoints: a caller who reads a lot of tool data
has fewer of those reads left in that hour, not fewer sign-in attempts.

| Tier | For | Counted per | Default | Setting |
|---|---|---|---|---|
| `SHARE_LINK` | Everything behind a share link: the shared recipe, its nutrition and its images | link, whoever opens it | 300 per day | `share-link-per-day` |

A share link is meant for the few people a recipe is shared with, not for publishing it. So its budget belongs to the
link, not to a caller: once it is used up, the link waits for its budget to refill, whoever asks, while every other
link and every other endpoint stay open. The endpoint names the link's path variable:
`@RateLimited(value = RateLimitTier.SHARE_LINK, perPathVariable = SharePaths.SHARE_ID_VARIABLE)`. Share ids are random
UUIDs, so there is nothing to guess and no budget per address.

A refusal is `429 RATE_LIMITED` with `Retry-After`.

**Who counts as one caller.** A signed-in user by their account, anybody else by their address: an IPv4 address, or
an IPv6 /64, as one subscriber usually holds a whole /64 (`ClientAddress`). The address comes from `X-Forwarded-For`,
which the reverse proxy overwrites with the real one; the backend is not published.

**How a budget fills.** `RequestBudget` keeps a token bucket per key (Bucket4j) in a bounded cache (Caffeine): it
holds a period's permits and refills them evenly, so a caller who used them all gets the next one after the period
divided by the budget. Keys untouched for a whole period are dropped, as their bucket is full again.

## Not rate limited

Public on purpose, and listed with their reason in `RateLimitCoverageIntegrationTest`: renewing a token, signing out,
the instance info, the legal texts and the Bring export (fetched by Bring's servers). Endpoints that need an account
and are cheap are not limited either.

## Limits that are not tiers

- **Mail per recipient** (`MailAllowance`, `opencookbook.auth.mails-per-hour-per-address`, 5): activation and reset
  mails to one inbox, whoever asks. Not an endpoint limit, and spent silently, so an answer never tells whether an
  account exists.
- **Recipe scans** (`opencookbook.ml.recipe-ocr.jobs-per-user-per-day`, 20): a daily quota counted from the stored
  jobs, as scans are the most expensive work and must survive a restart.
- **Caps on counts**: households per account, open invites per household, shopping lists, API keys. They limit how
  much exists, not how often something is asked.

## Adding an endpoint

1. Decide whether it needs a limit: anything a visitor can reach does, as does costly work.
2. Annotate the method or the controller with its tier. A method's annotation replaces the controller's.
3. `RateLimitCoverageIntegrationTest` fails for a public endpoint without a tier. An exemption goes into its list,
   with the reason.
