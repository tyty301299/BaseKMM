import SwiftUI
import Shared

struct StudioAskView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    let onService: () -> Void
    var body: some View {
        StudioPage(title: language.text("ui_080"), model: model) {
            Text(language.text("question_hint")).font(.largeTitle.bold())
            Text(language.systemText(model.state.currentStep)).foregroundStyle(.secondary)
            TextField(language.text("ui_137"), text: Binding(get: { model.state.question }, set: { model.send(StudioActionQuestion(value: $0)) }), axis: .vertical)
                .lineLimit(3...6).textFieldStyle(.roundedBorder)
            Text("\(model.state.question.count)/500").font(.caption).foregroundStyle(.secondary)
            LookPrimaryButton(title: language.text("ui_195"), disabled: model.state.question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || model.state.currentStep.isEmpty) { model.send(StudioActionAskSample.shared) }
            if !model.state.answer.isEmpty {
                Text(model.state.answer).padding(20).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 24))
            }
            Button(language.text("ui_132"), action: onService).frame(minHeight: 44)
        }
    }
}
