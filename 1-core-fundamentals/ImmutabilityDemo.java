import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * IMMUTABILITY - CÁCH THREAD-SAFE TỐT NHẤT LÀ KHÔNG CÓ SHARED MUTABLE STATE
 *
 * Object immutable: trạng thái không đổi sau khi tạo → có thể chia sẻ tự do giữa các thread
 * mà KHÔNG cần lock. (String, Integer, LocalDate, BigDecimal, List.of(), record...)
 *
 * 5 QUY TẮC TẠO IMMUTABLE CLASS:
 *   1. Khai báo class là final (hoặc constructor private) để không bị subclass phá
 *   2. Mọi field là private final
 *   3. Không có setter
 *   4. DEFENSIVE COPY mọi object mutable nhận vào constructor
 *   5. DEFENSIVE COPY mọi object mutable trả ra từ getter
 *   (+ không để 'this' thoát ra trong constructor)
 *
 * Interview Question: "Làm sao tạo immutable class? Vì sao String immutable?"
 * Answer: Xem 5 quy tắc. String immutable để: thread-safe, cache hashCode, dùng làm key của Map,
 *         String pool, an toàn bảo mật (đường dẫn file, URL, class name không bị đổi sau khi kiểm tra).
 */
public class ImmutabilityDemo {

    // ============================================
    // DEMO 1: IMMUTABLE CLASS CỔ ĐIỂN
    // ============================================
    static final class Money {
        private final long amount;
        private final String currency;

        Money(long amount, String currency) {
            this.amount = amount;
            this.currency = Objects.requireNonNull(currency);
        }

        long getAmount() { return amount; }
        String getCurrency() { return currency; }

        // "Sửa" = tạo object mới
        Money plus(Money other) {
            if (!currency.equals(other.currency)) throw new IllegalArgumentException("Khác tiền tệ");
            return new Money(amount + other.amount, currency);
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Money)) return false;
            Money m = (Money) o;
            return amount == m.amount && currency.equals(m.currency);
        }
        @Override public int hashCode() { return Objects.hash(amount, currency); }
        @Override public String toString() { return amount + " " + currency; }
    }

    // Java 16+: record = immutable data class gọn nhất (shallow immutable!)
    record Point(int x, int y) {
        Point withX(int newX) { return new Point(newX, y); }
    }

    static void demo1Basics() {
        Money a = new Money(100, "USD");
        Money b = a.plus(new Money(50, "USD"));
        System.out.println("  a = " + a + " (không đổi), b = a + 50 = " + b);

        Point p = new Point(1, 2);
        System.out.println("  " + p + " → withX(10) = " + p.withX(10) + "  (p vẫn là " + p + ")");
    }

    // ============================================
    // DEMO 2: DEFENSIVE COPY - "immutable giả" bị phá vỡ
    // ============================================
    static class BadPeriod {
        private final Date start;  // final nhưng Date là MUTABLE!
        private final Date end;

        BadPeriod(Date start, Date end) {
            if (start.after(end)) throw new IllegalArgumentException("start > end");
            this.start = start;    // ✗ giữ nguyên reference của caller
            this.end = end;
        }
        Date getStart() { return start; } // ✗ lộ reference nội bộ
        Date getEnd() { return end; }
    }

    static final class GoodPeriod {
        private final Date start;
        private final Date end;

        GoodPeriod(Date start, Date end) {
            Date s = new Date(start.getTime()); // ✓ copy trước, kiểm tra SAU trên bản copy
            Date e = new Date(end.getTime());
            if (s.after(e)) throw new IllegalArgumentException("start > end");
            this.start = s;
            this.end = e;
        }
        Date getStart() { return new Date(start.getTime()); } // ✓ trả bản copy
        Date getEnd() { return new Date(end.getTime()); }
    }

    static void demo2DefensiveCopy() {
        Date s1 = new Date(1000), e1 = new Date(2000);
        BadPeriod bad = new BadPeriod(s1, e1);
        e1.setTime(0); // caller sửa Date gốc
        System.out.println("  BadPeriod : end < start ? " + bad.getEnd().before(bad.getStart())
                + "  ✗ invariant bị phá dù field là final");
        bad.getStart().setTime(5000); // phá qua getter
        System.out.println("            start sau khi bị sửa qua getter = " + bad.getStart().getTime());

        Date s2 = new Date(1000), e2 = new Date(2000);
        GoodPeriod good = new GoodPeriod(s2, e2);
        e2.setTime(0);
        good.getStart().setTime(5000);
        System.out.println("  GoodPeriod: start=" + good.getStart().getTime() + ", end=" + good.getEnd().getTime()
                + "  ✓ không đổi");
        System.out.println("  → Tốt hơn: dùng java.time.Instant/LocalDate (đã immutable, khỏi copy).");
    }

    // ============================================
    // DEMO 3: unmodifiableList (VIEW) vs List.copyOf (COPY)
    // ============================================
    static void demo3Collections() {
        List<String> source = new ArrayList<>(List.of("a", "b"));
        List<String> view = Collections.unmodifiableList(source); // chỉ là "cửa sổ" nhìn vào source
        List<String> copy = List.copyOf(source);                  // bản sao thực sự bất biến

        source.add("c");
        System.out.println("  Sau source.add(\"c\"): view.size()=" + view.size()
                + " ✗ (thay đổi lọt qua view), copy.size()=" + copy.size() + " ✓");
        try {
            copy.add("x");
        } catch (UnsupportedOperationException e) {
            System.out.println("  copy.add() → UnsupportedOperationException ✓");
        }
    }

    // ============================================
    // DEMO 4: CHIA SẺ STATE - mutable (torn read) vs immutable snapshot
    // ============================================
    static final class MutableConfig {
        volatile String host = "A";
        volatile int port = 1;
    }

    record Config(String host, int port) {}

    static volatile boolean running = true;
    static volatile Config currentConfig = new Config("A", 1);

    static void demo4Snapshot() throws InterruptedException {
        // ----- Mutable: ghi 2 field riêng lẻ → reader thấy trạng thái nửa vời -----
        MutableConfig mc = new MutableConfig();
        running = true;
        final int[] torn = {0};

        Thread writer1 = new Thread(() -> {
            while (running) {
                mc.host = "B"; mc.port = 2;
                mc.host = "A"; mc.port = 1;
            }
        });
        Thread reader1 = new Thread(() -> {
            while (running) {
                String h = mc.host;
                int p = mc.port;
                boolean consistent = (h.equals("A") && p == 1) || (h.equals("B") && p == 2);
                if (!consistent) torn[0]++;
            }
        });
        writer1.start(); reader1.start();
        Thread.sleep(500);
        running = false;
        writer1.join(); reader1.join();
        System.out.println("  Mutable (2 volatile field) : đọc thấy cặp host/port không nhất quán " + torn[0] + " lần"
                + (torn[0] > 0 ? "  ✗" : "  (may mắn - vẫn sai về thiết kế)"));

        // ----- Immutable: thay cả object trong 1 lần ghi volatile -----
        running = true;
        final int[] bad = {0};
        Thread writer2 = new Thread(() -> {
            while (running) {
                currentConfig = new Config("B", 2);
                currentConfig = new Config("A", 1);
            }
        });
        Thread reader2 = new Thread(() -> {
            while (running) {
                Config c = currentConfig; // đọc 1 lần → snapshot nhất quán
                boolean consistent = (c.host().equals("A") && c.port() == 1) || (c.host().equals("B") && c.port() == 2);
                if (!consistent) bad[0]++;
            }
        });
        writer2.start(); reader2.start();
        Thread.sleep(500);
        running = false;
        writer2.join(); reader2.join();
        System.out.println("  Immutable snapshot (volatile Config): không nhất quán " + bad[0] + " lần  ✓ luôn 0");
        System.out.println("  → Pattern này dùng cho: hot-reload config, routing table, feature flags (copy-on-write).");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== IMMUTABILITY DEMO ===\n");

        System.out.println("--- Demo 1: Immutable class & record ---");
        demo1Basics();

        System.out.println("\n--- Demo 2: Defensive copy ---");
        demo2DefensiveCopy();

        System.out.println("\n--- Demo 3: unmodifiableList vs List.copyOf ---");
        demo3Collections();

        System.out.println("\n--- Demo 4: Immutable snapshot + volatile ---");
        demo4Snapshot();

        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: final có làm object immutable không?");
        System.out.println("A: KHÔNG. final chỉ khoá REFERENCE, không khoá nội dung object được trỏ tới.");
        System.out.println("   'final List<String> l' vẫn add() được.");

        System.out.println("\nQ: Vì sao immutable object thread-safe?");
        System.out.println("A: Không có ghi sau khi tạo → không có race. Nhờ final field semantics, thread nào");
        System.out.println("   thấy reference cũng thấy đầy đủ giá trị đã khởi tạo (miễn this không thoát trong ctor).");

        System.out.println("\nQ: record có thực sự immutable không?");
        System.out.println("A: Shallow immutable: field là final, nhưng nếu component là List/Date mutable thì");
        System.out.println("   vẫn bị sửa từ ngoài. Cần compact constructor copy: this.items = List.copyOf(items).");

        System.out.println("\nQ: Nhược điểm của immutable?");
        System.out.println("A: Tạo nhiều object nhỏ (áp lực GC - thường chấp nhận được), sửa 1 field phải copy.");
        System.out.println("   Với cấu trúc lớn dùng persistent data structure hoặc copy-on-write có chọn lọc.");

        System.out.println("\nQ: Thứ tự ưu tiên khi thiết kế thread-safe?");
        System.out.println("A: 1. Immutable  2. Thread confinement (ThreadLocal, local var)  3. Concurrent utils/Atomic");
        System.out.println("   4. Lock. Càng ở đầu danh sách càng ít lỗi và dễ hiểu.");
    }
}
