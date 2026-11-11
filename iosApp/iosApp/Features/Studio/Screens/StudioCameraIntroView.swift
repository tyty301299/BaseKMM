import SwiftUI
import Shared

struct StudioCameraIntroView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    let forWardrobe: Bool
    let onContinue: () -> Void
    var body: some View {
        StudioPage(title: "Camera", model: model) {
            Image(systemName: "camera").font(.system(size: 72)).foregroundStyle(LookStyle.accent).padding(.vertical, 35)
            Text(forWardrobe ? language.text("ui_032") : language.text("ui_167")).font(.largeTitle.bold())
            Text(language.text("ui_028"))
            if forWardrobe {
                LookPrimaryButton(title: language.text("ui_172")) { model.send(StudioActionAddSample(preset: "coat")) }
            } else { LookPrimaryButton(title: language.text("ui_129"), action: onContinue) }
        }
    }
}
