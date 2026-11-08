import Shared

extension AppError {
    var message: String {
        switch self {
        case .notFound: return "Không tìm thấy ghi chú."
        case .network: return "Không kết nối được máy chủ. Hãy thử lại."
        case .camera: return "Chưa có hình từ camera. Hãy thử lại."
        default: return "Không thể truy cập dữ liệu. Hãy thử lại."
        }
    }
}
