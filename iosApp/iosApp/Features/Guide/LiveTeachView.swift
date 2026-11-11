import Shared
import SwiftUI

/// Teaching flow: record a demonstration, let the compiler turn it into steps, review, save.
///
/// The server takes keyframes plus a timestamped transcript, never a video file, so recording
/// means sampling frames and listening for Vietnamese sentences at the same time.
struct LiveTeachView: View {
    @EnvironmentObject private var language: AppLanguage
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
                        Text(language.text("ui_244")).font(.title2.bold())
                            .foregroundStyle(ShadowStyle.accent)
                    default: record
                    }

                    if let error = model.state.error {
                        Text(language.systemText(error)).foregroundStyle(.red)
                    }
                }
                .padding(20)
            }
            .navigationTitle(language.text("ui_058"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(language.text("close")) { model.stop(); onFinished() }
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
            Text(language.text("ui_050"))
        }

        if model.state.phase == .recording {
            Text(language.format("recording_counts", String(model.state.elapsedMs / 1000), String(model.state.frameCount)))
                .font(.headline).foregroundStyle(ShadowStyle.accent)
            Text(language.text("ui_189"))
                .font(.subheadline).foregroundStyle(.secondary)
            if !model.state.liveTranscript.isEmpty {
                Text(model.state.liveTranscript).foregroundStyle(.secondary)
            }
            ForEach(Array(model.state.lines.suffix(3).enumerated()), id: \.offset) { _, line in
                Text("· \(line.text)").font(.caption)
            }
            Button(language.text("ui_061")) { model.stopRecording() }
                .frame(maxWidth: .infinity, minHeight: 48).buttonStyle(.borderedProminent)
        } else {
            if !model.state.speechAvailable {
                Text(language.text("ui_157"))
                    .font(.caption).foregroundStyle(.orange)
            }
            if model.state.frameCount > 0 {
                Text(language.format("recorded_counts", String(model.state.frameCount), String(model.state.lines.count)))
                    .font(.subheadline).foregroundStyle(ShadowStyle.accent)
            }
            TextField(language.text("ui_176"), text: Binding(
                get: { model.state.title },
                set: { model.send(TeachActionTitle(value: $0)) })).textFieldStyle(.roundedBorder)
            TextField(language.text("ui_135"), text: Binding(
                get: { model.state.teacher },
                set: { model.send(TeachActionTeacher(value: $0)) })).textFieldStyle(.roundedBorder)
            TextField(language.text("ui_068"), text: Binding(
                get: { model.state.notes },
                set: { model.send(TeachActionNotes(value: $0)) }), axis: .vertical)
                .lineLimit(2...5).textFieldStyle(.roundedBorder)
            Button(model.state.frameCount > 0 ? language.text("ui_069") : language.text("ui_018")) { model.startRecording() }
                .frame(maxWidth: .infinity, minHeight: 48).buttonStyle(.bordered)
            Button(language.text("ui_005")) { model.send(TeachActionCompile.shared) }
                .frame(maxWidth: .infinity, minHeight: 48).buttonStyle(.borderedProminent)
                .disabled(!model.state.canCompile)
        }
    }

    @ViewBuilder private var compiling: some View {
        ProgressView().progressViewStyle(.linear)
        Text(model.state.compilePhase == "verifying"
             ? language.text("ui_008")
             : language.text("ui_007")).font(.headline)
        Text(language.text("ui_164")).foregroundStyle(.secondary)
        Button(language.text("ui_076")) { model.send(TeachActionCancelCompile.shared) }
    }

    @ViewBuilder private var review: some View {
        if let draft = model.state.draft {
            Text(language.text("ui_009")).font(.title2.bold())
            Text(draft.title).font(.headline).foregroundStyle(ShadowStyle.accent)
            ForEach(Array(draft.steps.enumerated()), id: \.offset) { index, step in
                VStack(alignment: .leading, spacing: 4) {
                    TextField(language.format("step_number", String(index + 1)), text: Binding(
                        get: { step.instruction },
                        set: { model.send(TeachActionEditStep(index: Int32(index), instruction: $0)) }),
                        axis: .vertical)
                        .textFieldStyle(.roundedBorder)
                    if !step.tip.isEmpty {
                        Text(language.format("tip_detail", String(step.tip))).font(.caption).foregroundStyle(.secondary)
                    }
                    if !step.warning.isEmpty {
                        Text(language.format("warning_detail", String(step.warning))).font(.caption).foregroundStyle(.orange)
                    }
                }
            }
            Button(language.text("ui_111")) { model.send(TeachActionSave.shared) }
                .frame(maxWidth: .infinity, minHeight: 48).buttonStyle(.borderedProminent)
            Button(language.text("ui_059")) { model.send(TeachActionReset.shared) }
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
