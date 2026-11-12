# 01 — EvenOddPrinter: hai thread in số xen kẽ

**Source:** `EvenOddPrinter.java` · **Priority:** P1
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Đề bài & interviewer muốn thấy gì

Thread "odd" in 1, 3, 5…, thread "even" in 2, 4, 6… → output 1..N đúng thứ tự.

- **Trạng thái dùng chung** rõ ràng (`next`) được bảo vệ bởi **một lock**.
- `wait()` trong **`while`**, notify **sau khi** đổi trạng thái.
- **Kết thúc sạch**: không thread nào chờ mãi ở cuối.
- Biết nhiều cách.

### 1.2 Ba lời giải

| Cách | Ý chính | Điểm cần nhớ |
|---|---|---|
| `wait` / `notifyAll` | `while (next <= N && chưa tới lượt) lock.wait()` | Kiểm tra `next > N` **sau khi thức dậy**, rồi `notifyAll()` để thread kia cũng thoát |
| `ReentrantLock` + 2 `Condition` | `oddTurn`, `evenTurn` → `signal()` **đúng thread** | Sau vòng lặp `otherTurn.signal()` để thread kia không kẹt ở `await()` cuối |
| 2 `Semaphore` "chuyền bóng" | `oddPermit(1)`, `evenPermit(0)`; acquire của mình → in → release của bên kia | Đơn giản nhất; acquire/release tạo **happens-before** |

### 1.3 Bẫy hay gặp

- `if` thay vì `while` → spurious wakeup / thức dậy sai lượt.
- Quên đánh thức thread kia lúc kết thúc → **treo ở cuối** (demo `join(2000)` báo `TREO`).
- Demo **cố ý start `even` trước** → lời giải phải đúng bất kể thứ tự start.
- `volatile` + busy-wait → đốt CPU (chỉ chấp nhận với `Thread.onSpinWait()` và chờ cực ngắn).

### 1.4 Mở rộng 3 thread (1, 2, 3, 1, 2, 3…)

`next % 3 == myIndex` với `notifyAll`, hoặc 3 Semaphore xoay vòng.

---

## 2. Interview English

### Q1. Walk me through your wait/notify solution.

**Importance:** 🔴 Important

**Key sentence:** *One shared counter under one lock; wait in a while loop until it's my turn; notifyAll after changing it.*

**Suggested Answer:**
I have one shared variable, `next`, protected by **one lock object**. Each thread loops: inside `synchronized`, it **waits in a `while` loop** while it's not its turn. When it wakes up, it first checks if `next` is past N — if so, it calls `notifyAll` so the other thread can also exit, and returns. Otherwise it prints `next`, increments it, and calls **`notifyAll`** to hand over the turn.

### Q2. Why `while` and not `if` around `wait()`?

**Importance:** 🔴 Important

**Key sentence:** *After waking up, the condition may still be false, so I must check it again.*

**Suggested Answer:**
A thread can wake up when the condition is **still not true** — a **spurious wakeup**, or `notifyAll` woke it but it's still not its turn, or the state changed again before it got the lock. With `if`, it would continue and print out of order. With `while`, it **re-checks the condition** every time it wakes up, so it only proceeds when it's really its turn.

### Q3. How do you make sure both threads terminate?

**Importance:** 🔴 Important

**Key sentence:** *Check the stop condition after waking up and wake the other thread before exiting.*

**Suggested Answer:**
The classic bug is a hang at the end: one thread prints the last number and exits, and the other is still waiting **forever**. So the wait loop also stops when `next` is greater than N, and before returning, the thread calls **`notifyAll`** — or `signal` in the Condition version — so the other thread wakes up, sees the end condition and exits too. My test joins both threads with a **timeout** to catch a hang.

### Q4. What other ways can you solve it?

**Importance:** 🟡 Medium

**Key sentence:** *Lock with two Conditions to signal exactly one thread, or two Semaphores passing the turn.*

**Suggested Answer:**
With a `ReentrantLock`, I can create **two Conditions**, one per thread, and `signal` exactly the thread whose turn it is, instead of waking everyone. With **two Semaphores**, odd starts with one permit and even with zero; each thread acquires its own permit, prints, and **releases the other's** permit — like passing a ball. The semaphore version is the shortest, and acquire and release also give happens-before for visibility.

### Q5. How would you extend it to three threads? Why not just use `volatile` with a busy loop?

**Importance:** 🟢 Low

**Key sentence:** *Use `next % 3 == myIndex`; busy-waiting burns CPU.*

**Suggested Answer:**
For three threads printing in turn, each thread waits while `next % 3` is not its own index, and I use **`notifyAll`**, because `notify` might wake the wrong thread. Or I use three semaphores in a ring. A `volatile` flag with a busy loop works, but it **burns CPU** while spinning. That's only acceptable for very short waits with `Thread.onSpinWait()`.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | One shared state, one lock. |
| 2 | Always wait in a while loop. |
| 3 | Change the state, then notify. |
| 4 | Wake the other thread before exiting, or it hangs. |
| 5 | Two Conditions or two Semaphores also work. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| take turns / alternate | thay phiên |
| shared state | trạng thái dùng chung |
| spurious wakeup | thức dậy không lý do |
| hand over the turn | chuyền lượt |
| busy-wait / spin | chờ bằng vòng lặp |
| terminate cleanly | kết thúc sạch |

## 5. Self-test

1. Viết lại lời giải `wait/notifyAll` trong 10 phút, không treo ở cuối.
2. Vì sao `while` mà không phải `if`?
3. Vì sao demo start `even` trước?
4. Bản Condition: vì sao cần `otherTurn.signal()` sau vòng lặp?
5. Bản Semaphore: permit ban đầu là bao nhiêu? Vì sao visibility vẫn đúng?
6. Mở rộng 3 thread thế nào? Vì sao phải `notifyAll`?
