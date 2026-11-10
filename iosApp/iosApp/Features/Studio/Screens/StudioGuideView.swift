import SwiftUI
import Shared

struct StudioGuideView: View {
    @ObservedObject var model: StudioModel
    let onAsk: () -> Void
    let onDone: () -> Void
    var body: some View {
        StudioPage(title: "Làm theo", model: model) {
            if let skill = model.state.activeSkill {
                if model.state.completed {
                    Image(systemName: "checkmark.seal.fill").font(.system(size: 72)).foregroundStyle(LookStyle.accent)
                    Text("Bạn đã hoàn thành.").font(.largeTitle.bold())
                    Text("Hoàn thành luồng hướng dẫn mẫu. AI chưa đánh giá thao tác thật.")
                    LookPrimaryButton(title: "Về Học & làm", action: onDone)
                } else {
                    Text(skill.title).font(.title.bold())
                    Text("BƯỚC \(model.state.stepIndex + 1) / \(skill.steps.count)").font(.caption).foregroundStyle(LookStyle.accent)
                    ProgressView(value: Double(model.state.stepIndex + 1), total: Double(skill.steps.count))
                    StudioInstructionIllustration()
                    Text(model.state.currentStep).font(.title2.bold())
                    Text("Hướng dẫn mẫu. Không dùng camera để đánh giá thao tác.").foregroundStyle(.secondary)
                    LookPrimaryButton(title: "Tôi đã làm xong bước này") { model.send(StudioActionNextStep.shared) }
                    Button("Bước trước") { model.send(StudioActionPreviousStep.shared) }.disabled(model.state.stepIndex == 0).frame(minHeight: 44)
                    LookPrimaryButton(title: "Hỏi về bước này", action: onAsk)
                }
            } else if !model.state.loading { Text("Không tìm thấy kỹ năng. Hãy quay lại danh sách.") }
        }
    }
}
