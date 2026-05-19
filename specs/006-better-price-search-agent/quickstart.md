# Quickstart: Real Market Search Price Savings Agent
# Quickstart: Product Price Savings Agent

## 1. Configure the app

Use Gradle only.

Minimum local configuration:

```yaml
invoice-agent:
  enabled: true

market-search:
  enabled: true
  provider: fixture
  koog:
    enabled: true
  a2a:
    port: 8099
    path: /a2a/market-search

ai:
  koog:
    # Example only if the chosen KOOG integration path binds Spring properties.
    # Keep secrets in env vars rather than hardcoding.
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY:}
```

If using Browserbase-backed DeepSearch:

```yaml
market-search:
  enabled: true
  provider: deepsearch
  deepsearch:
    apiKey: ${DEEPSEARCH_API_KEY:}
    baseUrl: ${DEEPSEARCH_BASE_URL:https://api.browserbase.com}
    searchPath: /v1/search
    fetchPath: /v1/fetch
```

## 2. Run the application

```bash
./gradlew bootRun
```

## 3. Call the A2A API

```bash
curl -X POST http://localhost:8099/a2a/market-search \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "id": "req-1",
    "method": "message/send",
    "params": {
      "message": {
        "messageId": "msg-1",
        "role": "user",
        "parts": [
          {
            "kind": "text",
            "text": "{\"inputPrice\":38,\"currency\":\"EUR\",\"productTitle\":\"Frontline Spot On Dog S Solution\",\"brandName\":\"FRONTLINE\",\"modelName\":\"\",\"sku\":\"\",\"additionalAttributes\":{\"locale\":\"de-DE\"}}"
          }
        ]
      }
    }
  }'
```

Expected behavior:
- A KOOG agent is created for the request.
- The agent invokes market-search and comparison tools.
- The response returns a JSON-RPC task/result envelope from the A2A transport.

## 4. Check the KOOG A2A listener

```bash
curl http://localhost:8099/a2a/market-search
```

Expected behavior:
- The configured A2A port is actually listening.
- The endpoint is served by KOOG's A2A transport rather than a handwritten Spring controller.

## 5. Validate fixture mode quickly

```bash
./gradlew test --tests 'com.elegant.software.blitzpay.marketsearch.*'
./gradlew test --tests 'com.elegant.software.blitzpay.pricecomparison.*'
```

## 6. Validate the contract

```bash
./gradlew contractTest
```

## 7. KOOG-focused acceptance checks

- Confirm that one incoming request creates one KOOG run.
- Confirm that monitoring reports agent startup, search, extraction, comparison, and completion/failure stages.
- Confirm that `market-search.provider` can switch between `fixture`, `brave`, and `deepsearch` without changing the API schema.
- Confirm that the result remains stateless and nothing is persisted.
- Confirm that the configured A2A port/path exposes a real KOOG A2A listener and not a Spring MVC/WebFlux route.
## Purpose

Validate that the agent performs live product discovery, extracts comparable offers from retailer pages, returns structured savings data, and exposes monitoring in the same API response across fixture, Brave, and DeepSearch providers.

## Preconditions

- `./gradlew build` succeeds
- Agent functionality is enabled
- Outbound HTTP access is available in the runtime environment for live providers
- Any live provider credentials required by the chosen runtime configuration are available

## Example Configuration

### Fixture Mode

```yaml
invoice-agent:
  enabled: true

market-search:
  enabled: true
  provider: fixture
```

### Brave Mode

```yaml
invoice-agent:
  enabled: true

market-search:
  enabled: true
  provider: brave
  maxHits: 5
  maxPagesToFetch: 3
  requestTimeout: PT3S
  brave:
    apiKey: ${BRAVE_SEARCH_API_KEY}
```

### DeepSearch Mode

```yaml
invoice-agent:
  enabled: true

market-search:
  enabled: true
  provider: deepsearch
  maxHits: 5
  maxPagesToFetch: 3
  requestTimeout: PT3S
  deepsearch:
    baseUrl: ${DEEPSEARCH_BASE_URL}
    apiKey: ${DEEPSEARCH_API_KEY}
```

Fixture mode is the deterministic local verification path and should remain the default for automated tests.

## Local Verification Steps

1. Run `./gradlew test contractTest build`.
2. Start the application with one provider configured.
3. Submit a valid JSON-RPC request to `POST /a2a/market-search`.
4. Confirm the response is structured JSON rather than narrative text.
5. Confirm the response contains a `monitoring` object.
6. Confirm monitoring can reflect live stages such as `search_discovery` and `offer_extraction`.
7. Confirm a better-price scenario returns a `bestOffer.productUrl` and positive savings.
8. Confirm a no-savings scenario returns zero savings without a misleading cheaper recommendation.
9. Confirm a provider-failure or timeout scenario returns machine-readable `monitoring.failure` or `monitoring.bottleneck`.
10. Confirm Swagger/OpenAPI documents the same response schema regardless of provider choice.

## Example Request

```json
{
  "inputPrice": 329.99,
  "currency": "USD",
  "productTitle": "Sony WH-1000XM5 Wireless Noise Canceling Headphones",
  "brandName": "Sony",
  "modelName": "WH-1000XM5",
  "sku": "SONY-WH1000XM5-BLK"
}
```

## Expected Verification Signals

- `status` is one of:
  - `better_price_found`
  - `no_better_price_found`
  - `comparison_unavailable`
- `monitoring.stage` and `monitoring.progress` are always present
- `bestOffer.productUrl` points to a retailer page when a better price is found
- `monitoring.warnings`, `monitoring.bottleneck`, or `monitoring.failure` appear when search or extraction degrades
- provider selection changes execution behavior, not the response schema

## Test Coverage Expectations

- Unit tests cover search-query assembly, provider normalization, structured-data extraction, match qualification, and savings arithmetic
- Adapter tests stub fixture, Brave, and DeepSearch discovery payloads plus retailer-page HTTP responses
- Web/controller tests verify the public response schema and monitoring fields
- Contract tests verify HTTP and A2A parity with provider-neutral live-search monitoring
