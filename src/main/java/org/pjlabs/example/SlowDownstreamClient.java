package org.pjlabs.example;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Simulates a slow downstream service (e.g., a database or external API). */
@Component
public class SlowDownstreamClient {

  private static final Logger LOG = LoggerFactory.getLogger(SlowDownstreamClient.class);

  /**
   * Returns a CompletionStage that completes after a simulated delay. The response includes thread
   * and MDC info to prove context propagation.
   */
  public CompletionStage<String> fetchGreeting(final String name) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            Thread.sleep(100); // simulate I/O latency
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          final var traceId = MDC.get("traceId");
          LOG.info(
              "Fetching greeting for '{}' on thread={} traceId={}",
              name,
              Thread.currentThread().getName(),
              traceId);
          if ("FAIL".equals(name)) {
            throw new RuntimeException("Downstream error for " + name);
          }
          return "Hello, " + name + "!";
        });
  }
}
