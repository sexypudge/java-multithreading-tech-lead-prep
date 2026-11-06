# Tech Lead Scenarios — Concurrency in Production

At Senior/Tech Lead level, interviewers care less about API trivia and more about **how you reason about a running system**. Each scenario below has a structured answer you can speak in 2–3 minutes.

**Answer framework (use it every time):**
1. **Clarify** — what are the symptoms, what changed, what do the metrics say?
2. **Hypothesize** — list 2–4 likely causes, most probable first.
3. **Verify** — which data (metrics, thread dump, JFR, logs) confirms or kills each hypothesis?
4. **Fix** — short-term mitigation vs. long-term fix, and their trade-offs.
5. **Prevent** — monitoring, alerts, tests, review checklist.

---

## S1. High latency, but CPU is low. How do you investigate?

**Key insight:** low CPU + high latency means threads are **waiting**, not working. The question is *waiting for what*.

**Likely causes**
- Thread pool saturated (all workers busy/waiting, requests queue up)
- Waiting on a downstream: DB connection pool exhausted, slow HTTP dependency without timeouts
- Lock contention (many threads `BLOCKED` on one monitor)
- Thread-pool starvation deadlock (tasks waiting on tasks in the same pool)
- GC pauses (would usually show CPU spikes in GC threads — check GC logs anyway)

**How to verify**
- **Thread dump** ×3, 5–10 s apart (`jcmd <pid> Thread.print` or `/actuator/threaddump`):
  - many threads `WAITING` in `HikariPool.getConnection` → connection pool exhausted
  - many `RUNNABLE` in `socketRead0` → slow downstream / missing read timeout
  - many `BLOCKED` on the same lock, with one owner → contention
  - workers `WAITING` in `Future.get` / `CompletableFuture.join` → starvation
- **Metrics:** executor `active == max` and `queued` growing; Hikari `pending` > 0; downstream p99 latency.
- **JFR:** `jdk.JavaMonitorEnter`, `jdk.ThreadPark`, socket read events show where time goes.

**Fix**
- Short term: add/shorten timeouts, raise the *right* limit (often the downstream, not the thread pool), shed load.
- Long term: bulkheads (separate pools per dependency), circuit breaker, async/virtual threads for I/O fan-out, remove I/O from inside locks.

> **Sample answer:** "Low CPU with high latency tells me threads are waiting rather than computing. I'd take three thread dumps a few seconds apart and look at where most threads are parked — connection pool, socket reads, a monitor, or a `Future.get`. In parallel I'd check executor metrics — active versus max and queue depth — and the database pool's pending count. In my experience the most common cause is a slow downstream without a proper timeout, which then exhausts the worker pool. The fix is timeouts at every layer and isolating that dependency with its own bounded pool or a bulkhead, so one slow service can't take the whole application down."

---

## S2. A thread pool's queue keeps growing. What do you check and what are your options?

**Meaning:** arrival rate > service rate (Little's Law: `L = λ × W`). Either more work is arriving, or each task got slower.

**Check**
- Arrival rate (did traffic spike? a retry storm?) vs. task duration (did a downstream slow down?)
- `activeCount` vs `maximumPoolSize`, `completedTaskCount` rate
- Is the queue **unbounded**? (`Executors.newFixedThreadPool`, Spring Boot default `queue-capacity`) → it will end in OOM, and `maxPoolSize` is never used.
- Thread dump of the workers: what are they blocked on?

**Options (with trade-offs)**
| Option | When | Cost |
|---|---|---|
| Bound the queue + rejection policy | Always | Callers must handle rejection |
| `CallerRunsPolicy` | Internal producers that can slow down | Caller thread latency increases (back-pressure) |
| Fail fast (`AbortPolicy`) + 429/503 | User-facing requests | Some requests fail, but the system survives |
| Fix the slow dependency / add timeouts | Task duration increased | Requires root-cause work |
| Scale out | Real capacity shortage | Cost; may just move the bottleneck downstream |
| Bigger pool | Only if the bottleneck is really threads | Often makes it worse (see S4) |

> **Sample answer:** "A growing queue means work arrives faster than we complete it, so I first find out which side changed — more arrivals, or slower tasks. An unbounded queue just hides the problem until we run out of memory, so I'd bound it and choose an explicit rejection policy: `CallerRunsPolicy` for internal producers to get natural back-pressure, or fail fast with a 503 for user traffic. Then I'd fix the actual cause, which is usually a slower dependency. I would not just increase the pool size without knowing where the bottleneck is."

---

## S3. Your service calls a slow downstream API. Design the interaction.

**Design** (see `BoundedFanOut.java`)
1. **Timeouts** at every layer: connect timeout, read timeout, per-call timeout, overall request deadline.
2. **Bounded concurrency** to that downstream (Semaphore / bulkhead / dedicated small pool) — protects *them* and *us*.
3. **Circuit breaker** — stop calling when the error rate is high; fail fast and let it recover.
4. **Retries** — only for transient errors, bounded, exponential backoff **with jitter**, and only for idempotent operations.
5. **Fallback / degradation** — cached or default data; mark the response as partial.
6. **Cancellation** — when the caller gives up, stop the work (interrupt, `cancel(true)`), don't let orphan work pile up.
7. **Observability** — latency histogram, error rate, breaker state, bulkhead saturation.

**Trade-offs to mention:** retries multiply load (retry storms); timeouts too short cause false failures; fallbacks can hide outages — alert on fallback rate.

---

## S4. The worker pool is much larger than the database connection pool (200 vs 10).

**What happens:** at most 10 threads can talk to the DB; 190 are parked in `getConnection()`. You gain nothing but memory usage, context switching, longer tail latency, and connection-timeout errors under load.

**Options**
- Size the executor relative to the real bottleneck: roughly the DB pool size for DB-bound work (plus a little headroom).
- Size the **DB pool** by what the database can handle — more connections can make the DB slower (HikariCP's guideline starts around `cores × 2 + effective spindles` on the DB server).
- Separate pools: DB-bound work vs. HTTP-bound work vs. CPU work (bulkheads).
- Shorten connection hold time: no remote calls inside a transaction, smaller transactions.
- With virtual threads: you have no thread limit at all, so the **connection pool (or a Semaphore) becomes the only limiter** — size it deliberately.

> **Sample answer:** "If 200 workers share 10 connections, the database pool is the real limit and 190 threads are just waiting. A bigger worker pool doesn't add throughput; it adds memory, context switching and tail latency. I'd align the executor with the connection pool, keep transactions short and without remote calls, and isolate DB work from other I/O. If we move to virtual threads, this becomes even more important, because the connection pool is then the only thing limiting concurrency."

---

## S5. Blocking vs `CompletableFuture` vs virtual threads vs reactive — which one?

| Approach | Strengths | Weaknesses | Good fit |
|---|---|---|---|
| Blocking + bounded platform pool | Simple, debuggable | Thread count caps concurrency; threads are expensive | Moderate concurrency, CPU-bound work |
| `CompletableFuture` on executors | Composition: fan-out/fan-in, timeouts, fallbacks | Callback style, harder stack traces, executor choice matters | Combining several independent calls |
| Virtual threads (Java 21+) | Blocking style with massive I/O concurrency; real stack traces | Not faster for CPU work; pinning (synchronized on Java 21–23, native); still need limits for downstreams; ThreadLocal cost | Thread-per-request I/O-heavy services |
| Reactive (WebFlux/Reactor) | Back-pressure built in, streaming, very efficient | Steep learning curve, whole stack must be non-blocking, hard debugging | Streaming, very high fan-out, existing reactive stack |

> **Sample answer:** "I decide based on the workload and the team. For CPU-bound work, a pool sized near the core count is right and virtual threads won't help. For typical I/O-bound request handling on Java 21, virtual threads give me blocking-style code that is easy to read and debug, but I still put explicit limits — a semaphore or the connection pool — in front of every downstream. I use `CompletableFuture` when I need to compose several independent calls with timeouts and fallbacks. I'd choose reactive only if we need streaming or back-pressure end to end and the team is already comfortable with it, because the cost in complexity is high."

---

## S6. Production CPU is at 100%. What do you do?

1. Confirm it's the JVM and whether it's application threads or GC (`top`, GC logs/metrics).
2. Find the hot threads: `top -H -p <pid>` → convert TID to hex → find `nid=0x…` in the thread dump (or `ThreadDiagnosisDemo` shows the same via `ThreadMXBean`).
3. Take several dumps; a thread `RUNNABLE` in the same frame every time is the culprit. Common culprits: infinite loop, catastrophic regex backtracking, **`HashMap` corrupted by concurrent writes** (see `ConcurrentCollectionsDemo`), busy-spin loops, livelock retries.
4. If GC: heap too small or a memory leak (e.g., `ThreadLocal` in pools, unbounded queues/caches) → heap dump.
5. Mitigate (restart/scale/feature flag), then fix and add a regression test + alert.

---

## S7. A scheduled job runs twice / overlaps after scaling to 3 instances.

- Every instance runs `@Scheduled` → 3 executions. Fix with a distributed lock (ShedLock with a DB/Redis table), leader election, or move the job to a dedicated worker/queue.
- Make the job **idempotent** anyway (locks expire, instances crash).
- Spring Boot's scheduler has **1 thread** by default — a slow job delays all others; size `spring.task.scheduling.pool.size`.
- Long jobs must handle interruption for graceful shutdown.

---

## S8. Logs from `@Async` code lose the traceId / the user is `null`.

- MDC and `SecurityContextHolder` are `ThreadLocal`-based; pool threads don't inherit them.
- Fix: `TaskDecorator` that captures on submit and restores in the worker, then **clears in `finally`** (see `3-spring-boot-app/AsyncConfig.java`); `DelegatingSecurityContextAsyncTaskExecutor` for Spring Security; Micrometer context-propagation for tracing.
- Also: `@Transactional` context doesn't cross threads — async work does not see uncommitted data. Trigger async work **after commit** (`@TransactionalEventListener(phase = AFTER_COMMIT)`) or via an outbox.

---

## S9. How do you design monitoring and alerts for thread pools?

**Metrics per pool (Micrometer `executor.*` in Spring Boot):** active threads, pool size, queue size and remaining capacity, completed rate, rejected count, task duration (p50/p99), wait-in-queue time.

**Alerts (examples — tune to your SLOs)**
- `active / max > 90%` for 5 min → saturation
- queue remaining capacity < 20% → back-pressure imminent
- any rejections on a user-facing pool → page
- task p99 > SLO → investigate downstream

**Also:** named threads (`order-io-3`, not `pool-7-thread-3`), dashboards that put executor saturation next to downstream latency and DB pool wait time, and a runbook entry for "take 3 thread dumps".

---

## S10. How do you prevent concurrency bugs in your team (process question)?

- **Design:** prefer immutability, confinement, and higher-level utilities (`ConcurrentHashMap.merge`, `BlockingQueue`, executors) over hand-written locks and `wait/notify`.
- **Document** thread-safety guarantees of classes (`@ThreadSafe`/`@NotThreadSafe`, Javadoc stating which lock guards which field).
- **Code review checklist:** shared mutable state? check-then-act? I/O inside a lock? unbounded queue? swallowed `InterruptedException`? `ThreadLocal` without `remove()`? `join()` inside a pool task? timeouts on every remote call?
- **Testing:** stress tests with a start gate (see `ConcurrencyTestingDemo.java`), Awaitility for async, jcstress for low-level code, load tests before tuning.
- **Runtime:** metrics, named threads, thread dump runbooks, alerts on saturation.
