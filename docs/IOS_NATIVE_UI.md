# Нативные элементы iOS — 30 октября 2026

## Изменения

Общая логика остаётся в Kotlin/Compose. Верхняя панель iOS — настоящий
UINavigationBar с центральным заголовком, системными SF Symbols и входами
во все разделы/настройки. Прозрачная нижняя панель UITabBar сохранена.

План, фильтры задач, периоды финансов/здоровья и вкладки настроек контекста
используют UISegmentedControl. Дата в финансах, здоровье, целях и дне плана —
компактный UIDatePicker. Дедлайн и расписание задачи редактируются через
dateAndTime picker прямо в группе полей; null задаётся через «Указать»/«Убрать».
Время окончания — native time-only picker с прежним расчётом durationUntil.
Все действия редактора меняют черновик, база записывается только по Save.

Switches задач, целей и настроек встроенного агента используют UISwitch.
Размер берётся из intrinsicContentSize: Apple изменила размеры controls в iOS 26,
поэтому прежний фиксированный размер 51×31 обрезал правый край переключателя.
Повторное присваивание уже выбранного значения пропускается, чтобы не прерывать
нативную анимацию. Типографика iOS отделена от Android, палитры остаются общими.

UIKit controls внутри форм находятся под Metal canvas, чтобы учитывать clipping
прокрутки и footer. Cooperative interaction позволяет начать прокрутку на control.
Overlay/NonCooperative оставлены только у фиксированных navigation bars.
Новых библиотек, миграций БД, разрешений и сетевых действий нет.

## Даты и время

Дата без времени хранится прежней ISO-строкой. UIKit получает местное полуденное
время этой даты и возвращает civil date в том же часовом поясе. Это сохраняет
правильные дату и «сегодня» на устройствах по обе стороны UTC и избегает переходов
DST в полночь. Android DatePicker продолжает использовать UTC midnight.
Григорианский native calendar начинается с понедельника; язык интерфейса русский.
Формат времени UIDatePicker соответствует настройке iOS 12/24 часа. Задачи
по-прежнему сохраняются как epoch milliseconds и проверяются общим planner.

## Проверки

Финальные команды выполнились успешно, без API-ключей:

```sh
./gradlew :androidApp:build :shared:testAndroidHostTest :shared:iosSimulatorArm64Test

xcodebuild -project iosApp/Lifety.xcodeproj -scheme Lifety \
  -configuration Debug -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,id=0CF2279F-54B7-4BA7-AB07-32DC12D9A155' \
  -derivedDataPath /Users/rugilf/Documents/Codex/2026-10-25/e/work/day-plan-ios build
```

Окружение JDK/SDK — как в README. Android build включает lint и debug/release.
Host: 251 tests, 0 failures/errors, 6 live LLM tests skipped; выполнены 245.
iOS: 125 tests, 0 failures/errors/skips. Новый общий тест проверяет civil dates
в UTC+14, UTC−12, Москве, Берлине и Сан-Паулу, включая DST и 29 февраля.
Логи проверки: `/tmp/lifety-ios-native-final.log`,
`/tmp/lifety-native-final-validation.log`. `git diff --check` прошёл.

На iPhone 17 / iOS 26.5 Simulator вручную проверены старт, верхняя панель,
переход в План, открытие native calendar и выбор дня, открытие настроек кнопкой
gearshape, компактный редактор существующей задачи с native date/time controls,
кнопка сохранения и отмена. Проверки не записывали изменения задач и здоровья.

Последний визуальный прогон после исправления intrinsic размера UISwitch
не завершён: Device Hub перестал отвечать, CoreSimulator сообщил о несовпадении
версии framework и уже запущенной службы. Приложение успешно установлено и
запущено после перезапуска службы; UI-автоматизация окна осталась недоступной.
QA-устройство выключено, список booted simulators пуст. Device Hub оставался
зависшим; подтверждённого закрытия самого окна нет.

Не проверены физический iPhone, iPad, большие accessibility text sizes, VoiceOver,
окончательная ручная прокрутка с native controls и IME, native переключатели
всех модулей, финансовые даты в узких карточках. Это не полный перевод форм
в SwiftUI: формы и диалоги подтверждения пока в Compose.

## Документация платформ

Встраивание native views сделано по [документации Compose/UIKit](https://kotlinlang.org/docs/multiplatform/compose-uikit-integration.html).
Режимы, timezone и события picker описаны у [Apple UIDatePicker](https://developer.apple.com/documentation/uikit/uidatepicker).
Изменение размеров controls и системных анимаций — [Apple WWDC25](https://developer.apple.com/videos/play/wwdc2025/284/),
измерение — [UIView.intrinsicContentSize](https://developer.apple.com/documentation/uikit/uiview/intrinsiccontentsize).
