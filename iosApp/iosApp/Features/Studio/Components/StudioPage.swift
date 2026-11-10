import SwiftUI
import Shared

struct StudioPage<Content: View>: View {
    @Environment(\.lookOperationBusy) private var lookBusy
    let title: String
    @ObservedObject var model: StudioModel
    @ViewBuilder let content: () -> Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                if model.state.loading { ProgressView("Đang đọc dữ liệu trên máy…") }
                if model.busy { ProgressView(model.state.busy).accessibilityLabel(model.state.busy) }
                if let error = model.state.error {
                    Text(error).foregroundStyle(.red).accessibilityLabel("Lỗi. \(error)")
                    Button("Tải lại dữ liệu") { model.send(StudioActionRetry.shared) }.disabled(model.busy)
                }
                content().disabled(model.busy || lookBusy)
            }.padding(24).frame(maxWidth: 640).frame(maxWidth: .infinity)
        }
        .background(LookStyle.background.ignoresSafeArea()).navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
        .navigationBarBackButtonHidden(model.busy || lookBusy).interactiveDismissDisabled(model.busy || lookBusy)
    }
}
