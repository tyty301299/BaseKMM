import Foundation

/// Only identifiers and route parameters cross native navigation boundaries.
enum StudioRoute: Hashable {
    case addItem, wardrobePermission, item(String), occasion, generating, suggestions, outfit, cameraIntro
    case teach, recording, compiling, review, saved(String), skill(String), guide, ask
    case settings, components
}
