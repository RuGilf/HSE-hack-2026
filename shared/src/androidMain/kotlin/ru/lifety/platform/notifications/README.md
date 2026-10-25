# Android notifications — Codex 2

Implemented in `ru.lifety.platform.notifications`:

- `ExpenseNotificationListener` extends official `NotificationListenerService`.
- `AndroidNotificationAccess` provides bank selection, explicit opt-in/settings and opt-out.
- `NotificationSettings` persists only consent and selected packages (not transactions/text).
- Parser, operation models, bounded confirmation inbox and demo source are isolated in
  `ru.lifety.feature.finance.notifications` under commonMain and covered by common tests.

Codex 1: register the exact service fragment documented in
`shared/src/commonMain/kotlin/ru/lifety/feature/finance/INTEGRATION.md`.
The normal `LifetyApp(repositories)` injection is sufficient for Finance. No separate database,
new dependency, receiver, runtime notification-posting permission or service DI is needed.
No shared/Manifest/Gradle changes are part of this branch.
