import Shared
import SwiftUI

/// Teaching flow: record a demonstration, let the compiler turn it into steps, review, save.
///
/// The server takes keyframes plus a timestamped transcript, never a video file, so recording
/// means sampling frames and listening for Vietnamese sentences at the same time.
struct LiveTeachView: View {
    let onFinished: () -> Void

    @StateObject private var model: TeachModel
    @State private var cameraGranted = false

    init(container: AppContainer, shadow: ShadowStore, onFinished: @escaping () -> Void) {
        self.onFinished = onFinished
        _model = StateObject(wrappedValue: TeachModel(container: container, shadow: shadow))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    switch model.state.phase {
                    case .compiling: compiling
                    case .review: review
                    case .saved:
                        Text("Đã lưu kỹ năng.").font(.title2.bold())
                            .foregroundStyle(ShadowStyle.accent)
                    default: record
                    }

                    if let error = model.state.error {
                        Text(error).foregroundStyle(.red)
                    }
                }
                .padding(20)
            }
            .navigationTitle("Dạy kỹ năng mới")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Đóng") { model.stop(); onFinished() }
                }
            }
        }
        .onAppear {
            ShadowCamera.requestAccess { cameraGranted = $0 }
            model.prepareMicrophone()
        }
        .onChange(of: model.state.savedSkillId) { value in
            if value != nil { onFinished() }
        }
        .onDisappear { model.close() }
    }

    @ViewBuilder private var record: some View {
        if cameraGranted {
            CameraPreview(camera: model.camera)
                .frame(height: 260)
                .clipShape(RoundedRectangle(cornerRadius: 16))
        } else {
            Text("Cần quyền camera để ghi lại thao tác mẫu.")
        }

        if model.state.phase == .recording {
            Text("Đang ghi · \(model.state.elapsedMs / 1000)s · \(model.state.frameCount) khung hình")
                .font(.headline).foregroundStyle(ShadowStyle.accent)
            Text("Vừa làm vừa nói: \"đầu tiên…\", \"nhớ là…\", \"đừng…\"")
                .font(.subheadline).foregroundStyle(.secondary)
            if !model.state.liveTranscript.isEmpty {
                Text(model.state.liveTranscript).foregroundStyle(.secondary)
            }
            ForEach(Array(model.state.lines.suffix(3).enumerated()), id: \.offset) { _, line in
                Text("· \(line.text)").font(.caption)
            }
            Button("Dừng ghi") { model.stopRecording() }
                .frame(maxWidth: .infinity, minHeight: 48).buttonStyle(.borderedProminent)
        } else {
            if !model.state.speechAvailable {
                Text("Thiết bị chưa nhận giọng nói tiếng Việt — hãy gõ ghi chú bên dưới, AI vẫn học được.")
                    .font(.caption).foregroundStyle(.orange)
            }
            if model.state.frameCount > 0 {
                Text("Đã ghi \(model.state.frameCount) khung hình · \(model.state.lines.count) câu")
                    .font(.subheadline).foregroundStyle(ShadowStyle.accent)
            }
            TextField("Tên kỹ năng", text: Binding(
                get: { model.state.title },
                set: { model.send(TeachActionTitle(value: $0)) })).textFieldStyle(.roundedBorder)
            TextField("Người dạy", text: Binding(
                get: { model.state.teacher },
                set: { model.send(TeachActionTeacher(value: $0)) })).textFieldStyle(.roundedBorder)
            TextField("Ghi chú thêm (hoặc mô tả bằng chữ)", text: Binding(
                get: { model.state.notes },
                set: { model.send(TeachActionNotes(value: $0)) }), axis: .vertical)
                .lineLimit(2...5).textFieldStyle(.roundedBorder)
            Button(model.state.frameCount > 0 ? "Ghi lại" : "Bắt đầu ghi") { model.startRecording() }
                .frame(maxWidth: .infinity, minHeight: 48).buttonStyle(.bordered)
            Button("AI học ngay") { model.send(TeachActionCompile.shared) }
                .frame(maxWidth: .infinity, minHeight: 48).buttonStyle(.borderedProminent)
                .disabled(!model.state.canCompile)
        }
    }

    @ViewBuilder private var compiling: some View {
        ProgressView().progressViewStyle(.linear)
        Text(model.state.compilePhase == "verifying"
             ? "AI đang tự kiểm tra lại kỹ năng…"
             : "AI đang chia thao tác thành từng bước…").font(.headline)
        Text("Thường mất 10–30 giây.").foregroundStyle(.secondary)
        Button("Huỷ") { model.send(TeachActionCancelCompile.shared) }
    }

    @ViewBuilder private var review: some View {
        if let draft = model.state.draft {
            Text("AI đã học xong").font(.title2.bold())
            Text(draft.title).font(.headline).foregroundStyle(ShadowStyle.accent)
            ForEach(Array(draft.steps.enumerated()), id: \.offset) { index, step in
                VStack(alignment: .leading, spacing: 4) {
                    TextField("Bước \(index + 1)", text: Binding(
                        get: { step.instruction },
                        set: { model.send(TeachActionEditStep(index: Int32(index), instruction: $0)) }),
                        axis: .vertical)
                        .textFieldStyle(.roundedBorder)
                    if !step.tip.isEmpty {
                        Text("Mẹo: \(step.tip)").font(.caption).foregroundStyle(.secondary)
                    }
                    if !step.warning.isEmpty {
                        Text("Lưu ý: \(step.warning)").font(.caption).foregroundStyle(.orange)
                    }
                }
            }
            Button("Lưu kỹ năng") { model.send(TeachActionSave.shared) }
                .frame(maxWidth: .infinity, minHeight: 48).buttonStyle(.borderedProminent)
            Button("Dạy lại") { model.send(TeachActionReset.shared) }
        }
    }
}

/// Bridges `TeachStore` to SwiftUI, driving the camera and Vietnamese dictation.
final class TeachModel: ObservableObject {
    @Published private(set) var state: TeachState

    let camera = ShadowCamera()
    private let store: TeachStore
    private var subscription: StoreSubscription?
    private var listener: ShadowListener?

    init(container: AppContainer, shadow: ShadowStore) {
        let bridge = CameraFrameBridge(camera: camera)
        store = container.teachStore(shadow: shadow, camera: bridge)
        state = store.currentState
        subscription = store.observeState { [weak self] value in self?.state = value }
        listener = ShadowListener(
            elapsedSeconds: { [weak self] in Double(self?.store.currentState.elapsedMs ?? 0) / 1000 },
            onSentence: { [weak self] t0, t1, text in
                self?.store.dispatch(action: TeachActionSentence(t0: t0, t1: t1, text: text))
            },
            onPartial: { [weak self] text in
                self?.store.dispatch(action: TeachActionPartialSentence(text: text))
            },
            onUnavailable: { [weak self] in
                self?.store.dispatch(action: TeachActionSpeechAvailable(value: false))
            })
    }

    func prepareMicrophone() {
        listener?.requestAccess { [weak self] granted in
            self?.store.dispatch(action: TeachActionSpeechAvailable(
                value: granted && (self?.listener?.available ?? false)))
        }
    }

    func send(_ action: TeachAction) { store.dispatch(action: action) }

    func startRecording() {
        camera.start()
        store.dispatch(action: TeachActionStartRecording.shared)
        listener?.start()
    }

    func stopRecording() {
        listener?.stop()
        store.dispatch(action: TeachActionStopRecording.shared)
    }

    func stop() {
        listener?.stop()
        camera.stop()
    }

    func close() {
        listener?.release()
        subscription?.cancel()
        camera.stop()
        store.close()
    }
}
