# 05 — Synchronizers: CountDownLatch, CyclicBarrier, Semaphore, Phaser

**Source:** `SynchronizersDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Bảng phân biệt nhanh

| | Ý nghĩa | Ai chờ ai | Tái sử dụng |
|---|---|---|---|
| `CountDownLatch` | Chờ **N sự kiện** rồi đi tiếp | thread này chờ thread khác (bên chờ ≠ bên `countDown`) | ✗ (về 0 là xong) |
| `CyclicBarrier` | **N thread chờ nhau** ở điểm hẹn | các thread chờ **lẫn nhau** | ✓ (nhiều vòng) |
| `Semaphore` | **Tối đa N thread** cùng vào | không chờ ai, chỉ giới hạn số lượng | ✓ |
| `Phaser` | Barrier linh hoạt, số bên **thay đổi** lúc runtime | lẫn nhau, nhiều pha | ✓ |

### 1.2 CountDownLatch (Demo 1)

- (a) Chờ worker xong: `countDown()` **trong `finally`** (worker lỗi cũng phải đếm, nếu không main chờ mãi); `await(timeout)` ở production.
- (b) **Start gate**: `new CountDownLatch(1)`, mọi thread `await()`, main `countDown()` → cùng xuất phát. Hay dùng để **test concurrency**.
- Dùng 1 lần: về 0 thì `await()` trả về ngay, không reset được.

### 1.3 CyclicBarrier (Demo 2)

- `new CyclicBarrier(parties, barrierAction)`: `barrierAction` chạy bởi **thread đến cuối cùng**, trước khi thả tất cả.
- Dùng lại cho nhiều vòng → tính toán theo **pha** (mô phỏng, ma trận, test nhiều bước).
- 1 thread chết / timeout / bị interrupt → barrier **broken**, các thread khác nhận `BrokenBarrierException`.

### 1.4 Semaphore (Demo 3)

```java
permits.acquire();          // hết permit thì CHỜ
try {
    callDownstream();
} finally {
    permits.release();      // BẮT BUỘC trong finally
}
```

- 8 thread, 3 permit → tối đa 3 thread trong vùng.
- `tryAcquire(timeout)` → **load shedding**: từ chối thay vì chờ mãi.
- `Semaphore(1)` ≈ mutex nhưng **không có chủ sở hữu** (thread A acquire, thread B release được) và **không reentrant** → dễ rò permit.
- Dùng: giới hạn đồng thời tới connection pool, API có rate limit, **bulkhead**, giới hạn virtual thread.

### 1.5 Phaser (Demo 4, P2)

- `register()` / `arriveAndDeregister()` → số bên tham gia thay đổi lúc runtime; pha có đánh số.
- Ít gặp trong code nghiệp vụ; biết để so sánh với `CyclicBarrier`.

---

## 2. Interview English

### Q1. `CountDownLatch` vs `CyclicBarrier`?

**Importance:** 🔴 Important

**Key sentence:** *A latch waits for events and is one-shot; a barrier makes threads wait for each other and can be reused.*

**Suggested Answer:**
With a `CountDownLatch`, one or more threads **wait for N events** from other threads. The waiting threads and the counting threads are different, and it's **one-shot** — once it reaches zero it can't be reset. With a `CyclicBarrier`, N threads **wait for each other** at a meeting point, then all continue. It's **reusable** for many rounds and can run a barrier action when everyone arrives.

### Q2. When would you use a `Semaphore`?

**Importance:** 🔴 Important

**Key sentence:** *To limit how many threads use a resource at the same time.*

**Suggested Answer:**
A semaphore holds N **permits**. I use it to **limit concurrency** to a scarce resource — for example, a third-party API that only accepts 10 parallel requests, or a database. It's also how I limit concurrency with **virtual threads**, since there's no pool to limit them. I always release in `finally`, and with `tryAcquire` and a timeout I can **reject extra load** instead of waiting forever.

### Q3. What happens if a worker forgets to call `countDown()`?

**Importance:** 🔴 Important

**Key sentence:** *await() waits forever — so countDown goes in finally and await gets a timeout.*

**Suggested Answer:**
The count never reaches zero, so the thread calling `await()` **hangs forever**. That usually happens when a worker throws an exception before counting down. So I put `countDown()` in a **`finally` block**, and in production I use `await` **with a timeout**, so I can log an error and continue instead of hanging.

### Q4. How is `Semaphore(1)` different from a `ReentrantLock`?

**Importance:** 🟡 Medium

**Key sentence:** *A lock has an owner and is reentrant; a semaphore has neither.*

**Suggested Answer:**
A `ReentrantLock` has an **owner**: only the thread that locked it can unlock it, and the same thread can lock it **again**. A semaphore has **no owner** — any thread can release a permit — and it's **not reentrant**, so if the same thread acquires twice it can block itself. That flexibility makes it easy to **leak permits** if you forget to release.

### Q5. Give real examples of each synchronizer.

**Importance:** 🟡 Medium

**Key sentence:** *Latch to wait for startup or parallel calls, barrier for phases, semaphore for limits.*

**Suggested Answer:**
A **latch**: wait until several services finish starting, or call N APIs in parallel and continue when all have answered. I also use a latch with count one as a **start gate** in concurrency tests, so all threads start at the same moment. A **barrier**: parallel computation in phases, where every thread must finish step one before step two. A **semaphore**: limit to 10 concurrent requests to a downstream service.

### Q6. How is `Phaser` different from `CyclicBarrier`?

**Importance:** 🟢 Low

**Key sentence:** *Phaser lets the number of parties change at runtime.*

**Suggested Answer:**
`CyclicBarrier` has a **fixed number of parties** set in the constructor. `Phaser` lets threads **register and deregister at runtime**, and it tracks numbered **phases**. So it's more flexible when the number of workers changes between steps. In business code it's rare; I'd usually reach for a latch, a barrier, or `CompletableFuture` first.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | A latch waits for events; a barrier makes threads wait for each other. |
| 2 | A latch is one-shot; a barrier is reusable. |
| 3 | A semaphore limits how many threads use a resource at once. |
| 4 | Always count down and release in `finally`. |
| 5 | A semaphore has no owner and isn't reentrant. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| synchronizer | công cụ phối hợp thread |
| one-shot | dùng một lần |
| meeting point / rendezvous | điểm hẹn |
| permit | giấy phép |
| start gate | cổng xuất phát |
| load shedding | từ chối bớt tải khi quá tải |
| broken barrier | barrier bị hỏng |

## 5. Self-test

1. Latch vs Barrier vs Semaphore — ai chờ ai? Cái nào dùng lại được?
2. Vì sao `countDown()` phải ở `finally`? Vì sao `await` cần timeout?
3. Start gate là gì? Dùng để làm gì?
4. `barrierAction` chạy bởi thread nào? Khi nào barrier bị broken?
5. Semaphore dùng cho những tình huống nào? `tryAcquire` để làm gì?
6. `Semaphore(1)` khác `ReentrantLock` ở 2 điểm nào?
7. Phaser khác `CyclicBarrier` thế nào?
