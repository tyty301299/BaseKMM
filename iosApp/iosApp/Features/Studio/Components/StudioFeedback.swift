import SwiftUI
import Shared
import UIKit

final class StudioFeedback: ObservableObject {
    @Published private(set) var message: String?
    @Published private(set) var isError = false
    private var dismissTask: Task<Void, Never>?

    func show(_ feedback: UiMessage) {
        dismissTask?.cancel()
        message = feedback.text
        isError = feedback.isError
        UIAccessibility.post(notification: .announcement, argument: feedback.text)
        dismissTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            guard !Task.isCancelled else { return }
            self?.message = nil
        }
    }
    func dismiss() { dismissTask?.cancel(); message = nil }
    deinit { dismissTask?.cancel() }
}

struct StudioFeedbackOverlay: ViewModifier {
    @EnvironmentObject private var feedback: StudioFeedback
    func body(content: Content) -> some View {
        content.overlay(alignment: .top) {
            if let message = feedback.message {
                HStack(spacing: 12) {
                    Image(systemName: feedback.isError ? "exclamationmark.circle.fill" : "checkmark.circle.fill")
                        .foregroundStyle(feedback.isError ? Color.orange : LookStyle.accent)
                    Text(message).font(.subheadline).fixedSize(horizontal: false, vertical: true)
                    Button { feedback.dismiss() } label: { Image(systemName: "xmark").frame(width: 44, height: 44) }
                        .accessibilityLabel("Đóng thông báo")
                }
                .padding(.leading, 18).padding(.trailing, 4).padding(.vertical, 6)
                .modifier(LookGlass()).padding(.horizontal, 16).padding(.top, 8)
            }
        }
    }
}
