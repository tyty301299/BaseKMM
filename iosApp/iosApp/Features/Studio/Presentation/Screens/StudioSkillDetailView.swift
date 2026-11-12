import SwiftUI
import Shared

struct StudioSkillDetailView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    let skillID: String
    let onGuide: () -> Void
    @State private var confirm = false
    var body: some View {
        StudioPage(title: language.text("ui_104"), model: model) {
            if let skill = model.state.data.skills.first(where: { $0.id == skillID }) {
                Text(skill.title).font(.largeTitle.bold())
                Text(language.format("sample_steps_count", String(skill.steps.count))).foregroundStyle(.secondary)
                ForEach(Array(skill.steps.enumerated()), id: \.offset) { index, step in
                    Text("\(index + 1). \(step)").frame(maxWidth: .infinity, alignment: .leading)
                        .padding(18).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 20))
                }
                LookPrimaryButton(title: language.text("ui_019"), action: onGuide)
                Button(language.text("ui_200"), role: .destructive) { confirm = true }.frame(minHeight: 44)
            } else if !model.state.loading { Text(language.text("ui_102")) }
        }
        .confirmationDialog(language.text("ui_201"), isPresented: $confirm, titleVisibility: .visible) {
            Button(language.text("ui_197"), role: .destructive) { model.send(StudioActionDeleteSkill(id: skillID)) }
            Button(language.text("cancel"), role: .cancel) {}
        }
    }
}
