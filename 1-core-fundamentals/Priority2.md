## Lộ trình ưu tiên (bản điều chỉnh)

### Phase 1: Phải chắc trước khi đi phỏng vấn
Phủ phần lớn câu hỏi thường gặp.

1. Thread lifecycle, `start()` vs `run()`, `join()`, `sleep()`, `interrupt()` ✅
2. Race condition, thread safety (atomicity, visibility, ordering) ✅
3. `synchronized` và intrinsic lock ✅
4. `volatile`, Java Memory Model, **happens-before** ✅
5. `wait/notify/notifyAll` và Producer-Consumer ✅
6. Deadlock: nguyên nhân, phòng tránh, đọc thread dump ✅ (đọc thread dump mới ở mức giới thiệu)
7. `ExecutorService` và `ThreadPoolExecutor`: `corePoolSize`, `maxPoolSize`, queue, rejection policy, cách chọn kích thước pool ⬜
8. `Callable`, `Future`, `CompletableFuture` (kèm các bẫy: pool mặc định, nuốt exception, mất context) ⬜
9. `AtomicInteger` và CAS ⬜
10. `ConcurrentHashMap` và `BlockingQueue` ⬜

### Phase 2: Nên có, đặc biệt cho Senior trở lên
1. `ReentrantLock`, `Condition`, `ReadWriteLock` ✅
2. `ThreadLocal` và rò rỉ trong thread pool ✅
3. Livelock, starvation ✅
4. Immutability, thread confinement ✅
5. `CountDownLatch`, `CyclicBarrier`, `Semaphore` ⬜
6. **Virtual Threads** (platform vs virtual, khi nào dùng, pinning) ⬜
7. Spring `@Async`: cấu hình pool, mất MDC/SecurityContext/transaction, xử lý exception ⬜
8. Chẩn đoán sự cố: thread dump, CPU cao, pool đầy ⬜

### Phase 3: Đào sâu khi còn thời gian hoặc nhắm vị trí hiệu năng cao
1. `ForkJoinPool` và work stealing
2. `StampedLock` ✅ (mức đọc hiểu)
3. `LongAdder`, ABA problem
4. Structured Concurrency
5. False sharing, lock-free programming
6. Thread scheduling và JVM internals

### Thứ tự học đề xuất
1. Hoàn thành Phase 1 mục 1-6 (module 1, bạn gần xong).
2. Phase 1 mục 7 → 8 → 10 → 9.
3. Phase 2 mục 5, rồi mục 6 (Virtual Threads).
4. Phase 2 mục 7-8 để có thứ kể khi được hỏi kinh nghiệm thực tế.
5. Phase 3 chỉ khi cần.

Hiện tại module 1 phủ gần hết các mục ✅. Phần còn lại (⬜ trong Phase 1) thuộc module 2, nên bước tiếp theo hợp lý là bắt đầu module 2 với `ThreadPoolExecutor`. Bạn muốn tôi viết file đó không?