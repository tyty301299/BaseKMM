import SwiftUI
import Shared

struct LookPresetPicker: View {
    @ObservedObject var model: LookModel

    var body: some View {
        HStack(spacing: 12) {
            preset("coat", "Xanh rêu")
            preset("blouse", "Cam đào")
        }
    }

    private func preset(_ key: String, _ label: String) -> some View {
        Button { model.send(LookActionSelectPreset(preset: key)) } label: {
            Label(label, systemImage: "tshirt").font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity, minHeight: 52)
                .modifier(LookGlass())
                .overlay(Capsule().stroke(model.state.preset == key ? LookStyle.accent : .clear, lineWidth: 2))
        }.buttonStyle(.plain).disabled(model.state.saving)
            .accessibilityAddTraits(model.state.preset == key ? .isSelected : [])
    }
}
