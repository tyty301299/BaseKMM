import SwiftUI
import Shared

struct LookHomeView: View {
    @ObservedObject var model: LookModel
    @ObservedObject var studio: StudioModel
    @Binding var tab: Int
    let onCamera: () -> Void
    let onRecord: (String) -> Void
    let onService: () -> Void
    let onAdd: () -> Void
    let onItem: (String) -> Void
    let onOccasion: () -> Void
    let onTeach: () -> Void
    let onSkill: (String) -> Void
    let onSettings: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            if tab == 3 {
                LookHistoryView(model: model, studio: studio, onRecord: onRecord, onCamera: onCamera, onSkill: onSkill)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        HStack {
                            Text("SHADOW").font(.headline)
                            Spacer()
                            Button(action: onSettings) { Image(systemName: "slider.horizontal.3").frame(width: 44, height: 44) }
                                .accessibilityLabel("Tùy chọn")
                        }
                        if tab == 2 {
                            StudioLearningView(model: studio, onTeach: onTeach, onSkill: onSkill, onService: onService)
                        } else if tab == 1 {
                            StudioWardrobeView(model: studio, onAdd: onAdd, onItem: onItem)
                        } else {
                            VStack(alignment: .leading, spacing: 24) {
                                Text("AI OUTFITS").font(.caption).foregroundStyle(.secondary)
                                Text("Mặc đúng gu.\nĐúng dịp.").font(.largeTitle.bold())
                                LookMannequin().frame(height: 300).frame(maxWidth: .infinity)
                                    .background(Color.gray.opacity(0.25), in: RoundedRectangle(cornerRadius: 28))
                                LookPrimaryButton(title: "Phối đồ cho hôm nay", action: onOccasion)
                                LookPrimaryButton(title: "Mở camera thử mẫu", action: onCamera)
                                Button("Thêm trang phục", action: onAdd).frame(minHeight: 44)
                            }
                        }
                    }.padding(24).frame(maxWidth: 640).frame(maxWidth: .infinity)
                }
            }
            HStack(spacing: 0) {
                tabButton("Phối đồ", "sparkles", 0)
                tabButton("Tủ đồ", "tshirt", 1)
                tabButton("Học & làm", "book", 2)
                tabButton("Lịch sử", "clock.arrow.circlepath", 3)
            }
            .padding(8).modifier(LookGlass()).padding(.horizontal, 16).padding(.bottom, 8)
        }
        .background(LookStyle.background.ignoresSafeArea())
        .tint(LookStyle.accent).toolbar(.hidden, for: .navigationBar)
    }

    private func tabButton(_ title: String, _ icon: String, _ value: Int) -> some View {
        Button { tab = value } label: {
            VStack(spacing: 5) { Image(systemName: icon); Text(title).font(.caption2) }
                .frame(maxWidth: .infinity, minHeight: 48)
                .foregroundStyle(tab == value ? LookStyle.accent : .secondary)
                .background(tab == value ? Color.white.opacity(0.08) : .clear, in: Capsule())
        }.buttonStyle(.plain).accessibilityAddTraits(tab == value ? .isSelected : [])
    }
}
