# 2. Advanced Concurrency

Nối tiếp phần nền tảng (thread lifecycle, race condition, `synchronized`, `volatile`/JMM, `wait/notify`, deadlock).
Đây là các công cụ concurrency **dùng hàng ngày trong production** và cách chẩn đoán sự cố.

## Bản đồ file

| Priority | File | Việc cần làm được sau khi học |
|---|---|---|
| **P0** | `ExecutorServiceDemo` | Thứ tự core → queue → max → reject; vì sao tránh `Executors.newXxx`; rejection policy; sizing; `execute` vs `submit`; shutdown; `invokeAll/Any`; `ScheduledExecutorService` (fixedRate vs fixedDelay, exception huỷ lịch) |
| **P0** | `CompletableFutureDemo` | `thenApply/Compose/Combine`, `allOf/anyOf`, timeout; 4 bẫy: commonPool, nuốt exception, mất context, starvation khi `join()` trong pool |
| **P0** | `AtomicVariablesDemo` | `volatile count++` sai, tự viết vòng CAS, `LongAdder`, ABA |
| **P0** | `ConcurrentCollectionsDemo` | `HashMap` dùng chung **treo** (tái hiện trên Java 21), `merge`/`computeIfAbsent`, check-then-act trên CHM, `BlockingQueue` + back-pressure, `CopyOnWriteArrayList` |
| **P0** | `SynchronizersDemo` | `CountDownLatch`, `CyclicBarrier`, `Semaphore` (+ `Phaser` để so sánh) |
| **P0** | `VirtualThreadsDemo` | Throughput I/O, API, pinning, không pool virtual thread, giới hạn đồng thời bằng `Semaphore` |
| **P0** | `ThreadDiagnosisDemo` | CPU cao, pool starvation, lock contention, deadlock - đọc "thread dump" bằng `ThreadMXBean` |
| P1 | `ForkJoinDemo` | Work stealing, threshold, parallel stream khi nào có lợi/hại, bẫy blocking trong commonPool |

Spring `@Async` (cấu hình pool, MDC/SecurityContext/transaction, exception) nằm ở `3-spring-boot-app/` (`NOTES.md` + code chạy được).

## Thứ tự học gợi ý

1. `AtomicVariablesDemo` — nối thẳng bài `volatile`, ngắn nhất.
2. `ExecutorServiceDemo` — phần được hỏi nhiều nhất. Đọc kèm [`01_ExecutorService.md`](01_ExecutorService.md) (10 điểm khó trong production + checklist review code).
3. `CompletableFutureDemo` — học sau khi chắc pool.
4. `ConcurrentCollectionsDemo`
5. `SynchronizersDemo`
6. `VirtualThreadsDemo`
7. `ThreadDiagnosisDemo` — ghép mọi thứ: nhìn sự cố, đoán nguyên nhân từ thread dump.
8. `ForkJoinDemo` (nếu còn thời gian)

## Cách học hiệu quả

- **Phá code rồi chạy lại.** Đổi `ArrayBlockingQueue<>(2)` thành `LinkedBlockingQueue<>()` ở Demo 1 của `ExecutorServiceDemo` để thấy `maxPoolSize` biến mất; đổi `merge` thành `put(getOrDefault + 1)` để thấy mất dữ liệu; bỏ `try/catch` trong task định kỳ để thấy lịch bị huỷ.
- **Chạy nhiều lần.** Kết quả phụ thuộc số core: `LongAdder` nhanh hơn `AtomicLong`, kích thước commonPool, lỗi `HashMap`.
- **Che phần "INTERVIEW TIPS"** cuối mỗi file, tự trả lời thành tiếng 30-60 giây rồi mới đối chiếu.

## Chạy

Mỗi file là một chương trình độc lập (default package), cần **JDK 21+**:

```bash
cd 2-advanced-concurrency
java ExecutorServiceDemo.java
java VirtualThreadsDemo.java
```

Lỗi font tiếng Việt trên Windows: thêm `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`.

## Tự kiểm tra (không nhìn file)

1. Thứ tự `ThreadPoolExecutor` xử lý task mới? Vì sao queue vô hạn làm `maxPoolSize` vô nghĩa?
2. `submit()` và `execute()` khác nhau thế nào khi task ném exception?
3. `scheduleAtFixedRate` khi task chạy lâu hơn chu kỳ thì sao? Task ném exception thì sao?
4. `supplyAsync()` không truyền executor chạy ở đâu? Hậu quả?
5. `thenApply` và `thenCompose` khác gì? `orTimeout` có dừng task không?
6. `volatile int count; count++` sai ở đâu? CAS sửa bằng cách nào? ABA là gì?
7. CHM thread-safe rồi, vì sao `get` rồi `put` vẫn sai? Vì sao CHM cấm null?
8. `CountDownLatch` vs `CyclicBarrier` vs `Semaphore`?
9. Virtual thread có làm code CPU-bound nhanh hơn không? Pinning là gì? Có nên pool virtual thread?
10. Request treo nhưng CPU thấp: đọc gì trong thread dump?
11. Khi nào parallel stream làm chậm hệ thống?

**Tiếp theo:** `3-spring-boot-app/`, rồi luyện đề ở `4-interview-practice/`.
