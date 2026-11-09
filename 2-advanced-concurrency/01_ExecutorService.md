# ExecutorService / ThreadPoolExecutor: 10 điểm khó trong production

> Gọi API thì dễ: `submit()`, `get()`, `shutdown()`.
> Cái khó là **cấu hình đúng** và **biết pool sẽ làm gì khi có sự cố**. Phỏng vấn Tech Lead chủ yếu hỏi phần này.

Demo liên quan: `ExecutorServiceDemo.java` (cùng thư mục), `4-interview-practice/GracefulBatchProcessor.java`,
`3-spring-boot-app/.../AsyncConfig.java`.

---

## 1. Thứ tự nhận task: core → queue → max → reject

Nhiều người nghĩ pool tạo thêm thread tới `max` rồi mới xếp hàng. Thực tế **ngược lại**:

```
thread < core      → tạo thread mới (kể cả khi các core thread khác đang rảnh)
core đã đủ         → đưa vào QUEUE
queue đầy          → tạo thêm thread, tối đa tới max
max cũng đầy       → REJECT (RejectedExecutionHandler)
```

**Hệ quả:** với queue không giới hạn (`new LinkedBlockingQueue<>()`), queue không bao giờ đầy:
- `maxPoolSize` **không bao giờ được dùng**: pool chỉ chạy với `core` thread.
- Task dồn trong queue → latency tăng dần, cuối cùng OOM, mà không có lỗi nào báo trước.

```java
// ✗ max = 50 là vô nghĩa: luôn chỉ có 10 thread
new ThreadPoolExecutor(10, 50, 60, SECONDS, new LinkedBlockingQueue<>());

// ✓ queue có giới hạn → khi queue đầy mới tăng tới 50, sau đó reject
new ThreadPoolExecutor(10, 50, 60, SECONDS, new ArrayBlockingQueue<>(200));
```

Nếu muốn **tăng thread trước rồi mới xếp hàng** (kiểu Tomcat), phải tự viết queue có `offer()` trả `false`
khi còn có thể tạo thread, hoặc dùng `SynchronousQueue` (không có hàng đợi, nên cần `max` đủ lớn).

> **Interview:** "ThreadPoolExecutor fills core threads first, then the queue, and only creates threads up to max
> when the queue is full. So with an unbounded queue, max is never used and the backlog grows until OOM."

---

## 2. `Executors.newXxx()` có cấu hình mặc định nguy hiểm

| Factory | Bên trong | Vấn đề |
|---|---|---|
| `newFixedThreadPool(n)` | `LinkedBlockingQueue` không giới hạn | Backlog vô hạn → OOM, latency tăng âm thầm |
| `newSingleThreadExecutor()` | 1 thread + queue không giới hạn | Như trên |
| `newCachedThreadPool()` | `SynchronousQueue`, `max = Integer.MAX_VALUE` | Tải tăng đột biến → tạo hàng nghìn thread → hết RAM / context switch |
| `newScheduledThreadPool(n)` | `DelayedWorkQueue` không giới hạn | Backlog vô hạn |

**Production:** tự tạo `ThreadPoolExecutor` với
1. queue **có giới hạn**,
2. `ThreadFactory` **đặt tên thread**,
3. rejection policy **chọn có chủ ý**.

```java
AtomicInteger seq = new AtomicInteger();
ThreadFactory tf = r -> {
    Thread t = new Thread(r, "report-worker-" + seq.incrementAndGet());
    t.setUncaughtExceptionHandler((th, ex) -> log.error("Uncaught in {}", th.getName(), ex));
    return t;
};
ThreadPoolExecutor pool = new ThreadPoolExecutor(
        8, 16, 60, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(500),
        tf,
        new ThreadPoolExecutor.AbortPolicy());
```

(Ngoại lệ: virtual thread `Executors.newVirtualThreadPerTaskExecutor()` không cần giới hạn số thread,
nhưng vẫn phải giới hạn **tài nguyên phía sau** bằng `Semaphore`. Xem `VirtualThreadsDemo.java`.)

---

## 3. Chọn kích thước pool

Công thức kinh điển (Goetz):

```
CPU-bound :  threads ≈ N_cpu (hoặc N_cpu + 1)
I/O-bound :  threads ≈ N_cpu × (1 + wait_time / compute_time)
```

Đó chỉ là **điểm xuất phát**. Trong thực tế, giới hạn thường nằm ở chỗ khác:
- **DB connection pool:** 50 thread dùng chung 10 connection → 40 thread chỉ ngồi chờ connection.
- **Service phía sau:** rate limit, hoặc chỉ chịu được N request song song.
- **Lock / synchronized** dùng chung → thêm thread chỉ thêm tranh chấp.
- Bộ nhớ: mỗi platform thread có stack (mặc định ~1 MB, cấp phát dần).

Little's Law giúp ước lượng: `concurrency = throughput × latency`.
Ví dụ 200 req/s × 0,1 s = cần khoảng 20 task chạy đồng thời.

**Cách đúng:** đo bằng load test và metric (active, queue size, latency p99), rồi điều chỉnh. Không đoán.

> **Interview:** "I start from the formula, but the real limit is usually the DB pool or the downstream.
> There's no point having 50 threads if they share 10 connections. I size it with load tests and watch queue depth."

---

## 4. Rejection policy: mỗi lựa chọn có giá phải trả

| Policy | Hành vi | Khi nào dùng | Cái giá |
|---|---|---|---|
| `AbortPolicy` (mặc định) | Ném `RejectedExecutionException` | Request online: fail fast, trả 503/429 | Caller phải bắt và xử lý |
| `CallerRunsPolicy` | Thread gọi `execute()` tự chạy task | Batch/background: tạo **back-pressure** tự nhiên | Thread của caller (ví dụ Tomcat) bị chiếm → request chậm lại |
| `DiscardPolicy` | Bỏ task, không báo gì | Gần như không bao giờ | Mất dữ liệu không có log |
| `DiscardOldestPolicy` | Bỏ task cũ nhất trong queue, thử lại | Dữ liệu chỉ cần bản mới nhất (metric, giá...) | Mất dữ liệu không có log |

**Bẫy của `CallerRunsPolicy`:** sau khi pool đã `shutdown()`, nó **lặng lẽ bỏ task**, không chạy và không ném lỗi:

```java
public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
    if (!e.isShutdown()) r.run();   // đã shutdown → không làm gì cả
}
```

→ Code submit phải tự kiểm tra `isShutdown()` (xem `GracefulBatchProcessor`), hoặc dùng custom handler có log/metric.

**Tốt nhất:** custom handler **ghi log + tăng metric `rejected`** rồi mới áp dụng policy, để biết khi nào pool quá tải.

---

## 5. Exception biến mất không dấu vết

```java
pool.submit(() -> { throw new RuntimeException("boom"); });  // ✗ không log, không gì cả
pool.execute(() -> { throw new RuntimeException("boom"); }); // in stack trace, thread chết và được tạo thread mới
```

- `submit()` bọc task trong `FutureTask` → exception được **giữ trong `Future`**. Nếu không ai gọi `get()`, lỗi mất luôn.
- `execute()` → exception lên `UncaughtExceptionHandler` → worker thread chết, pool tạo thread mới thay thế (tốn kém nếu lỗi liên tục).

**`ScheduledExecutorService` còn tệ hơn:** một lần ném exception là **lịch chạy bị huỷ vĩnh viễn**, không có log:

```java
scheduler.scheduleAtFixedRate(() -> {
    try {
        doWork();
    } catch (Exception e) {          // ✓ bắt hết, nếu không job sẽ dừng hẳn sau lỗi đầu tiên
        log.error("Job failed", e);
    }
}, 0, 10, TimeUnit.SECONDS);
```

**Cách xử lý:**
- Luôn `get()` / `join()` hoặc gắn `exceptionally` / `whenComplete` cho kết quả.
- Fire-and-forget → bọc `try/catch` + log bên trong task.
- Hoặc override `ThreadPoolExecutor.afterExecute(Runnable r, Throwable t)` để log tập trung
  (với `submit()`, `t` là `null`; phải lấy exception từ `Future`).

---

## 6. Thread starvation deadlock

```java
ExecutorService pool = Executors.newFixedThreadPool(4);

pool.submit(() -> {
    Future<String> child = pool.submit(() -> loadDetail()); // ✗ cùng pool
    return child.get();                                     // chờ task con
});
```

Khi có ≥ 4 task cha chạy cùng lúc, cả 4 thread đều đang chờ task con, còn task con nằm trong queue và không có thread nào chạy nó.
→ **Hệ thống treo mà không có lock nào cả.** `ThreadMXBean.findDeadlockedThreads()` **không phát hiện được**:
thread dump chỉ thấy tất cả đều `WAITING` ở `FutureTask.get`.

Hay gặp với:
- `CompletableFuture.supplyAsync(...)` dùng chung `ForkJoinPool.commonPool()` rồi `join()` bên trong.
- Spring `@Async` gọi một `@Async` khác trên cùng executor rồi chờ kết quả.

**Cách xử lý:**
- Tách pool cho task cha và task con.
- Không chặn (`get()`) bên trong task. Dùng `thenCompose` / `thenCombine` để nối thay vì chờ.
- Với tác vụ chia nhỏ đệ quy → `ForkJoinPool` (work-stealing xử lý được `join()`).

---

## 7. Timeout không huỷ task

```java
Future<Result> f = pool.submit(() -> callSlowService());
try {
    f.get(1, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    f.cancel(true);   // chỉ gửi interrupt
}
```

- `get(timeout)` chỉ làm **người chờ** dừng chờ. Task **vẫn chạy và vẫn chiếm thread**.
- `cancel(true)` chỉ **gửi interrupt**. Task chỉ dừng nếu code tôn trọng interrupt
  (`sleep`, `wait`, `BlockingQueue.take`, NIO interruptible channel...). Socket I/O cổ điển, JDBC, vòng lặp tính toán
  không kiểm tra `isInterrupted()` sẽ **chạy tiếp**.
- `CompletableFuture.orTimeout()` / `completeOnTimeout()` **không huỷ** task bên dưới
  (`cancel(true)` trên `CompletableFuture` cũng không interrupt).

→ Nếu timeout liên tục, pool đầy dần bởi các task "zombie" mà không ai còn chờ kết quả.

**Cách xử lý:** đặt timeout **ở phía client**, nơi thực sự giải phóng tài nguyên:
- HTTP client: connect timeout + read/response timeout.
- JDBC: `setQueryTimeout`, timeout của connection pool.
- Kết hợp circuit breaker (Resilience4j) để ngừng gọi service đang chết.

Xem `4-interview-practice/BoundedFanOut.java` (client timeout + giới hạn đồng thời + deadline).

---

## 8. Shutdown

Quên `shutdown()` → thread non-daemon giữ **JVM sống mãi** (app không tắt, test treo).

| Method | Hành vi |
|---|---|
| `shutdown()` | Không nhận task mới; task đang chạy **và trong queue** vẫn chạy hết |
| `shutdownNow()` | Không nhận task mới; **interrupt** các thread đang chạy; trả về `List<Runnable>` task chưa chạy |
| `awaitTermination(t)` | Chờ tối đa `t` cho tới khi mọi task xong |
| `close()` (Java 19+) | `shutdown()` + chờ (không giới hạn); dùng được với try-with-resources |

**Mẫu chuẩn** (theo Javadoc `ExecutorService`):

```java
void shutdownAndAwait(ExecutorService pool) {
    pool.shutdown();                                       // 1. ngừng nhận task mới
    try {
        if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
            List<Runnable> dropped = pool.shutdownNow();   // 2. hết thời gian → interrupt
            log.warn("Dropped {} queued tasks", dropped.size());
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                log.error("Pool did not terminate");       // 3. task không tôn trọng interrupt
            }
        }
    } catch (InterruptedException e) {
        pool.shutdownNow();
        Thread.currentThread().interrupt();                // giữ lại trạng thái interrupt
    }
}
```

Lưu ý:
- Task **phải tôn trọng interrupt** thì `shutdownNow()` mới có tác dụng.
- Task bị bỏ (`dropped`) cần được xử lý: ghi lại để chạy lại, hoặc đảm bảo xử lý **idempotent**.
- Trong Spring: `setWaitForTasksToCompleteOnShutdown(true)` + `setAwaitTerminationSeconds(...)`, kết hợp
  `server.shutdown=graceful`. Kubernetes: `terminationGracePeriodSeconds` phải lớn hơn tổng thời gian chờ.

---

## 9. Thread được tái sử dụng: context bị mất và bị rò

Thread trong pool **sống lâu và chạy nhiều task**, nên có hai vấn đề ngược nhau:

**a) Context không đi theo task sang thread khác:**
`ThreadLocal`, MDC (traceId/requestId), Spring `SecurityContext`, `LocaleContext`, transaction đều gắn với thread.
Task chạy trên thread của pool sẽ **không thấy** chúng → log mất traceId, mất thông tin user.

→ Bọc task bằng decorator: **chụp** context ở thread gửi, **đặt lại** ở thread chạy, **dọn dẹp** khi xong.

```java
Runnable wrap(Runnable task) {
    Map<String, String> ctx = MDC.getCopyOfContextMap();   // chụp ở thread gửi
    return () -> {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        if (ctx != null) MDC.setContextMap(ctx); else MDC.clear();
        try {
            task.run();
        } finally {                                         // luôn dọn dẹp
            if (previous != null) MDC.setContextMap(previous); else MDC.clear();
        }
    };
}
```

Xem `MdcTaskDecorator` trong `3-spring-boot-app/.../AsyncConfig.java`.
Có sẵn: `DelegatingSecurityContextExecutor` (Spring Security), Micrometer context-propagation.

**b) ThreadLocal dính sang task sau:**
Nếu task đặt `ThreadLocal` mà không `remove()`, task **kế tiếp** trên cùng thread sẽ thấy giá trị cũ
→ **rò dữ liệu giữa các user**, và rò bộ nhớ (object không được GC, ClassLoader leak khi redeploy).

```java
try {
    USER.set(user);
    process();
} finally {
    USER.remove();   // ✓ bắt buộc với thread pool
}
```

Xem `1-core-fundamentals/ThreadLocalDemo.java`.

---

## 10. Observability và bulkhead

**Đặt tên thread:** thread tên `pool-3-thread-7` thì thread dump gần như vô dụng.
Tên như `payment-client-7` cho biết ngay pool nào đang kẹt.

**Metric cần theo dõi** (Micrometer `ExecutorServiceMetrics`, Spring Boot tự gắn `executor.*` cho `ThreadPoolTaskExecutor`):

| Metric | Ý nghĩa | Alert khi |
|---|---|---|
| `executor.active` | Số thread đang chạy task | Liên tục = `max` |
| `executor.queued` | Số task trong queue | > 70–80% capacity |
| `executor.pool.size` | Số thread hiện có | Luôn = `max` |
| `executor.completed` | Throughput | Giảm đột ngột |
| số task bị reject (tự đếm trong handler) | Quá tải | > 0 |
| thời gian chờ trong queue / thời gian chạy | Latency | p99 tăng |

**Bulkhead (cách ly lỗi):** một pool dùng chung cho mọi việc thì một service phía sau bị chậm sẽ chiếm hết thread,
và mọi chức năng khác chậm theo. Tách pool theo **loại việc** hoặc **theo service phía sau**:

```
ioExecutor         → gọi HTTP service phía sau   (Abort: fail fast)
backgroundExecutor → gửi email, sinh báo cáo      (CallerRuns: back-pressure)
paymentExecutor    → riêng cho payment-service    (lỗi ở đây không ảnh hưởng phần còn lại)
```

Đồng thời: tránh chạy việc blocking trên `ForkJoinPool.commonPool()` (mặc định của `CompletableFuture.supplyAsync`
và parallel stream), vì pool này dùng chung cho cả JVM và chỉ có `N_cpu - 1` thread.

---

## Tóm tắt để trả lời phỏng vấn

> "The API is simple; the hard part is the operational behaviour. ThreadPoolExecutor fills core threads, then the
> queue, then grows to max, so I always use a bounded queue and an explicit rejection policy: Abort for online
> requests, CallerRuns for background back-pressure. I avoid the `Executors` factories in production because of
> their unbounded queues or thread counts. Exceptions from `submit()` disappear unless someone reads the Future, and a
> scheduled task stops forever after one exception. Timeouts on a Future don't cancel the work, so I set timeouts on
> the client. Tasks must never block waiting on the same pool, or it starves. Context like MDC must be propagated and
> ThreadLocals cleaned up. On shutdown I use shutdown, awaitTermination, then shutdownNow. And I name threads, export
> queue depth, active count and rejections as metrics, and use separate pools as bulkheads."

## Checklist khi review code dùng thread pool

- [ ] Có dùng `Executors.newFixedThreadPool` / `newCachedThreadPool` không? Queue có giới hạn không?
- [ ] Rejection policy là gì, có log/metric khi reject không?
- [ ] Thread có tên rõ ràng không?
- [ ] Kết quả của `submit()` có được đọc không? Task fire-and-forget có `try/catch` + log không?
- [ ] Task scheduled có bắt mọi exception không?
- [ ] Có task nào chờ (`get`/`join`) task khác trên **cùng pool** không?
- [ ] Timeout có đặt ở client (HTTP/JDBC) không, hay chỉ ở `Future.get`?
- [ ] Code có tôn trọng interrupt không (không nuốt `InterruptedException`)?
- [ ] Pool có được shutdown đúng cách không (bean lifecycle / `close()`)?
- [ ] MDC / SecurityContext có được truyền không? `ThreadLocal` có `remove()` trong `finally` không?
- [ ] Kích thước pool có khớp DB pool / giới hạn của service phía sau không?
- [ ] Có metric `active`, `queued`, `rejected` và alert không?
