import SwiftUI

/// Native vector fixture for design review, deliberately not presented as an AI photo.
struct LookMannequin: View {
    @EnvironmentObject private var language: AppLanguage
    var preset = "coat"
    var before = false
    var flipped = false

    var body: some View {
        Canvas { context, size in
            let scale = min(size.width / 345, size.height / 440)
            context.translateBy(x: (size.width - 345 * scale) / 2, y: (size.height - 440 * scale) / 2)
            context.scaleBy(x: scale, y: scale)
            if flipped { context.translateBy(x: 345, y: 0); context.scaleBy(x: -1, y: 1) }
            func polygon(_ points: [CGPoint], _ color: Color) {
                var path = Path()
                path.addLines(points); path.closeSubpath()
                context.fill(path, with: .color(color))
            }
            func points(_ values: [(CGFloat, CGFloat)]) -> [CGPoint] { values.map { CGPoint(x: $0.0, y: $0.1) } }
            let skin = Color(red: 0.86, green: 0.68, blue: 0.53)
            let shirt = before ? Color(red: 0.85, green: 0.83, blue: 0.76) : preset == "blouse"
                ? Color(red: 0.89, green: 0.65, blue: 0.51) : Color(red: 0.55, green: 0.66, blue: 0.56)
            context.fill(Path(ellipseIn: CGRect(x: 112, y: 411, width: 122, height: 16)), with: .color(.black.opacity(0.15)))
            polygon(points([(136,242),(208,242),(214,304),(195,408),(171,408),(178,298),(168,298),(162,408),(137,408),(127,304)]), Color(red: 0.20, green: 0.24, blue: 0.20))
            context.fill(Path(roundedRect: CGRect(x: 160, y: 89, width: 26, height: 38), cornerRadius: 10), with: .color(skin))
            context.fill(Path(ellipseIn: CGRect(x: 145, y: 36, width: 56, height: 70)), with: .color(skin))
            polygon(points([(146,70),(143,45),(153,31),(181,28),(198,40),(202,73),(195,48),(168,53),(149,52)]), Color(red: 0.24, green: 0.21, blue: 0.18))
            polygon(points([(128,118),(108,139),(87,253),(101,260),(134,160),(204,118),(226,139),(251,252),(237,258),(205,161)]), skin)
            polygon(points([(129,116),(157,108),(173,120),(188,108),(212,116),(229,139),(244,243),(225,247),(212,165),(212,262),(130,262),(127,165),(113,246),(93,242),(108,138)]), shirt)
            polygon(points([(157,108),(173,127),(156,146),(143,118)]), .white.opacity(0.2))
            polygon(points([(188,108),(173,127),(190,146),(202,117)]), .white.opacity(0.2))
            var seam = Path(); seam.move(to: CGPoint(x: 173, y: 129)); seam.addLine(to: CGPoint(x: 173, y: 262))
            context.stroke(seam, with: .color(.black.opacity(0.15)), lineWidth: 1)
            for y in [164,203,241] { context.fill(Path(ellipseIn: CGRect(x: 176, y: CGFloat(y), width: 3, height: 3)), with: .color(.black.opacity(0.4))) }
            polygon(points([(135,403),(161,403),(161,416),(121,416),(126,409)]), .white)
            polygon(points([(172,403),(196,403),(213,415),(172,416)]), .white)
        }
        .accessibilityLabel(language.text("ui_078"))
    }
}

/// The same clearly-labelled sample garment photography used in the approved PDF.
struct StudioGarmentImage: View {
    @EnvironmentObject private var language: AppLanguage
    var preset = "coat"
    var body: some View {
        Image(preset == "blouse" ? "ShadowBlouse" : "ShadowCoat")
            .resizable().scaledToFill()
            .accessibilityLabel(preset == "blouse" ? language.text("ui_011") : language.text("ui_208"))
    }
}
