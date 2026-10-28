import java.util.concurrent.*;
import java.util.Arrays;

/**
 * FORK/JOIN FRAMEWORK - DIVIDE AND CONQUER
 * 
 * Work Stealing Algorithm:
 * - Idle threads "steal" work from busy threads
 * - Better load balancing
 * - Efficient for recursive tasks
 * 
 * Interview Question: "Fork/Join vs ExecutorService?"
 * Answer: Fork/Join - recursive tasks, work stealing
 *         ExecutorService - independent tasks, simpler
 */
public class ForkJoinDemo {

    // ============================================
    // RECURSIVETASK - WITH RETURN VALUE
    // ============================================
    /**
     * Sum array using divide-and-conquer
     */
    static class SumTask extends RecursiveTask<Long> {
        private static final int THRESHOLD = 1000;
        private final long[] array;
        private final int start;
        private final int end;

        public SumTask(long[] array, int start, int end) {
            this.array = array;
            this.start = start;
            this.end = end;
        }

        @Override
        protected Long compute() {
            int length = end - start;

            // Base case - compute directly
            if (length <= THRESHOLD) {
                long sum = 0;
                for (int i = start; i < end; i++) {
                    sum += array[i];
                }
                return sum;
            }

            // Recursive case - divide
            int mid = start + length / 2;
            SumTask leftTask = new SumTask(array, start, mid);
            SumTask rightTask = new SumTask(array, mid, end);

            // Fork left task (async)
            leftTask.fork();

            // Compute right task (current thread)
            long rightResult = rightTask.compute();

            // Join left task (wait for result)
            long leftResult = leftTask.join();

            return leftResult + rightResult;
        }
    }

    // ============================================
    // RECURSIVEACTION - NO RETURN VALUE
    // ============================================
    /**
     * Increment array elements
     */
    static class IncrementTask extends RecursiveAction {
        private static final int THRESHOLD = 1000;
        private final long[] array;
        private final int start;
        private final int end;

        public IncrementTask(long[] array, int start, int end) {
            this.array = array;
            this.start = start;
            this.end = end;
        }

        @Override
        protected void compute() {
            int length = end - start;

            if (length <= THRESHOLD) {
                for (int i = start; i < end; i++) {
                    array[i]++;
                }
                return;
            }

            int mid = start + length / 2;
            IncrementTask leftTask = new IncrementTask(array, start, mid);
            IncrementTask rightTask = new IncrementTask(array, mid, end);

            // Fork both tasks
            invokeAll(leftTask, rightTask);
        }
    }

    // ============================================
    // MAIN DEMO
    // ============================================
    public static void main(String[] args) {
        System.out.println("=== FORK/JOIN FRAMEWORK DEMO ===\n");

        // Create large array
        int size = 10_000_000;
        long[] array = new long[size];
        Arrays.fill(array, 1);

        // Demo 1: Sequential sum
        System.out.println("--- Sequential Sum ---");
        long start1 = System.currentTimeMillis();
        long sum1 = 0;
        for (long value : array) {
            sum1 += value;
        }
        long time1 = System.currentTimeMillis() - start1;
        System.out.println("  Sum: " + sum1);
        System.out.println("  Time: " + time1 + "ms");

        // Demo 2: Fork/Join sum
        System.out.println("\n--- Fork/Join Sum ---");
        ForkJoinPool pool = new ForkJoinPool();
        long start2 = System.currentTimeMillis();
        SumTask task = new SumTask(array, 0, array.length);
        long sum2 = pool.invoke(task);
        long time2 = System.currentTimeMillis() - start2;
        System.out.println("  Sum: " + sum2);
        System.out.println("  Time: " + time2 + "ms");
        System.out.println("  Speedup: " + (double)time1/time2 + "x");

        // Demo 3: RecursiveAction
        System.out.println("\n--- RecursiveAction (Increment) ---");
        long[] array2 = new long[1_000_000];
        Arrays.fill(array2, 1);
        
        long start3 = System.currentTimeMillis();
        IncrementTask incrementTask = new IncrementTask(array2, 0, array2.length);
        pool.invoke(incrementTask);
        long time3 = System.currentTimeMillis() - start3;
        
        System.out.println("  First element: " + array2[0]);
        System.out.println("  Last element: " + array2[array2.length - 1]);
        System.out.println("  Time: " + time3 + "ms");

        pool.shutdown();

        // ============================================
        // INTERVIEW TIPS
        // ============================================
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: Fork/Join vs ExecutorService?");
        System.out.println("A: Fork/Join - recursive tasks, work stealing");
        System.out.println("   ExecutorService - independent tasks, simpler");
        
        System.out.println("\nQ: RecursiveTask vs RecursiveAction?");
        System.out.println("A: RecursiveTask - có return value");
        System.out.println("   RecursiveAction - void, no return");
        
        System.out.println("\nQ: Work stealing?");
        System.out.println("A: Idle threads steal work from busy threads");
        System.out.println("   Better load balancing, efficient");
        
        System.out.println("\nQ: Khi nào dùng Fork/Join?");
        System.out.println("A: ✓ Recursive algorithms");
        System.out.println("   ✓ Divide-and-conquer problems");
        System.out.println("   ✓ Large data processing");
        System.out.println("   ✗ Simple parallel tasks (use ExecutorService)");
        
        System.out.println("\nQ: Threshold size?");
        System.out.println("A: Balance between parallelism và overhead");
        System.out.println("   Too small → too much overhead");
        System.out.println("   Too large → not enough parallelism");
        System.out.println("   Experiment và tune!");

        System.out.println("\n=== USE CASES ===");
        System.out.println("- Array/Collection processing");
        System.out.println("- Tree traversal");
        System.out.println("- Merge sort, Quick sort");
        System.out.println("- Matrix operations");
        System.out.println("- Image processing");
    }
}
