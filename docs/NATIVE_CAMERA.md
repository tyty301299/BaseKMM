# Camera và hội thoại native

Android dùng CameraX và Jetpack Compose; iOS dùng AVFoundation và SwiftUI. Màn đầu mở camera toàn màn hình, hội thoại nền trong suốt phía dưới, nút micro, ô nhập và nút gửi màu vàng. Tủ đồ và Bài đã học mở các màn native hiện có, lấy dữ liệu từ cùng backend SHADOW.

App không tải trang web để hiển thị giao diện. `ConversationStore` trong shared quản lý trạng thái, lịch sử, hủy yêu cầu và phản hồi; `HttpConversationRepository` gọi trực tiếp `POST /api/conversation` và đọc luồng NDJSON. Ảnh camera thu nhỏ có thể gửi kèm câu hỏi. Micro dùng nhận dạng tiếng Việt của hệ điều hành; phản hồi cho câu hỏi bằng giọng nói được đọc bằng TTS của thiết bị. Chế độ này chưa phải phiên âm thanh Gemini Live hai chiều của website. Các màn thử đồ live native cũ vẫn cần cấu hình provider tương ứng, chưa chuyển sang video Xmax.

Nút **Cài đặt kết nối** trên camera cho đổi **Base URL** và mã truy cập tùy chọn, lưu trên thiết bị. Mặc định `http://localhost:8001`. Dùng origin HTTPS khi kết nối server từ xa; HTTP chỉ chấp nhận loopback khi chạy local. Không nhập `/api` hoặc mã truy cập vào Base URL. Mã truy cập gửi qua header `x-shadow-access`; API key AI chỉ ở backend.

## Chạy local

Khởi động backend SHADOW trên Mac ở cổng 8001. Native app không cần bundle giao diện web. Có thể kiểm tra kết nối bằng `GET http://localhost:8001/api/health`.

Android:

```sh
./tools/build-mobile.sh android
adb reverse tcp:8001 tcp:8001
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n com.example.kmmbase.android/.app.MainActivity
```

`adb reverse` giúp localhost trong máy ảo hoặc thiết bị USB nối về backend trên Mac. Camera được xin quyền khi mở app, micro khi bấm nói. App dừng camera và nhận giọng nói khi vào nền. Đổi cấu hình kết nối sẽ hủy hội thoại đang chạy và xóa ngữ cảnh server cũ.

Với iOS, mở `iosApp/iosApp.xcodeproj`, chạy scheme `iosApp`; hoặc dùng `./tools/build-mobile.sh ios`. Simulator nối localhost trên Mac; iPhone thật cần Base URL HTTPS hoặc tunnel. Simulator không cung cấp camera thật; cần điện thoại để kiểm tra đầy đủ camera và nhận giọng nói.

## Phạm vi kiểm tra

Kiểm tra UI, nút mở tủ đồ/bài học, bật/tắt camera, đổi camera và cài đặt kết nối. Test shared dùng repository giả để kiểm tra luồng text/action, lỗi kết nối, giới hạn lịch sử và hủy yêu cầu; không gọi model. Giữ nguyên key mới trên backend theo yêu cầu. Không xác nhận hội thoại AI, nhận giọng nói hay thử đồ video đã chạy thành công trên điện thoại thật.

## Thêm đồ ngay trên điện thoại

Mở **Tủ đồ → + Thêm đồ từ điện thoại**. Chọn ảnh từ thư viện hoặc chụp ảnh mới, nhập tên, chọn loại đồ và bấm **Lưu vào tủ đồ**. Android và iOS đều dùng màn native; ảnh được sửa chiều và thu nhỏ tối đa 1024px trước khi gửi. Khi lưu thành công, món mới xuất hiện ngay trong danh sách. Mỗi lần mở lại tủ đồ, app tải dữ liệu mới từ backend để nhận cả thay đổi trên website.

API BE đã có trong `shadown-fourward/backend/wardrobe_api.py`:

```http
POST /api/wardrobe/items
Content-Type: application/json
x-shadow-access: <mã truy cập, nếu máy chủ yêu cầu>
```

```json
{
  "image": "data:image/jpeg;base64,<ảnh đã mã hóa>",
  "name": "Áo khoác của tôi",
  "category": "outerwear"
}
```

Loại đồ: `top`, `bottom`, `outerwear`, `dress`, `shoes`, `accessory`. BE nhận thêm PNG cho các client khác; app gửi JPEG. Phản hồi HTTP 200 chứa món đồ với `id`, `name`, `category`, `image_url` và các thuộc tính đã xử lý. Dữ liệu được lưu cùng kho ảnh và danh sách với website; website đọc lại bằng `GET /api/wardrobe/items`, ảnh tải từ `/wardrobe-media/images/...`. Ảnh thiếu hoặc không hợp lệ trả HTTP 422. Khi lỗi mạng, form giữ ảnh và thông tin để người dùng thử lại; nút lưu khóa trong lúc gửi để tránh bấm trùng.

Kiểm tra shared bao gồm lưu món mới, cập nhật danh sách, dữ liệu không hợp lệ, bấm lưu hai lần và đổi server trong lúc tải ảnh. Kiểm tra giao diện/API dùng backend test riêng chạy ở chế độ mock, không gọi model hoặc thêm dữ liệu thử vào tủ đồ thật.

Việc đọc JSON và ánh xạ danh sách tủ đồ chạy trên dispatcher nền trong adapter Ktor, tránh khóa luồng UI lúc tải catalog lớn. Android phân trang 24 món để giới hạn số ảnh được dựng cùng lúc.

Android dùng TextureView cho preview camera và lấy tối đa 4 khung hình/giây cho trợ lý; việc chuyển ảnh và nén JPEG không chạy trên luồng UI.
