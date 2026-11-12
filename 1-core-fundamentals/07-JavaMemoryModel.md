# 07 — Java Memory Model (JMM) & Happens-Before

**Source:** `JavaMemoryModelDemo.java` (học sau `VolatileDemo.java`)
**Goal:** trả lời được mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây** (4–5 câu ngắn).
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge (đọc hiểu, không cần học thuộc)

### 1.1 Vì sao cần JMM?

JIT và CPU được phép tối ưu, miễn là kết quả **trong một thread** không đổi. Với nhiều thread, việc này gây ra 2 vấn đề:

- **Visibility:** thread A ghi, nhưng thread B **không thấy** (giá trị nằm trong cache/thanh ghi). → `VolatileDemo` Demo 1
- **Ordering:** lệnh bị **đổi thứ tự**, nên thread khác thấy các lần ghi theo thứ tự khác với code. → Demo 3

**Happens-before (HB):** nếu A HB B thì B **chắc chắn thấy** mọi thứ A đã ghi. Đây là quy tắc logic, không phải thời gian thực.

### 1.2 Các quy tắc happens-before

| Quy tắc | Nội dung |
|---|---|
| Program order | Trong 1 thread, lệnh trước HB lệnh sau |
| Lock | Unlock HB lần lock tiếp theo trên **cùng** lock |
| Volatile | Ghi volatile HB lần đọc tiếp theo của **cùng** biến |
| Thread start | `t.start()` HB mọi lệnh trong `t` |
| Thread join | Mọi lệnh trong `t` HB lúc `t.join()` trả về |
| Transitivity | A HB B, B HB C ⇒ A HB C |

### 1.3 Safe publication (Demo 1)

**Publish** = đưa một object/dữ liệu cho thread khác dùng. **Safe** = thread kia thấy dữ liệu **đầy đủ**, không thấy object làm dở.

```java
int data = 0;
volatile boolean ready = false;

// Writer                    // Reader
data = 42;      // (1)       while (!ready) { }   // (3)
ready = true;   // (2)       return data;         // (4) chắc chắn thấy 42
```

(1)→(2) program order, (2)→(3) volatile, (3)→(4) program order ⇒ (1) HB (4). Nếu ghi `ready` **trước** `data` thì mất đảm bảo.

**4 cách publish an toàn:** `volatile` field · `final` field · `synchronized`/lock · đưa qua concurrent collection (`ConcurrentHashMap`, `BlockingQueue`) · (+ static initializer).

### 1.4 Vì sao DCL cần `volatile`

`instance = new Singleton()` gồm 3 bước: **cấp phát → chạy constructor → gán reference**. Bước 2 và 3 có thể bị đổi chỗ:

```
Thread A (trong synchronized)       Thread B (check đầu tiên, NGOÀI synchronized)
1. cấp phát
3. instance = <địa chỉ>  ──────►    instance != null → return instance
2. constructor (chưa xong!)         → dùng object làm dở ✗
```

`volatile` cấm đổi chỗ này và tạo HB giữa "constructor xong" và "B đọc instance".
**Thực tế:** dùng **holder idiom** hoặc **enum** — đơn giản, không thể viết sai.

> Lưu ý: bug này gần như không tái hiện được trên x86 + JVM hiện đại. Nó là "sai theo JMM", không phải "hay chạy ra lỗi".

### 1.5 Final field & `this` escape (Demo 5)

Thread nào thấy object **đã khởi tạo xong** thì chắc chắn thấy giá trị đúng của field `final`. Điều kiện: constructor **không được để lộ `this`** (đăng ký listener, start thread, gán vào biến static).
`final` chỉ khoá reference — **không** làm object bên trong trở thành immutable.

### 1.6 Data race vs race condition

- **Data race:** 2 thread dùng chung 1 biến, ít nhất 1 thread ghi, **không có HB**.
- **Race condition:** kết quả **sai** vì phụ thuộc thứ tự chạy của thread. Có thể xảy ra **dù không có data race** (vd: `ConcurrentHashMap` `get` rồi `put`).

---

## 2. Interview English

> "volatile vs synchronized" và "volatile counter vẫn race": xem `06-Volatile.md` Q2 và Q4.

Mỗi câu có **Key sentence** (nhớ câu này trước) và **Suggested Answer** (nói trong 30–45 giây).

### Q1. What is the Java Memory Model?

**Importance:** 🔴 Important

**Key sentence:** *The JMM defines when one thread can see changes made by another thread.*

**Suggested Answer:**
The Java Memory Model defines how threads share memory. It solves two problems. The first is **visibility**: one thread changes a value, but another thread may not see it. The second is **ordering**: the compiler and CPU can change the order of instructions. The JMM uses **happens-before rules** to say when a change is guaranteed to be visible.

### Q2. What is happens-before? Give examples.

**Importance:** 🔴 Important

**Key sentence:** *If A happens-before B, B is guaranteed to see everything A did.*

**Suggested Answer:**
Happens-before is a rule that guarantees visibility and ordering between two actions. If A happens-before B, then B can see all changes made by A. Common examples are: unlocking a lock happens-before the next lock on the same lock, a volatile write happens-before a later read of that variable, and `start()` and `join()` also create happens-before. The rule is also **transitive**.

### Q3. What is safe publication?

**Importance:** 🔴 Important

**Key sentence:** *Safe publication means other threads see a fully constructed object, not a half-built one.*

**Suggested Answer:**
Publishing an object means making it available to other threads. If we publish it without synchronization, another thread may see the reference but **old or default values** inside it. To publish safely, we need a happens-before relationship. Common ways are: a `volatile` or `final` field, a `synchronized` block, or a concurrent collection like `ConcurrentHashMap` or `BlockingQueue`.

### Q4. Why does double-checked locking need `volatile`?

**Importance:** 🔴 Important

**Key sentence:** *Without volatile, another thread can see the object before the constructor finishes.*

**Suggested Answer:**
Creating an object has three steps: allocate memory, run the constructor, and assign the reference. The JVM can **reorder** the last two steps. So another thread may see a non-null instance and use it before it is fully built. `volatile` **prevents this reordering**. In practice, I prefer the **holder class** or an **enum**, because they are simpler and safer.

### Q5. Why is letting `this` escape in a constructor dangerous?

**Importance:** 🟡 Medium

**Key sentence:** *If `this` escapes, other threads can see the object before it is ready — even its final fields.*

**Suggested Answer:**
Java guarantees that final fields are visible correctly, but only after the constructor finishes. If the constructor gives `this` to another thread — for example, by registering a listener or starting a thread — that thread can use the object too early. It may see **default values**, even in final fields. So I publish the object only after construction, for example with a **static factory method**.

```java
class Example {
    final int value;
    Example() {
        Registry.register(this); // ✗ 'this' escapes before value is set
        value = 100;
    }
}
```

### Q6. What is the difference between a data race and a race condition?

**Importance:** 🟡 Medium

**Key sentence:** *A data race is a memory problem; a race condition is a logic problem.*

**Suggested Answer:**
A data race happens when two threads access the same variable, at least one writes, and there is **no synchronization**. A race condition happens when the result depends on the **timing** of threads. They are different. For example, with `ConcurrentHashMap`, calling `get` and then `put` has no data race, but it is still a race condition. The fix is an **atomic method** like `computeIfAbsent`.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | The JMM solves two problems: visibility and ordering. |
| 2 | If A happens-before B, B sees everything A did. |
| 3 | Safe publication means other threads see a fully built object. |
| 4 | DCL needs volatile to stop reordering — but holder or enum is simpler. |
| 5 | A data race is a memory problem; a race condition is a logic problem. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| visibility | thread khác có thấy thay đổi hay không |
| reorder instructions | đổi thứ tự lệnh |
| happens-before | quan hệ đảm bảo "thấy được" giữa 2 hành động |
| publish an object | đưa object cho thread khác dùng |
| safe publication | publish mà thread khác thấy đủ dữ liệu |
| half-built / partially constructed object | object khởi tạo dở |
| `this` escape | để lộ `this` khi constructor chưa xong |
| default values | giá trị mặc định (0, null, false) |

## 5. Self-test

1. Hai vấn đề chính JMM giải quyết là gì? Cho ví dụ mỗi loại.
2. Kể ≥ 5 quy tắc happens-before.
3. Vẽ chuỗi HB trong ví dụ `data` + `volatile ready`. Nếu đổi thứ tự 2 lệnh ghi thì sao?
4. Safe publication là gì? Kể 4 cách.
5. Vì sao DCL cần `volatile`? Thay thế nào đơn giản hơn?
6. `final` đảm bảo gì, không đảm bảo gì? `this` escape là gì?
7. Race condition khác data race thế nào?
8. "volatile ép đọc/ghi từ main memory" — câu này chính xác tới đâu? (gợi ý: cách nói đơn giản hoá; chính xác là HB + memory barrier, cache vẫn dùng nhưng được đồng bộ)
