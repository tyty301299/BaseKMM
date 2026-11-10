# Chuẩn bị môi trường BaseKMM trên macOS

Project đã khai báo Kotlin 2.2.10. Không cần cài `kotlinc` riêng: Gradle tải compiler và plugin theo version catalog. Cần JDK 17 để chạy Gradle và Android SDK 35 để cấu hình các module. Xcode cũng gọi Gradle để tạo framework KMM.

## Cài trong thư mục project

Chạy trong **Terminal có mạng**:

```sh
cd /Users/tynguyen/Desktop/codexKMM/BaseKMM
python3 tools/setup_mobile_env.py --with-emulator
```

Script tìm JDK 17 có sẵn; nếu chưa có, tải Temurin 17 theo kiến trúc máy từ metadata chính thức của Adoptium và kiểm tra SHA-256. Android command-line tools được chọn từ repository XML chính thức của Google và kiểm tra checksum do repository cung cấp. Không tắt xác minh TLS.

Tất cả công cụ/cache mới nằm trong `.toolchains/`, đã được bỏ qua bởi Git. Không sửa shell profile toàn máy hoặc thay đổi `xcode-select`. Bộ cài:

- Chuẩn bị JDK 17, Android command-line tools, SDK Platform 35, Build Tools 35.0.0 và platform-tools.
- Với `--with-emulator`, thêm Emulator, system image Google APIs API 35 đúng kiến trúc và AVD `Shadow_API_35` nếu chưa có.
- Để người chạy tự đọc/chấp nhận Android SDK licenses trong Terminal; không tự động trả lời đồng ý.
- Ghi `.toolchains/env.sh`. Chỉ tạo `local.properties` nếu chưa tồn tại; file có sẵn được giữ nguyên. Nếu có `sdk.dir` cũ, kiểm tra đường dẫn đó trước khi build.

Nếu mạng lỗi, script trả mã lỗi và không thông báo cài thành công. Archive tải dở không được coi là bản cài hợp lệ. Có thể chạy lại cùng lệnh.

## Build

```sh
./tools/build-mobile.sh android
./tools/build-mobile.sh ios
```

Android chạy unit tests shared rồi assembleDebug. iOS dùng xcodebuild cho simulator và không yêu cầu signing. Build phase Xcode cũng source `tools/mobile-env.sh`, nên dùng được JDK cục bộ khi mở project bằng Xcode.

Để chạy Gradle trực tiếp trong cùng Terminal:

```sh
. ./tools/mobile-env.sh
./gradlew :androidApp:assembleDebug
```

## Chạy simulator/emulator sau khi build thành công

Android, Terminal thứ nhất:

```sh
cd /Users/tynguyen/Desktop/codexKMM/BaseKMM
. ./tools/mobile-env.sh
emulator -avd Shadow_API_35
```

Terminal thứ hai, sau khi emulator đã lên màn hình chính:

```sh
cd /Users/tynguyen/Desktop/codexKMM/BaseKMM
. ./tools/mobile-env.sh
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n com.example.kmmbase.android/.app.MainActivity
```

Nếu có nhiều thiết bị, thêm `-s <serial>` cho adb sau khi kiểm tra `adb devices`.

iOS: mở `iosApp/iosApp.xcodeproj` bằng bản Xcode đầy đủ, cài một iOS Simulator runtime nếu thiếu, chọn scheme `iosApp` và iPhone simulator rồi Run. Script cài JDK/SDK không thay thế Xcode hoặc dịch vụ CoreSimulator.

## Trạng thái lần kiểm tra này

- Cập nhật: JDK Temurin 17.0.21 đã chạy được trong `.toolchains/jdk`; Android command-line tools 23.0 đã được giải nén từ gói tải sẵn và cấu hình trong `.toolchains/env.sh`. SDK Platform 35, Emulator và system image vẫn chưa được cài.
- Build Android đã qua bước khởi động Java nhưng dừng tải Gradle 8.14.3 với `UnknownHostException: services.gradle.org`. Bộ công cụ chưa hoàn chỉnh và app chưa được chạy.
- Google Android repository và Gradle distribution host cũng không truy cập được từ phiên này.
- Chỉ kiểm tra được script bằng kiểm thử offline, cú pháp shell/Python và cấu hình plist Xcode. Chưa có build app hay chạy simulator thành công.
