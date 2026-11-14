import CoreGraphics

/// Keep the camera's field of view when preparing smaller frames for fitting.
/// A fixed 3:4 crop followed by the screen's aspect-fill crop magnifies 9:16 video.
struct FittingVideoGeometry {
    let width: Int
    let height: Int
    var size: CGSize { CGSize(width: width, height: height) }

    init(sensorSize: CGSize?) {
        // ShadowCamera delivers landscape sensor buffers, rotated right for display.
        let source = sensorSize.flatMap { value -> CGSize? in
            guard value.width.isFinite, value.height.isFinite, value.width > 0, value.height > 0 else { return nil }
            return CGSize(width: value.height, height: value.width)
        } ?? CGSize(width: 1080, height: 1920)
        let scale = 640 / max(source.width, source.height)
        width = max(4, Int((source.width * scale / 4).rounded()) * 4)
        height = max(4, Int((source.height * scale / 4).rounded()) * 4)
    }

    func transform(for extent: CGRect) -> CGAffineTransform {
        let scale = max(CGFloat(width) / extent.width, CGFloat(height) / extent.height)
        return CGAffineTransform(a: scale, b: 0, c: 0, d: scale,
                                 tx: CGFloat(width) / 2 - extent.midX * scale,
                                 ty: CGFloat(height) / 2 - extent.midY * scale)
    }
}
