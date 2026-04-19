# blockless-spring-grpc-example

A Spring Boot + gRPC demo app showing [blockless](https://github.com/pjlabs/blockless) in action.

## What it demonstrates

| RPC | Blockless feature | What it proves |
|---|---|---|
| `Greet` | `Blockless.get()` | Wait on a slow downstream without blocking platform threads |
| `GreetAll` | `Parallel.map()` + `withMaxConcurrency(5)` | Bounded parallel fan-out with gRPC + MDC context propagation |
| `GreetSafe` | `Parallel.toEither()` | Partial failure handling — some succeed, some fail, results stay in order |

Context propagation: SLF4J MDC traceId and gRPC Context flow from the
incoming request through virtual threads via `Slf4jMdcContextPropagator`
and `GrpcContextPropagator`.

## Run

```sh
mvn spring-boot:run
```

gRPC server starts on port 9090.

## Try it

Requires [grpcurl](https://github.com/fullstorydev/grpcurl).

**Single greeting** — `Blockless.get()`:
```sh
grpcurl -plaintext -d '{"name": "Toothless"}' localhost:9090 org.pjlabs.example.GreetingService/Greet
```

**Fan-out** — `Parallel.map()` with bounded concurrency:
```sh
grpcurl -plaintext -d '{"names": ["Alpha", "Beta", "Gamma", "Delta", "Epsilon"]}' localhost:9090 org.pjlabs.example.GreetingService/GreetAll
```

**Partial failure** — `Parallel.toEither()`, pass `"FAIL"` to trigger an error:
```sh
grpcurl -plaintext -d '{"names": ["Alpha", "FAIL", "Gamma"]}' localhost:9090 org.pjlabs.example.GreetingService/GreetSafe
```

**With trace-id** — context propagation through virtual threads:
```sh
grpcurl -plaintext -H 'trace-id: dragon-trace-789' -d '{"names": ["A", "B", "C"]}' localhost:9090 org.pjlabs.example.GreetingService/GreetAll
```

Check the app logs to see thread names, virtual thread info, and traceId flowing through.

## Tests

```sh
mvn test
```

8 tests covering:
- `Blockless.get()` round-trip
- `Parallel.map()` fan-out correctness and parallelism (5 x 100ms in <400ms)
- `Parallel.toEither()` partial failure and all-success cases
- MDC traceId propagation from gRPC header to each virtual thread

## Requirements

- Java 21+
- Maven

## License

Apache 2.0
