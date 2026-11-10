import SwiftUI
import Shared

struct LookCameraView: View {
    @ObservedObject var model: LookModel
    let onClose: () -> Void
    let onHistory: () -> Void

    var body: some View {
        Group {
            if let capture = model.state.capture {
                LookCaptureView(model: model, capture: capture, onClose: onClose, onHistory: onHistory)
            } else {
                camera
            }
        }.environment(\.colorScheme, .dark).tint(LookStyle.accent)
            .interactiveDismissDisabled(model.state.saving)
    }

    private var camera: some View {
        GeometryReader { geometry in
            ZStack {
                Color(red: 0.77, green: 0.79, blue: 0.72).ignoresSafeArea()
                LookMannequin(preset: model.state.preset, before: model.state.before, flipped: model.state.flipped)
                    .frame(height: geometry.size.height * 0.65).offset(y: -30)
                LinearGradient(colors: [.black.opacity(0.55), .clear, .clear, .black.opacity(0.85)], startPoint: .top, endPoint: .bottom)
                    .ignoresSafeArea().allowsHitTesting(false)
                VStack(spacing: 16) {
                    HStack {
                        cameraButton("Đóng camera", icon: "chevron.left", action: onClose)
                        Spacer()
                        Text("Camera thử đồ").font(.headline)
                        Spacer()
                        cameraButton("Lật hình nhân", icon: "arrow.triangle.2.circlepath") { model.send(LookActionFlip.shared) }
                    }
                    HStack {
                        Button(model.state.before ? "Trước khi thử" : "Đang thử · mẫu") { model.send(LookActionCompare.shared) }
                            .font(.subheadline.weight(.semibold)).padding(.horizontal, 20).frame(minHeight: 48).modifier(LookGlass())
                        Spacer()
                    }
                    Spacer(minLength: 12)
                    Text("Hình nhân minh họa · camera demo").font(.caption).multilineTextAlignment(.center)
                    LookPresetPicker(model: model)
                    HStack {
                        cameraButton("Mở lịch sử", icon: "clock.arrow.circlepath", action: onHistory)
                        Spacer()
                        Button { model.send(LookActionCapture.shared) } label: {
                            Circle().fill(.white).padding(7).overlay(Circle().stroke(.white, lineWidth: 3)).frame(width: 80, height: 80)
                        }.accessibilityLabel("Chụp kết quả mẫu")
                        Spacer()
                        Color.clear.frame(width: 48, height: 48).accessibilityHidden(true)
                    }.padding(.top, 4)
                    Text("Chụp ảnh mẫu").font(.caption)
                }.padding(.horizontal, 24).padding(.vertical, 12).foregroundStyle(.white)
            }
        }
    }

    private func cameraButton(_ label: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) { Image(systemName: icon).frame(width: 48, height: 48).modifier(LookGlass()) }
            .buttonStyle(.plain).accessibilityLabel(label)
    }
}
