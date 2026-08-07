# 🐛 DEBUG GUIDE — Dành cho VS Code (từ NetBeans chuyển qua)

> **Mục tiêu:** Biết cách tự trace 1 request từ FE → BE → DB, tự trả lời được câu hỏi của giảng viên

---

## 0. TRƯỚC KHI DEBUG: KIỂM TRA VS CODE

### Extension cần có

Mở VS Code → `Ctrl+Shift+X` (Extensions) → tìm và cài nếu chưa có:

| Extension | ID | Dùng để |
|-----------|-----|---------|
| **Extension Pack for Java** | `vscjava.vscode-java-pack` | Gói tổng hợp 6 extension Java |
| **Spring Boot Extension Pack** | `vmware.vscode-boot-dev-pack` | Hỗ trợ Spring Boot |
| **Debugger for Java** | `vscjava.vscode-java-debug` | Debug Java (có trong pack trên) |

> ⚡ **Kiểm tra nhanh:** Mở 1 file `.java` bất kỳ → nếu thấy code có màu và có `Run | Debug` ở trên method `main` là OK.

---

## 1. CÁCH MỞ DỰ ÁN ĐÚNG

```
1. Mở VS Code
2. File → Open Folder... (Ctrl+K Ctrl+O)
3. Chọn thư mục: D:\FPT\KI_5\SWP\SRC\Journal-Trend-Tracking-BE
   ⚠️ QUAN TRỌNG: Mở thư mục BE, KHÔNG mở thư mục SRC gốc!
```

**Tại sao phải mở đúng thư mục BE?**
Vì file `.vscode/launch.json` (tôi vừa tạo) nằm trong thư mục BE. Nếu mở thư mục SRC gốc, VS Code sẽ không thấy file cấu hình debug.

---

## 2. GIAO DIỆN DEBUG CỦA VS CODE

### Mở tab Debug

```
Cách 1: Click icon con bọ + tam giác ở thanh bên trái (Ctrl+Shift+D)
Cách 2: Ctrl+Shift+D
```

Giao diện debug có 4 vùng chính:

```
┌─────────────────────────────────────────────────────────┐
│  TOOLBAR (thanh công cụ debug)                          │
│  ▶ Continue │ ⏭ Step Over │ ⬇ Step Into │ ⬆ Step Out  │
│  🔄 Restart │ ⏹ Stop                                   │
├───────────────────────────┬─────────────────────────────┤
│  VARIABLES (biến)         │  EDITOR (code)              │
│  ┌─────────────────┐      │                             │
│  │ this = ...       │      │  ● 42: String jwt =        │
│  │ keyword = "deep" │      │     tokenProvider. ← đang  │
│  │ userEmail = ...  │      │     generateToken(...)     │
│  │ papers = [...]   │      │     dừng ở đây             │
│  └─────────────────┘      │                             │
│  WATCH (theo dõi)         │                             │
│  ┌─────────────────┐      │                             │
│  │ papers.size()   │      │                             │
│  └─────────────────┘      │                             │
│  CALL STACK (ngăn xếp)    │                             │
│  ┌─────────────────┐      │                             │
│  │ searchByKeyword │      │                             │
│  │ searchPapers    │      │                             │
│  │ doFilter        │      │                             │
│  └─────────────────┘      │                             │
├───────────────────────────┴─────────────────────────────┤
│  DEBUG CONSOLE (gõ lệnh để xem biến)                    │
│  > papers.size()                                        │
│  42                                                     │
│  > keyword                                              │
│  "deep learning"                                        │
└─────────────────────────────────────────────────────────┘
```

### Giải thích từng vùng

| Vùng | Chức năng | Giống NetBeans |
|------|-----------|----------------|
| **VARIABLES** | Xem tất cả biến hiện tại, mở rộng object | Variables tab |
| **WATCH** | Theo dõi biểu thức cụ thể (VD: `papers.size()`) | Watches |
| **CALL STACK** | Xem chuỗi method gọi đến vị trí hiện tại | Call Stack |
| **DEBUG CONSOLE** | Gõ lệnh Java để xem giá trị biến | Evaluate Expression |
| **BREAKPOINTS** | Danh sách tất cả breakpoint đã đặt | Breakpoints tab |

---

## 3. CÁCH ĐẶT BREAKPOINT — QUAN TRỌNG NHẤT

### Cách 1: Click chuột

```
Click vào lề TRÁI của dòng code (chỗ trống cạnh số dòng)
→ Xuất hiện chấm ĐỎ ●
→ Dòng đó sẽ được highlight màu đỏ
```

### Cách 2: Phím tắt

```
Đặt con trỏ ở dòng muốn dừng → F9
```

### Cách 3: Breakpoint có điều kiện

```
Chuột phải vào chấm đỏ ● → Edit Breakpoint... → Condition
Gõ: keyword.equals("deep learning")
→ Breakpoint chỉ dừng khi keyword đúng là "deep learning"
```

### Xóa breakpoint

```
Click lại vào chấm đỏ ● → biến mất
```

---

## 4. CÁCH CHẠY DEBUG — TỪNG BƯỚC

### Bước 1: Khởi động SQL Server (nếu chưa chạy)

```bash
# Mở terminal trong VS Code: Ctrl+`
cd D:\FPT\KI_5\SWP\SRC\Journal-Trend-Tracking-BE
docker compose up -d
```

### Bước 2: Chạy debug

```
Cách 1: Ctrl+Shift+D → chọn "Debug SCITRACK (Spring Boot)" → bấm F5
Cách 2: Mở JournalTrackingApplication.java → click "Run | Debug" phía trên method main
```

### Bước 3: Đợi app start

Nhìn console ở dưới, khi thấy dòng này là xong:

```
Started JournalTrackingApplication in 8.5 seconds
```

### Bước 4: Gửi request để trigger breakpoint

```
Cách 1 (dễ nhất): Mở trình duyệt → http://localhost:8080/swagger-ui/index.html
               → Tìm PaperSearchController → GET /search → Try it out → Execute

Cách 2: Mở FE (npm run dev) → search "deep learning" trên app
```

### Bước 5: Code dừng ở breakpoint

Khi code dừng, dòng hiện tại được **highlight màu vàng**. Lúc này bạn có thể:

| Thao tác | Phím tắt | Mô tả |
|----------|----------|-------|
| **Continue** | `F5` | Chạy tiếp đến breakpoint tiếp theo |
| **Step Over** | `F10` | Chạy dòng hiện tại, KHÔNG vào method |
| **Step Into** | `F11` | Nhảy VÀO method được gọi |
| **Step Out** | `Shift+F11` | Thoát KHỎI method hiện tại |
| **Restart** | `Ctrl+Shift+F5` | Chạy lại từ đầu |
| **Stop** | `Shift+F5` | Dừng debug |

### Bảng so sánh phím VS Code vs NetBeans

| Chức năng | NetBeans | VS Code |
|-----------|----------|---------|
| Step Over | `F8` | `F10` |
| Step Into | `F7` | `F11` |
| Step Out | `Ctrl+F7` | `Shift+F11` |
| Continue | `F5` | `F5` |
| Toggle Breakpoint | `Ctrl+F8` | `F9` |
| Evaluate Expression | `Ctrl+F9` | Gõ vào **DEBUG CONSOLE** |
| Stop Debug | `Shift+F5` | `Shift+F5` |

> ⚡ **Quan trọng:** `F8` trong NetBeans = Step Over, nhưng `F8` trong VS Code KHÔNG làm gì cả. Trong VS Code, Step Over là `F10`.

---

## 5. XEM GIÁ TRỊ BIẾN — 3 CÁCH

### Cách 1: Hover chuột (nhanh nhất)

```
Đưa chuột lên tên biến trong code → popup hiện giá trị
VD: Hover vào "keyword" → hiện "deep learning"
    Hover vào "papers" → hiện "[PaperDetailResponseDTO@1234, ...]"
```

### Cách 2: VARIABLES panel

```
Nhìn sang tab VARIABLES bên trái → mở rộng object bằng cách click ▶
VD: papers → ▶ → [0] → ▶ → title = "Attention Is All You Need"
```

### Cách 3: DEBUG CONSOLE (Evaluate Expression)

```
Gõ trực tiếp vào ô DEBUG CONSOLE ở dưới:
> papers.size()
42
> keyword
"deep learning"
> request.getQuery()
"deep learning"
```

> ⚡ **DEBUG CONSOLE** giống hệt **Evaluate Expression (Ctrl+F9)** trong NetBeans. Bạn gõ bất kỳ biểu thức Java nào, nó sẽ tính toán và trả về kết quả.

---

## 6. THỰC HÀNH DEBUG LUỒNG SEARCH

### Bài tập 1: Trace search (10 phút)

```
1. Mở PaperSearchController.java
2. Tìm method searchPapers() → F9 đặt breakpoint ở dòng:
      log.info("Search request: {}", request);
3. F5 → chạy debug
4. Mở Swagger → GET /api/v1/papers/search → query="deep learning" → Execute
5. Code dừng ở breakpoint → màn hình highlight vàng
6. Nhìn VARIABLES panel → mở object "request" → xem query, page, size
7. F11 (Step Into) → nhảy vào Orchestrator
8. Tiếp tục F10 (Step Over) từng dòng
9. Khi thấy dòng: openAlexFallbackSearchService.searchTopCited(...)
   → F11 (Step Into) để xem code gọi API
10. F10 vài lần nữa → xem biến "papers" → hover chuột xem size
```

### Bài tập 2: Xem response JSON (5 phút)

```
1. Đặt breakpoint ở dòng return trong searchPapers()
2. F5 → gửi request → dừng ở breakpoint
3. DEBUG CONSOLE gõ:
   > result.getPapers().size()
   > result.getTotalElements()
4. F10 để chạy qua dòng return
5. Mở trình duyệt → xem JSON response trong Swagger
```

### Bài tập 3: Debug khi OpenAlex lỗi (5 phút)

```
1. Tìm dòng catch (Exception e) trong PaperSearchOrchestrator
2. Đặt breakpoint trong catch block
3. Tạm thời sửa URL OpenAlex thành sai (để simulate lỗi)
   Hoặc: tắt mạng wifi
4. F5 → search → dừng ở catch block
5. DEBUG CONSOLE gõ:
   > e.getMessage()
   → Xem lỗi gì
6. F10 → thấy code chạy đến return buildEmptyResult()
```

---

## 7. THÊM BIẾN VÀO WATCH

WATCH giúp bạn theo dõi biến **xuyên suốt** quá trình debug, không cần hover từng lần:

```
1. Trong tab WATCH (bên trái), click dấu +
2. Gõ: papers.size()
3. Nhấn Enter
4. Biểu thức này sẽ tự động cập nhật mỗi khi bạn step
```

---

## 8. DEBUG LOG (KHÔNG CẦN BREAKPOINT)

Đôi khi không cần breakpoint, chỉ cần đọc log:

```java
// Thêm dòng này vào code tạm thời:
log.info("DEBUG keyword={}, papers.size={}", keyword, papers.size());

// Chạy lại app → xem console → thấy log
```

### Cách xem log trong VS Code

```
Tab TERMINAL (Ctrl+`) → đây chính là console của Spring Boot
Tất cả log.info(), log.warn(), log.error() đều hiện ở đây
```

---

## 9. SỬA LỖI THƯỜNG GẶP

### "Breakpoint không dừng"

```
1. Kiểm tra app đã chạy ở debug mode chưa? (thanh dưới cùng màu cam)
2. Kiểm tra breakpoint đã được enable? (chấm đỏ, không phải chấm xám)
3. Kiểm tra code có thực sự chạy qua dòng đó không?
```

### "Không tìm thấy main class"

```
1. Mở đúng thư mục BE bằng File → Open Folder
2. Đợi VS Code import project Java (thấy "Java: Importing projects..." ở thanh dưới)
3. Nếu vẫn lỗi: Ctrl+Shift+P → "Java: Clean Java Language Server Workspace"
```

### "Debug Console không gõ được"

```
Phải đang dừng ở breakpoint thì Debug Console mới hoạt động.
Nếu app đang chạy (không dừng), Debug Console bị khóa.
```

### "Login failed for user 'dbmaster'" (SQL Server)

```
Nguyên nhân: Có 2 file .env trong project:
  - SRC/.env           → DATABASE_USERNAME=dbmaster (SAI)
  - BE/.env            → DATABASE_USERNAME=admin    (ĐÚNG)

Spring Boot dùng spring.config.import=optional:file:.env[.properties]
để load .env từ working directory. Nếu working directory là SRC/ thay vì BE/,
nó sẽ load SRC/.env → sai credentials.

Cách fix:
1. Đảm bảo mở VS Code đúng thư mục BE: File → Open Folder → chọn Journal-Trend-Tracking-BE
2. File launch.json đã được cấu hình với "cwd": "${workspaceFolder}" để fix working directory
3. Nếu vẫn lỗi: kiểm tra lại docker compose đã chạy chưa (docker compose up -d trong thư mục BE)
```

---

## 10. TEST API BẰNG SWAGGER (KHÔNG CẦN FE)

Đây là cách nhanh nhất để test API mà không cần mở FE:

```
1. Mở trình duyệt → http://localhost:8080/swagger-ui/index.html
2. Tìm controller muốn test (VD: PaperSearchController)
3. Click GET /api/v1/papers/search
4. Click "Try it out"
5. Nhập query = "deep learning", page = 0, size = 5
6. Click Execute
7. Xem Response Body → chính là JSON trả về cho FE
```

**Swagger đặc biệt hữu ích để:**
- Test API không cần login (dùng cho endpoint public)
- Với endpoint cần login: test bằng Postman hoặc đăng nhập trên FE trước

---

## 11. TÓM TẮT: QUY TRÌNH DEBUG 1 TÍNH NĂNG

```
┌─────────────────────────────────────────────────────┐
│ 1. MỞ SWAGGER → test API → xem response JSON       │
│    http://localhost:8080/swagger-ui/index.html       │
├─────────────────────────────────────────────────────┤
│ 2. ĐẶT BREAKPOINT (F9) trong Controller             │
│    → Dòng đầu tiên của method searchPapers()         │
├─────────────────────────────────────────────────────┤
│ 3. BẤM F5 → chạy debug → gửi request từ Swagger     │
├─────────────────────────────────────────────────────┤
│ 4. HOVER CHUỘT → xem giá trị biến                   │
│    HOẶC: DEBUG CONSOLE → gõ tên biến                │
├─────────────────────────────────────────────────────┤
│ 5. F11 (Step Into) → vào Service                    │
│    F10 (Step Over) → chạy từng dòng                  │
├─────────────────────────────────────────────────────┤
│ 6. F5 (Continue) → chạy đến breakpoint tiếp theo    │
│    Shift+F5 → dừng debug                            │
└─────────────────────────────────────────────────────┘
```

### Phím tắt VS Code Debug (in ra dán lên tường)

```
F5          = Continue (chạy tiếp)
F9          = Toggle Breakpoint (đặt/xóa breakpoint)
F10         = Step Over (chạy qua, không vào method)
F11         = Step Into (nhảy vào method)
Shift+F11   = Step Out (thoát khỏi method)
Shift+F5    = Stop (dừng debug)
```

---

## 12. LỘ TRÌNH TẬP DEBUG (LÀM NGAY BÂY GIỜ)

- [ ] **5 phút:** Mở VS Code → mở thư mục BE → Ctrl+Shift+D → F5 → đợi app start
- [ ] **10 phút:** Đặt breakpoint trong `PaperSearchController.searchPapers()` → Swagger test → xem biến hover
- [ ] **10 phút:** Đặt breakpoint trong `PaperSearchOrchestrator.searchByKeyword()` → F11 step vào → trace hết luồng
- [ ] **5 phút:** DEBUG CONSOLE gõ `papers.size()` → `keyword` → `request.getQuery()`
- [ ] **5 phút:** Đặt breakpoint trong `AuthServiceImpl.login()` → Swagger login → xem JWT token
- [ ] **5 phút:** Cố ý tắt mạng → search → xem catch block chạy → `buildEmptyResult()`