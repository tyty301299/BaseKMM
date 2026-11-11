import SwiftUI
import Shared

struct StudioReviewView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    var body: some View {
        StudioPage(title: language.text("ui_056"), model: model) {
            Text(language.text("ui_013")).font(.largeTitle.bold())
            TextField(language.text("ui_176"), text: Binding(get: { model.state.title }, set: { model.send(StudioActionLessonTitle(value: $0)) }))
                .textFieldStyle(.roundedBorder).accessibilityLabel(language.text("ui_176"))
            ForEach(Array(model.state.draftSteps.enumerated()), id: \.offset) { index, _ in
                VStack(alignment: .leading, spacing: 10) {
                    Text(language.format("step_number", String(index + 1))).font(.headline).foregroundStyle(LookStyle.accent)
                    TextField(language.text("ui_144"), text: Binding(get: { model.state.draftSteps[index] }, set: { model.send(StudioActionEditStep(index: Int32(index), value: $0)) }), axis: .vertical)
                        .lineLimit(2...6).textFieldStyle(.roundedBorder).accessibilityLabel(language.format("step_instruction_number", String(index + 1)))
                }.padding(18).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 22))
            }
            LookPrimaryButton(title: language.text("ui_112"), disabled: !model.state.canSaveSkill || model.state.savedSkillId != nil) { model.send(StudioActionSaveSkill.shared) }
        }
    }
}
