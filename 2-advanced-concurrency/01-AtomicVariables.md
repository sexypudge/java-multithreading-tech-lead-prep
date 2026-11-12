# 01 — Atomic Variables & CAS

**Source:** `AtomicVariablesDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 volatile không đủ cho `count++`

- `count++` = **3 bước**: đọc → cộng 1 → ghi. Hai thread xen giữa → **lost update**.
- `volatile` lo **visibility + ordering**, **không** lo atomicity.
- Demo 1: `volatile int count++` thiếu hàng chục nghìn lần cộng; `synchronized` và `AtomicInteger` luôn đúng.

### 1.2 CAS (Compare-And-Swap)

Một lệnh CPU nguyên tử: `compareAndSet(expected, newValue)` — "nếu giá trị hiện tại == expected thì đổi sang newValue, trả `true`; ngược lại không làm gì, trả `false`".

```java
do {
    old  = value.get();
    next = f(old);
} while (!value.compareAndSet(old, next));   // có thread chen vào → old đã cũ → thử lại
```

- **Lock-free:** không thread nào bị chặn; thất bại thì **thử lại**.
- `Atomic*` = `volatile` (visibility) + CAS (atomicity).
- `updateAndGet` / `accumulateAndGet` chính là vòng CAS → hàm truyền vào **có thể bị gọi nhiều lần** → không được có side effect.

### 1.3 Tự viết vòng CAS khi API có sẵn không đủ (Demo 2)

`BoundedCounter.tryIncrement()`: chỉ tăng nếu `< limit`. `incrementAndGet()` không kiểm tra điều kiện được → đọc → kiểm tra → CAS → thất bại thì lặp lại. 8000 lượt thử, limit 1000 → luôn đúng 1000.

### 1.4 AtomicLong vs LongAdder (Demo 3)

| | `AtomicLong` | `LongAdder` |
|---|---|---|
| Cách làm | 1 biến, mọi thread CAS cùng chỗ | Chia thành nhiều **cell**, mỗi thread cộng vào cell riêng |
| Tranh chấp cao | CAS thất bại/thử lại liên tục | Nhanh hơn nhiều |
| Đọc | `get()` chính xác | `sum()` **không phải ảnh chụp tức thời** |
| Dùng cho | Giá trị cần chính xác để ra quyết định (sequence, id) | Metric, counter ghi nhiều đọc ít |

Máy 1–2 core có thể thấy kết quả ngược lại (gần như không có tranh chấp thật).

### 1.5 ABA (Demo 4)

- CAS chỉ so **giá trị**. A → B → A thì CAS tưởng "chưa ai đổi".
- Với số nguyên thường vô hại; với **con trỏ** trong stack/queue lock-free có thể làm hỏng cấu trúc.
- Sửa: `AtomicStampedReference` (thêm số phiên bản), `AtomicMarkableReference`.

### 1.6 Atomic vs lock

- Atomic: **một biến**, thao tác đơn giản.
- Bất biến liên quan **nhiều biến** (trừ tài khoản A, cộng tài khoản B) → phải dùng lock.
- Tranh chấp rất cao: CAS quay vòng tốn CPU; lock thì park thread.

---

## 2. Interview English

### Q1. Is `volatile int count; count++` thread-safe?

**Importance:** 🔴 Important

**Key sentence:** *No — volatile gives visibility, not atomicity.*

**Suggested Answer:**
No. `count++` is three steps: **read, add one, write**. `volatile` makes sure every thread sees the latest value, but it doesn't stop two threads from reading the same value and both writing back the same result. So we **lose updates**. To fix it, I use `AtomicInteger.incrementAndGet()` or a `synchronized` block.

### Q2. What is CAS and how does `AtomicInteger` work?

**Importance:** 🔴 Important

**Key sentence:** *CAS updates a value only if it still equals what I read; otherwise I retry.*

**Suggested Answer:**
CAS means compare-and-swap. It's an **atomic CPU instruction**: "if the current value equals the expected value, replace it with the new value". `AtomicInteger` reads the value, computes the new one, and tries CAS. If another thread changed it in between, CAS fails and it **retries**. It's **lock-free**, so no thread is blocked, but under very high contention the retries waste CPU.

### Q3. When do you use atomics and when do you use a lock?

**Importance:** 🔴 Important

**Key sentence:** *Atomics for one variable; locks when an invariant spans several variables.*

**Suggested Answer:**
I use atomics when the state is **a single variable** and the operation is simple, like a counter or a flag. If the rule involves **several variables** — for example, take money from account A and add it to account B — atomics can't make the whole thing atomic, so I use a lock. Atomics are also not magic under heavy contention: many threads retrying CAS on the same variable can be slow.

### Q4. How do you write a custom atomic operation, like "increment only if below a limit"?

**Importance:** 🟡 Medium

**Key sentence:** *Read, check, compareAndSet, and loop on failure.*

**Suggested Answer:**
I write a **CAS loop**. I read the current value, check the condition, and if it's OK, I call `compareAndSet(current, current + 1)`. If CAS fails, another thread changed the value, so I loop and read again. For simple cases, `updateAndGet` or `accumulateAndGet` do this loop for me, but the function **may run more than once**, so it must have **no side effects**.

### Q5. When would you use `LongAdder` instead of `AtomicLong`?

**Importance:** 🟡 Medium

**Key sentence:** *LongAdder is for high-contention counters where an exact instant value isn't needed.*

**Suggested Answer:**
`LongAdder` splits the counter into **several cells**, so different threads update different cells and don't fight over one variable. `sum()` adds them up. It's much faster under high contention, so it's great for **metrics and statistics**. But `sum()` is not an exact snapshot while threads are writing, so I don't use it when I need the exact value to make a decision, like generating IDs.

### Q6. What is the ABA problem?

**Importance:** 🟡 Medium

**Key sentence:** *The value goes A to B and back to A, so CAS thinks nothing changed.*

**Suggested Answer:**
CAS only compares values. If a thread reads A, then another thread changes it to B and back to A, the first thread's CAS still **succeeds**, even though the state changed. For a simple counter it's harmless, but in **lock-free data structures** with pointers it can corrupt the structure. The fix is `AtomicStampedReference`, which adds a **version number** to every change.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | `volatile` gives visibility, not atomicity. |
| 2 | CAS updates a value only if it hasn't changed; otherwise it retries. |
| 3 | Atomics are lock-free but can spin under high contention. |
| 4 | Use a lock when an invariant spans several variables. |
| 5 | `LongAdder` is for metrics; `AtomicLong` when I need the exact value. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| lost update | mất lần cập nhật do ghi đè |
| compare-and-swap (CAS) | so sánh rồi đổi, nguyên tử |
| lock-free | không dùng lock, không chặn thread |
| retry loop / spin | vòng thử lại |
| contention | tranh chấp |
| invariant | bất biến cần giữ |
| side effect | tác dụng phụ |

## 5. Self-test

1. Vì sao `volatile int count; count++` sai? Gồm mấy bước?
2. CAS là gì? Viết vòng lặp CAS bằng tay.
3. Vì sao hàm truyền vào `updateAndGet` không được có side effect?
4. Khi nào phải dùng lock thay vì `Atomic*`?
5. `LongAdder` nhanh hơn nhờ đâu? Khi nào **không** dùng?
6. ABA là gì? Sửa bằng gì? Khi nào nó thật sự gây hại?
