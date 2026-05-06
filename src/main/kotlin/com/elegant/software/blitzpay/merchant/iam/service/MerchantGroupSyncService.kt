package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.api.BranchCreated
import com.elegant.software.blitzpay.merchant.api.BranchNameUpdated
import com.elegant.software.blitzpay.merchant.api.MerchantActivated
import com.elegant.software.blitzpay.merchant.api.MerchantNameUpdated
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupBody
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupClient
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupNames
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupRepresentation
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.util.UUID

@Service
@Profile("!contract-test")
class MerchantGroupSyncService(private val client: KeycloakGroupClient) {

    private val log = LoggerFactory.getLogger(MerchantGroupSyncService::class.java)

    fun syncMerchant(event: MerchantActivated): Mono<Void> =
        ensureRootGroup()
            .flatMap { rootId -> ensureMerchantGroup(rootId, event.merchantId, event.merchantName) }
            .doOnSuccess { log.info("keycloak merchant group synced merchantId={}", event.merchantId) }
            .doOnError { e -> log.error("keycloak merchant group sync failed merchantId={}", event.merchantId, e) }
            .then()

    fun syncBranch(event: BranchCreated): Mono<Void> =
        ensureRootGroup()
            .flatMap { rootId -> ensureMerchantGroup(rootId, event.merchantId, event.merchantName) }
            .flatMap { merchantGroupId -> ensureBranchGroup(merchantGroupId, event.branchId, event.branchName, event.merchantId) }
            .doOnSuccess { log.info("keycloak branch group synced branchId={} merchantId={}", event.branchId, event.merchantId) }
            .doOnError { e -> log.error("keycloak branch group sync failed branchId={} merchantId={}", event.branchId, event.merchantId, e) }
            .then()

    fun updateMerchantName(event: MerchantNameUpdated): Mono<Void> =
        client.searchGroups(KeycloakGroupNames.merchantGroup(event.merchantId), true)
            .flatMap { groups ->
                val group = groups.firstOrNull()
                if (group == null) {
                    log.warn("keycloak merchant group not found for update merchantId={}", event.merchantId)
                    Mono.empty()
                } else {
                    client.updateGroup(group.id, group.withAttribute(KeycloakGroupNames.ATTR_MERCHANT_NAME, event.newName))
                        .doOnSuccess { log.info("keycloak merchant name updated merchantId={}", event.merchantId) }
                }
            }
            .doOnError { e -> log.error("keycloak merchant name update failed merchantId={}", event.merchantId, e) }

    fun updateBranchName(event: BranchNameUpdated): Mono<Void> =
        findMerchantGroupId(event.merchantId)
            .flatMap { merchantGroupId ->
                client.getChildren(merchantGroupId, KeycloakGroupNames.branchGroup(event.branchId), true)
                    .flatMap { children ->
                        val group = children.firstOrNull()
                        if (group == null) {
                            log.warn("keycloak branch group not found for update branchId={}", event.branchId)
                            Mono.empty()
                        } else {
                            client.updateGroup(group.id, group.withAttribute(KeycloakGroupNames.ATTR_BRANCH_NAME, event.newName))
                                .doOnSuccess { log.info("keycloak branch name updated branchId={}", event.branchId) }
                        }
                    }
            }
            .doOnError { e -> log.error("keycloak branch name update failed branchId={}", event.branchId, e) }

    private fun ensureRootGroup(): Mono<String> =
        client.searchGroups(KeycloakGroupNames.ROOT, true)
            .flatMap { groups ->
                if (groups.isEmpty()) {
                    client.createGroup(KeycloakGroupBody(KeycloakGroupNames.ROOT))
                        .map { it.extractId() }
                        .onErrorResume(WebClientResponseException.Conflict::class.java) {
                            client.searchGroups(KeycloakGroupNames.ROOT, true).map { it.first().id }
                        }
                } else {
                    Mono.just(groups.first().id)
                }
            }

    private fun ensureMerchantGroup(rootId: String, merchantId: UUID, merchantName: String): Mono<String> =
        ensureChildGroup(rootId, KeycloakGroupNames.merchantGroup(merchantId), merchantAttributes(merchantId, merchantName))

    private fun ensureBranchGroup(merchantGroupId: String, branchId: UUID, branchName: String, merchantId: UUID): Mono<String> =
        ensureChildGroup(merchantGroupId, KeycloakGroupNames.branchGroup(branchId), branchAttributes(branchId, branchName, merchantId))

    private fun ensureChildGroup(parentId: String, groupName: String, attributes: Map<String, List<String>>): Mono<String> =
        client.getChildren(parentId, groupName, true)
            .flatMap { children ->
                if (children.isEmpty()) {
                    client.createChildGroup(parentId, KeycloakGroupBody(groupName, attributes))
                        .map { it.extractId() }
                        .onErrorResume(WebClientResponseException.Conflict::class.java) {
                            client.getChildren(parentId, groupName, true).map { it.first().id }
                        }
                } else {
                    val existing = children.first()
                    client.updateGroup(existing.id, KeycloakGroupBody(existing.name, attributes))
                        .thenReturn(existing.id)
                }
            }

    private fun findMerchantGroupId(merchantId: UUID): Mono<String> =
        ensureRootGroup()
            .flatMap { rootId -> client.getChildren(rootId, KeycloakGroupNames.merchantGroup(merchantId), true) }
            .map { children -> children.firstOrNull()?.id ?: throw NoSuchElementException("merchant group not found: $merchantId") }

    private fun merchantAttributes(merchantId: UUID, merchantName: String) = mapOf(
        KeycloakGroupNames.ATTR_MERCHANT_ID to listOf(merchantId.toString()),
        KeycloakGroupNames.ATTR_MERCHANT_NAME to listOf(merchantName),
    )

    private fun branchAttributes(branchId: UUID, branchName: String, merchantId: UUID) = mapOf(
        KeycloakGroupNames.ATTR_BRANCH_ID to listOf(branchId.toString()),
        KeycloakGroupNames.ATTR_BRANCH_NAME to listOf(branchName),
        KeycloakGroupNames.ATTR_MERCHANT_ID to listOf(merchantId.toString()),
    )

    private fun org.springframework.http.ResponseEntity<Void>.extractId(): String =
        headers.location?.path?.substringAfterLast("/")
            ?: throw IllegalStateException("No Location header in Keycloak group creation response")

    private fun KeycloakGroupRepresentation.withAttribute(key: String, value: String): KeycloakGroupBody =
        KeycloakGroupBody(name, (attributes ?: emptyMap()) + mapOf(key to listOf(value)))
}
