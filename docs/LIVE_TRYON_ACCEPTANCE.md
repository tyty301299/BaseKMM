# Tiêu chí nghiệm thu thử đồ live

Ngày rà: 2026-11-12. Phạm vi đầu: một áo. Mục tiêu cuối: quần, áo, giày, mũ bám người qua camera khi di chuyển và xoay. Các ngưỡng dưới đây là mục tiêu đề xuất, chưa phải kết quả đo.

## Đánh giá hiện tại

- Web trong shadown-fourward dùng Vidu S2-Editing qua Agora (`web/js/screens/tryon-live.js`, `web/js/vidu-live.js`).
- Danh sách live hiện có top, bottom, dress, outerwear; chưa có giày/mũ.
- Native Android/iOS đã có luồng Agora/Vidu, DTO và store KMM riêng; xem [triển khai native](NATIVE_LIVE_TRYON.md). Server ngày 12/11 trả `live.available=false`, chưa chạy live thật.
- Chỉ số chất lượng mạng không chứng minh FPS, RAM hoặc độ trễ đầu cuối. Chưa thấy bộ đo đầy đủ trong luồng web live.
- Chưa có bằng chứng xoay 360 độ đúng sản phẩm. Video AI không mặc nhiên tương đương mô hình 3D chính xác.
- Chưa chạy dịch vụ thật hoặc đo trên thiết bị. Chưa đủ điều kiện nghiệm thu.

## Ngưỡng prototype đề xuất

| Tiêu chí | Ngưỡng | Cách kiểm tra |
|---|---|---|
| FPS camera local | Mục tiêu 30; ít nhất 95% cửa sổ 1 giây đạt 27 FPS | Đếm frame thực sự trình bày |
| FPS video AI đầu ra | Mục tiêu 30; ít nhất 95% cửa sổ 1 giây đạt 27 FPS | Đo riêng video remote, ghi cả frame lặp |
| Đứng hình khi live | Không freeze trên 500 ms trong bài chuẩn | Video đối chứng và timestamps |
| Độ trễ chuyển động tới hình AI | p50 ≤150 ms, p95 ≤250 ms | Camera ngoài quay người thật cùng màn hình; ít nhất 30 thao tác |
| Frame AI đầu tiên | p95 ≤8 giây | 20 phiên; tính từ start sau khi có quyền camera |
| Đổi áo | p95 ≤3 giây | 20 lần; tới khi đồ mới hiển thị ổn định, không chỉ server ACK |
| RAM khi chạy | Drift ≤10% sau warm-up; không tăng liên tục | Lấy mẫu mỗi giây; baseline, median, peak, cuối phiên |
| RAM sau thoát | Sau 30 giây không vượt baseline quá 30 MB; không tăng tích lũy qua 10 vòng | iOS physical footprint, Android PSS; gồm tiến trình web liên quan |
| Chạy lâu | Không crash, memory warning hoặc thermal serious/critical; vẫn đạt FPS | 15 phút trên máy thật; phân biệt nhiều phiên và một phiên liên tục |
| Chất lượng | ≥90% clip đạt, không clip nào biến dạng rõ mặt/cơ thể | Hai người chấm độc lập |

Chốt ngân sách RAM tuyệt đối theo từng thiết bị sau khi đo baseline. Không lấy trung bình nhiều máy để che máy yếu. Ghi model, OS, build, commit/working tree, engine/config, camera, độ phân giải, mạng và nhiệt độ.

Web: requestVideoFrameCallback và thống kê remote SDK. Không lấy requestAnimationFrame/CADisplayLink đơn lẻ làm FPS video AI. RTT không phải độ trễ toàn luồng. JS heap không phải RAM toàn ứng dụng. iOS dùng Instruments Allocations/VM Tracker và Core Animation; Android dùng Memory Profiler/Perfetto. Có lượt không profiler để kiểm tra overhead.

## Bộ kiểm thử một áo

Tối thiểu 5 người khác dáng × 3 áo (trơn, họa tiết, logo) × 2 điều kiện sáng = 30 clip. Chỉ gửi camera của người đồng ý tham gia tới dịch vụ.

Mỗi clip: đứng yên 5 giây, xoay chậm 45/90/180/270/360 độ, giơ tay, khoanh tay, đưa tay qua ngực, bước gần/xa, ra khỏi khung rồi quay lại. Máy cố định, phần thân cần đánh giá nằm trọn khung hình.

Chấm 0 = lỗi rõ, 1 = lỗi nhẹ, 2 = ổn định:

1. Đúng áo, màu, họa tiết/logo không đổi giữa các frame.
2. Cổ/vai/tay/gấu bám người, không rung/trượt.
3. Tay che áo đúng; không sinh chi thừa.
4. Giữ khuôn mặt, màu da, hình dáng người.
5. Biên áo, ánh sáng và chuyển động tự nhiên.
6. Các góc xoay nhất quán; mặt sau có ảnh/asset đối chứng.

Clip đạt khi mọi mục ≥1 và tổng ≥10/12. Thiếu ảnh mặt sau thì ghi chưa xác minh, không đánh đạt 360 độ. Sau khi áo đạt mới mở rộng quần, giày, mũ và phối nhiều món.

## Vòng đời và lỗi

- Đổi đồ 20 lần: kết quả cuối đúng lựa chọn cuối, RAM không tăng theo số lần.
- Mở/thoát 10 vòng: giải phóng camera, tracks, socket và tác vụ.
- Background/khóa máy/mất mạng/reconnect: không còn phiên ngầm ngoài chủ đích; không trình bày camera gốc như kết quả AI.
- Từ chối camera/token hết hạn/quota/phiên hết giờ: lỗi rõ, có thử lại.
- Chạy Wi-Fi ổn định và mạng giới hạn; ghi packet loss, FPS, độ trễ, freeze riêng.

Web hiện giới hạn phiên mặc định 120 giây. Nhiều phiên nối tiếp không được báo là 15 phút live liên tục. Không tự tăng giới hạn hoặc tạo phiên tính phí chỉ để đo.

## Thứ tự triển khai

1. Kiểm chứng engine thật với một áo trên web hiện có trước khi chọn cho native.
2. Nếu độ trễ/góc xoay không đạt, đánh fail và đánh giá engine khác hoặc AR 3D.
3. Tích hợp engine đạt vào native, đo lại RAM, nhiệt và vòng đời.
4. Xác minh engine hỗ trợ giày/mũ trước khi chỉ thêm UI chọn đồ.

Chưa thực hiện: live session, đo FPS/RAM trên máy thật, sửa BE. Native đã được triển khai; test mock không chứng minh chất lượng AI. Lượt chạy test Python trước không thực hiện được vì thiếu pytest. Reel tham khảo chưa truy cập được.
