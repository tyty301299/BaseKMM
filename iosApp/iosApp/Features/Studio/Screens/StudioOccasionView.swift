import SwiftUI
import Shared

struct StudioOccasionView: View {
    @ObservedObject var model: StudioModel
    @ObservedObject var looks: LookModel
    let onGenerate: () -> Void
    let onAdd: () -> Void
    var body: some View {
        StudioPage(title: "Bạn sắp đi đâu?", model: model) {
            Text("Đúng dịp. Đúng cảm giác.").font(.largeTitle.bold())
            Picker("Dịp sử dụng", selection: Binding(get: { looks.state.occasion }, set: { looks.send(LookActionSelectOccasion(occasion: $0)) })) {
                ForEach(["Đi làm", "Đi chơi", "Hẹn hò"], id: \.self) { Text($0).tag($0) }
            }.pickerStyle(.segmented)
            TextField("Mong muốn thêm (tối đa 300 ký tự)", text: Binding(get: { model.state.outfitNotes }, set: { model.send(StudioActionOutfitNotes(value: $0)) }), axis: .vertical)
                .lineLimit(3...5).textFieldStyle(.roundedBorder)
            Text("Gợi ý mẫu chỉ lấy từ tủ đồ local, chưa xử lý yêu cầu bằng AI.").foregroundStyle(.secondary)
            if model.state.data.items.isEmpty {
                Text("Tủ đồ đang trống. Thêm một món trước nhé.")
                LookPrimaryButton(title: "Thêm trang phục", action: onAdd)
            } else { LookPrimaryButton(title: "Tạo gợi ý mẫu", action: onGenerate) }
        }
    }
}
