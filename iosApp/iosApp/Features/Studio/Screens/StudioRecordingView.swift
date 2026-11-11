import SwiftUI
import Shared

struct StudioRecordingView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    let onCompile: () -> Void
    var body: some View {
        StudioPage(title: language.text("ui_149"), model: model) {
            VStack(spacing: 20) {
                StudioInstructionIllustration()
                Text(model.state.recording ? language.text("ui_221") : language.text("ui_236")).font(.headline)
                Text(language.text("ui_082")).font(.caption)
            }.frame(maxWidth: .infinity, minHeight: 240).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 28))
            Text(language.text("ui_118")).font(.headline)
            TextField(language.text("ui_145"), text: Binding(get: { model.state.transcript }, set: { model.send(StudioActionTranscript(value: $0)) }), axis: .vertical)
                .lineLimit(3...6).textFieldStyle(.roundedBorder)
            Text(language.text("ui_146")).font(.caption).foregroundStyle(.secondary)
            if model.state.recording {
                LookPrimaryButton(title: language.text("ui_062")) { model.send(StudioActionStopRecording.shared) }
            } else {
                LookPrimaryButton(title: language.text("ui_192"), disabled: !model.state.recorded, action: onCompile)
                Button(language.text("ui_106")) { model.send(StudioActionStartRecording.shared) }.frame(minHeight: 44)
            }
        }
    }
}
