# SHADOW — UI & prototype v2

Bộ ý tưởng cho iOS và Android, chưa phải app native hoặc tích hợp AI thật. Bản v2 thêm **camera thử đồ, chụp mẫu, lưu local và Lịch sử**. Không thay đổi code app/backend trong đợt này.

## Mở và thử

Mở `start.html` chọn nền tảng, hoặc mở trực tiếp `ios-prototype.html` / `android-prototype.html` bằng Chrome/Safari. `demo.html` cho đổi nền tảng. Các bản gộp chạy không cần mạng hay cài dependencies. Tất cả có 27 màn hình, sáng/tối và kính/nền đặc.

Luồng mới: **AI Outfits → bộ phối → mở camera demo → chọn áo → so sánh trước/sau → chụp mẫu → lưu trên máy → Lịch sử → xem lại / thử lại / tải ảnh / xóa**.

- Camera dùng **hình nhân vector**, không truy cập camera thật. Nút đảo hướng lật hình nhân, không chuyển camera phần cứng.
- Ảnh chụp là snapshot lựa chọn trang phục và dịp sử dụng. Không phải video feed hoặc ảnh thử đồ do AI sinh.
- Lưu bộ phối ngay từ màn chi tiết, hoặc lưu kỹ năng mẫu từ màn duyệt bước.
- Tab **Lịch sử** có bốn bộ lọc: tất cả / thử đồ / bộ phối / kỹ năng. Có trang trống, phân trang, chi tiết, xem lại và xác nhận xóa.
- **Bắt đầu lại** đặt lại flow nhưng giữ dữ liệu local. **Tùy chọn → Xóa toàn bộ dữ liệu demo** mới xóa local, sau xác nhận.
- Màn lỗi lưu cho quay lại thử; không báo thành công nếu storage thất bại.

## Lưu local trong prototype

Dùng `localStorage`, key `shadow.prototype.local.v1`, tối đa 40 mục. Lưu metadata có phiên bản: ID, loại, thời điểm, tên, preset trang phục, dịp, nội dung bước kỹ năng; thêm tên trang phục đã chỉnh và trạng thái chọn ảnh mẫu. Khi mở lại, hình nhân được dựng lại từ snapshot preset đã lưu. **Không lưu ảnh camera hay ảnh người thật**. Tải ảnh xuất SVG minh họa từ snapshot.

Chỉ nhận các trường/giá trị đã biết; không nạp HTML/SVG tùy ý từ storage. Không ghi khi dữ liệu hỏng, storage bị chặn hoặc hết dung lượng. Không tự xóa mục cũ khi đạt giới hạn. Gửi lặp thao tác lưu cùng một snapshot không tạo mục trùng. Màn chi tiết không còn mục hiển thị thông báo và đường về Lịch sử.

Nguồn dữ liệu được đọc lại trước thao tác ghi, và nhận sự kiện storage để cập nhật giữa các tab cùng origin. Đây không phải database giao dịch nhiều thiết bị. Không đồng bộ cloud. Mã gọi storage đã có test bằng storage giả lập; chưa có kiểm thử browser E2E.

`localStorage` phụ thuộc browser/origin: Safari chế độ riêng tư, iframe trong chat hoặc cấu hình quyền có thể từ chối; `file://` không được tiêu chuẩn hóa cách chia sẻ storage giữa các file. Mở lại **đúng file, đúng trình duyệt/profile** để thử giữ dữ liệu; nếu cần origin ổn định, dùng server local từ máy có quyền. Demo sẽ báo lỗi nếu không lưu được. Hai file iOS/Android có thể có vùng dữ liệu tách biệt tùy cách trình duyệt xử lý file.

## Ý tưởng UI

“**Một chút mới, từ đồ quen.**” Ảnh trang phục là nội dung chính; nền xanh đen, mảng kem và vàng nhấn. AI Outfits vẫn là tính năng của SHADOW. Điều hướng: **Phối đồ / Tủ đồ / Học & làm / Lịch sử**. Không thêm account/paywall.

Camera có vùng hình người lớn, lựa chọn trang phục bên dưới, nút chụp nổi ở vị trí dễ chạm và lối tắt vào Lịch sử. Kính chỉ ở điều hướng, công cụ nổi và nhãn trên camera. Chữ và thông tin dài giữ nền ổn định. Lịch sử không phụ thuộc AI hay mạng để xem lại dữ liệu đã lưu.

Luồng Học & làm theo vẫn có: chuẩn bị → quay mẫu → phân tích → sửa bước → lưu kỹ năng local → làm theo từng bước → hỏi mẫu. Câu trả lời và trạng thái xử lý là minh họa; không dùng phần trăm tiến độ giả.

## Nền tảng và Liquid Glass

| Trường hợp | Hướng triển khai sau khi duyệt UI |
|---|---|
| iOS 26+ | Liquid Glass native ở điều hướng/điều khiển nổi; API `glassEffect(_:in:)` đã đối chiếu availability trong SDK Xcode. |
| iOS cũ | System Material; không gọi fallback này là Liquid Glass native. |
| Android | Material navigation, touch target 48dp; kính tương ứng khi renderer hỗ trợ. Không dùng `Modifier.blur` để giả là backdrop blur. |
| Giảm độ trong / tương phản cao | Nền đặc và đường phân cách rõ. |
| Giảm chuyển động / máy yếu | Không morph/parallax/blur động. Không làm chậm camera. |

Artboard iOS 393×852, Android 412×892; vùng chạm tối thiểu 44 / 48. Prototype mô phỏng chất liệu bằng tint/viền/bóng; không tái tạo renderer khúc xạ của Apple. Camera và hiệu ứng native cần kiểm thử sau. SVG cố định không chứng minh hỗ trợ Dynamic Type hoặc font scaling; bản app cần layout reflow và safe area thật.

Light mode dùng vàng nâu cho chữ/icon nhỏ, nút vàng dùng chữ tối. Token trong `tokens.json`. Màn Thành phần UI có nút, lựa chọn, glass surface và nhãn AI / Demo / Dự phòng.

## Bàn giao

- `start.html`: chọn prototype iOS / Android.
- `ios-prototype.html`, `android-prototype.html`, `demo.html`: bản độc lập, đầy đủ flow.
- `inline-preview.html`: bản xem trực tiếp trong chat, dùng chung runtime; khả năng lưu phụ thuộc quyền storage của khung chat.
- `overview-ios.png`, `overview-android.png`: board mới tập trung camera, kết quả và Lịch sử. Các mục trong ảnh board là fixture minh họa; không tự seed vào lịch sử người dùng.
- `screens/`: 54 SVG chính và các biến thể PNG/SVG đã render.
- `flow.svg`, `tokens.json`, `ASSETS.md`: luồng, quy ước và nguồn ảnh.
- `design.js`, `controller.js`, `local-store.js`, `preview.js`: nguồn UI, flow, persistence, tương tác.
- `package.py`: đóng gói lại demo, preview và ZIP. `render.cjs`: xuất board/SVG; dùng Sharp cài sẵn trong môi trường này.

## Kiểm tra

Đã kiểm tra ảnh render camera và Lịch sử. `check.cjs` rasterize 216 tổ hợp màn/nền tảng/theme/kính, kiểm tra giới hạn điểm bấm/action và độ rộng chữ. `check-local.cjs` kiểm tra chụp, snapshot, lưu lặp, ba loại lịch sử, đọc lại storage, reset giữ dữ liệu, xóa một mục, quota, dữ liệu hỏng, lọc input và giới hạn 40 mục. Các test storage dùng adapter giả lập, không phải browser E2E.

Chrome/simulator không mở được từ sandbox nên chưa bấm thử trên browser hoặc thiết bị thật. Không tuyên bố camera, AI, Liquid Glass native hay lưu trữ native đã hoạt động.

Tham chiếu người dùng: Pinterest `696650636147162800` và Facebook reel `2749808685433780`. Cả hai chưa xem được do DNS, nên bản v2 dựa trên yêu cầu màn camera thử đồ và lưu local, không khẳng định sao chép đúng reel.

Chưa tạo file Figma vì chưa chọn team lưu file; bản local dùng độc lập được.
