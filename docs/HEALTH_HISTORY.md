# Health: история сна и шагов

Дата: 25 октября 2026. Основание — main `4816143` после PR #3.
Рабочая ветка — `feature/core`; самостоятельного merge в main нет.

## Реализовано

- Общий Compose-экран показывает всю сохранённую SQLite-историю, а не только
  `observeLatest()`. Периоды: 7 дней, 30 дней, всё время.
- Два графика: сон и шаги, среднее по дням с известным показателем,
  покрытие периода, выбор даты касанием и кнопками, список всех записанных дней.
- Ручная запись сна/шагов за сегодня или прошлую дату в Material 3 bottom sheet.
  Черновик сохраняется при повороте, форма закрывается после успешной записи;
  во время сохранения закрытие заблокировано. Будущая дата отклоняется.
- Health Connect на Android читает одним `aggregateGroupByPeriod` запросом
  дневные итоги за последние 30 календарных дней, включая текущий неполный день.
  Чтение и сохранение раздельные, только по явному действию. Предпросмотр показывает
  каждый полученный день. Открытие раздела не импортирует показатели.
- При повторном импорте завершённые дни обновляются по стабильному timestamp
  локальной полуночи. Текущий день получает timestamp момента чтения. Весь пакет
  сохраняется SQLDelight-транзакцией. Старые дни за пределами периода сохраняются.
- Смена разрешений или изменение локальной истории после предпросмотра требует
  перечитать данные. Пустой ответ ничего не записывает. Частичный импорт сохраняет
  известные поля существующей строки, если timestamp совпал.

## Как считаются показатели

Дата определяется в часовом поясе устройства. В одном дне для сна и шагов
независимо выбирается последнее ненулевое (`non-null`) значение по timestamp.
Несколько накопительных замеров шагов не складываются. Например, 3000 и 7000
шагов за один день дают 7000; более поздняя запись только шагов не стирает сон
из предыдущей записи того же дня. Явный 0 учитывается, `null` означает отсутствие
данных и не участвует в среднем. Средние округляются вниз до целой минуты/шага.

В 7/30-дневном периоде показаны и пропущенные даты. В «Всё время» берутся все
сохранённые даты, позиции на оси времени пропорциональны расстоянию между датами.
Линия прерывается между днями с пропусками. Выбор кнопками в этом режиме переходит
между сохранёнными днями. Список реализован через LazyColumn со стабильными ключами.

Импорт Health Connect — календарные дневные итоги, а не отдельные ночные сессии:
сон, пересекающий полночь, может распределяться между соседними днями провайдером.
Нет анализа стадий сна, пульса, медицинских рекомендаций или вымышленных измерений.
Старые snapshots не содержат границы измеряемого периода и источник; они привязаны
к дате их timestamp. Схема модели остаётся прежней.

Без дополнительного history permission приложение по умолчанию может читать
Health Connect начиная с 30 дней до первого разрешения. Текущий UI сознательно
запрашивает только последние 30 календарных дней; длительную историю и фоновое
чтение не запрашивает. Ограничения и использование LocalDateTime/Period проверены
по [официальной документации Android](https://developer.android.com/health-and-fitness/health-connect/aggregate-data).
Ранее импортированные локальные записи доступны без ограничения 30 днями.

## Контракт и файлы

Модель `ru.lifety.core.model.HealthSnapshot` неизменна. Публичный контракт
`ru.lifety.core.repository.HealthRepository` дополнен:

```kotlin
fun observeAll(): Flow<List<HealthSnapshot>>
suspend fun updateAll(snapshots: List<HealthSnapshot>)
```

Существующие `observeLatest()` и `update(snapshot)` сохраняют сигнатуры и семантику.
Ради исходной совместимости default `observeAll` оборачивает latest в список,
default `updateAll` вызывает update последовательно. Адаптеры с историей должны
переопределить эти методы: production SQLDelight выдаёт полный `timestamp DESC`
и записывает пакет атомарно. Каждая строка полностью заменяется на уровне
репозитория; объединение пустых полей при импорте находится в Health controller.

Таблицы, формат файла `lifety.db` и schema version 1 прежние. Добавлен только
`selectHealthHistory`; миграция не требуется, ранее сохранённые строки доступны.
DI, Manifest, Gradle и публичный `HealthScreen(modifier)` не менялись.
Dashboard/Assistant сохраняют прежнее поведение latest; история здоровья не
передаётся в AI автоматически. Финансовые функции не изменены.

Основные изменения:

- commonMain `feature/health/HealthHistory.kt`: группировка и статистика.
- commonMain `feature/health/HealthCharts.kt`, `HealthScreen.kt`: графики/история/форма.
- commonMain `feature/health/HealthController.kt`, `HealthConnectController.kt`:
  наблюдение, ручная дата, подтверждение импорта истории.
- androidMain `feature/health/AndroidHealthConnectGateway.kt`,
  `HealthConnectSection.android.kt`, `HealthPermissionsRationaleActivity.kt`:
  дневной aggregate, разрешения и предпросмотр.
- commonMain `core/repository/HealthRepository.kt`,
  `database/SqlDelightRepositories.kt`, `sqldelight/ru/lifety/database/Lifety.sq`:
  полная история и пакетная запись.
- commonTest `feature/health/HealthHistoryTest.kt`;
  androidHostTest `database/HealthHistoryPersistenceTest.kt`,
  `feature/health/{HealthHistoryControllerTest,HealthHistoryImportTest}.kt`.
- README, CONTRACTS, DESIGN_SYSTEM, health/INTEGRATION и этот отчёт.

## Проверка

JDK 21, Android SDK 37; ключи LLM удалены из окружения проверки:

```sh
env -u LIFETY_LLM_API_KEY -u LIFETY_LLM_BASE_URL -u LIFETY_LLM_MODEL \
  JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/Users/rugilf/Library/Android/sdk \
  ./gradlew :androidApp:build :shared:testAndroidHostTest \
    :shared:compileKotlinIosSimulatorArm64 \
    :shared:compileCommonMainKotlinMetadata --console=plain --stacktrace
```

Android debug/release build и lint, common metadata и iOS Simulator arm64 compile
успешны. Lint: 0 errors, 21 warning (в том числе существующие рекомендации
по зависимостям и конфигурации Android). Host suite: 138 tests, 135 passed, 3 skipped (существующие live LLM tests
без credentials), 0 failures/errors. Новые 13 тестов: группировка и пропуски,
ноль, независимые показатели, длинная история, большие суммы шагов, локальные даты
и DST, реактивность, атомарный rollback, перезапуск файловой SQLite, ручная дата,
права/отзыв, пустой ответ, частичный и повторный импорт, конфликт старой записи.

На отдельном Android 15 эмуляторе установлен debug APK и проверен холодный запуск
с искусственной 45-дневной историей. Эти данные не добавлены в приложение/репозиторий.
Проверка реальных разрешений и истории Health Connect на телефоне не выполнена.
Визуальная проверка графиков/клавиатуры и измерение FPS не выполнены: инструменту
управления UI недоступно отдельное окно эмулятора. iOS UI и runtime не запускались.
История произвольного размера не профилировалась; график «Всё время» рисует каждую
сохранённую дату без пагинации. Release APK остаётся unsigned.
