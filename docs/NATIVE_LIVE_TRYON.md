# Thử áo live native — Android và iOS

## Phạm vi

Vào **Camera → Thay đồ → Thử áo trực tiếp**. Chọn một áo (`category=top`) từ tủ đồ đã tải, cấp quyền camera và bấm bắt đầu. App gửi ảnh áo tới SHADOW để tạo phiên Vidu, dùng Agora native phát camera và nhận video AI. Không dùng WebView. Micro không được publish.

Camera gốc hiển thị riêng với nhãn, không được thay thế kết quả AI. Chỉ chuyển sang trạng thái live khi SDK báo đã hiển thị frame video từ `render_uid`. Có đổi áo, dừng, kiểm tra lại cấu hình, lỗi và giới hạn thời lượng/không thao tác. Phiên không tự mở lại khi quay về foreground. Sau lỗi hoặc hết hạn phải bấm bắt đầu lại.

RAM iOS là physical footprint của tiến trình; Android là PSS. FPS lấy từ `rendererOutputFrameRate` của video remote trong Agora, không phải nhịp vẽ UI. Những số này không chứng minh độ trễ đầu cuối, chất lượng áo, frame lặp hay độ mượt 360°.

## Clean Architecture và vị trí file

```text
shared/src/commonMain/kotlin/com/example/kmmbase/
  domain/
    model/LiveTryOn.kt                  # Entity/config/session, không phụ thuộc JSON hoặc SDK
    repository/LiveTryOnRepository.kt   # Contract API, control socket, video engine
    usecase/PrepareLiveTryOnUseCase.kt  # Kiểm tra áo, tải tham chiếu, tạo phiên
  data/
    dto/live/LiveTryOnDto.kt            # Kiểu wire và mã hóa request/control message
    mapper/LiveTryOnMapper.kt           # DTO → domain, kiểm tra credentials/UID/socket URL
    remote/live/LiveTryOnApi.kt         # HTTP, auth, ảnh tham chiếu
    remote/live/ViduControlSocket.kt    # WebSocket Vidu, init/switch/hangup
    repository/LiveTryOnRepositoryImpl.kt
  presentation/livetryon/
    LiveTryOnState.kt                   # Chỉ trạng thái UI, không chứa credentials
    LiveTryOnStore.kt                   # State machine, cancellation, timeout, generation
  di/AppContainer.kt                   # Constructor injection

androidApp/.../features/livetryon/
  data/AgoraLiveVideoEngine.kt          # Agora Android adapter
  presentation/LiveTryOnScreen.kt       # Compose + permission/lifecycle + composition root

iosApp/iosApp/Features/LiveTryOn/
  Data/AgoraLiveVideoEngine.swift       # Agora iOS adapter
  Data/LiveMemoryReader.swift
  Presentation/LiveTryOnModel.swift     # Swift bridge + Builder
  Presentation/LiveTryOnView.swift      # SwiftUI
```

Dependency direction: native UI → shared store/use case → domain repository contracts. Data implements those contracts; Agora adapters implement `LiveVideoEngine`. DTO và thư viện Ktor ở Data, Agora chỉ ở từng platform. Không đổi toàn bộ dự án KMM sang kiến trúc SPM riêng.

Credentials tồn tại trong bộ nhớ theo phiên, không đưa vào state, preferences hoặc log. Header SHADOW chỉ gửi đến server hiện tại; tải ảnh tham chiếu chặn khác origin và redirect. Tham chiếu hiện nhận JPEG/PNG tối đa 2 MB. Áo thiếu ảnh/ảnh vượt giới hạn có thông báo lỗi, không mở phiên giả.

## Hợp đồng Vidu

- `GET /api/tryon/config`: kiểm tra provider/available và ngưỡng phiên.
- `POST /api/tryon/live-session`: gửi `reference_image` data URI, nhận thông tin Agora và control socket.
- `type=1 → type=2`: init/ACK; chỉ retry `NOT_READY`, tối đa 8 lần trong timeout 15 giây.
- `type=13`: gửi ảnh áo mới. **Không có ACK thành công**; `type=14` chỉ báo lỗi. `sentGarmentId` là ảnh đã gửi, không xác nhận video đã thay áo xong. UI ghi “Đã gửi áo mới. Chờ video cập nhật.”
- `type=5`: hangup khi dừng. Cleanup socket có thời gian chờ giới hạn và tiếp tục giải phóng local ngay cả khi mạng đã mất.
- Numeric UID Agora dùng miền UInt32; hỗ trợ user account dạng chuỗi và ánh xạ UID video remote.

Đối chiếu [Vidu realtime parameters](https://platform.vidu.com/vidu-stream/doc/s2-editing/realtime/parameters) ngày 12/11/2026. SDK Agora native ghim 4.5.2; Android dùng `full-rtc-basic`, iOS dùng SPM `RtcBasic` và `Package.resolved`.

## Kiểm chứng và phần còn chặn

- Android debug APK và iOS Simulator (arm64/x86_64) build thành công. Shared Android host tests: 64 test, 0 failures/errors, trong đó có 10 test mới cho live. Các test server opt-in không chứng minh Vidu chạy thật khi chưa bật credentials.
- Có test shared cho DTO/UID/URL, cấu hình không khả dụng, chống tạo phiên trùng, hủy lúc tải ảnh, callback phiên cũ, timeout frame đầu, timeout handshake, giới hạn phiên và trạng thái gửi ảnh áo.
- GET cấu hình server mặc định ngày 12/11/2026 trả: `available=false`, provider `vidu`, model `vidu-s2-editing`, cap 120 giây, idle 60 giây. Không tạo phiên tính phí trong lượt kiểm tra.
- Cần cấu hình Vidu hợp lệ trên server để chạy end-to-end. Chưa chứng minh camera/video thật, đổi áo, FPS/RAM/nhiệt hay xoay 360° trên điện thoại.
- App thực thi cap và idle. BE hiện không chủ động hangup phiên thay client. Nếu app bị kill/mất mạng, việc kết thúc/billing phụ thuộc giới hạn và reconnect grace của provider. Hủy HTTP không bảo đảm hủy được phiên upstream đã tạo nhưng chưa nhận response.
- Chưa có giày/mũ, phối nhiều món, lưu video hoặc tự reconnect phiên tính phí.
- Android chưa có thiết bị/emulator kết nối trong lượt này. iOS đã cài và launch trên iPhone 14 Pro simulator iOS 18.2; công cụ thao tác Simulator không truy cập được nên chưa xác minh luồng UI bằng tương tác.

## Chạy lại

```sh
./tools/build-mobile.sh android
./tools/build-mobile.sh ios
```

Để nghiệm thu trên máy thật, dùng [bộ tiêu chí live](LIVE_TRYON_ACCEPTANCE.md). Chỉ đánh dấu đạt khi có số đo thực tế; build và unit test không thay thế kiểm chứng provider.
