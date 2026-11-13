import SwiftUI
import Shared
import PhotosUI
import ImageIO
import AVFoundation
import UniformTypeIdentifiers

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

    /// The guide and teach flows own their own Kotlin stores, built from this one.
    var shadowStore: ShadowStore { store }

    /// Headers for gated server media. Kept off `ShadowState` so the access code is never
    /// captured in a UI snapshot.
    var mediaHeaders: [String: String] { store.mediaHeaders() as? [String: String] ?? [:] }

    deinit {
        messageSubscription?.cancel()
        subscription?.cancel()
        store.close()
    }
}

enum ShadowStyle {
    static let background = LookStyle.background
    static let accent = LookStyle.accent
}

struct HomeView: View {
    @EnvironmentObject private var language: AppLanguage
    private let container: AppContainer
    private let onGuideCompleted: (String, String, String, Bool) -> Void
    @EnvironmentObject private var feedback: StudioFeedback
    @StateObject private var model: ShadowModel
    @AppStorage("shadow.endpoint") private var savedEndpoint = ""
    @State private var endpoint = ""
    @State private var accessCode = ""
    @State private var tab = 0
    @State private var addingWardrobeItem = false
    @State private var showSettings = false
    @State private var attemptedAutoConnect = false
    @State private var teaching = false
    @State private var event = "Đi làm"
    @State private var notes = ""
    @State private var personPhoto: PhotosPickerItem?
    @State private var personJPEG: Data?
    @State private var readingPhoto = false
    @State private var videoURL: URL?
    @State private var videoLease: ImportedVideoLease?
    @State private var videoDuration = 0.0
    @State private var frameTime = 0.0
    @State private var requestedFrameTime = 0.0
    @State private var photoError: String?
    @Environment(\.openURL) private var openURL

    init(container: AppContainer, initialTab: Int = 0, initialSettings: Bool = false, existingModel: ShadowModel? = nil, onGuideCompleted: @escaping (String, String, String, Bool) -> Void) {
        self.container = container
        self.onGuideCompleted = onGuideCompleted
        _tab = State(initialValue: initialTab)
        _showSettings = State(initialValue: initialSettings)
        _model = StateObject(wrappedValue: existingModel ?? ShadowModel(store: container.shadowStore()))
    }

    private var busy: Bool { !model.state.busy.isEmpty }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    Text(language.systemText(model.state.modeLabel)).font(.caption.weight(.semibold))
                        .foregroundStyle(ShadowStyle.accent)
                    if busy && model.state.connected { ProgressView(language.systemText(model.state.busy)).frame(maxWidth: .infinity, alignment: .leading) }
                    if busy && !model.state.connected { connectionLoading }
                    if let error = model.state.error {
                        Text(language.systemText(error)).foregroundStyle(.red).accessibilityLabel(language.format("error_detail", String(error)))
                    }
                    if !model.state.connected && !busy {
                        Button(language.text("settings_title")) { showSettings = true }
                            .buttonStyle(.borderedProminent)
                        Button(language.text("retry"), action: connect)
                    }
                    if tab == 0 { learning } else { wardrobe }
                }
                .frame(maxWidth: 680, alignment: .leading)
                .padding(24)
                .frame(maxWidth: .infinity)
            }
            Divider()
            HStack(spacing: 16) {
                tabButton(language.text("learn_tab"), symbol: "hand.draw", value: 0)
                tabButton(language.text("wardrobe_tab"), symbol: "tshirt", value: 1)
            }
            .padding(.horizontal, 16).padding(.vertical, 10)
        }
        .sheet(isPresented: $addingWardrobeItem) { WardrobeAddSheet(model: model) }
        .modifier(StudioFeedbackOverlay())
        .background(ShadowStyle.background)
        .tint(ShadowStyle.accent)
        .navigationTitle("SHADOW")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { showSettings = true } label: {
                    Label(language.text("settings_title"), systemImage: "gearshape")
                }
            }
        }
        .onAppear {
            model.onMessage = feedback.show
            if endpoint.isEmpty {
                endpoint = savedEndpoint.isEmpty ? ShadowDefaultsKt.DEFAULT_SHADOW_ORIGIN : savedEndpoint
            }
            if accessCode.isEmpty && endpoint == ShadowDefaultsKt.DEFAULT_SHADOW_ORIGIN {
                accessCode = ShadowDefaultsKt.DEFAULT_SHADOW_ACCESS_CODE
            }
            // Connect straight away so the demo opens on real skills, not a connect form.
            if !attemptedAutoConnect {
                attemptedAutoConnect = true
                if !model.state.connected && !busy { connect() }
                else if !busy { model.send(ShadowActionRefresh.shared) }
            }
        }
        .task(id: personPhoto) {
            guard let selected = personPhoto else { return }
            personJPEG = nil
            readingPhoto = true
            photoError = nil
            model.send(ShadowActionClearTryon.shared)
            do {
                let jpeg: Data
                if selected.supportedContentTypes.contains(where: { $0.conforms(to: .movie) }) {
                    guard let movie = try await selected.loadTransferable(type: ImportedPersonVideo.self) else { throw PhotoPreparationError.invalid }
                    var retained = false
                    defer { if !retained { try? FileManager.default.removeItem(at: movie.url) } }
                    let duration = try await AVURLAsset(url: movie.url).load(.duration).seconds
                    guard duration.isFinite, duration > 0 else { throw PhotoPreparationError.invalid }
                    jpeg = try await ShadowPhoto.videoFrame(movie.url, seconds: 0)
                    try Task.checkCancellation()
                    guard personPhoto == selected else { return }
                    if let videoURL { try? FileManager.default.removeItem(at: videoURL) }
                    videoURL = movie.url
                    videoLease = ImportedVideoLease(url: movie.url)
                    retained = true
                    videoDuration = duration
                    frameTime = 0
                    requestedFrameTime = 0
                } else {
                    guard let data = try await selected.loadTransferable(type: Data.self) else { throw PhotoPreparationError.invalid }
                    jpeg = try await Task.detached(priority: .userInitiated) { try ShadowPhoto.prepare(data) }.value
                    try Task.checkCancellation()
                    guard personPhoto == selected else { return }
                    if let videoURL { try? FileManager.default.removeItem(at: videoURL) }
                    videoURL = nil
                    videoLease = nil
                    videoDuration = 0
                }
                personJPEG = jpeg
                readingPhoto = false
            } catch {
                guard !Task.isCancelled, personPhoto == selected else { return }
                photoError = error is PhotoPreparationError && (error as? PhotoPreparationError) == .tooLarge ? "photo_too_large" : "photo_invalid"
                readingPhoto = false
            }
        }
        .task(id: requestedFrameTime) {
            guard let videoURL, requestedFrameTime > 0 else { return }
            personJPEG = nil
            readingPhoto = true
            do {
                let jpeg = try await ShadowPhoto.videoFrame(videoURL, seconds: requestedFrameTime)
                try Task.checkCancellation()
                personJPEG = jpeg
                model.send(ShadowActionClearTryon.shared)
                readingPhoto = false
            } catch {
                guard !Task.isCancelled else { return }
                photoError = "photo_invalid"
                readingPhoto = false
            }
        }
        .onDisappear {
            if model.state.tryingOn { model.send(ShadowActionCancelTryon.shared) }
        }
        .onChange(of: model.state.endpoint) { value in
            if !value.isEmpty {
                savedEndpoint = value
            }
        }
        .sheet(isPresented: $showSettings) {
            NavigationStack {
                Form {
                    Section(language.text("connection_title")) {
                        Label(language.text(model.state.connected ? "connected" : "not_connected"),
                              systemImage: model.state.connected ? "checkmark.circle.fill" : "network")
                        if model.state.connected { Text(model.state.endpoint).font(.caption).textSelection(.enabled) }
                        if busy && !model.state.connected { connectionLoading }
                        if let error = model.state.error { Text(language.systemText(error)).foregroundStyle(.red) }
                        connection
                        Text(language.text("auto_connect")).font(.caption).foregroundStyle(.secondary)
                    }
                    Section(language.text("language_title")) {
                        LanguageSetting()
                        Text(language.text("language_content")).font(.caption).foregroundStyle(.secondary)
                    }
                }
                .navigationTitle(language.text("settings_title"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) {
                    Button(language.text("close")) { showSettings = false }
                } }
            }
                .tint(ShadowStyle.accent)
        }
        .sheet(isPresented: Binding(
            get: { model.state.lesson != nil },
            set: { if !$0 { model.send(ShadowActionCloseLesson.shared) } }
        )) {
            ShadowLessonView(model: model, container: container, onGuideCompleted: onGuideCompleted)
                .modifier(StudioFeedbackOverlay())
                .preferredColorScheme(.dark).tint(ShadowStyle.accent)
        }
    }

    private func connect() {
        guard !busy else { return }
        if model.state.connected && endpoint.trimmingCharacters(in: CharacterSet(charactersIn: "/ ")) == model.state.endpoint && accessCode.isEmpty {
            model.send(ShadowActionReconnect.shared)
        } else {
            model.send(ShadowActionConnect(endpoint: endpoint, code: accessCode))
        }
    }

    private var connectionLoading: some View {
        HStack(spacing: 14) {
            ProgressView().tint(ShadowStyle.accent)
            VStack(alignment: .leading, spacing: 4) {
                Text(language.text("connecting")).font(.headline)
                Text(language.text("connection_wait")).font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(20).frame(maxWidth: .infinity, alignment: .leading)
        .modifier(LookGlass())
        .accessibilityElement(children: .combine)
    }

    private var connection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(language.text("server_hint"))
                .font(.subheadline).foregroundStyle(.secondary)
            TextField("https://shadow.example.com", text: Binding(get: { endpoint }, set: { endpoint = $0; accessCode = "" }))
                .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                .textFieldStyle(.roundedBorder).accessibilityLabel(language.text("server_address")).disabled(busy)
            SecureField(language.text("access_code_long"), text: $accessCode)
                .textFieldStyle(.roundedBorder).disabled(busy)
            Text(language.text("access_code_hint")).font(.caption).foregroundStyle(.secondary)
            primaryButton(language.text(model.state.connected ? "reconnect" : "connect"), disabled: endpoint.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || busy) {
                connect()
            }
        }
    }

    private var learning: some View {
        VStack(alignment: .leading, spacing: 24) {
            VStack(alignment: .leading, spacing: 10) {
                Text(language.text("learn_heading")).font(.largeTitle.bold())
                Text(language.text("learn_description"))
                    .foregroundStyle(.secondary)
            }
            Button(language.text("teach_camera")) { teaching = true }
                .frame(maxWidth: .infinity, minHeight: 48)
                .buttonStyle(.borderedProminent)
                .disabled(!model.state.connected || busy)
                .fullScreenCover(isPresented: $teaching) {
                    LiveTeachView(container: container, shadow: model.shadowStore) {
                        teaching = false
                        model.send(ShadowActionRefresh.shared) // the new skill should appear
                    }
                            }
            HStack {
                Text(language.text("learned_skills")).font(.title2.bold())
                Spacer()
                Button(language.text("refresh")) { model.send(ShadowActionRefresh.shared) }
                    .disabled(!model.state.connected || busy)
            }
            if model.state.skills.isEmpty {
                Text(model.state.connected ? language.text("skills_empty") : language.text("connect_to_skills"))
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
                        Text(language.format("step_count", String(skill.steps)) + (skill.teacher.isEmpty ? "" : language.format("teacher_credit", String(skill.teacher))))
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
            Text("Tủ đồ của bạn").font(.largeTitle.bold())
            Text("Thêm trang phục từ điện thoại, dùng chung tủ đồ với website.").foregroundStyle(.secondary)
            primaryButton("+ Thêm đồ từ điện thoại", disabled: !model.state.connected || busy) {
                model.send(ShadowActionClearWardrobeUpload.shared)
                addingWardrobeItem = true
            }
            HStack {
                Text(language.format("wardrobe_count", String(model.state.wardrobe.count))).font(.title2.bold())
                Spacer()
                Button(language.text("refresh")) { model.send(ShadowActionRefresh.shared) }
                    .disabled(!model.state.connected || busy)
            }
            if model.state.wardrobe.isEmpty {
                Text(language.text("wardrobe_empty"))
                    .foregroundStyle(.secondary)
            }
            let columns = [GridItem(.adaptive(minimum: 100), spacing: 8)]
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(model.state.wardrobe, id: \.id) { item in
                    VStack(spacing: 4) {
                        RemoteImage(url: item.imageUrl, headers: model.mediaHeaders, height: 110)
                        Text(item.name).font(.caption2).lineLimit(2)
                    }
                }
            }
            personPhotoSection
            Text(language.text("occasion_prompt")).font(.headline)
            Picker(language.text("occasion"), selection: $event) {
                Text(language.text("work")).tag("Đi làm")
                Text(language.text("casual")).tag("Đi chơi")
                Text(language.text("date")).tag("Hẹn hò")
            }.pickerStyle(.segmented).disabled(busy)
            TextField(language.text("extra_preferences"), text: $notes, axis: .vertical)
                .lineLimit(2...4).textFieldStyle(.roundedBorder).disabled(busy)
            Text("\(notes.utf16.count)/300").font(.caption).foregroundStyle(.secondary)
            primaryButton(language.text("suggest"), disabled: !model.state.connected || model.state.wardrobe.isEmpty || busy || notes.utf16.count > 300) {
                model.send(ShadowActionSuggest(event: event, notes: notes))
            }
            if let jpeg = personJPEG {
                primaryButton(language.text("create_tryon"), disabled: !model.state.connected || busy || readingPhoto || model.state.wardrobe.isEmpty || notes.utf16.count > 300) {
                    model.send(ShadowActionStylePerson(image: "data:image/jpeg;base64," + jpeg.base64EncodedString(), event: event, notes: notes))
                }
                Text(language.text("person_photo_privacy")).font(.caption).foregroundStyle(.secondary)
            }
            if model.state.tryingOn {
                Button(language.text("cancel")) { model.send(ShadowActionCancelTryon.shared) }.frame(minHeight: 44)
            }
            if let result = model.state.tryon {
                Text(language.text("tryon_result")).font(.title2.bold())
                RemoteImage(url: result.imageUrl, headers: model.mediaHeaders, height: 420, fit: true)
                Text(language.text(result.provider == "mock" ? "tryon_mock" : "tryon_ai"))
                    .font(.caption.weight(.semibold)).foregroundStyle(result.provider == "mock" ? .orange : .secondary)
                if !result.skippedItemIds.isEmpty { Text(language.text("tryon_partial")).font(.caption) }
                if !result.note.isEmpty { Text(result.note).font(.caption).foregroundStyle(.secondary) }
            }
            if let result = model.state.suggestions {
                Text(language.format("suggestions_for", String(model.state.suggestionsEvent))).font(.headline)
                Text(language.systemText(model.state.suggestionsLabel)).font(.caption.bold()).foregroundStyle(ShadowStyle.accent)
                if let weather = result.weather, weather.available {
                    Text(weather.summary).font(.subheadline).foregroundStyle(.cyan)
                }
                if !result.rulesUsed.isEmpty {
                    Text(language.text("ui_151") + result.rulesUsed.joined(separator: "; "))
                        .font(.caption).foregroundStyle(ShadowStyle.accent)
                }
                if result.outfits.isEmpty { Text(language.text("outfits_empty")) }
                ForEach(Array(result.outfits.enumerated()), id: \.offset) { _, outfit in
                    VStack(alignment: .leading, spacing: 10) {
                        Text(outfit.title).font(.title3.bold())
                        HStack(spacing: 8) {
                            ForEach(outfit.items, id: \.id) { item in
                                VStack(spacing: 4) {
                                    RemoteImage(url: item.imageUrl, headers: model.mediaHeaders)
                                    Text(item.name).font(.caption2).lineLimit(2)
                                }
                            }
                        }
                        Text(outfit.reason)
                        if !outfit.tips.isEmpty { Text(outfit.tips).foregroundStyle(.secondary) }
                    }.padding(.vertical, 12)
                    Divider()
                }
            }
        }
    }

    private var personPhotoSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(language.text("person_photo_title")).font(.title2.bold())
            Text(language.text("person_photo_detail")).foregroundStyle(.secondary)
            if let jpeg = personJPEG, let image = UIImage(data: jpeg) {
                Image(uiImage: image).resizable().scaledToFit().frame(maxWidth: .infinity, maxHeight: 320)
                    .clipShape(RoundedRectangle(cornerRadius: 24)).accessibilityLabel(language.text("photo_selected"))
            }
            PhotosPicker(selection: $personPhoto, matching: .any(of: [.images, .videos]), photoLibrary: .shared()) {
                Label(language.text(personJPEG == nil ? "choose_person_media" : "change_person_photo"), systemImage: "photo.on.rectangle")
                    .frame(maxWidth: .infinity, minHeight: 44)
            }.buttonStyle(.borderedProminent).disabled(busy || readingPhoto)
            if videoURL != nil && videoDuration > 0 {
                Text(language.text("video_frame")).font(.subheadline.bold())
                Slider(value: $frameTime, in: 0...max(0.01, videoDuration - 0.1), onEditingChanged: { editing in
                    if !editing { requestedFrameTime = max(0.001, frameTime) }
                }).disabled(busy || readingPhoto)
                Text(language.text("video_frame_note")).font(.caption).foregroundStyle(.secondary)
            }
            if readingPhoto { ProgressView(language.text("photo_loading")) }
            if let photoError { Text(language.text(photoError)).foregroundStyle(.red) }
        }
        .padding(20).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 24))
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
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: ShadowModel
    let container: AppContainer
    let onGuideCompleted: (String, String, String, Bool) -> Void
    @State private var stepIndex = 0
    @State private var question = ""
    @State private var guiding = false

    var body: some View {
        NavigationStack {
            ScrollView {
                if let lesson = model.state.lesson {
                    VStack(alignment: .leading, spacing: 20) {
                        Text(lesson.title).font(.largeTitle.bold())
                        Text(language.text("guide_description"))
                            .foregroundStyle(.secondary)
                        Button(language.text("start_guide")) { guiding = true }
                            .frame(minHeight: 44)
                            .fullScreenCover(isPresented: $guiding) {
                                LiveGuideView(skillId: lesson.id, skillTitle: lesson.title,
                                              container: container, shadow: model.shadowStore,
                                              onCompleted: { session, title, manual in
                                                  onGuideCompleted(session, lesson.id, title, manual)
                                              }) {
                                    guiding = false
                                }
                                                    }
                        if !lesson.steps.isEmpty {
                            Picker(language.text("select_step"), selection: $stepIndex) {
                                ForEach(Array(lesson.steps.enumerated()), id: \.offset) { index, _ in
                                    Text(language.format("step_number", String(index + 1))).tag(index)
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
                            Text(language.text("ask_step")).font(.headline)
                            TextField(language.text("question_hint"), text: $question, axis: .vertical)
                                .lineLimit(2...5).textFieldStyle(.roundedBorder)
                                .disabled(!model.state.busy.isEmpty)
                            Text("\(question.utf16.count)/500").font(.caption).foregroundStyle(.secondary)
                            Button(language.text("send_question")) {
                                model.send(ShadowActionAsk(stepId: step.id, question: question))
                            }.buttonStyle(.borderedProminent).foregroundStyle(.black)
                                .disabled(question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || question.utf16.count > 500 || !model.state.busy.isEmpty)
                        }
                        if !model.state.busy.isEmpty { ProgressView(language.systemText(model.state.busy)) }
                        if let error = model.state.error { Text(language.systemText(error)).foregroundStyle(.red) }
                        if let answer = model.state.answer {
                            Text(language.systemText(model.state.answerLabel)).font(.caption.bold()).foregroundStyle(ShadowStyle.accent)
                            Text(model.state.answerQuestion).font(.headline)
                            Text(answer.text).textSelection(.enabled)
                        }
                    }.padding(24)
                }
            }
            .background(ShadowStyle.background)
            .toolbar { Button(language.text("close")) { model.send(ShadowActionCloseLesson.shared) } }
        }
    }
}


struct ShadowConnectionStatus: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: ShadowModel
    let onSettings: () -> Void
    private var connecting: Bool { !model.state.connected && !model.state.busy.isEmpty }
    private var statusKey: String {
        connecting ? "connecting" : model.state.connected ? "connected" : model.state.error != nil ? "connection_failed" : "not_connected"
    }
    var body: some View {
        HStack(spacing: 12) {
            Button(action: onSettings) {
                HStack(spacing: 7) {
                    if connecting { ProgressView().controlSize(.small) }
                    else { Image(systemName: model.state.connected ? "checkmark.circle.fill" : "exclamationmark.circle.fill")
                        .foregroundStyle(model.state.connected ? Color.green : Color.orange) }
                    Text(language.text(statusKey)).font(.caption.weight(.semibold))
                }.padding(.horizontal, 12).frame(minHeight: 44)
            }.buttonStyle(.plain).modifier(LookGlass())
            Spacer(minLength: 0)
            Button { model.send(ShadowActionReconnect.shared) } label: {
                Label(language.text("reconnect"), systemImage: "arrow.clockwise").font(.caption.weight(.semibold))
            }.frame(minHeight: 44).disabled(!model.state.busy.isEmpty)
        }
    }
}

private enum PhotoPreparationError: Error, Equatable { case tooLarge, invalid }
private final class ImportedVideoLease {
    let url: URL
    init(url: URL) { self.url = url }
    deinit { try? FileManager.default.removeItem(at: url) }
}

private struct ImportedPersonVideo: Transferable {
    let url: URL
    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { video in SentTransferredFile(video.url) } importing: { received in
            let folder = FileManager.default.temporaryDirectory.appendingPathComponent("shadow-imports", isDirectory: true)
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            let target = folder.appendingPathComponent(UUID().uuidString).appendingPathExtension(received.file.pathExtension)
            try FileManager.default.copyItem(at: received.file, to: target)
            return ImportedPersonVideo(url: target)
        }
    }
}

enum ShadowPhoto {
    static func videoFrame(_ url: URL, seconds: Double) async throws -> Data {
        let generator = AVAssetImageGenerator(asset: AVURLAsset(url: url))
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: 1024, height: 1024)
        let (image, _) = try await generator.image(at: CMTime(seconds: seconds, preferredTimescale: 600))
        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(output, "public.jpeg" as CFString, 1, nil) else { throw PhotoPreparationError.invalid }
        CGImageDestinationAddImage(destination, image, [kCGImageDestinationLossyCompressionQuality: 0.85] as CFDictionary)
        guard CGImageDestinationFinalize(destination) else { throw PhotoPreparationError.invalid }
        return output as Data
    }

    static func prepare(_ data: Data) throws -> Data {
        guard data.count <= 30 * 1024 * 1024 else { throw PhotoPreparationError.tooLarge }
        guard let source = CGImageSourceCreateWithData(data as CFData, [kCGImageSourceShouldCache: false] as CFDictionary),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: 1024,
                kCGImageSourceShouldCacheImmediately: true,
              ] as CFDictionary) else { throw PhotoPreparationError.invalid }
        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(output, "public.jpeg" as CFString, 1, nil) else {
            throw PhotoPreparationError.invalid
        }
        CGImageDestinationAddImage(destination, image, [kCGImageDestinationLossyCompressionQuality: 0.85] as CFDictionary)
        guard CGImageDestinationFinalize(destination) else { throw PhotoPreparationError.invalid }
        return output as Data
    }
}
