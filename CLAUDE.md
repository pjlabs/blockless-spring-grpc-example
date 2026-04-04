# CLAUDE.md

## Project

Spring Boot + gRPC example app demonstrating the blockless library.
Shows blockless works outside any specific framework ecosystem.

## Stack

- Spring Boot 3.2.x + Java 21
- `net.devh:grpc-server-spring-boot-starter` for gRPC
- blockless from GitHub Packages (`org.pjlabs:blockless`)
- `fmt-maven-plugin` (Google Java Format)

## Build & Run

```sh
mvn compile                    # Compile + generate proto stubs
mvn test                       # Run tests
mvn spring-boot:run            # Start the app (gRPC on port 9090)
```

## Code Conventions

- **Java 21**: No preview features
- **`final var` over explicit types**
- **Imports over FQNs**
- **Method references over lambdas** when possible
- **Google Java Format**: enforced via `fmt-maven-plugin`

## Design Rules

- Keep it minimal — this is a demo, not a framework
- Every blockless feature used must be demonstrated with a test
- Don't add dependencies unless needed
