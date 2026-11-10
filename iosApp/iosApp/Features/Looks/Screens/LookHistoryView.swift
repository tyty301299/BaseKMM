import SwiftUI
import Shared

struct LookHistoryView: View {
    @ObservedObject var model: LookModel
    @ObservedObject var studio: StudioModel
    let onRecord: (String) -> Void
    let onCamera: () -> Void
    let onSkill: (String) -> Void

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 20) {
                Text("Lịch sử").font(.headline)
                Text("Những lần bạn thích.").font(.largeTitle.bold())
                Text("\(model.state.records.count + studio.state.data.skills.count) mục · lưu trên máy").font(.subheadline).foregroundStyle(.secondary)
                Picker("Loại đã lưu", selection: Binding(get: { model.state.filter }, set: { model.send(LookActionFilter(kind: $0)) })) {
                    Text("Tất cả").tag("all"); Text("Thử đồ").tag("tryon"); Text("Bộ phối").tag("outfit"); Text("Kỹ năng").tag("skill")
                }.pickerStyle(.menu)
                if model.state.loading { ProgressView("Đang mở Lịch sử…") }
                if let error = model.state.error {
                    Text(error).foregroundStyle(.red)
                    Button("Tải lại") { model.send(LookActionRetry.shared) }
                }
                if !model.state.loading && !studio.state.loading && model.state.filteredRecords.isEmpty && ((model.state.filter != "all" && model.state.filter != "skill") || studio.state.data.skills.isEmpty) {
                    Image(systemName: "clock.arrow.circlepath").font(.largeTitle).padding(.top, 40)
                    Text("Chưa có mục đã lưu trong nhóm này.").foregroundStyle(.secondary)
                    LookPrimaryButton(title: "Thử bộ đồ đầu tiên", action: onCamera)
                }
                if model.state.filter == "all" || model.state.filter == "skill" {
                    ForEach(studio.state.data.skills, id: \.id) { skill in
                        Button { onSkill(skill.id) } label: {
                            HStack(spacing: 16) {
                                Image(systemName: "book").foregroundStyle(LookStyle.accent)
                                VStack(alignment: .leading, spacing: 6) {
                                    Text(skill.title).font(.headline)
                                    Text("Kỹ năng mẫu · \(skill.steps.count) bước").font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer(); Image(systemName: "chevron.right")
                            }.padding(20).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 22))
                        }.buttonStyle(.plain)
                    }
                }
                ForEach(model.state.filteredRecords, id: \.id) { record in
                    Button { onRecord(record.id) } label: {
                        HStack(spacing: 16) {
                            LookMannequin(preset: record.preset, before: record.before, flipped: record.flipped)
                                .frame(width: 56, height: 72).background(.white.opacity(0.75), in: RoundedRectangle(cornerRadius: 10))
                            VStack(alignment: .leading, spacing: 8) {
                                Text(record.title).font(.headline)
                                Text(record.kind == "tryon" ? "Thử đồ mẫu" : "Bộ phối mẫu").font(.caption).foregroundStyle(.secondary)
                                Text(Date(timeIntervalSince1970: Double(record.createdAt) / 1000), style: .date).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer(); Image(systemName: "chevron.right")
                        }.padding(12).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 20))
                    }.buttonStyle(.plain)
                }
            }.padding(24).frame(maxWidth: 640).frame(maxWidth: .infinity)
        }
    }
}
