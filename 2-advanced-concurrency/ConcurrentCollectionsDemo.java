import java.util.concurrent.*;
import java.util.*;

/**
 * CONCURRENT COLLECTIONS - THREAD-SAFE COLLECTIONS
 * 
 * Tại sao không dùng synchronized wrappers?
 * ✓ Better performance - fine-grained locking
 * ✓ Non-blocking reads (ConcurrentHashMap)
 * ✓ Specialized operations (putIfAbsent, etc.)
 * 
 * Interview Question: "ConcurrentHashMap vs Hashtable?"
 * Answer: ConcurrentHashMap - segment locking, better performance
 *         Hashtable - lock toàn bộ map, slower
 */
public class ConcurrentCollectionsDemo {

    // ============================================
    // 1. CONCURRENTHASHMAP
    // ============================================
    static void concurrentHashMapDemo() {
        System.out.println("--- ConcurrentHashMap ---");
        
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        
        // Thread-safe operations
        map.put("A", 1);
        map.putIfAbsent("A", 2); // Không overwrite
        System.out.println("  putIfAbsent: " + map.get("A")); // Still 1
        
        // Atomic operations
        map.compute("A", (k, v) -> v + 10);
        System.out.println("  compute: " + map.get("A")); // 11
        
        map.merge("B", 1, Integer::sum); // Create if absent
        map.merge("B", 2, Integer::sum); // Merge if exists
        System.out.println("  merge: " + map.get("B")); // 3
    }

    // ============================================
    // 2. COPYONWRITEARRAYLIST
    // ============================================
    static void copyOnWriteArrayListDemo() {
        System.out.println("\n--- CopyOnWriteArrayList ---");
        
        CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
        
        // Write creates copy
        list.add("A");
        list.add("B");
        
        // Iterator không throw ConcurrentModificationException
        for (String item : list) {
            System.out.println("  Item: " + item);
            list.add("C"); // Safe during iteration
        }
        
        System.out.println("  Final size: " + list.size());
        System.out.println("  Use case: Many reads, few writes");
    }

    // ============================================
    // 3. BLOCKINGQUEUE - PRODUCER/CONSUMER
    // ============================================
    static void blockingQueueDemo() throws InterruptedException {
        System.out.println("\n--- BlockingQueue ---");
        
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(5);
        
        // Producer
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    queue.put(i); // Block if full
                    System.out.println("  Produced: " + i);
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // Consumer
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    Integer item = queue.take(); // Block if empty
                    System.out.println("  Consumed: " + item);
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
    }

    // ============================================
    // 4. CONCURRENTSKIPLISTMAP - SORTED MAP
    // ============================================
    static void concurrentSkipListMapDemo() {
        System.out.println("\n--- ConcurrentSkipListMap ---");
        
        ConcurrentSkipListMap<Integer, String> map = new ConcurrentSkipListMap<>();
        
        map.put(3, "Three");
        map.put(1, "One");
        map.put(2, "Two");
        
        System.out.println("  Sorted: " + map); // {1=One, 2=Two, 3=Three}
        System.out.println("  Use case: Sorted + concurrent access");
    }

    // ============================================
    // MAIN DEMO
    // ============================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== CONCURRENT COLLECTIONS DEMO ===\n");
        
        concurrentHashMapDemo();
        copyOnWriteArrayListDemo();
        blockingQueueDemo();
        concurrentSkipListMapDemo();
        
        // ============================================
        // INTERVIEW TIPS
        // ============================================
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: ConcurrentHashMap vs Hashtable?");
        System.out.println("A: ConcurrentHashMap - segment locking, better perf");
        System.out.println("   Hashtable - lock entire map");
        
        System.out.println("\nQ: CopyOnWriteArrayList use case?");
        System.out.println("A: Many reads, few writes (listeners, observers)");
        System.out.println("   Write expensive (copy entire array)");
        
        System.out.println("\nQ: BlockingQueue implementations?");
        System.out.println("A: ArrayBlockingQueue - bounded");
        System.out.println("   LinkedBlockingQueue - optionally bounded");
        System.out.println("   PriorityBlockingQueue - priority order");
        System.out.println("   SynchronousQueue - no capacity");
        
        System.out.println("\n=== CHEAT SHEET ===");
        System.out.println("Map: ConcurrentHashMap");
        System.out.println("List (read-heavy): CopyOnWriteArrayList");
        System.out.println("Queue: BlockingQueue implementations");
        System.out.println("Set: ConcurrentSkipListSet");
        System.out.println("Sorted Map: ConcurrentSkipListMap");
    }
}
