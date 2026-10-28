# Java Multithreading - Interview Questions & Answers

## 📚 Mục lục
1. [Cơ bản](#cơ-bản)
2. [Synchronization](#synchronization)
3. [Advanced Concurrency](#advanced-concurrency)
4. [Spring Boot](#spring-boot)
5. [Production & Debugging](#production--debugging)
6. [Behavioral Questions](#behavioral-questions)

---

## Cơ bản

### Q1: Process vs Thread?
**Answer:**
- **Process:** Chương trình độc lập, có memory space riêng, heavyweight
- **Thread:** Lightweight process, share memory trong cùng process
- **Ưu điểm thread:** Ít overhead, communication dễ hơn, context switch nhanh hơn

### Q2: Thread creation - cách nào tốt nhất?
**Answer:**
1. **Extend Thread** - Đơn giản nhưng không flexible (single inheritance)
2. **Implement Runnable** - ✅ Best practice, flexible
3. **Implement Callable** - Khi cần return value

**Prefer Runnable vì:**
- Có thể extend class khác
- Separation of concerns (task vs execution)
- Reusable

### Q3: start() vs run()?
**Answer:**
- **start():** Tạo thread mới và gọi run() trong thread đó
- **run():** Chỉ gọi method bình thường trong current thread
- **Lỗi thường gặp:** Gọi run() trực tiếp → không tạo thread mới

### Q4: Thread states?
**Answer:**
1. **NEW** - Thread created, chưa start()
2. **RUNNABLE** - Đang chạy hoặc ready to run
3. **BLOCKED** - Chờ monitor lock
4. **WAITING** - Chờ notification (wait, join)
5. **TIMED_WAITING** - Chờ có timeout (sleep, wait with timeout)
6. **TERMINATED** - Completed

### Q5: sleep() vs wait()?
**Answer:**
| Feature | sleep() | wait() |
|---------|---------|--------|
| Lock | KHÔNG release | RELEASE lock |
| Location | Anywhere | Trong synchronized |
| Wake up | Timeout | notify/notifyAll |
| State | TIMED_WAITING | WAITING |
| Class | Thread | Object |

---

## Synchronization

### Q6: synchronized hoạt động như thế nào?
**Answer:**
- Sử dụng **monitor lock** (intrinsic lock) trên object
- Chỉ 1 thread có thể hold lock tại một thời điểm
- **Method level:** Lock trên `this` (instance) hoặc `Class` (static)
- **Block level:** Lock trên object cụ thể

**Reentrant:** Thread đã hold lock có thể acquire lại

### Q7: synchronized method vs block?
**Answer:**
```java
// Method - lock toàn bộ
public synchronized void method() { }

// Block - chỉ lock critical section (BETTER)
public void method() {
    // non-critical code
    synchronized(lock) {
        // critical section only
    }
}
```

**Prefer block vì:**
- Minimize lock scope
- Better performance
- Fine-grained control

### Q8: volatile vs synchronized?
**Answer:**
| Feature | volatile | synchronized |
|---------|----------|--------------|
| Visibility | ✅ | ✅ |
| Atomicity | ❌ | ✅ |
| Lock | No | Yes |
| Performance | Faster | Slower |
| Use case | Flags, single write | Compound operations |

**volatile:** Chỉ đảm bảo visibility, KHÔNG đảm bảo atomicity
**synchronized:** Cả visibility và atomicity

### Q9: Deadlock - 4 điều kiện?
**Answer:**
1. **Mutual Exclusion** - Resource không thể share
2. **Hold and Wait** - Thread hold lock và chờ lock khác
3. **No Preemption** - Không thể force release lock
4. **Circular Wait** - T1 chờ T2, T2 chờ T1

**Cách tránh:**
- Lock ordering (luôn acquire theo thứ tự)
- Lock timeout (tryLock with timeout)
- Avoid nested locks
- Deadlock detection

### Q10: Race condition là gì?
**Answer:**
Kết quả phụ thuộc vào timing/ordering của threads

**2 loại phổ biến:**
1. **Read-Modify-Write:** `count++` (không atomic)
2. **Check-Then-Act:** `if (x == null) { x = new X(); }`

**Solutions:**
- synchronized
- Atomic variables
- Concurrent collections
- Immutable objects

---

## Advanced Concurrency

### Q11: ExecutorService vs manual threads?
**Answer:**
**ExecutorService advantages:**
- ✅ Thread reuse → giảm overhead
- ✅ Resource management → control số threads
- ✅ Task queue → handle load spikes
- ✅ Better API → submit, invokeAll, shutdown
- ✅ Easier to tune

**Thread pool sizing:**
- CPU-bound: `cores + 1`
- I/O-bound: `cores * (1 + wait_time/compute_time)`

### Q12: Fixed vs Cached thread pool?
**Answer:**
**FixedThreadPool:**
- Fixed số threads
- Unbounded queue
- Use case: Stable, predictable load

**CachedThreadPool:**
- Create threads on-demand
- Reuse idle threads (60s timeout)
- Use case: Many short-lived tasks, unpredictable load

### Q13: CompletableFuture vs Future?
**Answer:**
**Future:**
- Chỉ có `get()` blocking
- Không thể chain operations
- Limited exception handling

**CompletableFuture:**
- ✅ Non-blocking callbacks
- ✅ Composable (thenApply, thenCompose)
- ✅ Combine multiple futures
- ✅ Better exception handling
- ✅ Timeout support

### Q14: thenApply vs thenCompose?
**Answer:**
```java
// thenApply - transform value (map)
future.thenApply(x -> x * 2)

// thenCompose - chain futures (flatMap)
future.thenCompose(x -> anotherFuture(x))
```

### Q15: ConcurrentHashMap vs Hashtable?
**Answer:**
**ConcurrentHashMap:**
- Segment locking (Java 7) / CAS (Java 8+)
- Non-blocking reads
- Better performance
- null key/value NOT allowed

**Hashtable:**
- Lock entire map
- Blocking reads
- Legacy, avoid

### Q16: Atomic variables - khi nào dùng?
**Answer:**
**Use cases:**
- ✅ Simple counters (AtomicInteger)
- ✅ Flags (AtomicBoolean)
- ✅ References (AtomicReference)
- ✅ Lock-free algorithms

**Advantages:**
- No locks → no deadlock
- CAS-based → better performance
- Non-blocking

**Limitations:**
- Chỉ cho simple operations
- Complex logic → dùng synchronized

### Q17: CountDownLatch vs CyclicBarrier?
**Answer:**
**CountDownLatch:**
- One-time use
- Threads wait for events
- `countDown()` và `await()`
- Use case: Service startup, batch processing

**CyclicBarrier:**
- Reusable
- Threads wait for each other
- `await()` at barrier
- Use case: Parallel algorithms, simulations

### Q18: Semaphore use cases?
**Answer:**
- Limit concurrent access (connection pool)
- Resource throttling
- Rate limiting
- Implement bounded resources

```java
Semaphore semaphore = new Semaphore(10); // Max 10 concurrent
semaphore.acquire();
try {
    // Use resource
} finally {
    semaphore.release();
}
```

---

## Spring Boot

### Q19: @Async configuration?
**Answer:**
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}
```

### Q20: @Async không work, tại sao?
**Answer:**
**Common issues:**
1. **Self-invocation** - Gọi từ cùng class (proxy issue)
2. Method không **public**
3. Quên **@EnableAsync**
4. Không có thread pool config
5. Exception swallowed (return CompletableFuture)

**Solution:** Inject service và gọi từ class khác

### Q21: fixedRate vs fixedDelay?
**Answer:**
**fixedRate:**
- Interval từ **start time**
- Có thể overlap nếu task chậm
- Use case: Periodic tasks

**fixedDelay:**
- Interval từ **completion time**
- Không overlap
- Use case: Cleanup, maintenance

---

## Production & Debugging

### Q22: Làm sao detect deadlock trong production?
**Answer:**
**Detection:**
1. **Thread dump:** `jstack <pid>` hoặc `kill -3 <pid>`
2. **JConsole/VisualVM** - Detect deadlock tab
3. **ThreadMXBean:** `findDeadlockedThreads()`
4. **Monitoring:** Alert on thread state

**Analysis:**
- Look for "Found one Java-level deadlock"
- Check waiting threads và locks they hold
- Identify circular dependency

### Q23: Thread pool tuning strategy?
**Answer:**
**Steps:**
1. **Start conservative:** cores + 1 (CPU) hoặc cores * 2 (I/O)
2. **Monitor metrics:**
   - Active threads
   - Queue size
   - Rejection count
   - Response time
   - CPU usage
3. **Load test:** Simulate production load
4. **Adjust gradually:** Increase/decrease based on metrics
5. **Document:** Record changes và results

**Formula:**
- CPU-bound: `cores + 1`
- I/O-bound: `cores * (1 + WT/CT)`
  - WT = wait time
  - CT = compute time

### Q24: Memory leak với threads?
**Answer:**
**Common causes:**
1. **ThreadLocal không cleanup**
2. **Thread pool không shutdown**
3. **Threads hold references** to large objects
4. **Unclosed resources** in threads

**Prevention:**
```java
// ThreadLocal cleanup
threadLocal.remove();

// Proper shutdown
executor.shutdown();
executor.awaitTermination(60, TimeUnit.SECONDS);

// Try-with-resources
try (Resource r = getResource()) {
    // use resource
}
```

### Q25: Monitoring thread pool?
**Answer:**
**Metrics to track:**
- Active thread count
- Pool size (current/max)
- Queue size
- Completed task count
- Rejection count

**Tools:**
- Spring Actuator
- Micrometer
- JMX
- Custom metrics

```java
// Spring Actuator
management.metrics.enable.executor=true

// Access metrics
/actuator/metrics/executor.active
/actuator/metrics/executor.queued
```

---

## Behavioral Questions

### Q26: Describe a threading issue you debugged
**Answer template:**
1. **Situation:** Production issue - intermittent failures
2. **Problem:** Race condition in cache update
3. **Investigation:**
   - Thread dumps
   - Added logging with thread names
   - Reproduced with stress test
4. **Solution:** Added synchronization on cache update
5. **Result:** Issue resolved, added monitoring
6. **Learning:** Importance of thread-safety in shared state

### Q27: How do you ensure thread-safety?
**Answer:**
**Strategies:**
1. **Immutability** - Prefer immutable objects
2. **Synchronization** - synchronized, locks
3. **Atomic variables** - For simple operations
4. **Concurrent collections** - Built-in thread-safety
5. **Thread-local** - Per-thread state
6. **Stateless design** - No shared state

**Process:**
- Identify shared mutable state
- Document thread-safety guarantees
- Code review for concurrency issues
- Testing with multiple threads

### Q28: Trade-offs của multithreading?
**Answer:**
**Advantages:**
- ✅ Better resource utilization
- ✅ Improved responsiveness
- ✅ Parallel processing
- ✅ Better throughput

**Disadvantages:**
- ❌ Complexity (deadlock, race conditions)
- ❌ Debugging difficulty
- ❌ Context switching overhead
- ❌ Memory overhead per thread

**When NOT to use:**
- Simple, quick operations
- Single-core systems
- When overhead > benefit

---

## 💡 Tips cho Interview

### Cách trả lời tốt:
1. **Start with definition** - Giải thích concept
2. **Give example** - Code hoặc real-world scenario
3. **Discuss trade-offs** - Pros/cons, when to use
4. **Mention alternatives** - Other approaches
5. **Production considerations** - Monitoring, debugging

### Red flags to avoid:
- ❌ "I never had threading issues" (unrealistic)
- ❌ Không biết trade-offs
- ❌ Chỉ biết theory, không có practical experience
- ❌ Không mention monitoring/debugging

### Impress interviewer:
- ✅ Mention production experience
- ✅ Discuss monitoring strategies
- ✅ Talk about debugging techniques
- ✅ Show understanding of trade-offs
- ✅ Reference best practices

---

**Good luck! 🎯**
