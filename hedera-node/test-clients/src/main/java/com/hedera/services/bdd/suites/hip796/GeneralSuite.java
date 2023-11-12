package com.hedera.services.bdd.suites.hip796;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.fungibleTokenWithFeatures;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.assertPartitionInheritedExpectedProperties;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.lockNfts;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.lockUnits;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.nonFungibleTokenWithFeatures;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.partition;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.treasuryOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.LOCKING;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.PARTITIONING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

@HapiTestSuite
public class GeneralSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(GeneralSuite.class);
    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
            canCreateFungibleTokenWithLockingAndPartitioning(),
            canCreateNFTWithLockingAndPartitioning());
    }

    /**
     * <b>General-1</b>
     * <p>As a `token-issuer`, I want to create a fungible token definition with locking and/or partitioning
     * capabilities.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canCreateFungibleTokenWithLockingAndPartitioning() {
        return defaultHapiSpec("CanCreateFungibleTokenWithLockingAndPartitioning")
                .given(
                        fungibleTokenWithFeatures(PARTITIONING, LOCKING)
                                .withPartitions(RED_PARTITION, BLUE_PARTITION)
                                .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION))
                ).when(
                        lockUnits(ALICE, partition(RED_PARTITION), FUNGIBLE_INITIAL_BALANCE / 2)
                ).then(
                        assertPartitionInheritedExpectedProperties(RED_PARTITION),
                        assertPartitionInheritedExpectedProperties(BLUE_PARTITION),
                        // Alice cannot transfer any of her locked balance
                        cryptoTransfer(moving(
                                FUNGIBLE_INITIAL_BALANCE / 2,
                                partition(RED_PARTITION)).between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                // FUTURE - use a lock-specific status check
                                .hasKnownStatus(INSUFFICIENT_ACCOUNT_BALANCE),
                        // Alice can still transfer her unlocked balance to the token treasury
                        cryptoTransfer(moving(
                                FUNGIBLE_INITIAL_BALANCE / 2,
                                partition(RED_PARTITION)).between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                );
    }

    /**
     * <b>General-2</b>
     * <p>As a `token-issuer`, I want to create a non-fungible token definition with locking and/or partitioning
     * capabilities.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canCreateNFTWithLockingAndPartitioning() {
        return defaultHapiSpec("CanCreateNFTWithLockingAndPartitioning")
                .given(
                        nonFungibleTokenWithFeatures(PARTITIONING, LOCKING)
                                .withPartitions(RED_PARTITION, BLUE_PARTITION)
                                .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION).ownedSerialNos(1L, 2L))
                ).when(
                        lockNfts(ALICE, RED_PARTITION, 1L)
                ).then(
                        assertPartitionInheritedExpectedProperties(RED_PARTITION),
                        assertPartitionInheritedExpectedProperties(BLUE_PARTITION),
                        // Alice cannot transfer her locked NFT
                        cryptoTransfer(movingUnique(partition(RED_PARTITION), 1L)
                                .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                // FUTURE - use a lock-specific status code
                                .hasKnownStatus(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO),
                        // Alice can still transfer her unlocked NFT to the token treasury
                        cryptoTransfer(movingUnique(partition(RED_PARTITION), 2L)
                                .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                );
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
