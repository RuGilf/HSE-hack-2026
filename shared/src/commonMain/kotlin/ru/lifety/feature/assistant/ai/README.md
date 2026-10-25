# Codex 3: Dashboard, Assistant and Health

All public screen entry points retain `XxxScreen(modifier: Modifier = Modifier)`.
No models, repository interfaces, routes, Gradle, Manifest or SQL schema changes.

## Core integration

1. Supply the shared real `Repositories` through the existing `LifetyApp(repositories)`
   composition root. No feature-specific registration is needed. Null repositories
   render a clear disconnected state; no fake production data is inserted.
2. Repository flows must emit the initial local value and subsequent successful writes.
   Dashboard observes all three repositories, Health observes the latest snapshot,
   Assistant reads only explicitly permitted repositories when Preview is pressed.
3. The Dashboard assistant card describes the existing Assistant tab. A direct card
   callback needs a Core-owned navigation integration; feature packages do not import
   one another and no route has been added.
4. No budget limit exists in the agreed model/repository contracts. Dashboard shows
   actual month-to-date expenses separately by currency and an unavailable-budget
   message. A real budget source must be agreed with Core/Finance before showing a
   remaining balance. No fabricated zero balance or income is used.
5. Health Connect is deferred: it needs a Core-approved Android dependency, permissions,
   availability checks, permission launcher and Manifest integration. Manual sleep and
   steps already use `HealthRepository.update` and work without any health permission.

## AI behavior and privacy

- Settings and the API key live in process memory only. Leaving the Assistant tab
  retains settings for that process; process death clears them. The key can be cleared
  explicitly. No credentials enter saved state, SQLite, preferences or logs.
- All sharing switches default to false. Neither startup, local data updates nor
  changes to permissions or settings send requests. A denied repository is not read.
- Preview creates an immutable bounded context (up to 40 task titles, 100 characters
  each, plus total/completed/omitted counts). Original IDs are replaced by T1, T2, etc.
  Financial context contains only per-currency totals/counts for the last 30 days;
  merchants, categories, sources and expense IDs are omitted. Null data stay unknown.
- Preview shows the exact user-context JSON, destination and model. Settings or goal
  changes invalidate it. The Analyze button sends the displayed snapshot, even if a
  repository changes after preview. Refresh Preview to include newer data.
- Enter the full HTTPS OpenAI-compatible chat completions URL and provider model.
  Embedded credentials, query parameters, fragments and non-HTTPS URLs are rejected.
  Requests use bearer authentication, JSON response mode and a supporting system prompt.
  Redirects are disabled. The existing Core Ktor factory supplies the real platform
  engine and timeouts; clients are closed after the request. Feature code does not schedule retries.
- Network/HTTP/malformed response failures become recoverable UI messages. Cancellation
  propagates. Provider exception text and error bodies are never displayed or logged.
- Structured recommendations include explanations and optional task references and
  proposed UTC times. Unknown task references and invalid results are rejected. There
  is no write path from Assistant to tasks, finance or health; changes remain manual.
- The prompt requests supportive feedback, accounts for measurement age and prohibits
  medical diagnoses and treatment advice. This is a planning assistant, not medical care.

## Dashboard and health semantics

- Today uses the device's local calendar day, including DST. A task counts once if
  scheduled today or due today. Completion statistics apply to that set. Overdue and
  the next five outstanding deadlines are shown separately; today shows up to six
  rows with an overflow count.
- Expenses cover the current local calendar month through now, excluding future entries.
  Currencies are never merged and money is never converted to Double. Overflow renders
  as unavailable, never a wrapped negative amount. A timer refreshes boundaries every
  30 seconds while the dashboard is composed.
- Sleep and steps show the latest snapshot with its date; missing values do not become
  zero. Health manual input creates a new timestamped snapshot and explains replacement
  semantics. Sleep supports hours/minutes, steps support integer input; invalid data
  and write failures are surfaced without deleting form contents.
- UI uses Material 3 surface/container colors, typography, cards and progress indicators.
  Loading, errors, empty values and disconnected storage have separate states.

## Checks

Required baseline command: `gradlew.bat :androidApp:assembleDebug :shared:testAndroidHostTest`.
Tests cover privacy combinations, absence of background requests, frozen previews,
request JSON/authentication, denied-category transport rejection, malformed/HTTP errors,
cancellation, local date/DST boundaries, currency precision/overflow and manual health.

Actual execution results and environment limitations are recorded in the handoff report;
the existence of a test source does not imply that the test ran. A live provider call
requires a user-entered key and model. Android runtime/UI and iOS compilation require
their respective SDK/device environments.

## Changed files

All paths below start with `shared/src/<source set>/kotlin/ru/lifety/feature/`.

| Source set / directory | Files |
| --- | --- |
| commonMain / assistant | AssistantScreen.kt, AssistantController.kt |
| commonMain / assistant/ai | AiProvider.kt, ContextBuilder.kt, README.md |
| commonMain / dashboard | DashboardScreen.kt, DashboardState.kt |
| commonMain / health | HealthScreen.kt, HealthController.kt |
| androidMain | assistant/ai/AssistantTime.android.kt, dashboard/DashboardDates.android.kt, health/HealthDates.android.kt |
| iosMain | assistant/ai/AssistantTime.ios.kt, dashboard/DashboardDates.ios.kt, health/HealthDates.ios.kt |
| commonTest | assistant/ai/AiContractTest.kt, dashboard/DashboardSummaryTest.kt, health/ManualHealthTest.kt |
| androidHostTest | assistant/AssistantPrivacyTest.kt, assistant/ai/KtorAiProviderTest.kt, dashboard/DashboardDatesTest.kt, health/HealthControllerTest.kt |

## Verified handoff — 25 October 2026

- Windows x86_64, Microsoft OpenJDK 21.0.8, Gradle 9.5.1,
  Android SDK Platform 37.0 revision 2, Build Tools 36.0.0.
- `gradlew.bat --no-daemon :androidApp:assembleDebug :shared:testAndroidHostTest`:
  **BUILD SUCCESSFUL**.
- JUnit XML: **31 tests, 0 failures, 0 errors, 0 skipped**. This includes 26 feature
  tests added here and all 5 baseline contract/database tests.
- Debug APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
- Staged diff whitespace and ownership checks pass; all 22 changed files are inside
  Assistant, Dashboard, Health or their corresponding platform adapters/tests.
- A live external LLM request was not run: no user API key/model was supplied.
  Request construction, exact preview payload, authentication, redirect blocking,
  HTTP/malformed/transport errors and cancellation were checked using local fixtures.
- Android device launch and visual interaction were not checked. iOS compilation and
  runtime were not checked: the Windows host cannot run the Apple targets.
- Health Connect, HealthKit and P2 additions are not implemented. P0 has no new dependency,
  Manifest, route, model, repository-contract or schema requirements; Core must provide
  the existing shared repository container. Budget limits and Health Connect need the
  separate Core/Finance integration described above.
