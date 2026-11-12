import SwiftUI
import Shared

struct LookHomeView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: LookModel
    @ObservedObject var studio: StudioModel
    @ObservedObject var shadow: ShadowModel
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
        // Native TabView supplies Liquid Glass on iOS 26 and the system material on older iOS.
        TabView(selection: $tab) {
            page(0).tabItem { Label(language.text("outfit_tab"), systemImage: "sparkles") }.tag(0)
            page(1).tabItem { Label(language.text("wardrobe_tab"), systemImage: "tshirt") }.tag(1)
            page(2).tabItem { Label(language.text("learn_tab"), systemImage: "book") }.tag(2)
            LookHistoryView(model: model, studio: studio, onRecord: onRecord, onCamera: onCamera, onSkill: onSkill)
                .background(LookStyle.background.ignoresSafeArea()).tabItem { Label(language.text("history_tab"), systemImage: "clock.arrow.circlepath") }.tag(3)
        }
        .tint(LookStyle.accent)
        .toolbar(.hidden, for: .navigationBar)
    }

    private func page(_ section: Int) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                HStack {
                    HStack(spacing: 9) {
                        Image(systemName: "sparkle").foregroundStyle(LookStyle.accent)
                        Text("SHADOW").font(.subheadline.weight(.heavy)).tracking(2)
                    }
                    Spacer()
                    Button(action: onSettings) {
                        Image(systemName: "slider.horizontal.3").frame(width: 44, height: 44)
                    }
                    .modifier(LookGlass())
                    .accessibilityLabel(language.text("settings_title"))
                }
                ShadowConnectionStatus(model: shadow, onSettings: onSettings)
                if section == 3 {
                    LookHistoryView(model: model, studio: studio, onRecord: onRecord, onCamera: onCamera, onSkill: onSkill)
                } else if section == 2 {
                    StudioLearningView(model: studio, onTeach: onTeach, onSkill: onSkill, onService: onService)
                } else if section == 1 {
                    StudioWardrobeView(model: studio, onAdd: onAdd, onItem: onItem)
                } else {
                    outfitHome
                }
            }
            .padding(.horizontal, 24).padding(.top, 12).padding(.bottom, 28)
            .frame(maxWidth: 640).frame(maxWidth: .infinity)
        }
        .background(LookStyle.background.ignoresSafeArea())
    }

    private var outfitHome: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("AI OUTFITS").font(.caption.weight(.semibold)).tracking(2).foregroundStyle(.secondary)
            Text(language.text("outfit_heading")).font(.largeTitle.weight(.bold)).tracking(-1)
            HStack(alignment: .top, spacing: 12) {
                garment("coat", name: language.text("sample_coat"))
                garment("blouse", name: language.text("sample_blouse"))
            }
            LookPrimaryButton(title: language.text("outfit_today"), action: onService)
            Text(language.text("quick_start")).font(.headline)
            HStack(spacing: 12) {
                quickAction(language.text("person_photo_title"), symbol: "person.crop.rectangle", action: onService)
                quickAction(language.text("add_garment"), symbol: "plus", action: onAdd)
            }.fixedSize(horizontal: false, vertical: true)
            Button(action: onService) {
                HStack(spacing: 12) {
                    Image(systemName: "network").foregroundStyle(LookStyle.accent)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(language.text("live_service")).font(.subheadline.bold())
                        Text(language.text("live_service_detail")).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "arrow.up.right")
                }.padding(18).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 22))
            }.buttonStyle(.plain)
            Button(language.text("sample_flow"), action: onOccasion).frame(minHeight: 44)
            Text(language.text("sample_notice")).font(.caption).foregroundStyle(.secondary)
        }
    }

    private func garment(_ preset: String, name: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            GeometryReader { geometry in
                StudioGarmentImage(preset: preset)
                    .frame(width: geometry.size.width, height: geometry.size.height).clipped()
            }
            .aspectRatio(0.82, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 22))
            Text(name).font(.caption.weight(.semibold))
        }.frame(maxWidth: .infinity)
    }

    private func quickAction(_ title: String, symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 14) {
                Image(systemName: symbol).font(.title3).foregroundStyle(LookStyle.accent)
                Text(title).font(.subheadline.weight(.medium)).fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading).padding(18)
            .background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 22))
        }.buttonStyle(.plain)
    }
}
