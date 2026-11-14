import SwiftUI
import AVFoundation
import CoreLocation
import Shared

/// SwiftUI camera chrome backed by the shared conversation store and native capture/speech.
@MainActor
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
                HStack(spacing: 8) {
                    HStack(spacing: 7) {
                        Image("SkiLogo").resizable().scaledToFit().frame(width: 32, height: 32).clipShape(RoundedRectangle(cornerRadius: 9)).accessibilityHidden(true)
                        Text("SKI").font(.system(size: 18, weight: .bold)).tracking(2)
                    }
                    Spacer(minLength: 4)
                    navigationButton("Tủ đồ", action: onWardrobe)
                    navigationButton("Bài đã học", action: onLessons)
                }.font(.system(size: 13)).foregroundStyle(.white).frame(height: 44)
                HStack(spacing: 6) {
                    Button(gps.label) { gps.request(container) }.font(.system(size: 11)).foregroundStyle(accent)
                    Circle().fill(model.live.connected ? accent : .gray).frame(width: 6, height: 6)
                    Text(model.live.connected ? "Live · nghe liên tục" : model.live.status == .connecting ? "Đang mở Live…" : "Live đang tắt")
                        .font(.system(size: 11)).foregroundStyle(.white.opacity(0.7))
                }
                Spacer(minLength: 22)
                if model.showsOutfit && !model.outfitItems.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(model.outfitTitle).font(.headline)
                        HStack(spacing: 8) {
                            ForEach(model.outfitItems.prefix(3)) { item in
                                VStack(spacing: 4) {
                                    RemoteImage(url: item.imageURL, headers: model.imageHeaders(item.imageURL), height: 96, fit: true)
                                    Text(item.name).font(.caption2).lineLimit(2)
                                }.frame(maxWidth: .infinity)
                            }
                        }
                        if !model.outfitReason.isEmpty { Text(model.outfitReason).font(.caption).lineLimit(3) }
                    }.padding(12).background(.black.opacity(0.45), in: RoundedRectangle(cornerRadius: 16))
                        .foregroundStyle(.white).padding(.trailing, 42).padding(.bottom, 10)
                }
                if model.teaching.hasWork || model.teaching.status == "saved" {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(model.teaching.title).font(.caption).foregroundStyle(accent)
                        Text(model.teaching.message).font(.subheadline)
                        if model.teaching.draft != nil {
                            ScrollView {
                                Text(model.teaching.steps.enumerated().map { "\($0.offset + 1). \($0.element["instruction"] as? String ?? "")" }.joined(separator: "\n"))
                                    .font(.caption).frame(maxWidth: .infinity, alignment: .leading)
                            }.frame(maxHeight: 150)
                            Button("Lưu bài", action: model.saveTeaching).disabled(model.teaching.status == "saving")
                        }
                    }.padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .background(.black.opacity(0.45), in: RoundedRectangle(cornerRadius: 16)).foregroundStyle(.white)
                        .padding(.trailing, 42).padding(.bottom, 10)
                }
                if !model.lessonInstruction.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("\(model.lessonTitle) · \(model.lessonStep)/\(model.lessonSteps)").font(.caption).foregroundStyle(accent)
                        Text(model.lessonInstruction).font(.headline).lineLimit(4)
                    }.padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .background(.black.opacity(0.45), in: RoundedRectangle(cornerRadius: 16)).foregroundStyle(.white)
                        .padding(.trailing, 42).padding(.bottom, 10)
                }
                ScrollViewReader { proxy in
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            if model.messages.isEmpty {
                                Text("Chào bạn. Hôm nay bạn muốn mình giúp gì?")
                                    .font(.system(size: 20)).lineSpacing(5)
                            }
                            ForEach(Array(visibleTurns.enumerated()), id: \.offset) { _, turn in
                                Text(turn.text).font(.system(size: turn.role == "user" ? 14 : 18))
                                    .foregroundStyle(.white.opacity(turn.role == "user" ? 0.63 : 1)).lineSpacing(5)
                            }
                            Color.clear.frame(height: 1).id("latest")
                        }.frame(maxWidth: .infinity, alignment: .leading).padding(.trailing, 42)
                    }.frame(maxHeight: 240).fixedSize(horizontal: false, vertical: true)
                        .onChange(of: model.messages.last?.text) { _ in withAnimation { proxy.scrollTo("latest", anchor: .bottom) } }
                        .onChange(of: model.messages.count) { _ in withAnimation { proxy.scrollTo("latest", anchor: .bottom) } }
                }.foregroundStyle(.white).accessibilityLabel("Cuộc trò chuyện")
                ForEach(Array(model.links.enumerated()), id: \.offset) { _, link in
                    Button("\(link.label)  →") {
                        if ["wardrobe", "stylist", "tryon-photo", "tryon-live"].contains(link.route) { onWardrobe() }
                        else { onLessons() }
                    }.font(.system(size: 14)).foregroundStyle(accent).padding(.top, 8)
                }
                Text(status).font(.system(size: 12)).lineSpacing(3)
                    .foregroundStyle(model.live.error != nil || model.state.error != nil || shadow.state.error != nil ? Color(red: 1, green: 0.79, blue: 0.66) : .white.opacity(0.68))
                    .padding(.top, 14).padding(.bottom, 12)
                HStack(spacing: 10) {
                    control(model.listening ? "stop.fill" : "mic", model.listening ? "Tắt micro" : "Bật micro", highlighted: model.listening) {
                        typing = false; model.toggleMic()
                    }
                    TextField("Hỏi SKI…", text: $model.text)
                        .font(.system(size: 15)).tint(accent).foregroundStyle(.white)
                        .padding(.horizontal, 17).frame(minHeight: 46)
                        .background(.black.opacity(0.12), in: Capsule())
                        .overlay(Capsule().stroke(.white.opacity(0.25), lineWidth: 1))
                        .focused($typing).submitLabel(.send).accessibilityLabel("Câu hỏi")
                        .onSubmit { typing = false; model.send() }
                        .onChange(of: model.text) { value in if value.count > 500 { model.text = String(value.prefix(500)) } }
                    control(model.canCancelResponse ? "stop.fill" : "arrow.up", model.canCancelResponse ? "Dừng phản hồi" : "Gửi câu hỏi", highlighted: true) {
                        typing = false
                        if model.canCancelResponse { model.cancel() } else { model.send() }
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
        .onAppear { gps.refresh(container); configureLive(); if cameraVisible { model.start() } }
        .onDisappear { model.stop(); gps.stop() }
        .onChange(of: shadow.state.endpoint) { _ in configureLive() }
        .onChange(of: shadow.state.connected) { _ in configureLive() }
        .onChange(of: cameraVisible) { visible in
            if visible && scenePhase == .active && !settings { model.start() } else { model.stop() }
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active { gps.refresh(container) } else { gps.stop() }
            if phase == .active && cameraVisible && !settings { model.start() }
            else { model.stop() }
        }
        .sheet(isPresented: $settings, onDismiss: { draftCode = ""; if cameraVisible && scenePhase == .active { model.start() } }) {
            NavigationStack {
                Form {
                    Section {
                        TextField("Địa chỉ máy chủ", text: $draftEndpoint).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                        SecureField("Mã truy cập (nếu có)", text: $draftCode).textInputAutocapitalization(.never).autocorrectionDisabled()
                    } header: { Text("Máy chủ") }
                    footer: { Text("Mặc định kết nối hackathon.trioxstudios.com. Bạn có thể đổi địa chỉ máy chủ tại đây.") }
                    if let error = settingsError { Text(error).foregroundStyle(.red) }
                    Button("Lưu & kết nối") { saveSettings() }.foregroundStyle(accent)
                }
                .navigationTitle("Kết nối SKI").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Hủy") { settings = false } } }
            }.preferredColorScheme(.dark)
        }
    }

    private var visibleTurns: [LiveTranscript] { Array(model.messages.suffix(6)) }
    private func configureLive() {
        model.configure(endpoint: shadow.state.endpoint, headers: shadow.mediaHeaders, connected: shadow.state.connected)
        model.getLocation = { gps.liveLocation }
    }
    private var status: String {
        if let error = model.live.error { return error }
        if model.live.connected || model.live.status == .connecting { return model.live.detail }
        if let error = model.state.error { return error }
        if let notice = model.notice { return notice }
        if let error = shadow.state.error { return error }
        if model.state.busy { return "Đang phân tích yêu cầu…" }
        if model.listening { return "Đang nghe bạn nói…" }
        return shadow.state.connected ? "Thử áo, trò chuyện, học hoặc dạy một kỹ năng." : "Mở Cài đặt để kiểm tra kết nối."
    }
    private func navigationButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.system(size: 13, weight: .semibold))
                .padding(.horizontal, 14).frame(minHeight: 44)
                .background(.black.opacity(0.35), in: Capsule())
                .overlay(Capsule().stroke(.white.opacity(0.3), lineWidth: 1))
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
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

/// One-shot foreground fixes; coordinates are never saved to preferences.
@MainActor
private final class ForegroundStylingLocation: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var label = "Bật GPS"
    private var fix: CLLocation?
    var liveLocation: [String: Double]? {
        guard [.authorizedAlways, .authorizedWhenInUse].contains(manager.authorizationStatus),
              UIApplication.shared.applicationState == .active, let fix, abs(fix.timestamp.timeIntervalSinceNow) < 600 else { return nil }
        return ["lat": fix.coordinate.latitude, "lon": fix.coordinate.longitude]
    }
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
                self?.stop(); self?.fix = nil; self?.container?.clearLocation(); self?.label = "Thử lại GPS"
            }
        default:
            fix = nil; container.clearLocation(); label = "Bật GPS"
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
        self.fix = fix
        container?.updateLocation(lat: fix.coordinate.latitude, lon: fix.coordinate.longitude)
        label = "GPS ✓"
    }
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        stop(); fix = nil; container?.clearLocation(); label = "Thử lại GPS"
    }
    func stop() { timeout?.cancel(); timeout = nil; manager.stopUpdatingLocation() }
}
