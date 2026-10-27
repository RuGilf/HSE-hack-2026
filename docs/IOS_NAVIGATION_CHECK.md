# Проверка нижней панели iOS

26 октября 2026, main после обновления дневного плана.

SwiftUI `LifetyApp.swift` размещает общий Compose UIViewController. Сама нижняя
панель — системный UIKit `UITabBar`, встроенный через `UIKitView` в
`shared/src/iosMain/kotlin/ru/lifety/app/PlatformNavigationBar.ios.kt`.
Маршруты и состояние экранов остаются в общей Compose Navigation. Отдельного
SwiftUI TabView и второй независимой системы маршрутов нет.

26 октября фон UITabBar был приведён к фону Compose, но эта правка цвета
не обеспечивала прозрачность native view. Окончательное исправление режима
встраивания описано в разделе от 27 октября ниже.

Дополнительно фон SwiftUI host растянут до краёв через `ZStack` и
`Color("LaunchBackground").ignoresSafeArea()`. View встроенного Compose controller
получает тот же цвет из существующего asset. Таким образом, область под меню
и вокруг системного home indicator не зависит от чёрного фона контейнера.
После этой правки повторены iPhone portrait/landscape, редактор с экранной
клавиатурой, Xcode build и Android build/host tests. Нативная капсула меню сохранена.

## Ручная проверка

Использованы только отдельные QA simulators с локальными тестовыми данными:

- iPhone 17 / iOS 26.5: портрет и горизонтальная ориентация, поворот обратно;
  пять вкладок, подписи и подсветка соответствуют открытому разделу.
- iPhone: выбор дня в Плане сохраняется после перехода в другие разделы.
- iPhone: открытие задачи с Главной скрывает панель; при экранной клавиатуре
  кнопка сохранения редактора видна; закрытие возвращает Главную и её подсветку.
- iPad mini A17 Pro / iPadOS 26.5: портрет и горизонтальная ориентация;
  панель остаётся по центру снизу, все пять подписей помещаются. Проверены
  переключение План → Финансы, открытие и закрытие формы расхода. Панель не
  рисуется поверх формы и возвращается с выбранными Финансами после закрытия.
- Обновлённая сборка установлена и запущена на обоих симуляторах; записи iPhone
  сохранились после обновления. Никакие разрешения HealthKit/AI не запрашивались.

В проверенных сценариях перекрытия форм панелью и потери выбранного раздела
не обнаружены. Это проверка общего поведения и отображения; физический iPhone,
VoiceOver, максимальный Dynamic Type и изменение размера окна iPad не проверены.

## Сборка и тесты

```sh
xcodebuild -project iosApp/Lifety.xcodeproj -scheme Lifety \
  -configuration Debug -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=Lifety-iOS-QA' build
./gradlew :androidApp:build :shared:testAndroidHostTest :shared:iosSimulatorArm64Test
```

Xcode build и Android debug/release/lint успешны. Host tests: 155 passed,
6 live LLM tests skipped без credentials. Kotlin iOS tests: 61 passed.
Новые тесты для изменения цвета не добавлялись; визуальная правка проверена
в интерфейсе на обоих устройствах.

## 27 октября: прозрачный overlay

UITabBar теперь использует `UIKitInteropProperties(placedAsOverlay = true)`:
Compose не вырезает прямоугольник под UIKit. Фон UIView прозрачный, фоновой Box
под ним нет. Панель вынесена из bottomBar слота Scaffold и накладывается на
полное содержимое. Последний элемент каждого списка, FAB и поле чата учитывают
`LocalBottomNavigationInset`, поэтому их можно прокрутить/разместить выше меню.
Android сохраняет прежний Material NavigationBar с нулевым дополнительным inset.

В обновлённом iPhone 17 QA simulator проверены Finance portrait/landscape,
содержимое под системным стеклом без прямоугольной отсечки, пять разделов,
Plan FAB, Health, composer Assistant и формы поверх панели. Xcode build,
Android build/lint, 163 host tests и 65 native Kotlin iOS tests успешны;
6 live LLM tests пропущены без ключей. iPad и физический телефон в этой
итерации не перепроверялись. Детали категорий — [EXPENSE_CATEGORIES.md](EXPENSE_CATEGORIES.md).
