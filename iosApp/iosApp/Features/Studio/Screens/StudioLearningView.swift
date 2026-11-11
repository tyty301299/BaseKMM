import SwiftUI
import Shared

struct StudioLearningView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    let onTeach: () -> Void
    let onSkill: (String) -> Void
    let onService: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            Text(language.text("learn_heading")).font(.largeTitle.bold())
            Text(language.text("ui_168")).foregroundStyle(.secondary)
            LookPrimaryButton(title: language.text("ui_060"), disabled: model.busy, action: onTeach)
            Button(language.text("ui_057"), action: onService).frame(minHeight: 44)
            Text(language.text("ui_104")).font(.title2.bold())
            if model.state.loading { ProgressView(language.text("ui_231")) }
            if let error = model.state.error {
                Text(language.systemText(error)).foregroundStyle(.red)
                Button(language.text("refresh")) { model.send(StudioActionRetry.shared) }
            }
            if model.state.data.skills.isEmpty { Text(language.text("ui_038")).foregroundStyle(.secondary) }
            ForEach(model.state.data.skills, id: \.id) { skill in
                Button { onSkill(skill.id) } label: {
                    HStack(spacing: 16) {
                        Image(systemName: "book").foregroundStyle(LookStyle.accent)
                        VStack(alignment: .leading, spacing: 6) {
                            Text(skill.title).font(.headline)
                            Text(language.format("saved_steps_count", String(skill.steps.count))).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer(); Image(systemName: "chevron.right")
                    }.padding(20).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 24))
                }.buttonStyle(.plain)
            }
        }
    }
}
