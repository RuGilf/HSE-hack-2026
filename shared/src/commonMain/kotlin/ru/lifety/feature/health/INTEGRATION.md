# Android Health Connect

Ветка: `feature/assistant`. Основание: актуальный `main` (`2518b8a`), полученный
через `git pull --ff-only origin main`; обновление включено в feature-ветку.
Модели, публичные repository/screen signatures, SQL-схема, root DI и навигация
не изменены. Минимальные изменения Gradle и Manifest отдельно разрешены пользователем.

## Что работает

- Android 9+ проверяет наличие и доступность Health Connect. Android 14+ использует
  системный провайдер, Android 9–13 — установленное приложение Health Connect.
  На Android 7–8 и неподдерживаемых профилях остаётся ручной ввод.
- Установка/обновление провайдера и открытие его настроек доступны по явным кнопкам.
- Запрашиваются только `android.permission.health.READ_SLEEP` и
  `android.permission.health.READ_STEPS`. Пользователь может выбрать один показатель
  и выдать разрешения частично. Разрешения записи, фонового чтения и длинной истории
  не запрашиваются. Lifety не записывает ничего в Health Connect.
- При открытии экрана и возвращении из системных настроек проверяются только
  доступность и разрешения; показатели не читаются и SQLite не меняется.
- «Прочитать показатели» запускает ограниченное чтение выбранных разрешённых данных.
  Шаги агрегируются с начала текущего локального дня до момента запроса. Сон —
  за последние 24 часа. Используется Aggregate API с дедупликацией и приоритетами
  источников Health Connect; сырые записи и названия источников не копируются.
- Перед каждым aggregate разрешение проверяется заново. Отзыв доступа между запросами,
  исключения провайдера, таймауты и отсутствие записей обрабатываются без падения.
- Значения сначала появляются в предпросмотре. Только «Сохранить этот снимок» вызывает
  существующий `HealthRepository.update`. Общие SQLite Flow обновляют Health и Dashboard.
- Полностью пустой результат не заменяет ручные записи. `null` означает неизвестное,
  явный ноль остаётся нулём. Частичный снимок сохраняется целиком, без подмешивания
  старых значений; UI предупреждает об этом до подтверждения.
- Изменение локального снимка между чтением и сохранением требует нового предпросмотра.
  Смена выбора или отзыв доступа сбрасывают недействительный предпросмотр.
- Запись Health Connect не включает облачные/AI-разрешения. ContextBuilder и ручной
  Analyze продолжают использовать отдельные, выключенные по умолчанию разрешения.
- Ручная форма доступна при отсутствии Health Connect; одновременные действия импорта
  и ручного сохранения в UI блокируются. iOS по-прежнему использует ручной ввод.

## Подключение приложения часов / фитнеса

1. В совместимом приложении здоровья включить передачу сна и шагов в Health Connect.
   Синхронизировать часы/телефон с этим приложением. Lifety не подключается напрямую
   к закрытому API конкретного производителя и не требует аккаунта на сервере.
2. В Lifety → «Здоровье» → «Связь с Health Connect» нажать «Настроить разрешения».
   Выдать только нужное чтение. Если ранее отказали, открыть Health Connect и изменить
   разрешения Lifety в его настройках.
3. Нажать «Прочитать показатели», проверить период и значения, подтвердить сохранение.
   Если записей нет, проверить разрешения на запись у приложения-источника и его
   синхронизацию; существующие ручные значения Lifety останутся на месте.
4. Отозвать доступ можно в Health Connect. Уже сохранённые локальные снимки остаются
   в Lifety; отзыв прекращает дальнейшее чтение, а не удаляет историю приложения.

## Минимальная Android-интеграция

- `gradle/libs.versions.toml`: добавлен только connect-client `1.2.0-alpha06`.
  Это alpha-релиз с актуальными сертификатами провайдера. Release notes заявляют
  снижение minSdk до 24 в alpha05, однако фактический Maven AAR alpha06 по-прежнему
  содержит minSdk 26. Существующий minSdk Lifety остаётся 24: добавлен только
  точечный `tools:overrideLibrary="androidx.health.connect.client"`.
  Весь SDK/UI и создание клиента находятся за проверкой `SDK_INT >= 28`.
  Добавляемый библиотекой bound service принудительно выключен ниже API 28 через
  `res/values/health_connect.xml` и `res/values-v28/health_connect.xml`.
  Это необходимо отдельно проверить на API 24–27 при проверке на устройстве.
- `shared/build.gradle.kts`: Android-only `connect-client` и уже используемый в
  приложении `activity-compose` для системного Activity Result launcher.
  Версии существующего стека и `minSdk` не меняются.
- `androidApp/build.gradle.kts`: та же зависимость `connect-client` явно видна приложению,
  поскольку его Manifest настраивает bound service SDK. Это позволяет Lint разрешить
  класс компонента; версия берётся из одного общего catalog entry.
- `androidApp/src/main/AndroidManifest.xml`: два read permissions, видимость пакета
  Health Connect, feature-owned Activity с объяснением приватности и защищённый
  Android 14+ alias `VIEW_PERMISSION_USAGE`. Android 13 использует
  `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`.
- Activity объяснения находится в `shared/src/androidMain/.../feature/health` и
  не открывает БД, не читает показатели и не запускает AI. На отдельном экране
  используется та же `LifetyTheme`.

## Файлы

Относительно `shared/src/<source set>/kotlin/ru/lifety/feature/health/`:

| Source set | Файлы |
| --- | --- |
| commonMain | HealthScreen.kt, HealthConnectController.kt, INTEGRATION.md |
| androidMain | AndroidHealthConnectGateway.kt, HealthConnectSection.android.kt, HealthPermissionsRationaleActivity.kt |
| iosMain | HealthConnectSection.ios.kt |
| androidHostTest | AndroidHealthConnectGatewayTest.kt, HealthConnectControllerTest.kt |

Плюс четыре интеграционных файла Gradle/Manifest и два Android bool-ресурса,
перечисленные выше.

## Проверка

Обязательная команда: `gradlew.bat --no-daemon :androidApp:assembleDebug :shared:testAndroidHostTest`.
Новые тесты проверяют разрешения, отсутствие автоматического чтения/сохранения,
частичный доступ, ручное подтверждение точного снимка, пустые значения/нули,
конфликт локальной записи, отмену, ошибки и преобразование единиц/границы DST.

На этой Windows-машине `adb devices -l` не обнаружил подключённых устройств.
Поэтому реальный системный диалог, доступ к установленному провайдеру и импорт
показателей с телефона/часов требуют проверки на Android-устройстве. iOS HealthKit
не добавлялся; iOS runtime/компиляция на Windows недоступны.

Результат проверки 25 октября 2026, Windows x86_64 / JDK 21.0.8:

- `:androidApp:assembleDebug :shared:testAndroidHostTest`: **BUILD SUCCESSFUL**.
- **92 теста, 0 failures, 0 errors, 0 skipped**; в том числе 21 новый тест
  Health Connect (14 workflow/consent и 7 Android aggregate adapter).
- `:androidApp:lintDebug`: **BUILD SUCCESSFUL**, 0 ошибок, 21 предупреждение
  о версиях зависимостей и существующей конфигурации backup. Проверки не отключались.
- Итоговый merged Manifest: ровно два health read permissions, minSdk 24,
  bound service с `@bool/lifety_health_connect_enabled`. В собранном APK
  проверены `false` по умолчанию и `true` для v28, а также наличие
  `HealthDataSdkService` в DEX. Сам SDK требует явной compile-зависимости
  Android-приложения для корректного разрешения этого компонента в Lint.
- APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
- Core-модели, database, SQL, root DI и navigation не менялись.

## Официальные источники

- [Начало работы, permissions и privacy Activity](https://developer.android.com/health-and-fitness/health-connect/get-started)
- [Версии SDK и minSdk](https://developer.android.com/jetpack/androidx/releases/health-connect)
- [Агрегация и устранение дублей источников](https://developer.android.com/health-and-fitness/health-connect/aggregate-data)
