# Scientific Journal Publication Trend Tracking System - Backend

## Công nghệ sử dụng

- Java 21
- Spring Boot 3.5.14
- Spring Data JPA
- SQL Server
- Maven
- Lombok

## Yêu cầu môi trường

- JDK 21
- SQL Server đang chạy
- Maven

## Cài đặt

### 1. Clone project

```
git clone <repository-url>
```

### 2. Tạo database

Tạo database tên: JournalTrendDB

### 3. Tạo file application-local.properties

Tạo file tại **src/main/resources/application-local.properties**
Điền thông tin kết nối SQL Server của bạn (xem mục cấu hình)

### 4. Chạy project

```
mvn spring-boot:run
```

## API chạy tại

http://localhost:8080

## Quy tắc Git

- Không push trực tiếp lên main
- Không push file application-local.properties
- Mỗi feature tạo branch riêng từ develop
- Đặt tên branch: feature/tên-chức-năng

## Commit convention

feat: thêm API lấy danh sách journal
fix: sửa lỗi authentication
refactor: tối ưu query
docs: cập nhật README
