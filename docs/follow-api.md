# Follow API — `/api/v1/follows`

Tất cả 4 endpoint đều yêu cầu xác thực JWT (`Authorization: Bearer <token>`).

---

## 1. POST — Add Follow

Theo dõi một journal, research topic, hoặc keyword. Chỉ được chọn **đúng 1** target mỗi request.

```
POST /api/v1/follows
Content-Type: application/json
Authorization: Bearer <jwt>
```

### Request Body (`FollowRequest`)

| Field | Type | Required | Ghi chú |
|-------|------|:--------:|---------|
| `journalId` | `UUID` | XOR | Theo dõi journal |
| `topicId` | `UUID` | XOR | Theo dõi research topic |
| `keywordId` | `UUID` | XOR | Theo dõi keyword |
| `notifyEnabled` | `Boolean` | ❌ | Default `true` |

```json
{
  "journalId": "550e8400-e29b-41d4-a716-446655440000",
  "topicId": null,
  "keywordId": null,
  "notifyEnabled": true
}
```

### Response (200) — `AppResponse<FollowResponse>`

```json
{
  "status": 200,
  "message": "Follow added successfully",
  "timestamp": "2026-06-26T10:30:00",
  "data": {
    "followId": "UUID",
    "journalId": "UUID | null",
    "journalName": "string | null",
    "topicId": "UUID | null",
    "topicName": "string | null",
    "keywordId": "UUID | null",
    "keywordText": "string | null",
    "notifyEnabled": true,
    "createdAt": "2026-06-26T10:30:00"
  }
}
```

### Error Codes

| HTTP | Mã lỗi | Ý nghĩa |
|-----:|--------|---------|
| 400 | `FOLLOW_INVALID_TARGET` | Không gửi đúng 1 target (0 hoặc >1) |
| 404 | `RESOURCE_NOT_FOUND` | Journal/Keyword không tồn tại |
| 404 | `TOPIC_NOT_FOUND` | Research Topic không tồn tại |
| 409 | `FOLLOW_ALREADY_EXISTS` | Đã follow target này rồi |
| 403 | `FOLLOW_LIMIT_EXCEEDED` | ACADEMIC_USER vượt giới hạn follow |

> **Logic**: Researcher không giới hạn số follow. ACADEMIC_USER bị giới hạn theo config `academic_max_follows` (mặc định 20).

---

## 2. GET — Get My Follows

Lấy danh sách tất cả follow của user hiện tại.

```
GET /api/v1/follows
Authorization: Bearer <jwt>
```

Không có request params hay body.

### Response (200) — `AppResponse<List<FollowResponse>>`

```json
{
  "status": 200,
  "message": "Follows retrieved",
  "timestamp": "2026-06-26T10:30:00",
  "data": [
    {
      "followId": "UUID",
      "journalId": "UUID | null",
      "journalName": "Nature | null",
      "topicId": "UUID | null",
      "topicName": "string | null",
      "keywordId": "UUID | null",
      "keywordText": "string | null",
      "notifyEnabled": true,
      "createdAt": "2026-06-20T08:15:00"
    }
  ]
}
```

### Error Codes

| HTTP | Mã lỗi | Ý nghĩa |
|-----:|--------|---------|
| 404 | `USER_NOT_FOUND` | User không tồn tại |

---

## 3. PUT — Toggle Notification

Bật/tắt thông báo cho một follow.

```
PUT /api/v1/follows/{followId}?notifyEnabled=false
Authorization: Bearer <jwt>
```

| Param | Vị trí | Required | Mô tả |
|-------|--------|:--------:|-------|
| `followId` | Path | ✅ | UUID của follow |
| `notifyEnabled` | Query | ✅ | `true` = bật, `false` = tắt |

### Response (200) — `AppResponse<FollowResponse>`

```json
{
  "status": 200,
  "message": "Follow updated successfully",
  "timestamp": "2026-06-26T10:30:00",
  "data": {
    "followId": "UUID",
    "journalId": "UUID | null",
    "journalName": "string | null",
    "topicId": "UUID | null",
    "topicName": "string | null",
    "keywordId": "UUID | null",
    "keywordText": "string | null",
    "notifyEnabled": false,
    "createdAt": "2026-06-20T08:15:00"
  }
}
```

### Error Codes

| HTTP | Mã lỗi | Ý nghĩa |
|-----:|--------|---------|
| 404 | `FOLLOW_NOT_FOUND` | Follow không tồn tại hoặc không thuộc về user |

> **Logic**: Verify ownership — nếu follow không thuộc về user hiện tại, trả về `FOLLOW_NOT_FOUND` (không leak thông tin).

---

## 4. DELETE — Unfollow

Hủy theo dõi một follow.

```
DELETE /api/v1/follows/{followId}
Authorization: Bearer <jwt>
```

| Param | Vị trí | Required | Mô tả |
|-------|--------|:--------:|-------|
| `followId` | Path | ✅ | UUID của follow |

### Response (200) — `AppResponse<Void>`

```json
{
  "status": 200,
  "message": "Unfollowed successfully",
  "timestamp": "2026-06-26T10:30:00"
}
```

> `data` = `null` (không có trong JSON response do `@JsonInclude(Include.NON_NULL)`).

### Error Codes

| HTTP | Mã lỗi | Ý nghĩa |
|-----:|--------|---------|
| 404 | `FOLLOW_NOT_FOUND` | Follow không tồn tại hoặc không thuộc về user |

---

## DTO Reference

### `FollowRequest`

```java
UUID journalId;        // XOR — 1 trong 3
UUID topicId;          // XOR — 1 trong 3
UUID keywordId;        // XOR — 1 trong 3
Boolean notifyEnabled; // default true
```

### `FollowResponse`

```java
UUID followId;
UUID journalId;        // non-null nếu target là journal
String journalName;
UUID topicId;          // non-null nếu target là topic
String topicName;
UUID keywordId;        // non-null nếu target là keyword
String keywordText;
Boolean notifyEnabled;
LocalDateTime createdAt;
```

### `AppResponse<T>`

```java
int status;                 // 200
String message;
T data;                     // FollowResponse | List<FollowResponse> | null
LocalDateTime timestamp;    // auto-set tại thời điểm response
```

---

## Summary

| # | Method | Endpoint | Chức năng | Data (Response) |
|:-:|--------|----------|-----------|-----------------|
| 1 | `POST` | `/api/v1/follows` | Follow journal/topic/keyword | `FollowResponse` |
| 2 | `GET` | `/api/v1/follows` | Danh sách đang follow | `List<FollowResponse>` |
| 3 | `PUT` | `/api/v1/follows/{id}?notifyEnabled=` | Bật/tắt thông báo | `FollowResponse` |
| 4 | `DELETE` | `/api/v1/follows/{id}` | Hủy follow | `null` |
