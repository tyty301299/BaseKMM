package com.example.kmmbase.android.common

import com.example.kmmbase.domain.model.AppError

internal fun AppError.message(): String = when (this) {
    AppError.NOT_FOUND -> "Không tìm thấy ghi chú."
    AppError.STORAGE -> "Không thể truy cập dữ liệu. Hãy thử lại."
}
