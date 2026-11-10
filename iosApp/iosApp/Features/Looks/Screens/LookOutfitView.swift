import SwiftUI
import Shared

struct LookOutfitView: View {
    @ObservedObject var model: LookModel
    let onCamera: () -> Void
    let onService: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("AI OUTFITS · CONCEPT").font(.caption).foregroundStyle(.secondary)
            Text("Mặc đúng gu.\nĐúng dịp.").font(.largeTitle.bold())
            Text("Bộ phối minh họa để thử giao diện. Kết nối SHADOW để dùng tủ đồ thật.")
                .font(.subheadline).foregroundStyle(.secondary)
            LookMannequin(preset: model.state.preset).frame(height: 290)
                .frame(maxWidth: .infinity).background(Color(red: 0.81, green: 0.83, blue: 0.77), in: RoundedRectangle(cornerRadius: 28))
            LookPresetPicker(model: model)
            Picker("Dịp sử dụng", selection: Binding(get: { model.state.occasion }, set: { model.send(LookActionSelectOccasion(occasion: $0)) })) {
                ForEach(["Đi làm", "Đi chơi", "Hẹn hò"], id: \.self) { Text($0).tag($0) }
            }.pickerStyle(.menu)
            LookPrimaryButton(title: "Mở camera thử đồ", action: onCamera)
            LookPrimaryButton(title: !model.state.outfitSaved ? "Lưu bộ phối mẫu" : "Đã lưu trên máy", disabled: model.state.saving || model.state.outfitSaved) {
                model.send(LookActionSaveOutfit.shared)
            }
            if let error = model.state.error { Text(error).foregroundStyle(.red) }
            Button("Kết nối tủ đồ & AI thật", action: onService).frame(minHeight: 44)
        }
    }

}
