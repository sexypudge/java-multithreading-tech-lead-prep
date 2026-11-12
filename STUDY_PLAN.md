# Lộ trình ôn phỏng vấn Tech Lead — 3 tuần (bản tăng tốc)

> Mục tiêu: **tự nói và tự code được** các câu 🔴, không phải đọc hết repo.
> Thời gian: ~**2 giờ/ngày**, 6 ngày học + 1 ngày ôn mỗi tuần.
> Bỏ qua: mọi câu 🟢, `08-ForkJoin`, `Phaser`, `StampedLock`, Fork/Join threshold. Câu 🟡 chỉ đọc khi còn thời gian.

---

## Cách học mỗi ngày (2 giờ)

| Thời gian | Việc | Ghi chú |
|---|---|---|
| 10' | **Ôn lại hôm trước**: đọc to bảng "5 câu cần nhớ" của các file hôm qua | Lặp lại cách quãng, chống quên |
| 15' | Đọc phần **1. Technical Knowledge** | Đọc hiểu, không học thuộc |
| 20' | **Chạy demo** liên quan, thử phá code (bỏ `volatile`, đổi `while` → `if`, queue vô hạn…) | Thấy lỗi tận mắt thì nhớ lâu |
| 50' | **Luyện nói**: che *Suggested Answer*, chỉ nhìn *Key sentence*, nói to bằng tiếng Anh trong 45 giây, rồi mới đối chiếu | Phần quan trọng nhất — đừng cắt |
| 15' | Làm **Self-test** cuối file, không nhìn notes | |
| 10' | Ghi câu chưa trả lời được vào bảng **Câu yếu** cuối file này | Ngày ôn sẽ xử lý |

**Quy tắc "qua bài":** nói được Key sentence + 2–3 ý chính trong ≤ 45 giây, và trả lời được thêm một câu "tại sao?". Chưa được thì ghi vào Câu yếu, **đi tiếp**, không sa lầy.

---

## Tuần 1 — Nền tảng + Thread pool

| Ngày | File | Câu cần học | Xong |
|---|---|---|:-:|
| 1 | [`1/01-ThreadCreation`](1-core-fundamentals/01-ThreadCreation.md) · [`1/02-ThreadLifecycle`](1-core-fundamentals/02-ThreadLifecycle.md) · [`1/04-RaceCondition`](1-core-fundamentals/04-RaceCondition.md) | 01: Q1–4 · 02: Q1, Q2, Q5 · 04: Q1–3 | [ ] |
| 2 | [`1/05-Synchronization`](1-core-fundamentals/05-Synchronization.md) · [`1/06-Volatile`](1-core-fundamentals/06-Volatile.md) | 05: Q1–4 · 06: Q1–4 | [ ] |
| 3 | [`1/07-JavaMemoryModel`](1-core-fundamentals/07-JavaMemoryModel.md) · [`1/08-WaitNotify`](1-core-fundamentals/08-WaitNotify.md) | 07: Q1–4 · 08: Q1–4 | [ ] |
| 4 | [`1/09-ThreadInterruption`](1-core-fundamentals/09-ThreadInterruption.md) · [`1/11-Deadlock`](1-core-fundamentals/11-Deadlock.md) · [`1/13-ThreadLocal`](1-core-fundamentals/13-ThreadLocal.md) | 09: Q1–3, Q6 · 11: Q1–4 · 13: Q1, Q3, Q4 | [ ] |
| 5 | [`2/02-ExecutorService`](2-advanced-concurrency/02-ExecutorService.md) (+ đọc lướt [`02b`](2-advanced-concurrency/02b-ExecutorService-Production.md)) | Q1–7 | [ ] |
| 6 | [`2/03-CompletableFuture`](2-advanced-concurrency/03-CompletableFuture.md) | Q2–8 | [ ] |
| 7 | **Ôn tuần 1** | Xử lý bảng Câu yếu + checkpoint bên dưới | [ ] |

**Checkpoint tuần 1** (không nhìn notes):
- [ ] Giải thích `count++` 3 bước và vì sao `volatile` không sửa được.
- [ ] `synchronized` lock trên object nào ở 3 dạng; 2 instance có chặn nhau không.
- [ ] Vì sao DCL cần `volatile`; 4 quy tắc happens-before.
- [ ] Vì sao `wait()` phải trong `while`; xử lý `InterruptedException` đúng cách.
- [ ] 4 điều kiện deadlock + cách phòng; ThreadLocal rò trong pool thế nào.
- [ ] Thứ tự core → queue → max → reject; vì sao tránh `Executors.newFixedThreadPool`.
- [ ] `thenApply` vs `thenCompose`; 4 bẫy của CompletableFuture.

---

## Tuần 2 — Công cụ production + Spring + Scenario

| Ngày | File | Câu cần học | Xong |
|---|---|---|:-:|
| 8 | [`2/01-AtomicVariables`](2-advanced-concurrency/01-AtomicVariables.md) · [`2/04-ConcurrentCollections`](2-advanced-concurrency/04-ConcurrentCollections.md) | 01: Q1–3 · 04: Q1–3, Q6 | [ ] |
| 9 | [`2/06-VirtualThreads`](2-advanced-concurrency/06-VirtualThreads.md) · [`2/05-Synchronizers`](2-advanced-concurrency/05-Synchronizers.md) | 06: Q1–4 · 05: Q1–3 | [ ] |
| 10 | [`2/07-ThreadDiagnosis`](2-advanced-concurrency/07-ThreadDiagnosis.md) | Q1–4 (chạy demo, thử `jcmd <pid> Thread.print`) | [ ] |
| 11 | [`3/01-AsyncConfig`](3-spring-boot-app/01-AsyncConfig.md) · [`3/04-RequestIdFilter`](3-spring-boot-app/04-RequestIdFilter.md) | 01: Q1–5 · 04: Q1–3 | [ ] |
| 12 | [`3/02-AsyncService`](3-spring-boot-app/02-AsyncService.md) · [`3/03-AsyncController`](3-spring-boot-app/03-AsyncController.md) · [`3/05-ScheduledTasks`](3-spring-boot-app/05-ScheduledTasks.md) | 02: Q1–4 · 03: Q1, Q3, Q4 · 05: Q1–3 | [ ] |
| 13 | [`4/07-TechLeadScenarios`](4-interview-practice/07-TechLeadScenarios.md) | Q1–6, Q8 (S1–S6, S8) | [ ] |
| 14 | **Ôn tuần 2** | Xử lý bảng Câu yếu + checkpoint bên dưới | [ ] |

**Checkpoint tuần 2:**
- [ ] CAS là gì; khi nào Atomic, khi nào lock.
- [ ] CHM khác Hashtable; vì sao `get` rồi `put` trên CHM vẫn sai; dùng `merge`/`compute`.
- [ ] Latch vs Barrier vs Semaphore.
- [ ] Virtual thread: khác platform thread, pinning, vì sao không pool, giới hạn bằng Semaphore.
- [ ] CPU 100% làm gì; request treo mà CPU thấp làm gì.
- [ ] Pool mặc định của `@Async`; self-invocation; exception theo kiểu trả về; transaction không sang thread khác.
- [ ] MDC mất trong `@Async` và cách sửa; ShedLock khi chạy nhiều instance.
- [ ] Nói được S1–S6 theo khung **clarify → hypothesize → verify → fix → prevent** trong 1 phút mỗi câu.

---

## Tuần 3 — Code tay + Câu chuyện thật + Mock

| Ngày | Việc | Xong |
|---|---|:-:|
| 15 | **Code từ đầu** (giấy hoặc file trắng, 20'): counter thread-safe + test có start gate → so với [`4/02-ConcurrencyTesting`](4-interview-practice/02-ConcurrencyTesting.md). Học Q1–3 | [ ] |
| 16 | **Code từ đầu**: mẫu graceful shutdown 4 bước + retry có backoff → so với [`4/04-GracefulBatchProcessor`](4-interview-practice/04-GracefulBatchProcessor.md). Học Q1–3, Q5 | [ ] |
| 17 | Thiết kế fan-out 20 downstream trên giấy → so với [`4/03-BoundedFanOut`](4-interview-practice/03-BoundedFanOut.md). Học Q1–4. Rảnh thì [`4/05-TokenBucket`](4-interview-practice/05-TokenBucketRateLimiter.md) Q1–3 | [ ] |
| 18 | **Câu chuyện thật**: viết 3 câu chuyện của chính anh (sự cố / tối ưu / quyết định kỹ thuật), mỗi câu theo khung bên dưới, gắn vào scenario S1–S10 | [ ] |
| 19 | **Mock 1**: bốc ngẫu nhiên 20 câu 🔴, ghi âm câu trả lời, nghe lại, sửa câu yếu | [ ] |
| 20 | Code [`4/01-EvenOddPrinter`](4-interview-practice/01-EvenOddPrinter.md) bằng `wait/notifyAll` trong 10' (không treo ở cuối). Còn thời gian: đọc câu 🟡 của các file tuần 1–2 | [ ] |
| 21 | **Mock 2**: 20 câu 🔴 khác + 2 scenario + 1 bài code. Chạy checklist cuối | [ ] |

**Khung câu chuyện thật (STAR rút gọn, 2 phút):**
1. **Bối cảnh** — hệ thống gì, tải bao nhiêu (1 câu).
2. **Vấn đề** — triệu chứng + con số (latency, CPU, lỗi).
3. **Cách điều tra** — dùng gì (metric, thread dump, log), loại trừ giả thuyết nào.
4. **Giải pháp + trade-off** — vì sao chọn cách này mà không phải cách khác.
5. **Kết quả + phòng ngừa** — con số sau khi sửa, alert/test/checklist đã thêm.

---

## Checklist cuối cùng (trước ngày phỏng vấn)

- [ ] Nói được **mọi câu 🔴** trong các file ở lộ trình, ≤ 45 giây, không nhìn notes.
- [ ] Code được không nhìn: counter + test start gate, mẫu shutdown, EvenOdd `wait/notifyAll`.
- [ ] Vẽ được trên giấy: thread pool core/queue/max/reject; fan-out có Semaphore + timeout + fallback.
- [ ] Trả lời S1–S6, S8 trong 1–2 phút mỗi câu.
- [ ] Có sẵn 3 câu chuyện thật, mỗi câu có số liệu.
- [ ] Bị hỏi "tại sao?" 2–3 lần liên tiếp vẫn trả lời được.

---

## Chế độ gấp

**Chỉ còn 1 tuần:**

| Ngày | Học |
|---|---|
| 1 | `1/04`, `1/05`, `1/06` (chỉ 🔴) |
| 2 | `1/08`, `1/09`, `1/11`, `1/13` (chỉ 🔴) |
| 3 | `2/02-ExecutorService` |
| 4 | `2/03-CompletableFuture`, `2/04-ConcurrentCollections` Q1–3 |
| 5 | `3/01-AsyncConfig`, `3/02-AsyncService` |
| 6 | `4/07-TechLeadScenarios` S1–S4, S8 + `2/06-VirtualThreads` Q1–4 |
| 7 | Mock + câu chuyện thật |

**Chỉ còn 1 ngày:** đọc to bảng "5 câu cần nhớ" của mọi file trong lộ trình + `4/07-TechLeadScenarios` S1, S2, S4, S8 + [`INTERVIEW_QUESTIONS.md`](INTERVIEW_QUESTIONS.md) phần cheat sheet.

---

## Sau 3 tuần

Concurrency trong JVM đã đủ dùng. Với Tech Lead ở VN, phần thường quyết định kết quả tiếp theo là:
- Concurrency ở tầng **DB**: isolation level, optimistic/pessimistic lock, `SELECT … FOR UPDATE`, trừ tồn kho / đặt chỗ bị trùng.
- **Hệ phân tán**: distributed lock (Redis), Kafka at-least-once + idempotent consumer, outbox, saga.
- **System design** và câu hỏi leadership (review code, mentor, quyết định kỹ thuật).

---

## Bảng Câu yếu

Ghi lại câu chưa trả lời được; ngày ôn (7, 14, 19, 21) xử lý hết rồi gạch đi.

| Ngày | File · Câu | Vướng ở đâu | Đã ổn |
|---|---|---|:-:|
| | | | [ ] |
| | | | [ ] |
| | | | [ ] |
| | | | [ ] |
| | | | [ ] |
