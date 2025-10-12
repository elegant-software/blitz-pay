# BlitzPay (QuickPay)

A Spring Boot payment gateway application supporting TrueLayer integration.

## Features

- Spring Boot 3.5.6 with Kotlin
- Spring Modulith for modular monolith architecture
- TrueLayer payment integration
- PostgreSQL database
- **Arconia-style Dev Services** for local development

## Dev Services Support

This application now supports Arconia-style development services, making it easy to run the application locally without manual database setup.

See [ARCONIA-DEV-SERVICES.md](ARCONIA-DEV-SERVICES.md) for detailed documentation.

### Quick Start with Dev Services

**Recommended:** Open `src/test/kotlin/com/elegant/software/quickpay/TestApplication.kt` in your IDE and run the `main()` function.

Or verify dev services work with:
```bash
./gradlew test --tests '*ArconiaDevServicesIntegrationTest'
```

No database setup required! The PostgreSQL container will start automatically.

## Building

```bash
./gradlew build
```

## Running (Traditional)

With Docker Compose:

```bash
docker-compose up -d
./gradlew bootRun
```

## Configuration

See `src/main/resources/application.yml` for configuration options.

## Testing

```bash
./gradlew test
```

## Technology Stack

- Kotlin 2.0.21
- Spring Boot 3.5.6
- Spring Modulith 1.4.3
- PostgreSQL 16.2
- TrueLayer Java SDK
- Testcontainers for testing
