# Finance & Notifications — handoff for Codex 1

Branch: `feature/finance`. Foundation: `a89e0df`. Initial implementation: `847f405`.
Follow-up P0: `ffe9b6f`; P1: `f5cea50`; P2 adds monthly analytics after these separate commits (see branch history).

## Required Core / Android integration

1. Pass the single persistent `Repositories` container to `LifetyApp(repositories)`.
   `FinanceScreen(modifier: Modifier = Modifier)` reads `LocalRepositories.current?.expenses`.
   Null DI shows unavailable storage and disables writes; no fake expenses or alternative DB.
   `FinanceContent` also accepts explicit state plus suspend save/delete/confirm callbacks.
2. Add INSIDE `<application>` in `androidApp/src/main/AndroidManifest.xml`:

```xml
<service
    android:name="ru.lifety.platform.notifications.ExpenseNotificationListener"
    android:label="Lifety — импорт расходов"
    android:exported="false"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

Keep the listener in the app process: it shares the confirmation inbox with Compose.
No new receiver, runtime POST_NOTIFICATIONS, dependency, schema or migration is needed.
BIND_NOTIFICATION_LISTENER_SERVICE belongs on the service, not in uses-permission.
Reference: https://developer.android.com/reference/android/service/notification/NotificationListenerService

3. Provide the shared Lifety theme at the composition root. This checkout still has bare
   `MaterialTheme {}` and no shared palette. Finance inherits background/surface/primary/text
   and shapes from MaterialTheme; it does not introduce an independent theme. Apply the agreed
   near-black background, dark cards, bright-green accent, white typography and rounded shapes
   globally so Finance matches the other screens. No app/root/Gradle/Manifest files changed here.

## P0 — Finance

- Repository-backed manual add/edit/delete; merchant, amount, ISO currency, category, local date.
- Exact positive Long minor units; comma/dot fractional input, strict precision and overflow checks.
- Day, last-seven-local-days and full-calendar-month totals shown together; selectable presets,
  inclusive custom date ranges, all-time filter, per-date groups and empty/error/loading states.
- Currencies never mixed. Existing other ISO currencies are displayed alongside RUB/USD/EUR.
- Current StateFlow observation updates the screen for writes from any feature; errors keep user
  input/list data, cancellation propagates, failed subscriptions can recover after retry/write.
- Even if a sum overflows, the list stays inside the selected currency and date interval.

## P1 — Notifications

- Official NotificationListenerService. Persisted consent defaults off; selected-bank gate runs
  before extracting text. Only Sber/T-Bank/Alfa packages/examples are supported initially.
- Isolated common parser registry; local amount/currency/merchant/direction extraction.
  Income, transfers, refunds, failed and authorization messages do not become purchase drafts.
- Pure NotificationIntake reports hidden/unavailable/unrecognized content with a manual-entry
  fallback. It stores only a status enum and structured drafts, never the notification text.
- Draft confirmation opens the editable expense form. Nothing is written before user approval.
  confirmNotification checks the latest repository snapshot and preserves an existing/edited ID.
- Stable notification IDs, recent-event dedup, bank deselection/opt-out removes unconfirmed bank
  drafts. No banking text logs, network requests, AI calls, production fake repository or second DB.
- Demo button follows the same parser/intake/form/repository flow without permissions or a bank
  purchase; source, merchant and list badge explicitly identify the saved test record.

## P2 — Analytics

- Monthly budget per currency, progress and remaining/overspent amount use the current calendar
  month independently of the selected transaction-list period. Budget survives configuration
  recreation within the screen session, but is not durable storage and resets with a new session.
- Category bar chart displays exact amounts; Float is used only for visual bar fractions.
- Conservative merchant-based category hints are shared by notification and manual-entry flows.
  A manual suggestion never silently replaces a category the user selected.

## Demo and verification

1. Integrate normal persistent DI; open Finance, add/edit/delete a manual expense.
2. Select day/week/month/custom dates and currency; compare totals and category bars.
3. Set the current monthly limit; switch the list period and confirm budget stays monthly.
4. Tap `ДЕМО: тестовое уведомление` → `Проверить и сохранить` → inspect/edit → save.
   The 349.90 RUB COFFEE POINT draft becomes a visibly marked [ДЕМО] expense.
5. For live import, integrate Manifest, select a bank and explicitly grant Android listener access.
   A supported bank notification creates only a draft; a hidden/unknown format explains manual entry.

Tests cover CRUD, external Flow changes, repository failures/cancellation/recovery, exact money,
Long overflow, period/currency boundaries, leap/DST dates, parser examples, title rejection,
consent/package gates, hidden content, duplicate and repeated confirmations, demo and bar fractions.
Final check: Android debug/release build and lint, all 37 host tests, common metadata and
compileKotlinIosSimulatorArm64 passed. 0 test failures/errors/skips; lint has 18 warnings and
0 errors. No Android device/emulator is attached; UI/live-service behavior and iOS app linking/
runtime were not checked. The iOS date adapter factory call was corrected after its compile failure.

## Known limits

- Pending drafts are transient (max 50); process death loses unconfirmed drafts. Recent-ID memory
  covers 500 events. Confirmed expenses persist only in the existing repository.
- Identity uses bank package + Android notification key + original Notification.when (fallback
  postTime). Banks rewriting identity/time for the same purchase need a bank-specific transaction
  identifier parser. This intentionally small template set is not universal bank support.
- Durable budgets need a future Core preferences contract; no incompatible schema was added.
- Date input supports 1900–9999. Receipt scanning/fiscal API is intentionally absent.
## Category colors — 27 October 2026

Eight stable category accents are shared by Android and iOS: chart bars/legend,
transaction markers, editor chips and category suggestions. Tokens live in
`core/ui/LifetyDesign.kt`, ID mapping and decorative components in
`FinanceCategoryStyle.kt`. Unknown IDs use the neutral Other accent and retain
their original labels. Amounts, labels, category IDs and persistence are unchanged.

Android full build/lint, host tests (155 passed, 6 live LLM skips), native iOS tests
(61 passed) and Xcode simulator build passed. On the isolated iPhone 17 QA
simulator, checked all eight editor accents, saving a synthetic 250 RUB Transport
expense, blue/gray category bars, matching transaction markers and the selected
Transport chip after reopening the editor. Physical devices were not tested.
