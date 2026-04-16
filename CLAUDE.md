# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

QuizMemo — daily spaced-repetition quiz. Each day a user plays a single session, answering one multiple-choice question at a time until they get one wrong or run out of questions. Each correct answer = 1 point. The **next-question ordering** is the core rule of the product.

## Going public

The repo is structured so no secrets or private content end up in committed files. Before pushing for the first time, `grep` the tree for obvious markers (your OAuth client ID, your JWT signing key, your LAN IP) — anything that shows up outside `.env`, `local.properties`, `.claude/`, or `db/*.local.sql` is a mistake. See **Secrets** below for the three stores and the rotation rule.

## Architecture

Three pieces, all wired together locally via `docker-compose.yml`:

- `backend/QuizMemo.Api/` — ASP.NET Core (.NET 10) minimal-API. JWT auth (username/password + Google Sign-In), EF Core + Npgsql, endpoints grouped under `/auth` and `/quiz`.
- `db/` — PostgreSQL 16 (runs in Docker). Schema is owned by EF Core migrations. Seed files follow a `*.example.sql` (committed) + `*.local.sql` (gitignored, private) convention — see **Seeding** below.
- `android/` — Native Kotlin app (Jetpack Compose + Retrofit + kotlinx.serialization + Credential Manager for Google Sign-In). Single activity with a login screen and a quiz screen.

### Backend layout (why, not what)

- `Entities/` — plain POCOs. `Answer` stores `SessionDate` (DateOnly) + `SessionAttempt` (int) so "today's current run" is the tuple `(UserId, SessionDate, SessionAttempt)`. Multiple attempts per day are allowed — each `/quiz/session/reset` bumps the attempt number. The per-user pointer (`User.SessionAttempt`, `User.SessionAttemptDate`) tracks which attempt new answers belong to. `Question.Level` is a string (`"A1"`..`"C2"`) used by the CEFR estimator.
- `Data/AppDbContext.cs` — indexes on `(UserId, SessionDate, SessionAttempt)` and `(UserId, QuestionId)` are load-bearing for the ordering query; don't drop them.
- `Endpoints/QuizEndpoints.cs` — **the ordering rule lives here**. `GET /quiz/next` sorts by:
  1. never-answered questions first,
  2. then by historical error count (descending),
  3. then by historical correct count (ascending — well-known questions appear last).
  If the user has any incorrect answer in the *current attempt*, `/quiz/next` returns `null` and `/quiz/answer` rejects further submissions — "one miss ends the run". But the user can call `POST /quiz/session/reset` to start a fresh attempt (zero points, no incorrect) as many times as they want. Historical answers from prior attempts are kept — spaced-repetition ordering and CEFR estimation use them all.
- `Endpoints/QuizEndpoints.cs::/quiz/dashboard` — aggregated endpoint the Android dashboard reads: current session status, lifetime records (sessions played, answers, correct, best run), estimated CEFR level, progress required to reach the next level (attempts + accuracy gap), per-level stats, and recommendation text.
- `Quiz/CefrLevels.cs` — CEFR estimation config and recommendation text. **The rule**: user's estimated level is the highest CEFR band where they have ≥3 attempts AND ≥70% accuracy, AND every lower band is also passed. The iteration stops at the first failure, so a user who is 90% on A1 but 50% on A2 gets estimated as A1 (not A2). If no band passes yet → `null` with "not enough data" recommendation.
- `Endpoints/QuizEndpoints.cs::/quiz/level` — calls `CefrLevels` after joining answers with question levels. Returns per-level stats + estimated level + next-level recommendation text. The Android `LevelCard` reads this.
- `Auth/JwtService.cs` — symmetric HS256, 30-day token. The signing key comes from `Jwt:Key` in config. `appsettings.json` contains no secret — the key must be provided via user-secrets (for `dotnet run`) or `Jwt__Key` env var (for docker-compose/prod). `Program.cs` throws at startup if the key is missing. See **Secrets** below.
- `Endpoints/AuthEndpoints.cs` — exposes `/auth/register`, `/auth/login` (username/password), and `/auth/google` (Google ID token → our JWT). `/auth/google` validates the incoming ID token with `Google.Apis.Auth`, looks up the user first by `GoogleSubject` and then by `Email` (for linking existing password accounts), upserts, and issues our own JWT. Google-only users (`PasswordHash == null`) cannot use `/auth/login`.

### Android layout

- `app/build.gradle.kts` — reads `quizmemo.api.baseUrl` and `quizmemo.google.webClientId` from `local.properties` at build time and injects them as `BuildConfig.API_BASE_URL` and `BuildConfig.GOOGLE_WEB_CLIENT_ID`. `buildFeatures { buildConfig = true }` is required. Missing values fall back to the emulator-friendly defaults / empty strings — `GoogleAuth.kt` throws a clear error if the Client ID is empty.
- `network/ApiClient.kt` — base URL comes from `BuildConfig.API_BASE_URL`. `AuthInterceptor` holds the JWT in memory (singleton). If you ever need persistence, the `datastore-preferences` dependency is already declared.
- `auth/GoogleAuth.kt` — wraps Credential Manager + Google Identity SDK. `serverClientId` comes from `BuildConfig.GOOGLE_WEB_CLIENT_ID` (must be the Web OAuth client, not the Android one).
- `ui/QuizApp.kt` — a single composable that switches between `LoginScreen`, `DashboardScreen`, and `QuizScreen` via an in-memory `Screen` enum. Dashboard loads `/quiz/dashboard` and offers "Continue"/"Start/Restart session" buttons; Restart calls `/quiz/session/reset` before entering the quiz. QuizScreen only renders the question + points — level cards live on the dashboard. A `quizEpoch` counter keyed around `QuizScreen` forces a full state reset when re-entering. State is intentionally not hoisted into a ViewModel yet; promote it if screens multiply.

## Secrets

The repo is designed to contain **no secrets in committed files**. There are three independent stores depending on how you run each piece.

### 1. Backend via `dotnet run` (local dev) → dotnet user-secrets

The `QuizMemo.Api` project has a `UserSecretsId` in its csproj. The secret store lives outside the repo at `%APPDATA%\Microsoft\UserSecrets\<id>\secrets.json`.

Required keys:
- `Jwt:Key` — HS256 signing key, must be ≥32 chars
- `ConnectionStrings:Postgres` — full Npgsql connection string
- `Google:ClientId` — Web OAuth client ID from GCP

Set them with (run from `backend/QuizMemo.Api/`):
```bash
dotnet user-secrets set "Jwt:Key" "<at-least-32-random-chars>"
dotnet user-secrets set "ConnectionStrings:Postgres" "Host=localhost;Port=5432;Database=quizmemo;Username=<user>;Password=<password>"
dotnet user-secrets set "Google:ClientId" "<your-project>-<suffix>.apps.googleusercontent.com"
```

`appsettings.json` intentionally contains only non-sensitive defaults (`Jwt:Issuer`, logging, `AllowedHosts`). If a required key is missing, the app fails fast on startup — no silent fallback.

### 2. Backend via docker-compose → `.env` file at repo root

`docker compose` auto-reads `.env` and substitutes `${VAR}` references in `docker-compose.yml`. The backend inside the container gets real env vars (e.g. `Google__ClientId`) which ASP.NET Core picks up through `AddEnvironmentVariables`.

- `.env.example` is committed and documents every required variable.
- `.env` is gitignored (see the `!.env.example` exception in `.gitignore`).
- Copy the example to `.env` and fill in real values the first time you clone.

### 3. Android build → `android/local.properties`

Android Studio auto-generates `local.properties` on first open (for `sdk.dir`) and it's gitignored by default. We piggyback on it to inject build-time constants via `buildConfigField`.

Required keys (in `android/local.properties`):
- `quizmemo.api.baseUrl` — e.g. `http://10.0.2.2:8080/` for emulator, or `http://<LAN-IP>:5080/` for a physical device
- `quizmemo.google.webClientId` — same Web Client ID as the backend

These feed `BuildConfig.API_BASE_URL` (read by `ApiClient.kt`) and `BuildConfig.GOOGLE_WEB_CLIENT_ID` (read by `auth/GoogleAuth.kt`). `android/local.properties.example` is the committed template — copy and edit.

### Rotation rule

When you change a secret, update **all three stores**. They're independent — user-secrets doesn't back up to `.env` and vice versa. A good sanity check is to grep the repo for the old value before committing; it should only appear in ignored files.

## Common commands

### Local dev (docker-compose)

```bash
# One-time: copy the env template and fill it in
cp .env.example .env
# edit .env with real values

# Bring up postgres + api
docker compose up --build

# Just the database (run API from the IDE / dotnet run)
docker compose up postgres

# Seed sample questions — pick one:
docker exec -i quizmemo-postgres psql -U quizmemo -d quizmemo < db/seed.sql
docker exec -i quizmemo-postgres psql -U quizmemo -d quizmemo < db/seed_english.example.sql
# or your own curated file:
docker exec -i quizmemo-postgres psql -U quizmemo -d quizmemo < db/seed_english.local.sql
```

### Backend only

```bash
cd backend/QuizMemo.Api

# First time only — set required secrets (see Secrets section):
dotnet user-secrets set "Jwt:Key" "<at-least-32-random-chars>"
dotnet user-secrets set "ConnectionStrings:Postgres" "Host=localhost;Port=5432;Database=quizmemo;Username=<u>;Password=<p>"
dotnet user-secrets set "Google:ClientId" "<web-oauth-client>.apps.googleusercontent.com"

# First time: create the initial migration (DbContext auto-applies on startup)
dotnet ef migrations add Initial

# Run. Expects postgres reachable at the connection string above.
# By default the launch profile binds http://0.0.0.0:5080 so a phone on the
# same LAN can reach the API — adjust in Properties/launchSettings.json if
# you only want localhost.
dotnet run

# Add a new migration after entity changes
dotnet ef migrations add <Name>
```

`Program.cs` calls `db.Database.Migrate()` on startup, so any pending migrations apply automatically — you do **not** need `dotnet ef database update` in normal flow. If any required secret is missing, the app throws at startup rather than running with a silent default.

### Android

1. Open `android/` in Android Studio (Giraffe or newer). Gradle wrapper isn't committed — Android Studio generates it on first import.
2. First build will create `android/local.properties` (for `sdk.dir`). Copy `android/local.properties.example` into it (append, don't replace the `sdk.dir` line) and fill in `quizmemo.api.baseUrl` and `quizmemo.google.webClientId`.
3. Pick a run target:
    - **Emulator (AVD)**: set `quizmemo.api.baseUrl=http://10.0.2.2:8080/` — `10.0.2.2` aliases the host loopback. Simplest first-run path.
    - **Physical device over Wi-Fi** (e.g. Galaxy A55): set `quizmemo.api.baseUrl=http://<your-PC-LAN-IP>:5080/`. You also need to: (a) have `launchSettings.json` bind `0.0.0.0` (already set), (b) open Windows Firewall inbound 5080 for the Private profile, (c) confirm your phone and PC are on the same network. If Surfshark/other VPN is active on the PC, disconnect it or add a LAN split-tunnel exception.
4. When you change `local.properties`, do **Build → Rebuild Project** so the new `BuildConfig` values bake into the APK — just re-running sometimes reuses the old values.

## Conventions & gotchas

- **Time zones**: `SessionDate` uses `DateOnly.FromDateTime(DateTime.UtcNow)` — sessions roll over at UTC midnight, not local midnight. If this matters to users, change it in `QuizEndpoints.cs` in one place (all three endpoints compute `today` the same way).
- **Question ordering is in-memory**: `/quiz/next` pulls all questions + stats and orders client-side (in the API). Fine for hundreds of questions; rewrite as a single SQL query if you grow past a few thousand.
- **JWT claim for user id**: stored in `sub`. `GetUserId` in `QuizEndpoints.cs` falls back to `ClaimTypes.NameIdentifier` because ASP.NET Core sometimes remaps it.
- **Seeding**: there is no API endpoint for creating questions — intentional for now. Three committed options: `db/seed.sql` (3 generic trivia questions, the oldest scaffold), `db/seed_english.example.sql` (8 generic English phrasing questions, one per CEFR level, a template). Any real, private content belongs in a `db/*.local.sql` file which is gitignored. Pipe whichever you want into the container via `docker exec -i ... psql ... < file`. EF `HasData` in a migration is another option if you want questions to travel with the schema.
- **CEFR thresholds** (`CefrLevels.cs`): `MinAttempts = 3`, `PassThreshold = 0.7`. These numbers are deliberately lenient early on so a user gets a rough signal quickly. Tune by editing that single file — don't scatter magic numbers through endpoints.
- **Estimator is gap-stopping**: the loop breaks at the first level that fails to pass, so a user strong at C1 but who hasn't been tested at B2 will still cap at the highest passed band. This is intentional — we trust the curriculum ordering, not cherry-picked wins.
- **Cleartext HTTP on Android**: `usesCleartextTraffic="true"` is set so the emulator can hit the local API. Remove before shipping.

## Google Sign-In setup

Google login is wired end-to-end (Android Credential Manager → `POST /auth/google` → Google token validation → JWT), but it will not work until you fill in two secrets in one spot each. Both must point at **the same GCP project**.

### 1. Create OAuth clients in Google Cloud Console

1. Open Google Cloud Console → **APIs & Services → Credentials**.
2. Create (or pick) a project, and configure the **OAuth consent screen** (External / Testing is fine for dev — add your own email as a test user).
3. Create **two** OAuth 2.0 Client IDs:
    - **Web application** — this is the "server" client. Name it e.g. `QuizMemo Backend`. No redirect URIs needed for native flows. **Copy the client ID** — it looks like `123456-abc.apps.googleusercontent.com`. This is what the backend validates the `aud` claim against, and what Android passes as `serverClientId`.
    - **Android** — name it e.g. `QuizMemo Android`. Package name: `com.quizmemo`. SHA-1: get it from your debug keystore with `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android` and paste it in. No client ID needed in code — this one just authorizes the Android app to request tokens.

### 2. Wire the Web client ID into the three config slots

The same Web Client ID goes in three places, all of which are gitignored:

- **Backend (`dotnet run`)** — `dotnet user-secrets set "Google:ClientId" "<web-client-id>"` from `backend/QuizMemo.Api/`.
- **Backend (docker-compose)** — `GOOGLE_CLIENT_ID=...` in `.env` at repo root. `docker-compose.yml` substitutes it into `Google__ClientId` on the api container.
- **Android** — `quizmemo.google.webClientId=...` in `android/local.properties`. Read at build time into `BuildConfig.GOOGLE_WEB_CLIENT_ID` and consumed by `auth/GoogleAuth.kt`.

The Android client ID doesn't get referenced in code at all — Google's SDK verifies the app identity via the package name + SHA-1 you registered.

### 3. How the flow works (why there are two client IDs)

- Android kicks off Credential Manager with `GetGoogleIdOption.setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)`. This tells Google "mint me an ID token whose audience is this web client".
- Google returns a signed JWT to the app.
- The app POSTs it to `/auth/google` as `{ idToken }`.
- Backend calls `GoogleJsonWebSignature.ValidateAsync(idToken, audience = Google:ClientId)`. Google's library fetches Google's public keys, checks the signature, expiration, issuer, and audience.
- On success, the backend looks up the user by `GoogleSubject` (the stable `sub` claim), creates one if needed, and issues its own JWT (via `JwtService`) that the rest of the app uses.

### 4. Account linking rule (`AuthEndpoints.cs`)

`/auth/google` first looks up by `GoogleSubject`. If that fails, it falls back to matching by `Email` — this is how a user who originally signed up with username/password can link their Google account on next Google sign-in. A user with no password at all (`PasswordHash == null`) is a Google-only account and cannot use `/auth/login`.
