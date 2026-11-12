# Cấu trúc logic Android + iOS

Dự án giữ Kotlin Multiplatform cho nghiệp vụ và native UI trên hai nền tảng. Các chức năng hiện tại: kết nối SHADOW, danh sách/hỏi đáp kỹ năng, dạy bằng camera và giọng nói, hướng dẫn gấp áo/gấp quần/gói quà, tủ đồ/gợi ý phối đồ, thử đồ ảnh, thử áo live, lịch sử, notes và các màn Studio/Looks mẫu.

## Folder và trách nhiệm

```text
shared/src/commonMain/kotlin/com/example/kmmbase/
├── domain/
│   ├── model/          # Model theo chủ đề: Teach, Styling, Wardrobe, PhotoTryOn…
│   ├── repository/     # Interface theo khả năng: Teach, Skill, Wardrobe, PhotoTryOn…
│   └── usecase/        # Quy tắc: tạo hướng dẫn có sẵn, chuẩn bị phiên live, logic demo
├── data/
│   ├── dto/
│   │   ├── shadow/     # DTO HTTP, dạy kỹ năng, ảnh thử đồ và sự kiện WebSocket
│   │   └── live/       # DTO cấu hình/phiên/protocol live
│   ├── mapper/
│   │   ├── shadow/     # DTO → domain; domain → payload; bảo toàn metadata kỹ năng
│   │   └── livetryon/  # DTO live → domain
│   ├── remote/
│   │   ├── shadow/     # ShadowApi contract, Ktor HTTP/WS, xác thực endpoint
│   │   └── live/       # API live và adapter WebSocket Vidu
│   ├── local/          # Realm database và entity; không xuất managed object ra UI
│   └── repository/
│       ├── shadow/
│       ├── livetryon/
│       ├── looks/
│       ├── studio/
│       ├── notes/
│       └── greeting/
├── presentation/       # guide, teach, shadow, livetryon, looks, studio, notes
│                       # State/Action/Effect tách khỏi Store khi có tương ứng
├── platform/           # Hợp đồng Speaker, SpeechListener, FrameSource; tracing
└── di/                 # AppContainer, AppServices, LocalDataContainer

androidApp/…/android/
├── app/
├── navigation/
├── di/                 # NativeServices: chọn TTS/STT/video adapter
├── platform/           # camera, speech, media, vision, video
├── common/             # Tiện ích UI dùng chung, ngôn ngữ, ViewModel wrapper
└── features/<feature>/
    ├── presentation/   # Screens, components, Compose state binding
    └── data/           # Khi feature có SDK riêng, ví dụ Agora live

iosApp/iosApp/
├── App/
├── Navigation/
├── DI/                 # NativeServices
├── Platform/           # Camera, Speech, Media, Video
├── Common/             # Tiện ích UI dùng chung
└── Features/<Feature>/
    ├── Presentation/   # View, Model; Screens/Components khi nhiều màn
    └── Data/           # SDK riêng của feature, ví dụ Agora live
```

Xcode navigator phản ánh folder thật. File Swift phải có đúng một file reference và một build membership. Không tự tạo thêm Domain/Data trong mọi feature native: logic dùng chung đã nằm ở shared.

## Luồng phụ thuộc

`View → Action → Store → UseCase/Repository interface → Repository implementation → API/SDK/database`

- Domain không import Data, Ktor, Realm hay JSON serialization.
- Presentation không đọc/ghi JSON hoặc phụ thuộc implementation Data. CRUD đơn giản gọi repository interface trực tiếp; use case chỉ dành cho quy tắc nghiệp vụ.
- `ShadowRepository` là facade cho phiên server, kết hợp các contract nhỏ. `TeachStore` chỉ cần `TeachRepository`.
- `HttpShadowRepository` nhận `ShadowApi`, chuyển DTO qua mapper. `KtorShadowApi` xử lý wire format, HTTP và lỗi server.
- Socket decode thành `SessionEventDto`; mapper đổi sang `SessionEvent`. Store chỉ xử lý event domain.
- Dữ liệu kỹ năng do AI biên dịch có metadata detector/transition mở rộng. `TeachDraft.raw` là payload opaque: UI/Store không parse. Data sửa instruction theo ID, đồng bộ `on_enter_speech` khi instruction thay đổi và giữ nguyên trường không biết. Khi đổi backend có DSL khác, adapter mới phải chuyển phần metadata này.
- Các hướng dẫn có sẵn được khai báo bằng model domain; mapper tạo detector chain đúng schema server khi lưu.
- Local data dùng entity Realm, không cần thêm DTO HTTP giả cho các dữ liệu không qua mạng.

## Đổi dịch vụ ở đâu

| Thành phần | Contract | Điểm chọn implementation |
|---|---|---|
| API SHADOW / AI qua BE | `ShadowRepository`, các capability interface; `ShadowApi` ở Data | `DefaultAppServices.shadow` |
| Thử đồ ảnh | `PhotoTryOnRepository` | Adapter trong repository SHADOW; model AI thực tế do BE chọn |
| Thử áo live / control | `LiveTryOnRepository`, `LiveTryOnControl` | `DefaultAppServices.liveTryOn`; repository chọn control adapter |
| Video realtime | `LiveVideoEngine`, native `NativeLiveVideoEngine` có local/remote view | `NativeServices` trên từng nền tảng |
| Phát giọng | `Speaker` | `NativeServices`: AVSpeechSynthesizer trên iOS, TextToSpeech trên Android |
| Nhận giọng | `SpeechListener` | `NativeServices`; iOS bổ sung contract xin quyền native |
| Khung hình camera | `FrameSource` | CameraFrameBridge iOS / CameraFrameSource Android; preview vẫn là native |
| Lưu local | `LocalRepositories`, Note/Look/StudioRepository | `DefaultAppServices.openLocalData` |

`AppContainer` nhận `AppServices` qua constructor, có constructor mặc định để giữ cách tạo app hiện tại. Khi đổi provider, triển khai đúng contract, thay composition root và chạy contract tests. Đổi SDK video/camera vẫn cần adapter preview native tương ứng; DTO không làm hai SDK tự tương thích.

Đây là khả năng thay implementation có kiểm soát, **chưa có tự động failover giữa hai nhà cung cấp**. Không tự retry các thao tác có thể tạo phiên tính phí hoặc lưu trùng. Khóa bí mật của AI tiếp tục ở BE. Repo BE không được chỉnh trong lần tổ chức này.

## Kiểm tra

```sh
python3 tools/check_architecture.py
. ./tools/mobile-env.sh
./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug
```

`ShadowDataContractTest` kiểm tra chỉnh bước theo ID dù đảo thứ tự, giữ trường mở rộng, đồng bộ lời đọc, detector chain của ba hướng dẫn, speech epoch, media path, optional fields và sự kiện socket chưa biết. `TeachStoreTest` kiểm tra domain draft được chuyển cho repository; mapping JSON được kiểm tra tại Data.

Build iOS Simulator qua scheme `iosApp` với `CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=-`. Kiểm tra kiến trúc chỉ xác nhận ranh giới phụ thuộc và source membership; build/unit tests không chứng minh camera, giọng nói hoặc chất lượng AI trên điện thoại thật. Test live server vẫn phải bật opt-in riêng. Các màn mẫu local và giới hạn backend đã nêu trong `SHADOW_MOBILE.md` / `NATIVE_LIVE_TRYON.md` vẫn áp dụng.
