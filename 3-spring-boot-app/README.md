# Spring Boot Async Application

## 📋 Overview
Real-world Spring Boot application demonstrating multithreading concepts:
- @Async methods
- Thread pool configuration
- Scheduled tasks
- CompletableFuture integration
- Performance monitoring

## 🚀 Chạy Application

### Prerequisites
- Java 17+
- Maven 3.6+

### Build & Run
```bash
cd 3-spring-boot-app
mvn clean install
mvn spring-boot:run
```

Application sẽ chạy tại: http://localhost:8080

## 🔍 API Endpoints

### 1. Sync vs Async Comparison
```bash
# Sync - blocking, slow
curl http://localhost:8080/api/sync

# Async - non-blocking, fast
curl http://localhost:8080/api/async
```

### 2. Parallel API Calls
```bash
# Simulate calling multiple services in parallel
curl http://localhost:8080/api/parallel
```

### 3. Fire-and-Forget
```bash
# Process items in background
curl -X POST http://localhost:8080/api/process \
  -H "Content-Type: application/json" \
  -d '["item1", "item2", "item3"]'
```

### 4. Error Handling
```bash
# Demo exception handling in async
curl http://localhost:8080/api/error
```

### 5. Health Check
```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/actuator/health
```

## 🎯 Key Concepts Demonstrated

### 1. @Async Configuration
- Custom thread pool (AsyncConfig.java)
- Core/Max pool size tuning
- Queue capacity
- Rejection policies

### 2. Async Methods
- Fire-and-forget (void return)
- With return value (CompletableFuture)
- Exception handling
- Self-invocation pitfall

### 3. Scheduled Tasks
- @Scheduled with fixedRate
- @Scheduled with fixedDelay
- Cron expressions
- Separate executor for scheduled tasks

### 4. Performance Patterns
- Parallel execution
- Non-blocking I/O
- Resource pooling
- Monitoring with Actuator

## 📊 Monitoring

### Thread Pool Metrics
```bash
curl http://localhost:8080/actuator/metrics
```

### Application Health
```bash
curl http://localhost:8080/actuator/health
```

## 🎓 Interview Topics Covered

### Configuration
**Q: Làm sao configure thread pool cho @Async?**
- Implement AsyncConfigurer hoặc define @Bean Executor
- Set core/max pool size, queue capacity
- Choose rejection policy

**Q: Thread pool size bao nhiêu là đủ?**
- CPU-bound: cores + 1
- I/O-bound: cores * (1 + wait/compute)
- Monitor và adjust

### Common Issues
**Q: @Async không work, tại sao?**
- Self-invocation (gọi từ cùng class)
- Method không public
- Quên @EnableAsync
- Không có thread pool config

**Q: Exception trong @Async method?**
- Return CompletableFuture và handle với exceptionally()
- Hoặc implement AsyncUncaughtExceptionHandler

### Best Practices
**Q: Khi nào dùng @Async?**
- I/O operations (API calls, DB queries)
- Long-running tasks
- Fire-and-forget operations
- Parallel processing

**Q: Khi nào KHÔNG nên dùng @Async?**
- CPU-intensive tasks (dùng ForkJoin)
- Transactions (async breaks transaction context)
- Quick operations (overhead không đáng)

## 🔧 Tuning Tips

### Thread Pool Sizing
```java
// Conservative start
corePoolSize = 5
maxPoolSize = 10
queueCapacity = 100

// Monitor metrics:
// - Active threads
// - Queue size
// - Rejection count

// Adjust based on:
// - CPU usage
// - Response time
// - Throughput
```

### Rejection Policies
- **AbortPolicy** - Throw exception (default)
- **CallerRunsPolicy** - Run in caller thread (backpressure)
- **DiscardPolicy** - Silently discard
- **DiscardOldestPolicy** - Discard oldest in queue

## 📚 Files Structure
```
src/main/java/com/example/async/
├── AsyncDemoApplication.java  - Main application
├── AsyncConfig.java           - Thread pool config
├── AsyncService.java          - @Async methods
├── AsyncController.java       - REST endpoints
└── ScheduledTasks.java        - Scheduled jobs
```

## 💡 Production Considerations
1. **Monitoring** - Track thread pool metrics
2. **Alerting** - Alert on high rejection rate
3. **Graceful Shutdown** - Wait for tasks to complete
4. **Circuit Breaker** - Protect external calls
5. **Rate Limiting** - Prevent overload
6. **Logging** - Log thread names for debugging

## 🎯 Testing Tips
```bash
# Load test với Apache Bench
ab -n 1000 -c 10 http://localhost:8080/api/async

# Monitor threads
jstack <pid> | grep "Async-"

# Check thread pool
curl http://localhost:8080/actuator/metrics/executor.active
```

---
**Good luck với interview! 🚀**
