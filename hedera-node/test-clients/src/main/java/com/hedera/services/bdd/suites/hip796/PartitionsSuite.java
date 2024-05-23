/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.bdd.suites.hip796;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenPause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnpause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withHeadlongAddressesFor;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.CREATE_PARTITION_FUNCTION;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.DELETE_PARTITION_FUNCTION;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.UPDATE_PARTITION_FUNCTION;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.addPartition;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.fungibleTokenWithFeatures;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.nonFungibleTokenWithFeatures;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.managementContractOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.partition;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.partitionMoveKeyOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.treasuryOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.ADMIN_CONTROL;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.FREEZING;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.INTER_PARTITION_MANAGEMENT;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.KYC_MANAGEMENT;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.PARTITIONING;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.PAUSING;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.SUPPLY_MANAGEMENT;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.WIPING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

/**
 * A suite for user stories Partitions-1 through Partitions-18 from HIP-796.
 */
public class PartitionsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(PartitionsSuite.class);

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(
                createNewPartitionDefinitions(),
                updatePartitionDefinitionsMemo(),
                deletePartitionDefinitions(),
                transferBetweenPartitions(),
                transferNFTsWithinPartitions(),
                pauseTokenTransfersIncludingPartitions(),
                freezeTokenTransfersForAccountIncludingPartitions(),
                requireKycForTokenTransfersIncludingPartitions(),
                pauseTransfersForSpecificPartition(),
                freezeTransfersForSpecificPartitionOnAccount(),
                requireKycForPartitionTransfers(),
                createFixedSupplyTokenWithPartitionKey(),
                notHonorDeletionOfTokenWithExistingPartitions(),
                mintToSpecificPartitionOfTreasury(),
                burnFromSpecificPartitionOfTreasury(),
                wipeFromSpecificPartitionInUserAccount(),
                smartContractAdministersPartitions(),
                freezeOrPauseAtTokenLevelOverridesPartition());
    }

    /**
     * <b>Partitions-1:</b>
     * <p>As a `partition-administrator`, I want to create new `partition-definition`s
     * for my `token-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> createNewPartitionDefinitions() {
        return defaultHapiSpec("CreateNewPartitionDefinitions")
                .given(fungibleTokenWithFeatures(PARTITIONING))
                .when()
                .then(
                        // These default to using the TOKEN_UNDER_TEST
                        addPartition(RED_PARTITION), addPartition(BLUE_PARTITION));
    }

    /**
     * <b>Partitions-2</b>
     * <p>As a `partition-administrator`, I want to update existing `partition-definition`s
     * for my `token-definition`, such as the memo, of a `partition-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> updatePartitionDefinitionsMemo() {
        return defaultHapiSpec("UpdatePartitionDefinitionsMemo")
                .given(fungibleTokenWithFeatures(PARTITIONING, ADMIN_CONTROL)
                        .withPartition(RED_PARTITION, p -> p.memo("TBD")))
                .when(
                        // The partition should inherit the admin key and allow this
                        tokenUpdate(partition(RED_PARTITION)).entityMemo("Ah much better"))
                .then(getTokenInfo(partition(RED_PARTITION)).hasEntityMemo("Ah much better"));
    }

    /**
     * <b>Partitions-3</b>
     * <p>As a `partition-administrator`, I want to delete existing `partition-definition`s of my `token-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> deletePartitionDefinitions() {
        return defaultHapiSpec("DeletePartitionDefinitions")
                .given(fungibleTokenWithFeatures(PARTITIONING, ADMIN_CONTROL).withPartition(RED_PARTITION))
                .when(
                        // The partition should inherit the admin key and allow this
                        tokenDelete(partition(RED_PARTITION)))
                .then(getTokenInfo(partition(RED_PARTITION)).isDeleted());
    }

    /**
     * <b>Partitions-4</b>
     * <p>As the holder of a `partition-move-key`, I want to transfer independent
     * fungible token balances within partitions of an account.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> transferBetweenPartitions() {
        return defaultHapiSpec("TransferBetweenPartitions")
                .given(fungibleTokenWithFeatures(PARTITIONING, INTER_PARTITION_MANAGEMENT)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION)
                        .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION)
                                .andPartition(BLUE_PARTITION, pr -> pr.balance(0))))
                .when(
                        // Even though Alice doesn't sign, this is possible with the partition move key
                        cryptoTransfer(moving(FUNGIBLE_INITIAL_BALANCE, partition(RED_PARTITION))
                                        .betweenWithPartitionChange(ALICE, ALICE, partition(BLUE_PARTITION)))
                                .signedBy(DEFAULT_PAYER, partitionMoveKeyOf(TOKEN_UNDER_TEST)))
                .then(getAccountBalance(ALICE)
                        .hasTokenBalance(partition(RED_PARTITION), 0)
                        .hasTokenBalance(partition(BLUE_PARTITION), FUNGIBLE_INITIAL_BALANCE));
    }

    /**
     * <b>Partitions-5</b>
     * <p>As the holder of a `partition-move-key`, I want to transfer independent NFT serials within partitions of an account.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> transferNFTsWithinPartitions() {
        return defaultHapiSpec("TransferNFTsWithinPartitions")
                .given(nonFungibleTokenWithFeatures(PARTITIONING, INTER_PARTITION_MANAGEMENT)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION)
                        .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION, pr -> pr.ownedSerialNos(1L))
                                .andPartition(BLUE_PARTITION)))
                .when(
                        // Even though Alice doesn't sign, this is possible with the partition move key
                        cryptoTransfer(movingUnique(partition(RED_PARTITION), 1L)
                                        .betweenWithPartitionChange(ALICE, ALICE, partition(BLUE_PARTITION)))
                                .signedBy(DEFAULT_PAYER, partitionMoveKeyOf(TOKEN_UNDER_TEST)))
                .then(
                        getAccountBalance(ALICE)
                                .hasTokenBalance(partition(RED_PARTITION), 0)
                                .hasTokenBalance(partition(BLUE_PARTITION), FUNGIBLE_INITIAL_BALANCE),
                        // The total supplies of the token types reflect the serial number re-assignments
                        getTokenInfo(TOKEN_UNDER_TEST).hasTotalSupply(NON_FUNGIBLE_INITIAL_SUPPLY - 1),
                        getTokenInfo(partition(RED_PARTITION)).hasTotalSupply(0),
                        getTokenInfo(partition(BLUE_PARTITION)).hasTotalSupply(1));
    }

    /**
     * <b>Partitions-6</b>
     * <p>As a `token-administrator`, I want to `pause` all token transfers for my `token-definition`,
     * including for all partitions, by pausing the `token-definition` itself.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> pauseTokenTransfersIncludingPartitions() {
        return defaultHapiSpec("PauseTokenTransfersIncludingPartitions")
                .given(nonFungibleTokenWithFeatures(PARTITIONING, PAUSING)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION)
                        // Given Alice a serial number of both the parent and each partition
                        .withRelation(ALICE, r -> r.ownedSerialNos(1L)
                                .alsoForPartition(RED_PARTITION, pr -> pr.ownedSerialNos(2L))
                                .andPartition(BLUE_PARTITION, pr -> pr.ownedSerialNos(3L))))
                .when(tokenPause(TOKEN_UNDER_TEST))
                .then(
                        // Both RED and BLUE partitions are paused, as well as the parent
                        cryptoTransfer(movingUnique(TOKEN_UNDER_TEST, 1L).between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                .hasKnownStatus(TOKEN_IS_PAUSED),
                        cryptoTransfer(movingUnique(partition(RED_PARTITION), 2L)
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                .hasKnownStatus(TOKEN_IS_PAUSED),
                        cryptoTransfer(movingUnique(partition(BLUE_PARTITION), 3L)
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                .hasKnownStatus(TOKEN_IS_PAUSED));
    }

    /**
     * <b>Partitions-7</b>
     * <p>As a `token-administrator`, I want to `freeze` all token transfers for my `token-definition`
     * on a particular account, including for all partitions of the `token-definition`, by freezing the
     * `token-definition` itself.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> freezeTokenTransfersForAccountIncludingPartitions() {
        return defaultHapiSpec("FreezeTokenTransfersForAccountIncludingPartitions")
                .given(nonFungibleTokenWithFeatures(PARTITIONING, FREEZING)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION)
                        // Given Alice a serial number of both the parent and each partition
                        .withRelation(ALICE, r -> r.ownedSerialNos(1L)
                                .alsoForPartition(RED_PARTITION, pr -> pr.ownedSerialNos(2L))
                                .andPartition(BLUE_PARTITION, pr -> pr.ownedSerialNos(3L))))
                .when(tokenFreeze(TOKEN_UNDER_TEST, ALICE))
                .then(
                        // Both RED and BLUE partitions are frozen, as well as the parent
                        cryptoTransfer(movingUnique(TOKEN_UNDER_TEST, 1L).between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
                        cryptoTransfer(movingUnique(partition(RED_PARTITION), 2L)
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
                        cryptoTransfer(movingUnique(partition(BLUE_PARTITION), 3L)
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN));
    }

    /**
     * <b>Partitions-8</b>
     * <p>As a `token-administrator`, I want to require `kyc` to be set on the account for the association with my
     * `token-definition` to enable transfers of any tokens in partitions of the `token-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> requireKycForTokenTransfersIncludingPartitions() {
        return defaultHapiSpec("RequireKycForTokenTransfersIncludingPartitions")
                .given(nonFungibleTokenWithFeatures(PARTITIONING, KYC_MANAGEMENT)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION)
                        // Auto-grant KYC to ALICE here
                        .withRelation(ALICE, r -> r.ownedSerialNos(1L)
                                .alsoForPartition(RED_PARTITION, pr -> pr.ownedSerialNos(2L))
                                .andPartition(BLUE_PARTITION, pr -> pr.ownedSerialNos(3L))))
                .when(revokeTokenKyc(TOKEN_UNDER_TEST, ALICE))
                .then(
                        // All transfers, including the RED and BLUE partitions, fail without KYC
                        cryptoTransfer(movingUnique(TOKEN_UNDER_TEST, 1L).between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                .hasKnownStatus(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN),
                        cryptoTransfer(movingUnique(partition(RED_PARTITION), 2L)
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                .hasKnownStatus(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN),
                        cryptoTransfer(movingUnique(partition(BLUE_PARTITION), 3L)
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                .hasKnownStatus(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN));
    }

    /**
     * <b>Partitions-9</b>
     * <p>As a `token-administrator`, I want to `pause` all token transfers for a specific `partition-definition` of
     * my `token-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> pauseTransfersForSpecificPartition() {
        return defaultHapiSpec("PauseTransfersForSpecificPartition")
                .given(nonFungibleTokenWithFeatures(PARTITIONING, PAUSING)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION)
                        // Given Alice a serial number of both the parent and each partition
                        .withRelation(ALICE, r -> r.ownedSerialNos(1L)
                                .alsoForPartition(RED_PARTITION, pr -> pr.ownedSerialNos(2L))
                                .andPartition(BLUE_PARTITION, pr -> pr.ownedSerialNos(3L))))
                .when(
                        // Pause the RED partition
                        tokenPause(partition(RED_PARTITION)))
                .then(
                        // Parent token is not paused
                        cryptoTransfer(movingUnique(TOKEN_UNDER_TEST, 1L).between(ALICE, treasuryOf(TOKEN_UNDER_TEST))),
                        // Only the RED partition is paused
                        cryptoTransfer(movingUnique(partition(RED_PARTITION), 2L)
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                .hasKnownStatus(TOKEN_IS_PAUSED),
                        // The BLUE partition is not paused
                        cryptoTransfer(movingUnique(partition(BLUE_PARTITION), 3L)
                                .between(ALICE, treasuryOf(TOKEN_UNDER_TEST))));
    }

    /**
     * <b>Partitions-10</b>
     * <p>As a `token-administrator`, I want to `freeze` all token transfers for a specific `partition-definition`
     * of my `token-definition` on a particular account.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> freezeTransfersForSpecificPartitionOnAccount() {
        return defaultHapiSpec("FreezeTransfersForSpecificPartitionOnAccount")
                .given(nonFungibleTokenWithFeatures(PARTITIONING, FREEZING)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION)
                        // Given Alice a serial number of both the parent and each partition
                        .withRelation(ALICE, r -> r.ownedSerialNos(1L)
                                .alsoForPartition(RED_PARTITION, pr -> pr.ownedSerialNos(2L))
                                .andPartition(BLUE_PARTITION, pr -> pr.ownedSerialNos(3L))))
                .when(
                        // Freeze Alice for the RED partition
                        tokenFreeze(partition(RED_PARTITION), ALICE))
                .then(
                        // Alice not frozen out of parent token
                        cryptoTransfer(movingUnique(TOKEN_UNDER_TEST, 1L).between(ALICE, treasuryOf(TOKEN_UNDER_TEST))),
                        // Alice frozen out of the RED partition
                        cryptoTransfer(movingUnique(partition(RED_PARTITION), 2L)
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
                        // Alice not frozen out of the blue partition
                        cryptoTransfer(movingUnique(partition(BLUE_PARTITION), 3L)
                                .between(ALICE, treasuryOf(TOKEN_UNDER_TEST))));
    }

    /**
     * <b>Partitions-11</b>
     * <p>As a `partition-administrator`, I want to require a kyc flag to be set on the partition of an account to
     * enable transfers of tokens in that partition.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> requireKycForPartitionTransfers() {
        return defaultHapiSpec("RequireKycForPartitionTransfers")
                .given(
                        cryptoCreate(ALICE),
                        nonFungibleTokenWithFeatures(PARTITIONING, KYC_MANAGEMENT)
                                .withPartition(RED_PARTITION, p -> p.assignedSerialNos(1L)))
                .when(
                        // Explicit associate Alice without KYC
                        tokenAssociate(ALICE, partition(RED_PARTITION)))
                .then(cryptoTransfer(
                                movingUnique(partition(RED_PARTITION), 1L).between(treasuryOf(TOKEN_UNDER_TEST), ALICE))
                        .hasKnownStatus(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN));
    }

    /**
     * <b>Partitions-12</b>
     * <p>As a `token-administrator`, I want to be able to create a new `token-definition`
     * with a fixed supply and a `partition-key`.
     *
     * <p><b>TODO</b> - confirm this is how total supply should be reported by the token info query
     * for a partitioned token.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> createFixedSupplyTokenWithPartitionKey() {
        return defaultHapiSpec("CreateFixedSupplyTokenWithPartitionKey")
                .given(
                        // Without a supply key, this will be a fixed supply token
                        fungibleTokenWithFeatures(PARTITIONING)
                                .initialSupply(6L)
                                .withPartition(RED_PARTITION, p -> p.initialSupply(1L))
                                .withPartition(BLUE_PARTITION, p -> p.initialSupply(2L))
                                .withPartition(GREEN_PARTITION, p -> p.initialSupply(3L)))
                .when()
                .then(
                        // Even though the entire supply is contained in the partition definitions,
                        // the parent token still has the initial supply (while each partition has
                        // just the supply allocated to it?)
                        getTokenInfo(TOKEN_UNDER_TEST).hasTotalSupply(6L),
                        getTokenInfo(partition(RED_PARTITION)).hasTotalSupply(1L),
                        getTokenInfo(partition(BLUE_PARTITION)).hasTotalSupply(2L),
                        getTokenInfo(partition(GREEN_PARTITION)).hasTotalSupply(3L));
    }

    /**
     * <b>Partitions-13</b>
     * <p>As a node operator, I do not want to honor deletion of a `token-definition`
     * that has any `partition-definition` that is not also already deleted.
     *
     * <p><b>IMPLEMENTATION DETAIL:</b> This will likely require a new {@link com.hedera.hapi.node.state.token.Token}
     * field to track the number of un-deleted child partitions of a parent token type. Otherwise we would need to
     * iterate over all partitions of a parent token type to confirm all are deleted.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> notHonorDeletionOfTokenWithExistingPartitions() {
        return defaultHapiSpec("NotHonorDeletionOfTokenWithExistingPartitions")
                .given(fungibleTokenWithFeatures(PARTITIONING)
                        .initialSupply(6L)
                        .withPartition(RED_PARTITION, p -> p.initialSupply(1L))
                        .withPartition(BLUE_PARTITION, p -> p.initialSupply(2L))
                        .withPartition(GREEN_PARTITION, p -> p.initialSupply(3L)))
                .when(
                        // Attempt to delete "TokenWithPartition" which has a partition should fail
                        tokenDelete(TOKEN_UNDER_TEST)
                                // FUTURE - use a partition-specific status code
                                .hasKnownStatus(TOKEN_WAS_DELETED),
                        // Even deleting both RED and BLUE partitions, still cannot delete the parent
                        tokenDelete(partition(RED_PARTITION)),
                        tokenDelete(partition(BLUE_PARTITION)),
                        tokenDelete(TOKEN_UNDER_TEST)
                                // FUTURE - use a partition-specific status code
                                .hasKnownStatus(TOKEN_WAS_DELETED))
                .then(tokenDelete(partition(GREEN_PARTITION)), tokenDelete(TOKEN_UNDER_TEST));
    }

    /**
     * <b>Partitions-14</b>
     * <p>As a `supply-key` holder, I want to mint tokens into a specific partition of the treasury account.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> mintToSpecificPartitionOfTreasury() {
        return defaultHapiSpec("MintToSpecificPartitionOfTreasury")
                .given(fungibleTokenWithFeatures(PARTITIONING, SUPPLY_MANAGEMENT)
                        .initialSupply(99L)
                        .withPartition(RED_PARTITION, p -> p.initialSupply(0))
                        .withPartition(BLUE_PARTITION, p -> p.initialSupply(0)))
                .when(
                        // Minting tokens specifically to the RED partition
                        mintToken(partition(RED_PARTITION), 1L))
                .then(
                        // Validate that the minted tokens have been allocated to the specified partition
                        getTokenInfo(TOKEN_UNDER_TEST).hasTotalSupply(100L),
                        getTokenInfo(partition(RED_PARTITION)).hasTotalSupply(1L),
                        getTokenInfo(partition(BLUE_PARTITION)).hasTotalSupply(0L));
    }

    /**
     * <b>Partitions-15</b>
     * <p>As a `supply-key` holder, I want to burn tokens from a specific partition of the treasury account.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> burnFromSpecificPartitionOfTreasury() {
        return defaultHapiSpec("BurnFromSpecificPartitionOfTreasury")
                .given(fungibleTokenWithFeatures(PARTITIONING, SUPPLY_MANAGEMENT)
                        .initialSupply(99L)
                        .withPartition(RED_PARTITION, p -> p.initialSupply(1))
                        .withPartition(BLUE_PARTITION, p -> p.initialSupply(1)))
                .when(
                        // Burning tokens specifically from the RED partition
                        burnToken(partition(RED_PARTITION), 1L))
                .then(
                        // Validate that the burned tokens have been deducted from the specified partition
                        getTokenInfo(TOKEN_UNDER_TEST).hasTotalSupply(100L),
                        getTokenInfo(partition(RED_PARTITION)).hasTotalSupply(0L),
                        getTokenInfo(partition(BLUE_PARTITION)).hasTotalSupply(1L));
    }

    /**
     * <b>Partitions-16</b>
     * <p>As a `wipe-key` holder, I want to wipe tokens from a specific partition in the user's account.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> wipeFromSpecificPartitionInUserAccount() {
        return defaultHapiSpec("WipeFromSpecificPartitionInUserAccount")
                .given(nonFungibleTokenWithFeatures(PARTITIONING, WIPING)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION)
                        // Given Alice a serial number of both the parent and each partition
                        .withRelation(ALICE, r -> r.ownedSerialNos(1L)
                                .alsoForPartition(RED_PARTITION, pr -> pr.ownedSerialNos(2L))
                                .andPartition(BLUE_PARTITION, pr -> pr.ownedSerialNos(3L))))
                .when(wipeTokenAccount(partition(RED_PARTITION), ALICE, List.of(2L)))
                .then(
                        // Validate that the tokens have been wiped from the specified partition
                        getTokenNftInfo(partition(RED_PARTITION), 2L).hasCostAnswerPrecheck(INVALID_NFT_ID));
    }

    /**
     * <b>Partitions-17</b>
     * <p>As a `token-administrator` smart contract, I want to create, update, and delete partitions,
     * and in all other ways work with partitions as I would using the HAPI.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> smartContractAdministersPartitions() {
        return defaultHapiSpec("SmartContractAdministersPartitions")
                .given(fungibleTokenWithFeatures(PARTITIONING, ADMIN_CONTROL).managedByContract())
                .when(
                        // The smart contract creates a token with a partition
                        contractCall(
                                managementContractOf(TOKEN_UNDER_TEST),
                                CREATE_PARTITION_FUNCTION.getName(),
                                RED_PARTITION,
                                "NEVER RED"),
                        // The smart contract updates the token's partition details
                        contractCall(
                                managementContractOf(TOKEN_UNDER_TEST),
                                UPDATE_PARTITION_FUNCTION.getName(),
                                // Don't update the name
                                false,
                                "",
                                // Do update the memo
                                true,
                                "ALWAYS BLUE"))
                .then(
                        // The contract deletes the partition
                        withHeadlongAddressesFor(
                                List.of(partition(RED_PARTITION)),
                                addresses -> List.of(contractCall(
                                        managementContractOf(TOKEN_UNDER_TEST),
                                        DELETE_PARTITION_FUNCTION.getName(),
                                        addresses.get(partition(RED_PARTITION))))),
                        getTokenInfo(partition(RED_PARTITION))
                                .isDeleted()
                                .hasName(RED_PARTITION)
                                .hasEntityMemo("ALWAYS BLUE"));
    }

    /**
     * <b>Partitions-18</b>
     * <p>If freeze or pause is set at the `token-definition` level then it takes precedence over the
     * `partition-definition` level.
     *
     * <p><b>TODO</b> - confirm the significance of this story is just that a token-level freeze or pause
     * cannot be reversed by a partition-level unfreeze or unpause.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> freezeOrPauseAtTokenLevelOverridesPartition() {
        return defaultHapiSpec("FreezeOrPauseAtTokenLevelOverridesPartition")
                .given(nonFungibleTokenWithFeatures(PARTITIONING, PAUSING, FREEZING)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION)
                        // Given Alice a serial number of both the parent and each partition
                        .withRelation(ALICE, r -> r.ownedSerialNos(1L)
                                .alsoForPartition(RED_PARTITION, pr -> pr.ownedSerialNos(2L))
                                .andPartition(BLUE_PARTITION, pr -> pr.ownedSerialNos(3L))))
                .when(
                        // If the token definition is paused then an explicit unpause at the partition
                        // level won't undo that
                        tokenPause(TOKEN_UNDER_TEST),
                        tokenUnpause(partition(RED_PARTITION)),
                        cryptoTransfer(movingUnique(partition(RED_PARTITION), 2L)
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                .hasKnownStatus(TOKEN_IS_PAUSED),
                        // So now unpause the token definition for the analogous freeze test
                        tokenPause(TOKEN_UNDER_TEST))
                .then(
                        // If Alice is frozen out of the token definition then an explicit freeze at the partition
                        // level won't undo that
                        tokenFreeze(TOKEN_UNDER_TEST, ALICE),
                        tokenUnfreeze(partition(RED_PARTITION), ALICE),
                        cryptoTransfer(movingUnique(partition(RED_PARTITION), 2L)
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
