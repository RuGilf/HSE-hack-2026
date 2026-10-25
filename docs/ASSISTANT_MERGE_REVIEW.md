# Проверка feature/assistant — 25 октября 2026

Решение: объединить опубликованный `origin/feature/assistant` (`14a4311`)
в `feature/core` поверх Android UI polish (`f022cc8`). Main не менялся.
Локальный альтернативный worktree assistant не изменялся и не объединялся.

## Что добавлено

Один новый опубликованный коммит: Android Health Connect, сон и шаги.
Проверка доступности/разрешений при входе не читает измерения. Чтение запускается
кнопкой, сохранение точного снимка — отдельным подтверждением. Частичные разрешения,
отзыв доступа, пустые результаты, нули, ошибки/отмена, границы дня и DST покрыты тестами.
Общие модели, repository interfaces, SQL, DI, root navigation и AI transport сохранены.
На iOS остаётся ручной ввод, HealthKit не добавлен.

Единственный конфликт HealthScreen разрешён с сохранением компактной M3-компоновки,
IME, Snackbar и rememberSaveable черновиков. Добавлен HealthConnectSection и
блокировка ручной формы на время импорта. Старые дублирующие заголовки не возвращены.

## Проверки после разрешения конфликта

JDK 21, macOS arm64:

```sh
./gradlew :androidApp:build :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 --console=plain --stacktrace
```

BUILD SUCCESSFUL. Android debug/release, lint: 0 ошибок, 21 прежнее предупреждение.
92 host tests: 0 failures, errors и skipped. Это 71 прежний тест и 21 новый тест
workflow/aggregate adapter. iOS Simulator arm64 Kotlin compilation успешна.
На отдельном Android API 35 AVD: установка APK, холодный запуск MainActivity и
запуск HealthPermissionsRationaleActivity успешны; crash buffer пуст.
Реальный permission dialog и чтение показателей из провайдера не проверялись.

Merged Manifest сохраняет minSdk 24, добавляет ровно READ_SLEEP/READ_STEPS,
Activity объяснения и защищённый Android 14+ alias. Bound service библиотеки
использует bool false по умолчанию / true на API 28+.

## Ограничения и основание решения

Health Connect SDK 1.2.0-alpha06 — alpha-зависимость. Фактический AAR содержит
minSdk 26; приложение остаётся на 24 с точечным overrideLibrary. Код клиента
вызывается только на API 28+, сервис отключён ниже API 28. Эта защита проверена
по исходникам и итоговому Manifest, но запуск на API 24–27 ещё требует устройства.
Физический Android, импорт от часов/приложения-источника, iOS runtime не проверены.
Для хакатона изменение полезно и изолировано, при отсутствии Health Connect
сохраняется ручной ввод. Публикационную готовность эта проверка не подтверждает.

Официальные источники, сверенные при review:
- [SDK и версии](https://developer.android.com/jetpack/androidx/releases/health-connect)
- [Интеграция, permissions и версии Android](https://developer.android.com/health-and-fitness/health-connect/get-started)

Точный workflow: `shared/src/commonMain/kotlin/ru/lifety/feature/health/INTEGRATION.md`.
