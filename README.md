# BaseKMM

Base Kotlin Multiplatform cho Android và iOS, dùng chung logic và UI native riêng.

## Cấu trúc

- `shared/src/commonMain`: domain (repository interface, use case), data (repository implementation), di (constructor injection).
- `shared/src/commonTest`: test xử lý tên rỗng, khoảng trắng và tiếng Việt.
- `androidApp`: Jetpack Compose, màn hình mẫu gọi shared use case.
- `iosApp/iosApp.xcodeproj`: project Xcode + SwiftUI, tự build framework Shared bằng Gradle build phase.
- `gradle/libs.versions.toml`: quản lý phiên bản dependency tập trung.

Luồng mẫu: UI → GetGreetingUseCase → GreetingRepository → DefaultGreetingRepository.
Đây là base tối thiểu; đã thêm Realm local database; chưa thêm API, authentication hoặc navigation khi chưa có yêu cầu nghiệp vụ.

## Công cụ

Cấu hình cố định: Kotlin 2.2.10, AGP 8.10.1, Gradle 8.14.3, JDK 17, Android SDK 35.
Android min SDK 24; iOS deployment target 16.0. Các target iOS hiện có: thiết bị ARM64 và simulator Apple Silicon.

Tham khảo tương thích: https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html
Kotlin 2.2.10 được liệt kê với Xcode 16.3; Xcode 26.5 trên máy hiện tại chưa được kiểm chứng build với cấu hình này.
Plugin Android KMP: https://developer.android.com/kotlin/multiplatform/plugin

## Chạy Android

1. Cài JDK 17 và Android Studio; cài Android SDK Platform 35 qua SDK Manager.
2. Mở thư mục `KmmBase` bằng Android Studio và chọn Gradle JDK 17.
3. Tạo `local.properties` tại root, đặt `sdk.dir` thành đường dẫn Android SDK trên máy. Không commit file này.
4. Sync Gradle và chạy cấu hình module `androidApp`.

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug
```

## Chạy iOS

1. Cần JDK 17 và Android SDK như trên để Gradle cấu hình toàn bộ project.
2. Mở `iosApp/iosApp.xcodeproj`, chọn scheme `iosApp` và iPhone simulator Apple Silicon.
3. Run. Build phase `Compile Kotlin Framework` chạy `:shared:embedAndSignAppleFrameworkForXcode` tự động.
4. Nếu chạy thiết bị thật: chọn Development Team trong Signing & Capabilities và đổi bundle identifier.

```sh
./gradlew :shared:iosSimulatorArm64Test
```

Nếu dùng Mac Intel, cần thêm target `iosX64()` trong `shared/build.gradle.kts` và dùng task test tương ứng.
Lần build đầu cần internet để tải Gradle, dependencies và Kotlin/Native.

## Mở rộng

Thêm interface nghiệp vụ vào `domain`, implementation vào `data`, rồi ghép dependency ở `AppContainer`.
Dùng `androidMain` / `iosMain` khi cần API theo nền tảng. Logic dùng chung ở `commonMain` không phụ thuộc UI.
Đổi package `com.example.kmmbase`, Android applicationId và iOS bundle identifier trước khi dùng cho app thật.

## Trạng thái kiểm tra

- `project.pbxproj` qua `plutil -lint` thành công.
- Gradle shell script qua kiểm tra cú pháp; file wrapper JAR qua kiểm tra ZIP.
- Đã kèm checksum distribution chính thức trong Gradle Wrapper.
- Chưa build hoặc chạy test Kotlin thành công: máy chưa có Java Runtime và Android SDK tại thời điểm tạo.
- Chưa kiểm chứng compile/link iOS hoặc chạy simulator. Không xem các kiểm tra cấu trúc là xác nhận build thành công.

## Realm local data

Dùng **fork Infomaniak Realm Kotlin 3.2.9**, ghim Kotlin **2.2.10** theo bảng tương thích của nhà phát hành:
https://github.com/Infomaniak/realm-kotlin#version-compatibility
Đây là dependency bên thứ ba, không phải artifact `io.realm.kotlin` gốc. AGP được điều chỉnh về 8.10.1 theo dải tương thích Kotlin 2.2.10. Không cấu hình cloud sync.

- `domain/Note.kt`, `NoteRepository.kt`: model và hợp đồng CRUD/Flow.
- `data/local`: Realm entity, cấu hình file `kmmbase.realm`, schema version 1.
- `data/RealmNoteRepository.kt`: upsert theo primary key, query có tham số, delete, observe.
- `di/LocalDataContainer.kt`: sở hữu Realm instance, hỗ trợ thư mục mặc định hoặc truyền thư mục riêng.
- `AppContainer.localData`: lazy, chỉ mở database khi truy cập lần đầu. Màn hình greeting hiện tại chưa dùng dữ liệu Note.

Ví dụ trong coroutine của Android/shared:

```kotlin
val container = AppContainer() // giữ ở app/session scope
val notes = container.localData.notes
notes.save(Note(id = "note-1", title = "Xin chào", body = "Lưu bằng Realm"))
val saved = notes.findById("note-1")
// notes.observeAll().collect { ... } ở một job có lifecycle phù hợp
notes.delete("note-1")
// Hủy và join các job/Flow collector trước khi đóng:
container.close()
```

`save` và `delete` dùng suspend transaction. `findById` đọc trên Default dispatcher.
`observeAll` trả domain snapshots, không đưa managed Realm object ra UI.
Việc mở Realm là đồng bộ: khi tích hợp UI thật, mở container ngoài main thread để tránh chặn giao diện khi mở/migrate file lớn.
Trên iOS, dùng async bridge cho suspend API và một adapter có lifecycle cho Flow khi thêm UI Note.

Khi đổi schema, tăng `schemaVersion` và thêm migration khi cần chuyển đổi dữ liệu. Không bật xóa database khi migration lỗi.
Test iOS kiểm tra upsert, lưu qua lần mở lại, delete và ID rỗng:

```sh
./gradlew :shared:iosSimulatorArm64Test
```

Các test Realm chưa chạy vì môi trường thiếu JDK/Android SDK; compile/link với Xcode hiện tại cũng chưa được xác nhận.
