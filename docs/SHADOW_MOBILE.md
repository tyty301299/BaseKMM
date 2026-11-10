# SHADOW mobile — tích hợp ban đầu

> Cập nhật UI native: xem [SHADOW_NATIVE_UI.md](SHADOW_NATIVE_UI.md). Tài liệu dưới mô tả các luồng API ban đầu, hiện được mở qua mục Kết nối. Các luồng UI demo native đầy đủ, lịch sử và kỹ năng local mới không gọi AI.

## Hai luồng sản phẩm

- **Học & làm theo:** người dùng làm mẫu, AI phân tích thành kỹ năng, người dùng duyệt các bước; khi làm lại, AI quan sát và hướng dẫn. Không diễn đạt thành AI tự thực hiện công việc vật lý.
- **Tủ đồ:** người dùng thêm ảnh trang phục thật, kiểm tra nhãn nhận diện, chọn dịp đi làm/đi chơi rồi nhận bộ phối từ các món đang có. Bộ mẫu phải được ghi nhãn; không tự nạp mẫu vào tủ thật.

## Phạm vi hiện tại

| Hành động | Android / iOS native | Bản web SHADOW hiện có |
|---|---|---|
| Kết nối máy chủ HTTPS, mã truy cập | Đã triển khai | Có phiên đăng nhập riêng trong trình duyệt |
| Danh sách kỹ năng, đọc từng bước, mẹo và cảnh báo | Đã triển khai | Có |
| Hỏi về bước đang xem, phân biệt AI/demo/dự phòng | Đã triển khai | Có |
| Xem danh sách tên đồ, chọn dịp và nhận gợi ý | Đã triển khai | Có |
| Quay và dạy, xem lại/chỉnh sửa trước khi lưu | Mở trình duyệt bằng nút ghi rõ “Mở bản web” | Có |
| AI quan sát thao tác qua camera | Mở trình duyệt | Có |
| Thêm ảnh đồ, xem ảnh, chỉnh nhãn, mặc thử | Mở trình duyệt | Có |

Đây là bản tích hợp ban đầu, **chưa phải toàn bộ trải nghiệm native hoàn thiện**. Các màn ảnh/camera chưa được port. Danh sách native hiện dùng tên trang phục, chưa có thumbnail. Không nhúng webview rồi gọi đó là giao diện native. Không thay đổi thuật toán AI/backend trong đợt này.

Backend dạy kỹ năng hiện nhận `frames` + `transcript` + `notes` qua `/api/teach/compile`, không nhận nguyên file video. Web lấy mẫu khung hình từ camera và dùng nhận dạng lời nói của trình duyệt. Nếu sản phẩm cần tải video quay sẵn, cần bổ sung luồng trích khung hình, ASR và đồng bộ thời gian; không chỉ thêm nút upload.

## Thiết kế

Giữ nhận diện đang có trong `shadown-fourward/web/styles.css`: nền `#0B0B0F`, mặt nền `#16161E`, vàng `#FFD23F`, chữ hệ thống. Chọn hai mục điều hướng theo hai nhu cầu, tiêu đề nêu tác vụ, nút chính tối thiểu 44–48 pt/dp. Màn hình cuộn, tính safe area và bàn phím; nội dung thông báo không bị cắt bằng giới hạn một dòng. Không thêm thư viện UI hoặc tài nguyên ảnh bên ngoài.

Các trạng thái bắt buộc: chưa kết nối, đang tải, lỗi có thể thử lại, danh sách rỗng, có dữ liệu, AI đang trả lời, kết quả có nguồn. Câu hỏi giữ lại khi lỗi. Chuyển bước xóa câu trả lời của bước cũ; đóng kỹ năng hủy yêu cầu. Gợi ý ghi rõ dịp đã gửi, tránh nhầm với lựa chọn vừa đổi.

Chưa có kiểm tra ảnh chụp simulator/thiết bị. Chưa xác nhận Dynamic Type lớn, TalkBack/VoiceOver hoặc bàn phím trên thiết bị thật. Đây là định hướng thiết kế từ sản phẩm hiện có, không phải một bản Figma đã duyệt hay kết quả tham khảo web đã xác minh.

## Kiến trúc và kết nối

`SwiftUI / Compose → ShadowAction → ShadowStore → ShadowRepository → HttpShadowRepository`.

Shared dùng Ktor 3.2.3 (Android/Darwin) và kotlinx.serialization JSON 1.9.0. Giao diện không gọi HTTP trực tiếp. Mỗi kết nối có cookie jar trong RAM riêng; đổi máy chủ hủy việc cũ, đóng client và xóa dữ liệu cũ. Chỉ lưu địa chỉ máy chủ đã kết nối thành công vào preferences, không lưu access code hay khóa LLM. Cookie native không chia sẻ sang trình duyệt; bản web có thể hỏi lại mã.

App nhận **origin HTTPS** như `https://shadow.example.com`, không nhận `/api`, `/#/teach`, query, credentials hoặc redirect. Không hardcode prod, không có URL prod thật trong repo. Local backend cần tunnel HTTPS để chạy từ app/điện thoại; `localhost` trên điện thoại không phải laptop.

API sử dụng:

- `GET /api/health` → `ok`, `mode`, `access_required`.
- `POST /api/access` → cookie xác thực khi cần.
- `GET /api/skills`, `GET /api/skills/{id}` → tên, số bước, nội dung bước.
- `POST /api/ask` với `skill_id`, `step_id`, `question` → `answer`, `source`.
- `GET /api/wardrobe/items` → danh sách món đồ.
- `POST /api/stylist/suggest` với `event`, `notes` → `outfits`, `source`.

Native chưa gửi ảnh đang mặc hoặc thành phố/thời tiết. Muốn kiểm tra ngữ cảnh này, dùng màn stylist trên web. Câu hỏi giới hạn 500 UTF-16 code units, ghi chú 300, để Android và iOS đồng nhất; không âm thầm cắt nội dung. Timeout client 90 giây; nút gửi khóa trong khi đang xử lý. Các nguồn `mock`, `fallback`, `ai+fallback` đều có nhãn riêng.

## Chạy và kiểm tra

Chạy backend theo README trong `shadown-fourward`. Chưa có khóa AI có thể dùng `SHADOW_MODE=mock ./run.sh`. Không đặt khóa nhà cung cấp AI trong app.

Kiểm tra API từ thư mục BaseKMM, không cần cài thư viện Python:

```sh
python3 tools/shadow_smoke.py http://127.0.0.1:8000
python3 tools/shadow_smoke.py https://YOUR_SHADOW_HOST --require-ai
```

Lệnh đầu chỉ đọc health/kỹ năng/tủ đồ. `--exercise-ai` gọi hỏi đáp và phối đồ, có thể phát sinh chi phí model. `--require-ai` đồng thời bật kiểm tra model, thất bại nếu nhận demo/dự phòng hoặc nếu chưa có kỹ năng/tủ đồ để kiểm tra. Công cụ hỏi mã bằng `getpass` khi cần; cũng có thể nhận `SHADOW_ACCESS_CODE` từ môi trường. Không truyền mã vào URL/đối số CLI. Không tự thêm/xóa dữ liệu. PASS xác nhận hợp đồng và nguồn phản hồi, **không chứng minh chất lượng thị giác hay tính đúng đắn của lời khuyên**.

Kiểm tra mobile sau khi có JDK 17, Android SDK 35, Gradle dependencies và Kotlin/Native:

```sh
./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug
./gradlew :shared:iosSimulatorArm64Test
# Mac Intel: thay iosSimulatorArm64Test bằng iosX64Test.
```

Mở `iosApp/iosApp.xcodeproj`, chạy scheme `iosApp`. Nhập origin và mã truy cập ở “Kết nối”. Thử cả 2 mục, Q&A từng bước, tải lại sau khi thêm trang phục trên web, sai mã, mất mạng, gửi lặp, đóng khi đang trả lời và đổi máy chủ. Kiểm tra màn hẹp, chữ lớn, safe area và bàn phím.

## Những gì đã kiểm tra tại workspace này

- 6 test offline của **công cụ smoke** pass bằng `python3 -m unittest discover -s tools -p 'test_shadow_smoke.py' -v`. Dùng fixture, không chạy backend/AI.
- Swift frontend parse pass; Xcode project plist hợp lệ; `git diff --check` pass. Parse không phải type-check hay build/link với Shared.
- Có 5 test shared mới cho URL, xác thực/nguồn kết quả, gửi lặp/hủy, đổi máy chủ và đầu vào không hợp lệ. **Chưa chạy** vì máy không có Java runtime/JDK và Android SDK.
- Gradle build đã thử nhưng dừng do không có Java. Cài Python requirements đã thử nhưng không tải được package vì mạng/DNS bị hạn chế. Xcode có mặt; CoreSimulator không kết nối được trong môi trường này.
- **Chưa chạy backend local, chưa gọi AI thật/prod, chưa build thành công hoặc mở app mobile, chưa xác minh giao diện bằng ảnh.** README gốc có kết quả kiểm tra template từ máy khác; các kết quả đó không áp dụng cho thay đổi SHADOW này.

## Việc còn lại để hoàn thiện AI và mobile

1. Cung cấp URL prod HTTPS và mã truy cập của nhóm hoặc một môi trường local có đủ dependencies và khóa server. Chạy smoke với `--require-ai`.
2. Dùng video/thao tác thật của nhóm: quay, biên dịch kỹ năng, duyệt bước, lưu, mở lại rồi làm thử đúng/sai/che khuất. Đối chiếu với người dạy; chú ý chuyển bước quá sớm, thiếu cảnh báo và nhận dạng lời nói tiếng Việt. Repo backend đã có các test compiler/verify/confirm_frames để chạy bổ sung.
3. Dùng ảnh trang phục thật: kiểm tra nhận dạng và sửa nhãn, tạo gợi ý cho đi làm/đi chơi, xác nhận không sinh ID đồ ngoài tủ, thử ít đồ/thiếu nhóm đồ/quy tắc mâu thuẫn và khi model không phản hồi. Kiểm tra `source` ở từng phản hồi.
4. Build và chạy cả hai nền tảng, sửa lỗi thực tế nếu có; chụp và duyệt layout. Sau đó port quay/dạy, ảnh tủ đồ và hướng dẫn camera sang native dựa trên hợp đồng đã xác nhận.
