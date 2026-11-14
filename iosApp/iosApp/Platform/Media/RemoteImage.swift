import SwiftUI
import UIKit
import ImageIO
import CryptoKit

struct RemoteImage: View {
    let url: String
    let headers: [String: String]
    var height: CGFloat = 120
    var fit = false
    @State private var image: UIImage?

    private var pixels: Int { max(480, min(1600, Int(height * UIScreen.main.scale))) }
    private var requestKey: String {
        let auth = headers.sorted { $0.key < $1.key }.map { "\($0.key):\($0.value)" }.joined(separator: "\n")
        let hash = SHA256.hash(data: Data(auth.utf8)).map { String(format: "%02x", $0) }.joined()
        return "\(url)|\(pixels)|\(hash)"
    }
    var body: some View {
        Group {
            if let image {
                if fit { Image(uiImage: image).resizable().scaledToFit() }
                else { Image(uiImage: image).resizable().scaledToFill() }
            } else { Color.white.opacity(0.08) }
        }
        .frame(height: height).frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .task(id: requestKey) {
            image = nil
            let result = await WardrobeImageLoader.shared.load(url: url, headers: headers, key: requestKey, pixels: pixels)
            guard !Task.isCancelled else { return }
            image = result
        }
    }
}

/// Reuse wardrobe thumbnails across camera and wardrobe screens. Decode away from the UI thread.
private actor WardrobeImageLoader {
    static let shared = WardrobeImageLoader()
    private let cache = NSCache<NSString, UIImage>()
    private var loading: [String: Task<UIImage?, Never>] = [:]
    init() { cache.totalCostLimit = 32 * 1024 * 1024; cache.countLimit = 150 }

    func load(url: String, headers: [String: String], key: String, pixels: Int) async -> UIImage? {
        if let image = cache.object(forKey: key as NSString) { return image }
        if let existing = loading[key] { return await existing.value }
        guard let target = URL(string: url), ["https", "http"].contains(target.scheme) else { return nil }
        let task = Task<UIImage?, Never> {
            var request = URLRequest(url: target, timeoutInterval: 25)
            headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
            guard let (data, response) = try? await URLSession.shared.data(for: request),
                  let code = (response as? HTTPURLResponse)?.statusCode, (200..<300).contains(code) else { return nil }
            return await Task.detached(priority: .userInitiated) {
                guard let source = CGImageSourceCreateWithData(data as CFData, [kCGImageSourceShouldCache: false] as CFDictionary),
                      let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                        kCGImageSourceCreateThumbnailFromImageAlways: true,
                        kCGImageSourceCreateThumbnailWithTransform: true,
                        kCGImageSourceThumbnailMaxPixelSize: pixels,
                        kCGImageSourceShouldCacheImmediately: true,
                      ] as CFDictionary) else { return nil as UIImage? }
                return UIImage(cgImage: thumbnail)
            }.value
        }
        loading[key] = task
        let result = await task.value
        loading.removeValue(forKey: key)
        if let result, let cg = result.cgImage { cache.setObject(result, forKey: key as NSString, cost: cg.bytesPerRow * cg.height) }
        return result
    }
}
