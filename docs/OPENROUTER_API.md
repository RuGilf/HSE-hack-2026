# OpenRouter / Nemotron — настройка и проверка API

Модель: `nvidia/nemotron-3-ultra-550b-a55b:free`.
Публичный каталог OpenRouter на 25 октября 2026 подтверждает наличие модели,
контекст 1 000 000 токенов и поддержку tools/tool_choice/reasoning.
Рабочая база проекта — принятый PR #2, main `66af7d7`.

## Существующий экран Assistant

В «Подключение к ИИ» вручную указать:

- HTTPS endpoint: `https://openrouter.ai/api/v1/chat/completions`
- Модель: `nvidia/nemotron-3-ultra-550b-a55b:free`
- API-ключ: свой ключ OpenRouter; в исходники/настройки по умолчанию он не включён.

Выбрать категории, подготовить и проверить JSON, затем нажать «Проанализировать».
Ключ этого экрана остаётся только в process memory; разрешения default-off.
Поля настроек не отправляют запросов. Настройка не изменяет существующие записи.
Старый одноразовый путь ожидает валидированный JSON AnalysisResult; у него
сохранён прежний лимит max_tokens=1800. Новый движок не отправляет лимит генерации.

## Новый headless runtime

Он по-прежнему не подключён к экрану. Конфигурация Kotlin API:

```kotlin
ProviderSettings(
    baseUrl = "https://openrouter.ai/api/v1", // без /chat/completions
    model = "nvidia/nemotron-3-ultra-550b-a55b:free",
    contextTokens = 1_000_000,
)
```

Ключ передаётся отдельно в AgentCredentials; вызов vault.save означает явное
сохранение и не нужен для разового тестирования. Для live eval credentials
передаются только через окружение тестового процесса. Использованы исключительно
искусственные данные, настоящие задачи/расходы/здоровье из приложения не отправлялись.

## Исправления совместимости

- AgentMessage/ModelReply сохраняют provider reasoning_details без изменения
  порядка и opaque полей и передают их обратно с assistant/tool-call сообщением.
  При отсутствии details поддерживается обычное поле reasoning. Метаданные не
  становятся текстом пользовательского ответа; они сохраняются в private checkpoint.
  Старые checkpoint без новых optional fields читаются без миграции. Оценка
  контекста учитывает дополнительные поля.
- Только AI clients используют request/socket timeout 120 секунд (connect — 15).
  Общий Core client остаётся с 30 секундами. Для runLoop сохраняется общий бюджет
  180 секунд; отмена coroutine работает. Автоматических повторов POST нет.
- Добавлены regression tests полного HTTP tool-call round trip + disk checkpoint,
  legacy/plain-reasoning совместимости, замены inherited timeout и отмены запроса.
- Добавлен opt-in LiveAiProviderTest для реального пути текущего экрана.

## Первичная проверка

Прямой API probe: модель найдена; ответ API_OK получен за 6,07 секунды,
usage — 34 prompt / 46 completion / 80 total tokens, reported cost = 0.
Это один конкретный вызов, не обещание постоянной задержки или лимитов провайдера.

До исправления общего таймаута LiveAiProviderTest завершился ошибкой связи
на 30,105 секунды. После выделенной AI timeout policy повторный реальный запрос
прошёл за 6,48 секунды и вернул одну валидную рекомендацию. Это не сравнительный
benchmark: загрузка бесплатного провайдера меняется.

Исходный движок также прошёл оба live workflow: точный BigInt отчёт по валютам
и сохранение/восстановление/исправление подтверждённого предпочтения. Для отчёта
потребовалось 9 model calls и 8 tools; сумма RUB > 2^53 не потеряла точность.
Повторная проверка после изменений фиксируется ниже отдельно.

## Команды без ключа в файлах и истории команд

Окружение тестового процесса: LIFETY_LLM_API_KEY, LIFETY_LLM_BASE_URL,
LIFETY_LLM_MODEL. Затем:

```sh
./gradlew --no-daemon :shared:testAndroidHostTest --tests '*LiveAgentEvalTest' --rerun-tasks
./gradlew --no-daemon :shared:testAndroidHostTest --tests '*LiveAiProviderTest' --rerun-tasks
```

Без этих переменных live tests пропускаются; обычные HTTP fixtures не вызывают
внешний API. Передавать ключ через защищённый ввод окружения своего терминала,
не добавлять его к URL, исходникам, отчётам или Gradle properties.

На физическом Android и iOS runtime запросы пока не проверены. Android Keystore
нового ядра и его UI интеграция остаются отдельной проверкой.

Официальные источники:
- [Аутентификация и endpoint OpenRouter](https://openrouter.ai/docs/api_reference/authentication)
- [Карточка модели](https://openrouter.ai/nvidia/nemotron-3-ultra-550b-a55b:free)
- [Сохранение provider reasoning между вызовами инструментов](https://openrouter.ai/docs/guides/best-practices/reasoning-tokens#preserving-reasoning)

## Итог повторной проверки

После сохранения reasoning metadata оба LiveAgentEvalTest прошли заново:
2 tests, 0 failures/errors/skipped, 150,886 секунды на два сценария.
Exact-money workflow: IDLE, 9 model calls, 8 tool calls; память: recall/correction passed.
LiveAiProviderTest текущего экрана: 1 test passed, 6,48 секунды.

Финальная полная команда без credentials:

```sh
./gradlew :androidApp:build :shared:testAndroidHostTest \
  :shared:compileKotlinIosSimulatorArm64 :shared:compileCommonMainKotlinMetadata \
  --console=plain --stacktrace
```

BUILD SUCCESSFUL (44 секунды): Android debug/release + lint (0 ошибок,
21 прежнее предупреждение), iOS Simulator arm64 и common metadata compile.
125 host tests: 122 прошли, 3 live tests пропущены без credentials в этом прогоне;
все три отдельно успешно выполнены с реальным API, как указано выше.
Сессия тестового процесса с credentials закрыта. Ключ не включён в Git/APK/отчёты.
