# Lifety: локальный порт на iOS

26 октября 2026. База — `f8c942e`, рабочий каталог
`/Users/rugilf/Projects/lifety-core`, ветка `main`.
Первоначально порт проверен локально; затем владелец разрешил commit и push
в main. Состояние до порта сохранено в `HSE-hack-2026` (`f8c942e`).

## Что реализовано

- Полный SwiftUI/Xcode host с общим Compose-кодом, схемой Run/Test и Gradle Build
  Phase. Собирается из исходников: framework не нужно копировать вручную.
- Нативная нижняя панель UIKit UITabBar: SF Symbols, системное стекло, зелёный
  акцент Lifety, safe area и native accessibility. Android сохраняет M3-панель.
- Все пять разделов: Dashboard, План/Календарь, Финансы, Здоровье, Assistant.
  Общие CRUD, графики, фильтры, задачи с датами и AI approvals не переписаны.
- SQLite NativeSqliteDriver: общие репозитории и история сохраняются после
  завершения процесса. Модели и schema version не менялись.
- API через Ktor Darwin. Настройки и API-ключ после «Сохранить и подключить» —
  один элемент Keychain с `WhenUnlockedThisDeviceOnly`, без iCloud-синхронизации
  и без записи ключа в папку агента. Ключ не входит в приложение.
- Native QuickJS: вычисления BigInt, файловые отчёты, локальная история/память.
- Read-only HealthKit: сон и шаги, выбор показателей, системный запрос доступа,
  явное чтение последних 30 календарных дней, предпросмотр каждого дня и отдельное
  подтверждение пакетной записи в общую SQLite. Ручная история доступна всегда.
- Импорт можно отменить; запросы останавливаются при выходе/таймауте.
  Пустой результат не стирает данные, конфликт после preview требует перечитать.
- iOS-подписи настроек исправлены; недоступная секция банковских уведомлений скрыта.
  Есть AppIcon из существующего знака Lifety и цвет запуска без белой вспышки.

## Версии и запуск

Проверено: macOS arm64, Xcode 27.1 / SDK 27.1, Simulator iOS 26.5,
JDK 21, существующий KMP/Compose/SQLDelight стек. Deployment target **iOS 26.5+**.
Нативные объекты существующего QuickJS built for 26.5, Compose ICU — 18.5;
не заявляем поддержку iOS 17 при таких бинарниках. Поддержка более старых iOS
требует отдельно пересобрать/заменить эти зависимости и проверить runtime.
Intel simulator target в проекте не предусмотрен; поддерживаются arm64 simulator
и физический iPhone/iPad arm64.

1. Открыть `iosApp/Lifety.xcodeproj` в Xcode.
2. Scheme **Lifety**, выбрать iPhone Simulator, Run.
3. Для телефона: выбрать свою **Team** в Signing & Capabilities, проверить
   HealthKit и provisioning profile, подключить iPhone и Run. Team не зашита.
4. Assistant → настройки: базовый URL `https://openrouter.ai/api/v1`, своя модель
   и ключ; сохранить, явно выбрать доступ к данным и отправить сообщение.
5. Health → «Импорт из Apple Health»: настроить доступ, прочитать, проверить,
   сохранить. На реальном телефоне в Apple Health должны быть исходные записи.

Терминальная сборка без зависимости от IDE:

```sh
cd /Users/rugilf/Projects/lifety-core
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME="$HOME/Library/Android/sdk"
./scripts/build-ios-simulator.sh
```

Результат: `build/ios/Build/Products/Debug-iphonesimulator/Lifety.app`.
Для физического iPhone нужен device build с вашей подписью; simulator .app
на телефон не устанавливается. CocoaPods и сервер не нужны.

## HealthKit: правила данных

Шаги — `HKStatisticsCollectionQuery`, cumulativeSum по календарным дням.
Сон — asleepUnspecified/Core/Deep/REM; inBed/awake не считаются сном.
Пересекающиеся интервалы стадий и источников объединяются перед суммированием;
интервалы обрезаются по локальным границам дня и текущему моменту. DST учитывается.
Это дневные итоги сна, а не список отдельных ночей или стадий в интерфейсе.

Завершённые дни сохраняются со стабильным timestamp локальной полуночи, текущий
день — с моментом чтения. Последующие импорты не размножают завершённые дни.
`null` сохраняет неизвестность; поля существующей строки при частичном импорте
не стираются. Репозиторий записывает пакет атомарно. Несколько снимков одного дня
для графика обрабатываются существующей логикой latest-known каждого показателя.

HealthKit скрывает отказ в read-доступе: приложение не может достоверно показать
«чтение разрешено» по authorizationStatus или success callback. Пустой результат
может означать отсутствие данных или доступа. Это учтено в UI и следует
[модели разрешений Apple](https://developer.apple.com/documentation/healthkit/hkauthorizationstatus).
Дневные quantity-агрегаты используют
[HKStatisticsCollectionQuery](https://developer.apple.com/documentation/healthkit/hkstatisticscollectionquery).
Запись в HealthKit, фоновые чтения, HealthKit clinical records не запрашиваются.
Разрешения здоровья и разрешения отправки данных AI независимы.

## Структура и интеграция

- `iosApp/Lifety.xcodeproj`: готовый проект, shared scheme и Test target.
- `iosApp/Lifety/LifetyApp.swift`: host и регистрация `IosServices`.
- `iosApp/Lifety/{AppleHealthSource,SleepHistory,KeychainStore}.swift`: адаптеры.
- `iosApp/Lifety/{Info.plist,Lifety.entitlements,Assets.xcassets}`: пакет приложения.
- `iosApp/LifetyTests/IosPlatformTests.swift`: native XCTest.
- `scripts/build-ios-simulator.sh`: воспроизводимая сборка.
- `shared/.../app/PlatformNavigationBar`: expect и Android/iOS actual.
- `shared/src/iosMain/.../core/platform/IosServices.kt`: типизированная граница Swift.
- `shared/src/iosMain/.../feature/health/HealthConnectSection.ios.kt`: импорт и preview.
- `shared/src/iosMain/.../feature/assistant/agent/AgentPlatform.ios.kt`: Keychain vault.
- `shared/src/iosTest`: реальные NativeSqliteDriver/QuickJS и vault boundary tests.

Бизнес-модели, интерфейсы репозиториев, маршруты и public screen entry points
сохранены. Платформенные подписи используют внутренний `isIos`. Остальные экраны
остаются Compose Multiplatform; это не переписывание приложения целиком на SwiftUI.
Основа интеграции — [Compose/UIKit](https://kotlinlang.org/docs/multiplatform/compose-uikit-integration.html)
и [Gradle/Xcode direct integration](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html).

## Проверки и ограничения

Команды выполнены с указанным JDK/SDK:

```sh
./gradlew :androidApp:build :shared:testAndroidHostTest :shared:iosSimulatorArm64Test
xcodebuild -project iosApp/Lifety.xcodeproj -scheme Lifety -configuration Debug \
  -destination 'platform=iOS Simulator,id=0CF2279F-54B7-4BA7-AB07-32DC12D9A155' \
  -derivedDataPath /Users/rugilf/Documents/Codex/2026-10-25/e/work/ios-derived test
xcodebuild -project iosApp/Lifety.xcodeproj -scheme Lifety -configuration Debug \
  -sdk iphoneos -destination 'generic/platform=iOS' \
  -derivedDataPath /Users/rugilf/Documents/Codex/2026-10-25/e/work/ios-device \
  CODE_SIGNING_ALLOWED=NO build
```

- Wrapper `./scripts/build-ios-simulator.sh` также проверен без JAVA_HOME в
  окружении: на этом Mac автоматически выбран Homebrew JDK 21.
- Android debug/release build и lint — успешно; host suite: 148 passed,
  6 skipped (live LLM tests без ключей), 0 failures/errors.
- Kotlin/Native iOS simulator: **54 passed**, включая SQLite reopen, сохранение
  точных Long > 2^53 и всей Health-истории, реальный QuickJS BigInt/файлы,
  восстановление credentials через Swift-host boundary.
- Native XCTest: **4 passed** — реальный Keychain insert/update/reopen, DST,
  перекрытия сна, полночь, пропуски и обрезка текущего дня.
- Simulator приложение подписывается ad hoc; device arm64 собирается без подписи.
- В Device Hub вручную проверены старт, native tab switching, создание задачи
  с датой, появление в календаре, сохранение после перезапуска, ручные сон/шаги,
  обновление графиков, создание расхода и пересчёт сумм, запуск Assistant и его
  настройки Keychain, экран Apple Health, системный read-access sheet и ошибка
  чтения до выдачи разрешений. Тестовые записи созданы только в отдельном QA simulator.
- Не проверены реальный iPhone/provisioning, Apple Watch и чтение реальной истории
  HealthKit. Системный диалог показан; выдача разрешений и полный импорт реальных
  данных не заявляются проверенными. AI HTTP live на iOS не запускался без ключа.
- Нет чтения уведомлений других приложений на iOS; ручные финансы доступны.
  Ранее не реализованные напоминания/повторы не добавлены этим портом.
- Полный iPad UI, VoiceOver, Dynamic Type и измерение FPS не проверены.
  Часть контролов и формы остаются M3/Compose, нижняя панель — native UIKit.
