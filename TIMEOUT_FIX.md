# Fix for Timeout Exception in SSE Stream

## Problem
The application was throwing a `TimeoutException` after 300 seconds (5 minutes) when SSE clients remained connected but no payment updates were emitted:

```
java.util.concurrent.TimeoutException: Did not observe any item or terminal signal within 300000ms in 'sinkManyEmitterProcessor' (and no fallback has been configured)
```

## Root Cause
In `QrPaymentSseController.kt`, the SSE stream was using `.timeout(Duration.ofMinutes(5))` without a fallback publisher. This caused the stream to throw an exception when the timeout occurred instead of completing gracefully.

Additionally, the cleanup logic was only handling `ON_COMPLETE` signals, which meant the sink might not be properly cleaned up when the stream was cancelled by the client or terminated for other reasons.

## Solution
Made two key changes:

1. **Added graceful timeout fallback**: Changed from:
```kotlin
.timeout(Duration.ofMinutes(5))  // throws TimeoutException
```

To:
```kotlin
.timeout(Duration.ofMinutes(5), Flux.empty())  // completes gracefully
```

2. **Improved cleanup logic**: Changed from:
```kotlin
.doFinally { signal -> if (signal == SignalType.ON_COMPLETE) bus.complete(paymentRequestId) }
```

To:
```kotlin
.doFinally { signal -> 
    logger.debug { "SSE stream ended with signal $signal for paymentRequestId: $paymentRequestId" }
    bus.complete(paymentRequestId) 
}
```

By providing `Flux.empty()` as a fallback, the stream will now complete gracefully when the timeout occurs instead of throwing an exception. The improved cleanup logic ensures the sink is cleaned up for all termination types (complete, error, cancel), preventing resource leaks.

## Behavior
- If payment updates are emitted within 5 minutes, they are sent to the client normally
- If no updates are emitted within 5 minutes, the stream completes gracefully via the fallback
- The sink is properly cleaned up regardless of how the stream terminates (completion, timeout, cancellation, error)
- Debug logging added to help trace stream lifecycle in production
- No `TimeoutException` is thrown
