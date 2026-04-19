package org.pjlabs.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.inprocess.InProcessChannelBuilder;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.jupiter.api.Test;
import org.pjlabs.example.proto.GreetAllRequest;
import org.pjlabs.example.proto.GreetRequest;
import org.pjlabs.example.proto.GreetResult;
import org.pjlabs.example.proto.GreetingServiceGrpc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "grpc.server.inProcessName=test",
      "grpc.server.port=-1",
      "grpc.client.inProcess.address=in-process:test"
    })
class GreetingServiceTest {

  @GrpcClient("inProcess")
  private GreetingServiceGrpc.GreetingServiceBlockingStub greetingService;

  @Test
  void greetReturnsMessage() {
    final var request = GreetRequest.newBuilder().setName("Toothless").build();
    final var reply = greetingService.greet(request);

    assertEquals("Hello, Toothless!", reply.getMessage());
  }

  @Test
  void greetReportsThreadInfo() {
    final var request = GreetRequest.newBuilder().setName("Dragon").build();
    final var reply = greetingService.greet(request);

    assertTrue(
        !reply.getThreadName().isEmpty(), "reply should include the thread name that handled it");
  }

  @Test
  void greetAllFansOutAndReturnsAllGreetings() {
    final var request =
        GreetAllRequest.newBuilder().addNames("Alpha").addNames("Beta").addNames("Gamma").build();

    final var reply = greetingService.greetAll(request);

    assertEquals(3, reply.getRepliesCount());
    assertEquals("Hello, Alpha!", reply.getReplies(0).getMessage());
    assertEquals("Hello, Beta!", reply.getReplies(1).getMessage());
    assertEquals("Hello, Gamma!", reply.getReplies(2).getMessage());
  }

  @Test
  void greetAllCompletesInParallel() {
    // 5 names, each taking ~100ms downstream. If serial: ~500ms. If parallel: ~100ms + overhead.
    final var request =
        GreetAllRequest.newBuilder()
            .addNames("A")
            .addNames("B")
            .addNames("C")
            .addNames("D")
            .addNames("E")
            .build();

    final var start = System.nanoTime();
    final var reply = greetingService.greetAll(request);
    final var elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertEquals(5, reply.getRepliesCount());
    assertTrue(
        elapsedMs < 400,
        "expected parallel execution (~100ms) but took " + elapsedMs + "ms — suggests serial");
  }

  @Test
  void greetPropagatesTraceIdViaMdc() {
    final var channel =
        InProcessChannelBuilder.forName("test")
            .intercept(traceIdInterceptor("trace-dragon-123"))
            .usePlaintext()
            .build();

    try {
      final var stub = GreetingServiceGrpc.newBlockingStub(channel);
      final var reply = stub.greet(GreetRequest.newBuilder().setName("Toothless").build());

      assertEquals(
          "trace-dragon-123",
          reply.getTraceId(),
          "traceId from gRPC metadata must propagate to the service via MDC");
    } finally {
      channel.shutdownNow();
    }
  }

  @Test
  void greetAllPropagatesTraceIdToAllParallelTasks() {
    final var channel =
        InProcessChannelBuilder.forName("test")
            .intercept(traceIdInterceptor("trace-fanout-456"))
            .usePlaintext()
            .build();

    try {
      final var stub = GreetingServiceGrpc.newBlockingStub(channel);
      final var reply =
          stub.greetAll(
              GreetAllRequest.newBuilder().addNames("A").addNames("B").addNames("C").build());

      assertEquals(3, reply.getRepliesCount());
      for (final var r : reply.getRepliesList()) {
        assertEquals(
            "trace-fanout-456",
            r.getTraceId(),
            "traceId must propagate to each parallel task via Parallel.map + MDC");
      }
    } finally {
      channel.shutdownNow();
    }
  }

  @Test
  void greetSafeReturnsPartialResults() {
    final var request =
        GreetAllRequest.newBuilder().addNames("Alpha").addNames("FAIL").addNames("Gamma").build();

    final var reply = greetingService.greetSafe(request);

    assertEquals(3, reply.getResultsCount());

    // First succeeds
    assertEquals(GreetResult.OutcomeCase.REPLY, reply.getResults(0).getOutcomeCase());
    assertEquals("Hello, Alpha!", reply.getResults(0).getReply().getMessage());

    // Second fails
    assertEquals(GreetResult.OutcomeCase.ERROR, reply.getResults(1).getOutcomeCase());
    assertTrue(reply.getResults(1).getError().contains("Downstream error"));

    // Third succeeds
    assertEquals(GreetResult.OutcomeCase.REPLY, reply.getResults(2).getOutcomeCase());
    assertEquals("Hello, Gamma!", reply.getResults(2).getReply().getMessage());
  }

  @Test
  void greetSafeAllSucceed() {
    final var request = GreetAllRequest.newBuilder().addNames("A").addNames("B").build();

    final var reply = greetingService.greetSafe(request);

    assertEquals(2, reply.getResultsCount());
    assertTrue(
        reply.getResultsList().stream()
            .allMatch(r -> r.getOutcomeCase() == GreetResult.OutcomeCase.REPLY));
  }

  private static ClientInterceptor traceIdInterceptor(final String traceId) {
    return new ClientInterceptor() {
      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
          final MethodDescriptor<ReqT, RespT> method,
          final CallOptions callOptions,
          final Channel next) {
        return new SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
          @Override
          public void start(final Listener<RespT> responseListener, final Metadata headers) {
            headers.put(Metadata.Key.of("trace-id", Metadata.ASCII_STRING_MARSHALLER), traceId);
            super.start(responseListener, headers);
          }
        };
      }
    };
  }
}
