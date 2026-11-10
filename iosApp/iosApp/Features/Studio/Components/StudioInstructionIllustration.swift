import SwiftUI

/// Task illustration, not a live camera preview or AI assessment.
struct StudioInstructionIllustration: View {
    var body: some View {
        Canvas { context, size in
            let scale = min(size.width / 320, size.height / 220)
            context.translateBy(x: (size.width - 320 * scale) / 2, y: (size.height - 220 * scale) / 2)
            context.scaleBy(x: scale, y: scale)
            context.fill(Path(roundedRect: CGRect(x: 14, y: 139, width: 292, height: 66), cornerRadius: 14), with: .color(Color(red: 0.67, green: 0.60, blue: 0.49)))
            context.fill(Path(ellipseIn: CGRect(x: 90, y: 168, width: 142, height: 21)), with: .color(.black.opacity(0.18)))
            context.fill(Path(roundedRect: CGRect(x: 107, y: 162, width: 106, height: 15), cornerRadius: 7), with: .color(Color(red: 0.22, green: 0.29, blue: 0.22)))
            context.fill(Path(roundedRect: CGRect(x: 153, y: 70, width: 14, height: 100), cornerRadius: 6), with: .color(Color(red: 0.48, green: 0.56, blue: 0.46)))
            context.fill(Path(roundedRect: CGRect(x: 119, y: 41, width: 82, height: 50), cornerRadius: 9), with: .color(Color(red: 0.84, green: 0.87, blue: 0.80)))
            context.fill(Path(roundedRect: CGRect(x: 125, y: 47, width: 70, height: 38), cornerRadius: 6), with: .color(Color(red: 0.25, green: 0.34, blue: 0.25)))
            var brackets = Path()
            for (x, y, dx, dy) in [(CGFloat(32), CGFloat(34), CGFloat(20), CGFloat(20)), (288, 34, -20, 20), (32, 191, 20, -20), (288, 191, -20, -20)] {
                brackets.move(to: CGPoint(x: x, y: y+dy)); brackets.addLine(to: CGPoint(x: x, y: y)); brackets.addLine(to: CGPoint(x: x+dx, y: y))
            }
            context.stroke(brackets, with: .color(.white.opacity(0.8)), lineWidth: 2)
        }
        .frame(maxWidth: .infinity).frame(height: 220)
        .background(Color(red: 0.23, green: 0.29, blue: 0.23), in: RoundedRectangle(cornerRadius: 28))
        .accessibilityLabel("Minh họa lắp giá đỡ điện thoại, không phải camera thật")
    }
}
