import SwiftUI
import PhotosUI
import AVFoundation
import Shared

struct WardrobeAddSheet: View {
    @ObservedObject var model: ShadowModel
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var category = "top"
    @State private var selection: PhotosPickerItem?
    @State private var jpeg: Data?
    @State private var reading = false
    @State private var photoError: String?
    @State private var takingPhoto = false
    @FocusState private var editingName: Bool
    private let accent = LookStyle.highlight
    private let categories = [("top", "Áo"), ("bottom", "Quần"), ("outerwear", "Áo khoác"),
                              ("dress", "Váy / đầm"), ("shoes", "Giày"), ("accessory", "Phụ kiện")]
    private var busy: Bool { !model.state.busy.isEmpty }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if let jpeg, let image = UIImage(data: jpeg) {
                        Image(uiImage: image).resizable().scaledToFit().frame(maxWidth: .infinity).frame(height: 240)
                            .accessibilityLabel("Ảnh trang phục đã chọn")
                    } else {
                        VStack(spacing: 12) {
                            Image(systemName: "tshirt").font(.system(size: 40, weight: .light))
                            Text("Chọn ảnh trang phục")
                        }.foregroundStyle(.secondary).frame(maxWidth: .infinity).frame(height: 160)
                    }
                    PhotosPicker(selection: $selection, matching: .images) {
                        Label("Chọn ảnh từ thư viện", systemImage: "photo.on.rectangle")
                    }.disabled(busy || reading)
                    Button { openCamera() } label: { Label("Chụp ảnh", systemImage: "camera") }
                        .disabled(busy || reading || !UIImagePickerController.isSourceTypeAvailable(.camera))
                    if reading { ProgressView("Đang chuẩn bị ảnh…") }
                    if !UIImagePickerController.isSourceTypeAvailable(.camera) {
                        Text("Thiết bị này không có camera. Bạn có thể chọn ảnh từ thư viện.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                } footer: { Text("Chụp rõ từng món trên nền đơn giản. Ảnh được lưu vào cùng tủ đồ với website.") }
                Section("Thông tin món đồ") {
                    TextField("Tên món đồ", text: $name).disabled(busy)
                        .focused($editingName).submitLabel(.done).onSubmit { editingName = false }
                        .onChange(of: name) { value in if value.count > 80 { name = String(value.prefix(80)) } }
                    Picker("Loại đồ", selection: $category) {
                        ForEach(categories, id: \.0) { value in Text(value.1).tag(value.0) }
                    }.disabled(busy)
                }
                if let error = photoError ?? model.state.error { Text(error).foregroundStyle(.red) }
                if busy { ProgressView("Đang thêm đồ vào tủ…") }
                Button {
                    guard let jpeg else { return }
                    model.send(ShadowActionAddWardrobeItem(image: "data:image/jpeg;base64," + jpeg.base64EncodedString(), name: name, category: category))
                } label: {
                    Text("Lưu vào tủ đồ").font(.headline).frame(maxWidth: .infinity, minHeight: 36)
                }.listRowBackground(accent).foregroundStyle(.black)
                    .disabled(jpeg == nil || name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || busy || reading || !model.state.connected)
            }
            .navigationTitle("Thêm quần áo").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Hủy") { dismiss() }.disabled(busy) } }
            .interactiveDismissDisabled(busy)
            .task(id: selection) {
                guard let selected = selection else { return }
                reading = true; photoError = nil; jpeg = nil
                defer { reading = false }
                do {
                    guard let data = try await selected.loadTransferable(type: Data.self) else { throw WardrobePhotoError.invalid }
                    let prepared = try await Task.detached(priority: .userInitiated) { try ShadowPhoto.prepare(data) }.value
                    try Task.checkCancellation()
                    jpeg = prepared
                } catch is CancellationError { }
                catch { photoError = "Không đọc được ảnh này. Hãy chọn một ảnh khác." }
            }
            .onChange(of: model.state.wardrobeAddedId) { _ in finishIfSaved() }
            .onChange(of: model.state.busy) { _ in finishIfSaved() }
            .fullScreenCover(isPresented: $takingPhoto) {
                WardrobeCameraPicker { image in
                    takingPhoto = false
                    guard let image else { return }
                    photoError = nil
                    let scale = min(1, 1024 / max(image.size.width, image.size.height))
                    let size = CGSize(width: max(1, image.size.width * scale), height: max(1, image.size.height * scale))
                    let format = UIGraphicsImageRendererFormat(); format.scale = 1
                    let resized = UIGraphicsImageRenderer(size: size, format: format).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
                    jpeg = resized.jpegData(compressionQuality: 0.85)
                    if jpeg == nil { photoError = "Chưa xử lý được ảnh. Bạn thử chụp lại nhé." }
                }.ignoresSafeArea()
            }
        }.preferredColorScheme(.dark).tint(accent)
    }

    private func finishIfSaved() { if model.state.wardrobeAddedId != nil && !busy { dismiss() } }
    private func openCamera() {
        AVCaptureDevice.requestAccess(for: .video) { allowed in
            DispatchQueue.main.async {
                if allowed { takingPhoto = true }
                else { photoError = "Cho phép camera trong Cài đặt, hoặc chọn ảnh từ thư viện." }
            }
        }
    }
}

private enum WardrobePhotoError: Error { case invalid }
private struct WardrobeCameraPicker: UIViewControllerRepresentable {
    let onFinish: (UIImage?) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(onFinish) }
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let controller = UIImagePickerController()
        controller.sourceType = .camera
        controller.cameraCaptureMode = .photo
        controller.delegate = context.coordinator
        return controller
    }
    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onFinish: (UIImage?) -> Void
        init(_ onFinish: @escaping (UIImage?) -> Void) { self.onFinish = onFinish }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { onFinish(nil) }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            onFinish(info[.originalImage] as? UIImage)
        }
    }
}
