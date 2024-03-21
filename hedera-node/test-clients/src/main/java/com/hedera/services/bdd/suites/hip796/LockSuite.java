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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.fungibleTokenWithFeatures;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.lockNfts;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.lockUnits;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.nonFungibleTokenWithFeatures;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.unlockUnits;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.partition;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.treasuryOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.LOCKING;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.PARTITIONING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A suite for user stories Lock-1 through Lock-8 from HIP-796.
 */
// @HapiTestSuite
public class LockSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(LockSuite.class);

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                canLockSubsetOfUnlockedTokens(),
                canLockSubsetOfUnlockedTokensInPartition(),
                canUnlockSubsetOfLockedTokens(),
                canUnlockSubsetOfLockedTokensInPartition(),
                canLockSpecificNFTSerials(),
                canLockSpecificNFTSerialsInPartition(),
                canUnlockSpecificNFTSerials(),
                canUnlockSpecificNFTSerialsInPartition());
    }

    /**
     * <b>Lock-1</b>
     * <p>As a `lock-key` holder, I want to lock a subset of the currently held unpartitioned
     * unlocked fungible tokens held by a user's account without requiring the user's signature.
     * If an account has `x` unlocked tokens, then the number of tokens that can be additionally
     * locked is governed by: `0 &#60;= number_of_tokens_to_be_locked &#60;= x`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canLockSubsetOfUnlockedTokens() {
        return defaultHapiSpec("CanLockSubsetOfUnlockedTokens")
                .given(fungibleTokenWithFeatures(LOCKING).withRelation(ALICE))
                .when(lockUnits(ALICE, TOKEN_UNDER_TEST, FUNGIBLE_INITIAL_BALANCE / 2))
                .then(
                        // Can't unlock more than the locked amount
                        unlockUnits(ALICE, TOKEN_UNDER_TEST, FUNGIBLE_INITIAL_BALANCE / 2 + 1)
                                // FUTURE - replace with a lock-specific specific error code
                                .hasKnownStatus(INVALID_ACCOUNT_AMOUNTS),
                        // Can't lock more than the unlocked amount
                        lockUnits(ALICE, TOKEN_UNDER_TEST, FUNGIBLE_INITIAL_BALANCE / 2 + 1)
                                // FUTURE - replace with a lock-specific specific error code
                                .hasKnownStatus(INVALID_ACCOUNT_AMOUNTS),
                        // But if we unlock just enough units
                        unlockUnits(ALICE, TOKEN_UNDER_TEST, 1),
                        // Now the above lock works
                        lockUnits(ALICE, TOKEN_UNDER_TEST, FUNGIBLE_INITIAL_BALANCE / 2 + 1));
    }

    /**
     * <b>Lock-2</b>
     * <p>As a `lock-key` holder, I want to lock a subset of the currently held unlocked fungible
     * tokens held by a user's account in a partition without requiring the user's signature.
     * If an account has `x` unlocked tokens, then the number of tokens that can be additionally
     * locked is governed by: `0 &#60;= number_of_tokens_to_be_locked &#60;= x`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canLockSubsetOfUnlockedTokensInPartition() {
        return defaultHapiSpec("CanLockSubsetOfUnlockedTokensInPartition")
                .given(fungibleTokenWithFeatures(LOCKING, PARTITIONING)
                        .withPartition(RED_PARTITION)
                        .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION)))
                .when(lockUnits(ALICE, partition(RED_PARTITION), FUNGIBLE_INITIAL_BALANCE / 2))
                .then(
                        // Can't lock more than the unlocked amount
                        lockUnits(ALICE, partition(RED_PARTITION), FUNGIBLE_INITIAL_BALANCE / 2 + 1)
                                // FUTURE - replace with a lock-specific specific error code
                                .hasKnownStatus(INVALID_ACCOUNT_AMOUNTS));
    }

    /**
     * <b>Lock-3</b>
     * <p>As a `lock-key` holder, I want to unlock a subset of the currently held unpartitioned locked
     * fungible tokens held by a user's account without requiring the user's signature.
     * If an account has `x` locked tokens, then the number of tokens that can be additionally
     * unlocked is governed by: `0 &#60;= number_of_locked_tokens &#60;= x`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canUnlockSubsetOfLockedTokens() {
        return defaultHapiSpec("CanLockSubsetOfUnlockedTokens")
                .given(fungibleTokenWithFeatures(LOCKING).withRelation(ALICE))
                .when(lockUnits(ALICE, TOKEN_UNDER_TEST, FUNGIBLE_INITIAL_BALANCE / 2))
                .then(
                        // Can't unlock more than the locked amount
                        unlockUnits(ALICE, TOKEN_UNDER_TEST, FUNGIBLE_INITIAL_BALANCE / 2 + 1)
                                // FUTURE - replace with a lock-specific specific error code
                                .hasKnownStatus(INVALID_ACCOUNT_AMOUNTS),
                        // But if we lock just enough additional units
                        lockUnits(ALICE, TOKEN_UNDER_TEST, 1),
                        // Now the above unlock works
                        unlockUnits(ALICE, TOKEN_UNDER_TEST, FUNGIBLE_INITIAL_BALANCE / 2 + 1));
    }

    /**
     * <b>Lock-4</b>
     * <p>As a `lock-key` holder, I want to unlock a subset of the currently held locked fungible tokens
     * held by a user's account in a partition without requiring the user's signature.
     * If an account has `x` locked tokens in a partition, then the number of tokens that can be
     * additionally unlocked is governed by: `0 &#60;= number_of_locked_tokens &#60;= x`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canUnlockSubsetOfLockedTokensInPartition() {
        return defaultHapiSpec("CanUnlockSubsetOfLockedTokensInPartition")
                .given(fungibleTokenWithFeatures(LOCKING)
                        .withPartition(RED_PARTITION)
                        .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION)))
                .when(lockUnits(ALICE, partition(RED_PARTITION), FUNGIBLE_INITIAL_BALANCE / 2))
                .then(
                        // Can't unlock more than the locked amount
                        unlockUnits(ALICE, partition(RED_PARTITION), FUNGIBLE_INITIAL_BALANCE / 2 + 1)
                                // FUTURE - replace with a lock-specific specific error code
                                .hasKnownStatus(INVALID_ACCOUNT_AMOUNTS),
                        // But if we lock just enough additional units
                        lockUnits(ALICE, partition(RED_PARTITION), 1),
                        // But if we unlock just enough units
                        lockUnits(ALICE, partition(RED_PARTITION), 1),
                        // Now the above unlock works
                        unlockUnits(ALICE, partition(RED_PARTITION), FUNGIBLE_INITIAL_BALANCE / 2 + 1));
    }

    /**
     * <b>Lock-5</b>
     * <p>As a `lock-key` holder, I want to lock specific NFT serials currently unlocked in a user's account
     * without requiring the user's signature.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canLockSpecificNFTSerials() {
        return defaultHapiSpec("CanLockSpecificNFTSerials")
                .given(nonFungibleTokenWithFeatures(LOCKING).withRelation(ALICE, r -> r.ownedSerialNos(1L, 2L)))
                .when(lockNfts(ALICE, TOKEN_UNDER_TEST, 1L))
                .then(
                        // Can't transfer the locked serial no
                        cryptoTransfer(movingUnique(TOKEN_UNDER_TEST, 1L).between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                // FUTURE - replace with a lock-specific specific error code
                                .hasKnownStatus(INVALID_NFT_ID),
                        // Can transfer the unlocked serial no
                        cryptoTransfer(
                                movingUnique(TOKEN_UNDER_TEST, 2L).between(ALICE, treasuryOf(TOKEN_UNDER_TEST))));
    }

    /**
     * <b>Lock-6</b>
     * <p>As a `lock-key` holder, I want to lock specific NFT serials currently unlocked in a user's account
     * in a partition without requiring the user's signature.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canLockSpecificNFTSerialsInPartition() {
        return defaultHapiSpec("CanLockSpecificNFTSerialsInPartition")
                .given(nonFungibleTokenWithFeatures(LOCKING, PARTITIONING)
                        .withPartition(RED_PARTITION)
                        .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION, pr -> pr.ownedSerialNos(1L, 2L))))
                .when(lockNfts(ALICE, partition(RED_PARTITION), 1L))
                .then(
                        // Can't transfer the locked serial no
                        cryptoTransfer(movingUnique(partition(RED_PARTITION), 1L)
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                // FUTURE - replace with a lock-specific specific error code
                                .hasKnownStatus(INVALID_NFT_ID),
                        // Can transfer the unlocked serial no
                        cryptoTransfer(movingUnique(partition(RED_PARTITION), 2L)
                                .between(ALICE, treasuryOf(TOKEN_UNDER_TEST))));
    }

    /**
     * <b>Lock-7</b>
     * <p>As a `lock-key` holder, I want to unlock specific NFT serials currently locked in a user's account
     * without requiring the user's signature.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canUnlockSpecificNFTSerials() {
        return defaultHapiSpec("CanUnlockSpecificNFTSerials")
                .given(nonFungibleTokenWithFeatures(LOCKING).withRelation(ALICE, r -> r.ownedSerialNos(1L, 2L)))
                .when(lockNfts(ALICE, TOKEN_UNDER_TEST, 1L))
                .then(
                        // Can't transfer the locked serial no
                        cryptoTransfer(movingUnique(TOKEN_UNDER_TEST, 1L).between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                // FUTURE - replace with a lock-specific specific error code
                                .hasKnownStatus(INVALID_NFT_ID),
                        unlockUnits(ALICE, TOKEN_UNDER_TEST, 1L),
                        // Now we can transfer the serial no
                        cryptoTransfer(
                                movingUnique(TOKEN_UNDER_TEST, 1L).between(ALICE, treasuryOf(TOKEN_UNDER_TEST))));
    }

    /**
     * <b>Lock-8</b>
     * <p>As a `lock-key` holder, I want to unlock specific NFT serials currently locked in a user's account
     * in a partition without requiring the user's signature.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canUnlockSpecificNFTSerialsInPartition() {
        return defaultHapiSpec("CanUnlockSpecificNFTSerialsInPartition")
                .given(nonFungibleTokenWithFeatures(LOCKING, PARTITIONING)
                        .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION, pr -> pr.ownedSerialNos(1L, 2L))))
                .when(lockNfts(ALICE, partition(RED_PARTITION), 1L))
                .then(
                        // Can't transfer the locked serial no
                        cryptoTransfer(movingUnique(partition(RED_PARTITION), 1L)
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                // FUTURE - replace with a lock-specific specific error code
                                .hasKnownStatus(INVALID_NFT_ID),
                        unlockUnits(ALICE, partition(RED_PARTITION), 1L),
                        // Now we can transfer the serial no
                        cryptoTransfer(movingUnique(partition(RED_PARTITION), 1L)
                                .between(ALICE, treasuryOf(TOKEN_UNDER_TEST))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
