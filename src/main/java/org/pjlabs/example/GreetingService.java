package org.pjlabs.example;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.pjlabs.blockless.Blockless;
import org.pjlabs.blockless.Parallel;
import org.pjlabs.blockless.context.slf4j.Slf4jMdcContextPropagator;
import org.pjlabs.example.proto.GreetAllReply;
import org.pjlabs.example.proto.GreetAllRequest;
import org.pjlabs.example.proto.GreetReply;
import org.pjlabs.example.proto.GreetRequest;
import org.pjlabs.example.proto.GreetingServiceGrpc;
import org.slf4j.MDC;

/**
 * gRPC service demonstrating blockless utilities.
 *
 * <ul>
 *   <li>{@code Greet} — uses {@link Blockless#get} to wait on a slow downstream
 *   <li>{@code GreetAll} — uses {@link Parallel#map} to fan out calls with MDC propagation
 * </ul>
 */
@GrpcService
public class GreetingService extends GreetingServiceGrpc.GreetingServiceImplBase {

  private static final Parallel PARALLEL = Parallel.create(new Slf4jMdcContextPropagator());

  private final SlowDownstreamClient downstream;

  public GreetingService(final SlowDownstreamClient downstream) {
    this.downstream = downstream;
  }

  @Override
  public void greet(final GreetRequest request, final StreamObserver<GreetReply> responseObserver) {
    final var greeting = Blockless.get(downstream.fetchGreeting(request.getName()));

    final var reply =
        GreetReply.newBuilder()
            .setMessage(greeting)
            .setThreadName(Thread.currentThread().getName())
            .setVirtualThread(Thread.currentThread().isVirtual())
            .setTraceId(MDC.get("traceId") != null ? MDC.get("traceId") : "")
            .build();

    responseObserver.onNext(reply);
    responseObserver.onCompleted();
  }

  @Override
  public void greetAll(
      final GreetAllRequest request, final StreamObserver<GreetAllReply> responseObserver) {
    final var names = request.getNamesList();

    // Each parallel task captures its own thread info and MDC — proving propagation
    final var replies =
        PARALLEL.map(
            names,
            name -> {
              final var greeting = Blockless.get(downstream.fetchGreeting(name));
              return GreetReply.newBuilder()
                  .setMessage(greeting)
                  .setThreadName(Thread.currentThread().getName())
                  .setVirtualThread(Thread.currentThread().isVirtual())
                  .setTraceId(MDC.get("traceId") != null ? MDC.get("traceId") : "")
                  .build();
            });

    final var replyBuilder = GreetAllReply.newBuilder();
    replies.forEach(replyBuilder::addReplies);

    responseObserver.onNext(replyBuilder.build());
    responseObserver.onCompleted();
  }
}
