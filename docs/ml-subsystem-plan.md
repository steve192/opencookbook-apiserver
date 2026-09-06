# Cookpal ML Subsystem — Architecture & Implementation Plan

**Status:** Draft for approval · **Date:** 2026-09-02 · **First usecase:** Recipe OCR import

Repos affected: `cookpal-ml-subsystem` (new, private) · `opencookbook-apiserver` · `opencookbook-frontend` · `opencookbook` (compose). `opencookbook-proxy` is **not** affected.

---

## 1. What we are building

A separate Python service that runs ML/AI workloads for cookpal. It exposes one generic job API, keeps its own database, queue and admin UI, and is reached by the cookpal backend over an API token. The first job type is **recipe OCR**: photograph a recipe, get back a structured recipe (title, ingredients with amounts and units, preparation steps, servings, times) that pre-fills the existing recipe wizard.

The subsystem stays closed source. It is simply not published. When no ML URL is configured in a cookpal instance, every ML feature disappears — no endpoints, no UI entry points — exactly the way `opencookbook.sharing.enabled` already works today.

Recipe OCR is the first tenant, not the design target. Calorie detection and ingredient deduplication are planned, so the job API, queue, quota and admin surfaces are all generic from day one; adding a usecase must mean writing one handler and one result schema, nothing else.

---

## 2. Decisions locked

| # | Decision | Choice | Why |
|---|---|---|---|
| 1 | Hosting | We run the ML subsystem; it is not published | Self-hosters get the feature only if given a token and URL; otherwise it is cleanly absent |
| 2 | Admin UI | Own admin UI inside the ML repo | Tokens, limits and usage stats are ML concerns and stay closed with it |
| 3 | ML stack | Django + DRF + Django admin | Proven for exactly this workload in splex; free ORM, migrations, admin scaffolding, management-command worker |
| 4 | Structure extraction | Hybrid: deterministic rules first, small trainable line classifier for the rest | Works on day one without a corpus; improves with labelled data; no LLM, CPU only |
| 5 | Job flow | Job row in **both** services; app polls cookpal, cookpal polls ML | Survives restarts, enables per-user quota, retry and history; the app never learns the ML subsystem exists |
| 6 | Images | Retained, with per-user opt-in for training use | Enables debugging, hash caching and a growing training corpus; needs consent UI, ToS text and a deletion path |
| 7 | OCR UX | Camera + 4-point crop, gallery/file pick, two block questions, then pre-fill the existing recipe wizard | The wizard has full editing power, so the only thing worth asking first is the one thing it cannot fix by itself: which part of the page was which |
| 8 | Languages | German + English at launch, pluggable per-language resources | Lexicons are data files, so ES/FR/IT later is a data change, not a code change |
| 9 | ML API shape | One generic job envelope | One queue, one auth path, one quota mechanism, one Java client |
| 10 | Limits | Per-token rolling-window quota **and** a per-user daily quota inside cookpal | Protects our hardware, and stops one user draining an instance's allowance |
| 11 | Admin stats | Jobs over time by type/token, success & failure breakdown, latency & queue health, quota consumption | The full operational picture for a CPU-bound single worker |
| 12 | Delivery | Private repo, private image, own compose stack; cookpal compose gains two optional env vars | ML is never publicly exposed; the public compose advertises nothing |
| 13 | Token lifecycle | Tokens are revocable instantly and carry an optional expiry; revoked tokens fail closed | The only credential between the internet and a finite CPU box has to be switchable off |
| 14 | Worker protection | Per-token cap on in-flight (queued + processing) jobs; excess rejected at submit with `429` | One instance's batch must not push everyone else's latency into minutes on a single consumer |
| 15 | Admin test bench | The ML admin can run a job itself: upload images, see the extracted recipe and diagnostics | Validates extraction changes without a cookpal instance, a token or a phone |
| 16 | Multi-page input | One job may carry several photos (ingredients page + method page) | Cookbook recipes routinely span a spread; stitching pages server side beats two half-recipes |
| 17 | Block confirmation | After a scan, the app asks two quick questions about where the ingredients and the steps are, and lets the user redraw a rough area | Telling ingredients from steps is the weakest part of the rule layer on real pages; the person looking at the photo can settle it in one tap |
| 18 | Corrections are the corpus | A confirmed or redrawn area re-runs the extraction immediately and, with consent, is kept as a labelled example | The training data comes from the feature working, not from a separate labelling exercise nobody has time for |

### Confirming the blocks

Superseded decision 7's "no review screen": on real cookbook pages the rule layer separates
ingredients from preparation steps unreliably, and no amount of lexicon work fixes a layout it
has not seen. The person holding the phone can settle it instantly, so the app asks.

Two questions, no more, each answerable in one tap:

1. The detected ingredient area is drawn over the photo. **Is this where the ingredients are?**
   *Yes* · *No, let me show you* · *There are no ingredients in this picture*.
2. The same for the preparation steps.

"No, let me show you" asks for a **rough area** — one rectangle dragged over the block. Never a
line, never a word: the value here is the coarse division of the page, and asking for anything
finer would turn a two-tap confirmation into data entry, which is what the wizard is already
for.

An answer does two things. It immediately re-runs the extraction against the corrected areas —
no second photograph, no second OCR pass, because the lines are already known — so the wizard
opens with the better result. And, where the user consented, the areas are kept beside those
lines as a labelled example. The corpus therefore comes from the feature being used rather than
from a labelling exercise nobody has time for.

### Non-goals

Explicitly out of scope, so nobody designs around them:

- **PDF input.** splex has a rendering path we could borrow later, but recipes arrive as photos. A PDF branch means a second normalization path, a page-count limit and an embedded-text-versus-OCR decision, for no current user.
- **Handwritten recipes.** PPOCRV6 will not read them well. The UI should say so rather than fail mysteriously.
- **The app talking to the ML subsystem directly.** Always via the cookpal backend, always with the token held server side.

---

## 3. Architecture

```
┌──────────────────┐
│   Expo app       │  camera / gallery → crop points → upload
│  (web + native)  │  polls GET /api/v1/ml/jobs/{id} every 2s
└────────┬─────────┘
         │  user JWT
         │  multipart: image + crop points + trainingConsent
         ▼
┌──────────────────────────────────────────────┐
│  cookpal backend (Spring Boot)               │
│                                              │
│  MlJobController ──► MlJobService            │
│                        │                     │
│                        ├─ per-user daily quota check
│                        ├─ MlJob row (QUEUED) ─────► Postgres
│                        └─ MlSubsystemProxy         (cookpal db)
│                                │                   │
│  MlJobPollingCronjob ──────────┤                   │
└────────────────────────────────┼───────────────────┘
                                 │  Authorization: Bearer <ml token>
                                 │  POST /api/v1/jobs   (submit)
                                 │  GET  /api/v1/jobs/{id}  (poll, 2s)
                                 ▼
┌──────────────────────────────────────────────┐
│  ML subsystem (Django + DRF)   PRIVATE       │
│                                              │
│  /api/v1/jobs ──► token auth ──► quota ──►   │
│                   Job row (queued)  │        │
│                                     ▼        │
│                              ┌──────────────┐│
│                              │  worker      ││ single consumer,
│                              │  process     ││ claims one job at a time
│                              └──────┬───────┘│
│                                     ▼        │
│                    recipe_ocr handler        │
│                    (RapidOCR → rules → clf)  │
│                                              │
│  /admin  ── tokens, quotas, stats, corpus    │
└──────────────────┬───────────────────────────┘
                   ▼
            Postgres + media volume (images, model cache)
```

**Trust boundary.** The ML API token lives only in the cookpal backend's configuration. The app never sees it, never talks to the ML subsystem, and has no idea it exists. This is the mistake the old `ocr-import` branch made — a hardcoded bearer token shipped in `RestAPI.ts` — and we are not repeating it.

**Feature-flag cascade.** `opencookbook.ml.serviceUrl` empty → `MlJobController` beans are not registered, `InstanceInfoResponse.ocrImportEnabled` is `false`, and the frontend hides the OCR entry point. Mirrors `SharingDisabledIntegrationTest` exactly, and gets an equivalent `MlDisabledIntegrationTest`.

**Why cookpal keeps its own job row.** Three reasons: the app must survive an ML restart mid-job; per-user quota accounting needs a local record; and retry/history is impossible against a stateless proxy. The cost is one table and one scheduled poller.

---

## 4. The ML subsystem

New private repo, seeded into the existing `cookpal-ml-subsystem` (currently only a `research/` folder with spaCy/embeddings notebooks — keep it, move to `research/`).

### 4.1 Generic job API

Four endpoints. That is the whole external surface.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/jobs` | Submit. Multipart: `job_type`, `payload` (JSON), zero or more file attachments. Returns `{job_id, status, queue_position}` |
| `GET` | `/api/v1/jobs/{id}` | Poll. Returns `{job_id, job_type, status, result, error, created_at, finished_at}` |
| `DELETE` | `/api/v1/jobs/{id}` | Cancel a queued job / delete a finished one and its attachments |
| `GET` | `/api/v1/health` | Liveness, queue depth, enabled job types. **Unauthenticated** — it carries no job content and nothing caller-specific, so it doubles as the container probe and lets a cookpal instance check reachability before it holds a token |

Status is one of `queued` · `processing` · `completed` · `failed` · `cancelled`.

Job types register themselves in a handler registry:

```python
@register_job("recipe_ocr", result_schema=RecipeOcrResult, max_attachments=1)
class RecipeOcrHandler(JobHandler):
    def run(self, payload: dict, attachments: list[Attachment]) -> RecipeOcrResult: ...
```

`calorie_detection` and `ingredient_deduplication` later become two more decorated classes. No new routes, no new auth code, no new quota code.

`max_attachments` is per job type: `recipe_ocr` accepts several pages of one recipe, ordered by the order they were sent. Submitting more than a type allows is a `400`, not a silent truncation.

Submit is rejected with `429` when the token is over its rolling quota **or** already has `max_in_flight` jobs queued or processing. Both limits live on the token and both return a typed error the Java side can distinguish.

Errors are typed, not free text: `{error: {code: "OCR_NO_TEXT_FOUND", message: "...", retryable: false}}` so the Java side can map to user-facing messages without string matching.

### 4.2 Authentication

- Token format `cpml_<32 bytes base62>`, shown **once** at creation in the admin UI.
- Stored as SHA-256 hash, never in plaintext. Lookup by a short non-secret prefix stored alongside, so verification is one indexed query plus one constant-time compare.
- Sent as `Authorization: Bearer <token>`.
- Per token: label, owner note, created/last-used timestamps, active flag, optional expiry, quota settings, `max_in_flight`.
- **Revocation is immediate and fails closed.** Clearing `is_active` or passing `expires_at` makes the very next request `401`; there is no cache to wait out. The cookpal side treats a `401` as "ML unavailable" and flips the feature flag off rather than erroring once per user request.
- DRF authentication class + permission class; every job route requires a valid active token. The admin UI uses normal Django session auth, entirely separately.

### 4.3 Queue and worker

Lifted conceptually from splex's `receipt_extraction/worker.py`, simplified because we are on Postgres rather than SQLite:

- The **database is the queue**. No Redis, no Celery. A `Job` row with `status='queued'` ordered by `(priority DESC, created_at ASC)` is the work list.
- The worker runs as a separate process: `python manage.py run_ml_worker`, its own container in the compose stack.
- Claim is `SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1` inside a transaction, flipping to `processing`. This makes the design safe for more than one worker later without changing anything, even though we start with one.
- Wake-up is `LISTEN`/`NOTIFY` on job insert, with a 5-second poll as the backstop so a missed notification only costs latency, never correctness.
- On startup, `recover_pending()` returns any row stuck in `processing` (killed mid-job) to `queued`, bounded by an attempt counter so a job that reliably kills the worker eventually lands in `failed` instead of looping.
- One job at a time. The OCR model is the memory and CPU hog; concurrency here buys nothing on a CPU-bound box and costs a lot of RAM.

### 4.4 Data model

| Table | Holds |
|---|---|
| `ApiToken` | hash, prefix, label, active, expires_at, quota limits, timestamps |
| `Job` | uuid pk, token FK, job_type, status, priority, payload JSON, result JSON, error JSON, attempts, timings, content_hash |
| `JobAttachment` | job FK, file, content_type, size, sha256, retention_until, training_consent |
| `TokenUsage` | token FK, date, job_type, count — the rolling-window quota ledger |
| `JobEvent` | job FK, timestamp, from/to status, note — the audit trail behind the stats screen |
| `TrainingSample` | attachment FK, language, human labels JSON, labelled_by, labelled_at — the corpus |
| `ModelArtifact` | name, version, checksum, trained_at, metrics JSON — which classifier produced which result |

Every job result records the `ModelArtifact` version that produced it, so a quality regression can be traced to a model rather than guessed at.

### 4.5 Quotas and usage accounting

- Per token: `jobs_per_day` and `jobs_per_month`, both nullable meaning unlimited, settable per job type or globally.
- Counted at **submit**, not completion — otherwise a flood of failing jobs is free.
- Exhausted quota returns `429` with `Retry-After` and `{error: {code: "QUOTA_EXCEEDED", resets_at: ...}}`.
- The cookpal backend surfaces this to the user as "recipe scanning is temporarily unavailable, try again tomorrow" rather than a raw error, and marks the instance's ML availability degraded so the UI can grey out the entry point.

### 4.6 Admin UI

Django admin as the base — it gives us model CRUD, permissions, filtering and search for free — plus one custom dashboard view at `/admin/ml-stats/` rendered server-side with a small chart script.

The dashboard shows, per selectable time range:

- **Jobs over time**, stacked by `job_type`, filterable by token.
- **Success rate** with a failure breakdown by error code, each row drilling into recent failed jobs (payload, error, attached image, model version).
- **Latency and queue health**: current queue depth, oldest queued age, p50/p90/p99 processing duration, worker last-heartbeat.
- **Quota consumption per token**, sorted by proximity to exhaustion.

Token creation is an admin action that displays the plaintext once and never again.

A second admin screen is the **labelling tool** for the training corpus: it lists stored images where consent was given, renders the OCR boxes over the image, and lets you correct the per-line labels. This is what feeds Phase 3.

A third is the **test bench** at `/admin/ml-test/`. Upload one or more images, pick a job type, and it submits a real job through the real queue under an internal token, then renders the result: the extracted recipe as it would reach cookpal, the reconstructed lines with their labels overlaid on the image, the per-field confidences, timings and the model version. It is the fastest loop we have for judging an extraction change — no cookpal instance, no phone, no token juggling — and because it goes through the same handler and the same queue, what it shows is what a real job produces. Jobs it creates are marked `source='admin'` so they can be excluded from usage statistics.

### 4.7 Caching

Attachments are hashed on receipt. A submit whose `(job_type, content_hash, payload_hash)` matches a previously completed job returns that job's result immediately, without queueing. Re-scanning the same photo is free, and the "retry" button costs nothing when nothing changed. Cache hits are recorded but do not count against quota.

### 4.8 Retention, consent and deletion

- Attachments carry `retention_until`. Default without training consent: **24 hours** after completion — enough for retry and for me to debug a bad extraction.
- With consent, the image is retained indefinitely as a training candidate, and `training_consent=true` is stored on the row itself, not inferred from a user setting that may later change.
- A nightly `purge_expired_attachments` management command deletes expired files and blanks the DB reference. Job rows and their results survive; only the image goes.
- Consent travels **with the job** from cookpal, per submission, derived from the user's setting at submit time. Withdrawing consent later must also delete already-collected samples — so cookpal exposes `DELETE /api/v1/ml/training-data` which fans out to the ML subsystem for that instance's user id (hashed, never the email).
- The user id sent to the ML subsystem is a per-instance salted hash. The ML subsystem never learns who anyone is, and cannot correlate users across instances.

---

## 5. The extraction pipeline

This is the hard part and the part with no LLM in it. Nine stages.

```
 image + 4 crop points
        │
   A ── perspective warp & deskew        (OpenCV, server side)
        │
   B ── normalize: EXIF, RGB, downscale cap, contrast
        │
   C ── OCR: RapidOCR PPOCRV6 (ONNX, CPU)  → boxes + text + confidence
        │
   D ── layout reconstruction: deskew boxes, group by text height,
        detect columns, split each into blocks,
        rebuild reading order                          → ordered lines
        │
   E ── language detection (DE / EN) from line text
        │
   F ── rule layer: section headings, amount-leading lines,
        servings & time patterns, title heuristics     → confident labels
        │
   G ── line classifier for what rules could not label (CRF, CPU)
        │
   H ── ingredient parsing: amount / unit / name / additional info
        │
   I ── confidence scoring per field
        │
        ▼
   RecipeOcrResult JSON
```

### A. Perspective warp — server side, not client side

**This is a change from the old branch's assumption.** `expo-image-manipulator` does crop, resize, rotate and flip — it has no perspective transform, and splex's `ImageCropDialog` is a square pan/zoom cropper, not a quadrilateral one. Doing a true 4-point warp on the client means dragging in Skia or GL and maintaining it across web and native.

So: the client collects the four corner points and ships them as job payload metadata alongside the full image. The warp happens server side in OpenCV, where it is six lines and already correct. The client still downscales before upload (below), so the bandwidth cost is small.

What *is* worth reusing from splex's `ImageCropDialog` is its cross-platform input layer — RNGH gestures on native, bespoke DOM pointer events on web, both writing into one pan/zoom state — because RNGH's web build does not pick up mouse drag in that layout. We hit the same wall.

**Every box is in the coordinates of the picture that was sent.** The page is flattened before it is read, so everything found is found on the flattened page - but the only picture the caller holds is the one it took, and after a perspective warp those are not the same place. A block reported against the wrong one lands somewhere else on the photo, or off it entirely. So `normalize_page` keeps the way back (nine numbers, both sides in fractions, `null` when no warp was applied), the page geometry carries it, and every box crossing the API - the areas to confirm, the boxes the diagnostics draw - is mapped onto the photograph first. Corrections come back the same way: a line is moved into photo space to test whether a marked area contains it, because moving the line is exact where moving the area turns a rectangle into a quadrilateral.

**Crop corners are fractions, and are clamped to the picture.** A caller sending pixels where fractions were asked for multiplies the page by its own width, and the warp then asks the allocator for terabytes - enough to take down the host, not just the worker. The clamp is at the boundary, in `_ordered_corners`, and does not trust the client's own clamping.

**Several photographs are one recipe, never several.** Verified end to end: a recipe cut in half and sent as two pictures comes back as one recipe with all of its ingredients and steps, identical to the whole page read at once. Stages A-D run per image and E-I once over the concatenated lines, so a heading photographed on the first page governs lines on the second. Up to six photographs, enforced by the api server. It is for a double-page spread or a recipe running overleaf - it is not a way to import several recipes at once.

**Several pages, one recipe.** Stages A–D run per image. The resulting ordered lines are then concatenated in submission order into one sequence, and stages E–I run once over the whole thing. That way a `Zutaten` heading photographed on page one still governs the lines photographed on page two, and step numbering continues across the spread instead of restarting. Each line keeps its `pageIndex` so the diagnostics overlay can put it back on the right photo.

### B. Normalization

EXIF orientation applied first (phone photos are rotated more often than not), convert to RGB, cap the long edge at ~2400px, mild adaptive contrast. The preview the user sees and the pixels the OCR reads must be the same space, or every box we ever draw is offset.

### C. OCR — RapidOCR, not paddleocr

Use `rapidocr` with PPOCRV6 medium detection and recognition models, as splex does. It runs the same PaddleOCR models through ONNX Runtime without the PaddlePaddle runtime, which is a fraction of the image size, installs cleanly, and lets us cap `intra_op_num_threads` so OCR cannot starve the web tier. Word boxes are requested (`return_word_box=True`) because the ingredient parser needs sub-line geometry to split an amount column from a name column.

Models are downloaded once into a persistent cache volume at container start, not at first request — otherwise the first user of a fresh deployment waits minutes.

### D. Layout reconstruction

The stage that decides whether the whole thing works. Recipe pages are not receipts:

- **Two-column layouts are the norm** in cookbooks — ingredients in a narrow left column, method in a wide right one. Naive top-to-bottom reading order interleaves them into nonsense. Detect column bands by clustering box x-ranges and check for a vertical gutter with near-zero ink.
- **Deskew** using a robust page-wide angle from the box orientations, after the perspective warp has removed most of it.
- **Group boxes into lines** by oriented text height and baseline proximity, then lines into blocks by spacing and indentation.
- Retain normalized geometry on every line — needed for the classifier features, for debugging overlays in the admin tool, and for the labelling UI.

**The gutter threshold is measured from the page's own word spacing.** A gutter has to be wider than the spaces inside a line or every word break would split the page - but a magazine sets both tightly and a recipe card sets both wide, and what stays put between the two is the ratio, not either measurement. Against a fixed multiple of the text height a magazine's 13px gutter beside 32px type is rejected and the page collapses to one column, welding all four columns of a recipe together. The width is only a safety net in any case: what really establishes a gutter is that the whole height of the page leaves it empty, which no accident of word spacing does.

**Columns are a property of a band of the page, not of the page.** A cookbook sets its introduction across the measure, its ingredients in two columns underneath, and its method across the measure again. Asked of the page as a whole those ingredient columns are invisible - every line of prose above and below crosses the gutter, and a gutter that much text runs through is not one - so the two columns read as one and every row of the list is welded to the row beside it. The page is therefore cut into bands at the lines that run the full measure, and each band is asked separately where its columns are.

"Full measure" is **reaching both margins**, not being wide. A page whose wider column takes sixty per cent of the paper has every line of that column looking full-measure by width, and treating each as a break cuts the column into as many pieces as it has lines. The margins themselves are taken at the tenth percentile rather than the extremes, so a running head in the corner does not become the margin nothing else reaches.

**A gutter need not run the height of its band.** Where a paragraph sits directly above a two-column list with nothing between them, the gutter is clear for the list and crossed by the paragraph. So each candidate gutter is also scored by *how many consecutive lines it stays clear of*, and the best such run cuts the band into the stretch that is columned and the stretches that are not. This is a fallback only: a band already columned the whole way down is left alone, or a card of four even columns would be divided at whichever gutter happened to be clearest.

**Order settled during implementation: columns first, then blocks inside each column.** Grouping boxes into lines across the whole page instead lets a single detection that ran across the gutter weld two columns together, and one such line is enough to lose the page. Blocks are then a pass down each column, breaking where the distance between lines exceeds the page's own measured line pitch — measured, because leading varies between a recipe card and a cookbook spread by a factor of three, and any fixed threshold reads one of them as a single block and the other as one block per line. Connected-components block finding (RLSA, as in the `recipe-ocr-webservice` predecessor) was implemented and measured against this and is **not** used: it merges everything a bridging line touches, which cost an entire recipe card.

The block is what a step break follows when nothing numbers the steps, what the app draws when it asks whether it found the ingredients, and what stage F falls back on for lines no rule could place.

**Enumeration is read from the layout as well as the text (stages D and F).** A magazine numbers its method with a drop cap - a numeral set two or three lines tall beside the paragraph. Left among the ordinary detections its box overlaps every line it is set into, so it does not merely join the wrong one, it bridges them: two printed lines are welded into one line of the recipe whose halves read in the wrong order. It is taken out before lines are grouped and given back to the line beside its top, which then carries `starts_a_step`. The glyph is an ordinal and carries no content, so it is not part of the text - that it was there is the whole of what it says. A column the numbering missed still starts a step, because recognition drops the odd numeral and a missing break reads worse than an extra one.

**An entry is not the same thing as a line (stage I).** An ingredient too long for the measure wraps, and read a line at a time "450 g Flanksteak, quer zur Faser" / "in 7,5 cm lange dünne Streifen" / "geschnitten" becomes three ingredients, one of them called "geschnitten". A printed list says which of its lines are continuations in one of two ways, and a given book picks one: by the **leading**, setting extra space between entries and wrapping tight; or by a **hanging indent**, setting every entry evenly and pushing the continuations right. Both are measured from the block itself - the leading and the indent of a recipe card and of a cookbook have nothing in common - and the indent is measured against the line that began the entry rather than the block's margin, because a photographed page curves and that drift down a long column is wider than the indent being looked for.

Deliberately **not** the width of the line above, which appears to say the same thing and was tried first: an ingredient table sets the name against the left margin and the amounts against the right, so every row runs the full measure while none is continued - and reading width alone folded a whole card's table into its first row.

**Both measures keep the ingredient (stage H).** A cookbook that prints "3 EL (45 ml) Austernsauce" leaves the bracket where the name would be. Taking everything before a bracket as the name leaves nothing at all, and an ingredient with no name is dropped - so the whole line disappeared rather than a detail of it. A note that comes first describes the quantity; the name is what follows it.

**A component heading is told by the space around it (stage F).** A recipe made of parts names them - "Szechuan-Sauce", "Für den Teig" - and where the name is not one the lexicon knows it reads as an ingredient with no quantity. Not told by its size: detection boxes measure the letters that happen to be in the line, and a wrapped line of a long entry comes back taller than the heading above it. A heading is set close to what it heads and well clear of what came before, which is what being a heading means.

**A bullet separates ingredients, including mid-line (stage I).** A magazine sets its ingredients as a running paragraph - `1 EL Öl • 1 EL Tomaten-` / `mark• Salz, Pfeffer` - so an entry starts mid-line and finishes on the next, broken across the line end mid-word. Read a line at a time that list comes out as "1 EL Tomaten" and "mark, Salz". The block is rejoined with the same hyphen repair the method uses (now shared in `text.py`) and re-cut on the bullets; a line that opens with a quantity also starts an entry, since recognition drops the odd bullet.

**A block settles its own lines (stage F).** Most method lines carry nothing a rule can recognise — no quantity, no numbering, too short to read as prose — and are left unplaced beside the one line in the same paragraph that a rule did match. Where a block agrees with itself, the unplaced lines take its label, which recovers a step as the sentence it was printed as rather than the fragment a rule happened to match. Where a block reads as genuinely mixed, nothing is guessed: that is the case stage G and the block question exist for.

**Superseded, see B2 above.** Contrast normalization was tried and is not in stage B. CLAHE over the lightness channel, on the theory that faint indoor photographs were losing text. Measured across the whole example set it lost ingredients and steps on four documents and recovered none, so it is not there. What looked like a recognition failure on a dim page turned out to be a photograph holding the end of one recipe and the whole of another, which is a question for the block UI rather than a defect in stage B.

### B2. Is the photograph worth reading at all?

Some pictures cannot be read, and the useful thing to say is so. A recipe assembled from four recognised words looks like a bad reader rather than a bad photograph, and leaves somebody correcting nonsense instead of taking another picture - which would have taken them five seconds. The result therefore carries a `photo` object: `usable`, and a `problem` naming what to do about it.

Only failures that are unmistakable are reported, because a false "take another photo" insults a photograph that was fine:

- **sideways** - the median detection is taller than it is wide. Measured across the examples an upright page sits between four and fourteen times wider than tall, and a page on its side between 0.08 and 0.14: two orders of magnitude apart, so the threshold sits in empty space. Reported ahead of everything else, since a page on its side also reads as unreadable and telling somebody to retake a picture that only needs turning is advice they cannot act on. The app has rotate buttons, so the fix is one tap.
- **unreadable** - almost no detections, or almost no characters. The recognizer's own confidence is *not* used: measured, it returns ~1.0 on pages it has badly misread, so it says nothing.
- **too_small** - the type is under ten pixels tall after normalization, meaning the page is a small part of a large picture.

Contrast normalization (CLAHE) was tried here and is **not** in the pipeline: measured across the whole example set it lost ingredients and steps on four documents and recovered none.

### The operator's view

Everything an operator can do is reachable from `/admin/`, which lists a **Tools** panel above the models. Beyond the test bench and health:

- **Line classifier** (`/admin/ml-training/`) - how much corpus there is, how much of it consent allows to be used, and a button that runs the training. It calls the management command rather than reimplementing it, so there is one training path and the page cannot drift from it.
- **Corrections** - the corpus itself, one row per area somebody marked, filterable by kind and consent. Read-only: a correction is what somebody said, and editing it would teach the model something nobody agreed to.
- **Photographs** - listed on their own as well as under their job, because "what have people let us keep" is a question about the pictures rather than about the scans they came from.
- **Models** - what has been trained, its held-out accuracy, and which one is in use. Activation goes through `registry.activate`, so exactly one row is active and the switch happens in one write.

### E. Language detection

Cheap and lexicon-driven: score the OCR text against the DE and EN keyword lexicons and unit lists, take the winner, fall back to the instance's default. Language selects which resource bundle stages F–H use. Bundles are YAML/JSON data files under `extraction/lexicons/<lang>.yaml`, so adding Spanish is adding a file.

The bundle also carries the language's **cooking verbs**. Method text is full of quantities — "2 cm klein schneiden", "1 EL Öl erhitzen" — so a leading amount cannot settle a line on its own. A verb alone cannot either, because English eats what it also does (toast, roast, spread), so the verb counts only inside a finished or long-enough clause. Both together separate the ingredient that names a thing from the instruction that asks for something to be done to it.

### F. Rule layer

Deterministic, explainable, and enough to ship on its own:

- **Section headings**: `Zutaten`, `Für den Teig`, `Zubereitung`, `Anleitung` / `Ingredients`, `For the sauce`, `Method`, `Instructions`, `Directions`. Matched case-insensitively with fuzzy tolerance for OCR damage, and weighted by being short, isolated, and often larger or bolder than surrounding text.
- **Amount-leading lines**: a line beginning with a number, fraction, vulgar fraction or unit is an ingredient candidate. Same shape the existing Java `IngredientExtractor` already matches.
- **Servings**: `4 Portionen`, `für 4 Personen`, `Serves 4`, `Ergibt 12 Stück`.
- **Times**: `Zubereitungszeit 30 Min`, `Prep time 20 minutes`, `Backzeit`, `Total time`, ISO-ish durations.
- **Title**: largest text height in the top third of the page, not matching a heading lexicon, not amount-leading.
- **Steps**: long lines with sentence punctuation, in the block after a method heading; numbered prefixes (`1.`, `Schritt 2`) split steps reliably when present.

Lines the rules label with high confidence are done. Everything else falls to G.

### G. Line classifier

A linear-chain CRF (`sklearn-crfsuite`) over the ordered line sequence, labelling each line `title` · `ingredient` · `step` · `heading` · `meta` · `noise`. A CRF rather than an independent classifier because the label sequence is strongly ordered — ingredients cluster, steps cluster, a heading precedes its block — and that structure is most of the signal.

Features per line, all cheap and layout-aware: relative x/y, width, text height relative to page median, column index, distance to previous/next line, starts-with-digit, contains-unit-token, character count, word count, punctuation density, uppercase ratio, ends-with-period, matches-heading-lexicon, OCR confidence, plus the same features for the two neighbours on each side.

Trains in seconds on a laptop, is a few hundred KB pickled, runs in milliseconds, ships versioned as a `ModelArtifact` alongside the code. No GPU, no transformer, no inference server.

Until a corpus exists it is simply absent, and stage F's output passes through unchanged — which is why Phase 2 ships before Phase 3.

### H. Ingredient parsing — and where it should live

The cookpal backend already parses ingredient strings today: `IngredientExtractor.extractAmount / extractUnit / extractName / extractAdditionalInfo`, validated against `IngredientUnitHelper.isKnownUnit`. URL imports go through it. If the ML subsystem parses ingredients independently, OCR import and URL import will disagree about the same text, and we will be maintaining two ingredient parsers in two languages.

**Recommendation.** The ML result carries both: the raw line *and* a best-effort structured parse.

```json
"ingredients": [
  {"raw": "200 g Mehl (Type 405)", "amount": 200, "unit": "g",
   "name": "Mehl", "additionalInfo": "(Type 405)", "confidence": 0.94}
]
```

The cookpal backend treats the structured fields as the primary source, but validates `unit` against `IngredientUnitHelper`. If the unit is unknown to cookpal, it re-parses `raw` with `IngredientExtractor` and uses that instead. Both imports then converge on cookpal's unit vocabulary, the ML side keeps the geometry-aware parse that only it can do (it can see that "200" and "g Mehl" were two OCR boxes in different columns), and neither parser is duplicated wholesale.

Decided. The alternative — ML returns raw lines only and all parsing stays in Java — is simpler, but it throws away column geometry that materially helps on tabular ingredient lists, where the amount and the ingredient name arrive as two separate OCR boxes with a gutter between them.

### I. Confidence and what gets pre-filled

Every extracted field carries a confidence. Below a threshold, the field is returned but flagged, and the frontend leaves it visibly empty-but-suggested rather than silently wrong. A confidently wrong title is worse than a blank one, because the user will not notice it.

### Result schema

```json
{
  "language": "de",
  "title": {"value": "Apfelkuchen", "confidence": 0.91},
  "servings": {"value": 12, "confidence": 0.88},
  "preparationTime": {"value": 30, "confidence": 0.72},
  "totalTime": {"value": 90, "confidence": 0.65},
  "ingredients": [ ... ],
  "preparationSteps": [{"value": "Den Ofen auf 180 °C vorheizen.", "confidence": 0.95}],
  "rawText": "...",
  "modelVersion": "recipe-ocr-2026.09.1",
  "pageCount": 2,
  "diagnostics": {"lines": [...], "columns": 2, "pages": 2, "ocrSource": "rapidocr-ppocrv6"}
}
```

`diagnostics` is returned only when the token has the debug flag set — it is large, and it is for us.

### Training data and evaluation

- Corpus: consented images plus a seed set I label by hand from the existing sample images in `recipe-ocr-webservice/` (`example1_ingredients.jpg`, `example1_steps.jpg`, …) and new photos.
- Labelling happens in the ML admin's labelling screen, over the reconstructed lines — labelling ~40 pages is an evening's work and enough for a first CRF.
- Held-out eval set, never trained on. Metrics: per-label precision/recall/F1 on line classification, plus end-to-end field accuracy (title exact/fuzzy, ingredient set F1, step count and order).
- Every model version's metrics land in `ModelArtifact.metrics` so a regression is visible in the admin, not discovered by a user.

---

## 6. Cookpal backend changes (`opencookbook-apiserver`)

Follows the shape `RecipeScrapersWebserviceImporter` + `RecipeScraperServiceProxy` already established, so this is a familiar pattern in the codebase rather than a new one.

**Configuration** — new nested block on `OpencookbookConfiguration`, grouped like `Sharing` is:

```yaml
opencookbook:
  ml:
    serviceUrl: ""            # empty ⇒ every ML feature disabled
    apiToken: ""
    connectTimeoutSeconds: 5
    jobTimeoutSeconds: 300
    pollIntervalSeconds: 2
    recipeOcr:
      enabled: true
      jobsPerUserPerDay: 20
      maxImageSize: 10485760
```

**New code**

| Path | Purpose |
|---|---|
| `services/ml/MlSubsystemProxy.java` | HTTP client for the four ML endpoints; mirrors `RecipeScraperServiceProxy`, adds the bearer token and typed error mapping |
| `services/ml/MlJobService.java` | Submit, poll, cancel; owns per-user quota and job-row lifecycle |
| `services/ml/MlAvailabilityService.java` | Caches ML health; drives the feature flag and degrades on `429`/unreachable |
| `services/ml/recipeocr/RecipeOcrImportService.java` | Maps `RecipeOcrResult` → a transient `Recipe`, applying the `IngredientUnitHelper` validation from §5H |
| `entities/ml/MlJob.java` | uuid, owner, jobType, status, remoteJobId, result JSON, error, timestamps |
| `repositories/MlJobRepository.java` | Standard repo + a query for jobs needing polling |
| `cronjobs/MlJobPollingCronjob.java` | `@Scheduled` reconciliation of in-flight jobs; goes in the existing `cronjobs` package |
| `controllers/ml/MlController.java` | `POST /api/v1/ml/recipe-ocr` (multipart), `GET /api/v1/ml/jobs/{id}`, `DELETE /api/v1/ml/training-data` |
| `controllers/admin/AdminMlController.java` | Read-only: this instance's job counts, failures, quota consumption |
| `db/migration/V13__.sql` | `ml_job` table + sequence, following the existing `V<n>__.sql` convention |

**Changed code**

- `InstanceInfoResponse` gains `ocrImportEnabled`, set from config + availability — the same mechanism `sharingEnabled` uses.
- `WebSecurityConfiguration` — nothing to whitelist; all ML routes are authenticated.
- `application.yml` — the block above, all defaults empty/off.

**Upload size.** `spring.servlet.multipart.max-file-size` is currently `5MB`. Recipe photos routinely exceed that. The client downscales before upload (long edge 2400px, JPEG q80, typically ~600KB), but raise the limit to 10MB for headroom. `opencookbook-proxy` already sets `client_max_body_size 10M`, so nginx needs no change.

**Tests.** `MlDisabledIntegrationTest`, a direct sibling of `SharingDisabledIntegrationTest`, asserting every ML route 404s and `ocrImportEnabled` is false when no URL is configured. Plus service-level tests against a mocked proxy, and a `RecipeOcrImportService` test covering the unit-fallback branch.

---

## 7. Frontend changes (`opencookbook-frontend`)

**New**

- `src/screens/ocr/RecipeOcrImportScreen.tsx` — the wizard: capture → crop → progress → done. Rebuilt from scratch; the old `ocr-import` branch's flow is the reference, its code is not.
- `src/components/QuadCropper.tsx` — 4-point corner-drag overlay. Native gestures via RNGH, bespoke pointer events on web (the lesson from splex's `ImageCropDialog`). Emits four points in source-image pixel space plus a downscaled JPEG.
- `src/hooks/useMlJob.ts` — submit, poll with backoff, cancel on unmount, surface typed errors.
- Multi-page capture: after cropping a page the wizard offers "add another page", holding the cropped pages in a list and submitting them as one job. Pages are reorderable and removable before submit.
- `src/dao/RestAPI.ts` — `submitRecipeOcr(image, points, consent)`, `getMlJob(id)`, `deleteTrainingData()`. No token handling; the user JWT is all it sends.

**Changed**

- `NavigationRoutes.ts` — add `RecipeOcrImportScreen: undefined`, and extend `RecipeWizardScreen` params to `{ editing?: boolean, recipeId?: number, draftRecipe?: Recipe }`.
- `RecipeWizardScreen.tsx:51` — `useState<Recipe>(props.route.params?.draftRecipe ?? existingRecipe ?? emptyRecipe())`. One line. The scanned recipe is **not** persisted before the user saves, unlike URL import, because OCR accuracy does not warrant creating rows the user did not approve.
- `ImportScreen.tsx` — a "Scan a recipe" entry alongside URL import, rendered only when `ocrImportEnabled`.
- `SettingsScreen.tsx` — training-consent toggle with plain-language copy, plus a "delete my scan data" action.
- i18n: new keys in the translation bundles for the wizard, errors, quota exhaustion, and consent copy.

**Platforms.** Camera on native via `expo-image-picker`'s camera launch (already a dependency at 57.0.15); on web, file input plus gallery pick. No `expo-camera` dependency is added — the old branch used it, but picker-launched capture avoids a permissions surface and a package we would otherwise only use here.

---

## 8. Deployment

**ML subsystem** — its own `docker-compose.yml` in the private repo:

```
ml-web      Django + gunicorn, DRF API + admin
ml-worker   python manage.py run_ml_worker   (single instance)
ml-db       postgres:16-alpine
volumes     media (attachments), model-cache (ONNX models)
```

CI in the private repo builds to a private registry. Nothing is published.

**cookpal compose (`opencookbook`)** — two new optional variables in `.env`, empty by default, passed to the backend:

```
ML_SERVICE_URL=
ML_API_TOKEN=
```

No `ml` service is added to the public `docker-compose.yml` — it would advertise an image nobody can pull. `opencookbook-proxy` is untouched; the ML subsystem is reachable only from the cookpal backend, never from the internet.

**Resources.** RapidOCR PPOCRV6 medium on CPU is roughly 1–3s per page at 2400px with 2–4 threads, ~1.5GB RSS with models loaded. Size the box accordingly and pin `intra_op_num_threads` so the worker cannot starve the web tier.

---

## 9. Phases

Each phase ends in something demonstrable. Phase 2 is the first one users see.

**Phase 0 — Skeleton and contract.**
Private repo scaffolded: Django + DRF, models, migrations, token auth with revocation, generic job envelope, health endpoint, Django admin over the models. Worker process that claims jobs and runs a trivial echo handler. Docker compose stack up. *Done when:* a token can be created in the admin, `curl` can submit an echo job and poll its result, and revoking the token makes the next request `401`.

**Phase 1 — End-to-end thin slice.**
`recipe_ocr` handler doing stages A–D only, returning raw OCR lines with geometry. Cookpal side: config, proxy, `MlJob` entity, `V13__.sql`, controller, poller, quota, feature flag, `MlDisabledIntegrationTest`. Frontend: capture, crop, submit, poll, show raw text. First cut of the admin test bench, showing raw OCR output over the image. *Done when:* a photo taken in the app produces OCR text on screen, the same image run through the test bench shows the same lines, and removing the config makes the feature vanish.

**Phase 2 — Rule-based extraction, shippable.**
Stages E, F, H, I, and multi-page stitching. DE + EN lexicons. Result schema complete. `RecipeOcrImportService` mapping to `Recipe` with unit validation. Wizard pre-fill via `draftRecipe`. Multi-page capture in the app. Consent toggle and retention/purge. Test bench upgraded to render the full extracted recipe with per-field confidences. *Done when:* a photographed cookbook page opens the recipe wizard with title, ingredients and steps filled in well enough to be worth correcting rather than retyping. **This is the release.**

**Phase 3 — Classifier and training loop.**
Labelling screen in the ML admin. Seed corpus labelled. CRF trained, evaluated on held-out data, versioned as a `ModelArtifact`, wired in as stage G. *Done when:* end-to-end field accuracy on the eval set improves measurably over Phase 2's rules alone, and the numbers are visible in the admin.

**Phase 4 — Admin statistics and quota hardening.**
The stats dashboard (§4.6) in full. Per-token rolling quotas and the in-flight cap enforced and visualised. Failure drill-down. *Done when:* I can answer "is it healthy, who is using it, what is failing and why" from one screen.

**Phase 5 — Prove the envelope.**
Implement one further job type — ingredient deduplication is the cheaper of the two and the `research/` notebooks already explore it. *Done when:* the new usecase required a handler and a result schema, and touched no queue, auth, quota or admin code.

---

## 10. Risks and open items

| Risk | Mitigation |
|---|---|
| Two-column cookbook layouts interleave into nonsense | Column detection is explicit in stage D and gets its own test fixtures early; if it proves unreliable, fall back to asking the user to crop columns separately |
| Rule layer alone is too weak to ship at Phase 2 | Phase 1 gives real OCR output on real photos before we commit; if rules underperform, Phase 3 moves ahead of the release rather than after it |
| Two ingredient parsers drift apart | §5H's validation fallback keeps cookpal's vocabulary authoritative; shared test fixtures across both languages |
| Handwritten recipes | Out of scope (§2 non-goals). The UI says so rather than failing mysteriously |
| A bad extraction change ships unnoticed | The admin test bench runs the real handler through the real queue, and Phase 3's eval metrics land in `ModelArtifact` where a regression is visible |
| Consent withdrawal must actually delete | `DELETE /api/v1/ml/training-data` fans out; covered by an integration test, not just intent |
| Single worker becomes a bottleneck | Claim is already `SKIP LOCKED`, so scaling to N workers is a compose change, not a rewrite |
| Model download at first request | Downloaded at container start into a persistent volume |

**Resolved since the first draft**

Token revocation and the in-flight cap are in (decisions 13 and 14). Ingredient parsing is structured-plus-validation as described in §5H. PDF input is out (§2 non-goals). Multi-page capture is in Phase 2 (decision 16). The admin test bench is in (decision 15), first cut in Phase 1.
