package org.pjlabs.example;

import com.zaxxer.hikari.HikariDataSource;
import io.github.pjlabs.blockless.Blockless;
import io.github.pjlabs.blockless.Parallel;
import io.github.pjlabs.blockless.context.slf4j.Slf4jMdcContextPropagator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.pjlabs.example.db.ProductRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP benchmark endpoints — same logic as BenchmarkGrpcService, for JMeter.
 *
 * <p>GET /benchmark/query?mode=join&country=SE&sleepSeconds=0.05&threadType=pt&ptPoolSize=10&dbPoolSize=5
 * <p>GET /benchmark/fanout?mode=parallel&countries=SE,US,GB,DE,JP&sleepSeconds=0.05&maxConcurrency=3
 */
@RestController
@RequestMapping("/benchmark")
public class BenchmarkController {

  private static final ExecutorService VT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
  private static final int DEFAULT_PT_POOL = Runtime.getRuntime().availableProcessors();
  private static volatile ExecutorService ptExecutor = Executors.newFixedThreadPool(DEFAULT_PT_POOL);
  private static volatile int currentPtPoolSize = DEFAULT_PT_POOL;

  private final ProductRepository productRepository;
  private final HikariDataSource dataSource;

  public BenchmarkController(final ProductRepository productRepository, final DataSource dataSource) {
    this.productRepository = productRepository;
    this.dataSource = (HikariDataSource) dataSource;
  }

  @GetMapping("/query")
  public Map<String, Object> query(
      @RequestParam(defaultValue = "join") final String mode,
      @RequestParam(defaultValue = "") final String threadType,
      @RequestParam(defaultValue = "SE") final String country,
      @RequestParam(defaultValue = "0.05") final double sleepSeconds,
      @RequestParam(defaultValue = "0") final int ptPoolSize,
      @RequestParam(defaultValue = "0") final int dbPoolSize) throws Exception {

    resizeDbPoolIfNeeded(dbPoolSize);

    if ("pt".equals(threadType) || "vt".equals(threadType)) {
      final var executor = "pt".equals(threadType) ? getPtExecutor(ptPoolSize) : VT_EXECUTOR;
      final var future = CompletableFuture.supplyAsync(() -> doQuery(mode, country, sleepSeconds), executor);
      return future.get();
    }

    return doQuery(mode, country, sleepSeconds);
  }

  @GetMapping("/fanout")
  public Map<String, Object> fanOut(
      @RequestParam(defaultValue = "serial") final String mode,
      @RequestParam(defaultValue = "SE,US,GB,DE,JP") final List<String> countries,
      @RequestParam(defaultValue = "0.05") final double sleepSeconds,
      @RequestParam(defaultValue = "0") final int maxConcurrency) {

    final var start = System.nanoTime();

    final List<Integer> results;
    if ("parallel".equals(mode)) {
      var parallel = Parallel.create(new Slf4jMdcContextPropagator());
      if (maxConcurrency > 0) {
        parallel = parallel.withMaxConcurrency(maxConcurrency);
      }
      results = parallel.map(countries, c -> productRepository.countByCountryWithDelay(c, sleepSeconds));
    } else {
      results = countries.stream()
          .map(c -> productRepository.countByCountryWithDelay(c, sleepSeconds))
          .toList();
    }

    final var elapsed = (System.nanoTime() - start) / 1_000_000;

    return Map.of(
        "results", results,
        "elapsedMs", elapsed,
        "threadName", Thread.currentThread().getName(),
        "virtualThread", Thread.currentThread().isVirtual());
  }

  private Map<String, Object> doQuery(final String mode, final String country, final double sleepSeconds) {
    final var start = System.nanoTime();

    final int result;
    if ("blockless".equals(mode)) {
      result = Blockless.get(() -> productRepository.countByCountryWithDelay(country, sleepSeconds));
    } else {
      result = productRepository.countByCountryWithDelay(country, sleepSeconds);
    }

    final var elapsed = (System.nanoTime() - start) / 1_000_000;

    return Map.of(
        "result", result,
        "elapsedMs", elapsed,
        "threadName", Thread.currentThread().getName(),
        "virtualThread", Thread.currentThread().isVirtual());
  }

  private void resizeDbPoolIfNeeded(final int requestedSize) {
    if (requestedSize > 0 && requestedSize != dataSource.getMaximumPoolSize()) {
      dataSource.setMaximumPoolSize(requestedSize);
    }
  }

  private static synchronized ExecutorService getPtExecutor(final int requestedSize) {
    final var size = requestedSize > 0 ? requestedSize : DEFAULT_PT_POOL;
    if (size != currentPtPoolSize) {
      ptExecutor.shutdownNow();
      ptExecutor = Executors.newFixedThreadPool(size);
      currentPtPoolSize = size;
    }
    return ptExecutor;
  }
}
