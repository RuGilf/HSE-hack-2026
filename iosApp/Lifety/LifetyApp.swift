import SwiftUI
import LifetyShared

@main
struct LifetyIosApp: App {
    @AppStorage("lifety.appearance.theme") private var themeId = "lifety"
    init() {
        IosServices.shared.arcNavigation = NativeArcNavigationFactory()
        IosServices.shared.credentials = KeychainStore()
        IosServices.shared.health = AppleHealthSource()
        IosServices.shared.sectionLocks = SectionLockStorage()
        IosServices.shared.healthMetrics = AppleMetricSource()
    }
    private var background: UIColor { IosThemeColorsKt.iosThemeBackground(themeId: themeId) }
    var body: some Scene {
        WindowGroup {
            ZStack {
                // Fill the host's safe areas as well as the Compose content.
                Color(uiColor: background).ignoresSafeArea()
                LifetyRoot(background: background).ignoresSafeArea()
            }
            .preferredColorScheme(LifetyPalette.companion.fromId(id: themeId).palette.isDark ? .dark : .light)
        }
    }
}

private struct LifetyRoot: UIViewControllerRepresentable {
    var background: UIColor
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController()
        controller.view.backgroundColor = background
        return controller
    }
    func updateUIViewController(_ controller: UIViewController, context: Context) {
        controller.view.backgroundColor = background
    }
}

// Native SwiftUI navigation, backed by the same KMP routes and section guards.
private final class NativeArcNavigationFactory: IosArcNavigationFactory {
    func create(events: IosArcNavigationEvents) -> IosArcNavigationSession {
        MainActor.assumeIsolated { NativeArcNavigationSession(events: events) }
    }
}

@MainActor
private final class NativeArcNavigationSession: NSObject, @preconcurrency IosArcNavigationSession {
    private let model: ArcNavigationModel
    private let host: UIHostingController<ArcNavigationView>
    var viewController: UIViewController { host }

    init(events: IosArcNavigationEvents) {
        let model = ArcNavigationModel(events: events)
        self.model = model
        self.host = UIHostingController(rootView: ArcNavigationView(model: model))
        super.init()
        host.view.backgroundColor = .clear
        host.view.isOpaque = false
    }
    func update(items: [IosArcItem], selected: String, accent: Int64, text: Int64, dark: Bool, safeBottom: Double) {
        if model.items.map(\.route) != items.map(\.route) { model.items = items }
        if model.selected != selected { model.selected = selected; model.setExpanded(false) }
        if model.accentValue != accent { model.accentValue = accent; model.accent = Color(uiColor: arcColor(accent)) }
        if model.textValue != text { model.textValue = text; model.text = Color(uiColor: arcColor(text)) }
        if model.safeBottom != safeBottom { model.safeBottom = safeBottom }
        let style: UIUserInterfaceStyle = dark ? .dark : .light
        if host.overrideUserInterfaceStyle != style { host.overrideUserInterfaceStyle = style }
    }
    func close() { model.events = nil }
}

private func arcColor(_ argb: Int64) -> UIColor {
    UIColor(red: CGFloat((argb >> 16) & 255) / 255, green: CGFloat((argb >> 8) & 255) / 255,
            blue: CGFloat(argb & 255) / 255, alpha: CGFloat((argb >> 24) & 255) / 255)
}

@MainActor
private final class ArcNavigationModel: ObservableObject {
    @Published var items: [IosArcItem] = []
    @Published var selected = "dashboard"
    @Published var expanded = false
    @Published var accent: Color = .accentColor
    @Published var text: Color = .primary
    @Published var safeBottom = 0.0
    var accentValue: Int64 = .min
    var textValue: Int64 = .min
    var events: IosArcNavigationEvents?
    init(events: IosArcNavigationEvents) { self.events = events }
    func setExpanded(_ expanded: Bool) {
        guard self.expanded != expanded else { return }
        events?.onExpanded(expanded: expanded)
        let animation: Animation? = UIAccessibility.isReduceMotionEnabled ? nil : .spring(response: 0.3, dampingFraction: 0.82)
        withAnimation(animation) { self.expanded = expanded }
    }
    func select(_ route: String) { setExpanded(false); events?.onSelect(route: route) }
}

private struct ArcNavigationView: View {
    @ObservedObject var model: ArcNavigationModel
    var body: some View {
        GeometryReader { geometry in
            let center = CGPoint(x: geometry.size.width / 2, y: geometry.size.height - model.safeBottom - 36)
            ZStack {
                if model.expanded {
                    Color.clear.contentShape(Rectangle()).onTapGesture { model.setExpanded(false) }
                }
                GlassEffectContainer(spacing: 12) {
                    ZStack {
                        if model.expanded {
                        ForEach(Array(model.items.enumerated()), id: \.element.route) { index, item in
                            let angle = model.items.count == 1 ? -Double.pi / 2 : (-160 + 140 * Double(index) / Double(model.items.count - 1)) * .pi / 180
                            let target = CGPoint(x: center.x + cos(angle) * 116, y: center.y + sin(angle) * 116)
                            Button { model.select(item.route) } label: {
                                VStack(spacing: 4) {
                                    Image(systemName: item.symbol).font(.system(size: 21, weight: .medium))
                                    Text(item.title).font(.system(size: 10, weight: .medium)).lineLimit(1).minimumScaleFactor(0.75)
                                }
                                .foregroundStyle(item.route == model.selected ? model.accent : model.text)
                                .frame(width: 66, height: 66)
                            }
                            .buttonStyle(.plain)
                            .glassEffect(.regular.interactive(), in: .circle)
                            .accessibilityLabel(item.title)
                            .accessibilityAddTraits(item.route == model.selected ? .isSelected : [])
                            .position(target)
                            .transition(.offset(x: center.x - target.x, y: center.y - target.y).combined(with: .opacity))
                        }
                        }
                        Button { model.setExpanded(!model.expanded) } label: {
                            Image(systemName: model.expanded ? "xmark" : (model.items.first { $0.route == model.selected }?.symbol ?? "square.grid.2x2"))
                                .font(.system(size: 23, weight: .medium)).foregroundStyle(model.accent)
                                .frame(width: 88, height: 50)
                        }
                        .buttonStyle(.plain)
                        .glassEffect(.regular.interactive(), in: .capsule)
                        .highPriorityGesture(LongPressGesture(minimumDuration: 0.25).onEnded { _ in model.setExpanded(true) })
                        .accessibilityLabel(model.expanded ? "Закрыть меню разделов" : "Открыть меню разделов")
                        .accessibilityHint("Удерживайте для открытия дугового меню")
                        .position(center)
                    }
                }
            }
            .accessibilityAction(.escape) { model.setExpanded(false) }
        }
        .ignoresSafeArea()
    }
}
