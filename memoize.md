# Java Multithreading — Interview Practice Day 1

## 1. What is the difference between `start()` and `run()`?

**Answer:**

The `start()` method starts a new thread, and the JVM executes the `run()` method on that thread.

If we call `run()` directly, it executes on the current thread like a normal method.

If we call `start()` twice on the same Thread object, it throws an `IllegalThreadStateException` because a thread can only be started once.

## 2. What are the main thread states in Java?

**Answer:**

There are six main thread states in Java: `NEW`, `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING`, and `TERMINATED`.

## 3. What is the state of a thread waiting to enter a synchronized block?

**Answer:**

It is in the `BLOCKED` state because it is waiting to acquire a monitor lock held by another thread.

## 4. Why doesn't `Thread.sleep()` release the lock inside a synchronized block?

**Answer:**

Because `Thread.sleep()` pauses the current thread but does not release the monitor lock it holds.

## 5. What are the common ways to create tasks or threads in Java?

**Answer:**

There are three common approaches:

1. Extend the `Thread` class.
2. Implement the `Runnable` interface.
3. Implement the `Callable` interface and execute it using an executor.

In practice, we usually prefer `Runnable` or `Callable` with an executor because this separates task logic from thread management.

## 6. When would you use Runnable instead of Callable?

**Answer:**

I would use `Runnable` when I don't need to return a result from the task.

I would use `Callable` when I need to return a result or throw a checked exception.

## 7. What does the BLOCKED state mean?

**Answer:**

The `BLOCKED` state means a thread is waiting to acquire a monitor lock that another thread holds.

## Review notes

- `start()` starts a new thread; `run()` is a normal method when called directly.
- `sleep()` does not release a monitor lock.
- `BLOCKED` means waiting to acquire a monitor lock.
- `Runnable` is for tasks without a return value; `Callable` can return a result.
- A Java `Thread` object can be started only once.

## 8. Why does wait() release the monitor lock, while sleep() does not?

**Answer:**

The main difference is that `wait()` is used for thread coordination, while `sleep()` is used to pause a thread for a specific period of time.

When a thread calls `wait()`, it releases the monitor lock, allowing other threads to acquire it.

However, `sleep()` does not release any monitor locks. The thread keeps holding the lock while sleeping.

I would use `wait()` when threads need to coordinate based on a shared condition, and `sleep()` when I simply need to pause execution for a certain amount of time.


## 9.  lock.wait() notify() must be called in synchronized block or method, if not, JVM will throw IllegalMonitorStateException

## 10.  "t1.start(); t2.start(); t1.join(); t2.join();" khác gì so với "t1.start(); t1.join(); t2.start(); t2.join();" ?

**Answer:**

A thread always has to wait when calling join().

in the first example, 2 threads can run in parallel. in the second one, main thread have to wait for t1 to end before starting t2
