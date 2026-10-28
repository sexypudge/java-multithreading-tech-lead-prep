import java.util.concurrent.*;
import java.util.*;

/**
 * COMPLETABLE FUTURE - ASYNC PROGRAMMING HIỆN ĐẠI
 * 
 * CompletableFuture vs Future:
 * ✓ Non-blocking - không cần get() blocking
 * ✓ Composable - chain operations
 * ✓ Exception handling tốt hơn
 * ✓ Combine multiple futures
 * 
 * Interview Question: "CompletableFuture vs Future?"
 * Answer: Future chỉ có get() blocking, CompletableFuture có callbacks,
 *         chaining, combining, better error handling
 */
public class CompletableFutureDemo {

    // ============================================
    // 1. BASIC USAGE
    // ============================================
    static void basicUsageDemo() throws ExecutionException, InterruptedException {
        System.out.println("--- Basic Usage ---");

        // Create completed future
        CompletableFuture<String> future1 = CompletableFuture.completedFuture("Hello");
        System.out.println("  Completed: " + future1.get());

        // Async computation
        CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> {
            sleep(1000);
            return "World";
        });
        System.out.println("  Async: " + future2.get());

        // Run async (no return value)
        CompletableFuture<Void> future3 = CompletableFuture.runAsync(() -> {
            System.out.println("  Running async task...");
        });
        future3.get();
    }

    // ============================================
    // 2. CHAINING - thenApply, thenAccept, thenRun
    // ============================================
    static void chainingDemo() throws ExecutionException, InterruptedException {
        System.out.println("\n--- Chaining Operations ---");

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            System.out.println("  [1] Fetching user...");
            sleep(1000);
            return "User123";
        })
        .thenApply(user -> {
            System.out.println("  [2] Fetching orders for " + user);
            sleep(1000);
            return user + "'s orders";
        })
        .thenApply(orders -> {
            System.out.println("  [3] Processing " + orders);
            return orders.toUpperCase();
        });

        System.out.println("  Result: " + future.get());
    }

    // ============================================
    // 3. COMBINING - thenCompose, thenCombine
    // ============================================
    static void combiningDemo() throws ExecutionException, InterruptedException {
        System.out.println("\n--- Combining Futures ---");

        // thenCompose - sequential (flatMap)
        CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> {
            System.out.println("  Getting user ID...");
            return "123";
        }).thenCompose(userId -> CompletableFuture.supplyAsync(() -> {
            System.out.println("  Getting user details for " + userId);
            return "User-" + userId;
        }));

        System.out.println("  thenCompose: " + future1.get());

        // thenCombine - parallel
        CompletableFuture<String> userFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("  Fetching user...");
            sleep(1000);
            return "John";
        });

        CompletableFuture<Integer> ageFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("  Fetching age...");
            sleep(1000);
            return 30;
        });

        CompletableFuture<String> combined = userFuture.thenCombine(ageFuture, 
            (user, age) -> user + " is " + age + " years old");

        System.out.println("  thenCombine: " + combined.get());
    }

    // ============================================
    // 4. EXCEPTION HANDLING
    // ============================================
    static void exceptionHandlingDemo() {
        System.out.println("\n--- Exception Handling ---");

        // exceptionally - handle error
        CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> {
            if (Math.random() > 0.5) {
                throw new RuntimeException("Random error!");
            }
            return "Success";
        }).exceptionally(ex -> {
            System.out.println("  Error handled: " + ex.getMessage());
            return "Default value";
        });

        System.out.println("  Result: " + future1.join());

        // handle - handle both success and error
        CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("Error!");
        }).handle((result, ex) -> {
            if (ex != null) {
                return "Error: " + ex.getMessage();
            }
            return result;
        });

        System.out.println("  Handled: " + future2.join());
    }

    // ============================================
    // 5. MULTIPLE FUTURES - allOf, anyOf
    // ============================================
    static void multipleFuturesDemo() throws ExecutionException, InterruptedException {
        System.out.println("\n--- Multiple Futures ---");

        // allOf - wait for all
        CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> {
            sleep(1000);
            return "Task 1";
        });

        CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> {
            sleep(2000);
            return "Task 2";
        });

        CompletableFuture<String> f3 = CompletableFuture.supplyAsync(() -> {
            sleep(1500);
            return "Task 3";
        });

        CompletableFuture<Void> allOf = CompletableFuture.allOf(f1, f2, f3);
        allOf.get(); // Wait for all

        System.out.println("  All completed: " + f1.get() + ", " + f2.get() + ", " + f3.get());

        // anyOf - wait for first
        CompletableFuture<Object> anyOf = CompletableFuture.anyOf(
            CompletableFuture.supplyAsync(() -> { sleep(1000); return "Fast"; }),
            CompletableFuture.supplyAsync(() -> { sleep(2000); return "Slow"; })
        );

        System.out.println("  First completed: " + anyOf.get());
    }

    // ============================================
    // 6. REAL-WORLD EXAMPLE - API CALLS
    // ============================================
    static void realWorldExample() throws ExecutionException, InterruptedException {
        System.out.println("\n--- Real-World: Parallel API Calls ---");

        long start = System.currentTimeMillis();

        // Simulate 3 API calls in parallel
        CompletableFuture<String> userApi = CompletableFuture.supplyAsync(() -> {
            sleep(1000);
            return "User data";
        });

        CompletableFuture<String> orderApi = CompletableFuture.supplyAsync(() -> {
            sleep(1500);
            return "Order data";
        });

        CompletableFuture<String> paymentApi = CompletableFuture.supplyAsync(() -> {
            sleep(800);
            return "Payment data";
        });

        // Combine all results
        CompletableFuture<String> result = CompletableFuture.allOf(userApi, orderApi, paymentApi)
            .thenApply(v -> {
                try {
                    return String.format("Combined: %s, %s, %s", 
                        userApi.get(), orderApi.get(), paymentApi.get());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

        System.out.println("  " + result.get());
        System.out.println("  Time: " + (System.currentTimeMillis() - start) + "ms (parallel!)");
    }

    // ============================================
    // 7. TIMEOUT & COMPLETION
    // ============================================
    static void timeoutDemo() {
        System.out.println("\n--- Timeout & Completion ---");

        // completeOnTimeout (Java 9+)
        CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> {
            sleep(3000);
            return "Slow result";
        }).completeOnTimeout("Timeout value", 1, TimeUnit.SECONDS);

        System.out.println("  With timeout: " + future1.join());

        // Manual completion
        CompletableFuture<String> future2 = new CompletableFuture<>();
        
        // Complete manually after delay
        CompletableFuture.runAsync(() -> {
            sleep(1000);
            future2.complete("Manual completion");
        });

        System.out.println("  Manual: " + future2.join());
    }

    // ============================================
    // MAIN DEMO
    // ============================================
    public static void main(String[] args) throws Exception {
        System.out.println("=== COMPLETABLE FUTURE DEMO ===\n");

        basicUsageDemo();
        chainingDemo();
        combiningDemo();
        exceptionHandlingDemo();
        multipleFuturesDemo();
        realWorldExample();
        timeoutDemo();

        // ============================================
        // INTERVIEW TIPS
        // ============================================
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("Q: CompletableFuture vs Future?");
        System.out.println("A: Future - chỉ get() blocking");
        System.out.println("   CompletableFuture - callbacks, chaining, combining");
        
        System.out.println("\nQ: thenApply vs thenCompose?");
        System.out.println("A: thenApply - transform value (map)");
        System.out.println("   thenCompose - chain futures (flatMap)");
        
        System.out.println("\nQ: thenCombine vs thenCompose?");
        System.out.println("A: thenCombine - parallel, combine 2 results");
        System.out.println("   thenCompose - sequential, chain operations");
        
        System.out.println("\nQ: Exception handling?");
        System.out.println("A: exceptionally - handle error only");
        System.out.println("   handle - handle both success and error");
        System.out.println("   whenComplete - side effect, không change result");
        
        System.out.println("\nQ: allOf vs anyOf?");
        System.out.println("A: allOf - wait for all futures");
        System.out.println("   anyOf - wait for first completed");
        
        System.out.println("\nQ: Use cases?");
        System.out.println("A: - Parallel API calls");
        System.out.println("   - Async processing pipelines");
        System.out.println("   - Non-blocking I/O");
        System.out.println("   - Microservices communication");

        System.out.println("\n=== COMMON PATTERNS ===");
        System.out.println("// Sequential");
        System.out.println("future.thenCompose(a -> nextFuture(a))");
        System.out.println("\n// Parallel");
        System.out.println("future1.thenCombine(future2, (a,b) -> combine(a,b))");
        System.out.println("\n// Error handling");
        System.out.println("future.exceptionally(ex -> defaultValue)");
    }

    // Helper
    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
