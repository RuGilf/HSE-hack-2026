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
            ZStack {
                // Fill the host's safe areas as well as the Compose content.
                Color("LaunchBackground").ignoresSafeArea()
                LifetyRoot().ignoresSafeArea()
            }
            .preferredColorScheme(.dark)
        }
    }
}

private struct LifetyRoot: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController()
        controller.view.backgroundColor = UIColor(named: "LaunchBackground")
        return controller
    }
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
