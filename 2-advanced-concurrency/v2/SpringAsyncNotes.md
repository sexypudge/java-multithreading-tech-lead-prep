# Spring `@Async`: cấu hình pool, mất context, xử lý exception

> File này là **tài liệu + code mẫu**, không chạy độc lập (cần Spring). Dán vào một project Spring Boot nhỏ để thử.
> Nối tiếp `CompletableFutureDemo` (Demo 7 mất context, Demo 5 exception): `@Async` gặp **đúng các bẫy đó**, chỉ khác là Spring giấu chúng sau annotation.

## 1. `@Async` thực chất là gì?

`@Async` = Spring bọc bean bằng **proxy**. Khi gọi method, proxy **ném phần thân method vào một `Executor`** rồi trả về ngay.

```java
@Service
public class ReportService {

    @Async("reportExecutor")               // chỉ định rõ pool, đừng dựa vào mặc định
    public CompletableFuture<Report> build(long id) {
        Report r = heavyWork(id);           // chạy ở thread của reportExecutor, KHÔNG phải thread của caller
        return CompletableFuture.completedFuture(r);
    }
}
```

Hệ quả vì là **proxy**:

| Sai lầm | Vì sao hỏng |
|---|---|
| Gọi `this.build()` từ method khác **trong cùng class** (self-invocation) | Không đi qua proxy → chạy **đồng bộ** như thường, `@Async` bị bỏ qua |
| Gắn `@Async` lên method `private` / `final` / class `final` | Proxy không override được |
| Quên `@EnableAsync` | Không có gì xảy ra, không báo lỗi |

(`@Transactional` bị đúng các bẫy proxy này, nên một lần hiểu dùng được cả hai.)

## 2. Cấu hình pool (đừng dùng mặc định)

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(100);                                   // CÓ GIỚI HẠN
        ex.setThreadNamePrefix("report-");                          // để đọc thread dump
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.setTaskDecorator(new ContextCopyingDecorator());         // xem mục 3
        ex.setWaitForTasksToCompleteOnShutdown(true);               // shutdown êm
        ex.setAwaitTerminationSeconds(30);
        ex.initialize();
        return ex;
    }
}
```

Đây chính là `ThreadPoolExecutor` bạn đã học ở `ExecutorServiceDemo`: **core → queue → max → reject**. Cùng quy tắc, cùng bẫy.

**Pool mặc định là gì?** Tuỳ cấu hình (hãy kiểm tra đúng phiên bản Spring/Boot của anh):

- Spring thuần, không khai báo gì: `SimpleAsyncTaskExecutor` → **tạo thread mới cho mỗi task, không giới hạn, không tái sử dụng**.
- Spring Boot: tự tạo bean `applicationTaskExecutor` (`ThreadPoolTaskExecutor`), chỉnh qua `spring.task.execution.pool.*`.
  **Queue mặc định không giới hạn** → `max-size` gần như không bao giờ được dùng (đúng bẫy "queue vô hạn" ở `ExecutorServiceDemo`).
- Boot 3.2+ với `spring.threads.virtual.enabled=true`: dùng virtual thread (xem `VirtualThreadsDemo`).

→ Luôn tự khai báo executor có tên, queue có giới hạn, rejection policy rõ ràng, và tách pool theo loại việc (bulkhead).

## 3. Mất MDC / SecurityContext / transaction

Cả ba đều dựa trên **ThreadLocal**, nên **không tự đi theo** sang thread của pool (giống `CompletableFutureDemo` Demo 7).

| Thứ cần giữ | Hậu quả khi mất | Cách xử lý |
|---|---|---|
| **MDC** (traceId, requestId) | Log của task async không có traceId, không truy vết được | `TaskDecorator` (bên dưới) |
| **SecurityContext** | `SecurityContextHolder.getContext().getAuthentication()` = `null` → 401/NPE trong task async | `TaskDecorator`, hoặc bọc executor bằng `DelegatingSecurityContextAsyncTaskExecutor` |
| **Transaction** | Method `@Async` chạy ở thread khác → **KHÔNG nằm trong transaction của caller** | Xem dưới |

```java
public class ContextCopyingDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable task) {
        // CHỤP ở thread GỌI (đang chạy request)
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        SecurityContext sc = SecurityContextHolder.getContext();

        return () -> {
            try {
                // GẮN LẠI ở thread của pool
                if (mdc != null) MDC.setContextMap(mdc);
                SecurityContextHolder.setContext(sc);
                task.run();
            } finally {
                // BẮT BUỘC dọn: thread được tái sử dụng, không dọn thì dữ liệu rò sang task/request kế tiếp
                MDC.clear();
                SecurityContextHolder.clearContext();
            }
        };
    }
}
```

**Transaction và `@Async`** — hai hệ quả hay gặp:

1. Caller `@Transactional` gọi `@Async` method để đọc dữ liệu vừa ghi → async chạy **trước khi caller commit** → **không thấy dữ liệu** (giống vấn đề visibility, chỉ khác là ở tầng DB).
   Cách xử lý: chạy async **sau commit**, vd `@TransactionalEventListener(phase = AFTER_COMMIT)` kết hợp `@Async`, hoặc publish message sau khi commit (outbox).
2. Method `@Async` tự có transaction **riêng** nếu bạn gắn `@Transactional` lên nó. Đừng kỳ vọng rollback cùng caller.

## 4. Xử lý exception

Phụ thuộc kiểu trả về:

| Kiểu trả về | Exception đi đâu |
|---|---|
| `Future` / `CompletableFuture` | Nằm trong future; caller phải `get()`/`join()`/`handle()`, nếu không thì **mất dấu** (đúng bẫy #2) |
| `void` | Caller **không có cách bắt**; Spring chuyển cho `AsyncUncaughtExceptionHandler` |

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("Async method {} lỗi, params={}", method.getName(), Arrays.toString(params), ex);
    }
}
```

Mặc định, handler chỉ **log** lỗi. Với việc quan trọng (gửi email, đồng bộ dữ liệu), cần thêm cơ chế **retry / dead-letter / cảnh báo**, vì "chạy nền và lỗi âm thầm" là kiểu sự cố khó phát hiện nhất.

## 5. Checklist khi review code có `@Async`

- [ ] Có `@EnableAsync` chưa, và method có được gọi **qua bean khác** (không phải `this.`)?
- [ ] Executor được **đặt tên**, queue **có giới hạn**, rejection policy rõ ràng?
- [ ] `ThreadNamePrefix` có ý nghĩa để đọc thread dump?
- [ ] MDC / SecurityContext được truyền qua `TaskDecorator`, và có dọn trong `finally`?
- [ ] Task có cần dữ liệu **chưa commit** từ transaction của caller không?
- [ ] Lỗi được xử lý: `void` → `AsyncUncaughtExceptionHandler`; `CompletableFuture` → có `handle`/`exceptionally`?
- [ ] Có `join()`/`get()` trong chính pool đó không (starvation)?
- [ ] Có cần timeout cho việc gọi bên ngoài trong task không?

## 6. Câu hỏi phỏng vấn

**`@Async` hoạt động thế nào? Vì sao gọi trong cùng class không chạy async?**
Spring dùng proxy để chuyển lời gọi vào `Executor`. Gọi `this.method()` không đi qua proxy nên chạy đồng bộ.

**Pool mặc định của `@Async` là gì, có vấn đề gì?**
Spring thuần: `SimpleAsyncTaskExecutor` (thread mới mỗi task, vô hạn). Spring Boot: `ThreadPoolTaskExecutor` với queue vô hạn. Cả hai đều nên được thay bằng executor tự cấu hình, có giới hạn.

**Vì sao log mất traceId / `SecurityContext` bị null trong method `@Async`?**
Chúng dựa trên ThreadLocal gắn với thread request. Pool dùng thread khác. Dùng `TaskDecorator` để chụp và gắn lại, nhớ dọn trong `finally`.

**Exception trong method `@Async` trả `void` đi đâu?**
Không về caller được. Spring gọi `AsyncUncaughtExceptionHandler` (mặc định chỉ log).

**`@Transactional` có lan sang method `@Async` không?**
Không, transaction gắn với thread. Async method chạy ngoài transaction của caller (hoặc có transaction riêng) và có thể không thấy dữ liệu chưa commit.
