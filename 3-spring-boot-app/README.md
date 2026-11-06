# 3. Spring Boot Async Demo

Ứng dụng Spring Boot nhỏ để **chạy và quan sát** những gì hay bị hỏi về concurrency trong Spring:
`@Async` qua proxy, executor có giới hạn + bulkhead, truyền MDC sang thread pool, exception theo kiểu trả về,
timeout + fallback, back-pressure bằng `CallerRunsPolicy`, `@Scheduled`, metric của executor, graceful shutdown.

- **Lý thuyết + checklist review code:** [`NOTES.md`](NOTES.md)
- **Bằng chứng chạy được:** `src/test/java/.../AsyncBehaviorTest.java` (`mvn test`)

## Yêu cầu & chạy

JDK 21, Maven 3.9+, Spring Boot 3.5.

```bash
cd 3-spring-boot-app
mvn test               # 6 test: proxy, self-invocation, MDC, exception, song song, timeout
mvn spring-boot:run    # http://localhost:8080
```

## Cấu trúc

| File | Minh hoạ |
|---|---|
| `AsyncConfig` | 2 executor (bulkhead): `ioExecutor` (Abort → fail fast) và `backgroundExecutor` (CallerRuns → back-pressure); queue có giới hạn; `MdcTaskDecorator`; `AsyncUncaughtExceptionHandler`; shutdown êm |
| `AsyncService` | `@Async` trả `CompletableFuture` / `void`; ném exception; **self-invocation** |
| `AsyncController` | Các endpoint bên dưới |
| `RequestIdFilter` | Đặt `requestId` vào MDC → log in `[req=...]` cả trên thread của pool |
| `ScheduledTasks` | fixedRate / fixedDelay / cron và các bẫy production (1 thread mặc định, nhiều instance) |
| `application.properties` | Graceful shutdown, scheduler pool, actuator, cờ virtual threads |

## Endpoints - chạy thử và quan sát

```bash
# 1. Tuần tự vs song song: ~1800ms vs ~800ms
curl localhost:8080/api/sync
curl localhost:8080/api/parallel

# 2. Self-invocation: viaProxy chạy trên io-*, selfInvocation chạy trên thread Tomcat (http-nio-*)
curl localhost:8080/api/self-invocation

# 3. Timeout + fallback sau 1s (downstream mất 3s). Xem log: thread io-* vẫn bận thêm 2s → timeout KHÔNG huỷ task
curl localhost:8080/api/timeout

# 4. Exception trong CompletableFuture được xử lý bằng exceptionally
curl localhost:8080/api/error

# 5. Fire-and-forget (void): trả về ngay; "bob-fail" chỉ xuất hiện trong LOG qua AsyncUncaughtExceptionHandler
curl -X POST localhost:8080/api/notify -H "Content-Type: application/json" -d '["alice","bob-fail","carol"]'

# 6. Back-pressure: backgroundExecutor (core 2, max 4, queue 10) nhận 30 task 500ms
#    → một phần task chạy trên thread Tomcat (CallerRunsPolicy), request chậm lại thay vì OOM
curl "localhost:8080/api/saturate?tasks=30"

# 7. Chỉ số pool (gọi trong lúc 6 đang chạy để thấy active/queue tăng)
curl localhost:8080/api/pools

# 8. Theo dõi một request qua nhiều thread: tự đặt requestId rồi grep log
curl -H "X-Request-Id: demo-42" localhost:8080/api/parallel
```

Log mẫu của lệnh 8 - cùng `req=demo-42` trên các thread khác nhau nhờ `MdcTaskDecorator`:

```
INFO  [io-1           ] [req=demo-42] AsyncService : calling user-service (600 ms)
INFO  [io-2           ] [req=demo-42] AsyncService : calling order-service (800 ms)
INFO  [io-3           ] [req=demo-42] AsyncService : calling payment-service (400 ms)
```

Thử bỏ `executor.setTaskDecorator(...)` trong `AsyncConfig` → log async mất `req=`.

## Actuator

```bash
curl localhost:8080/actuator/metrics/executor.active?tag=name:ioExecutor
curl localhost:8080/actuator/metrics/executor.queued?tag=name:backgroundExecutor
curl localhost:8080/actuator/metrics/executor.pool.size?tag=name:ioExecutor
curl localhost:8080/actuator/threaddump        # jstack qua HTTP
curl localhost:8080/actuator/health
```

Spring Boot tự gắn metric `executor.*` cho mọi bean `ThreadPoolTaskExecutor` (tag `name` = tên bean).
Đây là những số liệu cần đưa lên dashboard và alert (xem `4-interview-practice/TECH_LEAD_SCENARIOS.md`, S9).

## Thử nghiệm thêm

- **Virtual threads:** bỏ comment `spring.threads.virtual.enabled=true` → thread Tomcat thành virtual (`/api/self-invocation` sẽ thấy `controllerThread` dạng `tomcat-handler-N`). Executor tự định nghĩa vẫn là platform pool có giới hạn.
- **Scheduler 1 thread:** đặt `spring.task.scheduling.pool.size=1` → `cleanup` (3s) làm trễ `heartbeat`.
- **Queue vô hạn:** đặt `setQueueCapacity(Integer.MAX_VALUE)` cho `backgroundExecutor` rồi gọi `/api/saturate?tasks=200` → không còn CallerRuns, `maxPoolSize` không bao giờ được dùng, queue phình.
- **Graceful shutdown:** gọi `/api/timeout` rồi Ctrl+C ngay → request đang chạy vẫn được trả lời trước khi app dừng.

## Câu hỏi phỏng vấn được minh hoạ

| Câu hỏi | Xem |
|---|---|
| `@Async` hoạt động thế nào, vì sao gọi trong cùng class không async? | `/api/self-invocation`, `AsyncBehaviorTest` |
| Pool mặc định của `@Async` là gì, cấu hình thế nào? | `AsyncConfig`, `NOTES.md` §2 |
| Exception của `@Async` đi đâu? | `/api/error`, `/api/notify`, `getAsyncUncaughtExceptionHandler` |
| Vì sao log mất traceId trong `@Async`? | `MdcTaskDecorator`, `RequestIdFilter` |
| `@Transactional` có sang `@Async` không? | `NOTES.md` §3 |
| fixedRate vs fixedDelay, chạy nhiều instance thì sao? | `ScheduledTasks` |
| Rejection policy nào cho trường hợp nào? | `ioExecutor` (Abort) vs `backgroundExecutor` (CallerRuns), `/api/saturate` |
| Theo dõi thread pool trong production? | `/api/pools`, `/actuator/metrics/executor.*` |
