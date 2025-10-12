# Fix for Timeout Exception in SSE Stream

## Problem
The application was throwing a `TimeoutException` after 300 seconds (5 minutes) when SSE clients remained connected but no payment updates were emitted:

```
java.util.concurrent.TimeoutException: Did not observe any item or terminal signal within 300000ms in 'sinkManyEmitterProcessor' (and no fallback has been configured)
```

## Root Cause
In `QrPaymentSseController.kt`, the SSE stream was using `.timeout(Duration.ofMinutes(5))` without a fallback publisher. This caused the stream to throw an exception when the timeout occurred instead of completing gracefully.

## Solution
Changed the timeout configuration from:
```kotlin
.timeout(Duration.ofMinutes(5))  // throws TimeoutException
```

To:
```kotlin
.timeout(Duration.ofMinutes(5), Flux.empty())  // completes gracefully
```

By providing `Flux.empty()` as a fallback, the stream will now complete gracefully when the timeout occurs instead of throwing an exception. This allows idle SSE connections to be cleaned up properly after 5 minutes without generating error logs.

## Behavior
- If payment updates are emitted within 5 minutes, they are sent to the client normally
- If no updates are emitted within 5 minutes, the stream completes gracefully
- The `doFinally` handler still cleans up the sink when the stream completes
- No `TimeoutException` is thrown
