# 08 — wait / notify / notifyAll

**Source:** `WaitNotifyDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.

---

## 1. Technical Knowledge

### 1.1 Ba method (thuộc `Object`)

| Method | Ý nghĩa |
|---|---|
| `wait()` | **Nhả** monitor và chờ (WAITING) tới khi được notify / interrupt / spurious wakeup |
| `notify()` | Đánh thức **một** thread bất kỳ đang chờ trên **cùng** monitor (không chọn được) |
| `notifyAll()` | Đánh thức **tất cả**; chúng lần lượt tranh lại monitor |

### 1.2 Ba quy tắc

1. Phải **giữ monitor** của object đó (trong `synchronized(obj)`), nếu không → `IllegalMonitorStateException`.
2. Luôn `wait()` trong vòng **`while`**, không dùng `if`.
3. **Đổi điều kiện trước** rồi mới notify; ưu tiên `notifyAll()`.

### 1.3 Diễn biến (Consumer chạy trước, queue rỗng)

1. Consumer vào `synchronized(lock)`, thấy queue rỗng → `wait()`: **nhả lock**, WAITING.
2. Producer vào được, add item, gọi `notifyAll()` → Consumer thức nhưng **BLOCKED** (notify **không nhả lock**).
3. Producer thoát `synchronized` → nhả lock.
4. Consumer lấy lại lock, **kiểm tra lại `while`**, rồi poll item.

Producer chạy trước: notify không đánh thức ai, tín hiệu **không được lưu lại**. Vẫn đúng vì Consumer vào sau thấy queue có hàng nên không wait. → Code đúng nhờ **kiểm tra điều kiện**, không nhờ "nhận tín hiệu".

### 1.4 Vì sao `while` mà không phải `if`? (Demo 2)

- Điều kiện có thể bị thread khác **lấy mất** trước khi ta lấy lại lock (2 consumer, 1 item, `notifyAll` → 1 consumer nhận `null`).
- **Spurious wakeup:** JVM/OS được phép đánh thức mà không có notify. (Không liên quan gì tới timeout.)

### 1.5 notify vs notifyAll (Demo 1, 3)

- `notify()` an toàn **chỉ khi** mọi thread chờ **cùng một điều kiện** và mỗi tín hiệu chỉ cần 1 thread xử lý.
- Producer + consumer chờ chung 1 monitor: `notify()` có thể đánh thức nhầm thread cùng phe → tất cả cùng wait → **treo**.
- Demo 3: 3 thread chờ một cổng, `notify()` → 2/3 thread **chờ mãi**.
- Tốt hơn: `Lock` + 2 `Condition` (`notFull`, `notEmpty`) để signal đúng nhóm.

### 1.6 Thread state

`wait()` → WAITING (`wait(ms)` → TIMED_WAITING) → được notify → **BLOCKED** → lấy lại lock → RUNNABLE.

### 1.7 Lưu ý code

- **Không `sleep()` bên trong `synchronized`**: giữ lock trong lúc ngủ, chặn mọi thread khác.
- Lock trên object `private final`, để code bên ngoài không notify nhầm.
- Production: hầu như **không tự viết** wait/notify → dùng `BlockingQueue`, `CountDownLatch`, `Semaphore`, `Lock + Condition`.

---

## 2. Interview English

### Q1. Why must `wait()` be called inside `synchronized`?

**Key sentence:** *To make "check the condition" and "start waiting" one atomic step — otherwise we lose the signal.*

**Suggested Answer:**
If we check the condition and then call `wait()` without a lock, another thread can change the condition and call `notify()` **in between**. The notification is not saved, so our thread waits **forever**. This is called a **lost wakeup**. Holding the lock makes the check and the wait one atomic step. The JVM enforces it with `IllegalMonitorStateException`.

### Q2. Why use `while` instead of `if` around `wait()`?

**Key sentence:** *After waking up, the condition may no longer be true.*

**Suggested Answer:**
There are two reasons. First, another thread may **take the item** before our thread gets the lock back. Second, **spurious wakeups** can happen: a thread can wake up without any notify. So after waking up, the thread must **check the condition again**. With `if`, it may continue with an empty queue.

### Q3. `notify()` vs `notifyAll()`?

**Key sentence:** *`notify()` wakes one random thread; `notifyAll()` wakes all of them.*

**Suggested Answer:**
`notify()` wakes **one** waiting thread, and we can't choose which. `notifyAll()` wakes **all** of them, and they compete for the lock. `notify()` is only safe when all threads wait for the same condition. If producers and consumers wait on the same monitor, `notify()` can wake the wrong thread and the program can **hang**. So I prefer `notifyAll()`, or a `Lock` with two `Condition`s.

### Q4. `wait()` vs `sleep()`?

**Key sentence:** *`wait()` releases the lock and waits for a signal; `sleep()` keeps the lock and waits for time.*

**Suggested Answer:**
`wait()` belongs to `Object`, must be called inside `synchronized`, and **releases the monitor**. It's used to coordinate threads based on a condition. `sleep()` belongs to `Thread`, can be called anywhere, and **keeps all locks**. It's only for pausing. So I never call `sleep()` while holding a lock.

### Q5. Does a thread run immediately after `notify()`?

**Key sentence:** *No — it must get the lock back first.*

**Suggested Answer:**
No. `notify()` **doesn't release the lock**. The woken thread moves to `BLOCKED` and waits until the notifying thread leaves the synchronized block. Then it gets the lock back, rechecks the condition, and continues.

### Q6. Would you use wait/notify in production?

**Key sentence:** *Rarely — I use BlockingQueue or other high-level tools.*

**Suggested Answer:**
Rarely. wait/notify is **low-level** and easy to get wrong. For producer-consumer, I use a `BlockingQueue`. For other cases, I use `CountDownLatch`, `Semaphore`, or `ReentrantLock` with `Condition`. But I still need to understand wait/notify, because these tools are built on the same ideas.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | `wait()` must be called while holding the same monitor. |
| 2 | Always call `wait()` inside a `while` loop. |
| 3 | `wait()` releases the lock; `notify()` does not. |
| 4 | `notify()` is safe only when all waiters wait for the same condition. |
| 5 | In production, use BlockingQueue instead of wait/notify. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| lost wakeup | mất tín hiệu đánh thức |
| spurious wakeup | thức dậy không có lý do |
| recheck the condition | kiểm tra lại điều kiện |
| producer-consumer | mẫu nhà sản xuất – người tiêu thụ |
| bounded buffer | hàng đợi có giới hạn |
| coordinate threads | phối hợp các thread |
| hang | treo |

## 5. Self-test

1. 3 quy tắc khi dùng wait/notify?
2. Vì sao `wait()` phải trong `synchronized`? Lost wakeup là gì?
3. 2 lý do phải dùng `while`?
4. Khi nào `notify()` an toàn? Vì sao 2P + 2C với `notify()` có thể treo?
5. Sau `notify()`, thread đi qua những state nào?
6. Vì sao không `sleep()` trong `synchronized`?
7. Mô tả 4 bước khi Consumer chạy trước. Producer chạy trước thì sao?
8. Production nên dùng gì thay wait/notify?
