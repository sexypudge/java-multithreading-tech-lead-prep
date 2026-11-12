# 05 — Synchronization (`synchronized`)

**Source:** `SynchronizationDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 synchronized đảm bảo 2 thứ

1. **Mutual exclusion:** tại một thời điểm chỉ 1 thread giữ **một monitor cụ thể**.
2. **Visibility:** unlock monitor M **happens-before** lần lock M tiếp theo → thread sau thấy mọi thay đổi của thread trước.

→ Vì vậy **getter cũng phải `synchronized`**, không chỉ setter.

### 1.2 Ba dạng — quan trọng nhất là LOCK TRÊN OBJECT NÀO

| Dạng | Lock trên |
|---|---|
| `synchronized` instance method | `this` |
| `synchronized (lock) { }` | object `lock` |
| `static synchronized` method | `Foo.class` |

### 1.3 Ai chặn ai? (Demo 3: ~600ms = chặn, ~300ms = không chặn)

| Tình huống | Chặn nhau? |
|---|---|
| 2 thread, **cùng** instance | **Có** |
| 2 thread, **khác** instance | Không (2 monitor khác nhau) |
| `static synchronized` vs instance `synchronized` | Không (`Foo.class` ≠ `this`) |

⚠ **Bẫy:** method static và method instance cùng sửa một field static → 2 lock khác nhau bảo vệ cùng dữ liệu = **không thread-safe**.

### 1.4 Method vs block

- Method: lock cả method, lock `this`/`Class`.
- Block: chọn **phạm vi** và **lock object** → thu hẹp critical section, dùng lock `private final` (code bên ngoài không lock nhầm được).
- Block **không tự động nhanh hơn** — chỉ có lợi khi thực sự giảm thời gian giữ lock.

### 1.5 Reentrancy

Thread đang giữ monitor có thể vào lại code `synchronized` trên **cùng** monitor (JVM đếm số lần giữ). Vd `outerMethod()` gọi `innerMethod()` — không tự deadlock.

### 1.6 Edge cases

- `synchronized(null)` → `NullPointerException`.
- Exception trong khối `synchronized` → monitor **vẫn tự nhả** (khác `Lock` quên `unlock()`).
- **Đừng lock trên:** field không `final` (bị gán lại = đổi lock), `String` literal hay `Integer` cache (dùng chung toàn JVM), object public (người khác lock nhầm).

### 1.7 Hiệu năng

- Không tranh chấp: **rất rẻ**. Chậm là do **contention**: chờ, park/unpark, context switch.
- Giảm: critical section ngắn, không I/O khi giữ lock, tách lock, hoặc dùng `Atomic*`, `ConcurrentHashMap`, `LongAdder`.
- Thay thế: `ReentrantLock`, `ReadWriteLock`, `StampedLock` (xem `10-ExplicitLock.md`), `Atomic*`, concurrent collections, immutable objects.

---

## 2. Interview English

### Q1. How does `synchronized` work?

**Importance:** 🔴 Important

**Key sentence:** *`synchronized` gives mutual exclusion and visibility on the same monitor.*

**Suggested Answer:**
Every Java object has a monitor lock. Before a thread enters a `synchronized` block, it must **acquire that lock**, and other threads that need the same lock must wait. `synchronized` also guarantees **visibility**: when a thread releases the lock, the next thread that takes it sees all the changes. That's why the getter must be synchronized too.

### Q2. What does each form of `synchronized` lock on?

**Importance:** 🔴 Important

**Key sentence:** *Instance methods lock `this`, static methods lock the Class object, blocks lock the object you choose.*

**Suggested Answer:**
A synchronized instance method locks `this`. A static synchronized method locks the `Class` object. A synchronized block locks the object in the parentheses. The most important thing is to know **which object is the lock**, because only code using the **same lock** blocks each other.

### Q3. Synchronized method vs synchronized block?

**Importance:** 🔴 Important

**Key sentence:** *A block lets me choose the lock and keep the critical section small.*

**Suggested Answer:**
A synchronized method locks the whole method on `this`. A block lets me protect only the part that needs it, and use a **private final lock object** that outside code can't use by mistake. But a block is **not automatically faster**. It only helps if it really reduces the time the lock is held.

### Q4. Do two threads block each other on different instances? Static vs instance?

**Importance:** 🔴 Important

**Key sentence:** *No — different objects have different locks.*

**Suggested Answer:**
No. Each instance has its own monitor, so threads using different instances run at the same time. A static synchronized method and an instance synchronized method also don't block each other, because one locks the `Class` object and the other locks `this`. This is a common bug: if both methods change the same static field, the field is **not protected**.

### Q5. What does reentrant mean?

**Importance:** 🟡 Medium

**Key sentence:** *A thread can acquire a lock it already holds.*

**Suggested Answer:**
Reentrant means a thread that already holds a lock can **take it again** without deadlocking itself. For example, one synchronized method can call another synchronized method on the same object. The JVM counts how many times the thread holds the lock and releases it when the count goes back to zero.

### Q6. Is `synchronized` slow?

**Importance:** 🟡 Medium

**Key sentence:** *It's cheap without contention; contention is what makes it slow.*

**Suggested Answer:**
Without contention, `synchronized` is very cheap because the JVM optimizes it. It becomes slow when **many threads compete** for the same lock: they wait, get parked, and cause context switches. To reduce this, I keep critical sections short, avoid I/O while holding a lock, and use classes like `AtomicInteger` or `ConcurrentHashMap` when they fit.

### Q7. What are some edge cases of `synchronized`?

**Importance:** 🟢 Low

**Key sentence:** *Never lock on null, a String literal, or a field that can change.*

**Suggested Answer:**
`synchronized(null)` throws a `NullPointerException`. If an exception is thrown inside a synchronized block, the lock is **released automatically**. I avoid locking on a non-final field, because if it changes, threads lock different objects. I also avoid String literals and public objects, because other code might lock on them too.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | `synchronized` gives mutual exclusion and visibility. |
| 2 | Instance methods lock `this`; static methods lock the Class object. |
| 3 | Only code using the same lock blocks each other. |
| 4 | Intrinsic locks are reentrant. |
| 5 | `synchronized` is cheap without contention; keep critical sections short. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| acquire / release a lock | lấy / nhả lock |
| monitor (intrinsic lock) | lock gắn với mỗi object |
| mutual exclusion | loại trừ lẫn nhau |
| critical section | đoạn code cần bảo vệ |
| lock contention | tranh chấp lock |
| reentrant | vào lại được lock đang giữ |
| context switch | chuyển ngữ cảnh giữa thread |

## 5. Self-test

1. `synchronized` đảm bảo 2 thứ gì? Vì sao getter cũng cần `synchronized`?
2. 3 dạng `synchronized` lock trên object nào?
3. 2 thread, 2 instance khác nhau — có chặn nhau không? static vs instance?
4. Bẫy static field bị sửa từ cả method static và instance là gì?
5. Method vs block? Block có luôn nhanh hơn không?
6. Reentrant là gì? Cho ví dụ.
7. `synchronized(null)`? Exception trong khối `synchronized`?
8. Vì sao không lock trên `String` literal hay field không final?
9. `synchronized` chậm khi nào? Giảm contention thế nào?
