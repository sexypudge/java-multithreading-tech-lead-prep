
## Java Multithreading – Interview Answer Sheet
**Topic: `wait()`, `notify()`, `notifyAll()` and Producer-Consumer**

### Question 1: What problem does the Producer-Consumer pattern solve?

**Interview answer:**

The Producer-Consumer pattern is used to coordinate two threads that share a common queue.

The Producer creates items and adds them to the queue, while the Consumer retrieves and processes those items.

If the queue is full, the Producer has to wait until the Consumer removes an item. If the queue is empty, the Consumer has to wait until the Producer adds an item.

We can use `wait()` and `notify()` to coordinate these threads.

**Key points:**
- Queue full → Producer waits.
- Queue empty → Consumer waits.
- Producer adds an item → notify waiting Consumer.
- Consumer removes an item → notify waiting Producer.

---

### Question 2: Why do we use `while` instead of `if` when calling `wait()`?

**Interview answer:**

We use a `while` loop instead of an `if` statement because a thread can wake up unexpectedly due to a spurious wakeup.

Also, after receiving a notification, the condition might have changed before the thread reacquires the lock.

Therefore, we need to check the condition again before continuing execution.

**Key points:**
- Spurious wakeups can happen.
- A notification does not guarantee the condition is still true.
- Always recheck the condition after waking up.

Example:

```java
synchronized (lock) {
    while (queue.isEmpty()) {
        lock.wait();
    }

    // Consume item
}
```

---

### Question 3: What is the difference between `wait()` and `sleep()` in Java?

**Interview answer:**

`wait()` is an instance method of the `Object` class, while `sleep()` is a static method of the `Thread` class.

The main difference is that `wait()` releases the object's monitor lock, but `sleep()` does not release any lock.

A thread waiting with `wait()` can be awakened by `notify()`, `notifyAll()`, interruption, or a timeout if using timed wait.

A sleeping thread wakes up after the specified time or when interrupted.

**Key comparison:**

| Feature | `wait()` | `sleep()` |
|---|---|---|
| Defined in | Object | Thread |
| Releases monitor lock | Yes | No |
| Requires synchronized context | Yes | No |
| Can be awakened by notify | Yes | No |
| Supports timeout | Yes | Yes |

---

### Question 4: What's the difference between `notify()` and `notifyAll()`?

**Interview answer:**

`notify()` wakes up one arbitrary thread waiting on the object's monitor.

`notifyAll()` wakes up all threads waiting on the same monitor.

However, waking up does not mean the threads can immediately continue execution. They must first compete to reacquire the monitor lock.

I generally prefer `notifyAll()` when multiple threads are waiting for different conditions because it is safer and avoids some notification-related problems.

**Key points:**
- `notify()` → wakes one arbitrary waiting thread.
- `notifyAll()` → wakes all waiting threads.
- Woken threads must reacquire the monitor.
- Always recheck the condition using `while`.

---

### Question 5: What is the correct way to use `wait()` and `notify()`?

**Example: Producer-Consumer**

```java
class ProducerConsumer {
    private final Queue<Integer> queue = new LinkedList<>();
    private final int capacity = 5;

    public synchronized void produce(int item)
            throws InterruptedException {

        while (queue.size() == capacity) {
            wait();
        }

        queue.add(item);

        notifyAll();
    }

    public synchronized int consume()
            throws InterruptedException {

        while (queue.isEmpty()) {
            wait();
        }

        int item = queue.poll();

        notifyAll();

        return item;
    }
}
```

**Important rules:**

1. Call `wait()`, `notify()`, and `notifyAll()` only while holding the same object's monitor.
2. `wait()` releases that monitor.
3. `notify()` does not release the monitor immediately.
4. Use `while` to check conditions.
5. Prefer `BlockingQueue` in production code instead of implementing Producer-Consumer manually.

---

## Final Review – What You Should Remember

| Concept | Most important takeaway |
|---|---|
| `wait()` | Releases monitor and waits |
| `notify()` | Wakes one arbitrary waiting thread |
| `notifyAll()` | Wakes all waiting threads |
| `sleep()` | Does not release monitor |
| `while` vs `if` | Always recheck the condition |
| Producer-Consumer | Coordinate threads through a shared queue |
