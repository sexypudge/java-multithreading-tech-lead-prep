Từ giờ, em sẽ dùng thống nhất format này cho các topic Multithreading:

1. Technical Knowledge: Ghi chú kỹ thuật ngắn gọn, chính xác, đủ để hiểu và ôn tập.
2. Interview English: Câu trả lời tiếng Anh khoảng 60–90 giây, đủ ý để luyện nói.
3. Key Vocabulary: Từ vựng và collocations quan trọng.
4. Interview Questions: Câu hỏi tự kiểm tra.
5. Review Status: Theo dõi mức độ nắm kiến thức.

Dưới đây là bản tổng hợp đầu tiên cho `SynchronizationDemo.java`.

# 04 – Synchronization

Source: SynchronizationDemo.java

## 1. Technical Knowledge

### 1.1. What is synchronization?

Synchronization là cơ chế kiểm soát việc nhiều threads truy cập vào shared resources, nhằm đảm bảo tính nhất quán của dữ liệu khi có nhiều threads cùng đọc và ghi.

Ví dụ:

```
class UnsafeCounter {
    private int count = 0;

    public void increment() {
        count++;
    }
}
```

Vấn đề nằm ở `count++`.

Đây không phải một atomic operation. Nó thực chất gồm ba bước:

1. Read: đọc giá trị hiện tại.
2. Modify: tăng giá trị lên 1.
3. Write: ghi giá trị mới trở lại.

Nếu hai threads cùng thực hiện, chúng có thể đọc cùng một giá trị và ghi đè kết quả của nhau.

Đây là nguyên nhân dẫn đến Race Condition.

### 1.2. Three ways to use synchronized

Java cung cấp ba cách sử dụng `synchronized` trong file này.

A. Synchronized Instance Method

```
public synchronized void increment() {
    count++;
}
```

- Lock được sử dụng là `this`.
- Mỗi object có một intrinsic lock riêng.
- Tại một thời điểm, chỉ một thread có thể giữ lock của object đó.
- Các threads khác muốn vào synchronized method trên cùng object phải chờ lock được giải phóng.

Lưu ý: Hai threads vẫn có thể đồng thời thực hiện synchronized methods nếu chúng gọi trên hai object khác nhau.

B. Synchronized Block

```
private final Object lock = new Object();

public void increment() {
    synchronized (lock) {
        count++;
    }
}
```

- Lock được sử dụng là object được chỉ định trong `synchronized`.
- Chỉ phần code nằm trong block được bảo vệ.
- Có thể giới hạn critical section thay vì khóa toàn bộ method.
- Những đoạn code bên ngoài block vẫn có thể được thực thi đồng thời.

C. Static Synchronized Method

```
public static synchronized void increment() {
    count++;
}
```

- Lock được sử dụng là `Class` object.
- Ví dụ: `Counter.class`.
- Lock này được chia sẻ giữa các threads khi gọi static synchronized methods của cùng một class.
- Phù hợp để bảo vệ shared static state.

| Mechanism                    | Lock object      | Scope                |
| ---------------------------- | ---------------- | -------------------- |
| Synchronized instance method | `this`           | Entire method        |
| Synchronized block           | Specified object | Block only           |
| Static synchronized method   | `Class` object   | Entire static method |

### 1.3. Synchronized Method vs Synchronized Block

| Criteria    | Method                   | Block                       |
| ----------- | ------------------------ | --------------------------- |
| Lock scope  | Entire method            | Specific code section       |
| Lock object | `this` hoặc Class object | Object được chỉ định        |
| Flexibility | Ít linh hoạt hơn         | Linh hoạt hơn               |
| Use case    | Bảo vệ toàn bộ method    | Chỉ bảo vệ critical section |

Important: Synchronized block không mặc định nhanh hơn synchronized method. Nó chỉ giúp giới hạn phạm vi lock khi thiết kế đúng.

### 1.4. Reentrant Synchronization

Java intrinsic locks có tính chất reentrant.

Điều đó có nghĩa là một thread đang giữ lock của một object có thể tiếp tục acquire chính lock đó nhiều lần mà không bị deadlock.

Ví dụ:

```
class ReentrantExample {

    public synchronized void outerMethod() {
        innerMethod();
    }

    public synchronized void innerMethod() {
        System.out.println("Inside inner method");
    }
}
```

Execution flow:

1. Thread A gọi `outerMethod()`.
2. Thread A acquire lock của object.
3. `outerMethod()` gọi `innerMethod()`.
4. Thread A tiếp tục acquire cùng lock.
5. Không xảy ra deadlock vì lock có tính reentrant.
6. Khi hoàn thành, thread giải phóng các lần acquire tương ứng.

Key takeaway: Java's intrinsic locks are reentrant.

### 1.5. Important Concepts

| Concept          | Meaning                                                       |
| ---------------- | ------------------------------------------------------------- |
| Shared resource  | Resource được nhiều threads truy cập                          |
| Critical section | Đoạn code truy cập shared mutable data cần được bảo vệ        |
| Intrinsic lock   | Lock tích hợp sẵn trong mọi Java object                       |
| Monitor          | Cơ chế đồng bộ gắn với intrinsic lock                         |
| Lock contention  | Tình trạng nhiều threads cùng tranh chấp một lock             |
| Race condition   | Kết quả phụ thuộc vào timing hoặc execution order của threads |

### 1.6. Important Notes

- `synchronized` giúp đảm bảo mutual exclusion trên cùng lock.
- Một thread phải acquire lock trước khi thực thi synchronized block hoặc method tương ứng.
- Threads khác muốn acquire cùng lock phải chờ.
- Lock được tự động giải phóng khi thread thoát khỏi synchronized method/block, kể cả khi có exception.
- Nên giữ critical section ngắn để giảm lock contention.
- Synchronized không bảo vệ dữ liệu nếu các threads sử dụng những lock object khác nhau.

## 2. Interview English

### Question: What is synchronization in Java, and how does synchronized work?



Main answer

&#x20;Target: 60–90 seconds



Synchronization in Java is a mechanism used to control access to shared resources when multiple threads are running concurrently.

It helps prevent race conditions and ensures data consistency.

Java provides the `synchronized` keyword, which uses an intrinsic lock associated with an object.

There are three common ways to use it.

First, we can declare an instance method as synchronized. In this case, the thread must acquire the object's monitor lock before executing the method.

Second, we can use a synchronized block to protect only a specific section of code. This gives us more control over the lock scope.

Third, we can declare a static method as synchronized. In this case, the lock belongs to the Class object rather than an individual instance.

When one thread holds a lock, other threads must wait if they try to acquire the same lock.

One important point is that Java's intrinsic locks are reentrant. This means a thread can acquire the same lock multiple times without causing a deadlock.

In practice, I prefer keeping the critical section as small as possible to reduce lock contention.

### Useful sentence patterns

Anh nên ghi nhớ các cụm sau thay vì học thuộc cả đoạn:

- control access to shared resources – kiểm soát truy cập tài nguyên dùng chung.
- prevent race conditions – ngăn chặn race condition.
- ensure data consistency – đảm bảo tính nhất quán dữ liệu.
- acquire an object's monitor lock – lấy monitor lock của object.
- protect a specific section of code – bảo vệ một đoạn code cụ thể.
- belong to the Class object – thuộc về Class object.
- hold a lock – đang nắm giữ lock.
- reduce lock contention – giảm tranh chấp lock.
- release the lock – giải phóng lock.

## 3. Key Vocabulary

| Vocabulary           | Meaning                               | Example                                       |
| -------------------- | ------------------------------------- | --------------------------------------------- |
| Mutual exclusion     | Loại trừ lẫn nhau                     | Synchronization provides mutual exclusion.    |
| Shared mutable state | Trạng thái dùng chung có thể thay đổi | Multiple threads access shared mutable state. |
| Critical section     | Vùng code quan trọng cần bảo vệ       | We should protect the critical section.       |
| Intrinsic lock       | Lock tích hợp sẵn                     | Every Java object has an intrinsic lock.      |
| Monitor lock         | Monitor lock                          | A thread must acquire the monitor lock.       |
| Lock contention      | Tranh chấp lock                       | Too much contention can affect performance.   |
| Reentrant            | Có thể acquire lại cùng lock          | Java intrinsic locks are reentrant.           |
| Concurrently         | Đồng thời                             | Multiple threads execute concurrently.        |

## 4. Interview Questions – Self-test

Đây là những câu hỏi anh cần tự trả lời mà không nhìn tài liệu. Chưa cần học thuộc model answer.

Self-test checklist

0/9 completed

What is synchronization in Java, and why do we need it?

Fundamental

Why is count++ not an atomic operation?

Fundamental

What is the difference between a synchronized method and a synchronized block?

Fundamental

What is the difference between instance synchronized and static synchronized methods?

Fundamental

What happens when two threads call a synchronized method on the same object?

Fundamental

Can two threads execute the same synchronized method at the same time on two different objects?

Understanding

What is a reentrant lock? Why doesn't the ReentrantExample cause a deadlock?

Understanding

Why should we keep the critical section as small as possible?

Understanding

Does synchronized guarantee thread safety if different threads use different lock objects?

Understanding

## 5. Review Strategy

| When             | What to do                                                  |
| ---------------- | ----------------------------------------------------------- |
| Day 1            | Understand the technical notes and practice the main answer |
| Day 2            | Answer the self-test questions without looking at notes     |
| Day 3–7          | Do a short interview focusing on weak points                |
| After 3–5 topics | Join a cumulative Multithreading interview                  |

## 6. What anh should remember

Nếu chỉ giữ lại 5 ý quan trọng nhất của topic này, hãy nhớ:

1. `count++` is not atomic.
2. `synchronized` provides mutual exclusion through intrinsic locks.
3. Instance synchronized method locks `this`.
4. Static synchronized method locks the Class object.
5. Intrinsic locks are reentrant.

Cách sử dụng file này: Phần Technical Knowledge giúp anh hiểu và ôn Java. Phần Interview English giúp anh luyện diễn đạt kiến thức đó bằng tiếng Anh. Hai phần cần bổ trợ cho nhau, không nên rút gọn English answer thành vài câu định nghĩa.

Ở buổi interview tiếp theo, em sẽ dùng 9 câu hỏi trong checklist làm cơ sở đánh giá, không mặc định rằng anh đã trả lời chúng.