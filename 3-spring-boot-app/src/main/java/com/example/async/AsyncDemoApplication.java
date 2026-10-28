package com.example.async;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SPRING BOOT ASYNC APPLICATION
 * 
 * @EnableAsync - Enable async method execution
 * @EnableScheduling - Enable scheduled tasks
 * 
 * Interview Topics:
 * - @Async configuration
 * - Thread pool tuning
 * - Exception handling
 * - Performance monitoring
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class AsyncDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsyncDemoApplication.class, args);
        System.out.println("\n=== Spring Boot Async Demo Started ===");
        System.out.println("Try these endpoints:");
        System.out.println("  GET  http://localhost:8080/api/sync");
        System.out.println("  GET  http://localhost:8080/api/async");
        System.out.println("  GET  http://localhost:8080/api/parallel");
        System.out.println("  POST http://localhost:8080/api/process");
        System.out.println("\nActuator:");
        System.out.println("  http://localhost:8080/actuator/health");
        System.out.println("  http://localhost:8080/actuator/metrics");
    }
}
