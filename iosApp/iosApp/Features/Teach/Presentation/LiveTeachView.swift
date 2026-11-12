import Shared
import SwiftUI
import AVKit

/// Teaching flow: record a demonstration, let the compiler turn it into steps, review, save.
///
/// Supports live keyframes/dictation and persisted video-learning jobs.
struct LiveTeachView: View {
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject private var language: AppLanguage
    let onFinished: () -> Void

    @StateObject private var model: TeachModel
    @State private var cameraGranted = false
    @State private var showVideoPicker = false
    @State private var recordVideo = false
    @State private var playVideo = false
    @State private var confirmDelete = false

    init(container: AppContainer, shadow: ShadowStore, onFinished: @escaping () -> Void) {
        self.onFinished = onFinished
        _model = StateObject(wrappedValue: TeachModel(container: container, shadow: shadow))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    videoControls
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
        .sheet(isPresented: $playVideo) {
            if let url = URL(string: model.state.videoLocalUri) { VideoPlayer(player: AVPlayer(url: url)) }
        }
        .sheet(isPresented: $showVideoPicker) {
            TeachVideoPicker(camera: recordVideo) { url in
                if let url { model.uploadVideo(url) }
                showVideoPicker = false
            }
        }
        .alert("Xóa video/tác vụ trên máy chủ?", isPresented: $confirmDelete) {
            Button("Xóa", role: .destructive) { model.send(TeachActionDeleteVideo.shared) }
            Button("Giữ lại", role: .cancel) {}
        } message: { Text("Bài đã lưu không bị xóa. Video và kết quả tác vụ này sẽ không mở lại được.") }
        .onAppear {
            ShadowCamera.requestAccess { cameraGranted = $0 }
            model.prepareMicrophone()
        }
        .onChange(of: model.state.savedSkillId) { value in
            if value != nil { onFinished() }
        }
        .onChange(of: scenePhase) { phase in
            if phase == .background { model.stopRecording(); model.stop() }
        }
        .onDisappear { model.close() }
    }

    @ViewBuilder private var videoControls: some View {
        if model.state.phase == .idle {
            Text("Học từ video: nhập tên và ghi chú, rồi quay hoặc chọn clip tối đa 180 giây / 32 MB.").font(.caption)
            HStack {
                Button("Chọn video") { model.stop(); recordVideo = false; showVideoPicker = true }
                Button("Quay video") { model.stop(); recordVideo = true; showVideoPicker = true }
                    .disabled(!cameraGranted || !UIImagePickerController.isSourceTypeAvailable(.camera))
            }.disabled(model.importingVideo || model.state.videoBusy || model.state.hasVideoJob || model.state.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
        if !model.state.videoLocalUri.isEmpty { Button("Xem video gốc") { playVideo = true } }
        if !model.state.videoStatus.isEmpty { Text(model.state.videoStatusLabel) }
        if model.state.videoBusy || model.importingVideo { ProgressView() }
        if model.state.hasVideoJob {
            Button("Mở lại kết quả") { model.send(TeachActionResumeVideo.shared) }.disabled(model.state.videoBusy || model.state.saving)
            if ["failed", "interrupted", "cancelled"].contains(model.state.videoStatus) {
                Button("Thử lại") { model.send(TeachActionRetryVideo.shared) }.disabled(model.state.videoBusy)
            }
            Button("Dừng xử lý") { model.send(TeachActionCancelVideo.shared) }.disabled(model.state.saving)
            Button("Xóa video/tác vụ trên máy chủ", role: .destructive) { confirmDelete = true }.disabled(model.state.videoBusy || model.state.saving)
        }
        if let error = model.videoError { Text(error).foregroundStyle(.red) }
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
                .disabled(model.state.stopping)
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
                .disabled(!cameraGranted || model.state.stopping || model.state.videoBusy || model.state.hasVideoJob)
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
            if model.state.saving { ProgressView() }
            Text(draft.title).font(.headline).foregroundStyle(ShadowStyle.accent)
            Text(draft.summary)
            Text("Nguồn: \(draft.compiledBy.isEmpty ? "chưa xác định" : draft.compiledBy)")
            if draft.compiledBy != "ai" || !draft.fallbackReason.isEmpty {
                Text("Bản nháp chưa được xác nhận là AI thật. Hãy kiểm tra kỹ trước khi lưu.").foregroundStyle(.orange)
            }
            ForEach(draft.warnings, id: \.self) { Text($0).foregroundStyle(.orange) }
            if !model.state.dirtyStepIds.isEmpty { Text("Bước đã sửa cần kiểm chứng lại trước khi lưu.").foregroundStyle(.orange) }
            ForEach(Array(draft.steps.enumerated()), id: \.offset) { index, step in
                VStack(alignment: .leading, spacing: 4) {
                    TextField(language.format("step_number", String(index + 1)), text: Binding(
                        get: { step.instruction },
                        set: { model.send(TeachActionEditStep(index: Int32(index), instruction: $0)) }),
                        axis: .vertical)
                        .textFieldStyle(.roundedBorder)
                        .disabled(model.state.saving || model.state.verifyingStep != nil)
                    if !step.referenceImageUrl.isEmpty {
                        RemoteImage(url: step.referenceImageUrl, headers: model.imageHeaders(step.referenceImageUrl), height: 100, fit: true)
                    }
                    Text(draft.checks.first(where: { $0.stepId == step.id })?.reason ?? "Chưa kiểm chứng bước này.").font(.caption)
                    if model.state.verifyingStep?.intValue == index { ProgressView() }
                    Button("Kiểm chứng lại bước") {
                        model.send(TeachActionVerifyStep(index: Int32(index), referenceFrame: nil))
                    }.disabled(model.state.saving || model.state.verifyingStep != nil || step.instruction.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    Menu("Chọn ảnh khác và kiểm chứng") {
                        ForEach(Array(draft.frames.enumerated()), id: \.offset) { _, frame in
                            Button("Ảnh \(frame.index + 1) · \(Int(frame.seconds)) giây") {
                                model.send(TeachActionVerifyStep(index: Int32(index), referenceFrame: KotlinInt(int: frame.index)))
                            }
                        }
                    }.disabled(draft.frames.isEmpty || model.state.saving || model.state.verifyingStep != nil)
                    if !step.tip.isEmpty {
                        Text(language.format("tip_detail", String(step.tip))).font(.caption).foregroundStyle(.secondary)
                    }
                    if !step.warning.isEmpty {
                        Text(language.format("warning_detail", String(step.warning))).font(.caption).foregroundStyle(.orange)
                    }
                }
            }
            Button(language.text("ui_111")) { model.send(TeachActionSave.shared) }
                .disabled(!model.state.canSave)
                .frame(maxWidth: .infinity, minHeight: 48).buttonStyle(.borderedProminent)
            Button(language.text("ui_059")) { model.send(TeachActionReset.shared) }
        }
    }
}
