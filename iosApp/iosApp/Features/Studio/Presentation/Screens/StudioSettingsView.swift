import SwiftUI
import Shared

struct StudioSettingsView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    @ObservedObject var shadow: ShadowModel
    let onService: () -> Void
    let onComponents: () -> Void
    @State private var confirm = false

    var body: some View {
        Form {
            Section {
                ShadowConnectionStatus(model: shadow, onSettings: onService)
                if shadow.state.connected { Text(shadow.state.endpoint).font(.caption).foregroundStyle(.secondary) }
                if let error = shadow.state.error { Text(language.systemText(error)).font(.caption).foregroundStyle(.red) }
                Button(action: onService) {
                    HStack(spacing: 14) {
                        settingIcon("network")
                        VStack(alignment: .leading, spacing: 4) {
                            Text(language.text("connection_title")).foregroundStyle(.primary)
                            Text(language.text("auto_connect")).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    }.padding(.vertical, 6)
                }
            } header: { Text(language.text("connect")) }
            .listRowBackground(LookStyle.panel)

            Section {
                LanguageSetting()
            } header: { Text(language.text("language_title")) }
            .listRowBackground(LookStyle.panel)

            Section {
                Toggle(isOn: Binding(get: { model.state.data.settings.dark }, set: {
                    model.send(StudioActionAppearance(dark: $0, glass: model.state.data.settings.glass))
                })) { Label(language.text("dark_appearance"), systemImage: "moon") }
                Toggle(isOn: Binding(get: { model.state.data.settings.glass }, set: {
                    model.send(StudioActionAppearance(dark: model.state.data.settings.dark, glass: $0))
                })) { Label(language.text("glass_effect"), systemImage: "square.on.square") }
            } header: { Text(language.text("appearance_title")) }
            .listRowBackground(LookStyle.panel)

            Section {
                Button(role: .destructive) { confirm = true } label: {
                    Label(language.text("clear_all_samples"), systemImage: "trash")
                }
            } header: { Text(language.text("local_data")) }
              footer: { Text(language.text("local_data_description")) }
            .listRowBackground(LookStyle.panel)

            Section {
                Button(action: onComponents) {
                    HStack {
                        Label(language.text("view_components"), systemImage: "square.grid.2x2")
                        Spacer()
                        Image(systemName: "chevron.right").font(.caption)
                    }.foregroundStyle(.secondary)
                }
            }.listRowBackground(LookStyle.panel)
            if model.busy { ProgressView(language.systemText(model.state.busy)) }
            if let error = model.state.error { Text(language.systemText(error)).foregroundStyle(.red) }
        }
        .disabled(model.busy)
        .scrollContentBackground(.hidden)
        .background(LookStyle.background.ignoresSafeArea())
        .tint(LookStyle.accent)
        .navigationTitle(language.text("settings_title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
        .confirmationDialog(language.text("clear_samples_question"), isPresented: $confirm, titleVisibility: .visible) {
            Button(language.text("clear_samples"), role: .destructive) { model.send(StudioActionClearData.shared) }
            Button(language.text("cancel"), role: .cancel) {}
        }
    }

    private func settingIcon(_ name: String) -> some View {
        Image(systemName: name).font(.headline).foregroundStyle(LookStyle.accent)
            .frame(width: 36, height: 36)
            .background(LookStyle.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
    }
}
