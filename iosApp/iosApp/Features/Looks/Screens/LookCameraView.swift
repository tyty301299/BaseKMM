import SwiftUI
import Shared
import AVFoundation
import Photos
import AppTrackingTransparency
import Network
import Vision

struct LookCameraView: View {
    @ObservedObject var model: LookModel
    let onClose: () -> Void
    let onHistory: () -> Void
    let onSettings: () -> Void
    let onNetworkRestored: () -> Void
    let container: AppContainer
    @ObservedObject var shadow: ShadowModel
    @State private var showTeach = false
    @State private var showSkills = false
    @State private var guideSelection: CameraSkillSelection?
    @State private var camera = ShadowCamera()
    @StateObject private var network = CameraNetworkStatus()
    @StateObject private var garment = GarmentDetector()
    @State private var granted = false
    @State private var ready = false
    @State private var front = false
    @State private var torch = false
    @State private var timer = 0
    @State private var countdown = 0
    @State private var working = false
    @State private var message = ""
    @State private var selectedMode = 0
    @State private var guideStep: Int? = nil
    @State private var manualSessionID = UUID().uuidString
    private let giftSteps = [
        "Đặt hộp lên giữa mặt trái của giấy gói.",
        "Kéo hai mép giấy qua hộp, chồng lên nhau và dán lại.",
        "Gấp hai cạnh bên thành hình tam giác, rồi gập và dán đầu hộp.",
        "Lặp lại ở đầu còn lại, sau đó buộc ruy băng."
    ]
    private let foldSteps = [
        "Trải áo phẳng, mặt trước úp xuống và vuốt thẳng.",
        "Gấp một bên áo và tay áo vào giữa.",
        "Gấp bên còn lại vào giữa để hai mép thẳng hàng.",
        "Gấp phần thân áo từ dưới lên và vuốt phẳng."
    ]
    private let pantsSteps = [
        "Trải quần phẳng, kéo thẳng hai ống quần.",
        "Gấp đôi quần theo chiều dọc, chồng hai ống lên nhau.",
        "Gấp phần đáy quần gọn vào trong.",
        "Gấp ống quần lên cạp và vuốt phẳng."
    ]
    private let modes: [(String, String, String)] = [
        ("Gói quà", "gift", "Đặt món quà vào khung hình"),
        ("Gấp áo", "tshirt", "Trải áo trên mặt phẳng"),
        ("Gấp quần", "figure.stand", "Trải quần trên mặt phẳng"),
        ("Thay đồ", "person.crop.rectangle", "Đưa trang phục vào khung hình"),
        ("Dạy học", "sparkles", "Dạy hoặc làm theo một kỹ năng")
    ]
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if granted { CameraPreview(camera: camera).ignoresSafeArea() }
            LinearGradient(colors: [.black.opacity(0.6), .clear, .clear, .black.opacity(0.9)], startPoint: .top, endPoint: .bottom).ignoresSafeArea().allowsHitTesting(false)
            if !ready {
                VStack(spacing: 12) { Image(systemName: "sparkles").font(.largeTitle); Text("SHADOW").font(.title.bold()) }.foregroundStyle(.white)
            } else if !granted {
                VStack(spacing: 12) {
                    Text("Cho phép truy cập camera để bắt đầu")
                    Button("Mở cài đặt") { if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) } }
                }.foregroundStyle(.white)
            }
            if (0...2).contains(selectedMode), let box = garment.box {
                GeometryReader { geometry in
                    let rect = garment.displayRect(box, in: geometry.size)
                    ObjectCorners()
                        .stroke(Color(red: 0.54, green: 0.97, blue: 0.17), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                        .shadow(color: .green.opacity(0.65), radius: 7)
                        .frame(width: max(0, rect.width), height: max(0, rect.height))
                        .position(x: rect.midX, y: rect.midY)
                        .allowsHitTesting(false)
                }.ignoresSafeArea().accessibilityHidden(true)
            }
            if countdown > 0 { Text("\(countdown)").font(.system(size: 90, weight: .bold)).foregroundStyle(.white) }
            if ready {
                VStack(spacing: 0) {
                    HStack {
                        control("Lịch sử", "clock.arrow.circlepath", onHistory)
                        Spacer()
                        control("Cài đặt", "gearshape", onSettings)
                    }.padding(.horizontal, 20).padding(.top, 8)
                    if network.online == false {
                        Label("Không có Internet · Vẫn có thể chụp ảnh", systemImage: "wifi.slash")
                            .font(.caption.weight(.semibold)).multilineTextAlignment(.center)
                            .padding(.horizontal, 14).padding(.vertical, 10)
                            .background(Color(red: 0.30, green: 0.22, blue: 0.12).opacity(0.94), in: Capsule())
                            .padding(.top, 12)
                    }
                    if let step = guideStep {
                        let steps = selectedMode == 0 ? giftSteps : selectedMode == 1 ? foldSteps : pantsSteps
                        VStack(alignment: .leading, spacing: 9) {
                            HStack {
                                Text("BƯỚC \(step + 1) / \(steps.count)").font(.caption.bold()).foregroundStyle(.yellow)
                                Spacer()
                                Button("Kết thúc") { guideStep = nil }.font(.subheadline.weight(.semibold))
                            }
                            HStack(alignment: .center, spacing: 12) {
                                Text(steps[step]).font(.headline).fixedSize(horizontal: false, vertical: true).frame(maxWidth: .infinity, alignment: .leading)
                                BuiltinStepIllustration(isGift: selectedMode == 0, step: step, isPants: selectedMode == 2)
                                    .frame(width: 112, height: 92)
                            }
                            HStack {
                                Button("Trước") { guideStep = max(0, step - 1) }.disabled(step == 0)
                                Spacer()
                                Button(step == steps.count - 1 ? "Hoàn thành" : "Đã xong bước này") {
                                    if step == steps.count - 1 {
                                        let id = selectedMode == 0 ? "shadow-builtin-goi-qua-v1" : selectedMode == 1 ? "shadow-builtin-gap-ao-v1" : "shadow-builtin-gap-quan-v1"
                                        model.send(LookActionSaveGuideCompletion(sessionId: manualSessionID, skillId: id, title: modes[selectedMode].0, source: "manual"))
                                        guideStep = nil
                                    } else { guideStep = step + 1 }
                                }.buttonStyle(.borderedProminent).tint(.white).foregroundStyle(.black)
                            }.font(.subheadline.weight(.semibold))
                        }.padding(16).background(.black.opacity(0.72), in: RoundedRectangle(cornerRadius: 20))
                            .padding(.horizontal, 20).padding(.top, 14)
                    }
                    Spacer(minLength: 16)
                    VStack(spacing: 18) {
                        HStack(spacing: 10) {
                            Image(systemName: modes[selectedMode].1)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Color(red: 0.88, green: 0.79, blue: 0.62))
                            Text(message.isEmpty ? ((0...2).contains(selectedMode) ? garment.status : modes[selectedMode].2) : message)
                                .font(.subheadline.weight(.medium)).lineLimit(2)
                            Spacer(minLength: 0)
                            control("Hẹn giờ: \(timer == 0 ? "tắt" : "\(timer) giây")", timer == 0 ? "timer" : "\(timer).circle") {
                                timer = timer == 0 ? 3 : timer == 3 ? 10 : 0
                            }
                        }
                        .padding(.leading, 16).padding(.trailing, 4).padding(.vertical, 4)
                        .background(.black.opacity(0.38), in: Capsule())
                        if guideStep == nil && (0...2).contains(selectedMode) {
                            HStack {
                                Button("Hướng dẫn mẫu") { manualSessionID = UUID().uuidString; guideStep = 0; message = "" }
                                    .font(.subheadline.weight(.semibold)).frame(maxWidth: .infinity, minHeight: 46)
                                    .background(.white, in: Capsule()).foregroundStyle(.black)
                                Button(!shadow.state.connected ? "Tự động" : shadow.state.modeLabel == "AI" ? "Tự động AI" : "Tự động demo") {
                                    if shadow.state.connected {
                                        shadow.send(ShadowActionPrepareBuiltinGuide(kind: selectedMode == 0 ? "gift" : selectedMode == 1 ? "fold" : "pants"))
                                    } else { message = "Cần kết nối SHADOW để tự chuyển bước" }
                                }.buttonStyle(.bordered).tint(.white).disabled(!shadow.state.busy.isEmpty)
                            }
                        } else if guideStep == nil && selectedMode == 4 {
                            HStack(spacing: 10) {
                                Button("Dạy kỹ năng mới") { if shadow.state.connected { showTeach = true } else { message = "Cần kết nối SHADOW để dạy kỹ năng" } }
                                Button("Kỹ năng đã học") { showSkills = true }
                            }.font(.subheadline.weight(.semibold)).buttonStyle(.bordered).tint(.white)
                        }
                        HStack {
                            control(torch ? "Tắt đèn" : "Bật đèn", torch ? "bolt.fill" : "bolt.slash") {
                                torch.toggle(); camera.setTorch(torch)
                            }
                            Spacer()
                            Button(action: capture) {
                                Circle().fill(.white).padding(6)
                                    .overlay(Circle().stroke(.white.opacity(0.95), lineWidth: 3))
                                    .frame(width: 78, height: 78)
                            }.disabled(!granted || working).opacity(granted && !working ? 1 : 0.45).accessibilityLabel("Chụp ảnh")
                            Spacer()
                            control("Đổi camera", "arrow.triangle.2.circlepath.camera") {
                                front.toggle(); torch = false; camera.setTorch(false); camera.flip(front: front)
                            }
                        }.padding(.horizontal, 25)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 2) {
                                ForEach(modes.indices, id: \.self) { index in feature(index) }
                            }.padding(5)
                        }.modifier(LookGlass())
                    }
                    .padding(.horizontal, 20).padding(.top, 24).padding(.bottom, 10)
                    .background(LinearGradient(colors: [.clear, .black.opacity(0.5), .black.opacity(0.75)], startPoint: .top, endPoint: .bottom))
                }.foregroundStyle(.white)
            }
        }
        .sheet(isPresented: $showSkills) {
            NavigationStack {
                List {
                    if !shadow.state.connected { Text("Chưa kết nối SHADOW. Hãy kiểm tra mạng và cài đặt kết nối.") }
                    else if shadow.state.skills.isEmpty { Text("Chưa có kỹ năng đã học. Hãy dạy một kỹ năng mới.") }
                    ForEach(shadow.state.skills, id: \.id) { skill in
                        Button(skill.title) { showSkills = false; guideSelection = CameraSkillSelection(id: skill.id, title: skill.title + (shadow.state.modeLabel == "AI" ? "" : " · Demo")) }
                    }
                }.navigationTitle("Kỹ năng đã học")
                    .toolbar { Button("Đóng") { showSkills = false } }
            }.presentationDetents([.medium, .large])
        }
        .fullScreenCover(isPresented: $showTeach, onDismiss: { shadow.send(ShadowActionRefresh.shared) }) {
            LiveTeachView(container: container, shadow: shadow.shadowStore) { showTeach = false }
        }
        .fullScreenCover(item: $guideSelection) { skill in
            LiveGuideView(skillId: skill.id, skillTitle: skill.title, container: container, shadow: shadow.shadowStore,
                onCompleted: { sessionID, title, manuallyAdvanced in
                    model.send(LookActionSaveGuideCompletion(sessionId: sessionID, skillId: skill.id,
                        title: String(title.prefix(100)), source: manuallyAdvanced ? "manual" : skill.title.hasSuffix(" · Demo") ? "demo" : "ai"))
                }) { guideSelection = nil }
        }
        .environment(\.colorScheme, .dark)
        .task { await start() }
        .onAppear { camera.setFrameHandler { [weak garment] buffer, front in garment?.offer(buffer, front: front) }; garment.setEnabled((0...2).contains(selectedMode), garmentOnly: selectedMode != 0) }
        .onChange(of: selectedMode) { garment.setEnabled((0...2).contains($0), garmentOnly: $0 != 0) }
        .onChange(of: shadow.state.preparedGuideId) { id in
            guard let id else { return }
            guideSelection = CameraSkillSelection(id: id, title: shadow.state.preparedGuideTitle + (shadow.state.modeLabel == "AI" ? "" : " · Demo"))
            shadow.send(ShadowActionClearPreparedGuide.shared)
        }
        .onDisappear { camera.setFrameHandler(nil); garment.setEnabled(false); camera.stop() }
        .onChange(of: network.online) { online in
            if online == true && network.wasOffline { network.wasOffline = false; onNetworkRestored() }
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active && ready { ShadowCamera.requestAccess { value in granted = value; if value { camera.start(front: front) } } }
            else if phase != .active { camera.stop() }
        }
    }

    private func start() async {
        // ATT is requested while the splash is visible, before the camera prompt.
        if ATTrackingManager.trackingAuthorizationStatus == .notDetermined {
            try? await Task.sleep(nanoseconds: 500_000_000)
            _ = await ATTrackingManager.requestTrackingAuthorization()
        }
        ready = true
        ShadowCamera.requestAccess { value in granted = value; if value { camera.start(front: front) } }
    }
    private func capture() {
        guard !working else { return }
        working = true; message = ""
        Task { @MainActor in
            if timer > 0 {
                for i in stride(from: timer, through: 1, by: -1) { countdown = i; try? await Task.sleep(nanoseconds: 1_000_000_000) }
            }
            countdown = 0
            guard let encoded = camera.capture(maxSide: 1920)?.components(separatedBy: ",").last,
                  let data = Data(base64Encoded: encoded), let image = UIImage(data: data) else {
                message = "Chưa chụp được ảnh, hãy thử lại"; working = false; return
            }
            let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
            guard status == .authorized || status == .limited else { message = "Cần quyền lưu ảnh trong Cài đặt"; working = false; return }
            do {
                try await PHPhotoLibrary.shared().performChanges { PHAssetChangeRequest.creationRequestForAsset(from: image) }
                message = "Đã lưu ảnh"
            } catch { message = "Không lưu được ảnh, hãy thử lại" }
            working = false
        }
    }
    private func control(_ label: String, _ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) { Image(systemName: icon).font(.system(size: 19, weight: .semibold)).frame(width: 48, height: 48).modifier(LookGlass()) }.accessibilityLabel(label)
    }
    private func feature(_ index: Int) -> some View {
        let selected = selectedMode == index
        return Button {
            selectedMode = index
            guideStep = nil
            message = ""
        } label: {
            Text(modes[index].0).font(.subheadline.weight(selected ? .semibold : .medium))
                .lineLimit(1).minimumScaleFactor(0.8)
            .padding(.horizontal, 16).frame(minHeight: 44)
            .foregroundStyle(selected ? Color.black : Color.white.opacity(0.8))
            .background(selected ? Color.white : Color.clear, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private final class CameraNetworkStatus: ObservableObject {
    @Published var online: Bool?
    var wasOffline = false
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "shadow.network")
    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            DispatchQueue.main.async {
                guard let self else { return }
                let connected = path.status == .satisfied
                if !connected { self.wasOffline = true }
                self.online = connected
            }
        }
        monitor.start(queue: queue)
    }
    deinit { monitor.cancel() }
}

private struct CameraSkillSelection: Identifiable { let id: String; let title: String }

/// Vision classifies the frame, then estimates a salient object region. This is not a trained
/// garment detector; the box is approximate and disappears when the classification is uncertain.
final class GarmentDetector: ObservableObject {
    @Published private(set) var box: CGRect?
    @Published private(set) var status = "Đưa trang phục vào khung hình · đang tìm"
    private let queue = DispatchQueue(label: "shadow.garment.vision", qos: .userInitiated)
    private let lock = NSLock()
    private var enabled = false
    private var garmentOnly = true
    private var busy = false
    private var generation = 0
    private var lastRun = CFAbsoluteTimeGetCurrent() - 2
    // Accessed only by the Vision queue: track between slower full classifications.
    private var trackingGeneration = -1
    private var trackedObject: VNDetectedObjectObservation?
    private var sequence = VNSequenceRequestHandler()
    private var lastDetection = CFAbsoluteTimeGetCurrent() - 10
    private var misses = 0
    private var aspect: CGFloat = 9.0 / 16.0

    func setEnabled(_ value: Bool, garmentOnly: Bool = true) {
        lock.lock(); enabled = value; self.garmentOnly = garmentOnly; generation += 1; misses = 0; lock.unlock()
        box = nil
        status = garmentOnly ? "Đưa trang phục vào khung hình · đang tìm" : "Đưa vật thể vào khung hình · đang tìm"
    }

    func offer(_ buffer: CVPixelBuffer, front: Bool) {
        lock.lock()
        let now = CFAbsoluteTimeGetCurrent()
        guard enabled, !busy, now - lastRun > 0.1 else { lock.unlock(); return }
        busy = true; lastRun = now
        let token = generation
        let needsGarment = garmentOnly
        lock.unlock()
        queue.async { [weak self] in
            guard let self else { return }
            if self.trackingGeneration != token {
                self.trackingGeneration = token
                self.trackedObject = nil
                self.sequence = VNSequenceRequestHandler()
                self.lastDetection = 0
            }
            let orientation: CGImagePropertyOrientation = front ? .leftMirrored : .right
            var found: CGRect?
            let currentTime = CFAbsoluteTimeGetCurrent()
            if let tracked = self.trackedObject, currentTime - self.lastDetection < 2.0 {
                let request = VNTrackObjectRequest(detectedObjectObservation: tracked)
                request.trackingLevel = .fast
                do {
                    try self.sequence.perform([request], on: buffer, orientation: orientation)
                    if let result = request.results?.first as? VNDetectedObjectObservation, result.confidence >= 0.35 {
                        self.trackedObject = result
                        found = result.boundingBox
                    } else {
                        self.trackedObject = nil
                    }
                } catch { self.trackedObject = nil }
            } else {
                self.lastDetection = currentTime
                let saliency = VNGenerateObjectnessBasedSaliencyImageRequest()
                let classify = VNClassifyImageRequest()
                let handler = VNImageRequestHandler(cvPixelBuffer: buffer, orientation: orientation)
                do {
                    // Generic object mode needs no image classification.
                    if needsGarment { try handler.perform([classify, saliency]) }
                    else { try handler.perform([saliency]) }
                    let clothes = ["shirt", "t-shirt", "jersey", "sweater", "sweatshirt", "blouse", "garment", "clothing", "apparel", "pants", "trousers", "jeans"]
                    let isClothing = (classify.results ?? []).prefix(20).contains { observation in
                        observation.confidence >= 0.08 && clothes.contains { observation.identifier.lowercased().contains($0) }
                    }
                    if !needsGarment || isClothing {
                        found = saliency.results?.first?.salientObjects?
                            .filter { $0.boundingBox.width > 0.1 && $0.boundingBox.height > 0.1 }
                            .max { $0.boundingBox.width * $0.boundingBox.height < $1.boundingBox.width * $1.boundingBox.height }?.boundingBox
                    }
                } catch { found = nil }
                if let found {
                    self.trackedObject = VNDetectedObjectObservation(boundingBox: found)
                    self.sequence = VNSequenceRequestHandler()
                } else { self.trackedObject = nil }
            }
            let imageAspect = CGFloat(CVPixelBufferGetHeight(buffer)) / CGFloat(CVPixelBufferGetWidth(buffer))
            self.lock.lock(); self.busy = false
            let current = self.enabled && self.generation == token
            if found == nil { self.misses += 1 } else { self.misses = 0 }
            let clear = self.misses >= 3
            self.lock.unlock()
            guard current else { return }
            DispatchQueue.main.async {
                self.lock.lock(); let valid = self.enabled && self.generation == token; self.lock.unlock()
                guard valid else { return }
                self.aspect = imageAspect
                if let found { self.box = found; self.status = needsGarment ? "Đã thấy trang phục · vùng nhận diện ước lượng" : "Đã thấy vật thể · vùng nhận diện ước lượng" }
                else if clear { self.box = nil; self.status = needsGarment ? "Chưa thấy trang phục · trải phẳng và đủ sáng" : "Chưa thấy vật thể · đặt vật ở nơi đủ sáng" }
            }
        }
    }

    func displayRect(_ rect: CGRect, in size: CGSize) -> CGRect {
        let scale = max(size.width / aspect, size.height)
        let origin = CGPoint(x: (size.width - aspect * scale) / 2, y: (size.height - scale) / 2)
        return CGRect(x: origin.x + rect.minX * aspect * scale,
                      y: origin.y + (1 - rect.maxY) * scale,
                      width: rect.width * aspect * scale, height: rect.height * scale)
    }
}

/// Four disconnected rounded corners, positioned over the detected region.
struct ObjectCorners: Shape {
    func path(in rect: CGRect) -> Path {
        let radius = min(16, rect.width / 4, rect.height / 4)
        let length = min(38, rect.width / 3, rect.height / 3)
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY + length))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY + radius))
        path.addQuadCurve(to: CGPoint(x: rect.minX + radius, y: rect.minY), control: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.minX + length, y: rect.minY))
        path.move(to: CGPoint(x: rect.maxX - length, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX - radius, y: rect.minY))
        path.addQuadCurve(to: CGPoint(x: rect.maxX, y: rect.minY + radius), control: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY + length))
        path.move(to: CGPoint(x: rect.maxX, y: rect.maxY - length))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - radius))
        path.addQuadCurve(to: CGPoint(x: rect.maxX - radius, y: rect.maxY), control: CGPoint(x: rect.maxX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.maxX - length, y: rect.maxY))
        path.move(to: CGPoint(x: rect.minX + length, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX + radius, y: rect.maxY))
        path.addQuadCurve(to: CGPoint(x: rect.minX, y: rect.maxY - radius), control: CGPoint(x: rect.minX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY - length))
        return path
    }
}

/// Small, static illustrations of the intended result at each step.
struct BuiltinStepIllustration: View {
    let isGift: Bool
    let step: Int
    var isPants = false

    var body: some View {
        Canvas { context, size in
            context.scaleBy(x: size.width / 120, y: size.height / 100)
            let paper = Color(red: 0.87, green: 0.79, blue: 0.65)
            let fold = Color(red: 0.69, green: 0.76, blue: 0.66)
            let line = Color(red: 0.25, green: 0.32, blue: 0.28)
            let accent = Color(red: 0.96, green: 0.65, blue: 0.23)
            func polygon(_ points: [CGPoint]) -> Path {
                var path = Path()
                guard let first = points.first else { return path }
                path.move(to: first); points.dropFirst().forEach { path.addLine(to: $0) }; path.closeSubpath()
                return path
            }
            if isGift {
                context.fill(Path(roundedRect: CGRect(x: 9, y: 18, width: 102, height: 69), cornerRadius: 6), with: .color(paper))
                context.fill(Path(roundedRect: CGRect(x: 35, y: 31, width: 50, height: 39), cornerRadius: 4), with: .color(fold))
                if step >= 1 {
                    context.fill(polygon([CGPoint(x: 9, y: 20), CGPoint(x: 57, y: 31), CGPoint(x: 57, y: 70), CGPoint(x: 9, y: 84)]), with: .color(paper.opacity(0.95)))
                    context.stroke(Path(CGRect(x: 35, y: 31, width: 50, height: 39)), with: .color(line.opacity(0.45)), lineWidth: 1)
                }
                if step >= 2 {
                    context.fill(polygon([CGPoint(x: 85, y: 31), CGPoint(x: 110, y: 18), CGPoint(x: 99, y: 51)]), with: .color(fold.opacity(0.9)))
                    context.fill(polygon([CGPoint(x: 85, y: 70), CGPoint(x: 110, y: 86), CGPoint(x: 99, y: 51)]), with: .color(fold.opacity(0.9)))
                }
                if step >= 3 {
                    var ribbon = Path(); ribbon.move(to: CGPoint(x: 59, y: 31)); ribbon.addLine(to: CGPoint(x: 59, y: 70)); ribbon.move(to: CGPoint(x: 35, y: 51)); ribbon.addLine(to: CGPoint(x: 85, y: 51))
                    context.stroke(ribbon, with: .color(accent), lineWidth: 5)
                    context.stroke(Path(ellipseIn: CGRect(x: 47, y: 39, width: 12, height: 11)), with: .color(accent), lineWidth: 3)
                    context.stroke(Path(ellipseIn: CGRect(x: 59, y: 39, width: 12, height: 11)), with: .color(accent), lineWidth: 3)
                }
            } else if isPants {
                let pants = polygon([CGPoint(x: 36, y: 15), CGPoint(x: 84, y: 15), CGPoint(x: 88, y: 86), CGPoint(x: 65, y: 86), CGPoint(x: 60, y: 48), CGPoint(x: 55, y: 86), CGPoint(x: 32, y: 86)])
                context.fill(pants, with: .color(fold))
                context.stroke(pants, with: .color(line.opacity(0.55)), lineWidth: 1.5)
                if step >= 1 { context.fill(Path(CGRect(x: 60, y: 18, width: 23, height: 68)), with: .color(line.opacity(0.18))) }
                if step >= 2 { context.fill(Path(CGRect(x: 54, y: 38, width: 12, height: 12)), with: .color(paper)) }
                if step >= 3 { context.fill(Path(roundedRect: CGRect(x: 38, y: 51, width: 43, height: 33), cornerRadius: 3), with: .color(paper.opacity(0.8))) }
            } else {
                let shirt = polygon([CGPoint(x: 39, y: 17), CGPoint(x: 50, y: 12), CGPoint(x: 70, y: 12), CGPoint(x: 81, y: 17), CGPoint(x: 104, y: 35), CGPoint(x: 91, y: 49), CGPoint(x: 80, y: 42), CGPoint(x: 80, y: 86), CGPoint(x: 40, y: 86), CGPoint(x: 40, y: 42), CGPoint(x: 29, y: 49), CGPoint(x: 16, y: 35)])
                context.fill(shirt, with: .color(fold))
                context.stroke(shirt, with: .color(line.opacity(0.55)), lineWidth: 1.5)
                if step >= 1 { context.fill(Path(CGRect(x: 40, y: 28, width: 13, height: 58)), with: .color(line.opacity(0.17))) }
                if step >= 2 { context.fill(Path(CGRect(x: 67, y: 28, width: 13, height: 58)), with: .color(line.opacity(0.17))) }
                if step >= 3 { context.fill(Path(roundedRect: CGRect(x: 43, y: 48, width: 34, height: 36), cornerRadius: 3), with: .color(paper.opacity(0.8))) }
            }
            if step > 0 && step < 3 {
                var arrow = Path()
                let left = step == 1
                let x1: CGFloat = left ? 12 : 108, x2: CGFloat = left ? 40 : 80
                arrow.move(to: CGPoint(x: x1, y: 53)); arrow.addLine(to: CGPoint(x: x2, y: 53))
                arrow.move(to: CGPoint(x: x2 + (left ? -7 : 7), y: 46)); arrow.addLine(to: CGPoint(x: x2, y: 53)); arrow.addLine(to: CGPoint(x: x2 + (left ? -7 : 7), y: 60))
                context.stroke(arrow, with: .color(accent), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
            }
        }
        .background(Color(red: 0.94, green: 0.92, blue: 0.87), in: RoundedRectangle(cornerRadius: 12))
        .overlay(alignment: .bottomTrailing) {
            Text("MINH HỌA").font(.system(size: 7, weight: .bold)).foregroundStyle(.black.opacity(0.55)).padding(5)
        }
        .accessibilityLabel("Minh họa bước \(step + 1)")
    }
}
