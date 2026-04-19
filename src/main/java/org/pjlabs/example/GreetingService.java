package org.pjlabs.example;

import io.github.pjlabs.blockless.Blockless;
import io.github.pjlabs.blockless.Parallel;
import io.github.pjlabs.blockless.context.grpc.GrpcContextPropagator;
import io.github.pjlabs.blockless.context.slf4j.Slf4jMdcContextPropagator;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.pjlabs.example.proto.GreetAllReply;
import org.pjlabs.example.proto.GreetAllRequest;
import org.pjlabs.example.proto.GreetReply;
import org.pjlabs.example.proto.GreetRequest;
import org.pjlabs.example.proto.GreetResult;
import org.pjlabs.example.proto.GreetSafeReply;
import org.pjlabs.example.proto.GreetingServiceGrpc;
import org.slf4j.MDC;

/**
 * gRPC service demonstrating blockless utilities.
 *
 * <ul>
 *   <li>{@code Greet} — uses {@link Blockless#get} to wait on a slow downstream
 *   <li>{@code GreetAll} — uses {@link Parallel#map} with bounded concurrency and context
 *       propagation
 *   <li>{@code GreetSafe} — uses {@link Parallel#toEither} for partial failure handling
 * </ul>
 */
@GrpcService
public class GreetingService extends GreetingServiceGrpc.GreetingServiceImplBase {

  private static final Parallel PARALLEL =
      Parallel.create(new GrpcContextPropagator(), new Slf4jMdcContextPropagator())
          .withMaxConcurrency(5);

  private final SlowDownstreamClient downstream;

  public GreetingService(final SlowDownstreamClient downstream) {
    this.downstream = downstream;
  }

  @Override
  public void greet(final GreetRequest request, final StreamObserver<GreetReply> responseObserver) {
    final var greeting = Blockless.get(downstream.fetchGreeting(request.getName()));

    final var reply = buildReply(greeting);

    responseObserver.onNext(reply);
    responseObserver.onCompleted();
  }

  @Override
  public void greetAll(
      final GreetAllRequest request, final StreamObserver<GreetAllReply> responseObserver) {
    final var names = request.getNamesList();

    final var replies =
        PARALLEL.map(
            names,
            name -> {
              final var greeting = Blockless.get(downstream.fetchGreeting(name));
              return buildReply(greeting);
            });

    final var replyBuilder = GreetAllReply.newBuilder();
    replies.forEach(replyBuilder::addReplies);

    responseObserver.onNext(replyBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void greetSafe(
      final GreetAllRequest request, final StreamObserver<GreetSafeReply> responseObserver) {
    final var names = request.getNamesList();

    final var results =
        PARALLEL.toEither(
            names,
            name -> {
              final var greeting = Blockless.get(downstream.fetchGreeting(name));
              return buildReply(greeting);
            });

    final var replyBuilder = GreetSafeReply.newBuilder();
    for (final var either : results) {
      if (either.isOk()) {
        replyBuilder.addResults(GreetResult.newBuilder().setReply(either.result()));
      } else {
        replyBuilder.addResults(GreetResult.newBuilder().setError(either.failure().getMessage()));
      }
    }

    responseObserver.onNext(replyBuilder.build());
    responseObserver.onCompleted();
  }

  private GreetReply buildReply(final String greeting) {
    return GreetReply.newBuilder()
        .setMessage(greeting)
        .setThreadName(Thread.currentThread().getName())
        .setVirtualThread(Thread.currentThread().isVirtual())
        .setTraceId(MDC.get("traceId") != null ? MDC.get("traceId") : "")
        .build();
  }
}
