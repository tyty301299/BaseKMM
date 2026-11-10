package com.example.kmmbase.domain.usecase

/** Deterministic fixture for flow review; never represents AI analysis of a recording. */
class BuildDemoSkillUseCase {
    operator fun invoke(): List<String> = listOf(
        "Đặt chân đế trên mặt bàn phẳng.",
        "Gắn thanh đứng vào chân đế.",
        "Siết khóa để thanh đứng chắc chắn.",
        "Gắn đầu kẹp và điều chỉnh góc nhìn.",
        "Đặt điện thoại vào kẹp, kiểm tra độ vững.",
    )
}
