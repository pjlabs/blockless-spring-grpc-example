package org.pjlabs.example;

import io.github.pjlabs.blockless.Blockless;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pjlabs.example.db.Product;
import org.pjlabs.example.db.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Four-way benchmark: PT vs VT × .join() vs Blockless.get() with real Postgres via Testcontainers.
 *
 * <p>Run with: mvn test -Dtest=BlocklessBenchmarkIT
 */
@SpringBootTest(properties = {"grpc.server.port=-1", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Testcontainers
class BlocklessBenchmarkIT {

  private static final int CONCURRENT = 200;
  private static final int ROUNDS = 3;
  private static final int PT_POOL = Runtime.getRuntime().availableProcessors();
  private static final double PG_SLEEP_SECONDS = 0.05; // 50ms

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:18").withDatabaseName("benchmark");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    // HikariCP pool size — this is the DB connection pool
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
  }

  @Autowired private ProductRepository productRepository;

  @BeforeEach
  void seedData() {
    if (productRepository.count() == 0) {
      for (final var country : new String[] {"SE", "US", "GB", "DE", "JP"}) {
        for (int i = 0; i < 20; i++) {
          productRepository.save(new Product("Product-" + i, country, 100 + i));
        }
      }
    }
  }

  @Test
  void fourWayBenchmark() throws Exception {
    System.out.println("\n=== Warming up ===");
    for (int i = 0; i < 5; i++) {
      productRepository.countByCountryWithDelay("SE", PG_SLEEP_SECONDS);
    }

    System.out.printf(
        "\nConfig: %d concurrent, PT pool=%d, HikariCP pool=10, query=%.0fms pg_sleep, %d rounds%n%n",
        CONCURRENT, PT_POOL, PG_SLEEP_SECONDS * 1000, ROUNDS);

    final var ptJoinAvg = runScenario("PT + .join()", true, false);
    final var ptBlocklessAvg = runScenario("PT + Blockless.get()", true, true);
    final var vtJoinAvg = runScenario("VT + .join()", false, false);
    final var vtBlocklessAvg = runScenario("VT + Blockless.get()", false, true);

    System.out.println("\n═══════════════════════════════════════════════════");
    System.out.printf("  AVERAGE TIME for %d concurrent requests%n", CONCURRENT);
    System.out.println("═══════════════════════════════════════════════════");
    System.out.printf("  PT + .join():          %,6d ms%n", ptJoinAvg);
    System.out.printf("  PT + Blockless.get():  %,6d ms%n", ptBlocklessAvg);
    System.out.printf("  VT + .join():          %,6d ms%n", vtJoinAvg);
    System.out.printf("  VT + Blockless.get():  %,6d ms%n", vtBlocklessAvg);
    System.out.println("═══════════════════════════════════════════════════\n");
  }

  private long runScenario(final String label, final boolean usePT, final boolean useBlockless)
      throws Exception {
    // Warmup
    fireAndWait(usePT, useBlockless);

    long total = 0;
    for (int r = 1; r <= ROUNDS; r++) {
      final var elapsed = fireAndWait(usePT, useBlockless);
      System.out.printf(
          "  %s round %d: %d ms [thread: %s]%n", label, r, elapsed, usePT ? "PT" : "VT");
      total += elapsed;
    }

    final var avg = total / ROUNDS;
    System.out.printf("  %s avg: %d ms%n%n", label, avg);
    return avg;
  }

  private long fireAndWait(final boolean usePT, final boolean useBlockless) throws Exception {
    try (var executor =
        usePT
            ? Executors.newFixedThreadPool(PT_POOL)
            : Executors.newVirtualThreadPerTaskExecutor()) {

      final var start = System.nanoTime();

      final var futures = new CompletableFuture<?>[CONCURRENT];
      for (int i = 0; i < CONCURRENT; i++) {
        futures[i] =
            CompletableFuture.runAsync(
                () -> {
                  if (useBlockless) {
                    Blockless.get(
                        () -> productRepository.countByCountryWithDelay("SE", PG_SLEEP_SECONDS));
                  } else {
                    productRepository.countByCountryWithDelay("SE", PG_SLEEP_SECONDS);
                  }
                },
                executor);
      }

      CompletableFuture.allOf(futures).join();
      return (System.nanoTime() - start) / 1_000_000;
    }
  }
}
