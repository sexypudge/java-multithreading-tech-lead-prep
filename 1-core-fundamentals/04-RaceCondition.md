# 04 — Race Condition

**Source:** `RaceConditionDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Race condition là gì?

Kết quả **phụ thuộc thứ tự chạy** của các thread. Xảy ra khi: nhiều thread dùng **shared mutable state**, ít nhất 1 thread ghi, và **không đồng bộ đúng**.

### 1.2 Hai mẫu kinh điển

| Mẫu | Ví dụ | Lỗi |
|---|---|---|
| **Read-Modify-Write** | `count++`, `balance -= x` | 2 thread đọc cùng giá trị → **lost update** |
| **Check-Then-Act** | `if (instance == null) instance = new X()` | Điều kiện đã kiểm tra **sai** khi hành động |

`count++` = 3 bước: **đọc → cộng → ghi** (bytecode `getfield → iadd → putfield`, xem `javap -c`).

### 1.3 Ba demo trong code

| Demo | Unsafe | Fix |
|---|---|---|
| Counter (8 thread × 100k) | Mất update | `synchronized` hoặc `AtomicInteger` (CAS, lock-free) |
| Lazy singleton | Tạo nhiều instance | **Holder idiom** (đơn giản nhất), `synchronized getInstance()`, DCL + `volatile`, enum |
| Rút tiền (150 thread × 10, số dư 1000) | Rút quá số dư | **Check + act trong CÙNG một `synchronized`** |

Demo dùng **start gate** (`CountDownLatch`) cho mọi thread chạy cùng lúc, và `yield()` để nới rộng cửa sổ race. Production cửa sổ nhỏ hơn — lỗi **hiếm nhưng vẫn có**.

### 1.4 Race condition vs data race

- **Data race:** 2 thread dùng cùng biến, ≥1 ghi, **không có happens-before** — khái niệm của JMM.
- **Race condition:** lỗi **logic** do timing. Có thể xảy ra **dù không có data race** (vd `ConcurrentHashMap` `get()` rồi `put()`).

### 1.5 Fix theo thứ tự ưu tiên

1. Bỏ shared mutable state (immutable, thread confinement)
2. Thao tác atomic có sẵn (`AtomicInteger`, `ConcurrentHashMap.merge/computeIfAbsent`)
3. Lock bao trọn cả chuỗi check + act

**Nhiều instance service:** lock trong JVM vô dụng → cần DB constraint, optimistic lock, distributed lock.

### 1.6 Phát hiện

Stress test nhiều thread + start gate, chạy lặp nhiều lần · code review tìm shared mutable state · `jcstress` cho test JMM. **Không thấy lỗi ≠ code đúng.**

---

## 2. Interview English

### Q1. What is a race condition?

**Importance:** 🔴 Important

**Key sentence:** *A race condition happens when the result depends on the timing of threads.*

**Suggested Answer:**
A race condition happens when several threads use shared data, at least one of them writes, and there is **no proper synchronization**. The result then depends on the order in which the threads run. So the program may work most of the time, but **sometimes give wrong results**.

### Q2. Why is `count++` not thread-safe?

**Importance:** 🔴 Important

**Key sentence:** *`count++` is three steps — read, add, write — not one.*

**Suggested Answer:**
`count++` looks like one operation, but it has three steps: read the value, add one, and write it back. Two threads can read the **same old value**, both add one, and both write the same result. One update is lost. This is called a **lost update**. I fix it with `AtomicInteger` or `synchronized`.

### Q3. What are the two common patterns of race conditions?

**Importance:** 🔴 Important

**Key sentence:** *Read-modify-write and check-then-act.*

**Suggested Answer:**
The first is **read-modify-write**, like `count++`, where two threads overwrite each other's updates. The second is **check-then-act**, like lazy initialization: two threads both see `instance == null` and both create an object. In both cases, the steps must be done **atomically**, as one unit.

### Q4. How do you fix the bank withdrawal race?

**Importance:** 🟡 Medium

**Key sentence:** *The check and the update must be inside the same lock.*

**Suggested Answer:**
The problem is that checking the balance and withdrawing are two separate steps. Another thread can change the balance between them, so the account can go **below zero**. I put both steps inside **the same synchronized block**, so no other thread can run in between. If the service has several instances, I need a database constraint or optimistic locking instead.

### Q5. Race condition vs data race?

**Importance:** 🟡 Medium

**Key sentence:** *A data race is a memory problem; a race condition is a logic problem.*

**Suggested Answer:**
A data race means two threads access the same variable, at least one writes, and there is **no synchronization**. A race condition means the result is wrong because of **timing**. You can have a race condition without a data race. For example, calling `get()` then `put()` on a `ConcurrentHashMap` is safe for memory, but the logic is still wrong. I'd use `computeIfAbsent` instead.

### Q6. How do you prevent and detect race conditions?

**Importance:** 🟡 Medium

**Key sentence:** *Avoid shared mutable state first; lock only when needed.*

**Suggested Answer:**
First, I try to **avoid shared mutable state** with immutable objects or thread confinement. Next, I use atomic classes or concurrent collections. If that's not enough, I use a lock around the whole compound operation. To detect races, I use **stress tests** with many threads starting together, and code review. But passing tests **don't prove** the code is correct.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | A race condition happens when the result depends on thread timing. |
| 2 | `count++` is read, add, write — two threads can lose an update. |
| 3 | The two classic patterns are read-modify-write and check-then-act. |
| 4 | Check and act must be inside the same lock. |
| 5 | Avoid shared mutable state first; lock last. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| shared mutable state | dữ liệu dùng chung có thể thay đổi |
| lost update | mất lần cập nhật |
| read-modify-write | đọc – sửa – ghi |
| check-then-act | kiểm tra rồi hành động |
| atomically / as one unit | như một khối không chia cắt |
| compound operation | thao tác gồm nhiều bước |
| stress test | test với tải cao, nhiều thread |

## 5. Self-test

1. Race condition xảy ra khi nào (3 điều kiện)?
2. `count++` gồm mấy bước? Vẽ interleaving gây mất update.
3. Kể 2 mẫu race condition, mỗi mẫu 1 ví dụ.
4. Kể 4 cách fix lazy singleton. Cách nào đơn giản nhất?
5. Vì sao rút tiền phải check và trừ trong cùng 1 lock?
6. Race condition khác data race? Cho ví dụ có race condition nhưng không có data race.
7. Service chạy nhiều instance thì `synchronized` còn đủ không?
8. Làm sao tái hiện race condition trong test?
