import SwiftUI
import Shared

struct StudioSavedSkillView: View {
    @ObservedObject var model: StudioModel
    let skillID: String
    let onGuide: () -> Void
    let onHistory: () -> Void
    var body: some View {
        StudioPage(title: "Đã lưu kỹ năng", model: model) {
            if model.state.data.skills.contains(where: { $0.id == skillID }) {
                Image(systemName: "checkmark.circle.fill").font(.system(size: 72)).foregroundStyle(LookStyle.accent).padding(.vertical, 32)
                Text("Đã lưu một cách làm hay.").font(.largeTitle.bold())
                Text("Kỹ năng mẫu nằm trên máy này. Mở lại để làm theo hoặc xem trong Lịch sử.")
                LookPrimaryButton(title: "Làm theo ngay", action: onGuide)
                LookPrimaryButton(title: "Xem Lịch sử", action: onHistory)
            } else if !model.state.loading { Text("Không tìm thấy kỹ năng đã lưu.") }
        }
    }
}
