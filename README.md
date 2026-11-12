# Java Multithreading — Tech Lead Interview Preparation

Runnable demos, notes and exercises for Senior / Tech Lead interviews on Java concurrency.
Every demo **terminates on its own and prints ✓ / ✗**, so you can see each bug happen and each fix work.

## 🗂️ Structure

```
1-core-fundamentals/     Threads, lifecycle, race conditions, synchronized, volatile/JMM, wait/notify,
                         interruption, explicit locks, deadlock/livelock, ThreadLocal, immutability
                         + interview notes in English (04-Synchronization, 05-Volatile, 06-JavaMemoryModel)
2-advanced-concurrency/  ThreadPoolExecutor, CompletableFuture, atomics/CAS, concurrent collections,
                         synchronizers, virtual threads, Fork/Join, thread-dump diagnosis
3-spring-boot-app/       @Async + bounded executors, MDC propagation, exceptions, timeouts, back-pressure,
                         @Scheduled, executor metrics — with tests (mvn test)
4-interview-practice/    Coding exercises with self-checking solutions + Tech Lead scenario answers
INTERVIEW_QUESTIONS.md   68 questions with interview-ready answers + 5-minute cheat sheet
Priority.md              P0–P3 checklist and a 2-week study plan
STUDY_PLAN.md            3-week fast-track plan (Vietnamese): day-by-day files + 🔴 questions, checkpoints, mocks
```

> **Ôn nhanh:** theo [`STUDY_PLAN.md`](STUDY_PLAN.md) — mỗi ngày đã ghi rõ file và câu 🔴 cần học.

## 🚀 Running

Requires **JDK 21+** (virtual threads, `ExecutorService.close()`); Maven 3.9+ for the Spring app.

```bash
# Modules 1, 2, 4: every file is a standalone program — run the source directly
cd 1-core-fundamentals
java RaceConditionDemo.java

# Module 3
cd 3-spring-boot-app
mvn test
mvn spring-boot:run
```

On Windows, if Vietnamese text or symbols look broken: `java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 X.java`.

## 📖 How to study

Follow the 2-week plan in [`Priority.md`](Priority.md). For each topic:

1. **Predict** the output before running a demo.
2. **Run it several times** — concurrency bugs are non-deterministic; "no bug this time" is not proof.
3. **Break it**: remove `volatile`/`synchronized`, change `while` → `if`, `notifyAll` → `notify`, use an unbounded queue.
4. **Explain it aloud in English in 60–90 seconds**, then compare with the `INTERVIEW TIPS` at the end of the file and with [`INTERVIEW_QUESTIONS.md`](INTERVIEW_QUESTIONS.md).
5. Practise the exercises and scenarios in [`4-interview-practice/`](4-interview-practice/) without looking at the solution first.

### If you only have 3 days

| Day | Focus |
|---|---|
| 1 | `RaceConditionDemo`, `SynchronizationDemo`, `VolatileDemo`, `JavaMemoryModelDemo`, `DeadlockDemo` |
| 2 | `ExecutorServiceDemo`, `CompletableFutureDemo`, `ConcurrentCollectionsDemo`, `VirtualThreadsDemo` |
| 3 | Spring app (`README` + `curl`), `TECH_LEAD_SCENARIOS.md`, cheat sheet, mock interview |

## 💡 What makes a Tech Lead answer

- Explain **why** and the **trade-off**, not just the API.
- Talk about **limits**: bounded queues, timeouts, concurrency limits on downstreams.
- Talk about **observability**: named threads, executor metrics, thread dumps, alerts.
- Know when **not** to add concurrency, and when the bottleneck is elsewhere (DB pool, downstream, lock).
- Be honest: "I haven't run X in production, but here is how it works and how I'd validate it" beats an invented story.

## 📚 Resources

- *Java Concurrency in Practice* — Brian Goetz et al. (still the reference for fundamentals)
- JEP 444 (Virtual Threads), JEP 491 (synchronized without pinning, Java 24), JEP 506 (Scoped Values, Java 25)
- `java.util.concurrent` Javadoc — especially `ThreadPoolExecutor`, `ExecutorService` (shutdown pattern), `CompletableFuture`
- Spring docs: Task Execution and Scheduling; Spring Boot: Task Execution, Virtual Threads, Actuator metrics
