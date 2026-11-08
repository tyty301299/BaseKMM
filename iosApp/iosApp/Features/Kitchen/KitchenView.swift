import AVFoundation
import SwiftUI
import Shared

// Set by the KITCHEN_SERVER_URL build setting. Real iPhones cannot reach the Mac as localhost.
private let kitchenServerUrl = Bundle.main.object(forInfoDictionaryKey: "KitchenServerUrl") as? String ?? ""

/// Bridges the Kotlin store to SwiftUI. Kotlin calls back on the main thread.
final class KitchenModel: ObservableObject {
    @Published private(set) var state: KitchenState
    @Published private(set) var isAuthorized = false
    let device = IosKitchenDevice()
    private let store: KitchenStore
    private var subscription: StoreSubscription?

    init(container: AppContainer) {
        store = container.kitchenStore(device: device, serverUrl: kitchenServerUrl)
        state = store.currentState
        subscription = store.observeState { [weak self] in self?.state = $0 }
    }

    func dispatch(_ action: KitchenAction) {
        store.dispatch(action: action)
    }

    func requestAccess() {
        AVCaptureDevice.requestAccess(for: .video) { camera in
            AVCaptureDevice.requestAccess(for: .audio) { microphone in
                DispatchQueue.main.async { [weak self] in
                    guard camera && microphone, let self else { return }
                    self.isAuthorized = true
                    self.device.startCamera()
                }
            }
        }
    }

    deinit {
        subscription?.cancel()
        store.close()
    }
}

private struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.backgroundColor = .black
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspect // The whole frame stays visible.
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {}
}

/// Draws where items belong and which ones are wrong, aligned to the letterboxed preview.
private struct GuideOverlay: View {
    let state: KitchenState

    var body: some View {
        GeometryReader { geometry in
            let frame = AVMakeRect(
                aspectRatio: CGSize(width: 720, height: 1_280),
                insideRect: CGRect(origin: .zero, size: geometry.size)
            )
            ZStack(alignment: .topLeading) {
                ForEach(Array(state.objects.enumerated()), id: \.offset) { _, detected in
                    let rect = place(detected.box, in: frame)
                    Rectangle().stroke(Color.blue, lineWidth: 2).frame(width: rect.width, height: rect.height).offset(x: rect.minX, y: rect.minY)
                    label(detected.label, color: .blue, at: rect)
                }
                if let zone = state.targetZone {
                    let rect = place(zone, in: frame)
                    Rectangle().fill(Color.green.opacity(0.2)).frame(width: rect.width, height: rect.height).offset(x: rect.minX, y: rect.minY)
                    Rectangle()
                        .stroke(Color.green, style: StrokeStyle(lineWidth: 3, dash: [10, 6]))
                        .frame(width: rect.width, height: rect.height)
                        .offset(x: rect.minX, y: rect.minY)
                    label("Đặt ở đây", color: .green, at: rect)
                }
                ForEach(Array(state.misplaced.enumerated()), id: \.offset) { _, zone in
                    let rect = place(zone, in: frame)
                    Rectangle().stroke(Color.red, lineWidth: 3).frame(width: rect.width, height: rect.height).offset(x: rect.minX, y: rect.minY)
                    label("Chuyển món này", color: .red, at: rect)
                }
            }
        }
        .allowsHitTesting(false)
    }

    private func place(_ zone: ZoneBox, in frame: CGRect) -> CGRect {
        CGRect(
            x: frame.minX + zone.left * frame.width,
            y: frame.minY + zone.top * frame.height,
            width: (zone.right - zone.left) * frame.width,
            height: (zone.bottom - zone.top) * frame.height
        )
    }

    private func label(_ text: String, color: Color, at rect: CGRect) -> some View {
        Text(text)
            .font(.caption.bold())
            .foregroundStyle(color)
            .offset(x: rect.minX + 4, y: rect.minY + 4)
    }
}

struct KitchenView: View {
    @StateObject private var model: KitchenModel

    init(container: AppContainer) {
        _model = StateObject(wrappedValue: KitchenModel(container: container))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !model.isAuthorized {
                Text("KitchenMate cần camera và micro để nhìn mặt bàn và nghe bạn nói.")
                ProgressView()
                Spacer()
            } else {
                content
            }
        }
        .padding(16)
        .navigationTitle("KitchenMate AI")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { model.requestAccess() }
    }

    @ViewBuilder private var content: some View {
        let state = model.state
        if state.phase == KitchenPhase.completed {
            Text("Hoàn thành!").font(.title.bold())
            HStack(alignment: .top, spacing: 8) {
                photo("Trước", state.beforeImage)
                photo("Sau", state.afterImage)
            }
            outputBanner(state)
            Spacer()
            Button("Làm lại") { model.dispatch(KitchenActionRestart.shared) }
                .buttonStyle(.borderedProminent)
        } else {
            ZStack {
                CameraPreview(session: model.device.captureSession)
                if state.phase == KitchenPhase.working {
                    GuideOverlay(state: state)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 12))
            if state.phase == KitchenPhase.ready {
                Text("Hướng camera vào mặt bàn bếp rồi bắt đầu.")
                outputBanner(state)
                Button("Bắt đầu") { model.dispatch(KitchenActionStart.shared) }
                    .buttonStyle(.borderedProminent)
            } else {
                working(state)
            }
        }
    }

    @ViewBuilder private func working(_ state: KitchenState) -> some View {
        ProgressView(value: Double(state.stepIndex), total: Double(state.steps.count))
        Text("Bước \(state.stepIndex + 1)/\(state.steps.count): \(state.currentStep.title)")
            .font(.title2.bold())
        Text(state.currentStep.instruction)
        if !state.objectSummary.isEmpty {
            Text("Đang thấy: \(state.objectSummary)").font(.subheadline)
        }
        if !state.caption.isEmpty {
            Text("AI: \(state.caption)").lineLimit(3)
        }
        outputBanner(state)
        HStack {
            Button(state.isChecking ? "Đang kiểm tra" : "Kiểm tra") {
                model.dispatch(KitchenActionCheck.shared)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!state.canCheck)
            if state.voice == VoiceStatus.error {
                Button("Kết nối lại giọng nói") { model.dispatch(KitchenActionRetryVoice.shared) }
                    .buttonStyle(.bordered)
            }
        }
        Text(voiceLabel(state.voice)).font(.caption).foregroundStyle(.secondary)
    }

    private func voiceLabel(_ voice: VoiceStatus) -> String {
        switch voice {
        case .connecting: return "Đang kết nối giọng nói"
        case .listening: return "Đang nghe, nói \"Xong rồi\" khi làm xong"
        case .error: return "Mất kết nối giọng nói, vẫn có thể bấm Kiểm tra"
        default: return "Giọng nói đang tắt"
        }
    }

    @ViewBuilder private func outputBanner(_ state: KitchenState) -> some View {
        if let output = state.output {
            Text(output.message)
                .font(.headline)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(color(for: output.kind).opacity(0.2))
                .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }

    private func color(for kind: OutputKind) -> Color {
        switch kind {
        case .success: return .green
        case .warning: return .orange
        case .error: return .red
        default: return .gray
        }
    }

    @ViewBuilder private func photo(_ label: String, _ base64: String?) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.headline)
            if let base64,
               let data = Data(base64Encoded: base64),
               let image = UIImage(data: data) {
                Image(uiImage: image).resizable().scaledToFit()
            }
        }
        .frame(maxWidth: .infinity)
    }
}
