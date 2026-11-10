import SwiftUI
import Shared

struct StudioSkillDetailView: View {
    @ObservedObject var model: StudioModel
    let skillID: String
    let onGuide: () -> Void
    @State private var confirm = false
    var body: some View {
        StudioPage(title: "Kỹ năng đã lưu", model: model) {
            if let skill = model.state.data.skills.first(where: { $0.id == skillID }) {
                Text(skill.title).font(.largeTitle.bold())
                Text("\(skill.steps.count) bước · dữ liệu mẫu trên máy").foregroundStyle(.secondary)
                ForEach(Array(skill.steps.enumerated()), id: \.offset) { index, step in
                    Text("\(index + 1). \(step)").frame(maxWidth: .infinity, alignment: .leading)
                        .padding(18).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 20))
                }
                LookPrimaryButton(title: "Bắt đầu làm theo", action: onGuide)
                Button("Xóa kỹ năng", role: .destructive) { confirm = true }.frame(minHeight: 44)
            } else if !model.state.loading { Text("Kỹ năng này không còn trên máy.") }
        }
        .confirmationDialog("Xóa kỹ năng khỏi Lịch sử?", isPresented: $confirm, titleVisibility: .visible) {
            Button("Xóa", role: .destructive) { model.send(StudioActionDeleteSkill(id: skillID)) }
            Button("Hủy", role: .cancel) {}
        }
    }
}
