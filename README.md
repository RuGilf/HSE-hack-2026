# Lifety — фундамент KMP

Android-first Kotlin Multiplatform + Compose Multiplatform для 7-часового
хакатона. Есть шесть экранов-заглушек и переключение разделов: обзор, задачи,
календарь, финансы, ассистент, здоровье. Бизнес-функции пока не реализованы.

## Запуск Android

Потребуются JDK 21, Android SDK Platform 37 (пакет `platforms;android-37.0`),
Build Tools 36.0.0, принятые лицензии SDK и доступ к Google Maven, Maven Central
и Gradle distributions при первой сборке. Android minSdk — 24. Wrapper уже в Git;
системный Gradle не требуется. Android Studio должна поддерживать AGP 9.3.

На этом ноутбуке:

```sh
cd /Users/rugilf/Projects/HSE-hack-2026
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :androidApp:assembleDebug
```

На другой машине задайте `JAVA_HOME` на установленный JDK 21, а `ANDROID_HOME`
на свой Android SDK. Вместо `ANDROID_HOME` можно создать `local.properties`:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

`local.properties` игнорируется Git. Не переносите абсолютные пути чужого ноутбука
в коммиты. Gradle JDK в настройках Android Studio также выставьте на JDK 21:
системный `java` на этом ноутбуке — JDK 25, который не использовался для проверки.

Откройте корень проекта в Android Studio, дождитесь Gradle sync, выберите
конфигурацию **androidApp** и устройство/эмулятор с Android API 24+.
APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
Для установки на подключённое устройство можно выполнить
`./gradlew :androidApp:installDebug`; эта команда требует доступного устройства.

## Фактическая структура

```text
HSE-hack-2026/
├── .gitattributes
├── .gitignore
├── AGENTS.md
├── README.md
├── docs/CONTRACTS.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.{jar,properties}
├── androidApp/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/ru/lifety/android/MainActivity.kt
│       └── res/values/{strings,styles}.xml
└── shared/
    ├── build.gradle.kts
    └── src/
        ├── commonMain/
        │   ├── kotlin/ru/lifety/
        │   │   ├── app/LifetyApp.kt
        │   │   ├── app/navigation/{AppDestination,RootNavigator}.kt
        │   │   ├── core/model/{Task,Expense,HealthSnapshot}.kt
        │   │   ├── core/repository/{TaskRepository,ExpenseRepository,HealthRepository,Repositories}.kt
        │   │   ├── core/ui/{LocalRepositories,PlaceholderScreen}.kt
        │   │   ├── core/network/HttpClientFactory.kt
        │   │   ├── database/DatabaseDriverFactory.kt
        │   │   └── feature/
        │   │       ├── tasks/TasksScreen.kt
        │   │       ├── calendar/CalendarScreen.kt
        │   │       ├── finance/FinanceScreen.kt
        │   │       ├── assistant/AssistantScreen.kt
        │   │       ├── assistant/ai/README.md
        │   │       ├── dashboard/DashboardScreen.kt
        │   │       └── health/HealthScreen.kt
        │   └── sqldelight/ru/lifety/database/Lifety.sq
        ├── androidMain/kotlin/ru/lifety/
        │   ├── core/network/HttpClientFactory.android.kt
        │   ├── database/DatabaseDriverFactory.android.kt
        │   └── platform/notifications/README.md
        ├── iosMain/kotlin/ru/lifety/
        │   ├── app/MainViewController.kt
        │   ├── core/network/HttpClientFactory.ios.kt
        │   └── database/DatabaseDriverFactory.ios.kt
        ├── commonTest/kotlin/ru/lifety/
        │   ├── core/model/SerializationContractTest.kt
        │   └── app/navigation/NavigationContractTest.kt
        └── androidHostTest/kotlin/ru/lifety/database/DatabaseSchemaTest.kt
```

`:androidApp` — Android application с Kotlin support от AGP 9. `:shared` — KMP
library с новым `com.android.kotlin.multiplatform.library` plugin и targets
`android`, `iosArm64`, `iosSimulatorArm64`. Общие модели/интерфейсы/UI не зависят
от Android SDK. Полного Xcode host-приложения пока нет.

## Контракты и параллельная работа

Точные модели, suspend-методы репозиториев, единицы измерения, семантика будущего
хранилища, пакеты, пути файлов, DI и точки навигации зафиксированы в
[docs/CONTRACTS.md](docs/CONTRACTS.md). Инструкции для агентов — также в
[AGENTS.md](AGENTS.md).

| Агент | Владеет |
| --- | --- |
| Codex 1 | core, database, tasks, calendar, root navigation, Gradle, Manifest, Android launcher, общая документация |
| Codex 2 | finance, Android notifications |
| Codex 3 | assistant, dashboard, health, AI integrations |

Все начинают от одного commit фундамента в разных branches/worktrees.
Codex 2/3 сохраняют публичные `XxxScreen(modifier: Modifier = Modifier)` и работают
через общие repository interfaces. Изменения зависимостей, схемы, моделей,
Manifest и routes координирует Codex 1. Каждый коммитит свою область; Codex 1
последовательно интегрирует изменения и проверяет общую сборку.

## Что подключено

- SQLDelight: одна общая схема `LifetyDatabase` с таблицами task/expense/healthSnapshot,
  Android и Native драйверы. Файл БД — `lifety.db`. CRUD и реализации репозиториев
  остаются следующему этапу Codex 1; стартовая заглушка не открывает БД.
- Coroutines + StateFlow: интерфейсы возвращают Flow, навигация публикует StateFlow.
- kotlinx.serialization: все общие модели `@Serializable`, точные JSON-поля.
- Ktor Client: общая фабрика с JSON/таймаутами, OkHttp/Darwin engines.
  Клиент не создаётся и запросы не отправляются при запуске заглушек.
- Repository DI: `Repositories` передаётся в `LifetyApp`, доступен через
  `LocalRepositories`; baseline работает с `null` до реализации контейнера.

Сервера, облачной БД, регистрации, векторного поиска, AI provider/API-ключа,
уведомлений и источника health-данных нет.

## Проверки

Проверено 25 октября 2026 на macOS arm64, JDK **21.0.12.1**, Gradle **9.5.1**,
Android SDK **37**, Build Tools **36.0.0**. Для iOS compile использован Xcode **27.1**.
Все команды ниже выполнены из корня репозитория с `JAVA_HOME`, указанным выше:

```sh
./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest
./gradlew :androidApp:build :shared:testAndroidHostTest
./gradlew :shared:compileKotlinIosSimulatorArm64
```

Результаты:

- Android debug APK: **BUILD SUCCESSFUL**.
- Полный `:androidApp:build`: **BUILD SUCCESSFUL**, включая debug/release assembly
  и lint. Release APK — unsigned, публикационная подпись не настроена.
- Shared host tests: **5 tests, 0 failures, 0 errors, 0 skipped** — JSON nullable
  полей и Unicode, точность денег больше 2⁵³, неизвестные health-значения,
  восстановление route, создание SQLite-схемы и точность 64-bit SQLite INTEGER.
- iOS Simulator arm64: **compileKotlinIosSimulatorArm64 — BUILD SUCCESSFUL**;
  проверены commonMain + iosMain и совместимость Compose-зависимостей.

В сборке остаются предупреждения Kotlin о beta-статусе expect/actual classes,
упаковке `libandroidx.graphics.path.so` без stripping и lint-рекомендации
обновить зависимости, добавить launcher icon и data extraction rules. Ошибок
компиляции, тестов и lint нет. Версии намеренно зафиксированы как проверенная
совместимая связка; обновления не являются частью фундамента.

Не проверено:

- Установка APK, запуск и визуальное взаимодействие с экранами на Android:
  `adb devices` не обнаружил подключённых устройств, эмулятор не запускался.
- Полное iOS host-приложение, линковка framework, iOS runtime, iOS device arm64:
  Xcode host-проект пока не создавался; проверена только simulator-компиляция KMP.
- Публикационная подпись release, публикация в магазины.
- Android/Native SQLite driver на устройстве: схема проверена через реальный
  SQLite JDBC в host-тесте. Само приложение пока не открывает БД.
- Реальные HTTP/AI вызовы, health-источники и notifications: эта функциональность
  намеренно отсутствует на этапе фундамента.

Версии фиксированы в version catalog. Обоснование совместимости и официальные
источники приведены в конце `docs/CONTRACTS.md`. Не обновляйте стек независимо
в ветках функций во время хакатона.
