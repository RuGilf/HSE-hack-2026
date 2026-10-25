# Android UI polish — 25 октября 2026

База: объединённый main `2518b8a`, разработка в `feature/core`.
Тёмная палитра и Lifety сохранены; публичные модели, repository interfaces,
SQL-схема, Gradle, Manifest, permissions и AI transport не менялись.

## Изменения

- Стандартная M3 Typography и более компактные заголовки; фоновые роли Material
  разделены, selected/tonal компоненты используют тёмные контейнеры.
- Пять Native NavigationBar sections, Material shapes/FAB, ListItem, overflow menus.
  На Главной карточки ведут в разделы, задачи — в редактор. Даты показываются по-русски.
- Удалены повторные hero headings и длинные вступления. API-настройки, импорт
  уведомлений и кастомный диапазон дат раскрываются по действию. Pending imports
  и точный AI preview остаются доступны независимо от раскрытых настроек.
- Motion specs 120/180/240 ms, короткие fade-переходы разделов/Плана,
  animateItem для вставки/удаления/перестановки, цвет completion, animated progress.
- Bottom sheets закрываются после успешной записи с штатной анимацией;
  повторная запись и закрытие во время записи блокируются.
- Черновики нового и существующего расхода сохраняются при rotation/config change;
  новый id также сохраняется, повторный save не создаёт новый id после rotation.
  Ручной Health сохраняет поля, использует IME Next/Done и Snackbar.
- Маскот читает animation state только в graphicsLayer, без recomposition и
  перерисовки Canvas на каждом кадре. Insets для видимости нижней панели читаются
  через derivedStateOf: не пересобирают корень на каждом кадре движения IME.
- Кнопки/даты M3 сохраняют обычные touch targets; календарь не выводит лишнюю
  шестую неделю, когда месяц укладывается в пять. Выбранный день имеет semantics.

## Проверки

JDK 21, Android SDK 37, macOS arm64:

```sh
./gradlew :androidApp:build :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 --console=plain --stacktrace
```

BUILD SUCCESSFUL: debug/release, lint (0 errors, 21 existing warnings),
71 host test (0 failures/errors/skipped), iOS Simulator arm64 Kotlin compilation.
Проверены существующие тесты persistence/Flow, Planner, Finance/Notifications,
Dashboard/Health, приватность AI и HTTP fixtures.

На изолированном AVD Android 15 API 35 arm64 проверены:

1. Dashboard → редактирование задачи с IME → сохранение; completion и фильтры,
   переходы Задачи/Календарь и быстрые повторные переключения всех пяти разделов.
2. Новый расход 129,90 RUB: title/amount сохранены после landscape/portrait;
   применение подсказки категории, запись и закрытие формы.
3. Существующий расход изменён до 139,90 RUB, снова повернут, сохранён с прежним
   id; удалён через overflow с подтверждением. После остановки процесса и запуска
   исходный итог 925,40 RUB восстановлен. Нет production seed данных.
4. Viewport 320 dp, font scale 1.3: ручные сон/минуты/шаги и сохранение доступны,
   клавиатура и прокрутка формы работают. Кнопка сохранения задачи остаётся
   видимой при открытой IME. Подписи нижней панели на крайней ширине сокращаются
   ellipsis; accessibility text сохраняет полные названия.
5. AI preview с разрешёнными только задачами; ключ не вводился, запросов в API нет.
   Настройки скрыты по умолчанию; просмотр JSON остаётся перед явной отправкой.
6. Animator duration scale 0: вкладки и закрытие формы работают. После теста
   настройки отдельного AVD восстановлены. Crash buffer пуст.

## Плавность и ограничения измерения

Запись экрана + SwiftShader не является честным benchmark: программный GPU
дал 154 janky frames из 411 (37,47%). Поэтому повторён отдельный прогретый прогон
без записи, с `-gpu host`: Apple M5 / Metal, Android emulator, debug APK,
15 переключений через пять разделов с интервалом 400 ms.

`dumpsys gfxinfo`: 389 rendered frames, 4 janky frames (1,03%), p50 17 ms,
p90 21 ms, p95 22 ms. Это не прямое сравнение до/после и не гарантия 60 FPS
на реальном телефоне. Видео для просмотра повторно записано с host GPU.

Не проверены физический Android, API 24–25 runtime, iOS linking/runtime,
банковский push/доступ к notifications, реальный AI-запрос с ключом.
Release APK unsigned. Macrobenchmark/Perfetto на физическом устройстве не запускались.
