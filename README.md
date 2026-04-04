# blockless-spring-grpc-example

A Spring Boot + gRPC demo app showing [blockless](https://github.com/pjlabs/blockless) in action.

## What it demonstrates

- `Blockless.get()` — wait on a slow downstream without blocking platform threads
- `Parallel.map()` — fan out multiple calls with MDC context propagation
- SLF4J MDC traceId flows from gRPC metadata through virtual threads

## Run

```sh
mvn spring-boot:run
```

gRPC server starts on port 9090.

## Try it

Requires [grpcurl](https://github.com/fullstorydev/grpcurl).

**Single greeting** — demonstrates `Blockless.get()`:
```sh
grpcurl -plaintext -d '{"name": "Toothless"}' localhost:9090 org.pjlabs.example.GreetingService/Greet
```

**Fan-out greeting** — demonstrates `Parallel.map()`:
```sh
grpcurl -plaintext -d '{"names": ["Alpha", "Beta", "Gamma", "Delta", "Epsilon"]}' localhost:9090 org.pjlabs.example.GreetingService/GreetAll
```

**With trace-id** — demonstrates MDC propagation through virtual threads:
```sh
grpcurl -plaintext -H 'trace-id: dragon-trace-789' -d '{"names": ["A", "B", "C"]}' localhost:9090 org.pjlabs.example.GreetingService/GreetAll
```

Check the app logs to see thread names, virtual thread info, and traceId flowing through.

## Tests

```sh
mvn test
```

6 tests covering:
- `Blockless.get()` round-trip
- `Parallel.map()` fan-out correctness and parallelism (5 x 100ms in <400ms)
- MDC traceId propagation from gRPC header to each virtual thread

## Requirements

- Java 21+
- Maven

## License

Apache 2.0
