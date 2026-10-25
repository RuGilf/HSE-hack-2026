# Lifety — Core, Planner & Finance

Kotlin Multiplatform + Compose Multiplatform, Android-first. Реализованы локальные
SQLDelight-репозитории, задачи, календарь, общий DI и Compose Navigation.
Finance объединён из `origin/feature/finance` (`847f405`): ручные расходы,
сводки и подтверждённый импорт уведомлений подключены к общей SQLite.
Dashboard, Assistant и Health пока остаются входами третьего агента.
Текущая интеграция описана в [docs/INTEGRATION_STATUS.md](docs/INTEGRATION_STATUS.md).

Рабочая ветка: **feature/core**, worktree: `/Users/rugilf/Projects/lifety-core`.
Основной checkout `/Users/rugilf/Projects/HSE-hack-2026` остаётся на main;
самостоятельное слияние в main не выполнялось.

## Запуск Android

Нужны JDK 21, Android SDK Platform 37 (`platforms;android-37.0`), Build Tools
36.0.0, принятые лицензии SDK и доступ к Google Maven/Maven Central при первой
сборке. Wrapper Gradle 9.5.1 включён в Git. Android minSdk — **24**, target/compile
SDK — **37**. Для java.time на API 24–25 включён core library desugaring.

```sh
cd /Users/rugilf/Projects/lifety-core
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :androidApp:assembleDebug
```

На другой машине используйте свои пути к JDK 21 и Android SDK. Вместо
`ANDROID_HOME` можно задать `sdk.dir=/absolute/path/to/sdk` в игнорируемом
`local.properties`. Gradle JDK в Android Studio тоже выставьте на JDK 21.

Откройте **этот worktree** в Android Studio с поддержкой AGP 9.3, выберите
конфигурацию `androidApp` и устройство API 24+. Для установки из терминала:
`./gradlew :androidApp:installDebug` (требует подключённого устройства).

APK относительно корня worktree:
`androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## Что работает

- Создание, редактирование, удаление и выполнение задач. Фильтры актуальных,
  завершённых и всех задач; состояния загрузки, пустого списка и ошибки.
- Необязательные план и дедлайн с выбором даты и 24-часового времени. Даты
  сохраняются в UTC epoch milliseconds, показываются в часовом поясе устройства.
- Месячный календарь с понедельника, переключение месяцев, выбор дня, индикаторы
  задач и переход в общий редактор. Учитывает и план, и дедлайн; совпадающие даты
  не дублируют задачу. Создание из календаря предлагает выбранный день в 09:00.
- Material 3 с карточками, подтверждением удаления, FAB, светлой/тёмной темой.
- Finance: ручное добавление/редактирование/удаление расходов, период и валюта,
  категории и сводки, сессионный бюджет. Банковские уведомления сохраняются
  только после подтверждения. Демо-уведомление работает без системного доступа.
- Android notification listener зарегистрирован в Manifest. Чтение выбранных
  банков включается пользователем через отдельное системное разрешение.
- `TaskRepository`, `ExpenseRepository`, `HealthRepository` сохраняют данные
  в `lifety.db`; SQLDelight Flow уведомляет подписчиков после записи.
- Android Application владеет одним контейнером БД, общим для всех экранов и
  Activity. iOS entry point также подключает persistent-контейнер. Диск работает
  на IO, а не на UI-потоке. При ошибке нет скрытой подмены на in-memory storage.
- NavHost сохраняет верхние routes, back stack и экранные состояния;
  редактор получает id задачи через typed route, Back возвращает к источнику.

Модели и публичные repository interfaces не изменены. Формат SQLite-таблиц и
schema version 1 сохранены: на этом этапе добавлены запросы, миграция не нужна.
Нет сервера, регистрации, облачной БД, Firebase, AI-реализации и внешних календарей.
Planner P1 (напоминания о дедлайнах, повторы, приоритеты, длительность) не реализован.

## Структура кода

Два Gradle-модуля: `:androidApp` и `:shared`. Основные файлы:

```text
androidApp/src/main/
  AndroidManifest.xml                         LifetyApplication registration
  kotlin/ru/lifety/android/
    LifetyApplication.kt                      Process-wide DB container
    MainActivity.kt                           Bootstrap + root Compose
shared/src/commonMain/kotlin/ru/lifety/
  app/
    LifetyApp.kt                              NavHost, six feature entries
    RepositoryBootstrap.kt                    Loading/error/retry
    navigation/{AppDestination,RootNavigator,TaskEditorRoute}.kt
  core/
    di/AppContainer.kt                        Driver + all repositories
    model/{Task,Expense,HealthSnapshot}.kt      Unchanged
    repository/*.kt                           Unchanged public interfaces
    navigation/PlannerNavigation.kt           Editor integration point
    planner/TaskListViewModel.kt               Shared StateFlow state
    time/PlannerDates.kt                      KMP date/time and day selection
    ui/{LocalRepositories,LifetyTheme,TaskCard,PlaceholderScreen}.kt
  database/
    DatabaseDriverFactory.kt                  SQLDelight Int adapters
    SqlDelightRepositories.kt                 Three persistent repositories
  feature/
    tasks/{TasksScreen,TaskEditorScreen,TaskEditorViewModel}.kt
    calendar/CalendarScreen.kt
    finance/                                  Expenses + confirmed imports
    dashboard/, assistant/, health/            Third agent entries
shared/src/commonMain/sqldelight/ru/lifety/database/Lifety.sq
shared/src/androidMain/                        SQLite/HTTP platform drivers
shared/src/iosMain/                            Drivers + MainViewController
shared/src/commonTest/                         Model, navigation, date tests
shared/src/androidHostTest/                    SQLite schema/persistence tests
```

## Интеграция других агентов

Полные стабильные сигнатуры, пакеты, пути и правила — в
[docs/CONTRACTS.md](docs/CONTRACTS.md). Владение файлами — в [AGENTS.md](AGENTS.md).

Все существующие `@Composable fun XxxScreen(modifier: Modifier = Modifier)`
зарегистрированы в NavHost. Другим агентам достаточно заменить тело своего
экрана; правки root-файла не нужны. Репозитории доступны через
`ru.lifety.core.ui.LocalRepositories.current` (`tasks`, `expenses`, `health`).
Production entry points передают реальный набор; `null` оставлен для preview.

Открытие редактора из любого feature без импорта tasks:
`LocalPlannerNavigation.current?.openTask(task.id)` или `.createTask()` из
`ru.lifety.core.navigation`. Верхние переходы доступны через
`LocalRootNavigator.current?.navigate(AppDestination.CALENDAR)` из
`ru.lifety.app.navigation`.

Codex 2 владеет Finance/Notifications; Codex 3 — Dashboard/Assistant/Health/AI.
Новые зависимости и изменения Manifest/схемы/routes согласуются с Codex 1.
От них не требуется ничего для работоспособности текущего Planner P0.
Finance listener уже зарегистрирован. Он не требует репозитория в сервисе:
публикует неподтверждённые предложения в общую очередь, а экран сохраняет
подтверждённые расходы через `LocalRepositories.current.expenses`.
Самостоятельное включение notification access не выполняется.

## Проверки

Проверено **25 октября 2026**, macOS arm64, JDK **21.0.12.1**, Gradle **9.5.1**,
Android SDK **37**, Build Tools **36.0.0**, Xcode **27.1**.
Команды выполнены с указанными выше `JAVA_HOME` и `ANDROID_HOME`:

```sh
./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest
./gradlew :androidApp:build :shared:testAndroidHostTest
./gradlew :shared:compileKotlinIosSimulatorArm64 :androidApp:build :shared:testAndroidHostTest
```

- Android debug/release assembly и lint — **BUILD SUCCESSFUL**.
- **34 host tests: 0 failures, 0 errors, 0 skipped**: 13 Core/Planner и 21 Finance/Notifications.
- Проверены реактивные insert/update/delete, полная замена nullable полей,
  стабильная сортировка, latest health по максимальному timestamp,
  точность денег >2⁵³, закрытие/повторное открытие файловой SQLite для всех
  трёх репозиториев и сохранение удаления при следующем открытии.
- Проверены локальная дата относительно UTC, civil date DatePicker,
  отсутствие дублей в календаре, leap day, DST gap detection.
- Общий код, платформенный DI и UI скомпилированы для **iOS Simulator arm64**.

Остаются предупреждения beta expect/actual classes и рекомендации lint по
обновлению фиксированных зависимостей, launcher icon и data extraction rules.
Release APK unsigned; публикационная подпись не настраивалась.

После объединения выполнен smoke test на временном эмуляторе **Android 15 API 35,
arm64**: установка и холодный запуск APK, создание задачи с датой, открытие и
редактирование из календаря, сохранение ручного расхода 125,50 RUB,
подтверждение демо-уведомления 349,90 RUB, проверка итога 475,40 RUB.
Затем процесс принудительно остановлен и запущен снова: задача и оба расхода
восстановились в интерфейсе; содержимое реальной Android SQLite также проверено.
Ошибок AndroidRuntime не обнаружено.

Не проверены физический Android, API 24–25 runtime, реальный банковский push и
системный notification access, iOS framework linking/runtime/device arm64.
Несохранённые черновики и неподтверждённые notification suggestions могут теряться
после завершения процесса; сохранённые данные остаются в SQLite.

Для ручного smoke test на устройстве: создать задачу с планом и дедлайном,
изменить её из календаря, отметить выполнение, проверить фильтры, перезапустить
приложение и проверить данные, удалить задачу и повторить запуск.
