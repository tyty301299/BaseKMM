import SwiftUI
import Shared

struct StudioLearningView: View {
    @ObservedObject var model: StudioModel
    let onTeach: () -> Void
    let onSkill: (String) -> Void
    let onService: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            Text("Bạn làm mẫu.\nSHADOW học cùng.").font(.largeTitle.bold())
            Text("Thử trọn luồng dạy và làm theo với bài mẫu. Không quay video hay phân tích AI trong chế độ này.").foregroundStyle(.secondary)
            LookPrimaryButton(title: "Dạy một kỹ năng · demo", disabled: model.busy, action: onTeach)
            Button("Dùng dịch vụ AI thật", action: onService).frame(minHeight: 44)
            Text("Kỹ năng đã lưu").font(.title2.bold())
            if model.state.loading { ProgressView("Đang tải kỹ năng…") }
            if let error = model.state.error {
                Text(error).foregroundStyle(.red)
                Button("Tải lại") { model.send(StudioActionRetry.shared) }
            }
            if model.state.data.skills.isEmpty { Text("Chưa có kỹ năng. Bắt đầu với một bài mẫu.").foregroundStyle(.secondary) }
            ForEach(model.state.data.skills, id: \.id) { skill in
                Button { onSkill(skill.id) } label: {
                    HStack(spacing: 16) {
                        Image(systemName: "book").foregroundStyle(LookStyle.accent)
                        VStack(alignment: .leading, spacing: 6) {
                            Text(skill.title).font(.headline)
                            Text("\(skill.steps.count) bước · lưu trên máy").font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer(); Image(systemName: "chevron.right")
                    }.padding(20).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 24))
                }.buttonStyle(.plain)
            }
        }
    }
}
