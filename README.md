# Scientific Journal Publication Trend Tracking System - Backend

Backend cho hệ thống **Scientific Journal Publication Trend Tracking System**.

---

## Công nghệ sử dụng

- Java 21
- Spring Boot 3.5.14
- Spring Data JPA
- SQL Server 2022 (chạy qua Docker)
- Maven
- Lombok

---

## Yêu cầu môi trường

| Công cụ        | Phiên bản |
| -------------- | --------- |
| JDK            | 21+       |
| Maven          | 3.x       |
| Docker Desktop | Mới nhất  |
| SSMS           | Bất kỳ    |

> ⚠️ **QUAN TRỌNG:** Không dùng SQL Server cài sẵn trên máy. Team thống nhất dùng SQL Server 2022 qua Docker để tránh lỗi tương thích.

---
## Cấu hình JDK 21
### Bước 1 — Cài đặt JDK 21 & Cấu hình IDE
1. Cài đặt trên máy tính (Bắt buộc để chạy được lệnh mvn):

- Tải và cài đặt JDK 21 (khuyên dùng Eclipse Temurin 21 hoặc Oracle JDK).

- Mở Environment Variables của Windows.

- Tạo biến JAVA_HOME trỏ tới thư mục cài đặt JDK 21 (VD: C:\Program Files\Java\jdk-21).

- Thêm %JAVA_HOME%\bin vào biến Path.

- Mở Terminal gõ java -version và mvn -version, nếu hiện 21.x là thành công.

2. Cấu hình trên IntelliJ IDEA:

- Mở File > Project Structure (Ctrl + Alt + Shift + S): Ở mục Project, chỉnh SDK và Language level thành 21.

- Mở File > Settings (Ctrl + Alt + S): Tìm đến Build, Execution, Deployment > Build Tools > Maven > Runner. Ở mục JRE, chọn 21 (hoặc Project JDK).
---

## Cài đặt và chạy project

### Bước 1 — Clone project

```bash
git clone <repository-url>
cd Journal-Trend-Tracking-BE
```

---

### Bước 2 — Khởi động SQL Server bằng Docker

Mở Docker Desktop, đảm bảo trạng thái **Engine running**.

Mở terminal tại thư mục gốc project, chạy:

```bash
docker compose up -d
```

Kiểm tra container đang chạy:

```bash
docker ps
```

Thấy `journal_sqlserver` với status **Up** là thành công.

> SQL Server sẽ chạy tại `localhost,1434`

---

### Bước 3 — Kết nối SSMS vào Docker SQL Server

Mở SSMS, điền thông tin kết nối:

| Field          | Giá trị                                |
| -------------- | -------------------------------------- |
| Server name    | `localhost,1434`                       |
| Authentication | `SQL Server Authentication`            |
| Login          | `sa`                                   |
| Password       | _(xem file `.env` hoặc hỏi team lead)_ |

> ⚠️ Phải chọn **SQL Server Authentication**, không phải Windows Authentication.
> Nếu máy bạn có sẵn SQL Server cũ, dùng đúng port `1434` để tránh nhầm.

---

### Bước 4 — Chạy script tạo database

Sau khi kết nối SSMS thành công, mở file:

```
database/testJournal1.sql
```

Nhấn **Execute (F5)** để chạy toàn bộ script.

Script sẽ tự động:

- Tạo database `JournalTrendDB`
- Tạo toàn bộ 23 bảng
- Tạo Views và Stored Procedures

---

### Bước 5 — Tạo file môi trường

Tạo file `src/main/resources/application-local.properties`:

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1434;databaseName=JournalTrendDB;encrypt=false;trustServerCertificate=true
spring.datasource.username=sa
spring.datasource.password=<password của Docker SQL Server>
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
```

> File này không được push lên GitHub. Mỗi người tự tạo trên máy của mình.

---

### Bước 6 — Chạy project

```bash
mvn spring-boot:run
```

Backend sẽ chạy tại:

```
http://localhost:8080
```

---

## Quản lý Docker

| Lệnh                     | Tác dụng                    |
| ------------------------ | --------------------------- |
| `docker compose up -d`   | Khởi động SQL Server        |
| `docker compose down`    | Tắt SQL Server (giữ data)   |
| `docker compose down -v` | Tắt và **xóa toàn bộ data** |
| `docker ps`              | Xem container đang chạy     |
| `docker compose logs`    | Xem log nếu lỗi             |

> ⚠️ Không chạy `docker compose down -v` khi đã có data quan trọng.

---

## Build project

```bash
mvn clean package
```

---

## Cấu trúc thư mục

```
src/main/java/com/gfi/journaltracking/
├── config/          # Security config, CORS, Beans
├── controller/      # REST API endpoints
├── service/         # Business logic
│   └── impl/        # Implementations
├── repository/      # JPA Repositories
├── entity/          # Database entities
├── dto/             # Data Transfer Objects
│   ├── request/
│   └── response/
├── exception/       # Custom exceptions
├── utils/           # Helper classes
└── constants/       # Enums, constants
```

---

## Quy tắc Git

- Không push trực tiếp lên `main`
- Không push file `application-local.properties`
- Không push file `.env`
- Mỗi feature tạo branch riêng từ `develop`
- Đặt tên branch: `feature/tên-chức-năng`
- Phải tạo Pull Request trước khi merge

---

## Tạo branch mới

```bash
git checkout develop
git pull
git checkout -b feature/tên-chức-năng
```

---

## Commit convention

```bash
feat: thêm API lấy danh sách journal
fix: sửa lỗi authentication
refactor: tối ưu query
docs: cập nhật README
style: format code
```

---

## Lưu ý

- Không push file `application-local.properties`
- Không push file `.env`
- Không sửa branch của người khác
- Luôn `git pull develop` mới nhất trước khi code
- Luôn bật Docker Desktop trước khi chạy project

---

## Contributors

Backend Team - Scientific Journal Publication Trend Tracking System
