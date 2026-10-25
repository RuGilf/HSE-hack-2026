# Lifety — Neon design system v1

Design update: 25 октября 2026. Общий контракт визуального языка всех функций.
[Исходный концепт пользователя](design/lifety-reference.png). Это референс, а не
список обязательных новых функций и не источник реальных пользовательских данных.

## Палитра

| Роль | HEX | Compose token |
| --- | --- | --- |
| Основной фон | `#101512` | `LifetyColors.Background` |
| Карточки | `#1D2520` | `LifetyColors.Surface` |
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
Android system bars, launcher mark и системный splash используют ту же палитру.

Внутри функций используйте `MaterialTheme.colorScheme`, `shapes`, `typography`.
`LifetyColors` предназначен для общих компонентов/brand drawing; не копируйте HEX
в каждый feature. Не оборачивайте экран дополнительным MaterialTheme с default
цветами — корень уже предоставляет тему всем пяти разделам.

## Размеры и типографика

`LifetySpacing`: Screen 20 dp, Section 24 dp, Content 16 dp, Item 12 dp, Small 8 dp.
Высота основного действия — 52 dp; круглая FAB — стандартная M3 56 dp.
Card — радиус 20–24 dp, input — 16 dp, sheet — 28 dp сверху.

Шрифт — системный Sans Serif. H1 — 30/36 sp semibold, H2 — 26/32,
app bar — 24/30, title — 20/26 или 16/22, основной текст — 14/20 или 16/23,
подписи — 12/18; подписи NavigationBar — 10/14. Размеры заданы в LifetyTheme.
Сохраняйте системное масштабирование текста. Важные действия имеют текст или
contentDescription; цвет не является единственным признаком completion/error.

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
Анимация — дыхание/плавание на 3 dp, цикл 2,6 с, без вспышек.
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

## Область текущего обновления

Применены общий theme, NavigationBar, Planner tabs, настройки входа, Tasks/Calendar,
Finance UI, editor sheets и общие placeholders. Бизнес-код Finance (суммы,
репозитории, источники, подтверждение уведомления), общие модели, схема и DI
сохранены. Существующие данные остаются на месте.

Dashboard, AI и Health не реализуются заново ради картинки. Агент 3 получает
готовые tokens/components и прежние screen/repository/navigation interfaces.
Приоритеты, длительность, повторения, пульс и другие отсутствующие поля из
концепта не добавлены в модель и не показаны как работающие функции.
