import SwiftUI
import Shared

@main
struct KmmBaseApp: App {
    private let container = AppContainer()
    var body: some Scene {
        WindowGroup { RootView(container: container) }
    }
}
