package org.pjlabs.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.jupiter.api.Test;
import org.pjlabs.example.proto.GreetAllRequest;
import org.pjlabs.example.proto.GreetRequest;
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
}
