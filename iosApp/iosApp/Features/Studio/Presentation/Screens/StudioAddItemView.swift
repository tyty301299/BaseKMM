import SwiftUI
import Shared

struct StudioAddItemView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    let onPermission: () -> Void
    var body: some View {
        StudioPage(title: language.text("add_garment"), model: model) {
            Text(language.text("ui_127")).font(.largeTitle.bold())
            Image(systemName: "tshirt").font(.system(size: 74)).frame(maxWidth: .infinity, minHeight: 160)
                .background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 28))
            Text(language.text("ui_017")).foregroundStyle(.secondary)
            LookPrimaryButton(title: language.text("ui_196"), action: onPermission)
            Text(language.text("ui_075")).font(.headline)
            LookPrimaryButton(title: language.text("ui_163")) { model.send(StudioActionAddSample(preset: "coat")) }
            LookPrimaryButton(title: language.text("ui_161")) { model.send(StudioActionAddSample(preset: "blouse")) }
        }
    }
}
