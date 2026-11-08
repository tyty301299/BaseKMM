import Shared

extension AppError {
    var message: String {
        self == AppError.notFound ? "Không tìm thấy ghi chú." : "Không thể truy cập dữ liệu. Hãy thử lại."
    }
}
