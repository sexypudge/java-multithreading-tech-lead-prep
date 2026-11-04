# Advanced Concurrency - Kỹ thuật hiện đại

## 📋 Danh sách Examples

### 1. ExecutorServiceDemo.java
Thread Pool management:
- Fixed, Cached, Scheduled thread pools
- Submit vs Execute
- Shutdown strategies
- Best practices

**Interview question:** "Làm sao tune thread pool size?"

### 2. CompletableFutureDemo.java
Async programming hiện đại:
- thenApply, thenCompose, thenCombine
- Exception handling
- Chaining operations
- Parallel execution

**Interview question:** "CompletableFuture vs Future?"

### 3. ForkJoinDemo.java
Divide-and-conquer parallelism:
- RecursiveTask vs RecursiveAction
- Work stealing
- When to use

**Interview question:** "Fork/Join vs ExecutorService?"

### 4. ConcurrentCollectionsDemo.java
Thread-safe collections:
- ConcurrentHashMap
- CopyOnWriteArrayList
- BlockingQueue
- Performance comparison

**Interview question:** "ConcurrentHashMap vs Hashtable?"

### 5. AtomicVariablesDemo.java
Lock-free programming:
- AtomicInteger, AtomicLong, AtomicReference
- Compare-And-Swap (CAS)
- Performance benefits

**Interview question:** "Atomic vs synchronized?"

### 6. SynchronizersDemo.java
High-level synchronization:
- CountDownLatch - wait for events
- CyclicBarrier - wait for each other
- Semaphore - limit concurrent access
- Phaser - flexible barrier

**Interview question:** "CountDownLatch vs CyclicBarrier?"

## 🎯 Key Concepts

### Executor Framework
Tách task submission khỏi execution:
- Thread pool reuse
- Better resource management
- Easier to tune performance

### CompletableFuture
Modern async programming:
- Non-blocking
- Composable
- Better error handling
- Functional style

### Concurrent Collections
Built-in thread-safety:
- No external synchronization needed
- Better performance than synchronized wrappers
- Specialized for different use cases

### Lock-Free Algorithms
CAS-based operations:
- No locks → no deadlock
- Better scalability
- Lower latency

## 💡 Best Practices
1. Prefer ExecutorService over manual threads
2. Use CompletableFuture for async operations
3. Choose right concurrent collection
4. Use atomic variables for simple counters
5. Understand trade-offs of each approach

## 🚀 Chạy examples
```bash
javac ExecutorServiceDemo.java
java ExecutorServiceDemo
```

Mỗi file có main method riêng để chạy độc lập.
