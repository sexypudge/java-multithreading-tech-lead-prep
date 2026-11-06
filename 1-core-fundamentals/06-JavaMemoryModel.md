# 06 — Java Memory Model (JMM) & Happens-Before

**Source:** `JavaMemoryModelDemo.java` (+ `VolatileDemo.java`)
**Goal:** giải thích được visibility, reordering, happens-before và vì sao DCL cần `volatile` — bằng tiếng Anh, 60–90 giây mỗi câu.

---

## 1. Technical Knowledge

### 1.1 Vì sao cần JMM?

Compiler (JIT) và CPU được phép tối ưu miễn là **kết quả trong một thread** không đổi:

- **Visibility:** giá trị có thể nằm trong thanh ghi/cache; JIT có thể hoist việc đọc ra khỏi vòng lặp → thread khác ghi nhưng thread này **không bao giờ thấy** (Demo 1, `VolatileDemo` Demo 1).
- **Ordering:** lệnh có thể bị sắp xếp lại; store buffer của CPU làm thứ tự ghi nhìn từ thread khác khác thứ tự trong code (Demo 4 — litmus test, quan sát được cả trên x86).

JMM định nghĩa **happens-before (HB)**: nếu A HB B thì mọi thay đổi do A (và trước A) tạo ra đều visible với B, và A không bị reorder ra sau B. HB là quan hệ **logic**, không phải thời gian thực.

### 1.2 Các quy tắc happens-before

| Quy tắc | Nội dung |
|---|---|
| Program order | Trong 1 thread, lệnh trước HB lệnh sau |
| Monitor lock | Unlock monitor M HB lần lock M tiếp theo |
| Volatile | Ghi volatile V HB mọi lần đọc V sau đó (thấy giá trị đó) |
| Thread start | `t.start()` HB mọi lệnh trong `t` |
| Thread join | Mọi lệnh trong `t` HB lúc `t.join()` trả về |
| Transitivity | A HB B, B HB C ⇒ A HB C |
| (bổ sung) | Final field sau constructor, static initializer, các tiện ích `java.util.concurrent` (put vào BlockingQueue HB take...) |

### 1.3 Safe publication bằng volatile (Demo 2)

```java
int data = 0;
volatile boolean ready = false;

// Writer                    // Reader
data = 42;      // (1)       while (!ready) { }   // (3) volatile read
ready = true;   // (2)       return data;         // (4) chắc chắn thấy 42
```

(1) HB (2) — program order; (2) HB (3) — volatile rule; (3) HB (4) — program order ⇒ (1) HB (4) nhờ transitivity. Đổi thứ tự (1)/(2) trong writer là mất đảm bảo.

### 1.4 Vì sao DCL cần `volatile`

`instance = new DclSingleton();` về logic gồm 3 bước:

```
1. allocate   : cấp phát bộ nhớ
2. construct  : chạy constructor (gán field)
3. assign     : gán reference vào 'instance'
```

Trong một thread, đổi thành `1 → 3 → 2` không làm kết quả khác, nên được phép reorder. Nhưng thread khác thì thấy:

```
Thread A (trong synchronized)       Thread B (check đầu tiên, NGOÀI synchronized)
1. allocate
3. instance = <địa chỉ>  ──────►    if (instance == null) → false
2. construct (chưa xong!)           return instance; → dùng object khởi tạo dở
```

Check đầu tiên của B nằm ngoài `synchronized` nên **không có HB** giữa "A khởi tạo xong" và "B đọc" → **unsafe publication**.

Khi `instance` là `volatile`:
- **Cấm reorder:** các ghi trước volatile write (bước 2) không được trôi ra sau nó.
- **Tạo HB:** `(constructor ghi field) HB (volatile write instance) HB (B đọc instance) HB (B dùng field)`.

> **Lưu ý để không bị hỏi vặn:** trong `JavaMemoryModelDemo`, `value` là `final`, nên nhờ final-field semantics field đó vẫn an toàn kể cả thiếu volatile. Ví dụ kinh điển dùng field **không** final. Bug này gần như không tái hiện được trên x86 + JVM hiện đại — nó là "code sai theo JMM", không phải "code hay chạy ra lỗi".
>
> **Thực tế:** ưu tiên **holder idiom** hoặc **enum singleton** — đơn giản hơn và không thể viết sai.

### 1.5 Final field & `this` escape (Demo 6)

Final field có **initialization safety**: thread nào thấy reference tới object **đã khởi tạo đúng cách** thì thấy giá trị final đã gán, kể cả khi publish qua data race. Điều kiện: `this` **không thoát ra** trong constructor (đăng ký listener, start thread, gán vào static field...). `final` chỉ khoá reference — không làm object được trỏ tới immutable.

### 1.6 Race condition vs data race

- **Data race:** 2 thread truy cập cùng biến, ≥ 1 ghi, không có HB — khái niệm của JMM.
- **Race condition:** lỗi logic do thứ tự thực thi — có thể xảy ra dù không có data race (`ConcurrentHashMap` get rồi put).

---

## 2. Interview English

### Q1. What is the difference between `volatile` and `synchronized`?

`volatile` guarantees visibility and ordering for single reads and writes, but it doesn't provide mutual exclusion, so compound operations like `counter++` are still not atomic. `synchronized` provides mutual exclusion plus visibility: only one thread at a time can execute code guarded by the same monitor, and everything done before the unlock is visible to the next thread that locks it. I use `volatile` for simple state like a shutdown flag, and `synchronized` when several operations must happen atomically.

### Q2. Give an example where `volatile` alone causes a race condition.

A volatile counter incremented by several threads. `counter++` is read, increment, write — two threads can read the same value and one update is lost. `volatile` makes the latest value visible, but it can't make the three steps atomic. I'd use `AtomicInteger` or `synchronized` instead.

### Q3. What is the Java Memory Model and what problems does it solve?

The JMM defines how threads interact through shared memory. It addresses two problems: visibility — a thread might not see another thread's write, because values can be cached or reads hoisted out of loops — and ordering — the compiler and CPU can reorder instructions for performance. The JMM defines happens-before rules that tell us exactly when a write is guaranteed to be visible to another thread.

### Q4. What is a happens-before relationship? Give examples.

If action A happens-before action B, then B is guaranteed to see the effects of A, and A is ordered before B. The main rules are: program order within a thread; unlocking a monitor happens-before a later lock of the same monitor; a volatile write happens-before a later read of the same variable; `Thread.start()` happens-before the started thread's actions; a thread's actions happen-before another thread returns from `join()`; and it's transitive. For example, if I write normal data and then set a volatile `ready` flag, a reader that sees `ready == true` is guaranteed to see the data too.

### Q5. Why does double-checked locking require `volatile`?

Because creating the object and assigning the reference can be reordered. Without `volatile`, another thread can pass the first, unsynchronized null check, see a non-null reference, and use an object whose constructor hasn't finished. Declaring the field `volatile` forbids that reordering and creates a happens-before edge between finishing the constructor and another thread reading the reference. In practice I prefer the holder-class idiom or an enum, which are simpler and can't be written incorrectly.

### Q6. Why can letting `this` escape during construction break final-field safety?

Final fields get special initialization safety, but only if the object is properly constructed. If the constructor publishes `this` — registering a listener, starting a thread — another thread can access the object before the constructor finishes and observe default values, even in final fields. So the object should only be published after construction completes, for example through a static factory method.

```java
class Example {
    final int value;
    Example() {
        Registry.register(this); // ✗ unsafe: 'this' escapes before value is assigned
        value = 100;
    }
}
```

---

## 3. Key Vocabulary

| Expression | Meaning |
|---|---|
| visibility | khả năng thấy thay đổi của thread khác |
| instruction reordering | sắp xếp lại lệnh |
| hoist a read out of a loop | đưa việc đọc ra ngoài vòng lặp |
| happens-before edge | cạnh/quan hệ happens-before |
| safe publication | công bố object an toàn |
| half-constructed object | object khởi tạo dở |
| initialization safety | an toàn khởi tạo (final field) |
| `this` escape | để lộ `this` khi constructor chưa xong |
| data race / race condition | (xem 1.6) |

## 4. Self-test

1. Hai vấn đề chính JMM giải quyết là gì? Cho ví dụ mỗi loại.
2. Kể ≥ 5 quy tắc happens-before.
3. Vẽ chuỗi HB trong ví dụ `data` + `volatile ready`. Nếu đổi thứ tự 2 lệnh ghi thì sao?
4. Vì sao DCL cần `volatile`? Thay thế nào đơn giản hơn?
5. `final` đảm bảo gì, không đảm bảo gì? `this` escape là gì?
6. Race condition khác data race thế nào?
7. "volatile ép đọc/ghi từ main memory" — câu này chính xác tới đâu?
