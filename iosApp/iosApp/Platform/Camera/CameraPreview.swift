import AVFoundation
import SwiftUI
import UIKit

struct CameraPreview: UIViewRepresentable {
    let camera: ShadowCamera

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.layer.session = camera.captureSession
        view.layer.videoGravity = .resizeAspectFill
        view.camera = camera
        view.addGestureRecognizer(UITapGestureRecognizer(target: view, action: #selector(PreviewView.focus(_:))))
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {}

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        // swiftlint:disable:next force_cast
        override var layer: AVCaptureVideoPreviewLayer { super.layer as! AVCaptureVideoPreviewLayer }
        weak var camera: ShadowCamera?
        @objc func focus(_ gesture: UITapGestureRecognizer) {
            camera?.focus(at: layer.captureDevicePointConverted(fromLayerPoint: gesture.location(in: self)))
        }
    }
}
