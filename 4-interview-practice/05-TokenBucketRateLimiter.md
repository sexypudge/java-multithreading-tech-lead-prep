# 05 — TokenBucketRateLimiter: rate limiter thread-safe có burst

**Source:** `TokenBucketRateLimiter.java` · **Priority:** P1
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Token bucket

- Xô chứa tối đa `capacity` token, nạp đều `ratePerSecond` token/giây.
- Mỗi request lấy 1 token; hết → từ chối (**429**) hoặc chờ.
- **Lazy refill**: không cần thread nền; tính token cần thêm từ thời gian trôi qua.
- `capacity` = **burst** cho phép; `rate` = throughput trung bình.

```java
public synchronized boolean tryAcquire() {
    long now = System.nanoTime();
    tokens = Math.min(capacity, tokens + (now - lastRefillNanos) * refillPerNano);
    lastRefillNanos = now;
    if (tokens >= 1) { tokens -= 1; return true; }
    return false;
}
```

### 1.2 Vì sao `synchronized`

- Refill + kiểm tra + trừ = **một thao tác ghép** trên **2 biến** (`tokens`, `lastRefillNanos`) → check-then-act.
- Critical section rất ngắn → gần như không tranh chấp.
- Lock-free được (AtomicLong + CAS trên trạng thái đóng gói) nhưng phức tạp mà ít lợi.

### 1.3 `nanoTime` vs `currentTimeMillis`

`System.nanoTime()` **đơn điệu**, không bị ảnh hưởng khi đổi giờ hệ thống / NTP. `currentTimeMillis` có thể nhảy lùi.

### 1.4 Kiểm chứng

100 req/s, burst 20, 8 thread spam 2s → allowed ≤ 20 + 100 × 2 = **220**.

### 1.5 Phân biệt khi phỏng vấn

| | Giới hạn | Công cụ |
|---|---|---|
| **Rate limiter** | Số request **trên giây** (throughput) | Token bucket, sliding window |
| **Concurrency limit** | Số request **đang chạy cùng lúc** | Semaphore, bulkhead |
| **Nhiều instance** | Limiter trong JVM chỉ giới hạn **từng instance** | Redis + Lua script (atomic), API gateway |

### 1.6 Follow-up

- Chờ thay vì từ chối: tính thời gian tới token kế tiếp rồi park; Guava `RateLimiter.acquire()`.
- Mỗi user một limiter: `ConcurrentHashMap.computeIfAbsent` + **eviction** (Caffeine) để không rò RAM.
- Production: Resilience4j `RateLimiter`, Bucket4j.

---

## 2. Interview English

### Q1. How does a token bucket rate limiter work?

**Importance:** 🔴 Important

**Key sentence:** *Tokens refill at a fixed rate up to a capacity; each request takes one or is rejected.*

**Suggested Answer:**
The bucket holds up to `capacity` tokens and refills at a **fixed rate**, for example 100 per second. Each request takes **one token**; if the bucket is empty, the request is rejected with 429 or has to wait. The capacity allows **short bursts**, and the rate controls the average. I refill **lazily**: on each call I compute how many tokens to add from the time passed, so no background thread is needed.

### Q2. How do you make it thread-safe? Why a lock and not atomics?

**Importance:** 🔴 Important

**Key sentence:** *Refill, check and take is one compound action on two variables, so one short lock is simplest.*

**Suggested Answer:**
Refilling, checking and taking a token is **one compound operation** on two variables — the token count and the last refill time. If two threads did it separately, both could take the last token. A single **`synchronized`** method makes it atomic, and the critical section is just a few arithmetic operations, so contention is low. A lock-free version with CAS is possible, but it's more complex for **little benefit**.

### Q3. Rate limit vs concurrency limit?

**Importance:** 🔴 Important

**Key sentence:** *A rate limit caps requests per second; a concurrency limit caps requests in flight.*

**Suggested Answer:**
A **rate limiter** limits how many requests start **per second** — it's about throughput, using a token bucket or sliding window. A **concurrency limit** limits how many requests are **running at the same time**, using a semaphore or bulkhead. They solve different problems: a slow downstream with 10 requests per second can still have hundreds in flight if each takes a long time. Often I need both.

### Q4. Your service runs on 10 instances. Does this limiter still work?

**Importance:** 🟡 Medium

**Key sentence:** *It only limits each instance; a global limit needs Redis or the API gateway.*

**Suggested Answer:**
It only limits **each JVM**, so 10 instances allow 10 times the rate. If the limit must be **global**, for example a partner's API quota, I use a shared store like **Redis with a Lua script**, so the check-and-decrement is atomic across instances, or I enforce it at the **API gateway**. Another option is to divide the limit by the number of instances, but that breaks when we scale.

### Q5. Why `System.nanoTime()` instead of `currentTimeMillis()`?

**Importance:** 🟢 Low

**Key sentence:** *nanoTime is monotonic; wall-clock time can jump.*

**Suggested Answer:**
`currentTimeMillis` is **wall-clock time**, which can jump forward or backward when the system clock is adjusted, for example by NTP. That could create or remove tokens incorrectly. `nanoTime` is **monotonic** — it only measures elapsed time — which is exactly what we need to compute the refill.

### Q6. How would you rate-limit per user?

**Importance:** 🟡 Medium

**Key sentence:** *A map of limiters created with computeIfAbsent, with eviction so it doesn't grow forever.*

**Suggested Answer:**
I keep a `ConcurrentHashMap` from user ID to limiter and create limiters with **`computeIfAbsent`**, so each user gets exactly one, even under concurrency. But the map would grow forever, so I use a cache with **eviction**, like Caffeine with expire-after-access. In production I'd use Bucket4j or Resilience4j, and for multiple instances, a Redis-backed limiter.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | Capacity allows bursts; the rate controls the average. |
| 2 | Refill lazily from elapsed time. |
| 3 | Refill, check and take must be one atomic action. |
| 4 | Rate limit is per second; concurrency limit is in flight. |
| 5 | An in-JVM limiter is per instance. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| token bucket | xô token |
| burst | đợt request dồn dập |
| lazy refill | nạp lại khi cần |
| throttle | hạn chế tốc độ |
| monotonic clock | đồng hồ đơn điệu |
| quota | hạn mức |
| eviction | loại bỏ entry cũ |

## 5. Self-test

1. Token bucket hoạt động thế nào? `capacity` và `rate` nghĩa là gì?
2. Lazy refill là gì?
3. Vì sao `synchronized` chứ không phải 2 biến atomic riêng?
4. Vì sao `nanoTime`?
5. Demo 100 req/s, burst 20, 2s → allowed tối đa?
6. Rate limit vs concurrency limit?
7. 10 instance thì sao? Limiter theo user thì sao?
