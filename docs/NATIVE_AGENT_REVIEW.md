# Review и интеграция native-agent-harness — 25 октября 2026

Решение: объединить всю опубликованную ветку `codex/native-agent-harness`
(`df063fa`) в `feature/core` поверх `b31966d`. Источник содержит четыре новых
коммита: `8e5c8d3`, `cc1c180`, `01b9194`, `df063fa`. Слияние без конфликтов.
Единственный открытый PR на момент проверки — #2 (`feature/core` → `main`).
Другие локальные worktree и main не менялись.

## Оценка

Ветка добавляет изолированный пакет `ru.lifety.feature.assistant.agent`,
Android/iOS адаптеры и тесты, плюс QuickJS 1.0.15 в shared Gradle.
Модели/репозиторные контракты, SQL, Manifest, root DI/navigation не меняются.
Не обнаружено блокеров для включения движка в общую кодовую базу.

Что проверено по исходникам и тестам:
- Runtime открывает одну сессию, сериализует операции, ограничивает модельные
  и инструментальные шаги; восстановление interrupted calls не повторяет действия.
- Рабочие файлы отделены от private checkpoint и credentials; проверяются пути,
  symlinks, revisions, квоты, read-only источники. BigInt сохраняет точность Long.
- Скрипт использует ограниченный QuickJS с таймаутом, лимитами памяти/стека;
  нет bindings Node, shell, сети и reflection. Это интерпретатор в процессе
  приложения, не изоляция отдельным OS-процессом.
- Модель может только предложить task/expense/memory изменение. Запись требует
  вызова approve со стороны интегратора после действия пользователя. Проверяется
  состояние записи на момент подтверждения, используются стабильные IDs.
- Read/check/write репозитория не является атомарным compare-and-set: конкурентная
  запись между проверкой и записью всё ещё возможна. Существующие Core interfaces
  не расширены ради строгой атомарности.
- Scopes default-off; смена доступа/forget очищает производные файлы и историю.
  Память содержит источник и дословную цитату. Повреждённый checkpoint не заменяется.
- HTTPS transport не следует redirects с ключом; размеры ответа и контекста
  ограничены, ошибки не содержат ключ/сырой API response. Провайдер определяет
  лимит генерации; max_tokens/max_completion_tokens не отправляются.

## Что появится в приложении после merge

Это Kotlin API и движок для будущего подключения UI. Текущий AssistantScreen
по-прежнему использует прежний ContextBuilder/AiProvider. Новый runtime не создаётся
при запуске приложения, ключи не сохраняются автоматически, фоновых задач нет.
Подключение чата, proposals/approval, просмотра файлов, scopes и lifecycle — отдельная
работа по `shared/src/commonMain/kotlin/ru/lifety/feature/assistant/agent/INTEGRATION.md`.
Не следует считать этот merge готовым агентным пользовательским сценарием.

Новый Android vault сохраняет ключ только при явном вызове vault.save, шифруя его
Android Keystore. Это отдельно от старого UI с ключом только в process memory.
Новый iOS vault сохраняет настройки, ключ держит в памяти runtime. Credential/device
runtime не проверены этим прогоном; iOS файлы также требуют проверки backup policy
перед подключением пользовательского потока.

## Выполненные проверки

macOS arm64, JDK 21. Первый запуск остановился на временном DNS-сбое dl.google.com.
Повтор в отдельном Gradle JVM загрузил зависимость из обычных репозиториев и прошёл:

```sh
env -u LIFETY_LLM_API_KEY -u LIFETY_LLM_BASE_URL -u LIFETY_LLM_MODEL \
  ./gradlew --no-daemon :androidApp:build :shared:testAndroidHostTest \
  :shared:compileKotlinIosSimulatorArm64 :shared:compileCommonMainKotlinMetadata \
  --console=plain --stacktrace
```

BUILD SUCCESSFUL: Android debug/release, lint (0 ошибок, 21 прежнее предупреждение),
iOS Simulator arm64 source compile и common metadata compile.
121 host test: 119 прошли, 0 failures/errors, 2 skipped — только LiveAgentEvalTest
без предоставленных для этого прогона credentials. Symlink test на macOS выполнен.
Локальные HTTP fixtures и реальные host QuickJS BigInt/timeout/memory сценарии прошли.
PT_LOAD alignment четырёх Android ABI QuickJS — 16384 байта.

Не проверены живой LLM этим интегратором, Android runtime/Keystore нового ядра,
iOS linking/runtime, физическое устройство и будущий UI агента. Результаты live
модели в исходной ветке являются отчётом её автора и здесь повторно не заявляются.

[QuickJS Kotlin API и host test substitution](https://github.com/dokar3/quickjs-kt).
