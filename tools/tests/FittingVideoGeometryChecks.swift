import CoreGraphics
import Foundation

@main enum FittingVideoGeometryChecks {
    static func displayed(_ point: CGPoint, in source: CGSize, viewport: CGSize) -> CGPoint {
        let scale = max(viewport.width / source.width, viewport.height / source.height)
        return CGPoint(x: (point.x - source.width / 2) * scale + viewport.width / 2,
                       y: (point.y - source.height / 2) * scale + viewport.height / 2)
    }

    static func main() {
        // Camera preview -> resized fitting input -> fullscreen result must leave
        // face/body landmarks in the same screen positions, including rotation.
        for sensor in [CGSize(width: 1920, height: 1080), CGSize(width: 1280, height: 720),
                       CGSize(width: 3840, height: 2160), CGSize(width: 4032, height: 3024),
                       CGSize(width: 1080, height: 1920)] {
            let source = CGSize(width: sensor.height, height: sensor.width)
            let geometry = FittingVideoGeometry(sensorSize: sensor)
            precondition(geometry.width % 4 == 0 && geometry.height % 4 == 0)
            precondition(geometry.width * geometry.height >= 200_000)
            precondition(max(geometry.width, geometry.height) == 640)
            let extent = CGRect(origin: .zero, size: source)
            for viewport in [CGSize(width: 393, height: 852), CGSize(width: 844, height: 390)] {
                for fraction in [CGPoint(x: 0.5, y: 0.15), CGPoint(x: 0.25, y: 0.5), CGPoint(x: 0.5, y: 0.9)] {
                    let point = CGPoint(x: source.width * fraction.x, y: source.height * fraction.y)
                    let before = displayed(point, in: source, viewport: viewport)
                    let after = displayed(point.applying(geometry.transform(for: extent)), in: geometry.size, viewport: viewport)
                    precondition(abs(before.x - after.x) < 0.01 && abs(before.y - after.y) < 0.01,
                                 "Fitting changed the camera field of view: \(sensor)")
                }
            }
        }
        let portrait = FittingVideoGeometry(sensorSize: CGSize(width: 1920, height: 1080))
        precondition(portrait.width == 360 && portrait.height == 640)
        for invalid in [nil, CGSize.zero, CGSize(width: CGFloat.infinity, height: 100)] {
            let fallback = FittingVideoGeometry(sensorSize: invalid)
            precondition(fallback.width == 360 && fallback.height == 640)
        }
        print("Fitting camera geometry PASS: unchanged field of view across preview and fitting video")
    }
}
