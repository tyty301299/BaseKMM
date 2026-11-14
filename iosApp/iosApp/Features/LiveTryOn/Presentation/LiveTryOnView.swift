import SwiftUI
import Shared
import XmaxSDK

struct LiveTryOnView: View {
    @StateObject var model: LiveTryOnModel
    let onClose: () -> Void
    private let initialGarmentId: String?
    private let onHomeCamera: Bool
    @State private var closing = false
    @State private var didAutoStart = false
    @Environment(\.scenePhase) private var scenePhase

    init(container: AppContainer, shadow: ShadowStore, initialGarmentId: String? = nil, onHomeCamera: Bool = false, onClose: @escaping () -> Void) {
        _model = StateObject(wrappedValue: LiveTryOnModel(container: container, shadow: shadow))
        self.initialGarmentId = initialGarmentId
        self.onHomeCamera = onHomeCamera
        self.onClose = onClose
    }

    var body: some View {
        VStack(spacing: 14) {
            HStack {
                Button(onHomeCamera ? "Camera gốc" : "Đóng") {
                    closing = true
                    Task {
                        await model.close()
                        onClose()
                    }
                }.disabled(closing)
                Spacer()
                Text(onHomeCamera ? "Thử đồ · Home" : "Thử đồ trực tiếp").font(.headline)
                Spacer()
                Button(action: model.flipCamera) {
                    Image(systemName: "arrow.triangle.2.circlepath.camera")
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel(model.cameraPosition == .front ? "Chuyển camera sau" : "Chuyển camera trước")
                .disabled(model.busy || model.switchingCamera)
            }
            ZStack {
                XmaxRealtimeDualVideo(localTrack: model.localTrack, remoteTrack: model.remoteTrack,
                                      isInteractionEnabled: false)
                if model.localTrack == nil {
                    VStack(spacing: 12) {
                        if model.busy { ProgressView().tint(.white) }
                        Text(model.hasAttempted ? model.message : model.garments.isEmpty ? "Chưa có trang phục trong túi đồ." : "Chọn trang phục rồi bắt đầu thử. Camera sẽ được gửi để tạo hình ảnh thử đồ trực tiếp.")
                            .multilineTextAlignment(.center).padding()
                    }
                }
            }.frame(maxWidth: .infinity, maxHeight: .infinity).clipped()
            if model.localTrack != nil {
                Text(model.message).font(.subheadline).multilineTextAlignment(.center)
            }
            if !model.diagnosticCode.isEmpty {
                Text("Mã lỗi: \(model.diagnosticCode)").font(.caption).textSelection(.enabled)
            }
            if model.live {
                Text("Còn \(model.remainingSeconds)s · \(model.videoFPS) FPS" + (model.transportDelayMS.map { " · Trễ truyền video: \($0) ms (chưa gồm AI)" } ?? ""))
                    .font(.caption2).foregroundStyle(.secondary)
            } else if !model.busy {
                Text(model.cameraPosition == .front ? "Camera trước" : "Camera sau").font(.caption)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(model.garments, id: \.id) { item in
                        Button {
                            model.selectedId = item.id
                        } label: {
                            Label(item.name, systemImage: model.appliedId == item.id ? "checkmark.circle.fill" : "tshirt")
                        }.buttonStyle(.bordered)
                            .tint(item.id == model.selectedId ? .yellow : .white)
                            .disabled(model.busy || model.switchingCamera)
                    }
                }
            }
            HStack {
                Button(model.live ? "Đổi trang phục" : model.hasAttempted ? "Thử lại" : "Bắt đầu thử") { model.start() }
                    .buttonStyle(.borderedProminent)
                    .disabled(model.busy || model.switchingCamera || model.selectedId.isEmpty || model.selectedId == model.appliedId)
                if model.live || model.busy {
                    Button("Dừng") { model.stop() }.buttonStyle(.bordered)
                }
            }
        }
        .disabled(closing)
        .padding(16).background(Color.black).foregroundStyle(.white)
        .onChange(of: scenePhase) { if $0 == .background { model.stop() } }
        .onAppear {
            guard !didAutoStart, let id = initialGarmentId else { return }
            didAutoStart = true
            model.selectedId = id
            model.start()
        }
        .onDisappear { model.stop() }
        .alert(item: $model.alert) { notice in
            Alert(title: Text(notice.title), message: Text(notice.message),
                  primaryButton: .default(Text("Thử lại")) { model.retry(notice) },
                  secondaryButton: .cancel(Text("Đóng")))
        }
    }
}
