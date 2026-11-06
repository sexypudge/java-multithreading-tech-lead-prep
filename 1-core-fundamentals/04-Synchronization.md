# 04 -- Java Synchronization

**Source:** `SynchronizationDemo.java`\
**Purpose:** Understand the examples and comments in the source file,
then explain the concepts clearly in a Java interview.

------------------------------------------------------------------------

## 1. Technical Knowledge

### 1.1 What is synchronization?

Synchronization controls concurrent access to shared resources. In this
demo, it is used to protect a shared counter so that multiple threads do
not update it unsafely.

The source file demonstrates three synchronization approaches:

1.  Synchronized instance method --- locks the current instance
    (`this`).
2.  Synchronized block --- locks a specific object.
3.  Static synchronized method --- locks the corresponding `Class`
    object.

### 1.2 Why is `count++` not thread-safe?

The source's `UnsafeCounter` contains:

``` java
public void increment() {
    count++; // NOT atomic! Read-Modify-Write
}
```

`count++` is a read-modify-write operation. Conceptually, it has three
steps:

1.  Read the current value.
2.  Add one.
3.  Write the new value back.

If two threads interleave these steps, both may read the same old value
and then write the same new value. One increment is effectively lost.

This is a race condition. The exact result can depend on thread timing,
so the incorrect result may not appear in every run.

### 1.3 Synchronized instance method

``` java
static class SafeCounterMethod {
    private int count = 0;

    public synchronized void increment() {
        count++;
    }

    public synchronized int getCount() {
        return count;
    }
}
```

Key points:

-   An instance synchronized method uses the current object's intrinsic
    monitor lock (`this`).
-   A thread must acquire that object's lock before entering the method.
-   Another thread attempting to enter a synchronized instance method
    guarded by the same object must wait until the lock is released.
-   The lock is released when the synchronized method exits, including
    when it exits by throwing an exception.
-   Different instances have different instance locks. Threads can
    therefore execute synchronized instance methods concurrently on
    different objects.

In this example, both `increment()` and `getCount()` synchronize on the
same instance, protecting access to `count`.

### 1.4 Synchronized block

``` java
static class SafeCounterBlock {
    private int count = 0;
    private final Object lock = new Object();

    public void increment() {
        // Non-critical code here

        synchronized (lock) {
            count++;
        }

        // More non-critical code
    }

    public int getCount() {
        synchronized (lock) {
            return count;
        }
    }
}
```

Key points:

-   A synchronized block acquires the monitor lock of the object
    specified in parentheses.
-   Here, the lock is the dedicated `lock` object.
-   Only the code inside the block is protected by this lock.
-   Code outside the block does not require this lock and can execute
    concurrently.
-   The lock object must be shared by all threads that need to
    coordinate access to the same state.
-   The `final` reference helps ensure that this lock reference is not
    reassigned.

**Why use a block?** It allows a narrower lock scope and more
fine-grained control. This can reduce contention when only a small part
of a method needs protection.

**Performance qualification:** A synchronized block is not automatically
faster than a synchronized method. The benefit depends on whether
narrowing the critical section actually reduces lock holding time or
contention.

### 1.5 Static synchronized method

``` java
static class StaticCounter {
    private static int count = 0;

    public static synchronized void increment() {
        count++;
    }

    public static synchronized int getCount() {
        return count;
    }
}
```

Key points:

-   A static synchronized method locks the `Class` object, not an
    instance.
-   For this nested class, the lock is `StaticCounter.class`.
-   The lock is shared by calls to static synchronized methods of that
    same class.
-   This is suitable for protecting static state shared at class level.

**Compare the lock targets:**

  Form                           Lock target
  ------------------------------ --------------------------------------
  Synchronized instance method   `this`
  `synchronized (lock)` block    The specified `lock` object
  Static synchronized method     The declaring class's `Class` object

An instance lock and a class lock are different locks. Synchronizing on
one does not automatically block code synchronized on the other.

### 1.6 Synchronized method vs synchronized block

  -----------------------------------------------------------------------
  Aspect                  Synchronized method     Synchronized block
  ----------------------- ----------------------- -----------------------
  Protected scope         Entire method           Only the block

  Lock target             `this` for an instance  Explicitly specified
                          method; class object    object
                          for a static method     

  Control                 Simpler, broader scope  More fine-grained

  Possible performance    May hold the lock for   Can reduce lock scope
  benefit                 the whole method        if only part of the
                                                  method needs protection
  -----------------------------------------------------------------------

Do not say that a block is always faster. The accurate point is that a
block gives more control over the lock scope.

### 1.7 Reentrant intrinsic locks

The source demonstrates reentrancy:

``` java
static class ReentrantExample {
    public synchronized void outerMethod() {
        System.out.println("In outerMethod");
        innerMethod();
    }

    public synchronized void innerMethod() {
        System.out.println("In innerMethod - Reentrant lock works!");
    }
}
```

Execution flow:

1.  A thread calls `outerMethod()` on an object and acquires that
    object's monitor.
2.  While still inside `outerMethod()`, it calls `innerMethod()` on the
    same object.
3.  `innerMethod()` requires the same object's monitor.
4.  The calling thread can acquire that lock again because intrinsic
    locks are reentrant.
5.  The call does not deadlock merely because the thread already owns
    the lock.

**Key point:** A thread that already owns an intrinsic lock can re-enter
synchronized code guarded by that same lock.

### 1.8 Synchronizing on `null`

The source's interview tips ask what happens when synchronizing on a
null object.

``` java
Object lock = null;

synchronized (lock) {
    // This block cannot be entered
}
```

This throws `NullPointerException`. A monitor must be associated with a
valid object; `null` is not an object on which a thread can acquire a
monitor.

### 1.9 Performance impact and lock contention

The source's interview tips connect performance impact with lock
contention and context switching.

-   **Lock contention** occurs when multiple threads compete to acquire
    the same lock.
-   Threads that cannot acquire the lock must wait.
-   Waiting and scheduling can add overhead; context switching may
    contribute to that overhead.
-   Excessive contention can reduce throughput and increase latency.

Practical ways to reduce unnecessary contention:

-   Keep critical sections short.
-   Minimize the amount of code protected by a lock.
-   Avoid synchronizing code that does not access the protected shared
    state.
-   Consider an appropriate concurrent collection or another concurrency
    primitive when it fits the use case.

Do not claim that every lock wait necessarily causes a context switch.
The important interview point is that contention and scheduling overhead
can hurt performance.

### 1.10 Alternatives to `synchronized`

The source lists these alternatives:

  -----------------------------------------------------------------------
  Alternative                         Typical use
  ----------------------------------- -----------------------------------
  `ReentrantLock`                     Explicit lock management and
                                      additional locking features

  `ReadWriteLock`                     Workloads where reads can safely
                                      happen concurrently while writes
                                      require exclusive access

  `StampedLock`                       Read/write locking with an
                                      optimistic-read mode; requires
                                      careful use

  Atomic variables                    Atomic operations on individual
                                      variables, such as counters

  Concurrent collections              Thread-safe collection operations
  -----------------------------------------------------------------------

These are not interchangeable drop-in replacements. Choose based on the
operation that must be made safe and the required concurrency behavior.

### 1.11 Understanding the demo and its helper method

The `testCounter(...)` helper:

1.  Creates the requested number of threads.
2.  Starts each thread.
3.  Each thread runs the increment task repeatedly.
4.  Calls `join()` on every thread so the main thread waits for all
    workers to finish.
5.  Compares the actual counter value with `threads * increments`.
6.  Prints the elapsed time and whether the result matches the expected
    value.

For the configured values:

-   Threads: `8`
-   Increments per thread: `100_000`
-   Expected final count: `8 * 100_000 = 800_000`

The unsafe counter typically ends far below the expected count (a run
on a 14-core machine produced about 170,000) because increments are
lost. The three synchronized implementations always reach 800,000.
`join()` matters twice: it makes `main` wait for the workers, and it
creates a happens-before edge so `main` sees all of their writes.

`main()` runs four demos: the counters, reentrancy, **lock targets**
(same instance blocks ≈ 600 ms; different instances and static vs
instance do not block ≈ 300 ms), and edge cases (`synchronized(null)`
throws NPE; an exception inside a synchronized block still releases
the monitor).

------------------------------------------------------------------------

## 2. Interview English

### Main question: What is synchronization in Java, and how does `synchronized` work?

**Target length: approximately 60--90 seconds**

Synchronization in Java is a mechanism for controlling access to shared
resources when multiple threads execute concurrently. It helps prevent
race conditions when threads update shared mutable data.

Java provides the `synchronized` keyword, which uses an object's
intrinsic monitor lock.

There are three common forms. First, a synchronized instance method
locks the current object, which is `this`. Second, a synchronized block
locks a specific object and allows us to protect only the critical
section. Third, a static synchronized method locks the corresponding
`Class` object, so it can protect class-level shared state.

When one thread holds a particular lock, another thread must wait before
entering code guarded by that same lock. However, threads using
different lock objects do not block each other just because both
sections are synchronized.

Java's intrinsic locks are also reentrant. This means a thread that
already owns a lock can acquire the same lock again, for example when
one synchronized method calls another synchronized method on the same
object.

In practice, I try to keep critical sections small to reduce unnecessary
lock contention. However, a synchronized block is not automatically
faster; its benefit depends on whether it reduces the time spent holding
the lock.

### Short answer: What is the difference between a synchronized method and a synchronized block?

A synchronized method protects the entire method, while a synchronized
block protects only a specific section of code. An instance synchronized
method locks `this`, whereas a synchronized block lets us choose the
lock object. A block gives us more fine-grained control and may reduce
lock contention if only a small part of the method needs
synchronization.

### Short answer: What is the difference between instance and static synchronization?

An instance synchronized method locks the current object, so different
instances have different locks. A static synchronized method locks the
class's `Class` object. Therefore, instance synchronization protects
access coordinated through an instance lock, while static
synchronization can coordinate access to class-level shared state.

### Short answer: What does reentrant mean?

A reentrant lock allows the thread that already owns the lock to acquire
it again. For example, a synchronized method can call another
synchronized method on the same object without deadlocking on that same
intrinsic lock.

### Short answer: What is lock contention, and how can it affect performance?

Lock contention happens when multiple threads compete for the same lock.
Threads that cannot acquire it have to wait, which can add scheduling
overhead and reduce throughput. We can reduce unnecessary contention by
keeping critical sections short and avoiding synchronization around code
that does not need it.

------------------------------------------------------------------------

## 3. Key Vocabulary and Collocations

  Phrase                          Meaning
  ------------------------------- --------------------------------------------------
  shared resource                 tài nguyên dùng chung
  shared mutable state            trạng thái dùng chung có thể thay đổi
  execute concurrently            thực thi đồng thời
  control access to               kiểm soát quyền truy cập vào
  prevent a race condition        ngăn race condition
  update shared data              cập nhật dữ liệu dùng chung
  read-modify-write operation     thao tác đọc -- sửa -- ghi
  acquire a lock                  lấy lock
  hold a lock                     nắm giữ lock
  release a lock                  giải phóng lock
  intrinsic monitor lock          monitor lock tích hợp của object
  critical section                vùng code cần được bảo vệ
  lock scope                      phạm vi lock
  fine-grained control            khả năng kiểm soát chi tiết
  lock contention                 tranh chấp lock
  class-level shared state        trạng thái dùng chung ở cấp class
  reentrant lock                  lock cho phép acquire lại bởi thread đang sở hữu
  reduce unnecessary contention   giảm tranh chấp không cần thiết

Useful sentence patterns:

-   "The method is guarded by the object's monitor lock."
-   "Other threads must wait until the lock is released."
-   "The block allows us to narrow the critical section."
-   "Different instances have different intrinsic locks."
-   "A static synchronized method locks the corresponding Class object."
-   "This can reduce contention, depending on the lock scope."

------------------------------------------------------------------------

## 4. Interview Questions -- Self-test

Try to answer each question without reading the notes.

### Fundamentals

1.  What is synchronization in Java, and why do we need it?
2.  Why is `count++` not an atomic operation?
3.  What happens when two threads call a synchronized method on the same
    object?
4.  What is the difference between a synchronized method and a
    synchronized block?
5.  What is the difference between instance synchronized and static
    synchronized methods?

### Understanding and application

6.  Can two threads execute synchronized instance methods at the same
    time on two different objects? Explain why.
7.  What happens if we use `synchronized` with a null reference?
8.  What does reentrant mean in Java? Walk through the
    `ReentrantExample`.
9.  What is lock contention, and how can it affect application
    performance?
10. Why can a synchronized block reduce contention compared with
    synchronizing an entire method?
11. What alternatives to `synchronized` are mentioned in the source, and
    when might each be useful?
12. What does the `testCounter()` helper do, and why does it call
    `join()` before checking the result?

------------------------------------------------------------------------

## 5. Five Key Takeaways

If you only review five points before an interview, remember these:

1.  `count++` is a read-modify-write operation, not an atomic operation.
2.  An instance synchronized method locks `this`; a static synchronized
    method locks the `Class` object.
3.  A synchronized block locks the explicitly specified object and
    protects only the block.
4.  Java intrinsic locks are reentrant.
5.  Keep critical sections appropriately small to reduce unnecessary
    lock contention; do not assume a synchronized block is always
    faster.

------------------------------------------------------------------------

## 6. Review Tracker

-   [ ] I can explain the three synchronization forms and their lock
    targets.
-   [ ] I can explain why `count++` can lose updates.
-   [ ] I can trace the `ReentrantExample` execution flow.
-   [ ] I can explain null synchronization and its exception.
-   [ ] I can discuss lock contention and performance trade-offs.
-   [ ] I can name the alternatives listed in the source.
-   [ ] I can answer the self-test questions in English without reading
    the notes.

**Suggested review cadence**

-   **Day 1:** Understand the technical notes and practise the main
    interview answer.
-   **Day 2:** Answer the self-test questions without looking at the
    file.
-   **Day 3--7:** Revisit weak questions and practise explaining the
    code execution flow.
-   **After several topics:** Include Synchronization in a cumulative
    Multithreading mock interview.
