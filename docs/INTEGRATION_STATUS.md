# Core + Finance integration — 25 октября 2026

Ветка назначения: `feature/core`, worktree `/Users/rugilf/Projects/lifety-core`.
Источники: Core `c3d3dc1`, Finance `origin/feature/finance` `847f405`.
Основная ветка main не изменялась. Объединение разрешено отдельным запросом
пользователя после завершения Core P0.

## Интеграционные изменения

- FinanceScreen включён через прежний route `finance` и получает общий
  SqlDelightExpenseRepository из production LocalRepositories.
- Manifest зарегистрировал ExpenseNotificationListener по фрагменту handoff.
  Доступ к уведомлениям остаётся default-off и запрашивается только по действию
  пользователя. POST_NOTIFICATIONS не добавлялся; отдельный процесс не используется.
- В FinancePlatform.ios.kt конструктор NSDate с недоступным named parameter
  заменён на `NSDate.dateWithTimeIntervalSince1970`, совместимый с текущим Kotlin/Native.
- Общие модели, repository interfaces, DB schema и Gradle-зависимости не менялись
  при объединении. Конфликтов Git не было.

## Проверено

```sh
./gradlew :androidApp:build :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64
```

Полная сборка успешна: Android debug/release + lint, iOS Simulator compile,
34 host tests (13 Core/Planner + 21 Finance/Notifications), 0 failures/errors/skipped.

Запущен временный AVD Android 15 API 35 arm64 на установленном SDK system image.
Установлен объединённый debug APK, MainActivity cold start — success.
Через UI выполнено:

1. Создание задачи `Lifety smoke task` с scheduledAt.
2. Отображение задачи на выбранном дне календаря и переход в редактор.
3. Изменение заголовка на `Lifety calendar edit`, возврат в календарь.
4. Добавление ручного расхода `Smoke Shop` на 125,50 RUB.
5. Создание демо-уведомления и явное подтверждение расхода
   `[ДЕМО] COFFEE POINT` на 349,90 RUB.
6. Принудительная остановка процесса, копирование Android SQLite и проверка:
   task сохранён, amountMinor расходов — 12550 и 34990, sources — manual и
   demo-notification. Повторный запуск приложения, оба экрана восстановили данные,
   итог Finance — 475,40 RUB. AndroidRuntime exceptions отсутствуют.
7. `dumpsys package` подтверждает регистрацию ExpenseNotificationListener
   с BIND_NOTIFICATION_LISTENER_SERVICE; реальный доступ не включался.

Тестовые записи находятся только в изолированном тестовом AVD; не добавляются
в production код и не являются стартовыми данными приложения.

## Что остаётся проверить

Физический Android, runtime API 24–25, настоящий банковский notification,
включение listener access в Settings, iOS framework linking и runtime.
Dashboard/Assistant/Health из третьей ветки ещё не объединены.

## Следующие шаги

Объединить готовую ветку третьего агента, затем пройти единый демонстрационный
сценарий: обзор → задача → календарь → расход → AI/health, в пределах фактически
реализованной функциональности. После этого унифицировать визуальные детали
экранов и повторить smoke test на целевом физическом устройстве.
