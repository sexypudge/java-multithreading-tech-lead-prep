# Java Multithreading - Tech Lead Interview Preparation

## 📚 Mục đích
Project này được thiết kế để chuẩn bị phỏng vấn Tech Lead về Java Multithreading, bao gồm:
- ✅ Kiến thức cơ bản (thường bị hỏi trong interview)
- ✅ Kỹ thuật hiện đại (production-ready)
- ✅ Real-world examples với Spring Boot

## 🗂️ Cấu trúc Project

### 1. Core Fundamentals (`1-core-fundamentals/`)
Kiến thức nền tảng - **BẮT BUỘC** phải nắm vững cho interview:
- Thread Creation (3 cách)
- Thread Lifecycle & States
- Synchronization & Locks
- wait/notify/notifyAll
- volatile keyword
- Deadlock & Race Conditions

### 2. Advanced Concurrency (`2-advanced-concurrency/`)
Kỹ thuật hiện đại - **QUAN TRỌNG** cho Tech Lead:
- ExecutorService & Thread Pools
- CompletableFuture
- Fork/Join Framework
- Concurrent Collections
- Atomic Variables
- Synchronizers (CountDownLatch, CyclicBarrier, Semaphore)

### 3. Spring Boot Application (`3-spring-boot-app/`)
Ứng dụng thực tế - **DEMO** kiến thức trong production:
- @Async configuration
- Thread Pool tuning
- Scheduled tasks
- Performance monitoring

## 🎯 Câu hỏi Interview thường gặp

### Cơ bản:
1. **Sự khác biệt giữa Thread và Runnable?**
   - Thread: Kế thừa class, không thể extend class khác
   - Runnable: Interface, linh hoạt hơn, best practice

2. **synchronized hoạt động như thế nào?**
   - Monitor lock trên object
   - Method level vs Block level
   - Reentrant lock

3. **volatile vs synchronized?**
   - volatile: Visibility, không đảm bảo atomicity
   - synchronized: Cả visibility và atomicity

4. **Deadlock là gì? Cách tránh?**
   - 4 điều kiện: Mutual exclusion, Hold and wait, No preemption, Circular wait
   - Giải pháp: Lock ordering, timeout, tryLock()

### Nâng cao:
5. **ExecutorService vs tạo Thread thủ công?**
   - Thread pool reuse
   - Resource management
   - Better control

6. **CompletableFuture vs Future?**
   - Non-blocking
   - Composable
   - Exception handling tốt hơn

7. **ConcurrentHashMap hoạt động thế nào?**
   - Segment locking (Java 7)
   - CAS operations (Java 8+)
   - Better performance than Hashtable

8. **Khi nào dùng CountDownLatch vs CyclicBarrier?**
   - CountDownLatch: One-time event, threads wait for completion
   - CyclicBarrier: Reusable, threads wait for each other

### Tech Lead Level:
9. **Làm sao tune thread pool size?**
   - CPU-bound: cores + 1
   - I/O-bound: cores * (1 + wait/compute ratio)
   - Monitor và adjust

10. **Cách debug thread issues trong production?**
    - Thread dumps
    - JMX monitoring
    - Profiling tools
    - Logging strategies

## 🚀 Cách chạy

### Core Fundamentals & Advanced:
```bash
cd 1-core-fundamentals
javac *.java
java <ClassName>
```

### Spring Boot App:
```bash
cd 3-spring-boot-app
mvn spring-boot:run
```

## 📖 Học theo thứ tự
1. Đọc README trong từng folder
2. Chạy code examples
3. Đọc comments trong code
4. Thử modify và test
5. Chuẩn bị giải thích cho interviewer

## 💡 Tips cho Interview
- Luôn giải thích **WHY** chọn approach đó
- Đề cập **trade-offs** của mỗi solution
- Nói về **production considerations** (monitoring, debugging)
- Cho examples **real-world** đã làm
- Biết khi nào **KHÔNG NÊN** dùng multithreading

## 📚 Resources
- Java Concurrency in Practice (Brian Goetz)
- Official Java Concurrency Tutorial
- Baeldung Java Concurrency guides

---
**Good luck với interview! 🎯**
