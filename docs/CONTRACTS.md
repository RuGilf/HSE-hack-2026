# Lifety — контракты фундамента v1

## Статус и структура

Общий компилируемый фундамент для 7-часового хакатона. Бизнес-функции не
реализованы. Фактический репозиторий: `/Users/rugilf/Projects/HSE-hack-2026`.
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
}

data class Repositories(
    val tasks: TaskRepository,
    val expenses: ExpenseRepository,
    val health: HealthRepository,
)
```

Требования к **будущим** реализациям Codex 1:

- Наблюдение выдаёт текущее состояние при подписке и изменения после успешной
  записи. Для пустой БД — `emptyList()` и `null`, без фиктивных данных.
- `upsert` полностью заменяет запись с тем же id либо вставляет новую;
  `delete` отсутствующего id успешен и ничего не меняет.
- Детерминированный порядок задач: `id ASC`; расходов: `timestamp DESC, id ASC`.
  UI задач/календаря сам выбирает необходимое представление и сортировку.
- Health хранит snapshots по timestamp; `update` заменяет snapshot с тем же
  timestamp. `observeLatest` выбирает максимальный timestamp, не последнюю запись
  по моменту вызова. `null`-показатели заменяются, а не сливаются со старым snapshot.
- Запись атомарна. Дисковые операции не блокируют UI; ошибки доходят до вызывающего
  кода, `CancellationException` не поглощается. Ошибки не маскируются пустыми списками.
- Репозитории создаются один раз в composition root, общие для всех функций.
  UI-state функций публикуется как `StateFlow`, изменяемый flow остаётся приватным.

Реализаций в baseline нет. Согласованные интерфейсы можно использовать с
feature-local test fake; не добавлять production in-memory «БД» вместо SQLite.

## SQLite и DI

Единственный стек БД — SQLDelight **2.4.0**: AndroidSqliteDriver на Android,
NativeSqliteDriver на iOS. `JdbcSqliteDriver` используется только в host-тесте.
Имя файла БД — `lifety.db`, сгенерированный класс —
`ru.lifety.database.generated.LifetyDatabase`.
Схема содержит таблицы `task`, `expense`, `healthSnapshot` с полями моделей;
`Boolean` и nullable `Int` имеют SQLDelight type projections.
Запросов CRUD и repository implementations пока нет. Сгенерированный код в `build/`
не редактировать и не коммитить; новые `.sq`/миграции согласует Codex 1.

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

Контейнер Codex 1 удерживает `SqlDriver`, создаёт `Repositories` и передаёт его в
`LifetyApp(repositories)`. Драйвер закрывается вместе с контейнером, не экраном.
UI получает репозитории через `ru.lifety.core.ui.LocalRepositories.current`.
В baseline `LifetyApp(repositories: Repositories? = null)` предоставляет `null`:
заглушки безопасно запускаются без хранилища, fake-данных и вызовов репозиториев.
До интеграции реального контейнера новые экраны должны при `null` показывать
заглушку/состояние «хранилище не подключено», а не падать или подменять данные.
После интеграции Codex 1 передаёт один реальный контейнер всем функциям.

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

`RootNavigator.destination: StateFlow<AppDestination>` — read-only состояние;
`navigate(destination: AppDestination): Unit` переключает верхний раздел.
Начальный раздел — dashboard. `LifetyApp` сохраняет route через `rememberSaveable`;
`AppDestination.fromRoute(String)` восстанавливает его, неизвестный route даёт dashboard.
Это выбор верхнего раздела без стека вложенных экранов. Межфункциональные переходы
и новые routes добавляет Codex 1; агент функции передаёт ему запрос на callback.
Внутреннюю навигацию функция держит внутри своего каталога.

Все шесть экранов уже вызываются в `when` корневого файла. Codex 2/3 заменяют
тело своего `XxxScreen`, сохраняя имя, пакет и сигнатуру. `modifier` применяется
к корневому UI-элементу. Feature-код не импортирует другие feature-пакеты;
dashboard/assistant работают с общими моделями и репозиториями.

## Сеть и Android notifications

`ru.lifety.core.network.createHttpClient(): HttpClient` — expect/actual-фабрика:
OkHttp на Android, Darwin на iOS, JSON ContentNegotiation, `expectSuccess = true`,
таймаут соединения 15 с, запроса и сокета 30 с. Владелец клиента закрывает его.
Фабрика не задаёт URL, ключ, AI provider и не отправляет запросов при запуске.
Codex 3 размещает будущую AI-логику в `feature/assistant/ai/**`, использует фабрику;
новые общие зависимости запрашивает у Codex 1. API-ключи не коммитить.

Для notifications зарезервирован пакет `ru.lifety.platform.notifications` в
androidMain. Классов, разрешений, receivers и services пока нет. Codex 2 передаёт
Codex 1 конкретные изменения Manifest/entry point/Gradle для интеграции. Не добавлять
Android imports в commonMain. Публичный notification API согласуется с Codex 1
до его использования tasks/calendar; в baseline такого API нет.

## Параллельная работа следующих трёх агентов

1. Все читают этот контракт и начинают от одного commit фундамента. Каждый получает
   собственную ветку и worktree, не отдельный Git-репозиторий.
2. Codex 1: `core/**`, `database/**`, tasks/calendar, реальный composition root,
   Gradle/Manifest/навигация. Codex 2: finance/notifications. Codex 3:
   assistant/dashboard/health/AI. Вне своей зоны — только согласованный запрос владельцу.
3. Codex 2/3 могут сразу разрабатывать UI/state с общими интерфейсами и локальными
   тестовыми fake, пока Codex 1 реализует SQLite-репозитории. Production DI остаётся у Codex 1.
4. Новая зависимость, модель, route, миграция, permission или receiver — сначала
   точная сигнатура/патч-запрос Codex 1. Не править общие файлы одновременно.
5. Каждый коммитит только свою область и проверяет Android-сборку и host-тесты.
   Codex 1 последовательно интегрирует коммиты 2/3, разрешает конфликты и повторяет проверки.
6. Не обновлять версии стека во время 7-часового хакатона без конкретной причины
   и согласованной проверки сборки. Не создавать сервер, облачную БД, регистрацию,
   векторный поиск или второе SQLite-решение.

Пример подготовки worktree (выполнять **после** commit baseline; эти команды
документированы, не выполнялись при создании фундамента):

```sh
git worktree add -b codex/1-core ../lifety-codex-1 HEAD
git worktree add -b codex/2-finance ../lifety-codex-2 HEAD
git worktree add -b codex/3-assistant ../lifety-codex-3 HEAD
```

Каждому worktree нужен свой игнорируемый `local.properties` с `sdk.dir` либо
`ANDROID_HOME`. Не использовать общий working tree трём агентам одновременно.

## Версии и источники

Версии зафиксированы в `gradle/libs.versions.toml`; wrapper — Gradle 9.5.1.
Kotlin/Compose compiler 2.4.20, Compose Multiplatform 1.12.1, Material3 1.9.0 (stable), AGP 9.3.1,
Coroutines 1.11.0, Serialization 1.11.0, Ktor 3.6.0, SQLDelight 2.4.0,
Activity Compose 1.13.0. JDK для проверки — 21, JVM bytecode — 17.
Android minSdk 24, compileSdk/targetSdk 37.

При выборе сверены официальные источники:
[KMP compatibility](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html),
[Compose compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html),
[Android KMP plugin](https://developer.android.com/kotlin/multiplatform/plugin),
[AGP 9.3](https://developer.android.com/build/releases/agp-9-3-0-release-notes),
[SQLDelight releases](https://github.com/sqldelight/sqldelight/releases),
[Ktor releases](https://github.com/ktorio/ktor/releases),
[Coroutines releases](https://github.com/Kotlin/kotlinx.coroutines/releases),
[Serialization releases](https://github.com/Kotlin/kotlinx.serialization/releases).
Фактические результаты сборки и ограничения проверки — в README.
