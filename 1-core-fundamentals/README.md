# 1. Core Fundamentals - Kiến thức nền tảng

> Module này là **nền móng** của toàn bộ Java Concurrency. Nếu phần này chưa chắc, `ExecutorService`, `CompletableFuture`, `@Async`... sẽ chỉ là học thuộc API.

## 🎯 Mục tiêu sau khi học xong

Tự trả lời được (không nhìn code):

1. Vì sao `count++` không thread-safe? Bytecode gồm những bước nào?
2. `synchronized` đảm bảo những gì? (mutual exclusion **và** visibility)
3. `volatile` đảm bảo gì, **không** đảm bảo gì? Vì sao double-checked locking cần `volatile`?
4. Vì sao `wait()` phải nằm trong `synchronized` và trong vòng `while`?
5. 6 trạng thái của thread, chuyển trạng thái khi nào?
6. 4 điều kiện deadlock; cách detect và phòng tránh?
7. Race condition khác data race thế nào?

## 🗺️ Lộ trình (10-11 ngày, rút còn 7 nếu đã quen Java)

Học theo mạch: *tạo thread → thấy vấn đề → giải pháp → vấn đề nâng cao*.

| Ngày | File | Trọng tâm |
|------|------|-----------|
| 1 | `ThreadCreationExamples` | Thread/Runnable/Callable, `start()` vs `run()`, start 2 lần |
| 2 | `ThreadLifecycleDemo` + `ThreadBasicsDemo` | 6 state thật sự, `join/sleep/yield`, daemon, uncaught exception |
| 3 | `RaceConditionDemo` | **Thấy lỗi trước**: read-modify-write, check-then-act, compound op |
| 4 | `SynchronizationDemo` | method / block / static, lock nào chặn lock nào, reentrant |
| 5 | `VolatileDemo` + `JavaMemoryModelDemo` | Visibility, ordering, **happens-before**, DCL, safe publication |
| 6 | `WaitNotifyDemo` | Producer-Consumer, `while` vs `if`, `notify` vs `notifyAll` |
| 7 | `ThreadInterruptionDemo` | Dừng thread an toàn, xử lý `InterruptedException` |
| 8 | `ExplicitLockDemo` | `ReentrantLock`, `Condition`, `ReadWriteLock`, `StampedLock` |
| 9 | `DeadlockDemo` + `LivelockStarvationDemo` | Tạo, phát hiện, sửa deadlock; livelock; starvation |
| 10 | `ThreadLocalDemo` + `ImmutabilityDemo` | Thread confinement, rò rỉ ThreadLocal trong pool, immutable |
| 11 | Ôn tập + bài tập cuối module | Xem bên dưới |

## 📚 Danh sách file

| File | Nội dung chính | Câu hỏi phỏng vấn |
|---|---|---|
| `ThreadCreationExamples` | 3 cách tạo task/thread, executor, bẫy `run()` và `start()` 2 lần | Runnable hay Thread? Runnable hay Callable? |
| `ThreadLifecycleDemo` | Quan sát tất định 6 state bằng latch; sơ đồ chuyển state | `sleep()` ở state nào? BLOCKED vs WAITING? |
| `ThreadBasicsDemo` | `join(timeout)`, daemon, `UncaughtExceptionHandler`, `yield()` | Daemon thread? Exception ở thread con đi đâu? |
| `RaceConditionDemo` | Lost update, 2 singleton, rút tiền âm - tái hiện ổn định bằng start gate | Ví dụ race condition thực tế? Race vs data race? |
| `SynchronizationDemo` | 3 dạng `synchronized`, đo ai chặn ai, `synchronized(null)`, exception nhả lock | Method vs block? 2 instance có chặn nhau không? |
| `VolatileDemo` | Visibility bug thật (worker không dừng), `volatile` không atomic | volatile vs synchronized? |
| `JavaMemoryModelDemo` | Quy tắc HB, safe publication, litmus test reordering, DCL/Holder/Enum, `final` | Happens-before? Vì sao DCL cần volatile? |
| `WaitNotifyDemo` | Bounded buffer 2P/2C, bug `if` tất định, `notify` bỏ sót thread | Vì sao `while`? notify vs notifyAll? |
| `ThreadInterruptionDemo` | Cờ interrupt, nuốt vs khôi phục, `synchronized` không interruptible | Dừng thread đúng cách? |
| `ExplicitLockDemo` | `tryLock`, `Condition`, `ReadWriteLock`, `StampedLock` optimistic read | Khi nào dùng ReentrantLock? |
| `DeadlockDemo` | Deadlock + `ThreadMXBean` phát hiện, lock ordering, `tryLock` + backoff | Detect & fix deadlock trong production? |
| `LivelockStarvationDemo` | Livelock + random backoff, fair vs unfair lock | Deadlock vs livelock vs starvation? |
| `ThreadLocalDemo` | `SimpleDateFormat`, request context, rò rỉ trong pool, `InheritableThreadLocal` | ThreadLocal gây leak thế nào? |
| `ImmutabilityDemo` | Immutable class, `record`, defensive copy, view vs copy, snapshot + volatile | `final` có làm object immutable không? |

**Ghi chú ôn tập (Interview English)** — mỗi class một file, cùng format: kiến thức (tiếng Việt) → câu hỏi + *Key sentence* + câu trả lời 30–45 giây → 5 câu cần nhớ → từ vựng → self-test. Mỗi câu hỏi có **mức độ quan trọng**: 🔴 Important (chắc chắn bị hỏi) · 🟡 Medium (hay gặp) · 🟢 Low (biết để không bị bất ngờ).

| # | File | Class |
|---|---|---|
| 01 | `01-ThreadCreation.md` | `ThreadCreationExamples` |
| 02 | `02-ThreadLifecycle.md` | `ThreadLifecycleDemo` |
| 03 | `03-ThreadBasics.md` | `ThreadBasicsDemo` |
| 04 | `04-RaceCondition.md` | `RaceConditionDemo` |
| 05 | `05-Synchronization.md` | `SynchronizationDemo` |
| 06 | `06-Volatile.md` | `VolatileDemo` |
| 07 | `07-JavaMemoryModel.md` | `JavaMemoryModelDemo` |
| 08 | `08-WaitNotify.md` | `WaitNotifyDemo` |
| 09 | `09-ThreadInterruption.md` | `ThreadInterruptionDemo` |
| 10 | `10-ExplicitLock.md` | `ExplicitLockDemo` |
| 11 | `11-Deadlock.md` | `DeadlockDemo` |
| 12 | `12-LivelockStarvation.md` | `LivelockStarvationDemo` |
| 13 | `13-ThreadLocal.md` | `ThreadLocalDemo` |
| 14 | `14-Immutability.md` | `ImmutabilityDemo` |

`memoize.md`: câu hỏi đã luyện (ghi chú cá nhân).

## 🧠 Phương pháp học mỗi file (5 bước)

1. **Đọc comment đầu file**, tự trả lời câu interview trước khi đọc code.
2. **Đoán output** trước khi chạy.
3. **Chạy nhiều lần** - kết quả đa luồng không cố định; không thấy lỗi ≠ code đúng.
4. **Phá code**: bỏ `synchronized`/`volatile`, đổi `while` → `if`, `notifyAll` → `notify`, đổi thứ tự lock.
5. **Giải thích lại** bằng lời (tiếng Anh) trong 60-90 giây.

Công cụ: `jps` + `jcmd <pid> Thread.print` (hoặc `jstack`), `jconsole`/VisualVM, `javap -c` (xem bytecode của `count++`).

## ⚠️ Lưu ý khi chạy

Mọi demo đều **tự kết thúc** (thread có thể kẹt vĩnh viễn được đặt daemon). Một số kết quả phụ thuộc JIT/CPU:

| File | Lưu ý |
|------|-------|
| `VolatileDemo` (Demo 1), `JavaMemoryModelDemo` (Demo 3) | Có máy không tái hiện được lỗi visibility/reordering. Không thấy lỗi ≠ code đúng. |
| `LivelockStarvationDemo` | Chênh lệch fair/unfair khác nhau theo máy/OS. |
| `ThreadLocalDemo` | Số lỗi `SimpleDateFormat` dùng chung thay đổi mỗi lần (có thể 0 nhưng vẫn sai). |
| `DeadlockDemo` | Trong lúc chạy Demo 1, thử `jcmd <pid> Thread.print` để thấy "Found one Java-level deadlock". |

## 🔑 Key Concepts

| Tính chất | Ý nghĩa | Công cụ |
|-----------|---------|---------|
| **Atomicity** | Thao tác không bị chia cắt | `synchronized`, `Lock`, `Atomic*` |
| **Visibility** | Thay đổi của thread này thấy được ở thread khác | `volatile`, `synchronized`, `final`, start/join |
| **Ordering** | Không bị reorder gây sai kết quả | `volatile`, `synchronized`, happens-before |

> `volatile` chỉ cho **visibility + ordering**. `synchronized` cho **cả ba** (trên cùng một monitor).

**Thứ tự ưu tiên khi thiết kế thread-safe:** immutable → thread confinement → concurrent utilities / atomic → lock.

**Liveness:** deadlock (chờ nhau mãi), livelock (chạy mãi không tiến), starvation (một thread không bao giờ tới lượt).

## 💡 Best Practices

1. Giảm shared mutable state (immutable trước, lock sau)
2. Lock trên object `private final` riêng, không lock `this` public, `String` literal, `Integer`
3. Critical section ngắn; **không** sleep/I/O/gọi code lạ khi đang giữ lock
4. Tránh nested lock; nếu bắt buộc, định nghĩa lock ordering
5. `wait()` luôn trong `while`; ưu tiên `notifyAll()` (hoặc tốt hơn: `BlockingQueue`, `Condition`)
6. `volatile` cho flag; `Atomic*`/`LongAdder` cho counter
7. Bắt `InterruptedException`: ném tiếp hoặc `Thread.currentThread().interrupt()`. **Không nuốt.**
8. Ghi rõ thread-safety của class (Javadoc: lock nào bảo vệ field nào)

## 📝 Bài tập cuối module (không nhìn code mẫu)

1. Counter thread-safe 3 cách (`synchronized`, `AtomicInteger`, `ReentrantLock`) + test có start gate → so với `4-interview-practice/ConcurrencyTestingDemo.java`
2. Worker dừng bằng `volatile boolean`, rồi viết lại bằng `interrupt()`
3. Producer-Consumer `wait/notifyAll`, 2P + 2C, bounded buffer (vì sao `notify()` có thể treo?)
4. In chẵn/lẻ xen kẽ bằng 2 thread → so với `4-interview-practice/EvenOddPrinter.java`
5. Cố ý tạo deadlock, chụp thread dump, chỉ ra "Found one Java-level deadlock", rồi sửa
6. Singleton 3 phiên bản (DCL, holder, enum), giải thích vì sao DCL cần `volatile`

## ✅ Checklist tự đánh giá

- [ ] Vẽ được sơ đồ 6 thread state và hàm nào gây chuyển state
- [ ] Giải thích `count++` gồm 3 bước và interleaving gây mất update
- [ ] `synchronized` method / block / static lock trên object nào; ai chặn ai
- [ ] `volatile` giải quyết gì, không giải quyết gì (1 ví dụ mỗi bên)
- [ ] Viết Producer-Consumer `wait/notifyAll` không lỗi
- [ ] 4 điều kiện deadlock và ≥ 3 cách phòng tránh; đọc được thread dump
- [ ] Phân biệt deadlock, livelock, starvation
- [ ] Nêu ≥ 4 quy tắc happens-before; giải thích vì sao DCL cần `volatile`
- [ ] Dừng thread bằng interrupt; vì sao không được nuốt `InterruptedException`
- [ ] So sánh `synchronized` / `ReentrantLock` / `ReadWriteLock` / `StampedLock`
- [ ] Rò rỉ `ThreadLocal` trong thread pool và cách khắc phục
- [ ] 5 quy tắc immutable class; view vs copy của collection

## 🚀 Chạy

```bash
cd 1-core-fundamentals
java ThreadCreationExamples.java          # JDK 11+ chạy thẳng file nguồn, không cần javac
# hoặc
javac -encoding UTF-8 -d out *.java && java -cp out RaceConditionDemo
```

> Yêu cầu: JDK 21 (khuyến nghị). Lỗi font tiếng Việt trên Windows: thêm `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`.

**Tiếp theo:** `2-advanced-concurrency/`.
