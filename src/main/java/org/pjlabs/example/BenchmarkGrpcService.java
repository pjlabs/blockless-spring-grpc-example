package org.pjlabs.example;

import com.zaxxer.hikari.HikariDataSource;
import io.github.pjlabs.blockless.Blockless;
import io.github.pjlabs.blockless.Parallel;
import io.github.pjlabs.blockless.context.slf4j.Slf4jMdcContextPropagator;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import net.devh.boot.grpc.server.service.GrpcService;
import org.pjlabs.example.db.ProductRepository;
import org.pjlabs.example.proto.BenchmarkReply;
import org.pjlabs.example.proto.BenchmarkRequest;
import org.pjlabs.example.proto.BenchmarkServiceGrpc;
import org.pjlabs.example.proto.FanOutReply;
import org.pjlabs.example.proto.FanOutRequest;

@GrpcService
public class BenchmarkGrpcService extends BenchmarkServiceGrpc.BenchmarkServiceImplBase {

  private static final ExecutorService VT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
  private static final int DEFAULT_PT_POOL = Runtime.getRuntime().availableProcessors();
  private static volatile ExecutorService ptExecutor =
      Executors.newFixedThreadPool(DEFAULT_PT_POOL);
  private static volatile int currentPtPoolSize = DEFAULT_PT_POOL;

  private static synchronized ExecutorService getPtExecutor(final int requestedSize) {
    final var size = requestedSize > 0 ? requestedSize : DEFAULT_PT_POOL;
    if (size != currentPtPoolSize) {
      ptExecutor.shutdownNow();
      ptExecutor = Executors.newFixedThreadPool(size);
      currentPtPoolSize = size;
    }
    return ptExecutor;
  }

  private final ProductRepository productRepository;
  private final HikariDataSource dataSource;

  public BenchmarkGrpcService(
      final ProductRepository productRepository, final DataSource dataSource) {
    this.productRepository = productRepository;
    this.dataSource = (HikariDataSource) dataSource;
  }

  private void resizeDbPoolIfNeeded(final int requestedSize) {
    if (requestedSize > 0 && requestedSize != dataSource.getMaximumPoolSize()) {
      dataSource.setMaximumPoolSize(requestedSize);
    }
  }

  @Override
  public void query(
      final BenchmarkRequest request, final StreamObserver<BenchmarkReply> responseObserver) {
    resizeDbPoolIfNeeded(request.getDbPoolSize());

    final var country = request.getCountry().isEmpty() ? "SE" : request.getCountry();
    final var sleep = request.getSleepSeconds() > 0 ? request.getSleepSeconds() : 0.05;
    final var mode = request.getMode().isEmpty() ? "join" : request.getMode();
    final var threadType = request.getThreadType();

    Runnable work =
        () -> {
          final var start = System.nanoTime();
          BenchmarkReply reply = null;
          var future = CompletableFuture.supplyAsync(() -> {
              return productRepository.countByCountryWithDelay(country, sleep);
          }).thenApply(result -> {
            final var elapsed = (System.nanoTime() - start) / 1_000_000;
            return BenchmarkReply.newBuilder()
                .setResult(result)
                .setThreadName(Thread.currentThread().getName())
                .setVirtualThread(Thread.currentThread().isVirtual())
                .setElapsedMs(elapsed)
                .build();
          });
          
          if ("blockless".equals(mode)) {
            reply = Blockless.get(future);
          } else {
            reply = future.join();
          }

          responseObserver.onNext(reply);
          responseObserver.onCompleted();
        };

    if ("pt".equals(threadType)) {
      CompletableFuture.runAsync(work, getPtExecutor(request.getPtPoolSize()));
    } else if ("vt".equals(threadType)) {
      CompletableFuture.runAsync(work, VT_EXECUTOR);
    } else {
      work.run();
    }
  }

  @Override
  public void fanOut(
      final FanOutRequest request, final StreamObserver<FanOutReply> responseObserver) {
    final var start = System.nanoTime();

    final var countries =
        request.getCountriesList().isEmpty()
            ? List.of("SE", "US", "GB", "DE", "JP")
            : request.getCountriesList();
    final var sleep = request.getSleepSeconds() > 0 ? request.getSleepSeconds() : 0.05;
    final var maxConcurrency = request.getMaxConcurrency();

    final List<Integer> results;
    if ("parallel".equals(request.getMode())) {
      var parallel = Parallel.create(new Slf4jMdcContextPropagator());
      if (maxConcurrency > 0) {
        parallel = parallel.withMaxConcurrency(maxConcurrency);
      }
      results = parallel.map(countries, c -> productRepository.countByCountryWithDelay(c, sleep));
    } else {
      results =
          countries.stream().map(c -> productRepository.countByCountryWithDelay(c, sleep)).toList();
    }

    final var elapsed = (System.nanoTime() - start) / 1_000_000;

    responseObserver.onNext(
        FanOutReply.newBuilder()
            .addAllResults(results)
            .setElapsedMs(elapsed)
            .setVirtualThread(Thread.currentThread().isVirtual())
            .setThreadName(Thread.currentThread().getName())
            .build());
    responseObserver.onCompleted();
  }
}
