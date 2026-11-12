# 02 — ConcurrencyTestingDemo: test code concurrent thế nào

**Source:** `ConcurrencyTestingDemo.java` · **Priority:** P0
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Counter thread-safe 4 cách (+ 1 bản sai)

| Class | Cách | Ghi chú |
|---|---|---|
| `UnsafeCounter` | `n++` | Mất update — test phải bắt được |
| `SynchronizedCounter` | `synchronized` cả `increment` **và** `get` | `get` cũng cần lock (visibility) |
| `LockCounter` | `ReentrantLock`, `unlock()` trong `finally` | |
| `AtomicCounter` | `incrementAndGet()` | CAS |
| `LongAdderCounter` | `increment()`, `sum()` | `sum()` chính xác **sau khi** mọi writer đã xong |

### 1.2 Công thức test concurrency (6 điểm)

1. **Start gate**: `CountDownLatch(1)` → mọi thread bắt đầu **cùng lúc**. Không có thì thread đầu chạy xong trước khi thread cuối start → test "luôn xanh".
2. **Nhiều thread hơn số core**, nhiều vòng lặp, **lặp lại cả test** nhiều lần.
3. Assert **bất biến** (tổng = threads × ops), không assert thứ tự in ra.
4. Mọi chỗ chờ đều có **timeout** (test treo tệ hơn test đỏ). **Không** `sleep()` để "đợi cho chắc".
5. **Propagate exception** từ worker về thread test qua `Future.get()` — assert trong thread khác **không làm test fail**.
6. Biết giới hạn: **test xanh ≠ code đúng**. Bug JMM → **jcstress**; đo hiệu năng → **JMH**.

### 1.3 Harness `runConcurrently`

```java
CountDownLatch ready = new CountDownLatch(threads);
CountDownLatch startGate = new CountDownLatch(1);
// mỗi worker: ready.countDown(); startGate.await(); for (...) action.run();
if (!ready.await(5, SECONDS)) throw new AssertionError("worker không sẵn sàng");
startGate.countDown();                                  // XUẤT PHÁT
for (Future<?> f : futures) f.get(30, SECONDS);         // exception + chống treo
```

`ready` đảm bảo mọi worker đã **đứng ở vạch** trước khi bắn súng.

### 1.4 Follow-up

- Test async (`CompletableFuture`, `@Async`): `join()` / `get(timeout)` hoặc **Awaitility** `await().atMost(2, SECONDS).until(...)`.
- Bug visibility (thiếu `volatile`): unit test gần như không bắt được → **jcstress**.
- So sánh throughput: **JMH** (`@Threads`, `@Fork`, `@Warmup`). Xu hướng khi tranh chấp cao: LongAdder < Atomic < lock.

---

## 2. Interview English

### Q1. How do you test concurrent code?

**Importance:** 🔴 Important

**Key sentence:** *Start gate, many threads, many repetitions, assert an invariant, timeouts everywhere.*

**Suggested Answer:**
I use a **start gate** — a `CountDownLatch` of one — so all threads start at the same moment and really overlap. I use more threads than cores and many iterations, and I **repeat the whole test** several times. I assert an **invariant**, like the final count equals threads times operations, not the output order. Every wait has a **timeout**, and I collect worker results with `Future.get()` so exceptions fail the test.

### Q2. Why do you need a start gate?

**Importance:** 🔴 Important

**Key sentence:** *Without it, threads run one after another and the race never happens.*

**Suggested Answer:**
Starting a thread takes time. Without a gate, the first thread may **finish all its work** before the last thread even starts, so the threads barely overlap and the test **passes even for broken code**. With a start gate, every worker waits at the same line, and I release them all at once, which **maximizes interleaving**. I also use a second latch to make sure all workers are ready before I open the gate.

### Q3. Your concurrency test is green. Is the code correct?

**Importance:** 🔴 Important

**Key sentence:** *No — a green run only means the bug didn't show up this time.*

**Suggested Answer:**
No. Concurrency bugs depend on **timing**, CPU count and the JIT, so a green run only means the bug **didn't happen this time**. Repeating the test raises the chance of catching it, but it's never proof. Bugs like missing `volatile` or reordering are almost impossible to catch with unit tests — for those I'd use **jcstress**. The main defense is still a correct design and code review.

### Q4. Why can't you just put assertions inside worker threads?

**Importance:** 🟡 Medium

**Key sentence:** *An exception in another thread doesn't fail the test; it must be propagated back.*

**Suggested Answer:**
JUnit only sees exceptions thrown on the **test thread**. If an assertion fails inside a worker thread, that thread dies, maybe prints a stack trace, and the test still **passes**. So I submit workers to an executor and call **`Future.get()`** on each one; any exception comes back as an `ExecutionException` and fails the test. `get` also has a timeout, so a hanging worker fails the test instead of blocking the build.

### Q5. How do you test async code without `Thread.sleep()`?

**Importance:** 🟡 Medium

**Key sentence:** *Wait on the result with a timeout, or poll a condition with Awaitility.*

**Suggested Answer:**
`sleep` makes tests **slow and flaky**: too short and it fails on a slow CI machine, too long and it wastes time. If I have a future, I call `get` or `join` with a **timeout**. If I'm waiting for a side effect, I use **Awaitility**: `await().atMost(2, SECONDS).until(...)`. It polls the condition and returns as soon as it's true.

### Q6. How would you compare the performance of the four counters?

**Importance:** 🟢 Low

**Key sentence:** *With JMH, not with a hand-written timing loop.*

**Suggested Answer:**
The timings printed by this demo are **not a real benchmark** — there's no warm-up and no separate JVM forks, so the JIT can distort the numbers. I'd use **JMH** with `@Threads`, `@Warmup` and `@Fork`. Under high contention, I'd usually expect `LongAdder` to be fastest, then `AtomicInteger`, then the locks — but I'd measure on the target hardware.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | Use a start gate so threads really overlap. |
| 2 | Assert an invariant, not the output order. |
| 3 | Every wait needs a timeout. |
| 4 | Propagate worker exceptions with Future.get(). |
| 5 | A green test doesn't prove thread safety. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| start gate | cổng xuất phát |
| interleaving | xen kẽ giữa các thread |
| invariant | bất biến |
| flaky test | test lúc xanh lúc đỏ |
| propagate an exception | đưa exception về |
| stress test | test dưới tải cao |
| micro-benchmark | đo hiệu năng đoạn code nhỏ |

## 5. Self-test

1. 6 điểm của công thức test concurrency?
2. Vì sao cần start gate? Vì sao cần thêm latch `ready`?
3. Vì sao `SynchronizedCounter.get()` cũng phải `synchronized`?
4. Assert trong worker thread có làm test fail không? Sửa thế nào?
5. Test xanh có chứng minh code đúng không? Bug visibility test bằng gì?
6. Test async không dùng `sleep` thế nào?
7. Vì sao số ms trong demo không phải benchmark?
