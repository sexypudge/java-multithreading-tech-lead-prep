# 14 — Immutability

**Source:** `ImmutabilityDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.

---

## 1. Technical Knowledge

### 1.1 Vì sao immutable?

Trạng thái **không đổi sau khi tạo** → chia sẻ tự do giữa các thread **không cần lock**. Ví dụ: `String`, `Integer`, `LocalDate`, `BigDecimal`, `List.of()`, `record`.

Nhờ **final field semantics**, thread nào thấy reference cũng thấy đầy đủ giá trị đã khởi tạo (miễn `this` không thoát trong constructor).

### 1.2 Năm quy tắc tạo immutable class

1. Class `final` (hoặc constructor private) → không bị subclass phá
2. Mọi field `private final`
3. Không có setter — "sửa" = **tạo object mới** (`plus()`, `withX()`)
4. **Defensive copy** object mutable **nhận vào** constructor (copy trước, kiểm tra **sau** trên bản copy)
5. **Defensive copy** object mutable **trả ra** từ getter
(+ không để `this` thoát ra trong constructor)

### 1.3 `final` ≠ immutable

`final` chỉ khoá **reference**, không khoá nội dung. `final List<String> l` vẫn `add()` được; `final Date d` vẫn `setTime()` được (Demo 2: `BadPeriod` bị phá dù field là final). Tốt hơn: dùng `Instant`/`LocalDate` (đã immutable).

### 1.4 record

**Shallow immutable:** field final, nhưng component là `List`/`Date` mutable thì vẫn bị sửa từ ngoài. Sửa bằng compact constructor: `this.items = List.copyOf(items)`.

### 1.5 View vs copy

| | `Collections.unmodifiableList(src)` | `List.copyOf(src)` |
|---|---|---|
| Bản chất | **View** — cửa sổ nhìn vào `src` | **Copy** thực sự |
| `src` thay đổi | Thay đổi **lọt qua** | Không ảnh hưởng |
| Gọi `add()` | `UnsupportedOperationException` | `UnsupportedOperationException` |

### 1.6 Immutable snapshot + volatile (Demo 4)

- 2 field volatile riêng (`host`, `port`) → reader thấy cặp **không nhất quán** (host mới, port cũ).
- Một `volatile Config` immutable, **thay cả object** trong 1 lần ghi → reader đọc 1 lần là có snapshot nhất quán.
- Dùng cho: hot-reload config, routing table, feature flags (copy-on-write).

### 1.7 Nhược điểm & thứ tự ưu tiên

- Nhược: tạo nhiều object nhỏ (áp lực GC — thường chấp nhận được); sửa 1 field phải copy. Cấu trúc lớn → persistent data structure / copy-on-write có chọn lọc.
- **Thứ tự ưu tiên thiết kế thread-safe:** immutable → thread confinement → concurrent utils / Atomic → lock.

### 1.8 Vì sao String immutable?

Thread-safe · cache `hashCode` · an toàn làm key của Map · String pool · bảo mật (đường dẫn, URL, class name không bị đổi sau khi kiểm tra).

---

## 2. Interview English

### Q1. Why are immutable objects thread-safe?

**Key sentence:** *Nothing changes after construction, so there is nothing to race on.*

**Suggested Answer:**
An immutable object **can't change** after it is created. Threads only read it, and reading alone can't cause a race condition. Also, Java's final field rules guarantee that other threads see the **fully initialized** values. So I can share immutable objects freely **without any locks**.

### Q2. How do you create an immutable class?

**Key sentence:** *Final class, private final fields, no setters, and defensive copies.*

**Suggested Answer:**
I make the class `final`, so no subclass can change its behavior. All fields are `private final`, and there are **no setters** — to "change" a value, I return a new object. If a field is a mutable object, like a `List` or `Date`, I make a **defensive copy** in the constructor and in the getter. Also, I don't let `this` escape from the constructor.

### Q3. Does `final` make an object immutable?

**Key sentence:** *No — `final` locks the reference, not the object.*

**Suggested Answer:**
No. `final` means the **reference** can't point to another object. But the object itself can still change. For example, a `final List` can still call `add()`, and a `final Date` can still call `setTime()`. To be truly immutable, the object inside must also be immutable, or I must **copy** it.

### Q4. Is a record immutable?

**Key sentence:** *Only shallowly — mutable components can still change.*

**Suggested Answer:**
A record is **shallow immutable**. Its fields are final, but if a component is a mutable object like a `List`, someone can still change that list from outside. To fix this, I copy it in a **compact constructor**, for example `this.items = List.copyOf(items)`.

### Q5. `unmodifiableList` vs `List.copyOf`?

**Key sentence:** *`unmodifiableList` is a view; `List.copyOf` is a real copy.*

**Suggested Answer:**
`Collections.unmodifiableList` returns a **read-only view** of the original list. I can't change it through the view, but if someone changes the original list, the view **shows the change**. `List.copyOf` creates a **real immutable copy**, so later changes to the original don't affect it. For immutable classes, I use `List.copyOf`.

### Q6. How do you share changing config safely between threads?

**Key sentence:** *Replace one immutable object through a volatile field.*

**Suggested Answer:**
If I store `host` and `port` in two separate volatile fields, a reader can see a **new host with an old port**. Instead, I put both in an immutable `Config` object and keep it in **one volatile field**. To update, I create a new `Config` and replace the reference. Readers always see a **consistent snapshot**. This works well for hot-reloading config or feature flags.

### Q7. What are the downsides of immutability?

**Key sentence:** *More objects, and every change needs a copy.*

**Suggested Answer:**
Every change creates a **new object**, so there are more small objects and more GC work, but this is usually fine. For large structures, copying everything can be expensive, so I use persistent data structures or copy-on-write carefully. Still, my order of choice for thread safety is: immutable first, then thread confinement, then concurrent utilities, and **locks last**.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | Immutable objects can be shared freely without locks. |
| 2 | Final class, private final fields, no setters, defensive copies. |
| 3 | `final` locks the reference, not the object. |
| 4 | A record is only shallowly immutable. |
| 5 | Share changing config by replacing one immutable object through a volatile field. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| immutable | bất biến |
| defensive copy | bản sao phòng thủ |
| shallow immutable | bất biến nông (chỉ lớp ngoài) |
| read-only view | cửa sổ chỉ đọc |
| consistent snapshot | ảnh chụp nhất quán |
| copy-on-write | sao chép khi ghi |
| hot-reload | nạp lại khi đang chạy |

## 5. Self-test

1. Vì sao immutable object thread-safe?
2. Kể 5 quy tắc tạo immutable class.
3. `final` có làm object immutable không? Cho ví dụ.
4. Vì sao copy trước rồi mới kiểm tra trong constructor?
5. record có thực sự immutable không? Sửa thế nào?
6. `unmodifiableList` vs `List.copyOf`?
7. Vì sao 2 field volatile riêng không an toàn, còn 1 volatile `Config` thì an toàn?
8. Vì sao String immutable (5 lý do)?
9. Thứ tự ưu tiên khi thiết kế thread-safe?
