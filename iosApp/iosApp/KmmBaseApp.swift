import SwiftUI
import Shared

@main
struct KmmBaseApp: App {
    private let container = AppContainer()
    var body: some Scene {
        WindowGroup { ContentView(container: container) }
    }
}

struct ContentView: View {
    let container: AppContainer
    @State private var name = ""
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text("iOS · Kotlin Multiplatform").foregroundStyle(.secondary)
                TextField("Tên của bạn", text: $name)
                    .textFieldStyle(.roundedBorder)
                Text(container.getGreeting.execute(name: name)).font(.title2)
                Spacer()
            }
            .padding(24)
            .navigationTitle("KmmBase")
        }
    }
}
