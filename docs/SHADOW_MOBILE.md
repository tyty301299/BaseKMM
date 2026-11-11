# SHADOW mobile — app native gọi máy chủ thật

App Android và iOS giờ chạy trọn bốn luồng SHADOW với máy chủ thật, không mở trình duyệt:
**Dạy** (camera + giọng nói → AI sinh kỹ năng), **Hướng dẫn** (camera + WebSocket chấm từng bước),
**Tủ đồ / Phối đồ** (ảnh thật, thời tiết thật), **Hỏi Shadow**.

Máy chủ mặc định là `https://fourward.thangqt.com`; app tự kết nối khi mở nên trên sân khấu không phải gõ gì.
Mã truy cập mặc định nằm trong `shared/.../domain/model/ShadowDefaults.kt` — đây là mã công khai của bản demo
hackathon, **đừng dùng cách này cho máy chủ có dữ liệu thật**.

## Luồng nào đã thật

| Luồng | Trạng thái | Gọi gì |
|---|---|---|
| Danh sách kỹ năng, đọc từng bước | Thật | `GET /api/skills`, `GET /api/skills/{id}` |
| Hỏi Shadow về một bước | Thật | `POST /api/ask` |
| **Hướng dẫn qua camera** | Thật | WebSocket `/session/control` + camera |
| **Dạy kỹ năng mới** | Thật | camera + nhận giọng nói → `POST /api/teach/compile`, `PUT /api/skills/{id}` |
| Tủ đồ (lưới ảnh thật) | Thật | `GET /api/wardrobe/items` |
| Phối đồ (thời tiết + bộ phối có ảnh) | Thật | `POST /api/stylist/suggest` với `city="Hà Nội"` |
| Thêm ảnh đồ, mặc thử | **Vẫn mở bản web** | nút "Thêm ảnh & quản lý tủ đồ" |
| Các màn mẫu cũ (hình nhân vẽ, lịch sử local) | Dữ liệu mẫu | Realm, không gọi mạng |

App mở thẳng vào luồng máy chủ. Các màn mẫu cũ vẫn còn phía sau (bấm "Đóng") làm phương án dự phòng.

## Hai chi tiết giao thức dễ làm sai

Đây là phần khiến Hướng dẫn nhanh và không nói chồng. Cả hai đều có test trong `GuideStoreTest`.

1. **`vision.result.confirming`** — khi máy chủ thấy một khung hình báo "xong", nó sẽ chấm khung tiếp theo **ngay**
   mà không chờ hết giãn cách. App phải gửi khung mới lập tức, bỏ qua `MIN_GAP_MS` một lần. Nhờ vậy xác nhận bước
   mất ~0,9 giây thay vì ~3,9 giây.
2. **`speech.epoch`** — mỗi câu nói có số thứ tự. Câu có epoch **không mới hơn** câu vừa đọc là câu cũ (hoặc phát
   lại sau khi kết nối lại) và phải **bỏ qua**; câu mới hơn thì **ngắt** câu đang đọc. Thiếu bước này, AI sẽ đọc
   chồng hoặc đọc lại câu cũ.

Nhịp gửi khung hình: định kỳ 1,5 giây, tối thiểu cách nhau 1,2 giây, JPEG cạnh dài 512px chất lượng 0,7.
Máy chủ tự thu nhỏ về `vision.image_max_side` (mặc định 448) và báo con số đó trong `session.ready`.

## Xác thực

Mã truy cập đi bằng **header `x-shadow-access`**, không dùng cookie: app native không có cookie jar của trình duyệt,
và header này được chấp nhận cho cả `/api/*` lẫn WebSocket. Ảnh trong tủ đồ cũng nằm sau cổng này, nên
`ShadowStore.mediaHeaders()` cấp header cho trình tải ảnh. Mã **không** nằm trong `ShadowState` để không bị chụp lại
trong snapshot giao diện hay saved instance state.

Hai điều đã kiểm chứng trực tiếp với máy chủ đang chạy:
- Máy chủ **không chặn** app native. `_same_origin()` trong `backend/security.py` trả `True` khi không có header
  `Origin`, nên client không phải trình duyệt đi qua được.
- WebSocket **phải dùng HTTP/1.1**. Caddy phục vụ HTTP/2 và bắt tay WS qua h2 trả 404. Ktor mặc định HTTP/1.1 nên
  không cần làm gì, nhưng nếu gặp 404 khi mở WS thì đây là nguyên nhân.

## Kiến trúc

```
SwiftUI / Compose ── Action ──▶ Store ──▶ Repository ──▶ máy chủ SHADOW
       ▲                          │
       └────────── State ─────────┘
```

Logic dùng chung nằm trong `shared`, kể cả phần khó: nhịp gửi khung hình, `confirming`, `speech_epoch`.
Mỗi nền tảng chỉ cung cấp camera và giọng nói qua hai interface nhỏ trong `shared/.../platform/Speech.kt`:

| Việc | Android | iOS |
|---|---|---|
| Camera (`FrameSource`) | `CameraFrameSource` + CameraX | `ShadowCamera` + AVFoundation |
| Đọc tiếng Việt (`Speaker`) | `VietnameseSpeaker` (`TextToSpeech`) | `ShadowSpeaker` (`AVSpeechSynthesizer`) |
| Nghe tiếng Việt | `VietnameseListener` (`SpeechRecognizer`) | `ShadowListener` (`SFSpeechRecognizer`) |

`AppContainer.guideStore(...)` và `teachStore(...)` dựng store ở phía Kotlin, nên Swift không phải chạm vào
dispatcher hay lambda `suspend`.

Backend **không nhận video**. Màn Dạy lấy khung hình mỗi ~2 giây và ngay sau mỗi câu nói (câu nói thường kết thúc
đúng lúc nhìn thấy kết quả), kèm transcript có mốc thời gian. Tối đa 64 khung hình; khi vượt, `TeachStore` tỉa bớt
phần giữa và **giữ khung đầu và khung cuối**, vì trạng thái kết thúc là thứ bộ biên dịch cần nhất.

Nếu thiết bị không nhận được giọng nói tiếng Việt, màn Dạy hiện cảnh báo và người dạy **gõ ghi chú** — backend vẫn
biên dịch được kỹ năng từ ghi chú cộng khung hình.

## Build và chạy

Lần đầu cần tải công cụ vào `.toolchains/` (xem [MOBILE_SETUP.md](MOBILE_SETUP.md)):

```sh
python3 tools/setup_mobile_env.py
./tools/build-mobile.sh android   # chạy test shared rồi assembleDebug
./tools/build-mobile.sh ios       # cần Xcode; đặt DEVELOPER_DIR nếu xcodebuild báo lỗi
```

iOS trên simulator:

```sh
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
xcrun simctl boot "iPhone 16 Pro"
xcrun simctl install "iPhone 16 Pro" .toolchains/DerivedData/Build/Products/Debug-iphonesimulator/KmmBase.app
xcrun simctl launch "iPhone 16 Pro" com.example.kmmbase.ios
```

Android cần thiết bị thật hoặc emulator; `sdkmanager` trên máy này tải nhầm emulator bản x86_64 nên emulator chưa
chạy được, cài thẳng lên máy Android qua `adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## Kiểm tra

```sh
./gradlew :shared:allTests          # 41 test, không cần mạng
```

Kiểm tra hợp đồng với máy chủ thật (cần mạng, phải tự bật):

```sh
SHADOW_ACCESS_CODE=<mã> ./gradlew :shared:testAndroidHostTest --tests '*LiveServerTest*'
```

`LiveServerTest` là thứ bắt được lỗi mà test giả không thấy: đổi tên trường, WebSocket từ chối bắt tay, ảnh trả về
đường dẫn tương đối. Lần chạy gần nhất (11/11): 3/3 pass — WS mở và nhận bước thật trong 1,1 giây; phối đồ trả
3 bộ kèm thời tiết Hà Nội thật trong 24 giây.

## Chưa kiểm tra được

- **Chưa chạy trên điện thoại thật.** Camera, đọc/nghe tiếng Việt và quyền truy cập chỉ mới chạy qua biên dịch và
  simulator. Bắt buộc thử trên iPhone và máy Android thật trước ngày thi: simulator không có giọng tiếng Việt và
  camera là ảnh giả.
- **Chưa đo độ chính xác với model đang chạy.** Máy chủ hiện dùng `deepseek-flash`, khác `qwen3-vl-8b-instruct`
  mà toàn bộ số liệu trong `shadown-fourward/PROGRESS.md` đã đo. Cần chạy lại eval trên bài demo thật.
- **Mặc thử** (`/api/tryon/photo`) chưa có trong app; mặc thử trực tiếp Decart cũng chưa, vì máy chủ báo
  `live.available: false` (thiếu `DECART_API_KEY`).
- Giới hạn tần suất: tối đa 3 WebSocket cùng lúc trên một IP, 30 tin nhắn/giây. Cả nhóm test chung một mạng có thể
  bị đóng kết nối với mã 1008 — biết trước để không nhầm là lỗi app.


## Truy vết app native

Lọc `[SHADOW]` trong Xcode Debug Console hoặc Android Studio Logcat (`message:SHADOW`).
Từ Terminal có Android SDK: `adb logcat -v threadtime | rg '\[SHADOW\]'`.
Log xuất qua stdout của app; chưa ghi file hay gửi lên server.

Mỗi dòng có dạng `[SHADOW][HTTP][<trace-id>] +123ms response status=200`.
Trace ID nhóm các dòng của một request, kết nối WebSocket hoặc instance store;
`+...ms` là thời gian kể từ lúc trace đó được tạo, không phải thời gian giữa hai dòng.
Các trace này là cục bộ, chưa phải ID dùng chung với server.

- `App`/`AppOperation`: loại thao tác và bắt đầu/thành công/hủy/lỗi.
- `HTTP`: method, route (ẩn ID động), status, thời gian đọc/parse JSON, loại exception.
  `success` ở đây chỉ xác nhận HTTP và JSON, chưa xác nhận mapping domain hay UI hoàn tất.
- `WS`: kết nối, nối lại, thời gian chờ, gửi thành công/bỏ qua/lỗi và lỗi parse JSON.
- `Guide`: trạng thái kết nối, phase/bước, lấy/gửi frame, kết quả vision,
  xác nhận ngay và speech epoch bị bỏ vì cũ. `frame_dispatch` là yêu cầu gửi;
  xem thêm `WS sent` hoặc `send_failed` để biết kết quả gửi.
- `Teach`: ghi hình, số frame/transcript, compile, review, save, hủy và lỗi.

Không log access code, header, payload, ảnh/base64, lời nói, câu hỏi, ghi chú,
địa chỉ server hoặc thông báo exception có thể chứa dữ liệu riêng tư.
Log frame có thể xuất mỗi 1,5–2 giây khi đang chạy. Chưa bổ sung log riêng
cho permission/callback camera, TTS/STT native; khi frame không có, xem `frame_unavailable`.
