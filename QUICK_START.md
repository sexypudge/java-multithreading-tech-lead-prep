# Quick Start Guide - 30 phút học nhanh

## 🎯 Mục tiêu
Nắm được 80% kiến thức multithreading trong 30 phút để sẵn sàng cho interview Tech Lead.

## ⏱️ Timeline

### Phút 1-5: Core Fundamentals (BẮT BUỘC)
```bash
cd 1-core-fundamentals
```

**Đọc và chạy theo thứ tự:**
1. **ThreadCreationExamples.java** (3 phút)
   - 3 cách tạo thread
   - Khi nào dùng cách nào
   - ⚠️ Câu hỏi chắc chắn bị hỏi!

2. **SynchronizationDemo.java** (2 phút)
   - synchronized method vs block
   - Race condition example
   - ⚠️ Must know!

### Phút 6-10: Problems & Solutions
3. **DeadlockDemo.java** (2 phút)
   - 4 điều kiện deadlock
   - Cách detect và fix
   - ⚠️ Senior level question!

4. **VolatileDemo.java** (2 phút)
   - volatile vs synchronized
   - Khi nào dùng volatile
   - ⚠️ Thường bị hỏi!

5. **WaitNotifyDemo.java** (1 phút - skim)
   - Producer-Consumer pattern
   - wait() vs sleep()

### Phút 11-20: Advanced (QUAN TRỌNG cho Tech Lead)
```bash
cd ../2-advanced-concurrency
```

6. **ExecutorServiceDemo.java** (4 phút)
   - Thread pool types
   - Sizing formula
   - ⚠️ Production experience question!

7. **CompletableFutureDemo.java** (3 phút)
   - Modern async programming
   - thenApply, thenCompose, thenCombine
   - ⚠️ Hiện đại, must know!

8. **ConcurrentCollectionsDemo.java** (2 phút)
   - ConcurrentHashMap
   - BlockingQueue
   - When to use what

9. **AtomicVariablesDemo.java** (1 phút - skim)
   - Lock-free programming
   - CAS operations

### Phút 21-25: Spring Boot (Real-world)
```bash
cd ../3-spring-boot-app
```

10. **Đọc README.md** (2 phút)
    - @Async configuration
    - Common pitfalls
    - Production tips

11. **Xem AsyncConfig.java** (1 phút)
    - Thread pool tuning
    - Rejection policies

12. **Xem AsyncController.java** (2 phút)
    - Sync vs Async comparison
    - Parallel API calls pattern

### Phút 26-30: Interview Prep
13. **INTERVIEW_QUESTIONS.md** (5 phút)
    - Đọc top 10 questions
    - Chuẩn bị câu trả lời
    - Behavioral questions

## 🎯 Top 10 Must-Know Questions

### 1. Thread creation - cách nào tốt nhất?
**Answer:** Runnable - flexible, best practice

### 2. synchronized method vs block?
**Answer:** Block tốt hơn - minimize lock scope

### 3. volatile vs synchronized?
**Answer:** volatile - visibility only, synchronized - visibility + atomicity

### 4. Deadlock - 4 điều kiện?
**Answer:** Mutual exclusion, Hold and wait, No preemption, Circular wait

### 5. ExecutorService vs manual threads?
**Answer:** ExecutorService - reuse, better management, easier tuning

### 6. Thread pool sizing?
**Answer:** CPU-bound: cores+1, I/O-bound: cores*(1+WT/CT)

### 7. CompletableFuture vs Future?
**Answer:** CompletableFuture - non-blocking, composable, better API

### 8. ConcurrentHashMap vs Hashtable?
**Answer:** ConcurrentHashMap - better performance, segment locking

### 9. @Async không work?
**Answer:** Self-invocation, không public, quên @EnableAsync

### 10. Detect deadlock trong production?
**Answer:** Thread dump (jstack), JConsole, ThreadMXBean

## 📝 Checklist trước Interview

### Kiến thức cơ bản ✅
- [ ] 3 cách tạo thread
- [ ] Thread states
- [ ] synchronized hoạt động thế nào
- [ ] volatile vs synchronized
- [ ] Deadlock conditions

### Advanced ✅
- [ ] ExecutorService types
- [ ] Thread pool sizing formula
- [ ] CompletableFuture operations
- [ ] Concurrent collections
- [ ] Atomic variables

### Spring Boot ✅
- [ ] @Async configuration
- [ ] Thread pool tuning
- [ ] Common pitfalls
- [ ] Scheduled tasks

### Production ✅
- [ ] Monitoring strategies
- [ ] Debugging techniques
- [ ] Performance tuning
- [ ] Best practices

## 💡 Last-minute Tips

### Trong interview:
1. **Luôn mention trade-offs** - Không có silver bullet
2. **Cho real examples** - Production experience
3. **Discuss monitoring** - How to detect issues
4. **Show debugging skills** - Thread dumps, profiling
5. **Mention best practices** - Industry standards

### Red flags to avoid:
- ❌ "Multithreading is easy"
- ❌ "I never had threading issues"
- ❌ Không biết khi nào KHÔNG nên dùng threads
- ❌ Chỉ biết theory, không có practical experience

### Impress interviewer:
- ✅ "In production, I monitor thread pool metrics..."
- ✅ "I debugged a deadlock using thread dumps..."
- ✅ "Trade-off here is performance vs complexity..."
- ✅ "For this use case, I would choose X because..."

## 🚀 Nếu có thêm thời gian

### 1 giờ thêm:
- Chạy tất cả examples
- Modify code và test
- Đọc hết INTERVIEW_QUESTIONS.md

### 2 giờ thêm:
- Chạy Spring Boot app
- Test các endpoints
- Xem thread pool metrics

### 1 ngày thêm:
- Implement own examples
- Practice explaining concepts
- Mock interview với bạn

## 📚 Resources nhanh

### Khi bị hỏi không biết:
- "That's a great question. Based on my understanding..."
- "I haven't encountered that specific scenario, but I would approach it by..."
- "Let me think through the trade-offs..."

### Câu hỏi hay để hỏi lại:
- "How do you currently monitor thread pools in production?"
- "What's your strategy for debugging threading issues?"
- "How do you decide between sync and async approaches?"

---

## ⚡ 5-Minute Cheat Sheet

Nếu chỉ có 5 phút trước interview:

```
1. Thread creation: Prefer Runnable
2. Synchronization: Block > Method
3. volatile: Visibility only, not atomicity
4. Deadlock: Lock ordering, timeout
5. ExecutorService: Reuse threads, better management
6. Thread pool: CPU=cores+1, I/O=cores*(1+WT/CT)
7. CompletableFuture: Non-blocking, composable
8. ConcurrentHashMap: Better than Hashtable
9. @Async: Avoid self-invocation
10. Production: Monitor, thread dumps, tune
```

---

**You got this! 💪 Good luck với interview!**
