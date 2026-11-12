import SwiftUI
import Shared

struct StudioSavedSkillView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    let skillID: String
    let onGuide: () -> Void
    let onHistory: () -> Void
    var body: some View {
        StudioPage(title: language.text("ui_242"), model: model) {
            if model.state.data.skills.contains(where: { $0.id == skillID }) {
                Image(systemName: "checkmark.circle.fill").font(.system(size: 72)).foregroundStyle(LookStyle.accent).padding(.vertical, 32)
                Text(language.text("ui_245")).font(.largeTitle.bold())
                Text(language.text("ui_101"))
                LookPrimaryButton(title: language.text("ui_108"), action: onGuide)
                LookPrimaryButton(title: language.text("ui_193"), action: onHistory)
            } else if !model.state.loading { Text(language.text("ui_092")) }
        }
    }
}
