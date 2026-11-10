import SwiftUI
import Shared

final class ShadowModel: ObservableObject {
    @Published private(set) var state: ShadowState
    var onMessage: ((UiMessage) -> Void)?
    private var messageSubscription: StoreSubscription?
    private let store: ShadowStore
    private var subscription: StoreSubscription?

    init(store: ShadowStore) {
        self.store = store
        state = store.currentState
        messageSubscription = store.observeMessages { [weak self] in self?.onMessage?($0) }
        subscription = store.observeState { [weak self] in self?.state = $0 }
    }

    func send(_ action: ShadowAction) { store.dispatch(action: action) }

    deinit {
        messageSubscription?.cancel()
        subscription?.cancel()
        store.close()
    }
}

private enum ShadowStyle {
    static let background = Color(red: 11/255, green: 11/255, blue: 15/255)
    static let accent = Color(red: 1, green: 210/255, blue: 63/255)
}

struct HomeView: View {
    @EnvironmentObject private var feedback: StudioFeedback
    @StateObject private var model: ShadowModel
    @AppStorage("shadow.endpoint") private var savedEndpoint = ""
    @State private var endpoint = ""
    @State private var accessCode = ""
    @State private var tab = 0
    @State private var showConnection = false
    @State private var event = "Đi làm"
    @State private var notes = ""
    @Environment(\.openURL) private var openURL

    init(container: AppContainer, initialTab: Int = 0) {
        _tab = State(initialValue: initialTab)
        _model = StateObject(wrappedValue: ShadowModel(store: container.shadowStore()))
    }

    private var busy: Bool { !model.state.busy.isEmpty }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    Text(model.state.modeLabel).font(.caption.weight(.semibold))
                        .foregroundStyle(ShadowStyle.accent)
                    if busy { ProgressView(model.state.busy).frame(maxWidth: .infinity, alignment: .leading) }
                    if let error = model.state.error {
                        Text(error).foregroundStyle(.red).accessibilityLabel("Lỗi. \(error)")
                    }
                    if showConnection || !model.state.connected { connection }
                    if tab == 0 { learning } else { wardrobe }
                }
                .frame(maxWidth: 680, alignment: .leading)
                .padding(24)
                .frame(maxWidth: .infinity)
            }
            Divider()
            HStack(spacing: 16) {
                tabButton("Học & làm theo", symbol: "hand.draw", value: 0)
                tabButton("Tủ đồ", symbol: "tshirt", value: 1)
            }
            .padding(.horizontal, 16).padding(.vertical, 10)
        }
        .modifier(StudioFeedbackOverlay())
        .background(ShadowStyle.background)
        .preferredColorScheme(.dark)
        .tint(ShadowStyle.accent)
        .navigationTitle("SHADOW")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button("Kết nối") { showConnection.toggle() }
            }
        }
        .onAppear {
            model.onMessage = feedback.show
            if endpoint.isEmpty { endpoint = savedEndpoint }
        }
        .onChange(of: model.state.endpoint) { value in
            if !value.isEmpty {
                savedEndpoint = value
                accessCode = ""
                showConnection = false
            }
        }
        .sheet(isPresented: Binding(
            get: { model.state.lesson != nil },
            set: { if !$0 { model.send(ShadowActionCloseLesson.shared) } }
        )) {
            ShadowLessonView(model: model)
                .modifier(StudioFeedbackOverlay())
                .preferredColorScheme(.dark).tint(ShadowStyle.accent)
        }
    }

    private var connection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Kết nối với SHADOW").font(.title2.bold())
            Text("Dùng địa chỉ HTTPS của nhóm để truy cập kỹ năng và tủ đồ.")
                .font(.subheadline).foregroundStyle(.secondary)
            TextField("https://shadow.example.com", text: $endpoint)
                .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                .textFieldStyle(.roundedBorder).accessibilityLabel("Địa chỉ máy chủ")
            SecureField("Mã truy cập, nếu máy chủ yêu cầu", text: $accessCode)
                .textFieldStyle(.roundedBorder)
            primaryButton("Kết nối máy chủ", disabled: endpoint.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || busy) {
                model.send(ShadowActionConnect(endpoint: endpoint, code: accessCode))
            }
        }
    }

    private var learning: some View {
        VStack(alignment: .leading, spacing: 24) {
            VStack(alignment: .leading, spacing: 10) {
                Text("Bạn làm mẫu.\nSHADOW học cùng.").font(.largeTitle.bold())
                Text("Ghi lại cách bạn làm, xem các bước AI đã học và hỏi khi cần hướng dẫn.")
                    .foregroundStyle(.secondary)
            }
            webButton("Dạy kỹ năng bằng camera", detail: "Mở bản web · quay và nói để AI chia bước", route: "teach")
            HStack {
                Text("Kỹ năng đã học").font(.title2.bold())
                Spacer()
                Button("Tải lại") { model.send(ShadowActionRefresh.shared) }
                    .disabled(!model.state.connected || busy)
            }
            if model.state.skills.isEmpty {
                Text(model.state.connected ? "Chưa có kỹ năng. Hãy dạy SHADOW thao tác đầu tiên của bạn." : "Kết nối máy chủ để xem các kỹ năng đã học.")
                    .foregroundStyle(.secondary)
            }
            ForEach(model.state.skills, id: \.id) { skill in
                Button {
                    model.send(ShadowActionOpenLesson(id: skill.id))
                } label: {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(alignment: .firstTextBaseline) {
                            Text(skill.title).font(.headline)
                            Spacer()
                            Image(systemName: "chevron.right").accessibilityHidden(true)
                        }
                        Text("\(skill.steps) bước" + (skill.teacher.isEmpty ? "" : " · Học từ \(skill.teacher)"))
                            .font(.caption).foregroundStyle(ShadowStyle.accent)
                        if !skill.summary.isEmpty { Text(skill.summary).font(.subheadline).foregroundStyle(.secondary) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 12)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain).disabled(busy)
                Divider()
            }
        }
    }

    private var wardrobe: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Hôm nay,\nbạn muốn mặc gì?").font(.largeTitle.bold())
            Text("Gợi ý từ những món đồ có trong tủ của bạn, phù hợp với dịp sắp tới.")
                .foregroundStyle(.secondary)
            webButton("Thêm ảnh & quản lý tủ đồ", detail: "Mở bản web · thêm ảnh đồ thật, chỉnh nhãn và mặc thử", route: "wardrobe")
            HStack {
                Text("Tủ đồ của bạn · \(model.state.wardrobe.count)").font(.title2.bold())
                Spacer()
                Button("Tải lại") { model.send(ShadowActionRefresh.shared) }
                    .disabled(!model.state.connected || busy)
            }
            if model.state.wardrobe.isEmpty {
                Text("Thêm ảnh trang phục của bạn trên bản web, rồi tải lại để bắt đầu phối đồ.")
                    .foregroundStyle(.secondary)
            }
            ForEach(model.state.wardrobe, id: \.id) { item in
                HStack {
                    Text(item.name)
                    Spacer()
                    if item.demo { Text("Đồ mẫu").font(.caption).foregroundStyle(ShadowStyle.accent) }
                }
                Divider()
            }
            Text("Bạn sắp đi đâu?").font(.headline)
            Picker("Dịp sử dụng", selection: $event) {
                Text("Đi làm").tag("Đi làm")
                Text("Đi chơi").tag("Đi chơi")
                Text("Hẹn hò").tag("Hẹn hò")
            }.pickerStyle(.segmented).disabled(busy)
            TextField("Mong muốn thêm (không bắt buộc)", text: $notes, axis: .vertical)
                .lineLimit(2...4).textFieldStyle(.roundedBorder).disabled(busy)
            Text("\(notes.utf16.count)/300").font(.caption).foregroundStyle(.secondary)
            primaryButton("Gợi ý phối đồ", disabled: !model.state.connected || model.state.wardrobe.isEmpty || busy || notes.utf16.count > 300) {
                model.send(ShadowActionSuggest(event: event, notes: notes))
            }
            if let result = model.state.suggestions {
                Text("Gợi ý cho: \(model.state.suggestionsEvent)").font(.headline)
                Text(model.state.suggestionsLabel).font(.caption.bold()).foregroundStyle(ShadowStyle.accent)
                if result.outfits.isEmpty { Text("Chưa có bộ phối phù hợp. Thử đổi dịp hoặc thêm trang phục.") }
                ForEach(Array(result.outfits.enumerated()), id: \.offset) { _, outfit in
                    VStack(alignment: .leading, spacing: 10) {
                        Text(outfit.title).font(.title3.bold())
                        Text(outfit.items.joined(separator: " + ")).foregroundStyle(ShadowStyle.accent)
                        Text(outfit.reason)
                        if !outfit.tips.isEmpty { Text(outfit.tips).foregroundStyle(.secondary) }
                    }.padding(.vertical, 12)
                    Divider()
                }
            }
        }
    }

    private func primaryButton(_ title: String, disabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).fontWeight(.semibold).frame(maxWidth: .infinity, minHeight: 36)
        }
        .buttonStyle(.borderedProminent).foregroundStyle(.black).disabled(disabled)
    }

    private func webButton(_ title: String, detail: String, route: String) -> some View {
        Button {
            if let url = URL(string: model.state.endpoint + "/#/" + route) { openURL(url) }
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                Label(title, systemImage: "arrow.up.right.square").font(.headline)
                Text(detail).font(.caption).foregroundStyle(.secondary)
            }.frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 8)
        }.buttonStyle(.bordered).disabled(!model.state.connected || busy)
    }

    private func tabButton(_ title: String, symbol: String, value: Int) -> some View {
        Button { tab = value } label: {
            Label(title, systemImage: symbol).font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity, minHeight: 44)
                .foregroundStyle(tab == value ? ShadowStyle.accent : .secondary)
        }.accessibilityAddTraits(tab == value ? .isSelected : [])
    }
}

private struct ShadowLessonView: View {
    @ObservedObject var model: ShadowModel
    @State private var stepIndex = 0
    @State private var question = ""
    @Environment(\.openURL) private var openURL

    var body: some View {
        NavigationStack {
            ScrollView {
                if let lesson = model.state.lesson {
                    VStack(alignment: .leading, spacing: 20) {
                        Text(lesson.title).font(.largeTitle.bold())
                        Text("Đọc các bước đã học. Để AI quan sát và hướng dẫn qua camera, mở bản web.")
                            .foregroundStyle(.secondary)
                        Button("Hướng dẫn qua camera · Mở bản web") {
                            var url = URLComponents(string: model.state.endpoint + "/")
                            url?.fragment = "/guide/" + lesson.id
                            if let value = url?.url { openURL(value) }
                        }.frame(minHeight: 44)
                        if !lesson.steps.isEmpty {
                            Picker("Bước cần xem", selection: $stepIndex) {
                                ForEach(Array(lesson.steps.enumerated()), id: \.offset) { index, _ in
                                    Text("Bước \(index + 1)").tag(index)
                                }
                            }.disabled(!model.state.busy.isEmpty)
                                .onChange(of: stepIndex) { _ in model.send(ShadowActionClearAnswer.shared) }
                            let step = lesson.steps[min(stepIndex, lesson.steps.count - 1)]
                            Text(step.instruction).font(.title2.bold())
                            if !step.tip.isEmpty { Text(step.tip) }
                            if !step.warning.isEmpty {
                                Label(step.warning, systemImage: "exclamationmark.triangle").foregroundStyle(.orange)
                            }
                            Divider()
                            Text("Hỏi SHADOW về bước này").font(.headline)
                            TextField("Bạn đang vướng ở đâu?", text: $question, axis: .vertical)
                                .lineLimit(2...5).textFieldStyle(.roundedBorder)
                                .disabled(!model.state.busy.isEmpty)
                            Text("\(question.utf16.count)/500").font(.caption).foregroundStyle(.secondary)
                            Button("Gửi câu hỏi") {
                                model.send(ShadowActionAsk(stepId: step.id, question: question))
                            }.buttonStyle(.borderedProminent).foregroundStyle(.black)
                                .disabled(question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || question.utf16.count > 500 || !model.state.busy.isEmpty)
                        }
                        if !model.state.busy.isEmpty { ProgressView(model.state.busy) }
                        if let error = model.state.error { Text(error).foregroundStyle(.red) }
                        if let answer = model.state.answer {
                            Text(model.state.answerLabel).font(.caption.bold()).foregroundStyle(ShadowStyle.accent)
                            Text(model.state.answerQuestion).font(.headline)
                            Text(answer.text).textSelection(.enabled)
                        }
                    }.padding(24)
                }
            }
            .background(ShadowStyle.background)
            .toolbar { Button("Đóng") { model.send(ShadowActionCloseLesson.shared) } }
        }
    }
}
