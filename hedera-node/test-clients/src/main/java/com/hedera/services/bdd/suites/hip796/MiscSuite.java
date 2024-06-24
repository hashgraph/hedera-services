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
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.unchangedFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenPause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingWithAllowance;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.fungibleTokenWithFeatures;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.autoRenewAccountOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.customFeeScheduleKeyOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.partition;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.treasuryOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.CUSTOM_FEE_SCHEDULE_MANAGEMENT;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.PARTITIONING;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.WIPING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.hip796.operations.DesiredAccountTokenRelation;
import com.hedera.services.bdd.suites.hip796.operations.TokenFeature;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

/**
 * A suite for user stories Misc-1 through Misc-6 from HIP-796.
 */

// too may parameters
@SuppressWarnings("java:S1192")
public class MiscSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(MiscSuite.class);

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(
                tokenOpsUnchangedWithPartitionDefinitions(),
                rentNotYetChargedForPartitionAndDefinitions(),
                approvalAllowanceSpecificPartition(),
                accountExpiryAndReclamationIsNotEnabled(),
                accountDeletionWithTokenHoldings(),
                customFeesAtTokenDefinitionLevel());
    }

    /**
     * <b>Misc-1</b>
     * <p>As a `token-administrator`, I would like all operations on the `token-definition`, such as freeze, pause,
     * metadata updates, kyc-flag updates, etc., to function unchanged from prior releases, even if
     * `partition-definitions` are specified, since they operate at the `token-definition` level and are not
     * specific to any single partition.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> tokenOpsUnchangedWithPartitionDefinitions() {
        return defaultHapiSpec("TokenOpsUnchangedWithPartitionDefinitions")
                .given(
                        // Create a partitioned token with all features
                        fungibleTokenWithFeatures(TokenFeature.values())
                                .withPartition(RED_PARTITION)
                                // Suppress automatic granting of KYC to Alice here
                                .withRelation(ALICE, r -> r.alsoForPartition(
                                                RED_PARTITION, DesiredAccountTokenRelation::kycRevoked)
                                        .kycRevoked()))
                .when(
                        grantTokenKyc(TOKEN_UNDER_TEST, ALICE),
                        grantTokenKyc(partition(RED_PARTITION), ALICE),
                        cryptoTransfer(
                                moving(FUNGIBLE_INITIAL_BALANCE, TOKEN_UNDER_TEST)
                                        .between(treasuryOf(TOKEN_UNDER_TEST), ALICE),
                                moving(FUNGIBLE_INITIAL_BALANCE, partition(RED_PARTITION))
                                        .between(treasuryOf(TOKEN_UNDER_TEST), ALICE)),
                        tokenUpdate(TOKEN_UNDER_TEST).memo("New parent memo"),
                        tokenUpdate(partition(RED_PARTITION)).memo("New partition memo"),
                        tokenFreeze(TOKEN_UNDER_TEST, ALICE),
                        tokenFreeze(partition(RED_PARTITION), ALICE),
                        wipeTokenAccount(TOKEN_UNDER_TEST, ALICE, 1L),
                        wipeTokenAccount(partition(RED_PARTITION), ALICE, 1L),
                        tokenPause(TOKEN_UNDER_TEST),
                        tokenPause(partition(RED_PARTITION)),
                        mintToken(TOKEN_UNDER_TEST, 1L),
                        mintToken(partition(RED_PARTITION), 1L),
                        burnToken(TOKEN_UNDER_TEST, 1L),
                        burnToken(partition(RED_PARTITION), 1L))
                .then(
                        revokeTokenKyc(TOKEN_UNDER_TEST, ALICE),
                        revokeTokenKyc(partition(RED_PARTITION), ALICE),
                        tokenDelete(partition(RED_PARTITION)),
                        tokenDelete(TOKEN_UNDER_TEST));
    }

    /**
     * <b>Misc-2</b>
     * <p>Rent: As a node operator, I want to charge rent for each `partition` and `partition-definition` on the ledger.
     * The account pays for `partition` rent unless an auto-renew-payer is specified on the account.
     *
     * <p><b>NOTE:</b> This is just a placeholder that verifies no rent is actually being charged, since that
     * feature will not be enabled for some time.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> rentNotYetChargedForPartitionAndDefinitions() {
        return propertyPreservingHapiSpec("RentNotYetChargedForPartitionAndDefinitions")
                .preserving("ledger.autoRenewPeriod.minDuration")
                .given(
                        overriding("ledger.autoRenewPeriod.minDuration", "1"),
                        fungibleTokenWithFeatures(PARTITIONING)
                                .autoRenewPeriod(1)
                                .withPartition(RED_PARTITION)
                                .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION)
                                        .autoRenewPeriod(1)),
                        balanceSnapshot("autoRenewBalanceBeforeRenewal", autoRenewAccountOf(TOKEN_UNDER_TEST)),
                        balanceSnapshot("aliceBalanceBeforeRenewal", ALICE))
                .when(
                        // Sleep for longer than the auto-renew period
                        sleepFor(2_000),
                        // Do a transfer to (theoretically) trigger an auto-renewal payment in this contrived scenario
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)))
                .then(
                        // Verify no changes from balance snapshots
                        getAccountBalance(autoRenewAccountOf(TOKEN_UNDER_TEST))
                                .hasTinyBars(unchangedFromSnapshot("autoRenewBalanceBeforeRenewal")),
                        getAccountBalance(ALICE).hasTinyBars(unchangedFromSnapshot("aliceBalanceBeforeRenewal")));
    }

    /**
     * <b>Misc-3</b>
     * <p>Approval/Allowance: As a user, I want to grant an allowance to another user for a specific amount in a
     * specific partition of my token balance (for fungible tokens).
     *
     * <p>Note the following:
     * <ol>
     *     <li>The allowance at a token-definition level will not be interpreted at a given partition level.</li>
     *     <li>Each partition should provide its own allowance.</li>
     *     <li>If I have a partitioned token and I have granted allowances to another user at a token-definition level
     *     (and not at the partition level), then an allowance-based transfer transaction that tries to transfer tokens
     *     from a specific partition will fail.</li>
     * </ol>
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> approvalAllowanceSpecificPartition() {
        return defaultHapiSpec("ApprovalAllowanceSpecificPartition")
                .given(
                        fungibleTokenWithFeatures(PARTITIONING)
                                .withPartitions(RED_PARTITION, BLUE_PARTITION)
                                .withRelation(ALICE, r -> r.alsoForPartition(RED_PARTITION)
                                        .alsoForPartition(BLUE_PARTITION)),
                        cryptoCreate(BOB))
                .when(
                        // Grant allowance to Bob at the token-definition level
                        cryptoApproveAllowance()
                                .payingWith(ALICE)
                                .addTokenAllowance(ALICE, TOKEN_UNDER_TEST, BOB, FUNGIBLE_INITIAL_BALANCE)
                                .fee(2 * ONE_HBAR),
                        // Bob is not approved to spend Alice's tokens from the RED partition
                        // simply because they have a token-definition level allowance
                        cryptoTransfer(movingWithAllowance(1L, partition(RED_PARTITION))
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                .payingWith(BOB)
                                .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                        // Grant allowance to Bob for the RED partition
                        cryptoApproveAllowance()
                                .payingWith(ALICE)
                                .addTokenAllowance(ALICE, partition(RED_PARTITION), BOB, FUNGIBLE_INITIAL_BALANCE)
                                .fee(2 * ONE_HBAR))
                .then(
                        // Bob is approved to spend Alice's tokens from the RED partition
                        cryptoTransfer(movingWithAllowance(1L, partition(RED_PARTITION))
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                .payingWith(BOB),
                        // Bub still not approved to spend Alice's tokens from the BLUE partition
                        cryptoTransfer(movingWithAllowance(1L, partition(BLUE_PARTITION))
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                .payingWith(BOB)
                                .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE));
    }

    /**
     * <b>Misc-4</b>
     * <p>Account expiry: As a node operator, I want to reclaim the memory used by expired accounts that haven’t paid their rent.
     *
     * <p>Note the following:
     * <ol>
     *     <li>Before Hedera implements archiving: When a user account expires, the tokens of each partition will be
     *     moved to the treasury account of the associated `token-definition`. This is consistent with how Hedera
     *     intends to treat the expiry of accounts that hold any tokens.</li>
     *     <li>After Hedera implements archiving: When a user account expires, the partitions will be archived along
     *     with the account. This is consistent with how Hedera intends to treat the expiry of accounts that hold any
     *     tokens after archiving is implemented.</li>
     *     <li>When a treasury account expires, the `token-definition` will be deemed as expired and the
     *     `token-definition` and all `partition-definition`s within that `token-definition` will be deleted/archived.
     *     This is consistent with how Hedera intends to treat the expiry of treasury accounts for any tokens.</li>
     * </ol>
     *
     * <p><b>NOTE:</b> This is just a placeholder that verifies nothing is actually being reclaimed, since that
     * feature will not be enabled for some time.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> accountExpiryAndReclamationIsNotEnabled() {
        return propertyPreservingHapiSpec("AccountExpiryAndReclamationIsNotEnabled")
                .preserving("ledger.autoRenewPeriod.minDuration")
                .given(
                        overriding("ledger.autoRenewPeriod.minDuration", "1"),
                        cryptoCreate(BOB).balance(0L),
                        fungibleTokenWithFeatures(PARTITIONING)
                                .autoRenewPeriod(1)
                                // Ensure auto-renew account cannot pay for auto-renew
                                .autoRenewAccount(BOB)
                                .withPartitions(RED_PARTITION, BLUE_PARTITION)
                                .withRelation(ALICE, r -> r.autoRenewPeriod(1)
                                        // Ensure Alice cannot pay for auto-renew
                                        .balance(0L)
                                        .onlyForPartition(RED_PARTITION)
                                        .andPartition(BLUE_PARTITION)))
                .when(
                        // Sleep for longer than the auto-renew period
                        sleepFor(2_000),
                        // Do a transfer to (theoretically) trigger expirations in this contrived scenario
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)))
                .then(
                        // Verify all tokens, partitions, and accounts still exist
                        getTokenInfo(TOKEN_UNDER_TEST),
                        getTokenInfo(partition(RED_PARTITION)),
                        getTokenInfo(partition(ALICE)));
    }

    /**
     * <b>Misc-5</b>
     * <p>Account deletion: As a node operator, I do not want to honor account deletion requests if the account holds
     * tokens, including in any partition. The user must dispose of their tokens from their account before the account
     * can be deleted.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> accountDeletionWithTokenHoldings() {
        return defaultHapiSpec("AccountDeletionWithTokenHoldings")
                .given(fungibleTokenWithFeatures(PARTITIONING, WIPING)
                        .withPartitions(RED_PARTITION)
                        .withRelation(
                                ALICE, r -> r.onlyForPartition(RED_PARTITION).balance(0L)))
                .when(
                        // Cannot delete Alice due to their RED partition tokens
                        cryptoDelete(ALICE).hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES))
                .then(
                        // Wipe Alice's token holdings before trying to delete the account again
                        wipeTokenAccount(partition(RED_PARTITION), ALICE, FUNGIBLE_INITIAL_BALANCE),
                        // Now we can delete Alice
                        cryptoDelete(ALICE));
    }

    /**
     * <b>Misc-6</b>
     * <p>Custom Fees at Token-Definition Level: As a token-issuer, I want to set custom fees at the `token-definition`
     * level and not at the partition level. The fees will be applied to all partitions of the `token-definition`.
     * Custom fees will not be applied when moving tokens between partitions of the same account.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> customFeesAtTokenDefinitionLevel() {
        return defaultHapiSpec("CustomFeesAtTokenDefinitionLevel")
                .given(fungibleTokenWithFeatures(PARTITIONING, CUSTOM_FEE_SCHEDULE_MANAGEMENT)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION)
                        .withCustomFee(fixedHbarFee(ONE_HBAR, customFeeScheduleKeyOf(TOKEN_UNDER_TEST)))
                        .withRelation(
                                ALICE, r -> r.onlyForPartition(RED_PARTITION).andPartition(BLUE_PARTITION))
                        .withRelation(BOB, r -> r.onlyForPartition(RED_PARTITION)))
                .when(
                        // Transfer tokens between different accounts, custom fee should be applied
                        cryptoTransfer(moving(1L, partition(RED_PARTITION)).between(ALICE, BOB))
                                .payingWith(ALICE)
                                .fee(ONE_HBAR)
                                .via("interUserTransfer"),
                        // Transfer tokens between different partitions of same account, custom fee should NOT be
                        // applied
                        cryptoTransfer(moving(1L, partition(RED_PARTITION))
                                        .betweenWithPartitionChange(ALICE, ALICE, BLUE_PARTITION))
                                .payingWith(ALICE)
                                .fee(ONE_HBAR)
                                .via("intraUserTransfer"))
                .then(
                        getTxnRecord("interUserTransfer")
                                .hasPriority(recordWith().assessedCustomFeeCount(1)),
                        getTxnRecord("intraUserTransfer")
                                .hasPriority(recordWith().assessedCustomFeeCount(0)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
