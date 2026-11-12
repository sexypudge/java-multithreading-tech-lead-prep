# 07 — Thread Diagnosis: thread dump, CPU cao, pool đầy, contention, deadlock

**Source:** `ThreadDiagnosisDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Quy trình khi production có sự cố

1. **Triệu chứng:** CPU cao? latency tăng? request treo? hết connection?
2. **Lấy thread dump:** `jcmd <pid> Thread.print` (hoặc `jstack <pid>`, `kill -3 <pid>`). Lấy **3 lần cách nhau 5–10 giây** → thread nào đứng yên một chỗ?
3. **Đọc trạng thái:** `RUNNABLE` / `BLOCKED` / `WAITING` / `TIMED_WAITING`.
4. **Đối chiếu bảng** dưới đây.

### 1.2 Bảng triệu chứng → thread dump → nguyên nhân

| Triệu chứng | Thread dump thường thấy | Nguyên nhân hay gặp |
|---|---|---|
| CPU 100% | vài thread `RUNNABLE` cùng một chỗ | vòng lặp vô hạn, regex xấu, `HashMap` dùng chung, GC |
| CPU thấp, request treo | nhiều thread `BLOCKED` / `WAITING` | lock contention, deadlock, pool đầy |
| Pool đầy / queue phình | worker `WAITING` ở `Future.get` / `join` / `take` | task chờ task cùng pool, downstream chậm không timeout |
| Hết connection | nhiều thread chờ ở `getConnection()` | giữ connection quá lâu, leak |

### 1.3 Trạng thái thread

- `BLOCKED`: chờ vào `synchronized` (monitor).
- `WAITING`: chờ vô hạn (`wait`, `join`, `park`, `Future.get`).
- `TIMED_WAITING`: chờ có hạn (`sleep`, `wait(t)`).
- `RUNNABLE`: đang chạy **hoặc đang chờ I/O ở mức OS** (vd `socketRead`).

### 1.4 Sự cố 1 — CPU cao

```bash
top -H -p <pid>                           # TID (thập phân) của thread nóng
printf '%x\n' <TID>                       # đổi sang hex
jstack <pid> | grep -A 20 nid=0x<hex>     # stack của chính thread đó
```

Demo dùng `ThreadMXBean.getThreadCpuTime` đo CPU từng thread trong 600ms → thread `report-generator-7` đứng đầu, stack chỉ thẳng dòng code. Nếu thread nóng là **GC thread** → xem log/metric GC thay vì code.

### 1.5 Sự cố 2 — Pool starvation

- Pool 2 thread, mỗi task submit task con vào **chính pool đó** rồi `get()`.
- Metric: `active == poolSize`, `queue > 0`, `completed` **không tăng**.
- Dump: mọi worker `WAITING` trong `FutureTask.get`. `findDeadlockedThreads()` **không phát hiện** (không có lock).
- Sửa: tách pool 2 tầng / `thenCompose` / không `get()` trong pool.

### 1.6 Sự cố 3 — Lock contention

- 3 thread `BLOCKED`, cùng chờ một lock, owner là `slow-holder`.
- `slow-holder` lại `TIMED_WAITING` (đang sleep/I/O) **trong khi vẫn giữ lock** → lỗi thiết kế: **không I/O bên trong `synchronized`**.

### 1.7 Sự cố 4 — Deadlock

- `ThreadMXBean.findDeadlockedThreads()`; `jstack` in **"Found one Java-level deadlock"** ở cuối dump.
- Vòng chờ A → B → A. Sửa: **thứ tự lock cố định**, hoặc `tryLock` có timeout.

### 1.8 Đặt tên thread

`pool-3-thread-12` không nói gì; `order-pool-12` chỉ thẳng module lỗi. Thread dump liệt kê **theo tên**.

---

## 2. Interview English

### Q1. Production CPU is at 100%. What do you do?

**Importance:** 🔴 Important

**Key sentence:** *Find the hottest thread with top -H, convert its ID to hex, and find its stack in a thread dump.*

**Suggested Answer:**
First, I run `top -H -p <pid>` to find the **thread using the most CPU**. I convert its thread ID to **hex** and take a thread dump with `jcmd Thread.print` or `jstack`. Then I search for `nid=0x...` to see **that thread's stack**, which usually points to the exact code — an infinite loop, a bad regex, or a shared `HashMap`. I take a few dumps a few seconds apart. If the hot threads are **GC threads**, I look at GC logs and memory instead.

### Q2. Requests hang but CPU is low. How do you investigate?

**Importance:** 🔴 Important

**Key sentence:** *Take thread dumps and see where the BLOCKED and WAITING threads are stuck.*

**Suggested Answer:**
Low CPU means threads are **waiting**, not working. I take several thread dumps and look at where the BLOCKED and WAITING threads are. Many threads **BLOCKED** on the same lock means **contention** — I check who owns it. jstack prints **"Found one Java-level deadlock"** for a deadlock. Workers **WAITING in `Future.get`** mean pool starvation. Threads in `socketRead` or `getConnection` mean a slow downstream or an exhausted connection pool, usually because of **missing timeouts**.

### Q3. What are the thread states? BLOCKED vs WAITING?

**Importance:** 🔴 Important

**Key sentence:** *BLOCKED waits for a monitor; WAITING waits for a signal.*

**Suggested Answer:**
**BLOCKED** means the thread is waiting to **enter a `synchronized` block** because another thread holds the monitor. **WAITING** means it's waiting with no time limit for something else — `wait`, `join`, `LockSupport.park`, or `Future.get`. **TIMED_WAITING** is the same with a timeout, like `sleep`. Be careful: **RUNNABLE** can also mean the thread is blocked in OS-level I/O, like reading from a socket.

### Q4. How do you recognize thread pool starvation?

**Importance:** 🔴 Important

**Key sentence:** *All threads active, queue growing, completed count flat, workers waiting on Future.get.*

**Suggested Answer:**
In the metrics, **active threads equal the max pool size**, the **queue keeps growing**, and the **completed task count stops increasing**. In the thread dump, all workers are **WAITING in `Future.get` or `join`**. The deadlock detector won't find it, because there's no lock. To prevent it, I use a bounded queue with a rejection policy, name the threads, export active, queue and rejected metrics, and never block on a task from the same pool.

### Q5. A thread holds a lock and does slow I/O. What do you see and how do you fix it?

**Importance:** 🟡 Medium

**Key sentence:** *Many BLOCKED threads pointing to one owner that is itself waiting on I/O.*

**Suggested Answer:**
In the dump, many threads are **BLOCKED** on the same lock, and the dump shows the **same owner**. That owner is not working — it's TIMED_WAITING or in a socket read, doing **I/O while holding the lock**. That's a design bug. I fix it by moving the I/O **outside the critical section**, keeping the lock only for the shared state, or using a finer-grained lock or a concurrent collection.

### Q6. How do you detect and fix a deadlock in production?

**Importance:** 🟡 Medium

**Key sentence:** *jstack reports it; fix it with a fixed lock order or tryLock with a timeout.*

**Suggested Answer:**
`jstack` or `jcmd` prints **"Found one Java-level deadlock"** at the end of the dump, with which thread holds which lock. In code, `ThreadMXBean.findDeadlockedThreads()` does the same, so we can expose it as a health check. The JVM can't recover, so we usually restart. The real fix is in the code: always acquire locks in **one fixed order**, or use `tryLock` with a timeout and back off.

### Q7. Why should you name your threads?

**Importance:** 🟡 Medium

**Key sentence:** *A thread dump is organized by thread name, so good names point straight to the problem.*

**Suggested Answer:**
In a thread dump, threads are listed by **name**. A name like `pool-3-thread-12` tells me nothing. A name like `order-pool-12` or `payment-client-3` tells me immediately **which module or pool** is stuck or burning CPU. So I always use a `ThreadFactory` that gives threads meaningful names, and the same names show up in logs and metrics.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | Take several thread dumps a few seconds apart. |
| 2 | High CPU: find the hot thread with top -H, then its stack. |
| 3 | Low CPU but hanging: look at BLOCKED and WAITING threads. |
| 4 | The deadlock detector can't see pool starvation. |
| 5 | Never do I/O while holding a lock. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| thread dump | ảnh chụp stack của mọi thread |
| hot thread | thread ngốn CPU |
| lock owner | thread đang giữ lock |
| lock contention | tranh chấp lock |
| connection pool exhausted | hết connection |
| health check | kiểm tra sức khỏe ứng dụng |
| root cause | nguyên nhân gốc |

## 5. Self-test

1. Quy trình 4 bước khi có sự cố? Vì sao lấy dump 3 lần?
2. Các lệnh tìm thread ngốn CPU trên Linux?
3. BLOCKED / WAITING / TIMED_WAITING / RUNNABLE nghĩa là gì? RUNNABLE có thể đang chờ I/O không?
4. Request treo, CPU thấp: nhìn dump thấy gì ứng với từng nguyên nhân?
5. Dấu hiệu pool starvation trong metric và trong dump? Vì sao deadlock detector không thấy?
6. Lock contention trong dump trông thế nào? Sửa ra sao?
7. Phát hiện deadlock bằng gì? Sửa thế nào?
