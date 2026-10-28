# Темы и иконки Lifety

Настройки открываются шестерёнкой в верхней панели любого раздела.
Выберите карточку темы — интерфейс меняется сразу. Выбор хранится на устройстве,
перезапуск не нужен. Переход к финансам/банковским уведомлениям из настроек удалён;
все финансовые функции остаются в своей вкладке.

Доступны Lifety (оригинал), Tokyo Night (Night), Catppuccin (Mocha), Gruvbox (Dark)
и Nord. Это адаптации палитр, не плагины Neovim. Сохраняются размеры, типографика
и поведение интерфейса. Фон, поверхности, поля, кнопки, маскот и нативная нижняя
панель используют общую выбранную тему. Цвета пользовательских категорий трат
сохраняются независимо. Все варианты тёмные.

## Иконка

Под темами находится предпросмотр и кнопка «Применить иконку …».
Смена темы и смена значка — отдельные действия: выбор палитры не вызывает системных
окон и сам по себе не меняет домашний экран. Можно оставить исходную иконку или
вернуть её кнопкой «Вернуть стандартную иконку».

- iOS: настоящий `UIApplication.setAlternateIconName`, четыре alternate app icon
  sets в Assets.xcassets; Xcode добавляет `CFBundleAlternateIcons` через
  `ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES`. `nil` возвращает основной значок.
  iOS может показать штатное уведомление. Текущее состояние читается из системы,
  ошибка не выдаётся за успешную смену. Документация:
  [Apple — alternate icons](https://developer.apple.com/documentation/xcode/configuring-your-app-to-use-alternate-app-icons).
- Android: пять launcher activity-alias, MainActivity остаётся включённой.
  Android 13+ переключает компоненты одним пакетным вызовом; на API 24–32 сначала
  включается выбранный значок, затем отключаются остальные. Используется
  `DONT_KILL_APP`; лаунчер может обновить значок не сразу. При ошибке выполняется
  попытка восстановить прежние компоненты. После обновления со старой версии
  закреплённый ярлык MainActivity может потребовать повторного добавления из списка
  приложений. Документация:
  [PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager#setComponentEnabledSettings(java.util.List)).

Иконки — исходный геометрический знак Lifety, окрашенный в фон/акцент темы;
чужие логотипы не используются. `scripts/generate-themed-icons.swift` строит
четыре PNG (1024×1024 RGB, без прозрачности) и Android vector drawables из
`LifetyPalette.kt`. Основная существующая иконка сохраняется.

## Контракты

- `core/appearance/LifetyPalette.kt`: стабильные IDs `lifety`, `tokyo-night`,
  `catppuccin`, `gruvbox`, `nord`; базовые палитры для интерфейса и значков.
- `core/appearance/AppearanceController.kt`: StateFlow, независимые операции
  сохранения темы/смены иконки, защита от повторных запросов и отображение ошибок.
- `AppearancePlatform.android.kt` / `.ios.kt`: SharedPreferences / NSUserDefaults
  для темы, нативный источник фактической иконки. Новых разрешений и БД нет.
- `LifetyTheme` читает сохранённый выбор и предоставляет `LocalAppearance`;
  bootstrap/loading получает ту же тему. Публичная сигнатура темы не меняется.
- UI функций продолжает использовать `MaterialTheme.colorScheme`. Роли для
  небольшого текста адаптируются к контрасту не ниже 4.5:1 на поверхностях;
  базовые editor colors и цвета значков не подменяются копиями из функций.
- Старый аргумент `onFinance` у `LifetySettingsSheet` сохранён для совместимости,
  но больше не используется. Пять основных разделов и бизнес-модели прежние.

## Источники палитр

Числовые палитры адаптированы для Material 3. Это не официальные приложения авторов.
Лицензии сохранены в `docs/licenses/`.

| Тема | Источник | Лицензия |
| --- | --- | --- |
| Tokyo Night | [folke/tokyonight.nvim](https://github.com/folke/tokyonight.nvim/blob/main/lua/tokyonight/colors/storm.lua), [Night](https://github.com/folke/tokyonight.nvim/blob/main/lua/tokyonight/colors/night.lua) | [Apache 2.0](licenses/tokyonight.txt) |
| Catppuccin Mocha | [catppuccin/palette](https://github.com/catppuccin/palette/blob/main/palette.json) | [MIT](licenses/catppuccin.txt) |
| Gruvbox Dark | [ellisonleao/gruvbox.nvim](https://github.com/ellisonleao/gruvbox.nvim/blob/main/lua/gruvbox.lua) | [MIT](licenses/gruvbox.txt) |
| Nord | [Nord colors](https://www.nordtheme.com/docs/colors-and-palettes/) | [MIT](licenses/nord.txt) |

## Проверки

- Android `:androidApp:build` (debug/release, lint) успешно; после последней правки
  цвета заголовка дополнительно проверен `:androidApp:assembleDebug`.
- `:shared:testAndroidHostTest`: 203 прошли, 6 live LLM пропущены без credentials.
- `:shared:iosSimulatorArm64Test`: 97 прошли. Новые тесты проверяют восстановление
  выбора, отказ записи, отказ смены иконки, повторные запросы, возврат исходной
  иконки и контраст основных текстовых ролей всех пяти палитр.
- Xcode Debug / iOS Simulator arm64: сборка успешна. В собранном Info.plist
  проверены четыре alternate icons для iPhone и iPad.
- На iPhone 17 / iOS 26.5 Simulator: выбран Catppuccin, изменились главная,
  маскот и native tab tint; после завершения процесса тема восстановилась.
  Иконка Catppuccin появилась на Home Screen; возврат основной иконки прошёл
  через штатное уведомление iOS. В настройках нет пункта финансов.

Не проверены физический iPhone, интерфейс на iPad и смена значка в Android-лаунчерах.
Автоматические тесты контроллера не заменяют проверку поведения конкретного лаунчера.
Системный splash остаётся исходным; тёмная тема применяется при старте Compose.
