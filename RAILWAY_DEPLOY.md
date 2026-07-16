# 🚂 Railway Deployment Guide — SCITRACK Backend

## Kiến trúc trên Railway

```
Railway Project: scitrack
│
├── Service 1: "api" (Spring Boot)
│   └── Build từ Dockerfile trong repo GitHub
│   └── Port: 8080 (Railway tự gán qua biến PORT)
│
├── Service 2: "sqlserver" (SQL Server Express)
│   └── Image: mcr.microsoft.com/mssql/server:2022-express
│   └── Port: 1433 (internal only)
│
└── Internal Network: api ↔ sqlserver (latency < 1ms)
```

---

## Bước 1: Đẩy code lên GitHub

Railway deploy từ GitHub repo. Đảm bảo các file sau đã có trên repo:

- [x] `Dockerfile` — multi-stage build
- [x] `.dockerignore`
- [x] `docker-compose.yml` — local testing

---

## Bước 2: Tạo Railway Project

1. Vào [railway.app](https://railway.app) → **New Project**
2. Chọn **Deploy from GitHub repo**
3. Chọn repo chứa code backend
4. Railway tự phát hiện `Dockerfile` và build

---

## Bước 3: Thêm SQL Server Express Service

1. Trong project, click **+ New Service**
2. Chọn **Docker Image**
3. Điền: `mcr.microsoft.com/mssql/server:2022-express`
4. Đặt tên service: `sqlserver`

### Environment Variables cho SQL Server:

| Variable | Value |
|---|---|
| `ACCEPT_EULA` | `Y` |
| `MSSQL_SA_PASSWORD` | `<mật khẩu mạnh của bạn>` |
| `MSSQL_PID` | `Express` |

### Network:
- **Không cần public domain** cho SQL Server (chỉ internal)
- Railway tự động tạo DNS nội bộ: `sqlserver.railway.internal`

---

## Bước 4: Cấu hình Environment Variables cho Spring Boot Service

Vào service "api" → **Variables** → thêm các biến sau:

### Bắt buộc:

| Variable | Value |
|---|---|
| `DATABASE_HOST` | `sqlserver.railway.internal` |
| `DATABASE_PORT` | `1433` |
| `DATABASE_NAME` | `JournalTrendDB` |
| `DATABASE_USERNAME` | `sa` |
| `DATABASE_PASSWORD` | `<giống MSSQL_SA_PASSWORD ở trên>` |
| `NEO4J_URI` | `neo4j+s://84c52b03.databases.neo4j.io` |
| `NEO4J_USERNAME` | `84c52b03` |
| `NEO4J_PASSWORD` | `<Neo4j password của bạn>` |
| `NEO4J_DATABASE` | `84c52b03` |
| `JWT_SECRET` | `<secret key của bạn>` |

### Khuyến nghị (có thì đầy đủ tính năng hơn):

| Variable | Value |
|---|---|
| `FRONTEND_URL` | `<URL frontend Railway của bạn>` |
| `MAIL_HOST` | `smtp.gmail.com` |
| `MAIL_PORT` | `587` |
| `MAIL_USERNAME` | `<email gmail>` |
| `MAIL_PASSWORD` | `<app password>` |
| `CLOUDINARY_CLOUD_NAME` | `<cloudinary cloud name>` |
| `CLOUDINARY_API_KEY` | `<cloudinary api key>` |
| `CLOUDINARY_API_SECRET` | `<cloudinary api secret>` |
| `DEEPSEEK_API_KEY` | `<deepseek api key>` |
| `CORE_API_KEY` | `<core api key>` |
| `OPENALEX_API_KEY` | `<openalex api key>` |
| `GOOGLE_CLIENT_ID` | `<google client id>` |
| `APP_AUTO_SYNC_ENABLED` | `false` (tắt auto-sync để tiết kiệm tài nguyên) |

---

## Bước 5: Kiểm tra Health Check

1. Railway tự dùng `/actuator/health` để health check
2. Sau khi deploy, vào tab **Deployments** → xem logs
3. Đợi SQL Server khởi động xong (~30s), Spring Boot sẽ tạo bảng từ `schema.sql`

---

## Bước 6: Lấy Public Domain

1. Vào service "api" → **Settings** → **Networking**
2. Bật **Public Networking** → tạo domain (VD: `scitrack-api.up.railway.app`)
3. FE sẽ gọi API qua domain này

---

## Chi phí ước tính (chạy 24/7)

| Service | vCPU | RAM | ~$/tháng |
|---|---|---|---|
| Spring Boot API | 1-2 | 1-2 GB | ~$8-12 |
| SQL Server Express | 1-2 | 2-3 GB | ~$12-18 |
| **Tổng** | | | **~$20-30** |

> Lưu ý: Đây là ước tính. Railway tính theo **compute time thực tế**. Nếu ít traffic, chi phí thấp hơn. $5 credit miễn phí mỗi tháng được trừ vào tổng.

---

## Tips

1. **Tắt auto-sync**: `APP_AUTO_SYNC_ENABLED=false` để tránh gọi OpenAlex liên tục tốn tài nguyên
2. **Giảm HikariCP pool**: Có thể giảm `maximum-pool-size` từ 10 xuống 5 nếu ít user
3. **Bật auto-sleep**: Railway có thể tắt service khi không dùng (nếu chấp nhận cold start)
4. **Backup database**: SQL Express volume data sẽ mất nếu service bị xóa — nên backup định kỳ
