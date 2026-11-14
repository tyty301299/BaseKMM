# KmmBase

> **Màn chính mới:** Camera toàn màn hình với chữ trò chuyện trong suốt, dựng bằng **Compose trên Android và SwiftUI trên iOS**. App gọi API backend trực tiếp. Mặc định nối `https://hackathon.trioxstudios.com`; nút Cài đặt trên camera cho đổi Base URL và mã truy cập. Xem [chạy local, kiến trúc và phạm vi kiểm tra](docs/NATIVE_CAMERA.md).
> **Thêm đồ trên điện thoại:** Tủ đồ → + Thêm đồ từ điện thoại → chụp/chọn ảnh → nhập tên và loại → Lưu vào tủ đồ. Android/iOS gọi trực tiếp API BE, dùng chung dữ liệu với website.
> Các màn native Phối đồ, Tủ đồ, Học & làm, Lịch sử và logic shared hiện có được giữ trong source.
> Chọn **Phối đồ cho hôm nay** để nhập ảnh người hoặc chọn một khung hình từ video, lấy bộ đồ gợi ý và tạo ảnh mặc thử. **SKI trực tuyến** có **Dạy** (camera + giọng nói) và **Hướng dẫn** (camera + WebSocket).
> Settings có trạng thái kết nối/reconnect và ngôn ngữ Việt/English. Các luồng mẫu local được ghi rõ; video nhập vào chỉ tạo ảnh từ khung hình, chưa xuất video thử đồ.
> Xem [giao thức, kiến trúc, cách build và những gì chưa kiểm tra](docs/SHADOW_MOBILE.md).
> **Thử áo live native (Android/iOS):** Camera → Thay đồ → Thử áo trực tiếp. Dùng Agora/Vidu, có DTO và Clean Architecture dùng chung; xem [cấu trúc và trạng thái kiểm chứng](docs/NATIVE_LIVE_TRYON.md). Server hiện báo live chưa khả dụng, chưa nghiệm thu trên máy thật.
> Các màn mẫu cũ vẫn còn làm dự phòng: [SHADOW_NATIVE_UI.md](docs/SHADOW_NATIVE_UI.md).
> **Chưa chạy trên điện thoại thật** — camera, TTS/STT tiếng Việt mới chỉ qua biên dịch và simulator.
> Các kết quả template ở cuối README không áp dụng cho phần SKI.

Base Kotlin Multiplatform cho Android và iOS, dùng chung logic và UI native riêng: Jetpack Compose trên Android, SwiftUI trên iOS. Base không dùng Compose Multiplatform.

## Xem UI HTML / prototype

- **[Xem PDF giao diện & flow iOS / Android (22 trang)](output/pdf/SHADOW_Full_App_Screen_Flow_iOS_Android.pdf)** - 54 artboard, 2 sơ đồ luồng và danh mục 27 màn hình mỗi nền tảng. Mở file PDF để xem trực tiếp trên GitHub hoặc chọn Download để tải về, không cần chạy HTML/GitHub Pages.
- [Mở trang chọn prototype iOS / Android](design/shadow-mobile/start.html)
- [Thử UI iOS](design/shadow-mobile/ios-prototype.html)
- [Thử UI Android](design/shadow-mobile/android-prototype.html)
- [Xem sơ đồ screen flow](design/shadow-mobile/flow.svg)

Prototype có các luồng tủ đồ, phối đồ, camera thử đồ toàn màn hình, lịch sử và học/làm theo. Camera và AI được mô phỏng; dữ liệu demo được lưu local trong trình duyệt.

Mở trực tiếp trên macOS, từ thư mục `BaseKMM`:

```sh
open design/shadow-mobile/start.html
```

Khi xem README trên GitHub, link file HTML phía trên mở mã nguồn. Workflow **SKI HTML Preview** xuất artifact `shadow-html-demo` để tải về và deploy giao diện lên GitHub Pages.

### Bật link UI trên GitHub Pages

1. Trong repo, mở **Settings → Pages → Build and deployment → Source → GitHub Actions**. Repo phải có quyền sử dụng Pages theo gói GitHub hiện tại.
2. Push thay đổi demo lên `feature/shadow-mobile` hoặc `main`. Workflow tự chạy khi HTML/assets hoặc workflow thay đổi. Khi workflow đã có trên nhánh mặc định, có thể chạy thủ công trong **Actions → SKI HTML Preview → Run workflow**.
3. Nếu environment `github-pages` giới hạn nhánh, cho phép nhánh đang deploy trong **Settings → Environments → github-pages**.
4. Sau khi job `deploy` thành công, mở link **Open UI**, **iOS prototype** hoặc **Android prototype** trong Summary của lần chạy. Đây là link web hiển thị UI, không phải trang source code.

Job `build` vẫn xuất artifact HTML nếu bước deploy bị chặn do Pages chưa bật. Tải `shadow-html-demo`, giải nén và mở `index.html`. Link web chỉ hoạt động sau khi deploy thành công; camera/AI trong demo vẫn là mô phỏng. Các lần deploy từ hai nhánh dùng chung một site, lần deploy thành công gần nhất sẽ được hiển thị.

Xem [toàn bộ sơ đồ App–BE–AI, model và checklist test chức năng](docs/APP_BE_ALL_FLOWS.md).

Luồng [học từ video gốc](docs/VIDEO_TEACH.md) đã có source Android/iOS/BE: upload, xử lý, mở lại bản nháp, sửa/verify và lưu. Cần deploy BE mới và nghiệm thu AI trên máy thật.

## Cây thư mục

Xem [cấu trúc folder, DTO, Clean Architecture và cách thay provider](docs/ARCHITECTURE.md).

- `shared/domain`: model, repository interface và use case.
- `shared/data`: DTO, mapper, remote API/socket, local entity và repository implementation theo chức năng.
- `shared/presentation/<feature>`: State, Action, Effect và Store.
- `shared/di`: cấu hình dịch vụ; `shared/platform`: hợp đồng camera/giọng nói.
- Android: `features/<feature>/presentation`, `platform/<capability>`, `di`.
- iOS: `Features/<Feature>/Presentation`, `Platform/<Capability>`, `DI`; Model tách khỏi View của Guide/Teach.

Thêm Swift file vào target Xcode. Chạy `python3 tools/check_architecture.py` để kiểm tra chiều phụ thuộc, package và source membership.

## Kiến trúc

Clean Architecture bản gọn, dữ liệu đi một chiều:

```text
SwiftUI / Compose ── Action ──▶ Store ──▶ UseCase ──▶ Repository ──▶ Realm
        ▲                         │
        └──────── State ──────────┘
```

| Tầng | Vị trí | Được phép phụ thuộc |
|---|---|---|
| UI | `androidApp`, `iosApp` | `presentation` |
| Presentation | `shared/presentation` | `domain` |
| Domain | `shared/domain` | Không phụ thuộc tầng nào khác |
| Data | `shared/data` | `domain` |
| DI | `shared/di` | Tất cả |

### Rule cho action và state

1. Action mô tả việc người dùng làm, ví dụ `Delete(id)`, `Retry`. UI không gọi thẳng Repository.
2. Mỗi màn hình có đúng một `State`. UI chỉ tự giữ thứ thuộc về hiển thị, như vị trí cuộn hay animation.
3. Việc chỉ xảy ra một lần dùng `Effect`, ví dụ lưu xong thì quay lại.
4. Store là class Kotlin thường và là `AutoCloseable`. Android bọc nó trong `ViewModel` để sống qua xoay màn hình, iOS bọc trong `ObservableObject`.
5. Chỉ tạo UseCase khi có quy tắc nghiệp vụ hoặc cần ghép nhiều nguồn dữ liệu. CRUD đơn giản cho Store gọi thẳng Repository.

Mẫu đầy đủ: `presentation/notes/NoteListStore.kt` và `NoteEditStore.kt`.

### Rule chuyển màn hình

1. Navigation nằm ở từng nền tảng: `navigation/AppNavHost.kt` trên Android, `Navigation/RootView.swift` trên iOS. `shared` không biết gì về màn hình.
2. Chỉ truyền id hoặc giá trị đơn giản, không truyền cả object.
3. Màn hình đích tự lấy dữ liệu theo id qua Store.
4. Màn hình không tự điều hướng, chỉ gọi callback như `onOpenNote(id)`.
5. Không truyền kết quả ngược lại: màn hình sửa lưu vào Realm, màn hình trước tự cập nhật qua `Flow`.
6. Id không còn dữ liệu thì hiện trạng thái "không tìm thấy", không được crash.

### Gọi Kotlin từ Swift

- Hàm `suspend` có `@Throws` được gọi bằng `try await`, lỗi Kotlin trở thành Swift error.
- `Flow` được nghe qua `observeState` và `observeEffects`, trả về `StoreSubscription` để hủy. Callback chạy trên main thread.

## Công cụ

Cấu hình cố định: Kotlin 2.2.10, AGP 8.10.1, Gradle 8.14.3, JDK 17, Android SDK 35.
Android min SDK 24; iOS deployment target 16.0. Các target iOS: thiết bị ARM64, simulator Apple Silicon và simulator Intel.

Realm fork 3.2.9 ghim Kotlin 2.2.10, nên không nâng Kotlin khi chưa có bản Realm tương ứng.

## Cài môi trường tự động

Chạy `python3 tools/setup_mobile_env.py --with-emulator` từ thư mục project trong Terminal có mạng. Xem [cài JDK/SDK, build và chạy simulator](docs/MOBILE_SETUP.md). Kotlin đã khai báo trong Gradle; không cần cài compiler riêng.

## Chạy Android

1. Cài JDK 17 và Android SDK Platform 35, qua Android Studio hoặc command-line tools.
2. Tạo `local.properties` tại root với `sdk.dir=<đường dẫn Android SDK>`. Không commit file này.
3. Mở thư mục `KmmBase` bằng Android Studio và chạy module `androidApp`, hoặc dùng lệnh:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug
```

## Chạy iOS

1. Cần JDK 17 và Android SDK như trên để Gradle cấu hình toàn bộ project.
2. Mở `iosApp/iosApp.xcodeproj`, chọn scheme `iosApp` và một iPhone simulator.
3. Run. Build phase `Compile Kotlin Framework` tự chạy `:shared:embedAndSignAppleFrameworkForXcode`.
4. Nếu chạy thiết bị thật: chọn Development Team trong Signing & Capabilities và đổi bundle identifier.

```sh
./gradlew :shared:iosSimulatorArm64Test # Mac Apple Silicon
./gradlew :shared:iosX64Test            # Mac Intel
```

Lần build đầu cần internet để tải Gradle, dependencies và Kotlin/Native.

## Trước khi dùng cho app thật

Đổi package `com.example.kmmbase`, Android applicationId và iOS bundle identifier.

## Quyền camera, micro và âm thanh

- Android (`AndroidManifest.xml`): `CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`. Camera và micro khai báo là phần cứng tùy chọn.
- iOS (`project.pbxproj`, build settings `INFOPLIST_KEY_*`): `NSCameraUsageDescription`, `NSMicrophoneUsageDescription`. Sửa nội dung thông báo theo mục đích thật của app trước khi lên App Store.

Đây mới là phần khai báo. Trước khi dùng camera hoặc micro vẫn phải xin quyền lúc chạy: Android dùng `rememberLauncherForActivityResult` với `RequestPermission`, iOS dùng `AVCaptureDevice.requestAccess`.
Phát âm thanh thông thường không cần quyền. Nếu cần phát nhạc khi app ở nền, iOS phải thêm `UIBackgroundModes` là `audio`, Android phải thêm foreground service.

## Realm local data

Dùng **fork Infomaniak Realm Kotlin 3.2.9**, là dependency bên thứ ba, không phải artifact `io.realm.kotlin` gốc: https://github.com/Infomaniak/realm-kotlin. Không cấu hình cloud sync.

- `AppContainer.openLocalData()`: suspend, chỉ mở database ở lần gọi đầu và chạy ngoài main thread.
- `RealmNoteRepository`: upsert theo primary key, query có tham số, delete, observe. Trả domain model, không đưa managed Realm object ra UI.
- Hủy các job và Flow collector trước khi gọi `AppContainer.close()`.

Khi đổi schema, tăng `schemaVersion` và thêm migration khi cần chuyển đổi dữ liệu. Không bật xóa database khi migration lỗi.

## Trạng thái kiểm tra

Đã kiểm tra trên Mac Intel, Xcode 26.5, JDK 17:

- `:shared:testAndroidHostTest`: 8 test pass.
- `:shared:iosX64Test`: 9 test pass, gồm test Realm.
- `:androidApp:assembleDebug` và `xcodebuild` cho iPhone simulator build thành công.
- App mở được màn hình Home trên Android emulator API 35 và iPhone 17 Pro simulator iOS 26.5.

Chưa kiểm tra target `iosSimulatorArm64` trên Mac Apple Silicon và chưa chạy trên thiết bị thật.

Chuẩn bị bản iOS nội bộ: [TestFlight](docs/TESTFLIGHT.md).
