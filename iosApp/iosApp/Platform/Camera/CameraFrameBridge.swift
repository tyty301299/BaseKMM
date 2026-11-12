import Shared

/// Adapts `ShadowCamera` to the Kotlin `FrameSource` the guide and teach stores expect.
final class CameraFrameBridge: NSObject, FrameSource {
    private let camera: ShadowCamera

    init(camera: ShadowCamera) {
        self.camera = camera
    }

    func capture(maxSide: Int32) async throws -> String? {
        camera.capture(maxSide: Int(maxSide))
    }
}
