import SwiftUI
import Shared

struct LiveTryOnView: View {
    @StateObject var model: LiveTryOnModel
    let onClose: () -> Void
    @Environment(\.scenePhase) private var scenePhase

    init(container: AppContainer, shadow: ShadowStore, onClose: @escaping () -> Void) {
        _model = StateObject(wrappedValue: LiveTryOnModel(container: container, shadow: shadow))
        self.onClose = onClose
    }

    private var active: Bool { model.state.phase == .live || model.state.phase == .connecting }

    var body: some View {
        VStack(spacing: 14) {
            HStack {
                Button("Đóng") { model.stop(); onClose() }
                Spacer()
                Text("Thử áo trực tiếp").font(.headline)
            }
            ZStack(alignment: .topTrailing) {
                if active {
                    LiveVideoSurface(surface: model.engine.remoteView)
                    if model.state.phase != .live {
                        Color.black
                        VStack(spacing: 12) {
                            ProgressView().tint(.white)
                            Text("Đang chờ video AI…")
                        }.frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    VStack(spacing: 4) {
                        LiveVideoSurface(surface: model.engine.localView).frame(width: 100, height: 140)
                        Text("Camera gốc").font(.caption2)
                    }.padding(8)
                } else {
                    Text(model.state.garments.isEmpty
                        ? "Chưa có áo trong tủ đồ. Thêm ảnh áo rồi mở lại màn này."
                        : "Chọn áo bên dưới. Khi bắt đầu, camera sẽ được gửi tới Vidu để tạo video thử đồ.")
                        .multilineTextAlignment(.center).padding()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }.frame(maxWidth: .infinity, maxHeight: .infinity).clipped()
            Text(model.state.message).font(.subheadline).multilineTextAlignment(.center)
            Text(String(format: "RAM app: %.0f MB", model.memoryMB)).font(.caption2).monospacedDigit()
            if active {
                Text("Video AI: \(model.state.fps) FPS · còn \(model.state.remainingSeconds)s").font(.caption).monospacedDigit()
                Button("Tôi vẫn đang thử") { model.store.activity() }
            }
            if model.permissionDenied {
                Button("Cho phép camera trong Cài đặt") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                }
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(model.state.garments, id: \.id) { item in
                        Button(item.name.isEmpty ? "Áo" : item.name) { model.store.select(id: item.id) }
                            .buttonStyle(.bordered)
                            .tint(item.id == model.state.selectedId ? .yellow : .white)
                            .disabled(model.state.changing || model.state.phase == .connecting)
                    }
                }
            }
            HStack(spacing: 14) {
                if active {
                    Button("Dừng") { model.stop() }.buttonStyle(.bordered)
                    if model.state.phase == .live {
                        Button(model.state.changing ? "Đang đổi…" : "Đổi áo") { model.store.applySelection() }
                            .buttonStyle(.borderedProminent)
                            .disabled(model.state.changing || model.state.selectedId == model.state.sentGarmentId)
                    }
                } else {
                    Button("Bắt đầu thử") { model.start() }.buttonStyle(.borderedProminent)
                        .disabled(!model.state.available || model.state.selectedId.isEmpty || model.state.phase == .loading)
                    Button("Kiểm tra lại") { model.store.reload() }.disabled(model.state.phase == .loading)
                }
            }
        }
        .padding(16).background(Color.black).foregroundStyle(.white)
        .onChange(of: scenePhase) { if $0 == .background { model.stop() } }
        .onDisappear { model.stop() }
    }
}

private struct LiveVideoSurface: UIViewRepresentable {
    let surface: UIView
    func makeUIView(context: Context) -> UIView { surface }
    func updateUIView(_ uiView: UIView, context: Context) {}
}
