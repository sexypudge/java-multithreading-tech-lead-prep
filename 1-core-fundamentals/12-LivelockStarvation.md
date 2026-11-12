# 12 — Livelock & Starvation

**Source:** `LivelockStarvationDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Ba lỗi liveness

| Vấn đề | Thread state | Tiến triển? | Ví dụ |
|---|---|---|---|
| **Deadlock** | BLOCKED / WAITING | Không | A giữ L1 chờ L2, B giữ L2 chờ L1 |
| **Livelock** | **RUNNABLE** (vẫn chạy!) | Không | 2 người cùng nhường đường cho nhau |
| **Starvation** | RUNNABLE / BLOCKED | **Một** thread không | Lock unfair, priority thấp |

### 1.2 Livelock

- Các thread **liên tục phản ứng với nhau** (nhường, retry, rollback) nhưng không tiến lên.
- Demo 1: 2 người ăn chung 1 cái thìa, ai cũng nhường → thìa chuyền qua lại mãi.
- Ví dụ thực tế: 2 transaction cùng rollback-retry ngay khi conflict; `tryLock` không đủ lock thì nhả, rồi thử lại **cùng nhịp**.
- **Khó phát hiện:** CPU bận, thread dump thấy RUNNABLE, log vẫn chạy, `jstack` **không** báo deadlock.
- Dấu hiệu: **CPU cao + throughput ≈ 0 + log lặp lại**; nhiều thread dump liên tiếp thấy cùng thread ở cùng đoạn retry.
- Sửa: **random backoff** / exponential backoff có jitter (phá sự đối xứng), thứ tự ưu tiên (ID nhỏ đi trước), giới hạn số lần retry, dùng coordinator.

### 1.3 Starvation

**Nguyên nhân:**
1. Lock **unfair** để thread mới chen ngang liên tục
2. Thread priority thấp (phụ thuộc OS, không đáng tin)
3. Thread giữ lock quá lâu (I/O trong critical section)
4. Pool nhỏ bị task dài chiếm hết, task ngắn chờ mãi
5. ReadWriteLock: reader liên tục làm writer đói

**Sửa:** fair lock (đánh đổi throughput), rút ngắn critical section, **tách pool** theo loại task, không dựa vào priority.

Demo 2: 4 thread tranh 1 lock — unfair thường lệch nhiều, fair thường đều hơn (khác nhau theo máy/OS).

---

## 2. Interview English

### Q1. Deadlock vs livelock vs starvation?

**Importance:** 🔴 Important

**Key sentence:** *Deadlock: stuck. Livelock: busy but no progress. Starvation: one thread never gets a turn.*

**Suggested Answer:**
In a **deadlock**, threads are blocked waiting for each other, so nothing moves. In a **livelock**, threads are still running and reacting to each other, but they make **no progress**, like two people stepping aside for each other in a hallway. In **starvation**, the system makes progress, but **one thread never gets** the resource because others keep taking it.

### Q2. When does livelock happen? Give an example.

**Importance:** 🟡 Medium

**Key sentence:** *When threads keep retrying in the same rhythm and keep colliding.*

**Suggested Answer:**
Livelock happens when threads keep **responding to each other** — yielding, retrying, or rolling back — without moving forward. For example, two threads use `tryLock`, fail to get both locks, release them, and retry **at the same time**. They collide again and again. Two database transactions that retry immediately after a conflict can do the same.

### Q3. How do you fix livelock?

**Importance:** 🟡 Medium

**Key sentence:** *Add random backoff to break the symmetry.*

**Suggested Answer:**
The main fix is **random backoff**, or exponential backoff with jitter. Each thread waits a different random time before retrying, so they stop colliding. Other options are giving priority by ID, so the smaller ID goes first, and **limiting the number of retries**.

### Q4. How do you detect livelock in production?

**Importance:** 🟢 Low

**Key sentence:** *High CPU, almost zero throughput, and repeating logs.*

**Suggested Answer:**
Livelock is hard to detect because threads look healthy: they are `RUNNABLE` and use CPU. `jstack` **won't report** a deadlock. The signs are **high CPU but almost no throughput**, and logs that repeat the same pattern. If I take several thread dumps in a row and see the same threads in the same retry code, it's probably livelock.

### Q5. What causes starvation and how do you fix it?

**Importance:** 🟡 Medium

**Key sentence:** *Unfair locks, long critical sections, or busy pools — fix with fairness and shorter locks.*

**Suggested Answer:**
Common causes are **unfair locks** that let new threads jump the queue, threads holding a lock for too long, a small pool filled with long tasks, and readers that keep a writer waiting. To fix it, I can use a **fair lock**, keep critical sections short, and use **separate pools** for different kinds of tasks. I don't rely on thread priority, because it depends on the OS.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | Deadlock: blocked; livelock: busy with no progress; starvation: one thread never runs. |
| 2 | Livelocked threads are RUNNABLE, so jstack doesn't report them. |
| 3 | Signs of livelock: high CPU, near-zero throughput, repeating logs. |
| 4 | Random backoff with jitter breaks livelock. |
| 5 | Fair locks reduce starvation but lower throughput. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| liveness | khả năng chương trình tiếp tục tiến lên |
| make progress | có tiến triển |
| retry in the same rhythm | thử lại cùng nhịp |
| random backoff / jitter | chờ ngẫu nhiên trước khi thử lại |
| break the symmetry | phá sự đối xứng |
| throughput | lượng việc xử lý được |
| starve / starvation | bị đói (không tới lượt) |

## 5. Self-test

1. So sánh deadlock, livelock, starvation (state, có tiến triển không, ví dụ).
2. Vì sao livelock khó phát hiện hơn deadlock?
3. Cho 2 ví dụ livelock thực tế.
4. Vì sao backoff phải ngẫu nhiên?
5. Kể 5 nguyên nhân starvation.
6. Fair lock đánh đổi gì?
7. Phát hiện livelock trong production bằng dấu hiệu nào?
