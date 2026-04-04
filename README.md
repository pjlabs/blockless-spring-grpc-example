# blockless-spring-grpc-example

A Spring Boot + gRPC demo app showing [blockless](https://github.com/pjlabs/blockless) in action outside any specific framework ecosystem.

## What it demonstrates

- `Blockless.get()` — wait on a slow downstream without blocking platform threads
- `Parallel.map()` — fan out multiple calls with MDC context propagation
- SLF4J MDC traceId flows through virtual threads

## Run

```sh
mvn spring-boot:run
```

gRPC server starts on port 9090.

## Requirements

- Java 21+
- Maven

## License

Apache 2.0
