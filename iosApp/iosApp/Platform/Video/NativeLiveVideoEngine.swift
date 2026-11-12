import Shared
import UIKit

protocol NativeLiveVideoEngine: LiveVideoEngine {
    var localView: UIView { get }
    var remoteView: UIView { get }
}
