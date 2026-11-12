# 04 — RequestIdFilter: MDC, theo dõi một request qua nhiều thread

**Source:** `src/main/java/com/example/async/RequestIdFilter.java` (+ `logging.pattern.console` trong `application.properties`)
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Filter làm gì

```java
String requestId = request.getHeader("X-Request-Id");      // có sẵn từ client/gateway thì dùng lại
if (requestId == null || requestId.isBlank()) requestId = UUID...;
MDC.put("requestId", requestId);
response.setHeader("X-Request-Id", requestId);              // trả lại cho client để báo lỗi kèm id
try {
    chain.doFilter(request, response);
} finally {
    MDC.remove("requestId");                                // thread Tomcat được tái sử dụng
}
```

- `OncePerRequestFilter`: chạy **đúng 1 lần** mỗi request (kể cả khi forward/async dispatch).
- Log pattern `[req=%X{requestId:-}]` → mỗi dòng log biết thuộc request nào.

### 1.2 Hai nửa của bài toán

| Nửa | Ai lo | Quên thì sao |
|---|---|---|
| Đặt + **dọn** MDC trên thread request | `RequestIdFilter` (`finally`) | requestId của request cũ **rò** sang request sau trên cùng thread Tomcat |
| **Truyền** MDC sang thread pool | `MdcTaskDecorator` (`AsyncConfig`) | Log trong `@Async` **mất** `req=` |

Log mẫu (`curl -H "X-Request-Id: demo-42" localhost:8080/api/parallel`):

```
INFO  [io-1] [req=demo-42] AsyncService : calling user-service (600 ms)
INFO  [io-2] [req=demo-42] AsyncService : calling order-service (800 ms)
INFO  [io-3] [req=demo-42] AsyncService : calling payment-service (400 ms)
```

### 1.3 Production

- Thực tế dùng **distributed tracing** (Micrometer Tracing / OpenTelemetry): traceId + spanId, truyền qua header (`traceparent`) giữa các service.
- Context propagation sang thread khác: Micrometer **context-propagation** / `ContextExecutorService`.
- Virtual threads: MDC vẫn là ThreadLocal → vẫn cần truyền khi đổi thread.

---

## 2. Interview English

### Q1. How do you trace one request through the logs?

**Importance:** 🔴 Important

**Key sentence:** *Put a request ID in the MDC at the entry point and print it in every log line.*

**Suggested Answer:**
I add a filter at the **entry point** that reads `X-Request-Id` from the header, or generates one, and puts it into the **MDC**. The log pattern prints it on every line, so I can grep all logs of one request. I also return it in the response header, so a client can report an error with the ID. In a real system I use **distributed tracing** with a traceId that's passed between services in headers.

### Q2. Why must the filter remove the MDC value in `finally`?

**Importance:** 🔴 Important

**Key sentence:** *Tomcat threads are reused, so a leftover value leaks into the next request.*

**Suggested Answer:**
MDC is a **ThreadLocal**, and Tomcat **reuses** its request threads. If I don't remove the value, the next request on that thread may log the **old request's ID**, which makes debugging misleading — and with user data in ThreadLocals it's even a security problem. Putting `remove()` in `finally` guarantees cleanup even if the request throws an exception.

### Q3. The request ID is in the controller logs but missing in `@Async` logs. Why?

**Importance:** 🔴 Important

**Key sentence:** *MDC belongs to the request thread; the pool thread needs a TaskDecorator to copy it.*

**Suggested Answer:**
Because the `@Async` method runs on a **pool thread**, and MDC lives in the request thread's ThreadLocal. The filter only sets it on the request thread. To fix it, I add a **`TaskDecorator`** to the executor that copies the MDC when the task is submitted and clears it afterwards. If I remove the decorator in this demo, the async log lines lose `req=`.

### Q4. Why use `OncePerRequestFilter`?

**Importance:** 🟢 Low

**Key sentence:** *It guarantees the filter runs only once per request, even with forwards or async dispatches.*

**Suggested Answer:**
A normal filter can run **more than once** for one request — for example on an error forward or an async dispatch when the controller returns a `CompletableFuture`. `OncePerRequestFilter` makes sure the logic runs **only once**, so I don't generate a second ID or overwrite the MDC halfway through the request.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | Put the request ID in the MDC at the entry point. |
| 2 | Reuse the incoming ID; generate one only if it's missing. |
| 3 | Always remove MDC values in finally. |
| 4 | MDC doesn't cross threads without a TaskDecorator. |
| 5 | In production, use distributed tracing. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| MDC (Mapped Diagnostic Context) | map context gắn vào log |
| entry point | điểm vào của request |
| correlation ID / request ID | mã nối các dòng log |
| distributed tracing | theo vết xuyên nhiều service |
| leak | rò rỉ |
| grep the logs | lọc log |

## 5. Self-test

1. Filter lấy requestId từ đâu? Vì sao trả lại header cho client?
2. Quên `MDC.remove()` thì chuyện gì xảy ra?
3. Vì sao log `@Async` mất requestId? Sửa ở đâu?
4. `OncePerRequestFilter` khác filter thường ở điểm nào?
5. Production dùng gì thay cho requestId tự sinh?
