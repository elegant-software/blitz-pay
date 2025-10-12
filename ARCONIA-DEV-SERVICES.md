# Arconia-Style Dev Services for BlitzPay

This project implements Arconia-style development services based on the reference: https://www.thomasvitale.com/arconia-dev-services-spring-boot/

## What is Arconia?

Arconia is a pattern/library for Spring Boot applications that provides pre-configured development services using Testcontainers. It leverages Spring Boot 3's `@ServiceConnection` annotation to automatically configure backing services like PostgreSQL, Redis, Kafka, etc. during development and testing.

## Implementation

Instead of adding the Arconia library directly (which may have dependency conflicts or availability issues), we've implemented the Arconia pattern using Spring Boot 3's native capabilities:

1. **TestApplication.kt** - A test application entry point that configures dev services
2. **DevServicesConfiguration** - Configuration class that defines Testcontainers with `@ServiceConnection`

## Usage

### Running the Application with Dev Services

There are several ways to run the application with dev services:

#### Option 1: Run from your IDE (Recommended)

Open `src/test/kotlin/com/elegant/software/quickpay/TestApplication.kt` and run the `main()` function directly from your IDE. This is the easiest and most reliable method.

#### Option 2: Verify with the integration test

```bash
./gradlew test --tests '*ArconiaDevServicesIntegrationTest'
```

This runs the integration test that starts the dev services and verifies they work correctly.

#### Option 3: Use the provided script

```bash
./run-with-dev-services.sh
```

This script sets up environment variables and provides guidance on running the application.

**Note:** Since `TestApplication.kt` is in the test source set, running it via Gradle's `bootRun` requires special configuration. The IDE approach is simpler and recommended for development.

### Benefits

- **No Manual Container Management**: The PostgreSQL container is automatically started and configured
- **Faster Development**: Container reuse across runs speeds up the development cycle
- **Consistent Environment**: All developers use the same PostgreSQL version (16.2)
- **Zero Configuration**: Spring Boot automatically configures the DataSource from the container

### How It Works

1. The `TestApplication.kt` uses Spring Boot's `fromApplication()` and `with()` pattern
2. `DevServicesConfiguration` defines a `PostgreSQLContainer` bean with `@ServiceConnection`
3. Spring Boot detects the `@ServiceConnection` and automatically configures:
   - `spring.datasource.url`
   - `spring.datasource.username`
   - `spring.datasource.password`
4. Container reuse is enabled for faster startup on subsequent runs

## Configuration

The PostgreSQL container is configured with:
- Image: `postgres:16.2`
- Reuse: Enabled for faster startup
- Auto-configuration: Handled by Spring Boot's `@ServiceConnection`

No additional configuration is required in `application.yml` when running with dev services.

### Container Reuse

Container reuse (`withReuse(true)`) requires enabling the Testcontainers reuse feature globally. Create a file `~/.testcontainers.properties` with:

```properties
testcontainers.reuse.enable=true
```

This allows containers to be reused across multiple runs, significantly speeding up startup times. Without this property, containers will be recreated on each run.

## Adding More Dev Services

The Arconia pattern makes it easy to add more development services. Here's how you can extend the `DevServicesConfiguration`:

### Example: Adding Redis Dev Service

```kotlin
@Bean
@ServiceConnection
fun redisContainer(): GenericContainer<*> {
    return GenericContainer(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379)
        .withReuse(true)
}
```

### Example: Adding Kafka Dev Service

```kotlin
@Bean
@ServiceConnection
fun kafkaContainer(): KafkaContainer {
    return KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
        .withReuse(true)  // Requires testcontainers.reuse.enable=true in ~/.testcontainers.properties
}
```

Simply add the appropriate Testcontainers dependency and create a bean with `@ServiceConnection`. Spring Boot will handle the rest!

**Note:** Container reuse only works when the container configuration remains the same. Different images, ports, or environment variables will create a new container.

## Testing

Tests can also leverage the dev services configuration. The `DevServicesTest.kt` demonstrates how to test that the dev services are properly configured.

## References

- [Arconia Dev Services - Thomas Vitale](https://www.thomasvitale.com/arconia-dev-services-spring-boot/)
- [Spring Boot Testcontainers Support](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing.testcontainers)
- [Testcontainers](https://www.testcontainers.org/)
