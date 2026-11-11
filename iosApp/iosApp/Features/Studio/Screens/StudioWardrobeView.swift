import SwiftUI
import Shared

struct StudioWardrobeView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    let onAdd: () -> Void
    let onItem: (String) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text(language.text("ui_181")).font(.largeTitle.bold())
            Text(language.text("ui_174")).foregroundStyle(.secondary)
            if model.state.loading { ProgressView(language.text("ui_225")) }
            if let error = model.state.error {
                Text(language.systemText(error)).foregroundStyle(.red)
                Button(language.text("refresh")) { model.send(StudioActionRetry.shared) }
            }
            if !model.state.loading && model.state.data.items.isEmpty {
                Image(systemName: "tshirt").font(.system(size: 64)).foregroundStyle(.secondary).padding(.vertical, 28)
                Text(language.text("ui_182")).font(.title2.bold())
                Text(language.text("ui_162"))
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    filter("all", title: language.text("filter_all"), symbol: "square.grid.2x2")
                    filter("Áo", title: language.text("filter_tops"), symbol: "tshirt")
                    filter("Áo khoác", title: language.text("filter_jackets"), symbol: "jacket")
                }.padding(6)
            }
            .background(LookStyle.panel, in: Capsule())
            .overlay(Capsule().strokeBorder(Color.primary.opacity(0.08), lineWidth: 1))
            .disabled(model.busy)

            if !model.state.data.items.isEmpty && model.state.filteredItems.isEmpty {
                Text(language.text("ui_040")).foregroundStyle(.secondary)
            }
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 140))], spacing: 20) {
                ForEach(model.state.filteredItems, id: \.id) { item in
                    Button { onItem(item.id) } label: {
                        VStack(alignment: .leading, spacing: 10) {
                            StudioGarmentImage(preset: item.preset).frame(height: 180).frame(maxWidth: .infinity)
                                .clipShape(RoundedRectangle(cornerRadius: 24))
                            Text(item.name).font(.headline)
                            Text(language.format("sample_category", String(item.category))).font(.caption).foregroundStyle(.secondary)
                        }
                    }.buttonStyle(.plain)
                }
            }
            LookPrimaryButton(title: language.text("add_garment"), disabled: model.busy, action: onAdd)
        }
    }
    private func filter(_ value: String, title: String, symbol: String) -> some View {
        let selected = model.state.wardrobeFilter == value
        return Button { model.send(StudioActionWardrobeFilter(value: value)) } label: {
            Label(title, systemImage: symbol)
                .font(.subheadline.weight(selected ? .semibold : .medium))
                .padding(.horizontal, 16).frame(minHeight: 44)
                .foregroundStyle(selected ? Color.black : Color.secondary)
                .background(selected ? LookStyle.highlight : Color.clear, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

}
