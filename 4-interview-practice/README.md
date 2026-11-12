# 4. Interview Practice — Coding Exercises & Tech Lead Scenarios

Modules 1–3 teach the concepts. This module is for **practising under interview conditions**.

## How to use

1. Read the problem statement at the top of each file — **not the solution**.
2. Write your own solution on paper or in a new file (15–30 minutes). Talk out loud while you write.
3. Run the provided solution and compare. Each file **checks itself** and prints ✓ / ✗.
4. Answer the "follow-up questions" printed at the end — interviewers almost always ask them.

Each file is a standalone program (JDK 21+):

```bash
cd 4-interview-practice
java EvenOddPrinter.java
```

## Coding exercises

| File | Priority | Problem | What the interviewer is checking |
|---|---|---|---|
| `EvenOddPrinter.java` | P1 | Two threads print 1..N alternately | Shared state under a lock, `wait` in `while`, clean termination; 3 solutions (wait/notify, Condition, Semaphore) |
| `ConcurrencyTestingDemo.java` | P0 | Thread-safe counter 4 ways + a test that proves the unsafe one is broken | Start gate, repetition, invariants, timeouts, exception propagation; why one green run proves nothing |
| `BoundedFanOut.java` | P0 | Call 20 downstreams, max 5 concurrent, per-call timeout, overall deadline, fallback | Bulkhead, timeouts at every layer, cancellation; `CompletableFuture` vs virtual threads trade-off |
| `GracefulBatchProcessor.java` | P0/P1 | Batch with bounded concurrency, retry + backoff + jitter, idempotency, graceful shutdown | Back-pressure, interrupt handling, `shutdown`/`shutdownNow` pattern, `CallerRunsPolicy` trap after shutdown |
| `TokenBucketRateLimiter.java` | P1 | Thread-safe rate limiter with bursts | Compound state under one lock, `nanoTime`, rate limit vs concurrency limit, distributed limits |
| `ThreadSafeLruCache.java` | P2 | O(1) LRU cache, thread-safe | The **ReadWriteLock trap** (`get()` mutates access order) — demonstrated failing |

**Interview English notes** — one file per exercise, same format as `1-core-fundamentals/` (Vietnamese notes → questions with *Key sentence* and a 30–45 s answer → sentences to memorize → vocabulary → self-test). Each question is tagged 🔴 Important · 🟡 Medium · 🟢 Low.

| # | File | Source |
|---|---|---|
| 01 | [`01-EvenOddPrinter.md`](01-EvenOddPrinter.md) | `EvenOddPrinter.java` |
| 02 | [`02-ConcurrencyTesting.md`](02-ConcurrencyTesting.md) | `ConcurrencyTestingDemo.java` |
| 03 | [`03-BoundedFanOut.md`](03-BoundedFanOut.md) | `BoundedFanOut.java` |
| 04 | [`04-GracefulBatchProcessor.md`](04-GracefulBatchProcessor.md) | `GracefulBatchProcessor.java` |
| 05 | [`05-TokenBucketRateLimiter.md`](05-TokenBucketRateLimiter.md) | `TokenBucketRateLimiter.java` |
| 06 | [`06-ThreadSafeLruCache.md`](06-ThreadSafeLruCache.md) | `ThreadSafeLruCache.java` |
| 07 | [`07-TechLeadScenarios.md`](07-TechLeadScenarios.md) | `TECH_LEAD_SCENARIOS.md` (short 45–60 s versions) |

Related exercises elsewhere in the repo:
- Producer–Consumer with `wait/notifyAll`: `1-core-fundamentals/WaitNotifyDemo.java`
- Producer–Consumer with `BlockingQueue` + poison pill: `2-advanced-concurrency/ConcurrentCollectionsDemo.java`
- Bounded buffer with `Lock` + two `Condition`s: `1-core-fundamentals/ExplicitLockDemo.java`
- Singleton (DCL / holder / enum): `1-core-fundamentals/JavaMemoryModelDemo.java`
- Diagnose and fix a deadlock: `1-core-fundamentals/DeadlockDemo.java`, `2-advanced-concurrency/ThreadDiagnosisDemo.java`

## Tech Lead scenarios

[`TECH_LEAD_SCENARIOS.md`](TECH_LEAD_SCENARIOS.md) — structured answers for:

1. High latency with low CPU
2. A thread-pool queue that keeps growing
3. Designing calls to a slow downstream
4. Worker pool much larger than the DB connection pool
5. Blocking vs `CompletableFuture` vs virtual threads vs reactive
6. CPU at 100% in production
7. Scheduled jobs running on multiple instances
8. Lost traceId / SecurityContext in async code
9. Thread-pool monitoring and alerting
10. Preventing concurrency bugs as a team

## Mock-interview checklist

- [ ] I can write `EvenOddPrinter` with `wait/notifyAll` in under 10 minutes without a hang at the end.
- [ ] I can explain why my concurrency test uses a start gate and repeats the run.
- [ ] I can whiteboard a fan-out with a semaphore, timeouts and fallbacks, and explain what `orTimeout` does **not** do.
- [ ] I can write the `shutdown → awaitTermination → shutdownNow → awaitTermination` pattern from memory.
- [ ] I can explain why an LRU cache can't use a read lock for `get()`.
- [ ] I can answer each scenario in `TECH_LEAD_SCENARIOS.md` aloud in 2–3 minutes, using the clarify → hypothesize → verify → fix → prevent structure.
