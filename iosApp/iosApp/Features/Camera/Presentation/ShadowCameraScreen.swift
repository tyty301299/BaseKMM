import SwiftUI
import AVFoundation
import CoreLocation
import Shared

/// SwiftUI camera chrome backed by the shared conversation store and native capture/speech.
struct ShadowCameraScreen: View {
    private let container: AppContainer
    @ObservedObject private var shadow: ShadowModel
    @StateObject private var gps = ForegroundStylingLocation()
    @StateObject private var model: NativeCameraChatModel
    let onWardrobe: () -> Void
    let onLessons: () -> Void
    let cameraVisible: Bool
    @Environment(\.scenePhase) private var scenePhase
    @State private var settings = false
    @State private var draftEndpoint = ""
    @State private var draftCode = ""
    @State private var settingsError: String?
    @FocusState private var typing: Bool
    private let accent = Color(red: 1, green: 210/255, blue: 63/255)

    init(container: AppContainer, shadow: ShadowModel, cameraVisible: Bool, onWardrobe: @escaping () -> Void, onLessons: @escaping () -> Void) {
        self.container = container
        self.shadow = shadow
        self.onWardrobe = onWardrobe
        self.onLessons = onLessons
        self.cameraVisible = cameraVisible
        _model = StateObject(wrappedValue: NativeCameraChatModel(store: container.conversationStore(shadow: shadow.shadowStore)))
    }

    var body: some View {
        ZStack {
            Color(red: 17/255, green: 21/255, blue: 18/255).ignoresSafeArea()
            if model.cameraGranted && model.cameraAvailable && model.cameraEnabled && !settings {
                CameraPreview(camera: model.camera).ignoresSafeArea()
            }
            LinearGradient(stops: [.init(color: .black.opacity(0.54), location: 0), .init(color: .clear, location: 0.3),
                                   .init(color: .clear, location: 0.5), .init(color: .black.opacity(0.82), location: 1)],
                           startPoint: .top, endPoint: .bottom).ignoresSafeArea().allowsHitTesting(false)
            if !model.cameraGranted || !model.cameraAvailable || !model.cameraEnabled {
                VStack(spacing: 16) {
                    Image(systemName: "camera").font(.system(size: 34, weight: .light)).foregroundStyle(.white.opacity(0.65))
                    Text(!model.cameraAvailable ? "Camera không khả dụng trên thiết bị này" : model.cameraGranted ? "Camera đang tắt" : "Cho phép camera để bắt đầu")
                        .multilineTextAlignment(.center)
                    if model.cameraAvailable {
                        Button(model.cameraGranted ? "Bật camera" : "Mở cài đặt") {
                            if model.cameraGranted { model.toggleCamera() }
                            else if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                        }.foregroundStyle(accent)
                    }
                }.padding(36).foregroundStyle(.white)
            }
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 16) {
                    Text("SHADOW").font(.system(size: 18, weight: .bold)).tracking(2)
                    Spacer(minLength: 4)
                    Button("Tủ đồ", action: onWardrobe)
                    Button("Bài đã học", action: onLessons)
                }.font(.system(size: 13)).foregroundStyle(.white).frame(height: 44)
                HStack(spacing: 6) {
                    Button(gps.label) { gps.request(container) }.font(.system(size: 11)).foregroundStyle(accent)
                    Circle().fill(shadow.state.connected ? accent : .gray).frame(width: 6, height: 6)
                    Text(shadow.state.connected ? "Đã kết nối" : shadow.state.busy.isEmpty ? "Chưa kết nối" : "Đang kết nối…")
                        .font(.system(size: 11)).foregroundStyle(.white.opacity(0.7))
                }
                Spacer(minLength: 22)
                ScrollViewReader { proxy in
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            if model.state.turns.isEmpty {
                                Text("Chào bạn. Hôm nay bạn muốn mình giúp gì?")
                                    .font(.system(size: 20)).lineSpacing(5)
                            }
                            ForEach(Array(visibleTurns.enumerated()), id: \.offset) { _, turn in
                                Text(turn.text).font(.system(size: turn.role == "user" ? 14 : 18))
                                    .foregroundStyle(.white.opacity(turn.role == "user" ? 0.63 : 1)).lineSpacing(5)
                            }
                            if !model.state.reply.isEmpty {
                                Text(model.state.reply).font(.system(size: 18)).lineSpacing(5)
                            }
                            Color.clear.frame(height: 1).id("latest")
                        }.frame(maxWidth: .infinity, alignment: .leading).padding(.trailing, 42)
                    }.frame(maxHeight: 240).fixedSize(horizontal: false, vertical: true)
                        .onChange(of: model.state.reply) { _ in withAnimation { proxy.scrollTo("latest", anchor: .bottom) } }
                        .onChange(of: model.state.turns.count) { _ in withAnimation { proxy.scrollTo("latest", anchor: .bottom) } }
                }.foregroundStyle(.white).accessibilityLabel("Cuộc trò chuyện")
                ForEach(Array(model.state.links.enumerated()), id: \.offset) { _, link in
                    Button("\(link.label)  →") {
                        if ["wardrobe", "stylist", "tryon-photo", "tryon-live"].contains(link.route) { onWardrobe() }
                        else { onLessons() }
                    }.font(.system(size: 14)).foregroundStyle(accent).padding(.top, 8)
                }
                Text(status).font(.system(size: 12)).lineSpacing(3)
                    .foregroundStyle(model.state.error != nil || shadow.state.error != nil ? Color(red: 1, green: 0.79, blue: 0.66) : .white.opacity(0.68))
                    .padding(.top, 14).padding(.bottom, 12)
                HStack(spacing: 10) {
                    control(model.listening ? "stop.fill" : "mic", model.listening ? "Tắt micro" : "Bật micro", highlighted: model.listening) {
                        typing = false; model.toggleMic()
                    }
                    TextField("Hỏi Shadow…", text: $model.text)
                        .font(.system(size: 15)).tint(accent).foregroundStyle(.white)
                        .padding(.horizontal, 17).frame(minHeight: 46)
                        .background(.black.opacity(0.12), in: Capsule())
                        .overlay(Capsule().stroke(.white.opacity(0.25), lineWidth: 1))
                        .focused($typing).submitLabel(.send).accessibilityLabel("Câu hỏi")
                        .onSubmit { typing = false; model.send() }
                        .onChange(of: model.text) { value in if value.count > 500 { model.text = String(value.prefix(500)) } }
                    control(model.state.busy ? "stop.fill" : "arrow.up", model.state.busy ? "Dừng phản hồi" : "Gửi câu hỏi", highlighted: true) {
                        typing = false
                        if model.state.busy { model.cancel() } else { model.send() }
                    }
                }
            }.padding(.horizontal, 16).padding(.top, 10).padding(.bottom, 18)
            VStack {
                HStack {
                    Spacer()
                    VStack(spacing: 12) {
                        control(model.cameraEnabled ? "camera" : "camera.slash", model.cameraEnabled ? "Tắt camera" : "Bật camera") { model.toggleCamera() }
                        control("arrow.triangle.2.circlepath.camera", "Đổi camera trước sau") { model.flip() }
                        control("gearshape", "Cài đặt kết nối") { openSettings() }
                    }
                }
                Spacer()
            }.padding(.top, 100).padding(.trailing, 14)
        }
        .preferredColorScheme(.dark)
        .onAppear { gps.refresh(container); if cameraVisible { model.start() } }
        .onDisappear { model.stop(); gps.stop() }
        .onChange(of: shadow.state.endpoint) { _ in model.clear() }
        .onChange(of: cameraVisible) { visible in
            if visible && scenePhase == .active && !settings { model.start() } else { model.stop() }
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active { gps.refresh(container) } else { gps.stop() }
            if phase == .active && cameraVisible && !settings { model.start() }
            else { model.stop() }
        }
        .sheet(isPresented: $settings, onDismiss: { draftCode = ""; model.start() }) {
            NavigationStack {
                Form {
                    Section {
                        TextField("Base URL", text: $draftEndpoint).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                        SecureField("Mã truy cập (nếu có)", text: $draftCode).textInputAutocapitalization(.never).autocorrectionDisabled()
                    } header: { Text("Máy chủ") }
                    footer: { Text("Chạy local dùng http://localhost:8001. Bạn có thể đổi địa chỉ máy chủ sau.") }
                    if let error = settingsError { Text(error).foregroundStyle(.red) }
                    Button("Lưu & kết nối") { saveSettings() }.foregroundStyle(accent)
                }
                .navigationTitle("Kết nối SHADOW").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Hủy") { settings = false } } }
            }.preferredColorScheme(.dark)
        }
    }

    private var visibleTurns: [ConversationTurn] {
        var turns = model.state.turns
        if !model.state.busy && model.state.error == nil && turns.last?.role == "assistant" { turns.removeLast() }
        return Array(turns.suffix(5))
    }
    private var status: String {
        if let error = model.state.error { return error }
        if let notice = model.notice { return notice }
        if let error = shadow.state.error { return error }
        if model.state.busy { return "Đang phân tích yêu cầu…" }
        if model.listening { return "Đang nghe bạn nói…" }
        return shadow.state.connected ? "Thử áo, trò chuyện, học hoặc dạy một kỹ năng." : "Mở Cài đặt để kiểm tra kết nối."
    }
    private func control(_ symbol: String, _ label: String, highlighted: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 18, weight: .regular))
                .foregroundStyle(highlighted ? Color(red: 23/255, green: 27/255, blue: 20/255) : .white)
                .frame(width: 44, height: 44)
                .background(highlighted ? accent : .black.opacity(0.24), in: Circle())
                .overlay(Circle().stroke(.white.opacity(highlighted ? 0 : 0.24), lineWidth: 1))
        }.buttonStyle(.plain).accessibilityLabel(label)
    }
    private func openSettings() {
        typing = false; model.stop()
        draftEndpoint = UserDefaults.standard.string(forKey: "shadow.endpoint") ?? ShadowDefaultsKt.DEFAULT_SHADOW_ORIGIN
        draftCode = UserDefaults.standard.string(forKey: "shadow.access_code") ?? ""
        settingsError = nil; settings = true
    }
    private func saveSettings() {
        do {
            let endpoint = try container.normalizeEndpoint(endpoint: draftEndpoint)
            let code = draftCode.trimmingCharacters(in: .whitespacesAndNewlines)
            UserDefaults.standard.set(endpoint, forKey: "shadow.endpoint")
            UserDefaults.standard.set(code, forKey: "shadow.access_code")
            model.clear()
            shadow.send(ShadowActionConnect(endpoint: endpoint, code: code))
            draftCode = ""; settings = false
        } catch { settingsError = "Dùng HTTPS hoặc http://localhost:8001, không kèm đường dẫn." }
    }
}

private final class NativeCameraChatModel: ObservableObject {
    @Published private(set) var state: ConversationState
    @Published var text = ""
    @Published private(set) var listening = false
    @Published private(set) var cameraGranted = false
    @Published private(set) var cameraAvailable = true
    @Published private(set) var cameraEnabled = true
    @Published private(set) var notice: String?
    let camera = NativeServices.makeCamera()
    private let speaker = NativeServices.makeSpeaker()
    private var listener: AuthorizedSpeechListener?
    private let store: ConversationStore
    private var subscription: StoreSubscription?
    private var active = false
    private var front = false
    private var spokenTurn = false

    init(store: ConversationStore) {
        self.store = store
        state = store.currentState
        subscription = store.observeState { [weak self] state in
            guard let self else { return }
            self.state = state
            if self.active && !state.busy && self.spokenTurn && !state.reply.isEmpty && state.error == nil {
                self.spokenTurn = false; self.speaker.speak(text: state.reply)
            }
        }
        listener = NativeServices.makeListener(elapsedSeconds: { Date().timeIntervalSince1970 },
            onSentence: { [weak self] _, _, sentence in
                guard let self, self.active, self.listening else { return }
                self.text = sentence; self.spokenTurn = true; self.send()
            }, onPartial: { [weak self] text in if self?.listening == true { self?.text = text } },
            onUnavailable: { [weak self] in
                self?.listening = false
                self?.notice = "Nhập câu hỏi nếu thiết bị chưa hỗ trợ nhận giọng nói tiếng Việt."
            })
    }
    func start() {
        active = true
        cameraAvailable = AVCaptureDevice.default(for: .video) != nil
        guard cameraAvailable else { return }
        ShadowCamera.requestAccess { [weak self] granted in
            guard let self else { return }
            self.cameraGranted = granted
            if granted && self.active && self.cameraEnabled { self.camera.start(front: self.front) }
        }
    }
    func stop() {
        active = false; listening = false
        listener?.stop(); speaker.stop(); camera.stop(); store.cancel()
    }
    func toggleCamera() {
        cameraEnabled.toggle()
        if cameraEnabled { start() } else { camera.stop() }
    }
    func flip() { front.toggle(); camera.flip(front: front) }
    func toggleMic() {
        if listening { listening = false; listener?.stop(); return }
        guard !state.busy else { return }
        speaker.stop(); notice = nil
        AVCaptureDevice.requestAccess(for: .audio) { [weak self] granted in
            DispatchQueue.main.async {
                guard let self, self.active else { return }
                guard granted else { self.notice = "Cho phép micro trong Cài đặt để nói chuyện."; return }
                self.listener?.requestAccess { [weak self] speechGranted in
                    guard let self, self.active else { return }
                    if speechGranted { self.listening = true; self.listener?.start() }
                    else { self.notice = "Cho phép nhận dạng giọng nói trong Cài đặt. Bạn vẫn có thể nhập câu hỏi." }
                }
            }
        }
    }
    func send() {
        let prompt = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !prompt.isEmpty, !state.busy else { return }
        listening = false; listener?.stop(); speaker.stop(); notice = nil; text = ""
        let image = cameraEnabled && cameraGranted && active ? camera.capture(maxSide: 640) : nil
        store.send(text: prompt, image: image)
    }
    func cancel() { store.cancel(); speaker.stop() }
    func clear() { store.clear(); text = ""; spokenTurn = false }
    deinit { subscription?.cancel(); listener?.shutdown(); speaker.shutdown(); camera.stop(); store.close() }
}

/// One-shot foreground fixes; coordinates are never saved to preferences.
@MainActor
private final class ForegroundStylingLocation: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var label = "Bật GPS"
    private let manager = CLLocationManager()
    private var container: AppContainer?
    private var timeout: Task<Void, Never>?
    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
    }
    func refresh(_ container: AppContainer) {
        self.container = container
        guard UIApplication.shared.applicationState == .active else { return }
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            label = "Đang lấy GPS…"
            manager.requestLocation()
            timeout?.cancel()
            timeout = Task { [weak self] in
                try? await Task.sleep(nanoseconds: 10_000_000_000)
                guard !Task.isCancelled else { return }
                self?.stop(); self?.container?.clearLocation(); self?.label = "Thử lại GPS"
            }
        default:
            container.clearLocation(); label = "Bật GPS"
        }
    }
    func request(_ container: AppContainer) {
        self.container = container
        if manager.authorizationStatus == .notDetermined { manager.requestWhenInUseAuthorization() }
        else if manager.authorizationStatus == .denied || manager.authorizationStatus == .restricted {
            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
        } else { refresh(container) }
    }
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if let container { refresh(container) }
    }
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard UIApplication.shared.applicationState == .active,
              let fix = locations.last, fix.horizontalAccuracy >= 0, abs(fix.timestamp.timeIntervalSinceNow) < 600 else { return }
        stop()
        container?.updateLocation(lat: fix.coordinate.latitude, lon: fix.coordinate.longitude)
        label = "GPS ✓"
    }
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        stop(); container?.clearLocation(); label = "Thử lại GPS"
    }
    func stop() { timeout?.cancel(); timeout = nil; manager.stopUpdatingLocation() }
}
