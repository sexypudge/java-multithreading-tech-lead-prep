# 02 — Thread Lifecycle & States

**Source:** `ThreadLifecycleDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.

---

## 1. Technical Knowledge

### 1.1 Sáu state (KHÔNG có "RUNNING")

| State | Khi nào |
|---|---|
| `NEW` | Đã `new Thread()`, chưa `start()` |
| `RUNNABLE` | Đang chạy, **hoặc** sẵn sàng chạy, **hoặc** đang chờ I/O ở OS (đọc socket...) |
| `BLOCKED` | Chờ vào `synchronized` (monitor đang bị thread khác giữ) |
| `WAITING` | Chờ vô hạn: `wait()`, `join()`, `LockSupport.park()` (`ReentrantLock.lock()`, `Future.get()`) |
| `TIMED_WAITING` | Chờ có hạn: `sleep(ms)`, `wait(ms)`, `join(ms)`, `parkNanos` |
| `TERMINATED` | `run()` đã kết thúc (bình thường hoặc do exception) |

### 1.2 Sơ đồ

```
        start()                    run() kết thúc
NEW ──────────► RUNNABLE ─────────────────────► TERMINATED
                 │   ▲     │   ▲
 synchronized    │   │     │   │ notify / unpark / hết hạn / interrupt
 nhưng lock bận  ▼   │     ▼   │
             BLOCKED │   WAITING / TIMED_WAITING
                 └───┘
          lấy được monitor
```

Thread thoát `wait()` phải lấy lại monitor → đi **WAITING → BLOCKED → RUNNABLE**.

### 1.3 Các điểm hay bị hỏi vặn

- **BLOCKED chỉ dành cho `synchronized`.** Chờ `ReentrantLock.lock()` → **WAITING** (vì dùng `park`).
- **Đọc socket (blocking I/O) → RUNNABLE.** Thread dump thấy RUNNABLE chưa chắc đang tốn CPU.
- Thread đang `await()` latch **trong** `synchronized` vẫn **giữ** monitor (state WAITING, nhưng thread khác vẫn BLOCKED).
- Thread `TERMINATED` **không start lại được**.

### 1.4 sleep() vs wait()

| | `sleep()` | `wait()` |
|---|---|---|
| Thuộc | `Thread` (static) | `Object` |
| Nhả lock? | **Không** | **Có** (nhả monitor đó) |
| Cần `synchronized`? | Không | **Có** |
| State | TIMED_WAITING | WAITING (`wait(ms)` → TIMED_WAITING) |

---

## 2. Interview English

### Q1. What are the thread states in Java?

**Key sentence:** *There are six states: NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, and TERMINATED.*

**Suggested Answer:**
A new thread is in the `NEW` state. After `start()`, it becomes `RUNNABLE`, which means running or ready to run. It can move to `BLOCKED` when waiting for a monitor lock, or to `WAITING` and `TIMED_WAITING` when waiting for a signal or a timeout. When `run()` finishes, the thread is `TERMINATED`. There is **no RUNNING state** in Java.

### Q2. What is the difference between BLOCKED and WAITING?

**Key sentence:** *BLOCKED waits for a monitor lock; WAITING waits for a signal.*

**Suggested Answer:**
A thread is `BLOCKED` when it wants to enter a `synchronized` block, but another thread holds the lock. A thread is `WAITING` when it **chooses to wait** for something, for example `wait()`, `join()`, or `park()`. One tricky point: a thread waiting for a `ReentrantLock` is `WAITING`, not `BLOCKED`, because `ReentrantLock` uses `park()`.

### Q3. What state is a thread in after `notify()`?

**Key sentence:** *It doesn't run immediately — it moves from WAITING to BLOCKED first.*

**Suggested Answer:**
`notify()` does not release the lock. So the thread that wakes up must **get the monitor back** first. It moves from `WAITING` to `BLOCKED`, and only when it gets the lock does it become `RUNNABLE` again.

### Q4. What state is a thread in while reading from a socket?

**Key sentence:** *RUNNABLE — the JVM doesn't separate "running" from "waiting for I/O".*

**Suggested Answer:**
It is `RUNNABLE`. From the JVM's point of view, the thread is running native code, even if the OS is waiting for data. So when I read a thread dump, a `RUNNABLE` thread is **not always using CPU**.

### Q5. sleep() vs wait()?

**Key sentence:** *`wait()` releases the lock; `sleep()` keeps it.*

**Suggested Answer:**
`sleep()` is a static method of `Thread`. It pauses the thread for some time and **keeps all locks**. `wait()` is a method of `Object`. It must be called inside `synchronized`, and it **releases the monitor** so other threads can enter. I use `wait()` to coordinate threads, and `sleep()` only to pause.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | Java has six thread states — there is no RUNNING state. |
| 2 | BLOCKED is only for synchronized; ReentrantLock waiting is WAITING. |
| 3 | After notify, a thread goes WAITING → BLOCKED → RUNNABLE. |
| 4 | Blocking I/O shows as RUNNABLE in a thread dump. |
| 5 | `wait()` releases the lock; `sleep()` does not. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| thread state | trạng thái thread |
| monitor lock | lock của `synchronized` |
| wait for a signal | chờ tín hiệu |
| time out | hết thời gian chờ |
| thread dump | ảnh chụp trạng thái mọi thread |
| get the lock back / reacquire the lock | lấy lại lock |

## 5. Self-test

1. Kể 6 state. Có state RUNNING không?
2. `sleep()` ở state nào? `join()`? `join(ms)`?
3. Chờ `ReentrantLock.lock()` ở state nào? Vì sao không phải BLOCKED?
4. Sau `notify()` thread đi qua những state nào?
5. Thread đọc socket ở state nào? Ý nghĩa khi đọc thread dump?
6. `sleep()` khác `wait()` ở 4 điểm nào?
