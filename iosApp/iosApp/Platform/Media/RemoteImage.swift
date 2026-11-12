import SwiftUI
import UIKit

struct RemoteImage: View {
    @EnvironmentObject private var language: AppLanguage
    let url: String
    let headers: [String: String]
    var height: CGFloat = 120
    var fit = false

    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                if fit { Image(uiImage: image).resizable().scaledToFit() }
                else { Image(uiImage: image).resizable().scaledToFill() }
            } else {
                Color.white.opacity(0.08)
            }
        }
        .frame(height: height)
        .frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .task(id: url) { image = nil; await load() }
    }

    private func load() async {
        guard image == nil, let target = URL(string: url) else { return }
        var request = URLRequest(url: target)
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
        guard let (data, _) = try? await URLSession.shared.data(for: request),
              let decoded = UIImage(data: data) else { return }
        image = decoded
    }
}

/// Live preview for a `ShadowCamera`.
