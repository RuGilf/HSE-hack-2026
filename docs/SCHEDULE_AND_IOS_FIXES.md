# Расписание и исправления iOS — 30 октября 2026

## Что добавлено

План → Расписание хранит фиксированные занятия отдельно от задач. Можно задать
дни недели или одну дату, название, начало и окончание. Редактирование серии
меняет все её повторения; удаление требует подтверждения. End <= start означает
следующие сутки, одинаковые часы — целые сутки. Часы следуют местному времени
устройства. Для поездок отдельный закреплённый timezone пока не предусмотрен.
Исключения отдельных повторений, импорт учебного файла и синхронизация с внешними
календарями не реализованы.

В План → День занятия и задачи стоят на одной шкале, в соседних колонках.
Пересечения задач помечены, занятые интервалы объединяются без двойного подсчёта;
свободные окна учитывают и задачи с оценкой, и расписание. Задачи без оценки
сохраняют неизвестную длительность: в сетке лишь короткий маркер, в расчёте
занятости их длительность не выдумывается. Сетку можно свернуть; записи за
границами выбранных часов остаются в обычном списке. Касание записи открывает
редактор, пустого часа в колонке задач — создание с этим временем.
Drag-and-drop переноса блоков пока нет: время меняется в редакторе.

Календарь отмечает занятия и открывает тот же редактор. ChatGPT получает
fixedSchedule при включённых задачах. Изменение занятия делает предыдущий
снимок устаревшим. Импорт повторно проверяет пересечения с фиксированными
занятиями перед транзакционным сохранением. API-агент получает расписание
только при доступе к задачам, как read-only файлы.

Решение опирается на совместный показ событий и задач в
[Todoist Calendar layout](https://www.todoist.com/help/todoist/features/use-the-calendar-layout-in-todoist-lPHRQTu0o)
и явную [длительность задач](https://www.todoist.com/help/todoist/features/set-a-task-duration-L1kYkZv8d).
Синхронизация аккаунтов Todoist не используется.

## Исправления и предыдущие запросы

- Название задачи владеет локальным TextFieldValue, включая selection/composition.
  StateFlow больше не перезаписывает позицию курсора устаревшим текстом.
- У расхода отдельное поле «Что купили · необязательно» до 2000 символов.
  Оно сохраняется в SQLite, возвращается в редактор и показывается в истории;
  изменение расхода старым API-инструментом не стирает заметку.
- Вход в защищённый раздел запускается после шестой цифры. В unlock передаётся
  актуальное шестизначное значение. Проверки кода, ограничения попыток и защита
  при уходе в фон сохранены; подтверждение нового кода в настройках остаётся.
- Настройки → Нижняя панель → Дуга: опциональная SwiftUI navigation с настоящим
  Liquid Glass, SF Symbols, удержанием и доступным альтернативным тапом.
  Состав/порядок прежних 1–5 разделов сохраняется. Панель UITabBar по умолчанию.
  Открытие/закрытие использует spring, Reduce Motion отключает анимацию.
- iOS host и native controls обновляют цвет по теме. UISwitch измеряет собственный
  размер в layout, конец переключателя не обрезается. Поля iOS мягкие заполненные,
  Android сохраняет Material outline. Убран clickable wrapper маркера панели:
  тап не закрывает, короткий свайп возвращает, достаточный свайп закрывает.

SwiftUI внедрён через
[официальную Compose interop-схему](https://kotlinlang.org/docs/multiplatform/compose-swiftui-integration.html),
[системное стекло](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views)
использует доступный в текущем deployment target API. Остальные формы остаются
Compose с UIKit controls, полной переписи приложения нет.

## Данные и файлы

Schema 8: `6.sqm` сохраняет данные 6→7 и добавляет expense.note / вид панели;
`7.sqm` добавляет scheduleBlock, без изменений прежних записей. Модель ScheduleBlock,
ScheduleRepository и optional Repositories.schedule — аддитивные контракты.
Старые Expense-конструкторы/JSON поддерживаются значением note="".
Точные пакеты и сигнатуры находятся в [CONTRACTS.md](CONTRACTS.md).

Основные файлы:

- core/model/ScheduleBlock.kt, core/repository/ScheduleRepository.kt,
  core/planner/Schedule.kt, DayTimeline.kt, core/planning/PlanExchange.kt;
- database/SqlDelightScheduleRepository.kt, Lifety.sq, миграции 6.sqm/7.sqm;
- feature/schedule/ScheduleScreen.kt, feature/tasks/DayTimeGrid.kt,
  TasksScreen.kt/DayTimelineContent.kt, feature/calendar/CalendarScreen.kt;
- TaskEditorScreen.kt, FinanceDialogs.kt/FinanceScreen.kt, SectionLockUi.kt;
- core/ui/LifetyTextField.kt, LifetyBottomSheet.kt, SheetSwipe.kt,
  NativeSurfaceColor.kt, native switch/segmented/time/birthday bindings;
- iosApp/Lifety/LifetyApp.swift, PlatformNavigationBar.ios.kt,
  core/platform/IosArcNavigation.kt, appearance/IosThemeColors.kt;
- AppContainer/PlanningProvider, PlanTransferController/ChatGptContext и
  read-only экспорт fixedSchedule встроенного агента.

## Проверки

Без LLM-ключей, JDK 21 / Android SDK как в README:

```sh
env -u LIFETY_LLM_API_KEY -u LIFETY_LLM_BASE_URL -u LIFETY_LLM_MODEL \
  JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/Users/rugilf/Library/Android/sdk \
  ./gradlew :androidApp:build :shared:testAndroidHostTest :shared:iosSimulatorArm64Test
```

Успешно: Android debug/release build и lint; host 267 tests, 0 failures/errors,
6 live LLM tests пропущены без ключей (выполнен 261); iOS 136 tests,
0 failures/errors/skips. Лог `/tmp/lifety-schedule-validation.log`.

Тесты охватывают репозитории после reopen, Flow при upsert/delete, миграции
6→8 и 7→8 без потери заметок/вида панели, weekly/one-off/overnight,
DST boundaries, половинно открытые пересечения и объединение занятости,
неизвестную длительность, fingerprint, включение/исключение расписания из
запроса и отказ импорта плана в занятые часы. Общие тесты жеста проверяют
порог, скорость, отмену и некорректную геометрию.

```sh
xcodebuild -project iosApp/Lifety.xcodeproj -scheme Lifety \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /Users/rugilf/Documents/Codex/2026-10-25/e/work/day-plan-ios build
```

Xcode build успешен (`/tmp/lifety-schedule-xcode-final.log`), приложение установлено
и запущено на отдельном Lifety-Agent-UI-QA, iPhone 17 / iOS 26.5. Данные основного
пользовательского симулятора не изменялись. `git diff --check` проходит.

Вручную через системную клавиатуру введены и сохранены задача «тест дело»,
расход 125 RUB с магазином «тест» и заметкой «хлеб», занятие «пара» 09:00–10:00
по будням. Подтверждены правильный порядок текста, сохранение задач/расхода
после обновления приложения, занятие в сетке дня и календаре, переход к редактору.
Проверены Lifety Light/Tokyo Night, вход в настройки, маркер (тап/короткий свайп
не закрывает, длинный закрывает), дуга (тап/удержание, переходы), возврат к
обычному UITabBar и восстановление темы/дуги при перезапуске.

Последние небольшие правки семи кнопок дней недели, создания задачи по касанию
сетки и символа отметки календаря покрыты сборкой; полный повторный ручной прогон
после них не заявляется. При завершении UI-прогона Device Hub закрылся,
список booted simulators пуст. Заметка введена и сохранена, но её строка ниже
сгиба финансового списка отдельно не была визуально подтверждена.

Не проверены физический iPhone, iPad, VoiceOver/крупный Dynamic Type, все варианты
1–5 пунктов дуги, Reduce Motion вручную, живой обмен с ChatGPT/API, ручной PIN unlock
(код пользователя не создавался/не менялся), ручные удаление/редактирование
расписания и разовая дата. Персистентность/валидация этих операций проверены тестами.
