import SwiftUI
import UIKit

// Product tokens shared by the native feature; camera chrome keeps contrast over imagery.
enum LookStyle {
    static let background = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(red: 16/255, green: 19/255, blue: 15/255, alpha: 1) : UIColor(red: 245/255, green: 243/255, blue: 238/255, alpha: 1) })
    static let panel = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(red: 29/255, green: 35/255, blue: 27/255, alpha: 1) : .white })
    static let accent = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(red: 1, green: 210/255, blue: 63/255, alpha: 1) : UIColor(red: 113/255, green: 88/255, blue: 0, alpha: 1) })
    static let highlight = Color(red: 1, green: 210/255, blue: 63/255)
}

private struct LookOperationBusyKey: EnvironmentKey { static let defaultValue = false }
private struct LookGlassEnabledKey: EnvironmentKey { static let defaultValue = true }
extension EnvironmentValues {
    var lookOperationBusy: Bool {
        get { self[LookOperationBusyKey.self] }
        set { self[LookOperationBusyKey.self] = newValue }
    }
    var lookGlassEnabled: Bool {
        get { self[LookGlassEnabledKey.self] }
        set { self[LookGlassEnabledKey.self] = newValue }
    }
}

struct LookGlass: ViewModifier {
    @Environment(\.lookGlassEnabled) private var glassEnabled
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    @ViewBuilder func body(content: Content) -> some View {
        if reduceTransparency || !glassEnabled {
            content.background(LookStyle.panel, in: Capsule())
        } else {
            if #available(iOS 26.0, *) {
                content.glassEffect(.regular, in: .capsule)
            } else {
                content.background(.ultraThinMaterial, in: Capsule())
            }
        }
    }
}

struct LookPrimaryButton: View {
    let title: String
    var disabled = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title).font(.headline).frame(maxWidth: .infinity, minHeight: 44)
        }
        .buttonStyle(.borderedProminent).buttonBorderShape(.capsule)
        .tint(LookStyle.highlight).foregroundStyle(.black).disabled(disabled)
    }
}
