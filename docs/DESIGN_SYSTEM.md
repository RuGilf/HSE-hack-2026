# Lifety — Android / Material 3 design system v2

Design update: 25 октября 2026. Android polish на базе main `2518b8a`. Общий контракт визуального языка всех функций.
[Исходный концепт пользователя](design/lifety-reference.png). Это референс, а не
список обязательных новых функций и не источник реальных пользовательских данных.

## Палитра

| Роль | HEX | Compose token |
| --- | --- | --- |
| Основной фон | `#101512` | `LifetyColors.Background` |
| Карточки | `#1D2520` | `LifetyColors.Surface` |
| Primary container | `#173C25` | `LifetyColors.PrimaryContainer` |
| Secondary container | `#293C30` | `LifetyColors.SecondaryContainer` |
| Приподнятая поверхность | `#263229` | `LifetyColors.SurfaceRaised` |
| Акцент | `#35EF70` | `LifetyColors.Accent` |
| Основной текст | `#F4F7F3` | `LifetyColors.TextPrimary` |
| Вторичный текст | `#9BAA9F` | `LifetyColors.TextSecondary` |
| Граница | `#34473A` | `LifetyColors.Outline` |
| Ошибка / просрочено | `#FF817D` | `LifetyColors.Error` |

Все значения определены **один раз** в
`shared/src/commonMain/kotlin/ru/lifety/core/ui/LifetyDesign.kt`.
`LifetyTheme.kt` назначает все роли Material 3, включая secondaryContainer и
surface containers: стандартный фиолетовый акцент не должен возвращаться.
Тема фиксированная тёмная; локальную светлую тему экраны не создают.
Primary/secondary containers тёмные: selected chips и tonal controls не превращаются
в большие неоновые плашки. Цвет content каждой роли соответствует её назначению.
Android system bars, launcher mark и системный splash используют ту же палитру.

Внутри функций используйте `MaterialTheme.colorScheme`, `shapes`, `typography`.
`LifetyColors` предназначен для общих компонентов/brand drawing; не копируйте HEX
в каждый feature. Не оборачивайте экран дополнительным MaterialTheme с default
цветами — корень уже предоставляет тему всем пяти разделам.

## Размеры и типографика

`LifetySpacing`: Screen 16 dp, Section 24 dp, Content 16 dp, Item 12 dp, Small 8 dp.
Основное действие — минимум 48 dp; FAB — M3 56 dp с rounded-square shape.
Card — 16–20 dp, inputs — стандартные M3 4 dp, sheet — 28 dp сверху.
Область выбора дня календаря — 48 dp. Минимальные интерактивные размеры M3 не отключаются.

Типографика — стандартная `Typography()` Material 3, системный шрифт.
Top app bar использует titleLarge 22 sp; нижние подписи — labelMedium 12 sp.
Заголовок раздела показывается в одном месте: общей TopAppBar. Внутри экранов —
локальные заголовки групп, без второго hero и длинного вступления перед формой.
Масштабирование шрифта сохраняется; не уменьшать текст под фиксированный контейнер.

Не добавляйте сильный glow к каждому контейнеру. Мягкое свечение — характерная
деталь маскота, остальные поверхности спокойные. Primary Button — неоновый фон,
тёмный текст; вторичные действия — FilledTonalButton/TextButton.

## Компоненты и навигация

- Пять NavigationBar sections: **Главная, План, Финансы, Здоровье, Ассистент**.
- Стабильные route IDs сохранены: `dashboard`, `tasks`, `finance`, `health`,
  `assistant`. Старый `calendar` остаётся совместимым входом в План → Календарь.
- `app/PlanScreen.kt`: SingleChoiceSegmentedButtonRow «Задачи / Календарь» с
  сохранением состояния каждого представления. Calendar не занимает шестой tab.
- Фильтры — FilterChip или SingleChoiceSegmentedButtonRow.
- Добавление/редактирование задач и расходов — **ModalBottomSheet**; даты —
  DatePicker. TaskEditorRoute, PlannerNavigation и repository signatures сохранены.
- `core/ui/LifetyDateField.kt`: `LifetyDateField(label, value, onChange, modifier,
  enabled)`, value — ISO civil date, без смены текущей финансовой модели.
- Finance: диапазон дат и настройка notifications раскрываются по действию;
  неподтверждённые расходы остаются видимыми независимо от раскрытия настроек.
  Строка расхода открывает редактор; действия — в стандартном overflow menu.
- Assistant: ключ/endpoint/модель в раскрывающемся блоке; разрешения и preview
  видимы отдельно. API-ключ не попадает в rememberSaveable. Точный JSON сохраняется
  в предпросмотре перед отправкой; автоматических запросов нет.
- Health: M3 segmented periods 7/30/всё время, Canvas-графики сна/шагов и список
  дней. Выбор дня касанием графика либо обычными кнопками. Нулевые значения и
  пропуски различаются; средние только по известным дням. Ручная форма с датой —
  ModalBottomSheet, IME Next/Done, сохранение черновика и Snackbar после записи.
  Импорт раскрывается отдельно; график проявляется за 240 ms без задержки данных.
- Настройки открываются шестерёнкой в верхней панели. Пока содержат только
  информацию о теме/локальных данных и переход к существующему управлению
  банковскими уведомлениями. Нет фиктивных профилей и неработающих toggles.
- `core/ui/LifetyIcon.kt`: общие vector symbols для нижней панели и действий.
- `core/ui/LifetyEmptyState.kt`: единый empty state с маскотом, текстом и
  необязательным рабочим CTA. Не подставляйте числовые данные из картинки.

## Маскот Lifety

Общий компонент находится в
`shared/src/commonMain/kotlin/ru/lifety/core/ui/LifetyMascot.kt`, пакет
`ru.lifety.core.ui`.

```kotlin
@Composable
fun LifetyMascot(
    modifier: Modifier = Modifier,
    emotion: LifetyEmotion = LifetyEmotion.Happy,
    animated: Boolean = true,
)
```

Состояния: `Happy`, `Calm`, `Focused`, `Celebrating`, `Sleepy`.
Рисунок — code-native Canvas: неоновое кольцо, улыбка и мягкое свечение;
изображение не зависит от растровых assets, Android/iOS используют один код.
Анимация — дыхание/плавание на 3 dp, по 2,6 с в каждом направлении, без вспышек.
`animated=false` — статичная версия для маленького знака и screenshot/preview.

Правила для всех агентов:

1. Один заметный анимированный маскот на экран, без повторения в каждой карточке.
2. Hero/empty state — 120 dp; небольшая подсказка — 56 dp. В header используйте
   маленькую **статичную** версию. Не растягивайте компонент непропорционально.
3. Happy — приветствие и начало; Calm — пустое состояние/пауза; Focused — работа;
   Celebrating — подтверждённый прогресс; Sleepy — визуальный отдых. Эмоция не
   создаёт и не заменяет health/AI данные.
4. Не копируйте drawing/animation в feature. Импортируйте LifetyMascot.
5. Не делайте маскота препятствием для формы или долгой анимацией перед действием.

## Движение и скорость

Единые spec — `core/ui/LifetyMotion.kt`: Quick 120 ms, Standard 180 ms,
Emphasis 240 ms. Не увеличивайте задержки бизнес-действий ради анимации.

- Root sections и вкладки Плана: fade 120/180 ms, без длинного бокового проезда.
- Строки списков: stable keys/contentType и animateItem для вставки/удаления/перестановки.
- Раскрытие дополнительных настроек: fade + expand/shrink от верхнего края, до 180 ms.
- Заполнение progress bars: 240 ms, только визуальная доля; денежные значения остаются Long.
- Формы: стандартные M3 sheet transitions. Сначала успешная запись, затем закрытие;
  редактор не исчезает до завершения exit animation и не разрешает повторную запись.
- Системный Animator duration scale учитывается Compose. Нет искусственных задержек,
  spring-анимаций каждого контейнера, постоянного glow всего интерфейса и обязательных intro.
- LifetyMascot читает анимацию в graphicsLayer: движение не пересобирает Compose-дерево
  и не перерисовывает Canvas на каждом кадре. Статичная версия остаётся в header.
- Счётчики/сводки мемоизированы по данным, SQLite операции — на IO. Списки lazy.
- У финансового редактора и ручного Health сохраняются поля при configuration change.
  Ключ AI всегда только в process memory, не в saved state.

## Область текущего обновления

Обновлены общая тема, навигация/Plan tabs, настройки, Tasks/Calendar,
Dashboard, Finance/Notifications UI, manual Health и Assistant UI. Repository/API
контракты, SQL-схема и AI transport/permissions не менялись; данные сохраняются.
В Dashboard карточки ведут в разделы, строки задач — в существующий planner editor.

Новых бизнес-функций, серверов, моделей, приоритетов и вымышленных данных ради
референса не добавлялось. Базовые цвета и маскот Lifety сохранены.
