# Nguồn ảnh trong prototype

Các ảnh đều lấy từ bộ demo đã có trong `shadown-fourward/backend/wardrobe_seed/images`, được thu nhỏ và nhúng vào prototype. Không phải ảnh riêng của người dùng, không phải ảnh AI mới sinh, không dùng làm tài nguyên thương mại.

- Áo khoác xanh rêu: ví dụ Kolors Virtual Try-On (`assets/cloth/02_upper.png`); quyền ảnh gốc chưa ghi rõ; chỉ dùng demo phi thương mại theo ghi chú repo.
- Blouse cam đào và polo hồng: ví dụ IDM-VTON / VITON-HD; chỉ dùng demo, nghiên cứu phi thương mại. Repo ghi CC BY-NC-SA 4.0 cho Space.
- Jeans, quần âu và sneaker: minh họa do dự án vẽ bằng Pillow, CC0.

Nguồn chi tiết: `shadown-fourward/backend/wardrobe_seed/SOURCES.md`.

Icon đường nét và hình nhân thử đồ và minh họa giá đỡ/tay trong camera do prototype vẽ bằng SVG. Không phải camera feed hoặc bằng chứng đánh giá thị giác của AI. Không sao chép hình ảnh từ Pinterest.

## Native app assets

Hai ảnh `coat`/`blouse` được tái sử dụng trong `iosApp/iosApp/Assets.xcassets` và `androidApp/src/main/res/drawable` để khớp bản PDF, vẫn chỉ là tài nguyên demo theo điều kiện nêu trên.

Icon chữ S trong `brand/shadow-icon.svg` là thiết kế vector mới cho SHADOW; PNG 1024px là bản xuất từ vector để đóng gói launcher icons. Không có góc bo được vẽ sẵn: iOS/Android tự áp dụng mask hệ thống.
