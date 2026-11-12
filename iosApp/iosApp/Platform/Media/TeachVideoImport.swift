import SwiftUI
import UIKit
import UniformTypeIdentifiers
import Shared

final class IOSTeachVideoSource: NSObject, TeachVideoSource {
    private let file: FileHandle
    let localUri: String
    let sizeBytes: Int64
    init(url: URL) throws {
        localUri = url.absoluteString
        file = try FileHandle(forReadingFrom: url)
        sizeBytes = Int64(try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0)
        super.init()
    }
    func readChunk(maxBytes: Int32) async throws -> KotlinByteArray? {
        guard let bytes = try file.read(upToCount: Int(maxBytes)), !bytes.isEmpty else { return nil }
        let result = KotlinByteArray(size: Int32(bytes.count))
        for (index, byte) in bytes.enumerated() { result.set(index: Int32(index), value: Int8(bitPattern: byte)) }
        return result
    }
    func close() { try? file.close() }
}

final class IOSTeachJobPersistence: NSObject, TeachJobPersistence {
    private let key: String
    init(endpoint: String) { key = "shadow.video-job." + endpoint; super.init() }
    func load() -> TeachVideoTicket? {
        guard let value = UserDefaults.standard.dictionary(forKey: key),
              let id = value["id"] as? String, let token = value["token"] as? String else { return nil }
        return TeachVideoTicket(jobId: id, token: token, title: value["title"] as? String ?? "",
                                teacher: value["teacher"] as? String ?? "", notes: value["notes"] as? String ?? "", localUri: value["localUri"] as? String ?? "")
    }
    func save(ticket: TeachVideoTicket?) {
        guard let ticket else { UserDefaults.standard.removeObject(forKey: key); return }
        UserDefaults.standard.set(["id": ticket.jobId, "token": ticket.token, "title": ticket.title,
                                   "teacher": ticket.teacher, "notes": ticket.notes, "localUri": ticket.localUri], forKey: key)
    }
}

struct TeachVideoPicker: UIViewControllerRepresentable {
    let camera: Bool
    let completed: (URL?) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(completed) }
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = camera ? .camera : .photoLibrary
        picker.mediaTypes = [UTType.movie.identifier]
        picker.videoMaximumDuration = 180
        picker.videoQuality = .typeMedium
        picker.delegate = context.coordinator
        return picker
    }
    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let completed: (URL?) -> Void
        init(_ completed: @escaping (URL?) -> Void) { self.completed = completed }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { completed(nil) }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            completed(info[.mediaURL] as? URL)
        }
    }
}
