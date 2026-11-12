# 02 — AsyncService: `@Async` qua proxy, self-invocation, exception, transaction

**Source:** `src/main/java/com/example/async/AsyncService.java` (+ `src/test/.../AsyncBehaviorTest.java`)
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 `@Async` = proxy

Spring bọc bean bằng **proxy**. Gọi method → proxy **đẩy thân method vào `Executor`** rồi trả về ngay. Thân method chạy trên thread của pool (`io-*`, `bg-*`), **không phải** thread của caller.

```java
@Async("ioExecutor")                      // luôn chỉ định rõ pool
public CompletableFuture<String> callDownstream(String service, long latencyMs) { ... }
```

### 1.2 Bẫy của proxy

| Sai lầm | Hậu quả |
|---|---|
| **Self-invocation**: `this.whereAmI()` trong cùng class | Không qua proxy → chạy **đồng bộ** trên thread gọi |
| `@Async` trên method `private` / `final`, class `final` | Proxy không override được → chạy đồng bộ |
| Quên `@EnableAsync` | Không có gì xảy ra, **không báo lỗi** |

`@Transactional` dính **đúng các bẫy này**. Sửa self-invocation: tách method sang bean khác (cách sạch nhất), hoặc inject chính bean (`@Lazy` self), hoặc `AopContext.currentProxy()`.

Bằng chứng (`AsyncBehaviorTest`): gọi qua proxy → tên thread bắt đầu bằng `io-`; `selfInvocation()` → tên thread của **test/Tomcat**.

### 1.3 Kiểu trả về quyết định exception đi đâu

| Kiểu trả về | Exception |
|---|---|
| `CompletableFuture<T>` | Spring hoàn thành future **exceptionally** (kể cả khi method ném thẳng) → caller xử lý bằng `exceptionally` / `handle`; `join()` ném `CompletionException` |
| `void` | Caller **không thấy** → `AsyncUncaughtExceptionHandler` |

Trong `@Async` method trả CF: dùng `CompletableFuture.completedFuture(value)` để trả kết quả.

### 1.4 `@Async` và transaction

- Transaction gắn với **thread** → method `@Async` **không nằm trong transaction của caller**.
- Caller `@Transactional` gọi `@Async` để đọc dữ liệu vừa ghi → async có thể chạy **trước commit** → không thấy dữ liệu.
- Sửa: `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`, hoặc **outbox**.
- `@Transactional` trên chính method `@Async` → transaction **riêng**, không rollback cùng caller.

### 1.5 Những gì test chứng minh (`AsyncBehaviorTest`)

| Test | Chứng minh |
|---|---|
| `asyncMethodCalledThroughProxyRunsOnPoolThread` | Qua proxy → thread `io-*` |
| `selfInvocationBypassesProxyAndRunsOnCallerThread` | Self-invocation → thread gọi |
| `mdcIsPropagatedToPoolThreadByTaskDecorator` | MDC sang được thread pool |
| `exceptionFromAsyncMethodCompletesTheFutureExceptionally` | Lỗi → `CompletionException` với cause đúng |
| `independentCallsRunInParallel` | 3 × 400ms song song < 1000ms |
| `completeOnTimeoutReturnsFallbackWithoutWaitingForSlowCall` | Fallback sau 200ms, không chờ 3s |

---

## 2. Interview English

### Q1. How does `@Async` work?

**Importance:** 🔴 Important

**Key sentence:** *Spring wraps the bean in a proxy that submits the method body to an executor.*

**Suggested Answer:**
When I enable async, Spring wraps the bean in a **proxy**. When another bean calls an `@Async` method, the proxy **submits the method body to an executor** and returns immediately — a `CompletableFuture` or nothing for `void`. The body runs on a pool thread, not the caller's thread. I always name the executor in the annotation, like `@Async("ioExecutor")`, instead of relying on the default.

### Q2. Why doesn't `@Async` work when you call the method from the same class?

**Importance:** 🔴 Important

**Key sentence:** *Self-invocation goes through `this`, not through the proxy, so it runs synchronously.*

**Suggested Answer:**
Because the async behavior lives in the **proxy**. When a method calls `this.asyncMethod()`, it calls the real object directly and **skips the proxy**, so the method runs **synchronously** on the caller's thread. My test proves it: through the proxy the thread name starts with `io-`, but with self-invocation it's the caller's thread. The clean fix is to move the async method to **another bean**. The same trap applies to `private` or `final` methods and to `@Transactional`.

### Q3. How are exceptions handled in `@Async` methods?

**Importance:** 🔴 Important

**Key sentence:** *It depends on the return type: in the future for CompletableFuture, in the uncaught handler for void.*

**Suggested Answer:**
If the method returns a `CompletableFuture`, Spring completes it **exceptionally**, even if the method just throws. The caller must handle it with `exceptionally`, `handle` or `join`, otherwise the error is lost. If the method returns `void`, the caller can't see it at all, and Spring passes it to the **`AsyncUncaughtExceptionHandler`**. So for anything important, I return a `CompletableFuture` or add proper handling and alerting.

### Q4. Does the caller's transaction propagate into an `@Async` method?

**Importance:** 🔴 Important

**Key sentence:** *No — transactions are bound to the thread, so async work runs outside the caller's transaction.*

**Suggested Answer:**
No. A transaction is bound to the **thread**, and the async method runs on another thread. So it's outside the caller's transaction, and it may run **before the caller commits** — then it doesn't see the new data. If I need to start async work after saving something, I trigger it **after commit**, with `@TransactionalEventListener(phase = AFTER_COMMIT)` or an outbox. If the async method has its own `@Transactional`, it's a **separate** transaction that won't roll back with the caller.

### Q5. What else can make `@Async` silently not work?

**Importance:** 🟡 Medium

**Key sentence:** *Missing @EnableAsync, private or final methods, or calling it on an object Spring didn't create.*

**Suggested Answer:**
If `@EnableAsync` is missing, nothing happens and there's **no error** — the method just runs synchronously. The proxy also can't intercept **private or final** methods or a final class. And if I create the object with `new` instead of getting it from Spring, there's no proxy at all. A quick check is to **log the thread name** inside the method, or write a test like mine that asserts it runs on a pool thread.

### Q6. How do you test `@Async` behavior?

**Importance:** 🟡 Medium

**Key sentence:** *Assert on thread names, MDC and timing, and wait with join or a timeout — never with sleep.*

**Suggested Answer:**
I use `@SpringBootTest` so the real proxies and executors are used. I assert the **thread name** to prove the method runs on the pool, check that **MDC** is propagated, and check that a failing method completes the future with the right cause. For parallelism, I assert that three 400 ms calls finish in **under one second**. I always wait with `join()` or a **timeout**, or Awaitility, never with `Thread.sleep`.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | `@Async` works through a proxy that submits to an executor. |
| 2 | Self-invocation skips the proxy and runs synchronously. |
| 3 | The return type decides where exceptions go. |
| 4 | Transactions don't cross threads. |
| 5 | Trigger async work after commit, not inside the transaction. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| proxy | đối tượng bọc ngoài bean |
| self-invocation | gọi method trong cùng class qua `this` |
| intercept | chặn lời gọi |
| complete exceptionally | hoàn thành với lỗi |
| after commit | sau khi transaction commit |
| outbox pattern | ghi sự kiện cùng transaction rồi gửi sau |
| silently | âm thầm, không báo lỗi |

## 5. Self-test

1. `@Async` hoạt động thế nào? Thân method chạy ở thread nào?
2. Vì sao self-invocation chạy đồng bộ? 3 cách sửa?
3. Những trường hợp nào khác làm `@Async` không có tác dụng?
4. Method trả CF ném exception thẳng thì sao? Trả `void` thì sao?
5. `@Async` có nằm trong transaction của caller không? Hậu quả và cách sửa?
6. 6 test trong `AsyncBehaviorTest` chứng minh điều gì?
