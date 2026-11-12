import SwiftUI
import Shared

struct StudioTeachView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    let onRecord: () -> Void
    var body: some View {
        StudioPage(title: language.text("ui_034"), model: model) {
            Text(language.text("ui_031")).font(.largeTitle.bold())
            StudioInstructionIllustration()
            Label(language.text("ui_258"), systemImage: "1.circle")
            Label(language.text("ui_188"), systemImage: "2.circle")
            Label(language.text("ui_055"), systemImage: "3.circle")
            Text(language.text("ui_012")).foregroundStyle(.secondary)
            LookPrimaryButton(title: language.text("ui_020"), action: onRecord)
        }
    }
}
