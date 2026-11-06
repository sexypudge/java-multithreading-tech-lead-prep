Sure, I'll package today's Q&A into Anki-style notes. Here are the Anki-style notes from today: The four Coffman conditions are: mutual exclusion, hold and wait, no preemption,, and circular wait. For corrections: say "no preemption", not "can force to release the force a lock". Say "I would prevent deadlocks by enforcing a consistent lock ordering." Tomorrow, review these six Anki cards. Then, practice English answers with correct verb agreement and clearer phrases. Okay, good job today. We'll build step by step.

# Java Multithreading – Deadlock Interview Notes
**Date:** November 5, 2026  
**Topic:** Deadlock – Interview Practice  
**Level:** Senior Java Developer

Hôm nay anh trả lời khá tốt về mặt kiến thức. Anh đã nắm được các concept chính của Deadlock. Điểm cần cải thiện nhiều nhất là cách diễn đạt tiếng Anh sao cho ngắn gọn, chính xác và tự nhiên hơn.

Dưới đây là answer sheet và Anki cards để anh ôn tập.

---

## Part 1. Interview Answer Sheet

### Q1. What are the four conditions required for a deadlock?

**Correct answer:**

There are four necessary conditions for a deadlock, known as Coffman conditions:

1. **Mutual Exclusion:** Only one thread can hold a lock at a time.
2. **Hold and Wait:** A thread holds one lock while waiting for another lock.
3. **No Preemption:** A lock cannot be forcibly taken away from a thread.
4. **Circular Wait:** Two or more threads wait for each other's locks in a circular chain.

**Điểm cần nhớ:**

Anh trả lời đúng cả bốn điều kiện.

Riêng No Preemption, cần nhớ:
- Không thể force một thread release lock.
- Thread phải tự release lock.

### Q2. How would you detect a deadlock in a running Java service?

**Correct answer:**

I would capture a thread dump using `jstack` or `jcmd`.

Then I would inspect the thread states, lock ownership, and waiting relationships to identify a circular wait pattern.

**Commands:**

```bash
jstack -l <PID>
```

Or:

```bash
jcmd <PID> Thread.print -l
```

### Q3. Do two BLOCKED threads automatically mean deadlock?

**Correct answer:**

No. Two BLOCKED threads do not necessarily mean deadlock.

I need to inspect the lock ownership and waiting relationships to determine whether there is a circular dependency.

**Key point:**

BLOCKED ≠ DEADLOCK.

### Q4. How would you prevent deadlocks in Java?

**Correct answer:**

There are several techniques to prevent deadlocks:

1. **Lock Ordering:** Always acquire locks in a consistent order.
2. **tryLock(timeout):** Avoid waiting indefinitely for a lock.
3. **Avoid Nested Locks:** Reduce the number of simultaneously held locks.
4. **Minimize Lock Scope:** Hold locks for the shortest possible time.

Lock ordering is usually the simplest prevention technique when we control the lock acquisition order.

### Q5. What is deadlock?

**Correct answer:**

A deadlock occurs when two or more threads are waiting for each other's locks, so none of them can proceed.

### Q6. What's one tool to confirm deadlock in production?

**Correct answer:**

I would use `jstack` to capture a thread dump and analyze the lock ownership and circular waiting relationships.

---

## Part 2. Anki Flashcards

Anh chỉ cần tạo 6 cards sau. Không cần đưa toàn bộ answer sheet vào Anki.

| # | Front (Question) | Back (Keywords) |
|---|---|---|
| 1 | What are the four Coffman conditions? | Mutual Exclusion; Hold and Wait; No Preemption; Circular Wait |
| 2 | What is deadlock? | Threads waiting for each other's locks; None can proceed |
| 3 | How to detect deadlock in Java? | jstack; jcmd; Thread dump; Lock ownership; Circular wait |
| 4 | Do two BLOCKED threads mean deadlock? | No; Check lock ownership; Identify circular dependency |
| 5 | How to prevent deadlock? | Lock ordering; tryLock(timeout); Avoid nested locks; Minimize lock scope |
| 6 | What is No Preemption? | Cannot forcibly take a lock; Thread must release voluntarily |

### Anki card example

**Front:**

What are the four conditions required for a deadlock?

**Back:**

- Mutual Exclusion
- Hold and Wait
- No Preemption
- Circular Wait

Try to explain each condition in your own words.

---

## Part 3. English Improvements

Một số câu anh đã trả lời hôm nay cần chỉnh lại:

| Your answer | Better version |
|---|---|
| Circular wait | Circular wait. |
| Can force to release the lock | No preemption. |
| I can detect a deadlock using thread dump with JS stack. | I can detect a deadlock using a thread dump with jstack. |
| Two threads are waiting to get the lock from each other threads. | Two threads are waiting for each other's locks. |
| I would like to prevent the deadlock by using lock ordering. | I would prevent deadlocks by enforcing a consistent lock ordering. |
| I will using JS stack. | I would use jstack. |

**English practice priority:**

Anh nên tập trung vào:
- Subject–verb agreement: `threads are`, không phải `threads is`.
- Conditional sentences: `If I detect... I would...`
- Technical vocabulary: `acquire a lock`, `release a lock`, `lock ownership`, `circular dependency`.

---

## Part 4. Review Plan

| Time | Activity |
|---|---|
| Tomorrow (D+1) | Review 6 Anki cards |
| D+3 | Explain all four Coffman conditions |
| D+7 | Practice deadlock interview again |
| D+14 | Explain deadlock prevention without notes |
| D+30 | Cumulative multithreading review |

## Đánh giá buổi học hôm nay

| Topic | Assessment |
|---|---|
| Coffman conditions | Good |
| Deadlock detection | Good |
| Thread dump analysis | Good |
| Deadlock prevention | Good |
| English expression | Needs improvement |

**Điều quan trọng nhất cần nhớ hôm nay:**

1. Deadlock requires four Coffman conditions.
2. BLOCKED threads do not automatically mean deadlock.
3. Thread dump helps identify lock ownership and circular waiting.
4. Lock ordering prevents circular wait.
5. `tryLock(timeout)` allows a thread to give up acquiring a lock instead of waiting indefinitely.