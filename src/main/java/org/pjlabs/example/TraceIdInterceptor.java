package org.pjlabs.example;

import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.slf4j.MDC;

/**
 * Server interceptor that reads a {@code trace-id} header from gRPC metadata and sets it in MDC.
 */
@GrpcGlobalServerInterceptor
public class TraceIdInterceptor implements ServerInterceptor {

  private static final Metadata.Key<String> TRACE_ID_KEY =
      Metadata.Key.of("trace-id", Metadata.ASCII_STRING_MARSHALLER);

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    final var traceId = headers.get(TRACE_ID_KEY);
    final var listener = next.startCall(call, headers);

    if (traceId == null) {
      return listener;
    }

    return new SimpleForwardingServerCallListener<>(listener) {
      @Override
      public void onMessage(final ReqT message) {
        MDC.put("traceId", traceId);
        try {
          super.onMessage(message);
        } finally {
          MDC.remove("traceId");
        }
      }

      @Override
      public void onHalfClose() {
        MDC.put("traceId", traceId);
        try {
          super.onHalfClose();
        } finally {
          MDC.remove("traceId");
        }
      }
    };
  }
}
