# 06 — Virtual Threads (Java 21+)

**Source:** `VirtualThreadsDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Platform thread vs virtual thread

| | Platform thread | Virtual thread |
|---|---|---|
| Ánh xạ | 1-1 với thread OS | Nhiều virtual → ít **carrier thread** (ForkJoinPool) |
| Stack | ~1MB, ngoài heap | Nhỏ (vài KB), **trên heap** |
| Số lượng | vài nghìn | hàng trăm nghìn / triệu |
| Daemon | tuỳ | **luôn daemon** |

### 1.2 Cơ chế mount / unmount

- Virtual thread được **mount** lên carrier để chạy.
- Gặp thao tác chặn (I/O, `sleep`, lock của `java.util.concurrent`) → **unmount**, carrier rảnh đi chạy virtual thread khác. Chờ xong → mount lại.
- ⇒ Viết code **đồng bộ, đơn giản** (thread-per-request) mà vẫn chịu được rất nhiều đồng thời cho việc I/O-bound.

### 1.3 KHÔNG phải để chạy nhanh hơn

- Không tăng tốc CPU-bound (vẫn giới hạn bởi số core).
- Lợi ích là **throughput** cho I/O-bound, không phải latency của một request.
- Demo 1: 2000 task chặn 200ms — pool 100 thread ≈ 4000ms; virtual thread ≈ 200ms.

### 1.4 API (Demo 2)

```java
Thread.ofVirtual().name("my-virtual").start(task);
Thread.startVirtualThread(task);
try (var vt = Executors.newVirtualThreadPerTaskExecutor()) {   // mỗi task 1 virtual thread mới
    vt.submit(task);
}                                                              // close() = shutdown + chờ
```

**Không pool virtual thread.** Pool tồn tại để giới hạn tài nguyên khan hiếm; virtual thread không khan hiếm.

### 1.5 Pinning (Demo 3)

Virtual thread **không unmount được** → giữ chặt carrier khi:
- chặn bên trong `synchronized` (**Java 21–23**; Java 24+ đã sửa — JEP 491),
- đang gọi native method / foreign function.

Demo (1 carrier): sleep trong `synchronized` ≈ 1000ms (chạy lần lượt) vs `ReentrantLock` ≈ 500ms (song song) trên Java 21–23.
- Tránh (Java 21–23): `ReentrantLock` quanh đoạn chặn.
- Phát hiện: `-Djdk.tracePinnedThreads=full` hoặc JFR event `jdk.VirtualThreadPinned`.

### 1.6 Giới hạn đồng thời bằng Semaphore (Demo 4)

1000 virtual thread nhưng DB chỉ chịu 10 truy vấn → `Semaphore(10)`. Virtual thread **không tự bảo vệ downstream**: không còn pool làm "van" → phải tự đặt van.

### 1.7 ThreadLocal & ScopedValue

- `ThreadLocal` vẫn dùng được, nhưng hàng triệu thread × bản sao riêng → tốn bộ nhớ. Đừng dùng để cache object nặng.
- Hướng mới: `ScopedValue` (preview Java 21–24, chính thức Java 25 — JEP 506).

---

## 2. Interview English

### Q1. What's the difference between a virtual thread and a platform thread?

**Importance:** 🔴 Important

**Key sentence:** *A platform thread is an OS thread; a virtual thread is a cheap JVM thread that unmounts when it blocks.*

**Suggested Answer:**
A platform thread maps **one-to-one to an OS thread**, with about 1 MB of stack, so we can only have a few thousand. A virtual thread is managed by the **JVM**. It runs on a small number of **carrier threads**, and its stack lives on the heap, so it's very cheap. When it blocks on I/O, it **unmounts** from the carrier, and the carrier runs another virtual thread.

### Q2. When should you use virtual threads, and when not?

**Importance:** 🔴 Important

**Key sentence:** *For lots of concurrent I/O; not for CPU-bound work.*

**Suggested Answer:**
They're great for **many concurrent I/O-bound tasks**, like a web server with thread-per-request, or calling several services. They improve **throughput**, not the speed of one request. They **don't help CPU-bound** work, because we're still limited by the number of cores. And on Java 21 to 23, code that blocks inside `synchronized` or calls native code can get **pinned**.

### Q3. What is pinning and how do you avoid it?

**Importance:** 🔴 Important

**Key sentence:** *A pinned virtual thread can't unmount, so it holds its carrier thread while blocking.*

**Suggested Answer:**
Pinning means the virtual thread **can't unmount** from its carrier while it's blocked, so the carrier is stuck too. On Java 21 to 23 this happens when you block **inside `synchronized`**, and on all versions during **native calls**. With few carriers, that kills throughput. The fix is to use `ReentrantLock` around blocking code. Java 24 fixed it for `synchronized`. To detect it, I use `-Djdk.tracePinnedThreads` or the JFR pinned event.

### Q4. Should you pool virtual threads?

**Importance:** 🔴 Important

**Key sentence:** *No — create one per task, and limit concurrency with a semaphore.*

**Suggested Answer:**
No. We pool platform threads because they're **expensive and scarce**. Virtual threads are cheap, so we **create a new one per task**, for example with `newVirtualThreadPerTaskExecutor`. But that means there's no pool limiting concurrency anymore. If the database can only handle 10 queries at a time, I add a **`Semaphore`** to protect it. Virtual threads don't protect the downstream for you.

### Q5. Do virtual threads replace `CompletableFuture` or reactive programming?

**Importance:** 🟡 Medium

**Key sentence:** *For many I/O cases, simple blocking code on virtual threads is easier to write and debug.*

**Suggested Answer:**
For many I/O-bound cases, yes: I can write **simple blocking code** and still handle high concurrency, and stack traces and debugging are **normal**. Reactive code is harder to read and debug. But `CompletableFuture` is still useful to **compose** or run independent calls in parallel, and reactive streams still fit for streaming and back-pressure use cases.

### Q6. Can you use `ThreadLocal` with virtual threads?

**Importance:** 🟡 Medium

**Key sentence:** *Yes, but with millions of threads it can use a lot of memory — ScopedValue is the new option.*

**Suggested Answer:**
Yes, it still works, and each virtual thread has its own copy. But with **millions** of virtual threads, those copies can use a lot of memory, so I don't use ThreadLocal to **cache heavy objects** like formatters. The newer alternative is `ScopedValue`, which is immutable and bound to a scope. It was a preview in Java 21 and became final in **Java 25**.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | Virtual threads are cheap JVM threads that unmount when they block. |
| 2 | They improve throughput for I/O, not speed for CPU work. |
| 3 | Pinning holds the carrier thread — use ReentrantLock on Java 21 to 23. |
| 4 | Don't pool virtual threads; create one per task. |
| 5 | Limit downstream concurrency with a semaphore. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| carrier thread | platform thread chở virtual thread |
| mount / unmount | gắn / gỡ khỏi carrier |
| pinning | virtual thread bị "đóng đinh" vào carrier |
| thread-per-request | mỗi request một thread |
| throughput | số việc xử lý trong một đơn vị thời gian |
| scarce resource | tài nguyên khan hiếm |
| downstream | service / hệ thống phía sau |

## 5. Self-test

1. Platform thread vs virtual thread: ánh xạ, stack, số lượng?
2. Mount / unmount hoạt động thế nào?
3. Virtual thread có làm code CPU-bound nhanh hơn không? Lợi ích thật là gì?
4. Pinning xảy ra khi nào? Java 24 thay đổi gì? Phát hiện bằng gì?
5. Vì sao không pool virtual thread? Giới hạn đồng thời bằng gì?
6. ThreadLocal với virtual thread có vấn đề gì? ScopedValue là gì?
