# Finance & Notifications — handoff for Codex 1

Branch: `feature/finance`, base `a89e0df`.

## Integration

No public model/repository/route signatures changed. `FinanceScreen(modifier: Modifier = Modifier)`
reads `LocalRepositories.current?.expenses`. Pass the existing, single persistent `Repositories`
container to `LifetyApp(repositories)` in the composition root. With null DI the screen explicitly
shows storage unavailable and disables writes; it never installs a production in-memory store.
`FinanceContent(state, onSave, onDelete, onRetry, modifier, storageConnected)` is also available for
explicit state/callback injection. Writes return success only after `ExpenseRepository.upsert/delete`.
The service does not need a database or DI: it only publishes parsed, unconfirmed suggestions.

Apply this exact fragment INSIDE `<application>` in `androidApp/src/main/AndroidManifest.xml`:

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

Do not add a separate process: the listener and Compose confirmation inbox share app memory.
No runtime `POST_NOTIFICATIONS`, broadcast receiver, new dependency, schema or migration is needed.
`BIND_NOTIFICATION_LISTENER_SERVICE` belongs on the service, not a `<uses-permission>` request.
User enables selected banks in Finance, then explicitly grants notification access in Android settings.
Until the Manifest fragment is integrated, the screen explains that registration is missing.
Official service declaration/lifecycle reference:
https://developer.android.com/reference/android/service/notification/NotificationListenerService

## Delivered

- Manual add/edit/delete through ExpenseRepository, merchant, exact positive minor-unit Long,
  currency, category and local date; stable IDs and original source/time preserved when editing.
- Inclusive local-date periods, month/7-day/all-time presets, date groups and category breakdown.
  RUB/USD/EUR manual entry; existing other ISO currencies are displayed separately too.
  No cross-currency addition, no floating-point money or silent overflow/rounding.
- Optional per-currency budget for the currently selected period, explicitly session-only.
- Android listener, default-off persisted consent and selected banks, no raw notification logging,
  persistence or network. Sber/T-Bank/Alfa example formats; conservative parsing of amount, currency,
  merchant and operation. Transfer/income/refund/failure/authorization messages do not enter inbox.
- Parser registry accepts additional `BankMessageParser` implementations without editing Service.
- Confirmation/edit/rejection before saving. Deterministic notification IDs prevent a replay from
  creating a second repository row; already-saved suggestions are hidden. No auto-saving.
- Demo source uses the same parser, inbox, confirmation form and repository. It works without any
  permission or actual bank app. Merchant/source/list badge explicitly mark demo transactions.

## Demonstration

1. Integrate the normal persistent Repositories container (no special finance wiring).
2. Open Finance → add a manual expense → edit amount/category → delete with confirmation.
3. Choose dates and currency; compare category/period totals and set a session budget.
4. Tap `ДЕМО: тестовое уведомление`: a 349.90 RUB COFFEE POINT suggestion appears.
5. `Проверить и сохранить` → inspect/edit fields → save. A [ДЕМО] transaction appears in the list.
6. To test live import, integrate the service fragment, select a bank, grant access explicitly,
   then post a supported bank purchase notification. It remains unconfirmed until step 5.

## Deliberate limits

- Pending suggestions are transient, bounded to 50; a process kill loses unconfirmed suggestions.
  A main-thread recent-ID set (500) suppresses repeats/dismissed suggestions in the process.
  Confirmed expenses persist ONLY in the existing repository and use stable IDs across restarts.
- Event identity is bank package + Android notification key + original `Notification.when`
  (fallback postTime). A bank that rewrites original timestamps for the same purchase requires a
  bank-specific transaction-ID parser. Formats/app package names evolve; this is an extensible
  small template set, not universal bank support. Review before saving remains mandatory.
- Budget is not a database field: it resets when leaving/recreating the finance session. Durable
  budget preferences/receipt links require a future contract owned by Core. No P2 receipt API.
- Date input supports 1900–9999. Calendar boundaries use the device timezone, not fixed UTC days.

## Plan / verification ledger

- [x] P0: exact money/date/period tests, repository CRUD/error/cancellation tests; Compose screen.
- [x] P1: local parser/operation/dedup/demo tests; Android opt-in and listener adapter.
- [x] Fresh independent review. Fixed failed observation recovery, unfinished/authorization payment
  rejection and preserving title context; regression tests were observed RED then GREEN.
- [x] `:androidApp:build :shared:testAndroidHostTest :shared:compileCommonMainKotlinMetadata` passed.
  Host suite: 26 tests, 0 failures/errors/skipped. Android lint: 0 errors, 18 warnings.
Commit target: `feature/finance` only; never merge into main.

Validation is reported with actual commands/results in the final handoff. No physical device or
emulator is attached; Android UI, a real bank notification and iOS compilation/runtime are not
verified on this Windows host. SDK 37 was installed with user-approved license acceptance.

## Follow-up delivery (2026-10-25)

The existing baseline feature commit is retained; history is not rewritten.

1. P0: explicit day / rolling-seven-day / full-calendar-month totals and selection;
   no out-of-period fallback when a total overflows; external Flow update regression.
2. P1: selected-bank intake with visible hidden/unrecognized outcomes, confirmation replay
   that preserves an already saved/edited expense, and consent/dedup/demo tests.
3. P2: current-calendar-month budget/progress and category bar chart, using inherited
   MaterialTheme tokens. Budget remains session-only because no Core budget contract exists.

Shared-theme integration for Codex 1: the checked-out foundation still has a bare
`MaterialTheme {}` in LifetyApp and no shared color palette. Provide Lifety's near-black
background, dark surfaces, bright-green primary, white text and rounded shapes there.
Finance intentionally consumes `MaterialTheme.colorScheme` / `MaterialTheme.shapes`;
it does not override the application's theme. The shared theme is required to display
all screens consistently in the agreed dark concept.
