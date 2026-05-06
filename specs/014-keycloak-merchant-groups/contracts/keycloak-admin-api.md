# Contract: Keycloak Admin REST API

**Consumer**: `merchant.iam` (`KeycloakGroupClient` — declarative HTTP Interface)  
**Provider**: Keycloak Admin REST API at `${KEYCLOAK_IAM_SERVER_URL}/admin/realms/${KEYCLOAK_IAM_REALM}/`

---

## HTTP Interface Definition (Spring Boot 4)

The client is declared as a Kotlin interface using Spring Framework 7 `@HttpExchange` annotations.  
Spring Boot 4 auto-proxies it via `@ImportHttpServices` — no implementation class is needed.

```kotlin
@HttpExchange("/groups")
interface KeycloakGroupClient {

    /** Search top-level groups or children by name (exact match). */
    @GetExchange
    fun searchGroups(
        @RequestParam search: String,
        @RequestParam exact: Boolean = true,
    ): Mono<List<KeycloakGroupRepresentation>>

    /** Create a top-level group. Returns 201 with Location header containing the new group ID. */
    @PostExchange
    fun createGroup(
        @RequestBody body: KeycloakGroupBody,
    ): Mono<ResponseEntity<Void>>

    /** List children of a group. */
    @GetExchange("/{parentId}/children")
    fun getChildren(
        @PathVariable parentId: String,
        @RequestParam search: String,
        @RequestParam exact: Boolean = true,
    ): Mono<List<KeycloakGroupRepresentation>>

    /** Create a child group under parentId. Returns 201 with Location header. */
    @PostExchange("/{parentId}/children")
    fun createChildGroup(
        @PathVariable parentId: String,
        @RequestBody body: KeycloakGroupBody,
    ): Mono<ResponseEntity<Void>>

    /** Full update of a group (name + attributes). */
    @PutExchange("/{groupId}")
    fun updateGroup(
        @PathVariable groupId: String,
        @RequestBody body: KeycloakGroupBody,
    ): Mono<Void>
}
```

---

## Registration (`@ImportHttpServices`)

```kotlin
@Configuration
@ImportHttpServices(
    group = "keycloak-admin",
    types = [KeycloakGroupClient::class],
    clientType = HttpServiceGroup.ClientType.WEB_CLIENT,   // reactive
)
class KeycloakWebClientConfig {

    @Bean
    fun keycloakGroupConfigurer(
        properties: KeycloakProperties,
        tokenProvider: KeycloakTokenProvider,
    ): HttpServiceGroupConfigurer = HttpServiceGroupConfigurer { groups ->
        groups.filterByName("keycloak-admin")
              .forEachClient { _, builder ->
                  // Base URL already includes realm path
                  builder.baseUrl("${properties.serverUrl}/admin/realms/${properties.realm}")
                  builder.filter(KeycloakBearerTokenFilter(tokenProvider))
              }
    }
}
```

Spring Boot 4's auto-configuration picks up the `HttpServiceGroupConfigurer` bean automatically — no manual `HttpServiceProxyFactory` wiring needed.

---

## Request / Response Models

```kotlin
data class KeycloakGroupBody(
    val name: String,
    val attributes: Map<String, List<String>> = emptyMap(),
)

data class KeycloakGroupRepresentation(
    val id: String,
    val name: String,
    val path: String,
    val attributes: Map<String, List<String>> = emptyMap(),
)
```

---

## Authentication

`KeycloakBearerTokenFilter` is an `ExchangeFilterFunction` that:
1. Obtains a token via client credentials (`/realms/master/protocol/openid-connect/token`)
2. Injects `Authorization: Bearer <token>` on every outgoing request
3. On 401 response, clears cache and retries the token fetch once

```kotlin
class KeycloakBearerTokenFilter(
    private val tokenProvider: KeycloakTokenProvider,
) : ExchangeFilterFunction {
    override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> =
        tokenProvider.getToken()
            .flatMap { token ->
                next.exchange(
                    ClientRequest.from(request)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .build()
                )
            }
            .flatMap { response ->
                if (response.statusCode() == HttpStatus.UNAUTHORIZED) {
                    tokenProvider.invalidate()
                    tokenProvider.getToken().flatMap { token ->
                        next.exchange(
                            ClientRequest.from(request)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                                .build()
                        )
                    }
                } else Mono.just(response)
            }
}
```

`KeycloakTokenProvider` calls `POST /realms/master/protocol/openid-connect/token` via a separate `WebClient` (not the group client) and caches the result until expiry.

---

## Keycloak Admin REST API Endpoints Used

| Operation | Method | Path (relative to base URL) |
|-----------|--------|-----------------------------|
| Search top-level groups | GET | `/groups?search=merchants&exact=true` |
| Create top-level group | POST | `/groups` |
| List child groups | GET | `/groups/{parentId}/children?search=…&exact=true` |
| Create child group | POST | `/groups/{parentId}/children` |
| Update group attributes | PUT | `/groups/{groupId}` |

---

## Group Naming Convention

| Entity | Keycloak group name | Parent |
|--------|---------------------|--------|
| Root | `merchants` | top-level |
| Merchant | `merchant_<MerchantApplication.id>` | `/merchants` |
| Branch | `branch_<MerchantBranch.id>` | `/merchants/merchant_<merchantId>` |

---

## Attributes Schema

### Merchant group

| Attribute key | Value | Source |
|---------------|-------|--------|
| `merchant_id` | UUID string | `MerchantApplication.id` |
| `merchant_name` | String | `BusinessProfile.legalBusinessName` |

### Branch group

| Attribute key | Value | Source |
|---------------|-------|--------|
| `branch_id` | UUID string | `MerchantBranch.id` |
| `branch_name` | String | `MerchantBranch.name` |
| `merchant_id` | UUID string | `MerchantBranch.merchantApplicationId` |

---

## Error Handling

| Status | Meaning | `KeycloakGroupClient` behaviour |
|--------|---------|--------------------------------|
| 201 Created | Group created | Extract ID from `Location` header |
| 204 No Content | Group updated | Success |
| 401 Unauthorized | Token expired | `KeycloakBearerTokenFilter` refreshes and retries |
| 404 Not Found | Group/parent absent | `MerchantGroupSyncService` ensures parent first |
| 409 Conflict | Duplicate name | `MerchantGroupSyncService` catches and fetches existing |
| 5xx Server error | Keycloak unavailable | Propagates as error; Modulith retries on restart |
