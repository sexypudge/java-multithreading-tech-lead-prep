# Java Concurrency Interview Checklist — Senior / Tech Lead

**Time budget:** 2 weeks  
**Target:** Senior Java Developer / Tech Lead interviews  
**Goal:** Build a strong interview-ready understanding of concurrency, not master every API in two weeks.

## How to use this checklist

- **[P0 — MUST LEARN]** Highest return for the two-week window. Understand the mechanism, explain trade-offs, and write a small example without copying.
- **[P1 — SHOULD LEARN]** Important for Senior/Tech Lead interviews. Learn after P0 or sooner if already familiar with P0.
- **[P2 — NICE TO HAVE]** Useful depth; skim or learn only if time remains or a job description calls for it.
- **[P3 — SKIP FOR NOW]** Defer during this two-week sprint. This does not mean the topic is unimportant in production.

**Study rule:** For every P0 topic, be able to (1) explain it in 60–90 seconds, (2) show a small code example, (3) identify a common bug, and (4) describe when you would use it in a real Java service.

---

## 1. Core Fundamentals (`1-core-fundamentals/`)

### [P0 — MUST LEARN]

- [ ] **Thread creation:** `Thread`, `Runnable`, `Callable`; starting a thread vs calling `run()`; when to use a task abstraction rather than manually creating threads.
- [ ] **Thread lifecycle and states:** `NEW`, `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING`, `TERMINATED`; `start()`, `sleep()`, `join()`.
- [ ] **Synchronization:** `synchronized` methods and blocks; object monitor vs class lock; mutual exclusion and visibility.
- [ ] **Race conditions:** lost updates, check-then-act, read-modify-write; how to reproduce and fix a race.
- [ ] **Java Memory Model (JMM):** visibility, atomicity, ordering; why a thread may not see another thread’s update.
- [ ] **Happens-before:** program order, monitor unlock/lock, volatile write/read, `Thread.start()`, and successful return from `join()`.
- [ ] **`volatile`:** visibility and ordering; why `count++` is not atomic; appropriate use cases such as a shutdown flag.
- [ ] **`wait()` / `notify()` / `notifyAll()`:** must own the monitor, wait in a `while` loop, condition predicates, spurious wakeups, and why `sleep()` is different.
- [ ] **`interrupt()` and `InterruptedException`:** cooperative cancellation, checking interrupt status, restoring the interrupt flag when catching `InterruptedException`, and safe task shutdown.
- [ ] **Deadlock and race conditions:** how they differ; common deadlock conditions; lock ordering as a prevention strategy.
- [ ] **Thread-safe design:** minimize shared mutable state; immutable objects; safe publication; when `final` helps and what it does not guarantee.

### [P1 — SHOULD LEARN]

- [ ] **`ReentrantLock`:** `lock()`, `unlock()` in `finally`, `tryLock()`, `lockInterruptibly()`, fairness at a high level; compare with `synchronized`.
- [ ] **`Condition`:** `await()`, `signal()`, `signalAll()`; how they compare with `wait()` / `notify()`.
- [ ] **`ThreadLocal`:** typical uses and memory/data-leak risks when threads are reused by a pool; cleanup with `remove()`.
- [ ] **Livelock and starvation:** definitions, examples, and ways to reduce the risk.
- [ ] **Thread-control APIs:** daemon threads, `isInterrupted()`, `yield()`; know what they do and their limitations.

### [P2 — NICE TO HAVE]

- [ ] **`ReadWriteLock`:** use cases and write/read trade-offs.
- [ ] **`StampedLock`:** optimistic reads, limitations, and why it is more complex than `ReadWriteLock`.
- [ ] **Detailed monitor internals:** deeper JVM-level implementation details beyond the contract and observable behavior.

### [P3 — SKIP FOR NOW]

- [ ] Memorizing low-level JVM implementation details of every lock implementation. Focus first on contracts, correct usage, and trade-offs.

---

## 2. Advanced Concurrency (`2-advanced-concurrency/`)

### 2.1 Executors and Thread Pools

#### [P0 — MUST LEARN]

- [ ] **`Executor`, `ExecutorService`, `ScheduledExecutorService`:** why task execution should usually be separated from task definition.
- [ ] **`execute()` vs `submit()`:** exception behavior, returned `Future`, and when each is useful.
- [ ] **`Callable`, `Future`, `get()`, `cancel()`:** retrieving results, blocking, and cancellation limitations.
- [ ] **`ThreadPoolExecutor` core mechanics:** `corePoolSize`, `maximumPoolSize`, `keepAliveTime`, queue, and `ThreadFactory`.
- [ ] **Task admission order:** core threads, queueing, growth toward maximum size, then rejection; understand that queue choice changes pool behavior.
- [ ] **Rejection policies:** `AbortPolicy`, `CallerRunsPolicy`, `DiscardPolicy`, `DiscardOldestPolicy`; trade-offs and safe defaults.
- [ ] **Shutdown:** `shutdown()` vs `shutdownNow()`, await termination, graceful shutdown, and interruption.
- [ ] **Pool sizing basics:** CPU-bound vs I/O-bound work; why formulas are starting points rather than rules; use metrics and load tests.
- [ ] **Thread-pool failure modes:** unbounded queues, task backlog, blocked workers, oversubscription, and silent task loss.

#### [P1 — SHOULD LEARN]

- [ ] **Pool types:** fixed, single-thread, cached, scheduled, and custom executors; understand risks in factory-created pools.
- [ ] Custom thread names and uncaught exception handling.
- [ ] Separating pools for workloads with different latency/resource needs.
- [ ] Limiting concurrency and applying backpressure rather than allowing an unbounded backlog.

#### [P2 — NICE TO HAVE]

- [ ] Deep implementation details of `ThreadPoolExecutor` hooks, worker bookkeeping, and custom executor subclasses.

### 2.2 CompletableFuture and Asynchronous Composition

#### [P0 — MUST LEARN]

- [ ] `supplyAsync()` vs `runAsync()`.
- [ ] `thenApply()` vs `thenCompose()` vs `thenCombine()`.
- [ ] `thenAccept()`, `thenRun()`, `allOf()`, `anyOf()`.
- [ ] Error handling: `exceptionally()`, `handle()`, `whenComplete()`.
- [ ] `join()` vs `get()`.
- [ ] Default executor vs passing an explicit executor; the risk of blocking work on `ForkJoinPool.commonPool()`.
- [ ] Parallel independent calls, composing dependent calls, timeout, and error propagation.

#### [P1 — SHOULD LEARN]

- [ ] Cancellation behavior and why cancellation may not stop underlying work automatically.
- [ ] Avoiding excessive blocking inside completion stages.
- [ ] Limiting concurrent calls when fanning out to multiple downstream services.

#### [P2 — NICE TO HAVE]

- [ ] Complex custom `CompletableFuture` subclasses and uncommon completion-stage APIs.

### 2.3 Atomic Variables and CAS

#### [P0 — MUST LEARN]

- [ ] `AtomicInteger`, `AtomicLong`, `AtomicReference` and common atomic operations.
- [ ] Compare-and-set (CAS), `compareAndSet()`, `getAndIncrement()`, `updateAndGet()`.
- [ ] When an atomic variable is sufficient and when a lock is clearer or necessary.
- [ ] Why multiple individually atomic operations do not automatically make a multi-step business operation atomic.

#### [P1 — SHOULD LEARN]

- [ ] CAS retry loops and contention.
- [ ] `LongAdder` vs `AtomicLong`; understand the workload-dependent trade-off.
- [ ] ABA problem and what `AtomicStampedReference` addresses.

#### [P2 — NICE TO HAVE]

- [ ] Designing complex lock-free data structures from scratch.
- [ ] Low-level memory-ordering APIs beyond the basic interview expectations.

### 2.4 Concurrent Collections

#### [P0 — MUST LEARN]

- [ ] **`ConcurrentHashMap`:** thread-safe access and atomic compound methods such as `putIfAbsent()`, `computeIfAbsent()`, `compute()`, and `merge()`.
- [ ] `ConcurrentHashMap` vs `Hashtable` vs `Collections.synchronizedMap()`.
- [ ] **`BlockingQueue`:** `put()` / `take()` vs `offer()` / `poll()`; bounded queues and backpressure.
- [ ] **Producer–Consumer:** implement using `BlockingQueue` and explain why it is generally safer than hand-written `wait()` / `notify()` for this use case.
- [ ] Weakly consistent iteration and why a concurrent collection does not make every multi-operation workflow atomic.

#### [P1 — SHOULD LEARN]

- [ ] `ArrayBlockingQueue` vs `LinkedBlockingQueue` vs `SynchronousQueue` and their capacity/admission trade-offs.
- [ ] `CopyOnWriteArrayList`: read-heavy/write-light use cases and write cost.
- [ ] `ConcurrentLinkedQueue` and non-blocking queue use cases.
- [ ] High-level understanding of `ConcurrentHashMap` contention management and implementation differences across Java versions.

#### [P2 — NICE TO HAVE]

- [ ] Memorize bucket-level implementation details or every internal concurrency optimization of `ConcurrentHashMap`.

### 2.5 Synchronizers

#### [P0 — MUST LEARN]

- [ ] `CountDownLatch`: wait until a set of tasks reaches completion.
- [ ] `CyclicBarrier`: coordinate threads at a shared phase/barrier.
- [ ] `Semaphore`: cap concurrent access to a constrained resource.
- [ ] Explain when a latch, barrier, or semaphore is the right fit.

#### [P2 — NICE TO HAVE]

- [ ] `Phaser`: synchronization across multiple phases or dynamically changing parties.
- [ ] `Exchanger`: exchange data between two threads.

### 2.6 Fork/Join and Parallel Streams

#### [P1 — SHOULD LEARN]

- [ ] `ForkJoinPool`, `RecursiveTask`, `RecursiveAction`, divide-and-conquer, and work-stealing.
- [ ] Parallel Streams: when they can help and when overhead outweighs the benefits.
- [ ] Common-pool contention and risks of blocking I/O in parallel streams.

#### [P2 — NICE TO HAVE]

- [ ] Implementing custom recursive fork/join algorithms and tuning pool internals.

### 2.7 Virtual Threads — Java 21+

#### [P0 — MUST LEARN]

- [ ] Platform threads vs virtual threads.
- [ ] `Thread.ofVirtual()` and `Executors.newVirtualThreadPerTaskExecutor()`.
- [ ] Why virtual threads can help I/O-heavy workloads but do not make CPU-bound work intrinsically faster.
- [ ] Why virtual threads generally should not be pooled like platform threads.
- [ ] Resource limits still exist: database connections, downstream capacity, rate limits, and memory.
- [ ] Compare virtual threads, bounded platform-thread pools, and reactive programming at a high level.

#### [P1 — SHOULD LEARN]

- [ ] Carrier threads and scheduler basics.
- [ ] Pinning: what it means, why JDK version matters, and how to investigate it when relevant.
- [ ] Virtual threads with Spring Boot and the constraints of the application’s Spring Boot/JDK versions.

### 2.8 Structured Concurrency and Scoped Values

#### [P2 — NICE TO HAVE]

- [ ] Parent/child task relationships, cancellation and error handling for groups of tasks.
- [ ] Structured Concurrency vs manually coordinating many `Future` instances or `CompletableFuture` tasks.
- [ ] Scoped Values and how they differ conceptually from `ThreadLocal`.
- [ ] Check the status and syntax for the target JDK; these APIs have been preview/incubator features in some releases.

---

## 3. Spring Boot Application (`3-spring-boot-app/`)

### 3.1 `@Async` and Spring Task Execution

#### [P0 — MUST LEARN]

- [ ] `@EnableAsync`, `@Async`, and how the call is dispatched to an executor.
- [ ] Configure a named `ThreadPoolTaskExecutor`.
- [ ] `@Async` returning `void` vs `Future` / `CompletableFuture` and how errors are observed.
- [ ] Self-invocation/proxy limitation: why calling an `@Async` method from another method on the same bean often does not run asynchronously.
- [ ] Exceptions from `@Async` tasks and the limitations of handling exceptions from `void` return methods.
- [ ] `@Async` vs `ExecutorService` vs `CompletableFuture`.

#### [P1 — SHOULD LEARN]

- [ ] Propagating MDC/trace IDs and `SecurityContext` using appropriate task decorators or context-propagation mechanisms.
- [ ] Why a thread-bound transaction does not automatically carry over to a new async thread; transaction boundaries and explicit design.
- [ ] Separate executors for different workload classes.

### 3.2 Scheduled Tasks

#### [P1 — SHOULD LEARN]

- [ ] `@Scheduled`: fixed rate vs fixed delay vs cron.
- [ ] Scheduler thread pool and handling task failures or long-running jobs.
- [ ] Why multiple application instances can execute the same scheduled task.
- [ ] Distributed coordination with ShedLock or another appropriate mechanism; idempotency remains important.

### 3.3 Thread Pool Tuning and Resource Management

#### [P0 — MUST LEARN]

- [ ] Tune from observed workload and measurements rather than blindly applying a formula.
- [ ] Relationship between application executor size and HikariCP/database connection-pool size.
- [ ] What happens when the worker pool greatly exceeds downstream capacity.
- [ ] Bounded queues, timeouts, rejection, and graceful shutdown.

#### [P1 — SHOULD LEARN]

- [ ] Virtual-thread configuration such as `spring.threads.virtual.enabled=true` only when supported by the chosen Spring Boot/JDK versions.
- [ ] Workload isolation, bulkheads and concurrency limits.

### 3.4 Monitoring and Observability

#### [P0 — MUST LEARN]

- [ ] Spring Boot Actuator and Micrometer basics.
- [ ] Observe pool size, active threads, queue depth, completed tasks and rejected tasks where metrics are available.
- [ ] Correlate thread-pool saturation with latency, throughput, error rates, database pool waits and downstream latency.
- [ ] Log thread name and correlation/trace ID where appropriate.

#### [P1 — SHOULD LEARN]

- [ ] Alerting thresholds and dashboard design for executor saturation.
- [ ] JFR/metrics-based investigation of elevated latency with low CPU utilization.

### 3.5 Resilience and Shutdown

#### [P1 — SHOULD LEARN]

- [ ] Timeouts, bounded retries with backoff, circuit breaker, bulkhead, and rate limiting.
- [ ] Cancellation of tasks after request timeout and avoiding unnecessary work.
- [ ] Graceful application shutdown and what to do with in-flight tasks.
- [ ] Idempotency when jobs or requests may be retried.

#### [P2 — NICE TO HAVE]

- [ ] Detailed Resilience4j integration configuration and advanced patterns beyond the core concepts.

---

## 4. Troubleshooting and Testing (`4-troubleshooting-and-testing/`)

### 4.1 Troubleshooting

#### [P0 — MUST LEARN]

- [ ] Read a basic thread dump using `jstack` or `jcmd`.
- [ ] Recognize `BLOCKED`, `WAITING`, and `TIMED_WAITING` patterns.
- [ ] Identify a deadlock from a thread dump and explain a practical next step.
- [ ] Diagnose pool saturation, blocked workers, queue growth and rejected tasks.
- [ ] Distinguish CPU saturation, thread starvation, connection-pool exhaustion and slow downstream dependencies.

#### [P1 — SHOULD LEARN]

- [ ] Java Flight Recorder (JFR) and Java Mission Control (JMC) at a practical level.
- [ ] VisualVM or an equivalent profiler.
- [ ] Investigate `ThreadLocal` leaks and memory retained by long-lived pooled threads.
- [ ] Investigate virtual-thread behavior with tools supported by the target JDK.

### 4.2 Concurrency Testing

#### [P0 — MUST LEARN]

- [ ] Write a repeatable test that exposes a race condition.
- [ ] Use `CountDownLatch` to coordinate thread starts and improve test reproducibility.
- [ ] Assert correctness under concurrent updates; avoid relying only on `sleep()` in tests.

#### [P1 — SHOULD LEARN]

- [ ] Awaitility for waiting on asynchronous conditions in tests.
- [ ] Distinguish unit tests, stress tests and benchmarks.
- [ ] JMH basics and why naive timing loops are unreliable.

#### [P2 — NICE TO HAVE]

- [ ] jcstress for deeper Java Memory Model/concurrency correctness testing.
- [ ] Advanced benchmark design, profilers and performance test infrastructure.

### 4.3 Performance and Capacity Planning

#### [P1 — SHOULD LEARN]

- [ ] Throughput, latency, p95/p99, saturation and queueing.
- [ ] Little’s Law: **L = λ × W** (average number in system = arrival rate × average time in system), and what it can/cannot tell you.
- [ ] Measure before and after changes; validate tuning with representative load tests.
- [ ] Distinguish latency improvements from throughput improvements.

#### [P2 — NICE TO HAVE]

- [ ] Detailed capacity models and mathematically elaborate pool-sizing exercises.

---

## 5. Interview Practice (`5-interview-practice/`)

### 5.1 Coding Exercises

#### [P0 — MUST LEARN]

- [ ] Thread-safe counter: compare `synchronized`, `AtomicInteger`, and a lock.
- [ ] Producer–Consumer with `BlockingQueue`.
- [ ] Execute multiple independent tasks with `CompletableFuture` and combine results.
- [ ] Implement bounded concurrency for calls/tasks.
- [ ] Graceful executor shutdown and interrupt-aware cancellation.
- [ ] Two threads coordinating work, using a synchronizer or a condition correctly.

#### [P1 — SHOULD LEARN]

- [ ] Print even/odd numbers using two threads.
- [ ] Producer–Consumer using `wait()` / `notifyAll()` to understand the low-level mechanism.
- [ ] Thread-safe Singleton with correct double-checked locking and `volatile`.
- [ ] A simple rate limiter or concurrency limiter.
- [ ] Diagnose and fix a supplied deadlocking example.

#### [P2 — NICE TO HAVE]

- [ ] Dining Philosophers.
- [ ] Thread-safe LRU Cache.
- [ ] More elaborate custom synchronizers or lock-free structures.

### 5.2 Interview Questions

#### [P0 — MUST LEARN]

- [ ] Why does `start()` differ from `run()`?
- [ ] Why can the same `Thread` object not be started twice?
- [ ] What does `volatile` guarantee, and what does it not guarantee?
- [ ] How do `synchronized` and happens-before relate to visibility and mutual exclusion?
- [ ] `wait()` vs `sleep()`; `notify()` vs `notifyAll()`.
- [ ] `execute()` vs `submit()`; `Future` vs `CompletableFuture`.
- [ ] How does a `ThreadPoolExecutor` decide to queue, grow or reject a task?
- [ ] How would you respond to a saturated executor or an ever-growing queue?
- [ ] `AtomicInteger` vs a lock; `ConcurrentHashMap` vs synchronized maps.
- [ ] When would you choose platform threads, virtual threads, or reactive programming?
- [ ] Why can increasing pool size make latency or stability worse?
- [ ] Why can an application have high latency while CPU utilization remains low?
- [ ] How do you propagate trace IDs/security context through asynchronous execution?
- [ ] How do you debug deadlock or thread starvation in production?

#### [P1 — SHOULD LEARN]

- [ ] `CountDownLatch` vs `CyclicBarrier` vs `Semaphore`.
- [ ] `synchronized` vs `ReentrantLock`.
- [ ] `AtomicLong` vs `LongAdder`.
- [ ] How do you size the application executor relative to the database connection pool?
- [ ] What are common `@Async` pitfalls in Spring?
- [ ] How do retry, timeout, bulkhead and cancellation interact?

#### [P2 — NICE TO HAVE]

- [ ] Explain detailed internals of every concurrent collection or synchronizer.
- [ ] Explain advanced Structured Concurrency APIs if the target role does not specify a recent JDK.

### 5.3 Tech Lead Scenarios

#### [P0 — MUST LEARN]

- [ ] A service has high latency and low CPU: how would you investigate?
- [ ] A thread pool’s queue keeps growing: what metrics do you check and what options do you consider?
- [ ] A service calls a slow downstream API: design bounded concurrency, timeout, cancellation and fallback behavior.
- [ ] The worker pool is much larger than the database pool: describe the likely bottleneck and options.
- [ ] Explain the trade-offs between blocking execution, `CompletableFuture`, virtual threads and reactive programming for a given workload.

#### [P1 — SHOULD LEARN]

- [ ] Design a batch processor with bounded concurrency, retries, idempotency and graceful shutdown.
- [ ] Design monitoring and alerts for thread-pool saturation.
- [ ] Discuss how to choose pool settings based on measurements and realistic load tests.

#### [P2 — NICE TO HAVE]

- [ ] Design a system processing 10,000 tasks/second in detail, including end-to-end capacity assumptions and downstream constraints. Treat the number as a scenario, not a universal benchmark.

---

## 6. Two-Week Plan (Recommended)

This plan intentionally prioritizes the highest-value material. Do not attempt to master every P1/P2 item before interviewing.

| Day | Focus | Required output |
|---:|---|---|
| 1 | Thread creation, lifecycle, interruption, `join()` | Explain `start()` vs `run()`, lifecycle and safe cancellation. Write a tiny runnable example. |
| 2 | Race conditions, `synchronized`, `volatile` | Reproduce a lost update and fix it. Explain visibility vs atomicity. |
| 3 | JMM and happens-before | Explain the key happens-before rules and connect them to code examples. |
| 4 | `wait/notifyAll`, `ReentrantLock`, deadlock | Implement a simple producer-consumer or coordination exercise; diagnose a deadlock. |
| 5 | `ExecutorService`, `Future`, shutdown | Explain `execute()` vs `submit()`, cancellation and graceful shutdown. |
| 6 | `ThreadPoolExecutor` and rejection | Draw the queue/growth/rejection flow. Explain bounded queues and CallerRuns/Abort policies. |
| 7 | `CompletableFuture` | Implement independent parallel calls, dependent composition and exception handling. Review Days 1–6. |
| 8 | Atomics and concurrent collections | Compare `AtomicInteger`/lock and use `ConcurrentHashMap.computeIfAbsent()` correctly. |
| 9 | `BlockingQueue` and synchronizers | Implement Producer–Consumer with `BlockingQueue`; explain latch, barrier and semaphore. |
| 10 | Virtual threads | Explain use cases, limitations, resource bottlenecks and comparison with pools/reactive. |
| 11 | Spring `@Async`, context, transactions | Create a small example and explain proxy/self-invocation and exception behavior. |
| 12 | Pool sizing, HikariCP, metrics, scheduling | Explain thread-pool/database-pool mismatch and how to investigate saturation. |
| 13 | Thread dump, troubleshooting, scenario questions | Read a sample dump or practice interpreting states; answer 3 production scenarios aloud. |
| 14 | Mock interview and review | Answer P0 questions without notes; revisit weak topics. Do not start large new topics. |

### If time becomes tight

Keep these at the top of the list:

1. JMM, happens-before, `synchronized`, `volatile`, race conditions.
2. `ExecutorService`, `ThreadPoolExecutor`, queueing/rejection, shutdown.
3. `CompletableFuture`, exception handling and executor selection.
4. `ConcurrentHashMap`, atomic variables and `BlockingQueue`.
5. Virtual-thread trade-offs.
6. Spring `@Async`, context/transaction boundaries, pool sizing and database capacity.
7. Thread dump basics and production troubleshooting.

Defer P2/P3 topics before sacrificing practice, mock interview answers, English explanations, or the other interview subjects you are preparing in parallel.

---

## 7. Suggested Structure for Each Study Example

For every implemented topic, keep a small folder with:

```text
<topic-name>/
├── README.md       # Concept, when to use it, trade-offs, common pitfalls
├── Example.java    # Small runnable example
└── Exercise.md     # One problem and expected behavior
```

For each topic, record:

- **Concept:** Explain it in your own words.
- **Problem solved:** What bug or design problem does it address?
- **Example:** A minimal runnable demonstration.
- **Pitfall:** A common incorrect implementation.
- **Trade-off:** What does this approach cost or constrain?
- **Interview answer:** A 60–90 second English explanation.

## Final Recommendation

For a two-week Senior/Tech Lead interview sprint, aim to **explain and demonstrate P0 topics**, understand the trade-offs of P1 topics, and deliberately defer P2/P3 unless a target job description makes them necessary. Interview readiness comes from being able to reason about behavior and diagnose failures—not from checking every item off the list.
