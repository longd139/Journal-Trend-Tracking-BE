# 🚀 Hướng dẫn chạy OpenAlex Scraper

Tool này giúp bạn lấy dữ liệu bài báo từ OpenAlex về, xuất ra file `.sql` để import thẳng vào database SCITRACK.

---

## Yêu cầu

Cài **Node.js 18+** một lần duy nhất: [tải tại đây](https://nodejs.org)

Kiểm tra đã cài chưa:
```bash
node -v
# Phải ra v18.x.x hoặc cao hơn
```

---

## Chạy tool

Mở **Command Prompt** hoặc **PowerShell**, gõ:

```bash
cd Journal-Trend-Tracking-BE\tools\openalex-scraper
node scraper.js
```

Tool sẽ hỏi từng bước, bạn chỉ cần trả lời:

```
📧 Your email: your@email.com           ← email của bạn

📋 Choose mode:
   [1] Search by keyword                ← tìm theo từ khóa
   [2] Batch by OpenAlex IDs / URLs     ← đưa link OpenAlex có sẵn
   Enter 1 or 2 [1]: 1

🔑 Keyword (e.g. machine unlearning): AI   ← từ khóa muốn tìm

📊 How many papers? [0-10000, 0=ALL, default=500]: 500
```

Đợi tool chạy xong, kết quả nằm trong folder **`output/`**.

---

## Output

File sinh ra có dạng: **`papers_2026-06-29T...sql`**

Mở file đó bằng **SSMS** hoặc **Azure Data Studio**, chạy vào database SCITRACK là xong.

> 📌 **An toàn:** File SQL có upsert logic — chạy lại bao nhiêu lần cũng không bị trùng dữ liệu.

---

## Các chế độ

| Chế độ | Cách dùng |
|--------|-----------|
| Tìm từ khóa | Chọn mode `1`, nhập keyword |
| Lấy theo link | Chọn mode `2`, paste link OpenAlex |
| Lấy tất cả | Nhập limit `0` |
| Không dùng email | Enter bỏ qua (rate limit thấp hơn) |

---

## Mẹo

- **Limit 500-1000** là hợp lý nhất — nhanh mà vẫn bắt được hầu hết bài quan trọng
- Dùng **email cá nhân** để tăng rate limit (100k requests/ngày)
- Sau khi chạy xong, tool sẽ hiện **bảng thống kê**: tổng citations, bài được cite nhiều nhất, phân bố theo năm, tạp chí...

---

## Fix lỗi thường gặp

| Lỗi | Cách fix |
|-----|----------|
| `node is not recognized` | Cài Node.js từ [nodejs.org](https://nodejs.org) |
| `Error: no papers retrieved` | Kiểm tra lại keyword hoặc link OpenAlex |
| `429 Rate limited` | Thêm email `-m your@email.com` |
