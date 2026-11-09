# 13 — ThreadLocal

**Source:** `ThreadLocalDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.

---

## 1. Technical Knowledge

### 1.1 ThreadLocal là gì?

Mỗi thread có **một bản sao riêng** của giá trị → không chia sẻ nên **không cần lock** (kỹ thuật **thread confinement**).

**Lưu ở đâu?** Trong field `ThreadLocalMap` của **chính Thread** đó: key = ThreadLocal (**weak** reference), value = **strong** reference. Không phải một map toàn cục.

### 1.2 Use cases

- Object **không thread-safe** và tốn kém khi tạo: `SimpleDateFormat`, buffer. (Demo 1: dùng chung → lỗi parse; ThreadLocal → 0 lỗi. Thực tế: dùng `DateTimeFormatter` — immutable, khỏi cần ThreadLocal.)
- **Context theo request:** userId, traceId (MDC của SLF4J), `SecurityContext` (Spring Security), connection của `@Transactional`.

### 1.3 Bẫy lớn nhất: thread pool

Thread trong pool sống lâu và được **tái sử dụng**. Quên `remove()`:
- **Rò dữ liệu:** request sau đọc được giá trị của request trước (Demo 3: sai user, lộ thông tin).
- **Memory leak:** value bị giữ mãi vì thread không chết (vd giữ reference tới ClassLoader khi redeploy).

**Quy tắc:** `set()` trong `try`, `remove()` trong `finally`; khai báo `static final`.

```java
CURRENT_USER.set("alice");
try {
    service();
} finally {
    CURRENT_USER.remove();
}
```

### 1.4 InheritableThreadLocal

- Giá trị được **copy lúc tạo thread con**.
- Với pool: worker tạo một lần, giữ **bản cũ** → **không** dùng để truyền context qua pool.
- Truyền context sang pool: bọc Runnable (copy MDC/SecurityContext thủ công), Spring `TaskDecorator`, `DelegatingSecurityContextExecutor`, hoặc TransmittableThreadLocal.

### 1.5 Virtual threads

Dùng được, nhưng hàng triệu virtual thread × mỗi thread một bản sao → **tốn RAM**. **ScopedValue** (chính thức từ Java 25) là lựa chọn bất biến, rẻ hơn.

### 1.6 Khi nào tránh?

Dùng như "biến global ẩn" → khó test, khó theo dõi luồng dữ liệu.

---

## 2. Interview English

### Q1. What is ThreadLocal and when do you use it?

**Key sentence:** *ThreadLocal gives each thread its own copy of a value, so no locking is needed.*

**Suggested Answer:**
`ThreadLocal` gives **each thread its own copy** of a variable, so threads don't share it and don't need locks. I use it for two things: objects that are **not thread-safe** and expensive to create, like `SimpleDateFormat`, and **request context**, like the user ID or trace ID. Spring uses it for `SecurityContext` and transactions.

### Q2. Where is the ThreadLocal value stored?

**Key sentence:** *In a map inside each Thread object, not in a global map.*

**Suggested Answer:**
Each `Thread` object has a field called `ThreadLocalMap`. The key is the `ThreadLocal` object, held by a **weak reference**, and the value is held by a **strong reference**. So the data lives with the thread, not in a shared global map.

### Q3. How can ThreadLocal cause a memory leak?

**Key sentence:** *In a thread pool, threads never die, so values stay forever unless you call `remove()`.*

**Suggested Answer:**
In a thread pool, threads live for a long time and are **reused**. The key is weak, but the value is **strong**, so if I forget `remove()`, the value stays in memory as long as the thread lives. Worse, the **next request** on the same thread can read the old value, for example another user's data. So I always call `remove()` in a `finally` block.

### Q4. Does ThreadLocal work with `@Async` or a thread pool?

**Key sentence:** *No — the value doesn't move to another thread automatically.*

**Suggested Answer:**
No. `ThreadLocal` values like `SecurityContext` or the MDC trace ID **stay on the original thread**. When work moves to another thread, the value is missing. `InheritableThreadLocal` doesn't help with pools, because it copies only when a thread is created. I use a Spring `TaskDecorator` or wrap the task to **copy the context** manually.

### Q5. Is ThreadLocal a good fit for virtual threads?

**Key sentence:** *It works, but it can use a lot of memory — ScopedValue is better.*

**Suggested Answer:**
It works, but we can have **millions** of virtual threads, and each one has its own copy. That can use a lot of memory. Java now has **`ScopedValue`**, which is immutable and cheaper for passing context. For new code with virtual threads, I'd consider `ScopedValue`.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | ThreadLocal gives each thread its own copy — no locking needed. |
| 2 | Always call `remove()` in a `finally` block. |
| 3 | In a thread pool, a forgotten value leaks to the next request. |
| 4 | ThreadLocal context doesn't move to another thread automatically. |
| 5 | For virtual threads, consider ScopedValue. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| thread confinement | giữ dữ liệu trong một thread |
| its own copy | bản sao riêng |
| request context | thông tin theo request |
| weak / strong reference | tham chiếu yếu / mạnh |
| memory leak | rò rỉ bộ nhớ |
| propagate context | truyền context sang thread khác |
| reuse threads | tái sử dụng thread |

## 5. Self-test

1. ThreadLocal là gì? Vì sao không cần lock?
2. Giá trị ThreadLocal được lưu ở đâu? Key/value là weak hay strong?
3. Kể 2 nhóm use case.
4. Quên `remove()` trong thread pool gây 2 vấn đề gì?
5. Quy tắc an toàn khi dùng ThreadLocal?
6. InheritableThreadLocal có dùng được với pool không? Vì sao?
7. Làm sao truyền SecurityContext/MDC sang `@Async`?
8. ThreadLocal với virtual threads có vấn đề gì?
