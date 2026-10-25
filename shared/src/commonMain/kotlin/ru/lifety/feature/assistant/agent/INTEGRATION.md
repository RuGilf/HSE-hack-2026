# Подключение агента Lifety

Ветка `codex/native-agent-harness`. Здесь только агент и платформенные адаптеры.
UI, AssistantScreen, навигация, общие модели, репозиторные контракты и схема БД
не меняются. Пакет: `ru.lifety.feature.assistant.agent`.

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

Foreground API не является Android-сервисом. Background/WorkManager не входят
в этот модуль. Управляйте запуском и close в своём lifecycle. Сначала отмените
и дождитесь job, затем закройте session перед новым open.

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

Здесь нет API-ключа/устройства: не проверены живой провайдер, Android runtime и
Keystore на устройстве, iOS runtime/link. Проверяется компиляция iOS исходников.
Один symlink-тест Windows пропускает из-за прав хоста. ELF PT_LOAD alignment
всех четырёх Android ABI QuickJS проверен: 16384 байта.
