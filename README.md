# KmmBase

Base Kotlin Multiplatform cho Android và iOS, dùng chung logic và UI native riêng: Jetpack Compose trên Android, SwiftUI trên iOS. Base không dùng Compose Multiplatform.

## Cây thư mục

```text
KmmBase/
├── shared/src/
│   ├── commonMain/kotlin/com/example/kmmbase/
│   │   ├── domain/                  # Không phụ thuộc tầng nào khác
│   │   │   ├── model/               # Note, AppError
│   │   │   ├── repository/          # Interface repository
│   │   │   └── usecase/             # Quy tắc nghiệp vụ
│   │   ├── data/
│   │   │   ├── local/               # Realm entity, cấu hình database
│   │   │   └── repository/          # Implementation của repository
│   │   ├── presentation/
│   │   │   ├── common/              # StoreSubscription cho Swift
│   │   │   └── notes/               # State, Action, Effect, Store theo màn hình
│   │   └── di/                      # AppContainer, LocalDataContainer
│   ├── commonTest/                  # Test chạy trên Android và iOS
│   └── iosTest/                     # Test Realm trên iOS
├── androidApp/src/main/kotlin/com/example/kmmbase/android/
│   ├── app/                         # Application, MainActivity
│   ├── navigation/                  # Route và AppNavHost
│   ├── common/                      # Giữ Store trong ViewModel, text lỗi
│   └── features/
│       ├── home/
│       └── notes/
├── iosApp/iosApp/
│   ├── App/                         # Điểm vào của app
│   ├── Navigation/                  # Route và RootView
│   ├── Common/                      # Text lỗi
│   └── Features/
│       ├── Home/
│       └── Notes/
├── server/                          # Backend FastAPI cho KitchenMate, giữ Gemini API key
│   ├── app/                         # api.py (endpoint), verify.py (kiểm tra ảnh), main.py
│   └── tests/
└── gradle/libs.versions.toml        # Phiên bản dependency
```

Thêm màn hình mới: tạo `presentation/<tên>/` trong `shared`, `features/<tên>/` trên Android và `Features/<Tên>/` trên iOS. File Swift mới phải được thêm vào target `iosApp` trong Xcode.

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

## KitchenMate AI

AI nhìn mặt bàn bếp qua camera, nói chuyện bằng tiếng Việt và hướng dẫn 3 bước: gom cốc, xếp bát, gom thìa. Mở từ nút **KitchenMate AI** ở màn hình Home.

```text
App ── POST /api/live-token ──▶ server ──▶ Gemini (tạo token dùng 1 lần)
App ◀──── WebSocket âm thanh, hình 1 fps ────▶ gemini-3.8-live
App ── POST /api/scan, /api/verify (ảnh, stepId) ──▶ server ──▶ gemini-3.8-flash
```

- `shared/presentation/kitchen/KitchenStore.kt`: luồng 3 bước, Before/After, nối giọng nói với kiểm tra ảnh.
- `shared/data/remote/KitchenRemote.kt`: gọi server và giao thức Gemini Live.
- `KitchenDevice`: camera, micro, loa làm native ở `AndroidKitchenDevice.kt` và `IosKitchenDevice.swift`.
- `server/app/verify.py`: điều kiện hoàn thành của từng bước. Id bước phải khớp `KitchenSteps` trong `shared`.

Mọi action đều cập nhật `KitchenState.output` (INFO, SUCCESS, WARNING, ERROR) và UI hiện thành banner, nên người dùng luôn biết action vừa làm ra kết quả gì.

Trên camera, khung xanh dương là từng cốc, bát, thìa nhận diện được (`objects`), khung xanh lá nét đứt là vị trí cần đặt đồ (`targetZone`), khung đỏ là món đang để sai (`misplaced`). App gọi `POST /api/scan` mỗi 5 giây để cập nhật; lần đầu khung xanh lá là vị trí mặc định của bước. Quét chỉ để vẽ, không dùng để kết luận bước đã xong. Khung trễ vài giây so với thực tế nên camera cần đặt cố định.

Chỉ kiểm tra khi người dùng nói "Xong rồi" hoặc bấm **Kiểm tra**. Mất giọng nói thì nút **Kiểm tra** vẫn dùng được.

### Chạy server

```sh
cd server
python3 -m venv .venv
.venv/bin/pip install -r requirements-dev.txt
cp .env.example .env   # điền GEMINI_API_KEY
.venv/bin/python -m uvicorn app.main:app --port 8787
.venv/bin/python -m pytest
```

Chạy không cần key: đặt `MOCK_GEMINI=1`. Khi đó mỗi bước sai ở lần kiểm tra đầu và đúng ở lần sau, còn giọng nói không hoạt động.

### Địa chỉ server

- Android emulator: mặc định `http://10.0.2.2:8787`. Điện thoại thật: build với `-PkitchenServerUrl=https://...`. Bản release bắt buộc HTTPS.
- iPhone simulator: mặc định `http://localhost:8787`. iPhone thật: đổi build setting `KITCHEN_SERVER_URL` thành URL HTTPS, hoặc `http://<IP của Mac>:8787` khi chung Wi-Fi và chạy uvicorn với `--host 0.0.0.0`.
- Simulator iOS không có camera nên dùng ảnh xám giả, chỉ để thử giao diện với `MOCK_GEMINI=1`. Nhận ảnh thật phải dùng iPhone thật.

Hai endpoint chưa có xác thực, chỉ dùng cho demo. Trước khi public phải thêm xác thực và giới hạn tần suất, nếu không ai cũng có thể dùng quota Gemini.

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
