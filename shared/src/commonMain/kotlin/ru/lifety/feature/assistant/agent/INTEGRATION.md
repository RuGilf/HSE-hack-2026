# Подключение агента Lifety

Ветка `codex/native-agent-harness`. Здесь ядро, платформенные адаптеры и
подключённый к ним AssistantScreen. Навигация, общие модели, репозиторные
контракты и схема БД не меняются. Пакет ядра: `ru.lifety.feature.assistant.agent`.

## Готовый экран приложения

Дополнение: ответы и сохранённые разборы отображаются через Material 3
multiplatform-markdown-renderer 0.46.0. Зависимость разрешена пользователем отдельно.
История подключена через HealthRepository.observeAll из свежего main 7dd6c09.
Кнопка подключения показывает
«Сохранено» и переход «К чату» после успешной настройки; доступность провайдера
проверяется реальной отправкой, сохранение само по себе запрос не делает.
На предоставленном OpenRouter/nvidia/nemotron-3-ultra-550b-a55b:free прошёл
реальный Connect -> Send через AgentChatController. Ключ только в env теста.
Совместный прогон поверх main 7dd6c09: Android build/lint, common metadata и
iOS Simulator source compile успешны; 154 host tests, 147 passed, 7 skipped
(6 opt-in live и symlink Windows). Отдельно live Connect -> Send прошёл на
предоставленном OpenRouter. Исправлено падение раскрытия действий при неверном
типе path от модели; cancel -> повторная отправка -> reopen покрыты HTTP-тестом.

Health читает data/health/history/*.json, а не один latest. В источнике указан
deviceTimeZone. Android Health Connect из main предлагает отдельный импорт до 30
календарных дней с предпросмотром; пустые дни пропускаются, текущий
день неполный. Сон агрегируется в пределах календарного дня, это не список
отдельных ночных сессий. При обновлении APK повторите явный импорт: старое
однодневное чтение не содержит историю. Одинаковые timestamps заменяются,
повторные текущие наблюдения группируются по дню при анализе. Batch save выполняет
общий updateAll атомарно; отдельного хранилища агента для здоровья нет.

AssistantScreen использует AgentChatController и rememberAgentRuntime (Android/iOS),
получает тот же LocalRepositories. Корневой DI и маршрут assistant менять не надо.
Старый AssistantController/одноразовый AI оставлен для совместимости, экран его
больше не вызывает. Настройки открываются шестерёнкой; «Сохранить и подключить»
явно сохраняет credentials через платформенный vault, но не отправляет запрос.
Выбор доступа требует «Применить доступ»; scopes изначально выключены.
Отправка сообщения — единственный запуск API. «Стоп» отменяет текущий job;
при выходе экран закрывает session. Скрытые reasoning-блоки не отображаются.
Действия показаны компактным раскрываемым блоком с понятными названиями.
Proposals отображают значения до/после и требуют отдельного подтверждения.
«Сохранённое» содержит подтверждённую память и отчёты; удаление памяти предупреждает
об очистке рабочих материалов. Новый разговор оставляет факты и файлы.
API-ключ не используется в rememberSaveable, чат не подменяется синтетикой.
На физическом устройстве интерфейс/Keystore и iOS runtime ещё требуют smoke test.
Проверки UI-интеграции: Android build/lint, common metadata и iOS Simulator
source compile прошли; 137 host tests, 131 passed, 6 skipped (5 opt-in API и
symlink Windows). Новый controller проверен реальным локальным HTTP fixture:
инициализация/настройки не отправляют запросы, Send отправляет, New Chat не
вызывает API. Перед UI-подключением прошли три живых сценария на предоставленном
API: точный денежный отчёт, совет о сне и восстановление сохранённого разбора.

## Поведение в разговоре и сохранённые разборы

Агент отвечает обычным языком: полезный вывод или одно необходимое уточнение,
короткое объяснение. Внутренние пути, ID и поля JSON остаются в отчёте/трассировке,
если пользователь отдельно не просил их показать. Не удаляйте технические
источники из сохранённых отчётов ради краткости чата.

Индекс содержит до трёх имён файлов на каталог notes/reports/scratch/scripts,
а остальные обнаруживаются через files_list/search. Содержимое не загружается
заранее. Ограничение health repository до latest не исключает отдельный импорт
истории; импорт и сохранённый анализ не являются подтверждённым личным фактом.
Для wellbeing есть отдельный загружаемый skill; это инструкции, а не медицинская
модель или интеграция с Health Connect.

Результаты инструментов текущего пользовательского хода остаются в контексте
до сжатия. Старые результаты можно заменить ссылками на сохранённый оригинал.
Сжатие получает цитируемую историю в одном JSON-сообщении, чтобы модель не
продолжала вызовы из старого диалога. LOCAL_STATE передаётся до реальной истории,
последнее сообщение пользователя/результат инструмента остаётся последним.

Для сокращения обменов модель может вернуть до 12 независимых tool_calls за
один ответ; runner выполняет их по порядку и возвращает все результаты вместе.
files_read по умолчанию отдаёт до 6000 символов, меньший limit остаётся доступен.
script_run принимает необязательный content: сохраняет scripts/*.js и исполняет
его одним вызовом. Для перезаписи нужен expectedRevision; при конфликте запуск
не происходит. Внутри одного скрипта доступны несколько чтений, расчёт и запись
отчёта. Вызов только с path по-прежнему запускает ранее сохранённый код.
Это сокращает необходимые шаги, но не гарантирует фиксированное число API-запросов:
выбор пакетирования и метода остаётся за моделью.

Проверка 25 октября поверх origin/main 4816143: Android build/lint, common
metadata и iOS Simulator arm64 source compile прошли. Полный host-набор без
внешнего API: 135 тестов, 129 passed, 6 skipped (5 opt-in live и symlink Windows).
Предыдущий общий прогон с API выявил устаревшее имя поля индекса в WorkspaceTest
(исправлено) и сетевой сбой до первого ответа модели в bedtime eval. Живые проверки
запускаются отдельно; успешный локальный набор не доказывает доступность провайдера.

Изолированный браузерный пульт находится в androidHostTest/.../agent/playground
с отдельным README. В APK он не включается. PersonalAdviceEvalTest включает
реальный API только при наличии LIFETY_LLM_*; использует синтетические данные и
сохраняет трассировки в ignored shared/build/advice-eval, не в чате пользователя.

## Android: точка входа

```kotlin
val runtime = createAndroidAgentRuntime(applicationContext)
// После явного ввода пользователем; ключ не должен попадать в чат или workspace.
runtime.vault.save(AgentCredentials(
    settings = ProviderSettings(
        baseUrl = enteredBaseUrl, // включая /v1, если этого требует провайдер
        model = enteredModel,     // Chat Completions + function calling
        contextTokens = 32768,    // реальное окно выбранной модели
    ),
    key = enteredApiKey,
))
// Существующий Repositories из DI, например из LocalRepositories на стороне UI.
// null допустим: файловая среда работает, источники приложения недоступны.
val session = runtime.open(repositories)
```

Сохранение настроек и open — suspend. open загружает состояние, HTTP-запроса нет.
Ключ на Android шифруется через Keystore, файлы находятся в noBackupFilesDir.
На iOS: `createIosAgentRuntime()`, API-ключ только в памяти объекта runtime,
на диск сохраняются настройки без ключа. Держите один runtime/одну session в
вашем владельце жизненного цикла; повторное open до close вернёт busy.

## Данные, запуск, остановка

```kotlin
// Только по явному выбору пользователя; данные могут быть отправлены LLM API.
session.runner.changeScopes(AgentScopes(
    finance = true, tasks = true, health = false, memory = true,
))

// Отдельная coroutine вашего UI: StateFlow документа/фазы/ошибки/числа вызовов.
session.runner.state.collect { state -> renderYourUi(state) }

// В другой coroutine/обработчике:
val job = session.start(userMessage)
job.join() // можно не ждать: прогресс приходит через state
session.cancel()
job.join() // дождаться сохранения прерванного запуска
session.close()
```

Перед сменой scopes объясните: история чата, ожидающие предложения и созданные
агентом файлы очищаются. Подтверждённые факты сохраняются приватно, отключённые
домены исключаются из профиля/контекста. Отозвать уже отправленные данные нельзя.
Это разрешение на работу агента с данными; системные Health Connect permissions
и синхронизацию реализует владелец Health/Core.

При отзыве доступа во время работы сначала вызовите session.cancel() и дождитесь
активного job.join(), затем changeScopes. Методы runner сериализованы; сама
смена scopes не отменяет уже работающий job. До завершения операции не запускайте
новую задачу с прежними разрешениями.

Foreground API не является Android-сервисом. Background/WorkManager не входят
в этот модуль. Управляйте запуском и close в своём lifecycle. Сначала отмените
и дождитесь job, затем закройте session перед новым open.

Стандартный бюджет запуска: до 16 модельных и 48 инструментальных шагов,
до 180 секунд. При необходимости передайте `runLimits = AgentLimits(...)` в
runtime.open. Размер контекста берётся из ProviderSettings. Лимит генерации
определяет провайдер: max_tokens/max_completion_tokens в HTTP не отправляются.
Внутренний outputReserveTokens — только резерв при локальной проверке контекста.

## Подтверждение действий и память

Показывайте `state.document.proposals` со status pending/applying, точные kind,
body и expected (старое состояние), кнопки подтверждения и отклонения.

```kotlin
session.runner.approve(proposal.id) // suspend, только по действию пользователя
session.runner.reject(proposal.id)
session.runner.forget(fact.id)      // также очищает чат и производные файлы
session.runner.newChat()           // очищает чат/предложения, оставляет файлы/факты
```

После перезапуска applying требует ручного подтверждения; автоматического
повтора нет. Stable IDs защищают от повторного создания записи. Сравнение состояния
обнаруживает правки, уже существующие на момент подтверждения. Чтение и запись
репозитория не являются атомарным compare-and-set: изменение между ними возможно;
для строгой защиты нужен новый Core-контракт условной записи. Поддержаны
task_upsert/delete, expense_upsert/delete и
memory_set, запись через переданные репозитории. Health read-only.
Созданное предложение ещё не является сохранённой задачей/расходом.

Подтверждённая память содержит источник, дату, домен и дословную цитату текущего
сообщения; исправление заменяет факт по key+domain. Гипотезы агент сохраняет в
notes/. Перед forget предупредите об удалении чата/производных файлов, чтобы
забытое не вернулось из старой заметки. Остальная подтверждённая память остаётся.

Ошибки запуска/approve доступны в state.error. Файловые методы, forget/newChat
также могут бросить исключение хранилища — обработайте его в coroutine. Если
checkpoint повреждён, initialize возвращает ERROR и не перезаписывает оригинал.

## Среда и расширение

- INDEX.md, schemas/, skills/ — индекс, схемы и инструкции по запросу.
- data/expenses/*.json, data/tasks/*.json — полные снимки с разбивкой на части.
- data/health/latest.json — только последний снимок; истории в контракте нет.
- memory/profile.json — разрешённые подтверждённые факты.
- notes/, scripts/, scratch/, reports/ — редактируемые файлы.
- history/ — результаты инструментов/скриптов, недоступные для записи агенту.
- Checkpoint/credentials находятся вне доступной агенту рабочей папки.

Runtime.workspace предоставляет files/read/write/delete/runScript и интегратору.
Перезапись/удаление требуют актуальную revision. Просмотр файлов делает ваш UI.
Новый анализ — другой скрипт; новый источник — адаптер экспорта с описанием и
разрешением. Собственной БД расходов у агента нет. Исходный текст банковских
уведомлений не экспортируется. amountMinor — строка целого Long, расчёты через
BigInt, валюты отдельно. null означает неизвестное, не ноль.

Скрипты: JavaScript ES module/top-level await; fs.readText, fs.list (JSON-строка),
fs.writeText и console.log. Нет Node/shell/npm/network/reflection bindings.
QuickJS выполняется в процессе приложения, это не OS/process sandbox.
Лимиты: heap 32 МиБ, stack 256 КиБ, JS 2 секунды, 200 файловых операций,
16000 символов stdout; workspace 20 МиБ, файл 1 МиБ, до 1024 файлов.

Контекст: правила/инструменты, небольшой индекс, до пяти фактов, текущая цель и
структурированная сводка. Подробности читаются по необходимости. Старые успешные
объёмные tool results заменяются ссылками, оригиналы доступны в history/.
Сводка хранит goal/constraints/verified/artifacts/failed_attempts/next. Raw history
сохраняется в checkpoint до 4 МиБ. Незавершённые tool calls после перезапуска
получают interrupted receipts и не повторяются автоматически.

Счётчик входа — консервативная верхняя оценка по UTF-8, не tokenizer произвольной
модели. Actual prompt_tokens берётся из usage, если API его отдаёт. Маленькое
окно может закончиться раньше номинала; рекомендуемый старт — от 32k.

## Зависимости и проверка интегратора

QuickJS 1.0.15 уже добавлен в shared/build.gradle.kts с разрешения пользователя;
host runtime заменён на quickjs-kt-jvm. Изменение зависимости уже входит в ветку,
дополнительный патч применять не нужно. Manifest уже имеет INTERNET;
дополнительных изменений не требуется. Все прочие общие файлы не менялись.

```powershell
.\gradlew.bat :androidApp:build :shared:testAndroidHostTest `
  :shared:compileCommonMainKotlinMetadata :shared:compileKotlinIosSimulatorArm64
```

Host-тесты используют реальный QuickJS, файловую систему и локальный HTTP-сервер.
Решения модели в сценариях задаёт тестовый AgentModel: это проверки механики,
не качества выбранной LLM. После подключения UI проверьте живую модель:
найти данные -> написать/исправить скрипт -> посчитать -> сохранить отчёт ->
новая session -> вспомнить/исправить предпочтение -> подтвердить действие.
Замерьте качество результата, токены и шаги на разных формулировках.

LiveAgentEvalTest включается переменными LIFETY_LLM_BASE_URL, LIFETY_LLM_MODEL и
LIFETY_LLM_API_KEY в окружении тестового процесса. Ключ не находится в исходниках,
JSON-запросах или отчёте; диагностические запросы этого теста содержат только
синтетическую фикстуру и пишутся в ignored build/. На предоставленном Gemini API
прошли оба сценария: вычисление/сохранение точных денежных сумм и восстановление
памяти в новой сессии с подтверждённым исправлением предпочтения.

Android runtime/Keystore на устройстве и iOS runtime/link не проверены: устройства
нет. Компиляция iOS исходников проверяется указанной задачей. Один symlink-тест
Windows пропускает из-за прав хоста. ELF PT_LOAD alignment всех четырёх Android
ABI QuickJS проверен: 16384 байта. Локальный HTTP endpoint хост-теста не является
адресом API для телефона; интегратор должен задать доступный устройству адрес,
учитывая Android network security policy.

При достижении квоты можно удалить ненужные editable-файлы. Полный reset среды
интегратор выполняет только после подтверждения пользователя: cancel/join,
workspace.clear(), runner.newChat(). Отчёты/скрипты/история файлов удалятся,
подтверждённая память останется в приватном checkpoint и экспортируется заново.
Итоговый совместный прогон на origin/main 2518b8a: Android build, host tests,
common metadata и iOS simulator source compile успешны; 100 тестов, 0 ошибок,
1 пропуск (право Windows на symlink). Оба live eval включены и прошли.

## Передача другому агенту

Ветка синхронизирована с origin/main 4816143, включая OpenRouter reasoning metadata.
AssistantScreen уже подключён к AgentRuntime; дополнительной проводки в корне
не требуется. Сигнатура экрана, route и Repositories из DI сохранены.

Из сопутствующего аудита проекта (не изменения этой ветки):

- Покупка с отдельной суммой кэшбэка действительно теряется в банковском парсере.
  Нужен разбор основной операции/её сегмента: одной перестановки purchase/income
  мало, потому что текущая проверка отвергает две суммы до баланса.
- exported=false для NotificationListenerService совпадает с официальным примером
  Android; это само по себе не блокер. С Android 7 callbacks идут на main thread,
  а minSdk проекта 24. Заявленный Binder-thread race этим путём не подтверждён.
  Источник: https://developer.android.com/reference/android/service/notification/NotificationListenerService

## OpenRouter compatibility update

Provider reasoning_details are retained verbatim through tool calls and private checkpoints;
plaintext reasoning is supported when details are absent. Existing checkpoints remain readable.
AI request/socket timeout is 120 seconds; the total runner budget and cancellation still apply.
OpenRouter baseUrl is https://openrouter.ai/api/v1. Exact tested model/configuration and
results are in docs/OPENROUTER_API.md. No key is stored in this guide or supplied by default.
