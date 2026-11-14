import Shared
import SwiftUI

/// Full-screen camera with the step HUD on top. SKI watches through the camera and speaks each
/// line in Vietnamese; long-pressing the HUD reveals the ◀ ▶ controls used to rescue a live demo.
struct LiveGuideView: View {
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject private var language: AppLanguage
    let skillId: String
    let skillTitle: String
    let onExit: () -> Void
    let onCompleted: (String, String, Bool) -> Void

    @StateObject private var model: GuideModel
    @State private var cameraGranted = false
    @State private var completionSessionID = UUID().uuidString
    @StateObject private var objectDetector = GarmentDetector()

    init(skillId: String,
         skillTitle: String,
         container: AppContainer,
         shadow: ShadowStore,
         onCompleted: @escaping (String, String, Bool) -> Void = { _, _, _ in },
         onExit: @escaping () -> Void) {
        self.skillId = skillId
        self.skillTitle = skillTitle
        self.onExit = onExit
        self.onCompleted = onCompleted
        _model = StateObject(wrappedValue: GuideModel(container: container, shadow: shadow))
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if cameraGranted {
                CameraPreview(camera: model.camera).ignoresSafeArea()
            } else {
                VStack(spacing: 16) {
                    Text(language.text("ui_049"))
                        .foregroundStyle(.white).multilineTextAlignment(.center)
                    Button(language.text("back"), action: leave).tint(ShadowStyle.accent)
                }
                .padding(32)
            }

            if let box = objectDetector.box {
                GeometryReader { geometry in
                    let rect = objectDetector.displayRect(box, in: geometry.size)
                    ObjectCorners().stroke(Color(red: 0.54, green: 0.97, blue: 0.17), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                        .shadow(color: .green.opacity(0.65), radius: 7)
                        .frame(width: max(0, rect.width), height: max(0, rect.height))
                        .position(x: rect.midX, y: rect.midY).allowsHitTesting(false)
                }.ignoresSafeArea().accessibilityHidden(true)
            }
            VStack {
                hud
                Spacer()
                footer
            }
        }
        .onAppear {
            objectDetector.setEnabled(true, garmentOnly: skillId == "shadow-builtin-gap-ao-v1" || skillId == "shadow-builtin-gap-quan-v1")
            model.camera.setFrameHandler { [weak objectDetector] buffer, front in objectDetector?.offer(buffer, front: front) }
            ShadowCamera.requestAccess { granted in
                cameraGranted = granted
                if granted { model.start(skillId: skillId) }
            }
        }
        .onChange(of: model.state.completed) { complete in
            if complete { onCompleted(completionSessionID, model.state.skillName.isEmpty ? skillTitle : model.state.skillName, model.state.manualAdvanceUsed) }
        }
        .onChange(of: scenePhase) { phase in
            if phase == .background { leave() }
        }
        .onDisappear { model.camera.setFrameHandler(nil); objectDetector.setEnabled(false); model.close() }
    }

    private var hud: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !model.state.speechAvailable { Text("Giọng tiếng Việt chưa sẵn sàng. Hãy đọc hướng dẫn trên màn hình.").font(.caption).foregroundStyle(.orange) }
            HStack {
                Text(model.state.skillName.isEmpty ? skillTitle : model.state.skillName)
                    .font(.headline).foregroundStyle(.white)
                Spacer()
                Button(language.text("ui_158"), action: leave).tint(ShadowStyle.accent)
            }

            if skillTitle.hasSuffix(" · Demo") {
                Text("DEMO · Kết quả kiểm tra giả lập").font(.caption.bold()).foregroundStyle(.orange)
            }
            if model.state.completed {
                Text(language.text("ui_014")).font(.title2.bold()).foregroundStyle(ShadowStyle.accent)
            } else if let step = model.state.step {
                Text(language.format("step_progress", String(step.index + 1), String(step.total)))
                    .font(.caption.bold()).foregroundStyle(ShadowStyle.accent)
                HStack(alignment: .center, spacing: 12) {
                    Text(step.instruction).font(.title3.bold()).foregroundStyle(.white).frame(maxWidth: .infinity, alignment: .leading)
                    if ["shadow-builtin-goi-qua-v1", "shadow-builtin-gap-ao-v1", "shadow-builtin-gap-quan-v1"].contains(skillId) {
                        BuiltinStepIllustration(isGift: skillId == "shadow-builtin-goi-qua-v1", step: Int(step.index), isPants: skillId == "shadow-builtin-gap-quan-v1")
                            .frame(width: 112, height: 92)
                    }
                }
                if !step.tip.isEmpty {
                    Text(step.tip).font(.subheadline).foregroundStyle(.white.opacity(0.8))
                }
                if !step.warning.isEmpty {
                    Text(language.format("warning_detail", String(step.warning))).font(.subheadline).foregroundStyle(.orange)
                }
                if !step.referenceImageUrl.isEmpty, let url = URL(string: step.referenceImageUrl) {
                    RemoteImage(url: url.absoluteString, headers: model.imageHeaders(url.absoluteString), height: 96)
                        .frame(width: 96, height: 96)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            } else {
                Text(language.text("ui_213")).foregroundStyle(.white)
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
        if model.state.connection != SocketStatus.open || ["no_frame", "error", "not_done"].contains(model.state.checkStatus) { return .orange }
        if model.state.judging { return ShadowStyle.accent }
        return model.state.running ? .green : .gray
    }

    private var statusLabel: String {
        if model.state.connection == SocketStatus.closed { return language.text("ui_214") }
        if model.state.connection == SocketStatus.connecting { return language.text("connecting") }
        if model.state.checkStatus == "no_frame" { return "Chưa lấy được ảnh camera" }
        if model.state.checkStatus == "error" { return "Không kiểm tra được ảnh" }
        if model.state.checkStatus == "confirming" { return "Đang xác nhận · giữ nguyên vài giây" }
        if model.state.checkStatus == "not_done" { return "Chưa xác nhận · giữ rõ vật trong khung" }
        if model.state.judging { return "AI đang kiểm tra…" }
        return model.state.running ? language.text("ui_227") : language.text("ui_155")
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
                Text(language.systemText(error)).font(.subheadline).foregroundStyle(.red)
            }
            if model.state.running {
                Button("Nghe lại bước này") { model.repeatInstruction() }.buttonStyle(.bordered)
                HStack(spacing: 12) {
                    if (model.state.step?.index ?? 0) > 0 {
                        Button("Bước trước") { model.step(forward: false) }.buttonStyle(.bordered)
                    }
                    Button("Tôi đã xong bước này") { model.step(forward: true) }
                        .frame(maxWidth: .infinity).buttonStyle(.borderedProminent)
                }.disabled(model.state.connection != SocketStatus.open)
                Text("Chỉ bấm khi đã làm xong; AI vẫn tự kiểm tra.")
                    .font(.caption2).foregroundStyle(.white.opacity(0.7))
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
