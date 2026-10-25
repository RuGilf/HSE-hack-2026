# Интеграция опубликованных веток — 25 октября 2026

После этой интеграции пользователь объединил PR #1 в main (`2518b8a`).
Последующая доработка Android UI описана в [UI_POLISH.md](UI_POLISH.md).
Ниже сохранён отчёт о первоначальном объединении функций.

Ветка назначения: `feature/core`, worktree `/Users/rugilf/Projects/lifety-core`.
SSH remote: `git@github.com:RuGilf/HSE-hack-2026.git`.
Main не изменяется; force push не используется. Объединение и push разрешены
пользователем отдельным запросом.

## Источники

| Ветка сервера | Проверенный commit | Решение |
| --- | --- | --- |
| `main` | `a89e0df` | Фундамент, без изменений |
| `feature/core` | `b353cd9` до текущей интеграции | Core/Planner + неоновая тема |
| `feature/finance` | `c703a1f` | Объединена; предыдущая версия `847f405` уже была в Core |
| `feature/assistant` | `e30a688` | Объединена: Dashboard, opt-in AI, ручной Health |

Отдельно существует локальная `feature/assistant` (`e63b63c`) в другом worktree.
Она не продолжает опубликованный `e30a688`: содержит альтернативные
`AiAgentService`/`MockAiAgentService`, изменения root DI и in-memory/demo helpers.
Это не готовое fast-forward обновление удалённой ветки. Её не объединяли и не
переписывали: в интегрированной версии сохранён опубликованный opt-in API с
предпросмотром контекста и текущими публичными Core-контрактами.
Локальная `feature/finance` остаётся в своём worktree на прежнем фундаменте;
источник финансового обновления — именно remote branch.

## Что объединено

- Finance: день/последние семь локальных дней/календарный месяц, отдельные валюты,
  месячный лимит в сессии, прогресс и категории, подсказка категории при вводе.
- Notifications: объяснение скрытого/нераспознанного сообщения и ручной fallback;
  повторное подтверждение не перезаписывает уже исправленный расход.
- Dashboard: реальные задачи за сегодня, дедлайны, расходы месяца и последнее
  здоровье из общих репозиториев. Нет вымышленных стартовых записей.
- Health: ручные сон и шаги с `HealthRepository.update`; Health Connect/HealthKit
  не подключены.
- Assistant: default-off разрешения категорий, локальный предпросмотр фиксированного
  JSON, явный запрос к пользовательскому HTTPS API. Ключ только в памяти процесса;
  без фоновых запросов и автоматических записей рекомендаций в репозитории.

## Разрешение интеграционных расхождений

- Конфликты `FinanceScreen.kt` и `FinanceDialogs.kt` разрешены с сохранением новой
  бизнес-логики и существующего дизайна: ModalBottomSheet, DatePicker, FAB,
  отступы, empty state/маскот. Исправление фильтра при переполнении суммы сохранено.
- Исправлены NSDate factories в `AssistantTime.ios.kt`, `DashboardDates.ios.kt`
  и `HealthDates.ios.kt` для текущего Kotlin/Native.
- Новые карточки Assistant/Dashboard используют тёмные поверхности общей темы;
  название раздела задач согласовано с «Планом». Шкалы задач и финансов получили
  отдельный тёмный track, чтобы нулевой/частичный прогресс оставался различимым.
- Модели, repository interfaces, SQLite schema, Gradle/Manifest и DI не расширялись.
  Новых зависимостей и permissions для интеграции не потребовалось.

## Проверки

С JDK 21 и Android SDK из README выполнено:

```sh
./gradlew :androidApp:build :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 --console=plain --stacktrace
```

BUILD SUCCESSFUL. Android debug/release и lint: 0 errors, 21 warnings.
71 host test: 0 failures, 0 errors, 0 skipped.
iOS Simulator arm64: Kotlin compilation successful.
Тесты включают SQLite persistence/Flow, Planner, Finance и notification intake,
периоды/точные суммы, Dashboard и ручной Health, приватность контекста,
зафиксированный preview, построение HTTP/auth, ошибки API и cancellation.

На отдельном AVD Android 15 API 35 arm64 установлен объединённый APK и проверены:

1. Cold start и Dashboard: задача из ранее сохранённой SQLite отображается как
   одна актуальная задача за сегодня; текущие расходы доступны в Finance.
2. Finance: новая месячная граница до 31 октября, сводки день/неделя/месяц,
   категории и лимит 1000 RUB при 925,40 RUB расходов: остаток 74,60 RUB.
3. Health: сохранение 7 ч 30 мин сна и 8000 шагов через экран. После force-stop
   процесса и повторного запуска значения восстановились из общей SQLite.
4. Assistant: разрешены только задачи; предпросмотр показал реальную задачу
   с ref T1 и без секций finances/health. Ключ и модель не вводились, запрос к
   провайдеру не отправлялся.
5. Crash buffer пуст. Тестовые записи только в отдельном AVD; production seed нет.

## Ограничения

Не проверены физический Android, API 24–25 runtime, реальный банковский push и
выдача notification access, реальный AI-запрос с пользовательским ключом,
iOS framework linking/runtime/device arm64. Месячные лимиты Finance — сессионные,
ожидающие будущего Core preferences-контракта. Черновики notifications не являются
персистентными, подтверждённые расходы сохраняются в SQLite.
