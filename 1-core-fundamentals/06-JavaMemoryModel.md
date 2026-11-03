### volatile ngăn vấn đề reorder và đảm bảo safe publication của instance của DCL. (https://claude.ai/chat/b8b8dc79-c28c-4063-aa3c-d173f4329ea9)

## 1. Vấn đề: `new` không phải một bước

```java
instance = new DclSingleton();
```

Về mặt logic, dòng này gồm 3 bước:

```
1. allocate   : cấp phát vùng nhớ cho object
2. construct  : chạy constructor (gán value = 100)
3. assign     : gán reference vào biến instance
```

Trong một thread, đổi thứ tự thành `1 → 3 → 2` không làm kết quả đổi, nên compiler/CPU được phép làm vậy (đây là reorder). Nhưng thread khác thì thấy rõ sự khác biệt.

## 2. Chuyện gì xảy ra nếu `instance` không volatile

```
Thread A (trong synchronized)       Thread B (check ngoài synchronized)
-----------------------------       -----------------------------------
1. allocate
3. instance = <địa chỉ>  ──────►    if (instance == null)  → false (thấy non-null)
                                    return instance;       → dùng ngay
2. construct (chưa chạy xong!)      instance.field → 0 (giá trị mặc định)
```

Thread B thấy `instance != null` nên bỏ qua `synchronized` và dùng object **chưa khởi tạo xong**. Đây là **unsafe publication**: reference đã được công bố nhưng nội dung object thì chưa.

Lý do B không được bảo vệ: lần check đầu tiên của B nằm **ngoài** `synchronized`, nên không có quan hệ happens-before nào giữa việc A khởi tạo xong và việc B đọc.

## 3. `volatile` giải quyết bằng cách nào

Khi `instance` là `volatile`, ghi vào nó là **volatile write**, và nó có hai tác dụng:

- **Chống reorder:** mọi ghi đứng *trước* volatile write (trong đó có bước 2, chạy constructor) không được trôi ra sau nó. Thứ tự buộc phải là `1 → 2 → 3`.
- **Tạo happens-before:** volatile write HB volatile read tiếp theo của cùng biến. Chuỗi hợp lệ:

```
(constructor ghi field) HB (instance = ...)    ← program order + volatile write
(instance = ...)        HB (B đọc instance)    ← volatile rule
(B đọc instance)        HB (B dùng field)      ← program order
⟹ B chắc chắn thấy field đã khởi tạo xong      ← transitivity
```

Đây chính là mẫu ở Demo 2: constructor đóng vai trò `data = 42`, còn `instance` đóng vai trò `ready`.

## Cách nói gọn khi phỏng vấn

> "Không có volatile, `instance = new X()` có thể bị reorder: reference được gán trước khi constructor chạy xong. Thread khác check null ngoài synchronized sẽ thấy non-null và dùng object khởi tạo dở. volatile cấm reorder đó và tạo happens-before giữa lúc khởi tạo xong và lúc thread khác đọc, nên đảm bảo safe publication."

## Một lưu ý để bạn không bị hỏi vặn

Trong file của bạn, `value` là `final`, nên nhờ final field semantics, bug này thực ra **không** xảy ra với field đó dù thiếu volatile. Ví dụ kinh điển để chứng minh bug thường dùng field **không** final. Nếu muốn demo cho đúng ý, hãy bỏ `final` ở `value`, nhưng nhớ rằng bug này gần như không tái hiện được trên x86 và JVM hiện đại, nên đó vẫn là "code sai theo JMM" chứ không dễ chạy ra lỗi.