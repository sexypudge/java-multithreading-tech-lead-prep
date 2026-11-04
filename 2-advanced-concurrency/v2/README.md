# 2-advanced-concurrency

Nối tiếp phần nền tảng (thread lifecycle, race condition, `synchronized`, `volatile`/JMM, `wait/notify`, deadlock).
Phần này là các công cụ concurrency "dùng hàng ngày" và cách chẩn đoán sự cố.

## Bản đồ theo lộ trình

| Lộ trình | File | Việc cần làm được sau khi học |
|---|---|---|
| **P1-7** ExecutorService, ThreadPoolExecutor | `ExecutorServiceDemo` | Nói đúng thứ tự core → queue → max → reject; vì sao tránh `Executors.newXxx`; chọn kích thước pool; `shutdown` đúng cách |
| **P1-8** Callable, Future, CompletableFuture | `CompletableFutureDemo` | 4 bẫy: pool mặc định, nuốt exception, mất context, chặn thread trong pool |
| **P1-9** AtomicInteger & CAS | `AtomicVariablesDemo` | Giải thích `volatile count++` sai, tự viết vòng CAS, ABA, `LongAdder` |
| **P1-10** ConcurrentHashMap & BlockingQueue | `ConcurrentCollectionsDemo` | `merge`/`computeIfAbsent`, vì sao CHM vẫn dính check-then-act, producer-consumer + back-pressure |
| **P2-5** CountDownLatch, CyclicBarrier, Semaphore | `SynchronizersDemo` | Phân biệt 3 loại, ví dụ thực tế cho từng loại |
| **P2-6** Virtual Threads | `VirtualThreadsDemo` | Khi nào dùng/không dùng, pinning, không pool virtual thread, giới hạn đồng thời bằng Semaphore |
| **P2-7** Spring `@Async` | `SpringAsyncNotes.md` | Cấu hình pool, mất MDC/SecurityContext/transaction, xử lý exception |
| **P2-8** Chẩn đoán sự cố | `ThreadDiagnosisDemo` | Quy trình khi CPU cao / request treo / pool đầy; đọc thread dump |

`ForkJoinDemo` có sẵn trong project không nằm trong lộ trình ưu tiên, giữ nguyên.

## Cách chạy

Không có `package`, mỗi file là một chương trình độc lập, cần JDK 21+:

```bash
java ExecutorServiceDemo.java          # chạy thẳng không cần javac
java VirtualThreadsDemo.java           # cần Java 21+
```

Trong IntelliJ: nếu project để file trong package, thêm dòng `package ...;` ở đầu mỗi file.
Nếu console hiển thị lỗi font tiếng Việt: thêm `-Dstdout.encoding=UTF-8` vào VM options.

## Thứ tự học gợi ý

1. `AtomicVariablesDemo` — nối thẳng bài `volatile`, ngắn nhất, trả lời được "vì sao `count++` sai".
2. `ExecutorServiceDemo` — phần được hỏi nhiều nhất trong Phase 1.
3. `CompletableFutureDemo` — dùng sau khi chắc pool.
4. `ConcurrentCollectionsDemo`
5. `SynchronizersDemo`
6. `ThreadDiagnosisDemo` — ghép lại mọi thứ đã học: nhìn một sự cố, đoán nguyên nhân từ thread dump.
7. `VirtualThreadsDemo`, `SpringAsyncNotes.md`

## Cách học hiệu quả (giống file JMM)

- **Phá code rồi chạy lại.** Ví dụ: đổi `ArrayBlockingQueue<>(2)` thành `LinkedBlockingQueue<>()` ở Demo 1 của `ExecutorServiceDemo` rồi xem `maxPoolSize` biến mất thế nào; đổi `merge` thành `put(getOrDefault+1)` để thấy mất dữ liệu.
- **Chạy nhiều lần.** Kết quả đa luồng không cố định; không thấy lỗi **không** có nghĩa là code đúng.
- **Một số kết quả phụ thuộc số core**: `HashMap` mất dữ liệu, `LongAdder` nhanh hơn `AtomicLong`, `commonPool` mặc định. Máy ít core có thể không thấy khác biệt.
- **Cuối mỗi file có "INTERVIEW TIPS"**: che đi, tự trả lời thành tiếng 30-60 giây rồi mới đối chiếu.

## Tự kiểm tra (trả lời không nhìn file)

1. Thứ tự `ThreadPoolExecutor` xử lý task mới? Vì sao queue vô hạn làm `maxPoolSize` vô nghĩa?
2. `submit()` và `execute()` khác nhau thế nào khi task ném exception?
3. `supplyAsync()` không truyền executor chạy ở đâu? Hậu quả?
4. `thenApply` và `thenCompose` khác gì?
5. `volatile int count; count++` sai ở đâu? CAS sửa bằng cách nào?
6. CHM thread-safe rồi, vì sao `get` rồi `put` vẫn sai?
7. `CountDownLatch` vs `CyclicBarrier` vs `Semaphore`?
8. Virtual thread có làm code CPU-bound nhanh hơn không? Pinning là gì?
9. Request treo nhưng CPU thấp: bạn làm gì? Đọc gì trong thread dump?
10. Vì sao MDC/SecurityContext mất trong method `@Async` và sửa thế nào?
