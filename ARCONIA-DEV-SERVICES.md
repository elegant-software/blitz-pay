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

#### Option 1: Using the provided script (Recommended)

```bash
./run-with-dev-services.sh
```

This script sets up dummy environment variables and runs the application with dev services enabled.

#### Option 2: Using Gradle directly

```bash
./gradlew bootRun -PmainClass=com.elegant.software.quickpay.TestApplicationKt
```

Make sure to set the required TrueLayer environment variables first.

#### Option 3: Run from your IDE

Open `src/test/kotlin/com/elegant/software/quickpay/TestApplication.kt` and run the `main` function directly.

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
        .withReuse(true)
}
```

Simply add the appropriate Testcontainers dependency and create a bean with `@ServiceConnection`. Spring Boot will handle the rest!

## Testing

Tests can also leverage the dev services configuration. The `DevServicesTest.kt` demonstrates how to test that the dev services are properly configured.

## References

- [Arconia Dev Services - Thomas Vitale](https://www.thomasvitale.com/arconia-dev-services-spring-boot/)
- [Spring Boot Testcontainers Support](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing.testcontainers)
- [Testcontainers](https://www.testcontainers.org/)
