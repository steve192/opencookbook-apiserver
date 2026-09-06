# Cookpal admin panel

The administration panel for a cookpal instance: accounts, recipes, ingredients, public shares,
recipe scans and Bring exports. It is a React app that the api server serves under `/admin`, and
it talks to the `/api/v1/admin/...` endpoints as a signed in user holding the `ADMIN` role.

## Working on it

Two terminals, no build-and-copy in between - vite serves the panel with hot reload and proxies
everything under `/api` to a running api server.

```
cd .. && mvn spring-boot:run          # the api server on :8080
npm install && npm run dev            # the panel on http://localhost:5173/admin/
```

Point the proxy somewhere else with `BACKEND_URL=https://beta.cookpal.io npm run dev`.

The panel refuses anybody without the `ADMIN` role, so the first account has to be given it
directly after signing up:

```
psql -c "update cookpal_user set roles = 'ADMIN' where email_address = 'me@example.com'"
```

## Building it

The maven build does it. `mvn package` runs `npm run build:if-changed` here (through
frontend-maven-plugin, which brings its own node), and the bundle is written straight into
`../src/main/resources/static`, so every jar carries the panel that belongs to it. Nothing has to
be built or copied by hand.

The npm script compares a hash of the sources, the config and `package-lock.json` against the
bundle that is already there and does nothing when they match, so a build that changes no
frontend file costs about a second. `npm ci` is only run when the lock file changed or
`node_modules` is gone.

```
mvn package                  # builds the panel if it changed, then the jar
mvn package -DskipAdminUi    # leaves the bundle that is already there alone
npm run build                # just the bundle, unconditionally
npm run lint
```

## How it is put together

```
src/
  api/          one typed module per resource, over a single http client that signs
                requests, renews an expired token once and retries
  hooks/        loading data (useAsyncData) and running actions (useActionRunner)
  components/   collection/  the one searchable, sortable, selectable list, as a table
                             where there is room and as cards where there is not
                form/        the one form dialog, built from field definitions
  screens/      one file per screen: what its fields are, and what can be done to them
  navigation/   the drawer and the entries in it
```

A screen describes its entity once - the field definitions drive the table columns, the cards and
the details panel alike - and says which actions a row and a selection support. Adding a screen
means adding an api module, a list of fields and a list of actions, not another table.
