# Lifety — контракты интеграции v1 (Core & Planner P0)

## Статус и структура

Core & Planner P0 реализован в ветке `feature/core`, worktree
`/Users/rugilf/Projects/lifety-core`. Основной репозиторий:
`/Users/rugilf/Projects/HSE-hack-2026`; слияние в main не выполняется этой задачей.
Реализованы SQLite-репозитории всех трёх доменов, задачи, календарь, DI и навигация.
Finance интегрирован до `origin/feature/finance` (`c703a1f`): его экран использует
общий expenses repository. Dashboard, Assistant и Health подключены из
`origin/feature/assistant` (`e30a688`). Их публичные screen signatures сохранены.
В iOS-адаптерах исправлена совместимость NSDate factory. Локальная альтернативная
`feature/assistant` (`e63b63c`) не объединялась: это другая mock-AI/DI реализация.
Два Gradle-модуля: `:androidApp` (запуск Android) и `:shared` (KMP, включая Compose).
Границы функций — каталоги и Kotlin-пакеты внутри `:shared`, не отдельные
Gradle-модули. Это сокращает настройку и число общих файлов при параллельной работе.

Базовый пакет: `ru.lifety`. Далее `C` означает
`shared/src/commonMain/kotlin/ru/lifety`, `A` —
`shared/src/androidMain/kotlin/ru/lifety`, `I` —
`shared/src/iosMain/kotlin/ru/lifety`.

| Файлы | Пакет | Владелец |
| --- | --- | --- |
| `C/core/model/{Task,Expense,HealthSnapshot}.kt` | `ru.lifety.core.model` | Codex 1 |
| `C/core/repository/{TaskRepository,ExpenseRepository,HealthRepository,Repositories}.kt` | `ru.lifety.core.repository` | Codex 1 |
| `C/core/ui/{LocalRepositories,PlaceholderScreen}.kt` | `ru.lifety.core.ui` | Codex 1 |
| `C/core/network/HttpClientFactory.kt`, `A/core/network/HttpClientFactory.android.kt`, `I/core/network/HttpClientFactory.ios.kt` | `ru.lifety.core.network` | Codex 1 |
| `C/database/DatabaseDriverFactory.kt`, `A/database/DatabaseDriverFactory.android.kt`, `I/database/DatabaseDriverFactory.ios.kt` | `ru.lifety.database` | Codex 1 |
| `shared/src/commonMain/sqldelight/ru/lifety/database/Lifety.sq` | generated: `ru.lifety.database.generated` | Codex 1 |
| `C/app/LifetyApp.kt`, `C/app/navigation/{AppDestination,RootNavigator}.kt`, `I/app/MainViewController.kt` | `ru.lifety.app`, `ru.lifety.app.navigation` | Codex 1 |
| `C/feature/tasks/**` | `ru.lifety.feature.tasks` | Codex 1 |
| `C/feature/calendar/**` | `ru.lifety.feature.calendar` | Codex 1 |
| `C/feature/finance/**` | `ru.lifety.feature.finance` | Codex 2 |
| `A/platform/notifications/**` | `ru.lifety.platform.notifications` | Codex 2 |
| `C/feature/assistant/**`, включая `ai/**` | `ru.lifety.feature.assistant`, `ru.lifety.feature.assistant.ai` | Codex 3 |
| `C/feature/dashboard/**` | `ru.lifety.feature.dashboard` | Codex 3 |
| `C/feature/health/**` | `ru.lifety.feature.health` | Codex 3 |
| `androidApp/**`, все Gradle-файлы, wrapper, Manifest, README, CONTRACTS, AGENTS | `ru.lifety.android` для Android entry point | Codex 1 |

Платформенные адаптеры функций можно добавлять в `A/feature/<feature>/**` и
`I/feature/<feature>/**` с тем же владельцем. Тесты живут в `shared/src/commonTest/`,
`shared/src/androidHostTest/`, `shared/src/iosTest/` с зеркальной структурой пакетов;
владелец теста совпадает с владельцем кода. Общие тесты моделей, БД и навигации — Codex 1.

## Единые модели

Все три — `@Serializable data class`, все свойства `val`, без значений по умолчанию.
Имена полей Kotlin и JSON совпадают; переносить модели в feature-пакеты запрещено.

```kotlin
package ru.lifety.core.model

@Serializable
data class Task(
    val id: String,
    val title: String,
    val scheduledAt: Long?,
    val deadlineAt: Long?,
    val isCompleted: Boolean,
)

@Serializable
data class Expense(
    val id: String,
    val amountMinor: Long,
    val currency: String,
    val merchant: String,
    val category: String,
    val timestamp: Long,
    val source: String,
)

@Serializable
data class HealthSnapshot(
    val timestamp: Long,
    val sleepMinutes: Int?,
    val steps: Int?,
)
```

- Все даты — Unix epoch в **миллисекундах UTC**. Часовой пояс применяется в UI.
- `null` времени означает отсутствие даты; `null` health-показателя — неизвестное
  значение, а не ноль. `sleepMinutes` — целые минуты, `steps` — целое число шагов.
- `id` — стабильная непрозрачная строка, создаётся вызывающим кодом; репозиторий
  не заменяет id. Формат UUID допустим, но интерфейс его не требует.
- `amountMinor` — целое количество минимальных единиц валюты; 12345 RUB = 123,45 ₽.
  Не переводить деньги в `Double`. `currency` — uppercase ISO 4217, например `RUB`.
- `category`, `source` — строки, а не общие enum. Для будущего ручного ввода
  согласованы `source = "manual"` и резервная категория `"other"`.
- Здесь нет валидации, правил категоризации, расчётов или получения health-данных.

## Репозитории

Файлы и пакет указаны в таблице. `Flow` — `kotlinx.coroutines.flow.Flow`.
Изменяющие методы **suspend**, возвращают `Unit`; сигнатуры не меняются агентами функций.

```kotlin
interface TaskRepository {
    fun observeAll(): Flow<List<Task>>
    suspend fun upsert(task: Task)
    suspend fun delete(id: String)
}

interface ExpenseRepository {
    fun observeAll(): Flow<List<Expense>>
    suspend fun upsert(expense: Expense)
    suspend fun delete(id: String)
}

interface HealthRepository {
    fun observeLatest(): Flow<HealthSnapshot?>
    suspend fun update(snapshot: HealthSnapshot)
    fun observeAll(): Flow<List<HealthSnapshot>>
    suspend fun updateAll(snapshots: List<HealthSnapshot>)
}

data class Repositories(
    val tasks: TaskRepository,
    val expenses: ExpenseRepository,
    val health: HealthRepository,
)
```

Гарантии SQLDelight-реализаций Codex 1:

- Наблюдение выдаёт текущее состояние при подписке и изменения после успешной
  записи. Для пустой БД — `emptyList()` и `null`, без фиктивных данных.
- `upsert` полностью заменяет запись с тем же id либо вставляет новую;
  `delete` отсутствующего id успешен и ничего не меняет.
- Детерминированный порядок задач: `id ASC`; расходов: `timestamp DESC, id ASC`.
  UI задач/календаря сам выбирает необходимое представление и сортировку.
- Health хранит snapshots по timestamp; `update` заменяет snapshot с тем же
  timestamp. `observeLatest` выбирает максимальный timestamp, не последнюю запись
  по моменту вызова. `null`-показатели заменяются, а не сливаются со старым snapshot.
- `health.observeAll()` в SQLDelight выдаёт всю сохранённую историю в порядке
  `timestamp DESC`; `updateAll()` выполняет пакет в одной транзакции (всё или ничего).
  Для исходной совместимости интерфейс по умолчанию преобразует `observeLatest()`
  в список и вызывает `update()` для каждого элемента пакета. Адаптеры с собственной
  историей обязаны переопределить оба метода, как production SQLDelight.
  Атомарность пакета гарантируется SQLDelight, а не default-адаптером.
- Запись атомарна. Дисковые операции не блокируют UI; ошибки доходят до вызывающего
  кода, `CancellationException` не поглощается. Ошибки не маскируются пустыми списками.
- Репозитории создаются один раз в composition root, общие для всех функций.
  UI-state функций публикуется как `StateFlow`, изменяемый flow остаётся приватным.

Реализации: `ru.lifety.database.SqlDelightTaskRepository`,
`SqlDelightExpenseRepository`, `SqlDelightHealthRepository` в
`C/database/SqlDelightRepositories.kt`. Запросы выполняются на переданном
`CoroutineDispatcher` (по умолчанию `Dispatchers.IO`); SQLDelight `asFlow()` с
`mapToList`/`mapToOneOrNull` передаёт изменения всем подписчикам. Одна SQL-команда
`INSERT OR REPLACE` атомарно полностью заменяет строку. Feature-local test fake
допустим только в тестах; production использует одну SQLite БД.

## SQLite и DI

Единственный стек БД — SQLDelight **2.4.0**: AndroidSqliteDriver на Android,
NativeSqliteDriver на iOS. `JdbcSqliteDriver` используется только в host-тесте.
Имя файла БД — `lifety.db`, сгенерированный класс —
`ru.lifety.database.generated.LifetyDatabase`.
Схема содержит таблицы `task`, `expense`, `healthSnapshot` с полями моделей;
`Boolean` и nullable `Int` имеют SQLDelight type projections.
CRUD-запросы находятся в существующем `Lifety.sq`: `selectTasks`, `upsertTask`,
`deleteTask`, `selectExpenses`, `upsertExpense`, `deleteExpense`,
`selectLatestHealth`, `selectHealthHistory`, `upsertHealth`. Формат таблиц и schema version 1 не изменены,
миграция для этого этапа не требуется. Int-адаптеры подключаются в
`createDatabase`, проверяют диапазон Int при чтении SQLite INTEGER.
Сгенерированный код в `build/` не редактировать и не коммитить; дальнейшие
изменения схемы и миграции согласует Codex 1.

Стабильные фабрики:

```kotlin
// commonMain, ru.lifety.database
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
fun createDatabase(driver: SqlDriver): LifetyDatabase
// androidMain constructor: DatabaseDriverFactory(context: Context)
// iosMain constructor: DatabaseDriverFactory()
```

`ru.lifety.core.di.AppContainer(driver: SqlDriver, dispatcher: CoroutineDispatcher = Dispatchers.IO)`
в `C/core/di/AppContainer.kt` удерживает driver и один `Repositories` для приложения;
`close()` закрывает соединение. Репозитории не закрываются при переходах между экранами.

Android: `ru.lifety.android.LifetyApplication` в
`androidApp/src/main/kotlin/ru/lifety/android/LifetyApplication.kt` владеет
контейнером на время процесса. `container(): Deferred<AppContainer>` создаёт его
в application coroutine scope на IO, сохраняет один успешный результат и
позволяет повторить инициализацию после ошибки. MainActivity ожидает контейнер
через `RepositoryBootstrap`, передаёт `repositories` в `LifetyApp`. Смена Activity
не создаёт новую БД. `android:name=".LifetyApplication"` установлен в Manifest.
На iOS `MainViewController` использует один lazy-контейнер на время процесса,
также создаваемый вне UI-потока. ОС освобождает ресурсы при завершении процесса;
тесты и явно созданные контейнеры вызывают `close()` самостоятельно.

`RepositoryBootstrap` показывает загрузку/ошибку и повторную попытку без подмены
БД in-memory данными. UI всех функций получает тот же набор через
`ru.lifety.core.ui.LocalRepositories.current` с полями `.tasks`, `.expenses`, `.health`.
`LifetyApp(repositories: Repositories? = null)` сохраняет прежнюю сигнатуру:
`null` допустим для изолированного preview/test host; production Android/iOS
entry points передают реальные репозитории. Tasks и Calendar при `null` показывают
явное состояние «хранилище не подключено». Другим экранам следует делать так же.
Finance notification service не обращается к БД: он публикует неподтверждённые
предложения в `FinanceNotifications.inbox`; запись выполняет FinanceScreen
через существующий repository только после подтверждения. Отдельный DI-адаптер
сервису не нужен, отдельный Android process запрещён (очередь общая в памяти).

## Общий визуальный контракт

Все экраны используют корневую `ru.lifety.core.ui.LifetyTheme`. Палитра, размеры,
типографика, правила маскота и точные API общих UI-компонентов закреплены в
[DESIGN_SYSTEM.md](DESIGN_SYSTEM.md). Владелец `C/core/ui/**` — Codex 1.
`LifetyMascot(modifier = Modifier, emotion = LifetyEmotion.Happy, animated = true)`
доступен всем функциям без platform assets. Не дублировать цвета и drawing.
Модели, репозитории, схема БД и feature entry signatures при редизайне не меняются.
Android polish v2 использует стандартную M3 Typography, единые motion specs из
`C/core/ui/LifetyMotion.kt`, компактные секции и сохранение черновиков при
configuration change. Разрешения AI и процессное хранение ключа не менялись.

## Навигационные интеграционные точки

`ru.lifety.app.LifetyApp(repositories: Repositories? = null)` — общий Compose root.
Android launcher: `ru.lifety.android.MainActivity` в
`androidApp/src/main/kotlin/ru/lifety/android/MainActivity.kt`.
iOS entry: `ru.lifety.app.MainViewController()` в `I/app/MainViewController.kt`.
Объявлены targets `android`, `iosArm64`, `iosSimulatorArm64`; framework `LifetyShared`
статический. Полного Xcode host-приложения в baseline нет.

Каждый публичный вход функции — `@Composable fun XxxScreen(modifier: Modifier = Modifier)`:

| Route / enum | Пакет | Файл и функция |
| --- | --- | --- |
| `dashboard` / `DASHBOARD` | `ru.lifety.feature.dashboard` | `C/feature/dashboard/DashboardScreen.kt`: `DashboardScreen` |
| `tasks` / `TASKS` | `ru.lifety.feature.tasks` | `C/feature/tasks/TasksScreen.kt`: `TasksScreen` |
| `calendar` / `CALENDAR` | `ru.lifety.feature.calendar` | `C/feature/calendar/CalendarScreen.kt`: `CalendarScreen` |
| `finance` / `FINANCE` | `ru.lifety.feature.finance` | `C/feature/finance/FinanceScreen.kt`: `FinanceScreen` |
| `assistant` / `ASSISTANT` | `ru.lifety.feature.assistant` | `C/feature/assistant/AssistantScreen.kt`: `AssistantScreen` |
| `health` / `HEALTH` | `ru.lifety.feature.health` | `C/feature/health/HealthScreen.kt`: `HealthScreen` |

Корень использует Compose Navigation: `rememberNavController()` + `NavHost`.
Шесть прежних строковых routes сохранены. Основных пунктов NavigationBar пять:
**Главная, План, Финансы, Здоровье, Ассистент**. `tasks` открывает `PlanScreen`
с вкладками Задачи/Календарь; `calendar` — совместимый вход в План с выбранным
календарём. Оба отмечают один пункт «План». Состояния вкладок сохраняются.
`TasksScreen` и `CalendarScreen` сохраняют свои публичные сигнатуры.
Настройки открываются через кнопку верхней панели и `LifetySettingsSheet`.
Начальный раздел — dashboard;
верхние разделы сохраняют состояние при переключении, повторный выбор не создаёт
дубликат destination. Android Back и iOS Back обрабатывает Navigation.

`RootNavigator.destination: StateFlow<AppDestination>` и
`navigate(destination: AppDestination): Unit` сохранены. Это фасад NavHost;
`LocalRootNavigator.current` в пакете `ru.lifety.app.navigation` позволяет
перейти к верхнему разделу, например `navigate(AppDestination.CALENDAR)`.
Конструктор получил необязательный callback `onNavigate`; старый вызов
`RootNavigator(initialDestination)` остаётся корректным. `AppDestination.fromRoute`
сохраняет fallback неизвестной строки в dashboard. Восстановлением реального
back stack и аргументов теперь управляет NavController.

Новый внутренний typed destination:

```kotlin
// ru.lifety.app.navigation, C/app/navigation/TaskEditorRoute.kt
@Serializable
data class TaskEditorRoute(val taskId: String? = null, val scheduledAt: Long? = null)
```

Он открывает `TaskEditorScreen(taskId, initialScheduledAt, onClose, modifier)`
в пакете `ru.lifety.feature.tasks`. Отсутствующий `taskId` означает новую задачу;
`scheduledAt` задаёт начальное время для создания из календаря. Для редактирования
передаётся только id, актуальная задача читается из репозитория. Opaque id не
склеивается с URL: аргументы кодирует typed Navigation. Сохранение/удаление/отмена
возвращают на предыдущий экран. Редактор отображается через ModalBottomSheet,
нижняя навигация скрыта в редакторе. Finance сохраняет свой локальный контракт
`ExpenseEditorDialog`, но тоже отображает форму в ModalBottomSheet.

Стабильная точка открытия planner из любой функции без импорта `feature.tasks`:

```kotlin
// ru.lifety.core.navigation, C/core/navigation/PlannerNavigation.kt
interface PlannerNavigation {
    fun openTask(id: String)
    fun createTask(scheduledAt: Long? = null)
}
// LocalPlannerNavigation.current?.openTask(task.id)
// LocalPlannerNavigation.current?.createTask()
```

`LocalPlannerNavigation` предоставлен корнем вместе с репозиториями. Новые
верхние routes добавляет Codex 1. Feature-local навигация остаётся у функции.

Все шесть экранов уже зарегистрированы в NavHost корневого файла. Codex 2/3 заменяют
тело своего `XxxScreen`, сохраняя имя, пакет и сигнатуру. `modifier` применяется
к корневому UI-элементу. Feature-код не импортирует другие feature-пакеты;
dashboard/assistant работают с общими моделями и репозиториями.

## Сеть и Android notifications

`ru.lifety.core.network.createHttpClient(): HttpClient` — expect/actual-фабрика:
OkHttp на Android, Darwin на iOS, JSON ContentNegotiation, `expectSuccess = true`,
таймаут соединения 15 с, запроса и сокета 30 с. Владелец клиента закрывает его.
Фабрика не задаёт URL, ключ, AI provider и не отправляет запросов при запуске.
Codex 3 размещает AI-логику в `feature/assistant/ai/**`, использует фабрику;
новые общие зависимости запрашивает у Codex 1. API-ключи не коммитить.
`AiProvider`/`KtorAiProvider`, `ContextBuilder`, `AssistantController` реализованы
в feature-пакете без изменения Core. Разрешения всех категорий default-off,
preview фиксирует JSON до явного Analyze; ключ хранится только в памяти процесса.
Никаких записей в репозитории из AI. Полные контракты и ограничения —
`C/feature/assistant/ai/README.md`. Health наблюдает `HealthRepository.observeAll()`;
ручной ввод использует `update`, подтверждённый пакет — `updateAll`.
Dashboard сохраняет прежнюю подписку на последние показатели. Android Health Connect
читает дневные агрегаты сна/шагов за 30 дней только по явному действию, сохраняет
пакет после предпросмотра и подтверждения. Модель неизменна. Правила группировки,
совместимости и повторного импорта — в `docs/HEALTH_HISTORY.md`. Разрешения AI от этого не меняются. На iOS остаётся ручной ввод;
HealthKit не подключён. Android-only connect-client 1.2.0-alpha06 использует
READ_SLEEP/READ_STEPS и Activity объяснения. SDK и его bound service включаются
только на API 28+, minSdk приложения остаётся 24 с точечным overrideLibrary.
Записей в Health Connect, фонового чтения и новых разрешений нет. Добавлен только
SQL-запрос истории; таблицы, schema version и DI прежние.
Точный workflow и ограничения — C/feature/health/INTEGRATION.md.

Android notification adapter реализован Codex 2 в
`ru.lifety.platform.notifications`. `ExpenseNotificationListener` зарегистрирован
в `androidApp/src/main/AndroidManifest.xml` с service permission
`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`, `exported=false`, без
отдельного процесса. Не требуется runtime POST_NOTIFICATIONS и не добавляется
uses-permission BIND_NOTIFICATION_LISTENER_SERVICE. Сам пользователь выбирает
банки, включает consent и выдаёт notification listener access в Android Settings.
По умолчанию импорт выключен. Сервис не сохраняет расходы автоматически.
Точные ограничения и инструкция демонстрации —
`C/feature/finance/INTEGRATION.md`. Живой банковский push и системное разрешение
пока не тестировались; подтверждение демо-уведомления проверено в Android.

## Новый headless agent API

Пакет `ru.lifety.feature.assistant.agent` владеет AgentRuntime, AgentSession,
AgentRunner, AgentWorkspace и LifetyAgentEnvironment; источник —
`codex/native-agent-harness` (`df063fa`). Это отдельный Kotlin API без подключения
к AssistantScreen. Существующий UI/AI, root DI и навигация сохраняются.
Android: `createAndroidAgentRuntime(applicationContext)`; iOS:
`createIosAgentRuntime()`. Сессия получает существующий `Repositories?`.
Scopes default-off, HTTP начинается только с `session.start`; записи приложений —
через proposals и явный `runner.approve`. Health read-only. Перед подключением
нужен UI разрешений, точного preview действий, управления памятью и lifecycle.
Android vault.save явно сохраняет шифрованный ключ; это не меняет process-only
политику старого AssistantScreen. QuickJS 1.0.15 — common dependency, для host
runtime подставляется JVM artifact. SQL/Manifest не меняются. Точный API,
лимиты и порядок cancel/join/close — C/feature/assistant/agent/INTEGRATION.md.
Результат интеграционной проверки — docs/NATIVE_AGENT_REVIEW.md.

## Параллельная работа следующих трёх агентов

1. Все читают этот контракт и начинают от одного commit фундамента. Каждый получает
   собственную ветку и worktree, не отдельный Git-репозиторий.
2. Codex 1: `core/**`, `database/**`, tasks/calendar, реальный composition root,
   Gradle/Manifest/навигация. Codex 2: finance/notifications. Codex 3:
   assistant/dashboard/health/AI. Вне своей зоны — только согласованный запрос владельцу.
3. Codex 2/3 могут сразу разрабатывать UI/state с общими интерфейсами и локальными
   тестовыми fake; SQLite-репозитории и production DI уже доступны в `feature/core`.
4. Новая зависимость, модель, route, миграция, permission или receiver — сначала
   точная сигнатура/патч-запрос Codex 1. Не править общие файлы одновременно.
5. Каждый коммитит только свою область и проверяет Android-сборку и host-тесты.
   Интеграция веток выполняется отдельным поручением; текущая задача делает только
   commit в feature/core, без merge в main.
6. Не обновлять версии стека во время 7-часового хакатона без конкретной причины
   и согласованной проверки сборки. Не создавать сервер, облачную БД, регистрацию,
   векторный поиск или второе SQLite-решение.

На этом ноутбуке worktree уже подготовлены:

| Ветка | Worktree |
| --- | --- |
| `feature/core` | `/Users/rugilf/Projects/lifety-core` |
| `feature/finance` | `/Users/rugilf/Projects/lifety-finance` |
| `feature/assistant` | `/Users/rugilf/Projects/lifety-assistant` |

Не переключать чужие ветки, не редактировать их working tree и не создавать
повторные ветки для уже подготовленных агентов. Каждому worktree нужен свой
игнорируемый `local.properties` с `sdk.dir` либо `ANDROID_HOME`. Финансовая и
assistant-ветки могут продолжать работу с прежними интерфейсами; при последующем
объединении core-коммит подключит реальные репозитории к тем же экранным входам.

## Planner P0: поведение и границы

- `TasksScreen` создаёт/редактирует/удаляет задачи, отмечает выполнение;
  фильтры «Актуальные», «Завершённые», «Все». Список сортируется для UI отдельно
  от repository order. Пустое название запрещает только редактор; модели и
  репозитории не получают новых ограничений. Название перед сохранением trim.
- План и дедлайн — независимые nullable даты с временем. Они могут быть в прошлом;
  редактирование не меняет id и completion без явного действия пользователя.
- `TaskEditorViewModel` сохраняет черновик при configuration change. Несохранённый
  черновик при завершении процесса не гарантируется; сохранённые данные остаются в SQLite.
- `CalendarScreen` — месячная сетка с понедельника, выбор дня и отметки наличия задач.
  Показывает задачи по локальной дате `scheduledAt` **или** `deadlineAt`. Если даты
  совпали, задача отображается один раз. Задачи без обеих дат в календаре не видны.
  Новая задача выбранного дня получает начальное время 09:00 локально, которое
  можно изменить в редакторе. Нажатие на задачу открывает тот же редактор.
- `C/core/time/PlannerDates.kt` содержит KMP-конвертацию локальных дат/времени в
  epoch milliseconds. DatePicker трактуется как civil date в UTC; timezone
  применяется только при объединении с локальным временем. Несуществующее время
  при spring-forward DST отклоняется UI, неоднозначное время при осеннем переходе
  выбирается по стандартной семантике kotlinx-datetime (раннее смещение).
- `C/core/planner/TaskListViewModel.kt` — общий StateFlow state задач/календаря;
  в `C/core/ui/TaskCard.kt` и `LifetyTheme.kt` находятся общий компонент и M3-тема.
- Нет recurrence, приоритетов, длительности или напоминаний о дедлайнах. Контракты моделей
  не расширены. Notifications — зона Codex 2, AI/health UI — Codex 3.

## Версии и источники

Версии зафиксированы в `gradle/libs.versions.toml`; wrapper — Gradle 9.5.1.
Kotlin/Compose compiler 2.4.20, Compose Multiplatform 1.12.1, Material3 1.9.0 (stable), AGP 9.3.1,
Coroutines 1.11.0, Serialization 1.11.0, Ktor 3.6.0, SQLDelight 2.4.0,
Activity Compose 1.13.0. Добавлены Navigation Compose 2.9.2, Lifecycle ViewModel Compose 2.9.2
и kotlinx-datetime 0.7.1, desugar_jdk_libs 2.1.5 для java.time на Android API 24–25;
прежние версии не обновлялись. JDK для проверки — 21, JVM bytecode — 17.
Android minSdk 24, compileSdk/targetSdk 37.

При выборе сверены официальные источники:
[KMP compatibility](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html),
[Compose compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html),
[Android KMP plugin](https://developer.android.com/kotlin/multiplatform/plugin),
[AGP 9.3](https://developer.android.com/build/releases/agp-9-3-0-release-notes),
[SQLDelight releases](https://github.com/sqldelight/sqldelight/releases),
[Ktor releases](https://github.com/ktorio/ktor/releases),
[Coroutines releases](https://github.com/Kotlin/kotlinx.coroutines/releases),
[Serialization releases](https://github.com/Kotlin/kotlinx.serialization/releases),
[Compose Navigation](https://kotlinlang.org/docs/multiplatform/compose-navigation-routing.html),
[kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime),
[Android API desugaring](https://developer.android.com/studio/write/java8-support).
Фактические результаты сборки и ограничения проверки — в README.

## API compatibility: OpenRouter

После main 66af7d7 проверен OpenRouter / Nemotron. Общая core HTTP factory
сохраняет таймауты 15/30/30 секунд; оба AI clients отдельно задают request/socket
120 секунд. Бюджет runner 180 секунд и cancellation сохраняются.
AgentMessage/ModelReply получили только optional reasoningDetails/reasoning для
точного round trip метаданных провайдера; старые private checkpoints читаются.
Core models, repository interfaces, SQL и public screen signatures не менялись.
Настройки и фактические live test results — docs/OPENROUTER_API.md.
