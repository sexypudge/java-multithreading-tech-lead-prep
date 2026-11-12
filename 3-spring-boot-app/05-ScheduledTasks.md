# 05 — ScheduledTasks: fixedRate, fixedDelay, cron và bẫy production

**Source:** `src/main/java/com/example/async/ScheduledTasks.java` (+ `spring.task.scheduling.*` trong `application.properties`)
**Goal:** trả lời mỗi câu bằng tiếng Anh đơn giản trong **30–45 giây**.
**Mức độ quan trọng:** 🔴 Important = chắc chắn bị hỏi, phải trả lời trôi chảy · 🟡 Medium = hay gặp, cần nắm ý chính · 🟢 Low = biết để không bị bất ngờ.

---

## 1. Technical Knowledge

### 1.1 Ba kiểu lịch

| Kiểu | Chu kỳ tính từ | Ví dụ trong demo |
|---|---|---|
| `fixedRate` | lúc **bắt đầu** lần trước | `heartbeat` mỗi 10s |
| `fixedDelay` | lúc **kết thúc** lần trước | `cleanup` chạy 3s + delay 15s → 2 lần bắt đầu cách nhau ≈ 18s |
| `cron` | lịch "giây phút giờ ngày tháng thứ" | `0 * * * * *` = đầu mỗi phút |

- `fixedRate` với task chậm hơn chu kỳ → lần sau chạy **ngay khi lần trước xong**, mặc định **không chồng lấn** (chỉ chồng nếu thêm `@Async`).
- `initialDelay`: chờ trước lần chạy đầu.

### 1.2 Bốn bẫy production

| # | Bẫy | Sửa |
|---|---|---|
| 1 | Scheduler mặc định của Spring Boot chỉ có **1 thread** → task chậm làm trễ **mọi** task khác | `spring.task.scheduling.pool.size=2+`, `thread-name-prefix=sched-` |
| 2 | Chạy **N instance** (K8s replicas) → job chạy **N lần** | Distributed lock (**ShedLock** + DB/Redis), leader election, hoặc worker riêng; job phải **idempotent** |
| 3 | Exception trong `@Scheduled` | Spring **log và lần sau vẫn chạy** (khác `ScheduledExecutorService` thuần: lịch **bị huỷ âm thầm**) |
| 4 | Job dài + shutdown | Xử lý interrupt: `catch InterruptedException` → khôi phục cờ, dừng |

Thử: `spring.task.scheduling.pool.size=1` → `cleanup` (3s) làm trễ `heartbeat`.

### 1.3 ShedLock

```java
@Scheduled(cron = "0 * * * * *")
@SchedulerLock(name = "report", lockAtMostFor = "5m", lockAtLeastFor = "30s")
public void minutelyReport() { ... }
```

- Instance đầu tiên lấy được lock trong bảng DB chạy; instance khác **bỏ qua** lần đó.
- `lockAtMostFor`: instance chết giữa chừng thì lock vẫn tự hết hạn.
- Lock có thể hết hạn khi job còn chạy → **vẫn cần idempotent**.

### 1.4 Bật/tắt

`@EnableScheduling` trong class lồng có `@ConditionalOnProperty(demo.scheduling.enabled)` → test tắt scheduling cho log đỡ ồn.

---

## 2. Interview English

### Q1. `fixedRate` vs `fixedDelay` vs `cron`?

**Importance:** 🔴 Important

**Key sentence:** *Fixed rate counts from the start, fixed delay from the end, cron follows a calendar.*

**Suggested Answer:**
`fixedRate` measures the period from the **start** of the previous run. If a run takes longer than the period, the next one starts **right after it ends** — by default runs of the same method never overlap. `fixedDelay` waits the period **after** the previous run finishes, so a 3-second job with a 15-second delay starts about every 18 seconds. `cron` runs on a **calendar schedule**, like the start of every minute.

### Q2. You scaled to 3 instances and the scheduled job runs 3 times. What do you do?

**Importance:** 🔴 Important

**Key sentence:** *Use a distributed lock like ShedLock, and make the job idempotent anyway.*

**Suggested Answer:**
Every instance has its own scheduler, so each one runs the job. I add a **distributed lock**, for example **ShedLock** with a database table: only the instance that gets the lock runs, the others skip. `lockAtMostFor` releases the lock if an instance crashes. Other options are leader election or moving the job to a dedicated worker. And I still make the job **idempotent**, because locks can expire while the job is still running.

### Q3. One slow scheduled job delays all the others. Why?

**Importance:** 🔴 Important

**Key sentence:** *Spring Boot's scheduler has only one thread by default.*

**Suggested Answer:**
By default Spring Boot's `ThreadPoolTaskScheduler` has **one thread**, shared by every `@Scheduled` method. If one job takes a long time, all other jobs **wait for it**. I set `spring.task.scheduling.pool.size` to a reasonable number and give the threads a prefix like `sched-`. For really heavy jobs, I hand the work off to a **separate executor**, so the scheduler only triggers them.

### Q4. What happens if a `@Scheduled` method throws an exception?

**Importance:** 🟡 Medium

**Key sentence:** *Spring logs it and runs the next time — unlike a plain ScheduledExecutorService.*

**Suggested Answer:**
For repeating `@Scheduled` tasks, Spring's error handler **logs the exception and keeps the schedule**, so the next run still happens. That's different from a plain `ScheduledExecutorService`, where one exception **cancels the schedule forever** without any log. Even so, I catch and handle errors inside the job, add metrics, and alert if a job fails repeatedly.

### Q5. How do you make a long scheduled job shut down cleanly?

**Importance:** 🟡 Medium

**Key sentence:** *Respond to interrupts: restore the flag and stop, and keep the work resumable.*

**Suggested Answer:**
On shutdown, the scheduler **interrupts** running jobs. The job must respond: when it catches `InterruptedException`, it **restores the interrupt flag** and stops, instead of swallowing it. For long loops, I check `Thread.currentThread().isInterrupted()` between items. I also process in small batches and save progress, so the next run can **continue where it stopped**, and that only works safely if the job is idempotent.

---

## 3. Five sentences to memorize

| No. | Key sentence |
|---|---|
| 1 | Fixed rate counts from the start; fixed delay from the end. |
| 2 | Spring Boot's scheduler has one thread by default. |
| 3 | Every instance runs the job — use ShedLock. |
| 4 | Scheduled jobs must be idempotent. |
| 5 | `@Scheduled` survives exceptions; a plain ScheduledExecutorService doesn't. |

## 4. Key Vocabulary

| Expression | Meaning |
|---|---|
| schedule / cron expression | lịch chạy / biểu thức cron |
| overlap | chạy chồng lấn |
| replica / instance | bản sao của service |
| distributed lock | lock phân tán |
| leader election | bầu chọn instance chính |
| idempotent | chạy nhiều lần cho cùng kết quả |
| resumable | tiếp tục được từ chỗ dừng |

## 5. Self-test

1. `fixedRate` / `fixedDelay` / `cron` khác nhau thế nào? Task chậm hơn chu kỳ thì sao?
2. `cleanup` chạy 3s, `fixedDelay` 15s → hai lần bắt đầu cách nhau bao lâu?
3. Scheduler mặc định của Spring Boot có mấy thread? Hậu quả?
4. Scale 3 instance thì job chạy mấy lần? ShedLock hoạt động thế nào? `lockAtMostFor` để làm gì?
5. Exception trong `@Scheduled` khác gì exception trong `ScheduledExecutorService`?
6. Job dài xử lý shutdown thế nào?
