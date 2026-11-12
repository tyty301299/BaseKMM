import SwiftUI
import Shared

struct StudioGuideView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    let onAsk: () -> Void
    let onDone: () -> Void
    var body: some View {
        StudioPage(title: language.text("ui_107"), model: model) {
            if let skill = model.state.activeSkill {
                if model.state.completed {
                    Image(systemName: "checkmark.seal.fill").font(.system(size: 72)).foregroundStyle(LookStyle.accent)
                    Text(language.text("ui_015")).font(.largeTitle.bold())
                    Text(language.text("ui_073"))
                    LookPrimaryButton(title: language.text("ui_187"), action: onDone)
                } else {
                    Text(skill.title).font(.title.bold())
                    Text(language.format("step_progress", String(model.state.stepIndex + 1), String(skill.steps.count))).font(.caption).foregroundStyle(LookStyle.accent)
                    ProgressView(value: Double(model.state.stepIndex + 1), total: Double(skill.steps.count))
                    StudioInstructionIllustration()
                    Text(language.systemText(model.state.currentStep)).font(.title2.bold())
                    Text(language.text("ui_079")).foregroundStyle(.secondary)
                    LookPrimaryButton(title: language.text("ui_178")) { model.send(StudioActionNextStep.shared) }
                    Button(language.text("previous_step")) { model.send(StudioActionPreviousStep.shared) }.disabled(model.state.stepIndex == 0).frame(minHeight: 44)
                    LookPrimaryButton(title: language.text("ui_081"), action: onAsk)
                }
            } else if !model.state.loading { Text(language.text("ui_093")) }
        }
    }
}
