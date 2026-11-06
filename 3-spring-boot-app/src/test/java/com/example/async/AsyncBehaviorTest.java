package com.example.async;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Biến các câu trả lời phỏng vấn về @Async thành assertion chạy được.
 * Chạy: mvn test
 */
@SpringBootTest(properties = "demo.scheduling.enabled=false")
class AsyncBehaviorTest {

    @Autowired
    AsyncService service;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void asyncMethodCalledThroughProxyRunsOnPoolThread() {
        String where = service.whereAmI().join();
        assertThat(where).startsWith("io-");
    }

    @Test
    void selfInvocationBypassesProxyAndRunsOnCallerThread() {
        String where = service.selfInvocation();
        assertThat(where).startsWith(Thread.currentThread().getName());
    }

    @Test
    void mdcIsPropagatedToPoolThreadByTaskDecorator() {
        MDC.put(RequestIdFilter.KEY, "test-123");
        String where = service.whereAmI().join();
        assertThat(where).contains("requestId=test-123");
    }

    @Test
    void exceptionFromAsyncMethodCompletesTheFutureExceptionally() {
        CompletableFuture<String> future = service.failingCall();
        assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void independentCallsRunInParallel() {
        long start = System.nanoTime();
        CompletableFuture<String> a = service.callDownstream("a", 400);
        CompletableFuture<String> b = service.callDownstream("b", 400);
        CompletableFuture<String> c = service.callDownstream("c", 400);
        CompletableFuture.allOf(a, b, c).join();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertThat(elapsedMs).isLessThan(1000);   // tuần tự sẽ ≥ 1200ms
    }

    @Test
    void completeOnTimeoutReturnsFallbackWithoutWaitingForSlowCall() {
        long start = System.nanoTime();
        String result = service.callDownstream("slow", 3000)
                .completeOnTimeout("fallback", 200, TimeUnit.MILLISECONDS)
                .join();
        assertThat(result).isEqualTo("fallback");
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(1500);
    }
}
