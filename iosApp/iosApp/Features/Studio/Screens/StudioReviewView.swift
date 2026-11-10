import SwiftUI
import Shared

struct StudioReviewView: View {
    @ObservedObject var model: StudioModel
    var body: some View {
        StudioPage(title: "Duyệt kỹ năng", model: model) {
            Text("Bạn xác nhận trước khi lưu.").font(.largeTitle.bold())
            TextField("Tên kỹ năng", text: Binding(get: { model.state.title }, set: { model.send(StudioActionLessonTitle(value: $0)) }))
                .textFieldStyle(.roundedBorder).accessibilityLabel("Tên kỹ năng")
            ForEach(Array(model.state.draftSteps.enumerated()), id: \.offset) { index, _ in
                VStack(alignment: .leading, spacing: 10) {
                    Text("Bước \(index + 1)").font(.headline).foregroundStyle(LookStyle.accent)
                    TextField("Nội dung bước", text: Binding(get: { model.state.draftSteps[index] }, set: { model.send(StudioActionEditStep(index: Int32(index), value: $0)) }), axis: .vertical)
                        .lineLimit(2...6).textFieldStyle(.roundedBorder).accessibilityLabel("Nội dung bước \(index + 1)")
                }.padding(18).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 22))
            }
            LookPrimaryButton(title: "Lưu kỹ năng trên máy", disabled: !model.state.canSaveSkill || model.state.savedSkillId != nil) { model.send(StudioActionSaveSkill.shared) }
        }
    }
}
