import Shared
import SwiftUI

/// Full-screen camera with the step HUD on top. SHADOW watches through the camera and speaks each
/// line in Vietnamese; long-pressing the HUD reveals the ◀ ▶ controls used to rescue a live demo.
struct LiveGuideView: View {
    let skillId: String
    let skillTitle: String
    let onExit: () -> Void

    @StateObject private var model: GuideModel
    @State private var cameraGranted = false

    init(skillId: String,
         skillTitle: String,
         container: AppContainer,
         shadow: ShadowStore,
         onExit: @escaping () -> Void) {
        self.skillId = skillId
        self.skillTitle = skillTitle
        self.onExit = onExit
        _model = StateObject(wrappedValue: GuideModel(container: container, shadow: shadow))
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if cameraGranted {
                CameraPreview(camera: model.camera).ignoresSafeArea()
            } else {
                VStack(spacing: 16) {
                    Text("Cần quyền camera để SHADOW nhìn thao tác của bạn.")
                        .foregroundStyle(.white).multilineTextAlignment(.center)
                    Button("Quay lại", action: leave).tint(ShadowStyle.accent)
                }
                .padding(32)
            }

            VStack {
                hud
                Spacer()
                footer
            }
        }
        .onAppear {
            ShadowCamera.requestAccess { granted in
                cameraGranted = granted
                if granted { model.start(skillId: skillId) }
            }
        }
        .onDisappear { model.close() }
    }

    private var hud: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(model.state.skillName.isEmpty ? skillTitle : model.state.skillName)
                    .font(.headline).foregroundStyle(.white)
                Spacer()
                Button("Thoát", action: leave).tint(ShadowStyle.accent)
            }

            if model.state.completed {
                Text("Bạn đã hoàn thành!").font(.title2.bold()).foregroundStyle(ShadowStyle.accent)
            } else if let step = model.state.step {
                Text("BƯỚC \(step.index + 1) / \(step.total)")
                    .font(.caption.bold()).foregroundStyle(ShadowStyle.accent)
                Text(step.instruction).font(.title3.bold()).foregroundStyle(.white)
                if !step.tip.isEmpty {
                    Text(step.tip).font(.subheadline).foregroundStyle(.white.opacity(0.8))
                }
                if !step.warning.isEmpty {
                    Text("Lưu ý: \(step.warning)").font(.subheadline).foregroundStyle(.orange)
                }
                if !step.referenceImageUrl.isEmpty, let url = URL(string: step.referenceImageUrl) {
                    AsyncImage(url: url) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
                        .frame(width: 96, height: 96)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            } else {
                Text("Đang chuẩn bị…").foregroundStyle(.white)
            }

            status
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.black.opacity(0.7))
        .onLongPressGesture { model.toggleDebugControls() }
    }

    private var status: some View {
        HStack(spacing: 8) {
            Circle().fill(statusColor).frame(width: 10, height: 10)
            Text(statusLabel).font(.caption).foregroundStyle(statusColor)
            if model.state.latencyMs > 0 {
                Text("\(model.state.latencyMs) ms").font(.caption2).foregroundStyle(.white.opacity(0.6))
            }
        }
    }

    private var statusColor: Color {
        if model.state.connection != SocketStatus.open { return .orange }
        if model.state.judging { return ShadowStyle.accent }
        return model.state.running ? .green : .gray
    }

    private var statusLabel: String {
        if model.state.connection == SocketStatus.closed { return "Đang kết nối lại…" }
        if model.state.connection == SocketStatus.connecting { return "Đang kết nối…" }
        if model.state.judging { return "SHADOW đang nhìn…" }
        return model.state.running ? "Đang theo dõi" : "Sẵn sàng"
    }

    private var footer: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !model.state.spokenLine.isEmpty {
                Text(model.state.spokenLine).foregroundStyle(.white)
            }
            if !model.state.hint.isEmpty {
                Text(model.state.hint).font(.subheadline).foregroundStyle(.orange)
            }
            if let error = model.state.error {
                Text(error).font(.subheadline).foregroundStyle(.red)
            }
            if model.state.debugControls {
                HStack(spacing: 12) {
                    Button("◀") { model.step(forward: false) }.buttonStyle(.bordered)
                    Button("▶") { model.step(forward: true) }.buttonStyle(.bordered)
                }
            }
            if model.state.completed {
                Button(action: leave) {
                    Text("Xong").frame(maxWidth: .infinity).padding(.vertical, 14)
                }
                .background(ShadowStyle.accent).foregroundStyle(.black)
                .clipShape(RoundedRectangle(cornerRadius: 14))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.black.opacity(0.7))
    }

    private func leave() {
        model.stop()
        onExit()
    }
}

/// Bridges `GuideStore` to SwiftUI. The store itself is built in Kotlin (`AppContainer`), so this
/// only supplies the camera and Vietnamese voice.
final class GuideModel: ObservableObject {
    @Published private(set) var state: GuideState

    let camera = ShadowCamera()
    private let speaker = ShadowSpeaker()
    private let store: GuideStore
    private var subscription: StoreSubscription?

    init(container: AppContainer, shadow: ShadowStore) {
        let bridge = CameraFrameBridge(camera: camera)
        let voice = SpeakerBridge(speaker: speaker)
        store = container.guideStore(shadow: shadow, camera: bridge, speaker: voice)
        state = store.currentState
        subscription = store.observeState { [weak self] value in
            self?.state = value
        }
    }

    func start(skillId: String) {
        camera.start()
        store.dispatch(action: GuideActionStart(skillId: skillId))
    }

    func stop() {
        store.dispatch(action: GuideActionStop.shared)
        camera.stop()
    }

    func step(forward: Bool) {
        store.dispatch(action: GuideActionStep(forward: forward))
    }

    func toggleDebugControls() {
        store.dispatch(action: GuideActionShowDebugControls(visible: !state.debugControls))
    }

    func close() {
        subscription?.cancel()
        camera.stop()
        store.close()
    }
}
