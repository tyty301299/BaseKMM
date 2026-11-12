# Học kỹ năng từ video — triển khai và nghiệm thu

Cập nhật 12/11/2026. Đã triển khai source Android, iOS và BE trong `../shadown-fourward`. Chưa deploy production; build pass không xác nhận chất lượng AI hay camera thật.

## Đầu ra người dùng

Từ màn Dạy kỹ năng, nhập tên/ghi chú, chọn hoặc quay video. App upload dạng stream, nhận ticket riêng và lưu trên thiết bị. BE tách ảnh/audio, biên dịch thành bản nháp có tóm tắt, bước, ảnh tham chiếu, nguồn AI/mock và kết quả kiểm chứng. Người dùng xem video gốc, duyệt/sửa/đổi ảnh và xác minh trước khi lưu. Bài đã lưu mở bằng Guide hiện có để người khác thực hành qua camera.

App vẫn hỗ trợ camera + transcript trực tiếp. Video gốc là một luồng riêng, không phải video thử đồ.

## Cấu hình BE

- Python 3.12 đã được kiểm thử; cần `ffmpeg` và `ffprobe` trên PATH. Dockerfile đã thêm ffmpeg.
- Bật `SHADOW_VIDEO_TEACH_ENABLED=1` trong môi trường BE sau khi cấu hình provider AI. Mặc định tắt, `/config` trả `available=false` và upload trả 503.
- Một process uvicorn (`--workers 1`). Worker dùng file lock để ngăn hai process cùng tiêu thụ thư mục job.
- Job nằm cạnh thư mục skills: `${SHADOW_SKILLS_DIR}-video-jobs`. Với Docker mặc định: `/data/skills-video-jobs`; compose đã có named volume `teach_video_jobs`. Không đặt thư mục này vào media public.
- Giữ volume skills và volume video jobs qua mỗi lần nâng cấp. SQLite lưu trạng thái và hash token; kết quả được ghi xong trước khi status chuyển `ready`.
- Access gate hiện có vẫn áp dụng. Mỗi request job cần thêm `x-shadow-job-token`; không đưa token vào URL hay log.
- Compiler/vision tái dùng cấu hình provider hiện có. Health server lần kiểm tra gần nhất báo compiler `qwen3.6-35b-a3b`, vision `qwen3-vl-8b-instruct`; đó là cấu hình công bố, chưa chứng minh chất lượng đầu ra.
- STT server tùy chọn dùng voice client hiện có với model `large-v3-turbo`, mỗi đoạn audio 15 giây. Nếu không có transcript, phải có ghi chú mô tả thao tác. Voice hướng dẫn native vẫn là Android TextToSpeech / iOS AVSpeechSynthesizer.

Thay đổi BE được đóng gói trong `output/backend-video.patch`, với SHA256 trước/sau ở `output/backend-video-manifest.json`. Đã áp dụng 10 file vào BE repo và kiểm tra hash. Chưa build Docker image hoặc thay đổi server đang chạy.

## Vòng đời tác vụ

`uploading → uploaded → queued → extracting → transcribing (nếu có) → learning → verifying → ready`.

- App đóng màn hình: đóng nguồn file, dừng upload/poll cục bộ. Nếu đã nhận ticket, tác vụ BE tiếp tục; mở lại màn Dạy rồi chọn **Mở lại kết quả**.
- Start gọi lặp không tạo tác vụ mới. Không tự retry cuộc gọi AI có thể tính phí.
- BE restart: các job đang xử lý chuyển `interrupted`; người dùng chọn **Thử lại**. Job queued tiếp tục được nhận.
- **Dừng xử lý** hủy worker nhưng giữ video và ticket để retry.
- **Xóa video/tác vụ trên máy chủ** cần xác nhận, xóa source/result trong job; bài đã lưu độc lập không bị xóa. DELETE gọi lặp thành công.
- Reset bản nháp không âm thầm xóa ticket. Khi còn job, không trộn với một phiên quay camera khác.
- Instruction đã sửa cập nhật lời đọc, vô hiệu kiểm chứng cũ, chặn lưu đến khi BE tạo lại detector và verify `ok`/`fixed`. BE cũ chưa trả `detector_refreshed` sẽ bị app từ chối an toàn.

## Giới hạn cần biết

- Video tối đa **32 MB / 180 giây / cạnh 4096px**; tối đa 30 job chưa xóa trên server. Không upload resume theo byte; mạng ngắt khi upload cần gửi lại.
- Tối đa 24 ảnh mẫu cạnh 448px. Timestamp transcript theo đoạn 15 giây, không phải word-level; không đảm bảo bắt được thao tác ngắn giữa hai ảnh.
- App xem lại video gốc, chưa có timeline tự seek theo từng bước. Chưa có đồng bộ ticket giữa nhiều máy/tài khoản.
- Chưa có TTL tự xóa video. Cần người dùng xóa job không dùng; video gốc local vẫn được giữ. Draft compiler/ảnh được quản lý theo cơ chế skills hiện có, không thuộc việc xóa thư mục job.
- Nếu upload xong nhưng response/ticket bị mất do mạng hoặc app chết đúng thời điểm đó, job chưa có chủ ở phía app có thể chiếm quota; chưa có cơ chế recovery bằng upload ID phía client.
- Các kiểm chứng dùng ảnh buổi dạy không thay thế bộ ảnh độc lập của người học. Chưa nghiệm thu nhận nhầm bước, che khuất, thiếu sáng hoặc tốc độ xử lý AI thật.
- UI video mới chủ yếu tiếng Việt, chưa hoàn tất bản dịch tiếng Anh.

## Kiến trúc

- Shared `domain/model/TeachVideo`, `domain/repository/TeachVideoSource`, `TeachRepository`: hợp đồng nghiệp vụ, không phụ thuộc Ktor/SwiftUI/Compose.
- `data/dto/shadow`, mapper, `data/remote/shadow/KtorShadowApi`: wire DTO, JSON, stream upload và kiểm tra hợp đồng BE.
- `presentation/teach/TeachStore`: state, chống trùng, generation/cancel, persistence ticket, polling, review/verify/save.
- Native `Platform/Media`: đọc file và lưu ticket; `Features/Teach/Presentation` (iOS), `features/teach/presentation` (Android): picker/camera/video player và UI.
- BE `teach_video/{api,storage,processor,detector}.py`: API, SQLite, media pipeline và làm mới detector. Dùng compiler/verifier hiện có.

## Kết quả kiểm tra

- BE: **290 passed**, gồm ffmpeg/ffprobe trên clip thật, upload → mock compile → restart → đọc kết quả → delete lặp, token sai, config tắt, detector cần AI.
- Shared: XML **81 tests, 0 failures/errors**. Có **4 LiveServerTest return sớm** khi chưa opt-in; không tính là 81 kiểm tra server thật.
- Android `assembleDebug`: thành công; APK tại `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
- iOS Simulator `xcodebuild`: thành công; app tại `.toolchains/DerivedData/Build/Products/Debug-iphonesimulator/KmmBase.app`.
- iPhone 17 Simulator iOS 27.2: cài và launch thành công sau khi bổ sung `@executable_path/Frameworks` và ký ad-hoc Agora. Script build/run dùng `CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=-`; không cần Apple account cho simulator. Đã bổ sung mô tả quyền speech recognition. Đã thấy hộp quyền ATT của app; chưa đi tiếp qua hộp quyền. Đây là smoke test khởi động, chưa kiểm chứng camera/video/AI end-to-end.
- `tools/check_architecture.py` và `git diff --check`: pass.
- Server được bảo vệ đang trả 401 với mã cấu hình hiện tại. Chưa xác minh API mới trên deployment.
- iPhone thật trước đó bị chặn bởi Xcode account/provisioning của team Ty Nguyen; chưa xác nhận signing đã được sửa.

## Kịch bản nghiệm thu trên cả Android và iOS

1. Mã đúng/sai, server chưa bật video, offline: hiện lỗi đúng; không báo hoàn tất.
2. Clip ngắn có lời tiếng Việt; clip không tiếng nhưng có ghi chú; clip không tiếng/không ghi chú phải báo cần bổ sung.
3. Video quá 32 MB/180 giây, file hỏng, hủy picker, từ chối camera/micro; không gửi nhầm nguồn cũ.
4. Upload một lần khi bấm lặp; thoát/mở app sau khi có ticket; nhận lại đúng kết quả, không tự tạo job AI mới.
5. Dừng → retry; server restart lúc learning → interrupted → retry; xóa sau khi response lần đầu bị mất.
6. Review phải chỉ rõ AI/mock, tóm tắt, bước/ảnh; sửa instruction → Save bị chặn → verify đúng tiêu chí mới → lưu.
7. Người thứ hai mở bài đã lưu, thực hiện qua camera; thử cố tình sai/che khuất, xác minh không tự chuyển bước sai.
8. Gói quà/gấp áo/gấp quần: âm thanh, nghe lại, manual, AI, lịch sử từ Home; background dừng camera/voice.
9. Ghi thời gian từng phase và kết quả thực tế. Chỉ đánh pass khi thấy đầu ra đúng; không đánh pass vì HTTP 200 hoặc build thành công.
