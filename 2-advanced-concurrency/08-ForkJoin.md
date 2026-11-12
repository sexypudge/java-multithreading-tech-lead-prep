# 08 — Fork/Join & Parallel Streams

**Source:** `ForkJoinDemo.java`
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

> File này là **P1** trong lộ trình — học sau các file P0.

---

## 1. Technical Knowledge

### 1.1 ForkJoinPool khác ThreadPoolExecutor

| | `ThreadPoolExecutor` | `ForkJoinPool` |
|---|---|---|
| Hàng đợi | 1 queue chung | Mỗi worker một **deque** riêng |
| Cân bằng tải | lấy từ queue chung | **work stealing**: worker rảnh trộm từ **đuôi** deque của worker khác |
| `join()` | chặn thread | worker đang chờ **đi chạy task khác** |
| Phù hợp | task độc lập, I/O với pool riêng | task **đệ quy, CPU-bound** |

### 1.2 RecursiveTask / RecursiveAction

```java
protected Long compute() {
    if (end - start <= threshold) return sumSequential();   // đủ nhỏ → tuần tự
    SumTask left = ..., right = ...;
    left.fork();                     // đẩy nửa trái vào deque (có thể bị trộm)
    long r = right.compute();        // tự làm nửa phải
    long l = left.join();            // rồi mới join
    return l + r;
}
```

- `RecursiveTask<V>` có kết quả; `RecursiveAction` không (dùng `invokeAll(a, b)`).
- ✗ Sai thường gặp: `fork()` cả hai rồi `join()` (thread hiện tại ngồi không); `join()` **trước** `compute()` (mất song song).

### 1.3 Threshold (Demo 1)

- Quá nhỏ → chi phí tạo task lấn át. Quá lớn → không đủ song song.
- Phép cộng đơn giản: **băng thông bộ nhớ** là nút thắt → speedup khiêm tốn.
- Đo nghiêm túc → **JMH** (warm-up JIT).

### 1.4 Parallel stream (Demo 3)

Có lợi khi: dữ liệu **lớn**, thao tác **CPU-bound**, **không side effect**, nguồn **dễ chia** (array, `ArrayList`, range) — và **đã đo** thấy nhanh hơn.

Không dùng khi:
- dữ liệu nhỏ (100 phần tử → parallel thường **chậm hơn**),
- I/O / blocking,
- có side effect: `forEach(list::add)` vào `ArrayList` → mất phần tử / exception. Dùng `collect` / `toList()`,
- nguồn khó chia (`LinkedList`, `Stream.iterate`),
- cần thứ tự (`forEachOrdered` mất lợi ích),
- web server bận (đã đủ song song theo request).

### 1.5 Bẫy commonPool (Demo 4)

- `parallelStream()` và `supplyAsync()` không có executor đều chạy trên `ForkJoinPool.commonPool()` — **dùng chung cả JVM**, ≈ `N_cpu - 1` thread (+ thread gọi).
- 16 task "I/O" 200ms → bị giới hạn bởi số thread commonPool, và **mọi parallel stream / CF mặc định khác cũng phải xếp hàng**.
- I/O → executor riêng có giới hạn, hoặc virtual thread.
- Chạy parallel stream trên pool riêng: `pool.submit(() -> list.parallelStream()...).join()` (dựa vào implementation).

---

## 2. Interview English

### Q1. Fork/Join vs ExecutorService?

**Importance:** 🟡 Medium

**Key sentence:** *Fork/Join is for recursive CPU-bound tasks with work stealing; ExecutorService for independent tasks.*

**Suggested Answer:**
Fork/Join is designed for **divide-and-conquer**: a task splits itself into smaller subtasks recursively, and it's **CPU-bound**. Each worker has its own deque, and idle workers **steal** work, and `join()` doesn't waste the thread. `ThreadPoolExecutor` has one shared queue and fits **independent tasks**, including I/O work with a dedicated pool.

### Q2. What is work stealing?

**Importance:** 🟡 Medium

**Key sentence:** *Idle workers take tasks from the tail of busy workers' deques.*

**Suggested Answer:**
In a `ForkJoinPool`, each worker has its **own deque**. It pushes and pops its own tasks at the **head**. When a worker runs out of work, it **steals** a task from the **tail** of another worker's deque. This reduces contention, because owners and thieves work at different ends, and it **balances the load** automatically when some branches of the recursion are bigger than others.

### Q3. When should you NOT use a parallel stream?

**Importance:** 🔴 Important

**Key sentence:** *Small data, blocking I/O, shared state, or hard-to-split sources.*

**Suggested Answer:**
I avoid it when the data is **small** — the cost of splitting and merging is bigger than the gain. Also for **I/O or blocking** work, because it runs on the shared common pool. Also when there are **side effects** or shared mutable state, like adding to an `ArrayList`. And when the source is hard to split, like a `LinkedList`, or when order matters. In a busy web server, requests are already parallel. I only use it after **measuring** a real gain.

### Q4. Why is blocking I/O in a parallel stream dangerous?

**Importance:** 🔴 Important

**Key sentence:** *It blocks the JVM-wide common pool, so every other parallel stream and default async task waits.*

**Suggested Answer:**
Parallel streams run on `ForkJoinPool.commonPool()`, which is **shared by the whole JVM** and has only about **cores minus one** threads. If I do slow HTTP or DB calls there, those few threads are blocked, and **every other** parallel stream and every `CompletableFuture` without an executor in the application has to **wait in line**. For I/O, I use a dedicated bounded executor or virtual threads.

### Q5. How do you choose the threshold in a Fork/Join task?

**Importance:** 🟢 Low

**Key sentence:** *Too small adds task overhead; too large loses parallelism — measure it.*

**Suggested Answer:**
If the threshold is **too small**, we create millions of tiny tasks and the overhead dominates. If it's **too large**, there aren't enough tasks to keep all cores busy. I pick a size where each leaf does a meaningful amount of work, then **measure**, ideally with JMH. Also, for simple operations like summing, **memory bandwidth** is often the bottleneck, so the speedup is smaller than the number of cores.

### Q6. What's the correct fork/join pattern inside `compute()`?

**Importance:** 🟢 Low

**Key sentence:** *Fork one half, compute the other half yourself, then join.*

**Suggested Answer:**
I **fork** the left half, so another worker can steal it, then **compute** the right half on the current thread, and finally **join** the left half. A common mistake is forking both halves and then joining — the current thread just sits there. Another mistake is calling `join()` before `compute()`, which makes it **sequential**. For `RecursiveAction`, `invokeAll(left, right)` does the right thing.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | Fork/Join is for recursive, CPU-bound work. |
| 2 | Idle workers steal tasks from other workers' deques. |
| 3 | Fork one half, compute the other, then join. |
| 4 | Parallel streams need big data, CPU work and no side effects. |
| 5 | Never block in the common pool. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| divide and conquer | chia để trị |
| work stealing | trộm việc |
| deque | hàng đợi hai đầu |
| threshold | ngưỡng chia nhỏ |
| speedup | mức tăng tốc |
| memory bandwidth | băng thông bộ nhớ |
| side effect | tác dụng phụ |

## 5. Self-test

1. ForkJoinPool khác ThreadPoolExecutor ở 3 điểm nào?
2. Work stealing hoạt động thế nào? Vì sao trộm ở đuôi?
3. Mẫu `fork / compute / join` đúng? 2 cách viết sai?
4. Threshold quá nhỏ / quá lớn thì sao?
5. Khi nào **không** dùng parallel stream (kể ít nhất 4)?
6. Vì sao blocking I/O trong parallel stream ảnh hưởng cả ứng dụng?
