# blockless-spring-grpc-example

A Spring Boot + gRPC demo app showing [blockless](https://github.com/pjlabs/blockless) in action, with real Postgres for benchmarking.

## What it demonstrates

| RPC | Blockless feature | What it proves |
|---|---|---|
| `Greet` | `Blockless.get()` | Wait on a slow downstream without blocking platform threads |
| `GreetAll` | `Parallel.map()` + `withMaxConcurrency(5)` | Bounded parallel fan-out with gRPC + MDC context propagation |
| `GreetSafe` | `Parallel.toEither()` | Partial failure handling — some succeed, some fail, results stay in order |
| `Query` | `Blockless.get()` vs direct call | Benchmark: compare join vs blockless on real DB queries |
| `FanOut` | `Parallel.map()` vs serial | Benchmark: compare serial vs parallel fan-out on real DB |

## Setup

Requires Docker, Java 21+, Maven.

```sh
# Start Postgres
docker compose up -d

# Start the app (gRPC on port 9090, Postgres on 5433)
mvn spring-boot:run -Dfmt.skip=true
```

## Try it

Requires [grpcurl](https://github.com/fullstorydev/grpcurl).

### GreetingService

```sh
grpcurl -plaintext -d '{"name": "Toothless"}' localhost:9090 org.pjlabs.example.GreetingService/Greet

grpcurl -plaintext -d '{"names": ["Alpha", "Beta", "Gamma", "Delta", "Epsilon"]}' localhost:9090 org.pjlabs.example.GreetingService/GreetAll

grpcurl -plaintext -d '{"names": ["Alpha", "FAIL", "Gamma"]}' localhost:9090 org.pjlabs.example.GreetingService/GreetSafe

grpcurl -plaintext -H 'trace-id: dragon-trace-789' -d '{"names": ["A", "B", "C"]}' localhost:9090 org.pjlabs.example.GreetingService/GreetAll
```

### BenchmarkService

**Single query — join vs blockless:**
```sh
# Direct call (default gRPC handler thread)
grpcurl -plaintext -d '{"mode": "join", "country": "SE", "sleep_seconds": 0.05}' localhost:9090 org.pjlabs.example.BenchmarkService/Query

# Blockless.get() — runs query on a virtual thread
grpcurl -plaintext -d '{"mode": "blockless", "country": "SE", "sleep_seconds": 0.05}' localhost:9090 org.pjlabs.example.BenchmarkService/Query

# Force handler onto a PT or VT
grpcurl -plaintext -d '{"mode": "blockless", "thread_type": "pt", "country": "SE", "sleep_seconds": 0.05}' localhost:9090 org.pjlabs.example.BenchmarkService/Query

grpcurl -plaintext -d '{"mode": "blockless", "thread_type": "vt", "country": "SE", "sleep_seconds": 0.05}' localhost:9090 org.pjlabs.example.BenchmarkService/Query
```

**Fan-out — serial vs parallel:**
```sh
# Serial (one query at a time)
grpcurl -plaintext -d '{"mode": "serial", "countries": ["SE","US","GB","DE","JP"], "sleep_seconds": 0.05}' localhost:9090 org.pjlabs.example.BenchmarkService/FanOut

# Parallel (all queries at once)
grpcurl -plaintext -d '{"mode": "parallel", "countries": ["SE","US","GB","DE","JP"], "sleep_seconds": 0.05}' localhost:9090 org.pjlabs.example.BenchmarkService/FanOut

# Parallel with bounded concurrency
grpcurl -plaintext -d '{"mode": "parallel", "countries": ["SE","US","GB","DE","JP"], "sleep_seconds": 0.05, "max_concurrency": 3}' localhost:9090 org.pjlabs.example.BenchmarkService/FanOut
```

### Benchmark parameters

| Parameter | Values | Description |
|---|---|---|
| `mode` | `join`, `blockless` (Query) / `serial`, `parallel` (FanOut) | Which approach to use |
| `thread_type` | `pt`, `vt`, empty | Force handler thread type (Query only) |
| `country` | any seeded country | `SE`, `US`, `GB`, `DE`, `JP`, `BR`, `IN`, `AU` |
| `sleep_seconds` | float | pg_sleep per query (simulates I/O latency) |
| `max_concurrency` | int | Limit parallel tasks (FanOut only, 0 = unbounded) |
| `pt_pool_size` | int | PT handler pool size (Query only, 0 = availableProcessors) |
| `db_pool_size` | int | HikariCP max pool size (Query only, 0 = keep current, default 10) |

## Load testing with JMeter

Concurrency is driven by JMeter (or any gRPC load tool), not the app itself. Each JMeter thread sends one request — N threads = N concurrent requests.

Example test matrix:

| JMeter threads | mode | thread_type | pt_pool_size | What it tests |
|---|---|---|---|---|
| 200 | join | (empty) | — | Baseline: gRPC default handler + direct DB call |
| 200 | blockless | (empty) | — | Blockless.get() on gRPC default handler |
| 200 | join | pt | 10 | PT handler pool of 10 + direct DB call |
| 200 | blockless | pt | 10 | PT handler pool of 10 + Blockless.get() |
| 200 | join | vt | — | VT handler + direct DB call |
| 200 | blockless | vt | — | VT handler + Blockless.get() |

For fan-out, use `BenchmarkService/FanOut` with `serial` vs `parallel` mode.

## Tests

```sh
mvn test
```

## Requirements

- Java 21+
- Maven
- Docker (for Postgres)

## License

Apache 2.0
