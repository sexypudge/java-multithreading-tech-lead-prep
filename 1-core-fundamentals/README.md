# 1. Core Fundamentals - Kiến thức nền tảng

> Module này là **nền móng** của toàn bộ Java Concurrency. Nếu phần này chưa chắc, các phần `ExecutorService`, `CompletableFuture`, `@Async`... sẽ chỉ là học thuộc API.

---

## 🎯 Mục tiêu sau khi học xong

Bạn phải tự trả lời được (không nhìn code) các câu sau:

1. Vì sao `count++` không thread-safe? Bytecode gồm những bước nào?
2. `synchronized` đảm bảo những gì? (mutual exclusion **và** visibility)
3. `volatile` đảm bảo gì, **không** đảm bảo gì? Vì sao double-checked locking cần `volatile`?
4. Vì sao `wait()` phải nằm trong `synchronized` và phải đặt trong vòng `while`?
5. 6 trạng thái của thread, chuyển trạng thái khi nào?
6. 4 điều kiện của deadlock; cách detect và cách phòng tránh?
7. Race condition khác data race thế nào?

---

## 🗺️ Lộ trình học (đề xuất 7-10 ngày)

Thứ tự **không** phải theo số thứ tự file. Học theo mạch: *tạo thread → thấy vấn đề → học cách giải quyết → học vấn đề nâng cao*.

| Ngày | File | Trọng tâm |
|------|------|-----------|
| 1 | `ThreadCreationExamples.java` | 3 cách tạo thread, `start()` vs `run()` |
| 2 | `ThreadLifecycleDemo.java` | 6 trạng thái thật sự của `Thread.State` |
| 3 | `RaceConditionDemo.java` | **Thấy lỗi trước**: read-modify-write, check-then-act |
| 4 | `SynchronizationDemo.java` | Giải pháp: method / block / static, reentrant |
| 5 | `VolatileDemo.java` | Visibility, ordering, happens-before |
| 6 | `WaitNotifyDemo.java` | Giao tiếp giữa thread, Producer-Consumer |
| 7 | `DeadlockDemo.java` | Tạo, phát hiện, sửa deadlock |
| 8-10 | Ôn tập + bài tập cuối module | Xem phần bên dưới |

---

## 📚 Danh sách examples hiện có

### 1. `ThreadCreationExamples.java`
- Extend `Thread`, implement `Runnable`, implement `Callable` (qua `ExecutorService`)
- **Phải nắm:** `start()` vs `run()`, vì sao thread không `start()` 2 lần được
- **Interview:** "Runnable hay Thread? Runnable hay Callable?"

### 2. `ThreadLifecycleDemo.java`
- Java chỉ có **6 state**: `NEW`, `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING`, `TERMINATED`
- ⚠️ **Không có state `RUNNING`** trong `Thread.State`. `RUNNABLE` gồm cả "đang chạy" lẫn "sẵn sàng chạy", và cả khi đang chờ I/O ở mức OS.
- **Interview:** "`sleep()` thì thread ở state nào?" → `TIMED_WAITING`; "BLOCKED vs WAITING?"

### 3. `RaceConditionDemo.java`
- Read-modify-write (`count++`), check-then-act (lazy singleton), compound operation
- Giải pháp: `synchronized`, `AtomicInteger`, double-checked locking
- **Interview:** "Cho ví dụ race condition trong dự án thật"

### 4. `SynchronizationDemo.java`
- `synchronized` method (lock `this`), block (lock object riêng), static (lock `Class`)
- Reentrancy
- **Interview:** "synchronized method vs block?", "lock trên `null`?", "2 instance khác nhau có block nhau không?"

### 5. `VolatileDemo.java`
- Visibility problem, `volatile` flag, `volatile` không atomic
- **Interview:** "volatile vs synchronized?", "volatile có atomic không?"

### 6. `WaitNotifyDemo.java`
- `wait()` / `notify()` / `notifyAll()`, Producer-Consumer
- **Interview:** "Vì sao `while` thay vì `if`?", "notify vs notifyAll?"

### 7. `DeadlockDemo.java`
- Deadlock kinh điển (chuyển tiền), fix bằng lock ordering và `tryLock`
- **Interview:** "Detect deadlock trong production thế nào?"

---

## 🧠 Phương pháp học cho mỗi file (5 bước)

1. **Đọc comment đầu file** và tự trả lời câu interview trước khi đọc code.
2. **Đoán output** trước khi chạy. Viết ra giấy.
3. **Chạy 5-10 lần**. Concurrency bug không deterministic, một lần chạy chưa chứng minh được gì.
4. **Phá code**: bỏ `synchronized`, bỏ `volatile`, đổi `while` thành `if`, đổi thứ tự lock. Quan sát chuyện gì xảy ra.
5. **Giải thích lại** bằng lời của mình (hoặc viết ngắn vào file `notes.md`). Không giải thích được nghĩa là chưa hiểu.

### Công cụ nên dùng khi học
- `jps` + `jstack <pid>`: xem thread dump khi chương trình bị treo (đặc biệt với `DeadlockDemo`)
- `jconsole` / VisualVM: xem thread state theo thời gian thực
- `javap -c ClassName`: xem bytecode của `count++` để thấy 3 bước đọc-cộng-ghi

---

## ⚠️ Lưu ý khi chạy examples

| File | Lưu ý |
|------|-------|
| `VolatileDemo` (Demo 1) | Worker thread **có thể không bao giờ dừng**, vì không có `volatile`. Thread này không phải daemon nên JVM sẽ không tự thoát; dùng `Ctrl+C`. Kết quả phụ thuộc JIT/CPU, có máy vẫn chạy bình thường. |
| `DeadlockDemo` (Demo 1) | `interrupt()` **không** gỡ được thread đang `BLOCKED` chờ `synchronized`. Chương trình có thể không thoát; dùng `jstack` để quan sát rồi `Ctrl+C`. Đây chính là điểm học. |
| `DeadlockDemo` (Demo 3) | `tryLock` có thể khiến cả 2 thread cùng timeout (không phải deadlock nhưng cũng không thành công). Dòng "✓ No deadlock" in ra cả khi transfer thất bại. Hãy tự kiểm tra return value. |
| `RaceConditionDemo` | Singleton/transfer unsafe có thể **vẫn ra kết quả đúng** ở nhiều lần chạy. Tăng số thread hoặc chạy lặp để thấy lỗi. |
| `WaitNotifyDemo` | Chương trình kết thúc bằng `System.exit(0)` vì demo `notify()` để 1 thread chờ mãi. Có chủ đích. |

---

## 🔑 Key Concepts

### Thread Safety
Code an toàn khi nhiều thread truy cập đồng thời. Các hướng tiếp cận:
- Immutable objects (không có shared mutable state, là cách tốt nhất)
- Synchronization (`synchronized`, `Lock`)
- Thread confinement (`ThreadLocal`, biến cục bộ)
- Atomic operations

### Ba tính chất cần phân biệt
| Tính chất | Ý nghĩa | Công cụ |
|-----------|---------|---------|
| **Atomicity** | Thao tác không bị chia cắt | `synchronized`, `Lock`, `Atomic*` |
| **Visibility** | Thay đổi của thread này thấy được ở thread khác | `volatile`, `synchronized`, `final` |
| **Ordering** | Không bị reorder gây sai kết quả | `volatile`, `synchronized`, happens-before |

> `volatile` chỉ cho **visibility + ordering**. `synchronized` cho **cả ba**.

### Liveness Problems
- **Deadlock:** các thread chờ nhau mãi mãi
- **Livelock:** các thread vẫn chạy nhưng không tiến triển
- **Starvation:** thread không bao giờ được cấp tài nguyên

---

## 💡 Best Practices

1. Ưu tiên giảm shared mutable state (immutable trước, lock sau)
2. Lock trên object `private final` riêng, không lock trên `this` hay `String`
3. Giữ critical section nhỏ; **không** gọi `sleep`/I/O/code lạ khi đang giữ lock
4. Tránh nested lock; nếu bắt buộc, định nghĩa lock ordering
5. `wait()` luôn trong vòng `while`; ưu tiên `notifyAll()`
6. Dùng `volatile` cho flag đơn giản; dùng `Atomic*` cho counter
7. Khi bắt `InterruptedException`: hoặc ném tiếp, hoặc gọi `Thread.currentThread().interrupt()`. **Không nuốt lặng lẽ.**
8. Ghi rõ thread-safety của class (`@ThreadSafe`, `@NotThreadSafe`, Javadoc)

---

## 📝 Bài tập cuối module

Làm **không nhìn** code mẫu:

1. **Counter:** viết counter thread-safe theo 3 cách (`synchronized`, `AtomicInteger`, `ReentrantLock`) và đo thời gian với 10 thread × 1 triệu lần.
2. **Stop flag:** viết worker dừng được bằng `volatile boolean`, sau đó viết lại bằng `interrupt()`.
3. **Producer-Consumer** với `wait/notifyAll`, 2 producer + 2 consumer, bounded buffer. (Gợi ý: vì sao `notify()` có thể gây treo ở trường hợp này?)
4. **In số chẵn/lẻ xen kẽ** bằng 2 thread dùng `wait/notify`.
5. **Deadlock:** cố ý tạo deadlock, chụp thread dump bằng `jstack`, chỉ ra dòng "Found one Java-level deadlock", rồi sửa.
6. **Singleton:** viết 3 phiên bản (DCL, static holder, enum) và giải thích vì sao DCL cần `volatile`.

---

## ✅ Checklist tự đánh giá

- [ ] Liệt kê và vẽ được sơ đồ 6 thread state, nói được hàm nào gây chuyển state
- [ ] Giải thích được `count++` gồm 3 bước và minh hoạ interleaving gây mất update
- [ ] Phân biệt được `synchronized` method / block / static: lock trên object nào
- [ ] Nói được `volatile` giải quyết gì, không giải quyết gì, kèm 1 ví dụ mỗi bên
- [ ] Viết được Producer-Consumer bằng `wait/notifyAll` không lỗi
- [ ] Nêu 4 điều kiện deadlock và ít nhất 3 cách phòng tránh
- [ ] Đọc được thread dump và tìm ra deadlock
- [ ] Phân biệt deadlock, livelock, starvation

---

## 🔜 Sẽ bổ sung trong module này

Các chủ đề thường bị hỏi ở mức Senior/Tech Lead mà chưa có file riêng:

| File dự kiến | Nội dung |
|--------------|----------|
| `ThreadInterruptionDemo.java` | `interrupt()`, `isInterrupted()`, xử lý `InterruptedException`, dừng thread an toàn |
| `ThreadBasicsDemo.java` | `join()`, `sleep()`, `yield()`, daemon thread |
| `JavaMemoryModelDemo.java` | Happens-before, reordering, safe publication, DCL giải thích sâu |
| `ExplicitLockDemo.java` | `ReentrantLock`, `Condition`, `ReadWriteLock`, `StampedLock` so với `synchronized` |
| `ThreadLocalDemo.java` | Cách dùng và memory leak khi kết hợp thread pool |
| `LivelockStarvationDemo.java` | Tái hiện livelock, starvation và cách khắc phục |
| `ImmutabilityDemo.java` | Immutable class, `final`, defensive copy |

---

## 🚀 Chạy examples

```bash
# Biên dịch tất cả
javac *.java

# Chạy từng file (mỗi file có main riêng)
java ThreadCreationExamples
java RaceConditionDemo

# Khi chương trình bị treo: lấy thread dump
jps                    # tìm pid
jstack <pid>           # xem state và deadlock
```

> Yêu cầu: JDK 17+ (khuyến nghị JDK 21 để chuẩn bị cho phần Virtual Threads ở module sau).
