# 01 — Creating Threads & Tasks

**Source:** `ThreadCreationExamples.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.

---

## 1. Technical Knowledge

### 1.1 Ba cách tạo task/thread

| Cách | Đặc điểm | Khi nào dùng |
|---|---|---|
| `extends Thread` | Trộn **việc cần làm** với **cơ chế chạy**; không extend class khác được | Hầu như không dùng |
| `implements Runnable` | Chỉ mô tả task: `void run()`, không trả kết quả | Task không cần kết quả |
| `implements Callable<V>` | `V call() throws Exception`: **trả kết quả**, được ném checked exception | Task cần kết quả → submit vào executor, nhận `Future` |

**Ý chính ở level Senior:** production gần như **không tự `new Thread()`**. Ta viết task (Runnable/Callable) rồi giao cho `ExecutorService`.

### 1.2 Vì sao không `new Thread()` tuỳ tiện?

- Mỗi platform thread tốn ~1MB stack + system call để tạo.
- Không giới hạn được số lượng → dễ hết tài nguyên.
- Khó đặt tên, khó shutdown, exception dễ bị mất.
→ `ExecutorService` giải quyết hết. Java 21+: việc I/O-bound có thể dùng virtual thread.

### 1.3 Hai bẫy kinh điển

- **Gọi `run()` thay vì `start()`:** `run()` chỉ là gọi method thường, chạy trên **thread hiện tại**. `start()` mới tạo thread mới.
- **`start()` hai lần:** ném `IllegalThreadStateException`. Một `Thread` object chỉ start được **một lần**.

### 1.4 Future

- `submit()` **không chặn**; `future.get()` mới chặn tới khi có kết quả.
- Production: luôn dùng `get(timeout, unit)`.
- Java 19+: `ExecutorService` là `AutoCloseable` → dùng `try-with-resources` (`close()` = shutdown + chờ task xong).

---

## 2. Interview English

### Q1. What are the ways to create a thread in Java?

**Key sentence:** *I define a task with Runnable or Callable and run it with an ExecutorService.*

**Suggested Answer:**
There are three common ways: extend `Thread`, implement `Runnable`, or implement `Callable`. Extending `Thread` mixes the task with the thread, so I rarely use it. In practice, I write the task as a `Runnable` or `Callable` and submit it to an `ExecutorService`. This **separates the task from thread management**.

### Q2. Runnable vs Callable?

**Key sentence:** *Callable returns a result and can throw checked exceptions; Runnable cannot.*

**Suggested Answer:**
`Runnable` has a `run()` method that returns nothing. `Callable` has a `call()` method that **returns a value** and can **throw checked exceptions**. When I submit a `Callable` to an executor, I get a `Future` and call `get()` to read the result. I use `Runnable` when I don't need a result.

### Q3. What is the difference between `start()` and `run()`?

**Key sentence:** *`start()` creates a new thread; `run()` is just a normal method call.*

**Suggested Answer:**
`start()` asks the JVM to create a **new thread** and then call `run()` on that thread. If I call `run()` directly, it runs on the **current thread**, like a normal method. Also, a thread can only be started once. Calling `start()` twice throws `IllegalThreadStateException`.

### Q4. Why shouldn't we create threads manually in production?

**Key sentence:** *Threads are expensive, so we reuse them with a thread pool.*

**Suggested Answer:**
Each platform thread uses about one megabyte of stack memory, and creating it is slow. If we create threads without a limit, we can run out of resources. Manual threads are also hard to name, monitor, and shut down. An `ExecutorService` **reuses threads**, limits their number, and gives us a clean shutdown.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | Define the task with Runnable or Callable, run it with an ExecutorService. |
| 2 | Callable returns a result and can throw checked exceptions. |
| 3 | `start()` creates a new thread; `run()` runs on the current thread. |
| 4 | A thread can only be started once. |
| 5 | Always call `Future.get()` with a timeout in production. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| task | việc cần làm (Runnable/Callable) |
| thread management | quản lý thread |
| thread pool / executor | nhóm thread được tái sử dụng |
| submit a task | gửi task vào executor |
| return a result | trả về kết quả |
| checked exception | exception bắt buộc khai báo/bắt |
| reuse threads | tái sử dụng thread |

## 5. Self-test

1. Kể 3 cách tạo task/thread. Vì sao ít dùng `extends Thread`?
2. Runnable khác Callable thế nào?
3. Gọi `run()` trực tiếp thì chạy trên thread nào?
4. `start()` hai lần thì sao?
5. `submit()` có chặn không? Cái gì chặn?
6. Vì sao production không tự `new Thread()`?
