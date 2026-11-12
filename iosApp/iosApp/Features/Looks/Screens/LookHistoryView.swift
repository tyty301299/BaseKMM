import SwiftUI
import Shared

struct LookHistoryView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: LookModel
    @ObservedObject var studio: StudioModel
    let onRecord: (String) -> Void
    let onCamera: () -> Void
    let onSkill: (String) -> Void

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 20) {
                Text(language.text("history_tab")).font(.headline)
                Text(language.text("ui_142")).font(.largeTitle.bold())
                Text(language.format("saved_items_count", String(model.state.records.count + studio.state.data.skills.count))).font(.subheadline).foregroundStyle(.secondary)
                Picker(language.text("ui_105"), selection: Binding(get: { model.state.filter }, set: { model.send(LookActionFilter(kind: $0)) })) {
                    Text(language.text("filter_all")).tag("all"); Text(language.text("ui_169")).tag("tryon"); Text(language.text("ui_021")).tag("outfit"); Text(language.text("ui_099")).tag("skill"); Text("Đã làm").tag("guide")
                }.pickerStyle(.menu)
                if model.state.loading { ProgressView(language.text("ui_222")) }
                if let error = model.state.error {
                    Text(language.systemText(error)).foregroundStyle(.red)
                    Button(language.text("refresh")) { model.send(LookActionRetry.shared) }
                }
                if !model.state.loading && !studio.state.loading && model.state.filteredRecords.isEmpty && ((model.state.filter != "all" && model.state.filter != "skill") || studio.state.data.skills.isEmpty) {
                    Image(systemName: "clock.arrow.circlepath").font(.largeTitle).padding(.top, 40)
                    Text(language.text("ui_039")).foregroundStyle(.secondary)
                    LookPrimaryButton(title: language.text("ui_165"), action: onCamera)
                }
                if model.state.filter == "all" || model.state.filter == "skill" {
                    ForEach(studio.state.data.skills, id: \.id) { skill in
                        Button { onSkill(skill.id) } label: {
                            HStack(spacing: 16) {
                                Image(systemName: "book").foregroundStyle(LookStyle.accent)
                                VStack(alignment: .leading, spacing: 6) {
                                    Text(skill.title).font(.headline)
                                    Text(language.format("sample_skill_steps", String(skill.steps.count))).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer(); Image(systemName: "chevron.right")
                            }.padding(20).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 22))
                        }.buttonStyle(.plain)
                    }
                }
                ForEach(model.state.filteredRecords, id: \.id) { record in
                    Button { onRecord(record.id) } label: {
                        HStack(spacing: 16) {
                            if record.isGuide {
                                Image(systemName: record.preset.contains("quan") ? "figure.stand" : record.preset.contains("ao") ? "tshirt" : "checkmark.circle")
                                    .font(.title2).foregroundStyle(LookStyle.accent).frame(width: 56, height: 72)
                                    .background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 10))
                            } else {
                                LookMannequin(preset: record.preset, before: record.before, flipped: record.flipped)
                                    .frame(width: 56, height: 72).background(.white.opacity(0.75), in: RoundedRectangle(cornerRadius: 10))
                            }
                            VStack(alignment: .leading, spacing: 8) {
                                Text(record.title).font(.headline)
                                Text(record.isGuide ? (record.kind == "guide-manual" ? "Hoàn thành · hướng dẫn mẫu" : record.kind == "guide-demo" ? "Hoàn thành · demo" : "Hoàn thành · AI") : record.kind == "tryon" ? language.text("ui_170") : language.text("ui_023")).font(.caption).foregroundStyle(.secondary)
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
