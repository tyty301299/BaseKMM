import SwiftUI
import Shared

struct HomeView: View {
    let container: AppContainer
    let onOpenNotes: () -> Void
    @State private var name = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("iOS · Kotlin Multiplatform").foregroundStyle(.secondary)
            TextField("Tên của bạn", text: $name)
                .textFieldStyle(.roundedBorder)
            Text(container.getGreeting.execute(name: name)).font(.title2)
            Button("Ghi chú", action: onOpenNotes).buttonStyle(.borderedProminent)
            Spacer()
        }
        .padding(24)
        .navigationTitle("KmmBase")
    }
}
