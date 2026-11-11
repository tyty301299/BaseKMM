import SwiftUI
import Shared

struct LookDetailView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: LookModel
    let recordID: String
    let onRetry: () -> Void
    @State private var confirmDelete = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                if let record = model.state.records.first(where: { $0.id == recordID }) {
                    Text(record.title).font(.largeTitle.bold())
                    LookMannequin(preset: record.preset, before: record.before, flipped: record.flipped)
                        .frame(height: 360).frame(maxWidth: .infinity)
                        .background(Color(red: 0.81, green: 0.83, blue: 0.77), in: RoundedRectangle(cornerRadius: 28))
                    Text(language.format("saved_sample_occasion", String(record.occasion)))
                    LookPrimaryButton(title: language.text("retry"), disabled: model.state.saving) {
                        model.send(LookActionReuse(id: recordID)); onRetry()
                    }
                    Button(language.text("ui_199"), role: .destructive) { confirmDelete = true }
                        .frame(minHeight: 44).disabled(model.state.saving)
                } else if model.state.loading { ProgressView() }
                else { Text(language.text("ui_134")) }
                if let error = model.state.error { Text(language.systemText(error)).foregroundStyle(.red) }
                Button(language.text("close")) { dismiss() }.disabled(model.state.saving)
            }.padding(24).frame(maxWidth: 640).frame(maxWidth: .infinity)
        }
        .background(LookStyle.background.ignoresSafeArea()).tint(LookStyle.accent)
        .confirmationDialog(language.text("ui_204"), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(language.text("ui_197"), role: .destructive) { model.send(LookActionDelete(id: recordID)) }
            Button(language.text("cancel"), role: .cancel) {}
        }
        .interactiveDismissDisabled(model.state.saving)
    }
}
