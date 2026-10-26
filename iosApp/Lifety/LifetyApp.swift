import SwiftUI
import LifetyShared

@main
struct LifetyIosApp: App {
    init() {
        IosServices.shared.credentials = KeychainStore()
        IosServices.shared.health = AppleHealthSource()
    }
    var body: some Scene {
        WindowGroup {
            LifetyRoot()
                .ignoresSafeArea()
                .preferredColorScheme(.dark)
        }
    }
}

private struct LifetyRoot: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
