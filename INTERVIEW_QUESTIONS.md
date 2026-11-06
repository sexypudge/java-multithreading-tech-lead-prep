# Java Concurrency — Interview Questions & Answers (Senior / Tech Lead)

Answers are written in the English you would actually say in an interview: short, precise, with the trade-off. Each section points to the runnable demo that proves the answer.

**Contents**
0. [5-minute cheat sheet](#0-5-minute-cheat-sheet)
1. [Threads basics](#1-threads-basics)
2. [Java Memory Model](#2-java-memory-model)
3. [Locks & liveness](#3-locks--liveness)
4. [Executors & thread pools](#4-executors--thread-pools)
5. [CompletableFuture](#5-completablefuture)
6. [Atomics, concurrent collections, synchronizers](#6-atomics-concurrent-collections-synchronizers)
7. [Virtual threads](#7-virtual-threads)
8. [Spring](#8-spring)
9. [Production & debugging](#9-production--debugging)
10. [Behavioral](#10-behavioral)

Scenario questions ("high latency, low CPU", "queue keeps growing"...) are in [`4-interview-practice/TECH_LEAD_SCENARIOS.md`](4-interview-practice/TECH_LEAD_SCENARIOS.md).

---

## 0. 5-minute cheat sheet

```
Thread vs Runnable     Define tasks (Runnable/Callable), run them on an ExecutorService.
start() vs run()       start() = new thread; run() = plain method call. A Thread can start only once.
synchronized           Mutual exclusion + visibility on ONE monitor. Instance → this, static → Class.
volatile               Visibility + ordering, NOT atomicity. count++ on a volatile is still broken.
happens-before         unlock→lock, volatile write→read, start(), join(), transitivity.
wait()                 Hold the monitor, call in a while loop, notifyAll after changing the condition.
Deadlock               4 conditions; fix with global lock ordering or tryLock + timeout + backoff.
ThreadPoolExecutor     core → queue → grow to max → reject. Unbounded queue ⇒ max is never used.
Executors.newFixed/Cached   Unbounded queue / unbounded threads ⇒ build your own bounded pool.
submit vs execute      submit hides exceptions in the Future; execute → UncaughtExceptionHandler.
Pool sizing            CPU ≈ cores; I/O ≈ cores × (1 + wait/compute) — a starting point, then measure.
CompletableFuture      Pass your own executor; end chains with handle/exceptionally; timeouts don't cancel work.
ConcurrentHashMap      Single ops atomic; use merge/compute/computeIfAbsent, not get-then-put. No nulls.
Virtual threads        Cheap, great for blocking I/O, NOT faster for CPU; don't pool them; still limit downstreams.
@Async                 Proxy-based: self-invocation runs synchronously; configure a bounded named executor.
Production             Named threads, bounded queues, timeouts everywhere, executor metrics, 3 thread dumps.
```

---

## 1. Threads basics

**Q1. Process vs thread?**
A process has its own address space; threads in a process share heap memory but each has its own stack and program counter. Threads are cheaper to create and switch and can communicate through shared memory — which is exactly why we need synchronization.

**Q2. `Thread` vs `Runnable` vs `Callable` — which do you use?**
I define the work as a `Runnable` (no result) or `Callable` (returns a result, may throw checked exceptions) and submit it to an `ExecutorService`. Extending `Thread` mixes the task with the execution mechanism. In production I almost never create raw threads: executors give me bounded resources, naming and controlled shutdown. → `ThreadCreationExamples.java`

**Q3. `start()` vs `run()`? Can you start a thread twice?**
`start()` asks the JVM to create a new thread that then calls `run()`. Calling `run()` directly executes it on the current thread. Starting the same `Thread` twice throws `IllegalThreadStateException`.

**Q4. What are the thread states?**
`NEW`, `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING`, `TERMINATED`. There is no `RUNNING` state — `RUNNABLE` covers running, ready, and blocked in native I/O. `BLOCKED` is only for waiting to enter a `synchronized` block; a thread waiting for a `ReentrantLock` is `WAITING` (parked). → `ThreadLifecycleDemo.java`

**Q5. `sleep()` vs `wait()`?**
| | `sleep()` | `wait()` |
|---|---|---|
| Defined in | `Thread` (static) | `Object` |
| Releases monitor | No | Yes (only that monitor) |
| Must hold monitor | No | Yes, else `IllegalMonitorStateException` |
| Wakes up on | Timeout / interrupt | notify / notifyAll / timeout / interrupt / spurious wakeup |
| State | `TIMED_WAITING` | `WAITING` (`TIMED_WAITING` with timeout) |

**Q6. Why must `wait()` be called inside `synchronized` and inside a `while` loop?**
Holding the monitor makes "check the condition and start waiting" atomic with respect to "change the condition and notify"; otherwise the notification can arrive in between and be lost forever. The `while` loop is needed because another thread can consume the condition before we reacquire the monitor, and because spurious wakeups are allowed. → `WaitNotifyDemo.java` (demo 2 shows the `if` bug deterministically)

**Q7. `notify()` vs `notifyAll()`?**
`notify()` wakes one arbitrary waiter. It's only safe when all waiters wait for the same condition and any one of them can handle the signal. With producers and consumers on the same monitor, `notify()` can wake the wrong side and everyone ends up waiting — use `notifyAll()`, or a `Lock` with separate `Condition`s.

**Q8. How do you stop a thread correctly?**
Cooperatively: call `interrupt()`, and the task checks `isInterrupted()` or reacts to `InterruptedException` from blocking calls. When catching `InterruptedException`, either rethrow it or restore the flag with `Thread.currentThread().interrupt()` — never swallow it. `Thread.stop()` is deprecated because it releases locks in the middle of an update. Note that a thread `BLOCKED` on `synchronized` does not respond to interrupt; `lockInterruptibly()` does. → `ThreadInterruptionDemo.java`

**Q9. What is a daemon thread?**
A background thread that doesn't keep the JVM alive; the JVM exits when only daemon threads remain, and they are stopped abruptly (no `finally`). Use for housekeeping, never for work that must complete, like writing data. Must be set before `start()`. → `ThreadBasicsDemo.java`

**Q10. What happens to an exception thrown in a worker thread?**
It doesn't propagate to the parent thread. For a raw thread, it goes to the `UncaughtExceptionHandler` (default prints the stack trace). With `ExecutorService.submit()` it is captured in the `Future` and only seen on `get()`; with `execute()` it goes to the handler and the worker is replaced.

---

## 2. Java Memory Model

**Q11. What problems does the JMM solve?**
Compilers and CPUs cache values and reorder instructions as long as single-threaded behavior is preserved. Without rules, one thread may never see another's write (visibility) or may see writes in a surprising order (ordering). The JMM defines **happens-before**: if A happens-before B, B sees everything A did. → `JavaMemoryModelDemo.java`

**Q12. Name the happens-before rules.**
Program order within a thread; monitor unlock → subsequent lock of the same monitor; volatile write → subsequent read of the same variable; `Thread.start()` → actions in the started thread; actions in a thread → successful return from `join()`; transitivity. Also: final-field semantics after construction and static initialization.

**Q13. `volatile` vs `synchronized`?**
`volatile` gives visibility and ordering for single reads and writes, without locking and without mutual exclusion — so `count++` on a volatile still loses updates. `synchronized` gives mutual exclusion plus visibility, so it protects compound actions and invariants across several variables. I use `volatile` for flags and for publishing immutable snapshots. → `VolatileDemo.java`

**Q14. Why does double-checked locking need `volatile`?**
`instance = new Singleton()` is allocate, construct, assign — and construct/assign may be reordered. Without `volatile`, another thread can pass the unsynchronized first check, see a non-null reference and use a half-constructed object. A volatile write prevents that reordering and creates a happens-before edge to the reader. Simpler alternatives: the holder-class idiom or an enum singleton.

**Q15. Race condition vs data race?**
A data race is a JMM concept: two threads access the same variable, at least one writes, with no happens-before ordering. A race condition is a logic bug where the result depends on timing — it can exist without a data race, e.g., `get()` then `put()` on a `ConcurrentHashMap`. → `RaceConditionDemo.java`

**Q16. Does `final` make an object immutable/thread-safe?**
`final` fixes the reference, not the object it points to. It does give initialization safety: if the object is properly constructed (`this` doesn't escape the constructor), any thread that sees the reference sees the final fields' initialized values. True immutability needs final fields, no setters, defensive copies, and a final class. → `ImmutabilityDemo.java`

---

## 3. Locks & liveness

**Q17. `synchronized` method vs block?**
They differ in scope and lock object: an instance method locks `this` for the whole method; a block lets me choose the lock and protect only the critical section. I prefer a block on a `private final` lock object so outside code can't lock on it by accident. A block is not automatically faster — it helps only if it actually reduces lock hold time. → `SynchronizationDemo.java`

**Q18. Do two synchronized methods on different instances block each other? Static vs instance?**
No — each instance has its own monitor. Static synchronized methods lock the `Class` object, which is a different lock from any instance, so static and instance synchronized methods don't block each other. Protecting the same static field with both is a bug.

**Q19. What does "reentrant" mean?**
A thread that already holds a lock can acquire it again — e.g., a synchronized method calling another synchronized method on the same object. The lock keeps a hold count. Both `synchronized` and `ReentrantLock` are reentrant; `StampedLock` and `Semaphore` are not.

**Q20. `ReentrantLock` vs `synchronized`?**
`ReentrantLock` adds `tryLock` with timeout, interruptible acquisition, optional fairness and multiple `Condition`s, at the cost of having to `unlock()` in `finally`. I default to `synchronized` for simplicity and use `ReentrantLock` when I need one of those features. → `ExplicitLockDemo.java`

**Q21. When would you use `ReadWriteLock` or `StampedLock`?**
`ReadWriteLock` when reads dominate and the read section is long enough to benefit from concurrent readers; it has more overhead than a simple lock, writers can starve, and you can't upgrade read → write. `StampedLock` adds optimistic reads (read without locking, then `validate`), very fast for read-mostly data, but it's not reentrant and easy to misuse. Watch out: an operation that looks like a read may mutate state — `LinkedHashMap.get()` in access order does (see `4-interview-practice/ThreadSafeLruCache.java`).

**Q22. What is a deadlock? How do you prevent and detect it?**
Threads wait for each other forever. It requires all four Coffman conditions: mutual exclusion, hold and wait, no preemption, circular wait. Prevent with a global lock order (e.g., by account id), `tryLock` with timeout and randomized backoff, and by never calling unknown code while holding a lock. Detect with a thread dump (`jcmd <pid> Thread.print` prints "Found one Java-level deadlock") or `ThreadMXBean.findDeadlockedThreads()` for alerting. The JVM never resolves it for you. → `DeadlockDemo.java`

**Q23. Deadlock vs livelock vs starvation?**
Deadlock: threads are blocked and make no progress. Livelock: threads keep running and reacting to each other (yielding, retrying) but make no progress — CPU is busy, the thread dump shows `RUNNABLE`. Starvation: some thread never gets the resource, e.g., because of an unfair lock or long-running tasks hogging a pool. Fix livelock with randomized backoff; starvation with fair locks, shorter critical sections, or separate pools. → `LivelockStarvationDemo.java`

**Q24. What is `ThreadLocal` and what's the risk?**
A per-thread variable, used for request context (MDC, security context, transactions) or for non-thread-safe objects. In thread pools, threads live forever: if you don't `remove()` in `finally`, data leaks to the next task (wrong user!) and values are retained in memory. Values are not automatically propagated to other threads. → `ThreadLocalDemo.java`

---

## 4. Executors & thread pools

**Q25. Why use an `ExecutorService` instead of creating threads?**
Thread reuse (platform threads cost about 1 MB of stack and an OS call), a bound on concurrency and memory, queueing, named threads, results via `Future`, and controlled shutdown. → `ExecutorServiceDemo.java`

**Q26. How does `ThreadPoolExecutor` handle a new task?**
If fewer than `corePoolSize` threads exist, it starts a new thread. Otherwise it queues the task. If the queue is full and fewer than `maximumPoolSize` threads exist, it starts a new thread. Otherwise it rejects the task with the configured policy. Consequence: with an unbounded queue, the pool never grows beyond core, so `maximumPoolSize` is meaningless.

**Q27. What's wrong with `Executors.newFixedThreadPool` and `newCachedThreadPool` in production?**
Fixed uses an unbounded `LinkedBlockingQueue` — under overload tasks pile up until OOM, and nothing is ever rejected. Cached allows `Integer.MAX_VALUE` threads — a burst of slow tasks creates thousands of threads. I build a `ThreadPoolExecutor` with a bounded queue, an explicit rejection policy and a named thread factory.

**Q28. Rejection policies?**
`AbortPolicy` (default) throws `RejectedExecutionException`; `CallerRunsPolicy` runs the task on the submitting thread, which slows the producer down — natural back-pressure; `DiscardPolicy` silently drops (dangerous); `DiscardOldestPolicy` drops the oldest queued task. Note that after `shutdown()`, `CallerRunsPolicy` silently discards tasks.

**Q29. `execute()` vs `submit()`?**
`execute(Runnable)` returns nothing; an exception goes to the thread's `UncaughtExceptionHandler`. `submit()` returns a `Future` and captures the exception inside it — if nobody calls `get()`, the failure is silently lost. So with `submit()`, either check the future or catch and log inside the task.

**Q30. How do you size a thread pool?**
Start from the workload: CPU-bound ≈ number of cores; I/O-bound ≈ cores × (1 + wait time / compute time). But that's only a starting point — the real constraint is often a downstream resource, like a DB pool of 10 connections. Then I load test and watch active threads, queue depth, latency percentiles and CPU, and adjust. I also separate pools by workload type (bulkheads).

**Q31. Why can a bigger pool make things worse?**
If the bottleneck is elsewhere (DB, a downstream API, a lock), more threads just wait — while consuming memory, increasing context switches, overloading the downstream, and increasing tail latency. Little's Law (`L = λ × W`) also tells you that more concurrency at a fixed throughput only means longer waits.

**Q32. `shutdown()` vs `shutdownNow()`? How do you shut down gracefully?**
`shutdown()` stops accepting tasks and lets queued and running tasks finish. `shutdownNow()` interrupts running tasks and returns the tasks that never started. The graceful pattern: `shutdown()` → `awaitTermination(grace)` → if not done, `shutdownNow()` → `awaitTermination` again. Tasks must respond to interrupts for this to work. → `4-interview-practice/GracefulBatchProcessor.java`

**Q33. `scheduleAtFixedRate` vs `scheduleWithFixedDelay`?**
Fixed rate schedules from the start time; if a run takes longer than the period, the next run starts right after it finishes — runs of the same task never overlap. Fixed delay waits the delay after each run finishes. Watch out: an exception thrown by a periodic task silently cancels all future runs. → `ExecutorServiceDemo.java` demo 8

---

## 5. CompletableFuture

**Q34. `Future` vs `CompletableFuture`?**
`Future` can only be polled or blocked on with `get()`. `CompletableFuture` adds non-blocking composition (`thenApply`, `thenCompose`, `thenCombine`, `allOf`, `anyOf`), exception handling (`exceptionally`, `handle`, `whenComplete`), manual completion and timeouts (`orTimeout`, `completeOnTimeout`). → `CompletableFutureDemo.java`

**Q35. `thenApply` vs `thenCompose` vs `thenCombine`?**
`thenApply` maps the result with a synchronous function (like `map`). `thenCompose` chains a function that itself returns a `CompletableFuture` (like `flatMap`), avoiding `CompletableFuture<CompletableFuture<T>>`. `thenCombine` joins the results of two independent futures that run in parallel.

**Q36. Where does `supplyAsync()` run without an executor? Why does it matter?**
On `ForkJoinPool.commonPool()`, which is shared by the whole JVM (including parallel streams) and has about cores − 1 threads. Blocking I/O there starves everything else. I always pass a dedicated, bounded executor for I/O.

**Q37. How are exceptions handled? `join()` vs `get()`?**
The exception is stored in the future, wrapped in `CompletionException`. `join()` throws the unchecked `CompletionException`; `get()` throws the checked `ExecutionException`. If nobody joins or handles the future, the error disappears — so I end pipelines with `exceptionally`/`handle`/`whenComplete` to log or fall back.

**Q38. Does `orTimeout` cancel the underlying work?**
No. It completes the future exceptionally, but the task keeps running and keeps its thread. You need real timeouts at the client (HTTP/DB) level, or interrupt-based cancellation. Also, the timeout counts from when the stage is created, including time spent queued.

**Q39. What is thread-pool starvation deadlock?**
A task running in a pool blocks on (`get`/`join`) another task submitted to the same pool; when all threads do this, the inner tasks never get a thread. Avoid blocking inside pool tasks — compose with `thenCompose` — or use separate pools for the two levels. → `CompletableFutureDemo.java` demo 8

---

## 6. Atomics, concurrent collections, synchronizers

**Q40. How does `AtomicInteger` work? CAS?**
It combines a volatile value with compare-and-swap: read the current value, compute the new one, and `compareAndSet(expected, new)` — if another thread changed it in between, retry. It's lock-free, but under heavy contention CAS loops waste CPU. → `AtomicVariablesDemo.java`

**Q41. Atomic vs lock?**
Atomics for a single variable with simple updates. When an invariant spans several variables (debit A and credit B together), use a lock — two individually atomic operations are not atomic together.

**Q42. `AtomicLong` vs `LongAdder`?**
`LongAdder` spreads updates across cells to reduce contention and sums them on read, so it's much faster for write-heavy counters under contention, but `sum()` isn't an atomic snapshot. Use it for metrics, not for logic that needs an exact current value.

**Q43. What is the ABA problem?**
CAS only compares values: if a value goes A → B → A, a CAS expecting A succeeds although things changed. It matters for lock-free structures with reused nodes. `AtomicStampedReference` adds a version stamp.

**Q44. `ConcurrentHashMap` vs `Hashtable` vs `Collections.synchronizedMap`?**
Hashtable and synchronizedMap lock the whole map for every operation. `ConcurrentHashMap` (Java 8+) has lock-free reads and uses CAS plus per-bin locking for writes, so it scales. It doesn't allow null keys or values, because `get()` returning null would be ambiguous in a concurrent context. Iteration is weakly consistent. → `ConcurrentCollectionsDemo.java`

**Q45. Is code using `ConcurrentHashMap` automatically thread-safe?**
No. Individual operations are atomic, sequences are not: `if (!map.containsKey(k)) map.put(k, v)` or `put(k, get(k) + 1)` are race conditions. Use `putIfAbsent`, `computeIfAbsent`, `compute`, `merge`. The function passed to `compute*` must be short and must not modify the map.

**Q46. What happens if `HashMap` is used concurrently?**
Lost updates, exceptions, or a corrupted structure where `put()` spins forever at 100% CPU — this still happens on modern Java, not only Java 7. `ConcurrentCollectionsDemo` reproduces the hang on Java 21.

**Q47. `BlockingQueue` — what is it for?**
Producer–consumer without hand-written `wait/notify`: `put` blocks when full, `take` blocks when empty; `offer`/`poll` with timeouts don't block forever. A **bounded** queue gives back-pressure; an unbounded one hides overload until OOM. It's also the work queue inside every `ThreadPoolExecutor`. Stop consumers with a poison pill or interrupt.

**Q48. When is `CopyOnWriteArrayList` appropriate?**
Read-mostly lists with rare writes, such as listener lists: iteration needs no locking and never throws `ConcurrentModificationException`, but every write copies the whole array.

**Q49. `CountDownLatch` vs `CyclicBarrier` vs `Semaphore`?**
A latch lets threads wait until N events happen; it's one-shot. A barrier makes N threads wait for each other, and it's reusable across rounds. A semaphore limits how many threads can access a resource at once — a concurrency limit, for example 10 concurrent calls to a partner API. → `SynchronizersDemo.java`

---

## 7. Virtual threads

**Q50. What are virtual threads and when do they help?**
Lightweight threads (Java 21) scheduled by the JVM onto a small pool of carrier threads. When a virtual thread blocks on I/O, it unmounts and frees the carrier. You get thread-per-request, blocking-style code with very high concurrency for I/O-bound work. They don't make CPU-bound work faster. → `VirtualThreadsDemo.java`

**Q51. Should you pool virtual threads?**
No. They're cheap, so create one per task (`Executors.newVirtualThreadPerTaskExecutor()`). Pools exist to limit a scarce resource; to limit concurrency to a downstream, use a `Semaphore` or the connection pool.

**Q52. What is pinning?**
A virtual thread that can't unmount, so it holds its carrier while blocked: inside `synchronized` on Java 21–23 (fixed for `synchronized` in Java 24 by JEP 491), or during native calls. Mitigate with `ReentrantLock` around blocking calls on older JDKs; detect with `-Djdk.tracePinnedThreads=full` (Java 21–23) or the JFR event `jdk.VirtualThreadPinned`.

**Q53. Any other pitfalls?**
Removing the thread limit moves the bottleneck to downstream resources — a DB pool of 10 still allows only 10 queries. `ThreadLocal` caches multiplied by millions of threads waste memory (`ScopedValue` is final since Java 25). CPU-heavy tasks can monopolize carriers.

---

## 8. Spring

**Q54. How does `@Async` work? Why doesn't it work sometimes?**
Spring wraps the bean in a proxy that submits the method body to an executor. It doesn't work when: the method is called from the same class (self-invocation bypasses the proxy and runs synchronously), the method is `private`/`final` or the class is `final` (the proxy can't intercept it), or `@EnableAsync` is missing. → `3-spring-boot-app` (`/api/self-invocation` and `AsyncBehaviorTest`)

**Q55. Which executor does `@Async` use? How do you configure it?**
Plain Spring falls back to `SimpleAsyncTaskExecutor` (a new thread per task). Spring Boot auto-configures a `ThreadPoolTaskExecutor` whose queue is **unbounded** by default. I define named executors with bounded queues, explicit rejection policies, a thread name prefix, a `TaskDecorator`, and graceful shutdown — and I separate pools by workload. → `3-spring-boot-app/.../AsyncConfig.java`

**Q56. Exceptions in `@Async` methods?**
With a `CompletableFuture` return type, the exception completes the future exceptionally and the caller handles it. With `void`, the caller can't see it; Spring passes it to the `AsyncUncaughtExceptionHandler`, which by default only logs it.

**Q57. Why is the traceId missing in async logs / why is the user null?**
MDC and `SecurityContextHolder` are `ThreadLocal`-based and don't cross into pool threads. Use a `TaskDecorator` that captures context on submit and restores it in the worker, clearing it in `finally` (or `DelegatingSecurityContextAsyncTaskExecutor`, Micrometer context propagation).

**Q58. Does `@Transactional` propagate into an `@Async` method?**
No. Transactions are bound to the thread. The async method runs outside the caller's transaction and may not see uncommitted data. Trigger async work after commit (`@TransactionalEventListener(phase = AFTER_COMMIT)`) or use an outbox.

**Q59. `@Scheduled` fixedRate vs fixedDelay? Pitfalls?**
fixedRate measures from the start of each run, fixedDelay from the end. By default, Spring does **not** run overlapping executions of the same scheduled method — a slow fixedRate task just delays the next run (unless the method is also `@Async`). Pitfalls: Spring Boot's scheduler has a single thread by default, so one slow job delays all others (`spring.task.scheduling.pool.size`); with multiple instances every instance runs the job (use ShedLock and make the job idempotent).

**Q60. How do you monitor executors in Spring Boot?**
Actuator + Micrometer automatically publish `executor.active`, `executor.queued`, `executor.pool.size`, `executor.completed` etc. for `ThreadPoolTaskExecutor` beans (tagged by bean name). `/actuator/threaddump` gives a thread dump over HTTP.

---

## 9. Production & debugging

**Q61. How do you read a thread dump?**
Take three, 5–10 s apart (`jcmd <pid> Thread.print`, `jstack`, `kill -3`). Group threads by state and top frames: many `BLOCKED` on one lock (look at the owner), `WAITING` in `getConnection` (pool exhaustion), `RUNNABLE` in `socketRead` (slow downstream), workers `WAITING` in `Future.get` (starvation), a thread `RUNNABLE` in the same frame every time (hot loop). The end of the dump reports Java-level deadlocks. → `2-advanced-concurrency/ThreadDiagnosisDemo.java`

**Q62. How do you find the thread that burns CPU?**
`top -H -p <pid>` to get the hottest thread id, convert to hex, and find `nid=0x…` in the thread dump. Or use JFR / async-profiler for a CPU profile.

**Q63. Memory leaks related to threads?**
`ThreadLocal` values in pooled threads not removed; unbounded queues accumulating tasks; executors that are created per request and never shut down; threads holding references to large objects; classloader leaks on redeploy via `ThreadLocal`.

**Q64. What do you monitor for thread pools?**
Active vs max threads, queue size and remaining capacity, rejection count, task duration and time-in-queue (p99), completed rate — next to downstream latency and DB pool wait time. Alert on sustained saturation and on any rejection for user-facing pools.

**Q65. How do you test concurrent code?**
Stress tests with many threads released together through a `CountDownLatch` start gate, repeated many times, asserting invariants, with timeouts everywhere and exceptions propagated from workers. Awaitility for async assertions; jcstress for memory-model-level code; JMH for performance. A passing test doesn't prove correctness. → `4-interview-practice/ConcurrencyTestingDemo.java`

---

## 10. Behavioral

**Q66. Tell me about a concurrency issue you debugged.** (STAR template)
- **Situation:** intermittent wrong totals / timeouts in production under peak load.
- **Task:** find the root cause without being able to reproduce it locally.
- **Action:** correlated metrics (pool saturation, DB pool wait) with the incident window; took three thread dumps; found most workers waiting on `getConnection()` because a slow report query held connections; reproduced it with a load test.
- **Result:** shortened transactions, gave the report its own pool (bulkhead), added timeouts and saturation alerts; p99 dropped and the incident class disappeared.
- **Learning:** limits and timeouts are design decisions; observability must exist before the incident.

> Use your own real story if you have one. If you don't have production experience with a topic, say so honestly and explain how you would approach it — that's better than an invented story.

**Q67. How do you ensure thread safety in a codebase?**
Avoid shared mutable state (immutability, confinement), then use high-level utilities (concurrent collections, atomics, executors), then locks with documented guarding rules. Back it up with code review checklists, stress tests and production monitoring.

**Q68. When should you NOT use multithreading?**
When the work is small or sequential by nature and the coordination overhead exceeds the gain; when the real bottleneck is a single shared resource (one DB, one lock); when correctness risk outweighs a latency improvement nobody needs; and inside an already highly parallel server (e.g., parallel streams inside every request handler can starve the common pool).
