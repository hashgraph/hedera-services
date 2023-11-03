package com.hedera.services.bdd.suites.hip796;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * A suite for user stories Association-1 through Association-7 from HIP-796.
 */
public class PartitionAssociationsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(PartitionAssociationsSuite.class);

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                associateWithPartitionedTokenAutomatically(),
                associateWithPartitionAndToken(),
                autoAssociateSiblingPartitions(),
                autoAssociateChildPartitions(),
                disassociateEmptyPartition(),
                doNotAllowDisassociateWithNonEmptyPartition(),
                ensurePartitionsRemovedBeforeTokenDisassociation());
    }

    /**
     * <b>Association-1</b>
     * <p>As a user, I want to associate with a `token-definition` that has `partition-definitions`.
     * When tokens are sent to my account for a partition of that `token-definition`, then I want to
     * automatically associate with that `partition-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec associateWithPartitionedTokenAutomatically() {
        return defaultHapiSpec("AssociateWithPartitionedTokenAutomatically")
                .given(
                        newKeyNamed("TokenAdminKey"),
                        cryptoCreate("user"),
                        tokenCreate("PartitionedToken")
                                .adminKey("TokenAdminKey")
                                .withPartition("GoldPartition")
                                .withPartition("SilverPartition")
                                .initialSupply(0)
                )
                .when(
                        // The user associates with the 'PartitionedToken' token-definition
                        tokenAssociate("user", "PartitionedToken"),
                        // Tokens are issued to a specific partition
                        mintToken("PartitionedToken", List.of(Metadata.newBuilder().setMemo("GoldPartition").build())),
                        // The tokens for 'GoldPartition' are transferred to the user
                        cryptoTransfer(
                                movingUnique(1)
                                        .withMemo("GoldPartition")
                                        .between("treasury", "user")
                        )
                )
                .then(
                        // Verify the user is now associated with the 'GoldPartition' partition-definition automatically
                        getAccountInfo("user").hasAssociationWith("PartitionedToken", "GoldPartition"),
                        // Verify the user's balance for the 'GoldPartition' reflects the transferred amount
                        getAccountBalance("user").hasTokenBalance("PartitionedToken", "GoldPartition", 1)
                );
    }

    /**
     * <b>Association-2</b>
     * <p>As a user, I want to associate with a `partition-definition`, exactly as I would for associating with any other
     * `token-definition`, and automatically get the `token-definition` associated too without extra cost, without using
     * the auto-association slots.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec associateWithPartitionAndToken() {
        return defaultHapiSpec("AssociateWithPartitionAndToken")
                .given(
                        newKeyNamed("TokenAdminKey"),
                        cryptoCreate("user"),
                        tokenCreate("PartitionedToken")
                                .adminKey("TokenAdminKey")
                                .initialSupply(0)
                                .withPartition("PlatinumPartition")
                )
                .when(
                        // User associates with the specific 'PlatinumPartition' of the 'PartitionedToken'
                        tokenAssociate("user", "PartitionedToken", "PlatinumPartition")
                )
                .then(
                        // Verify the user is associated with the 'PlatinumPartition' partition-definition
                        getAccountInfo("user").hasAssociationWith("PartitionedToken", "PlatinumPartition"),
                        // Verify the user is also associated with the parent 'PartitionedToken' token-definition
                        getAccountInfo("user").hasAssociationWith("PartitionedToken")
                );
    }

    /**
     * <b>Association-3</b>
     * <p>As a user, once associated with a `partition-definition`, I want transfers into my account for "sibling"
     * `partition-definition`s to be auto-partition-associated with extra cost, without using the auto-association slots.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec autoAssociateSiblingPartitions() {
        return defaultHapiSpec("AutoAssociateSiblingPartitions")
                .given(
                        newKeyNamed("TokenAdminKey"),
                        cryptoCreate("user"),
                        tokenCreate("PartitionedToken")
                                .adminKey("TokenAdminKey")
                                .initialSupply(0)
                                .withPartition("GoldPartition")
                                .withPartition("SilverPartition")
                                .withPartition("BronzePartition"),
                        tokenAssociate("user", "GoldPartition")
                )
                .when(
                        // Transfer to user account for 'SilverPartition' which is a sibling partition to 'GoldPartition'
                        cryptoTransfer(
                                moving(100, "SilverPartition").between("treasury", "user")
                        )
                )
                .then(
                        // Verify that the user is automatically associated with 'SilverPartition' after the transfer
                        getAccountInfo("user").hasAssociationWith("SilverPartition"),
                        // Verify no extra cost incurred for the auto-association
                        validateFeeForLastOperation(),
                        // Verify auto-association slot for 'user' has not been used
                        getAccountInfo("user").autoAssociationSlotsRemaining(10) // Assuming the user had 10 slots initially
                );
    }

    /**
     * <b>Association-4</b>
     * <p>As a user, once associated with a `token-definition`, I want any transfers into my account for "child"
     * `partition-definition`s to be auto-partition-associated with extra cost, without using the auto-association slots.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec autoAssociateChildPartitions() {
        return defaultHapiSpec("AutoAssociateChildPartitions")
                .given(
                        newKeyNamed("TokenAdminKey"),
                        cryptoCreate("user"),
                        tokenCreate("MasterToken")
                                .adminKey("TokenAdminKey")
                                .initialSupply(0)
                                // Assume that defining partitions here will make them "child" partitions of 'MasterToken'
                                .withPartition("GoldPartition")
                                .withPartition("SilverPartition"),
                        tokenAssociate("user", "MasterToken")
                )
                .when(
                        // Transfer to user account for 'GoldPartition' which is a "child" partition to 'MasterToken'
                        cryptoTransfer(
                                moving(100, "GoldPartition").between("treasury", "user")
                        )
                )
                .then(
                        // Verify that the user is automatically associated with 'GoldPartition' after the transfer
                        getAccountInfo("user").hasAssociationWith("GoldPartition"),
                        // Verify no extra cost incurred for the auto-association
                        validateFeeForLastOperation(),
                        // Verify auto-association slot for 'user' has not been used
                        getAccountInfo("user").autoAssociationSlotsRemaining(10) // Assuming the user had 10 slots initially
                );
    }

    /**
     * <b>Association-5</b>
     * <p>As a user, if a partition in my account holds no tokens, I want to disassociate from that `partition-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec disassociateEmptyPartition() {
        return defaultHapiSpec("DisassociateEmptyPartition")
                .given(
                        newKeyNamed("TokenAdminKey"),
                        cryptoCreate("user"),
                        tokenCreate("MasterToken")
                                .adminKey("TokenAdminKey")
                                .initialSupply(0)
                                .withPartition("EmptyPartition"),
                        tokenAssociate("user", "MasterToken"),
                        // Assume the user has previously been associated with 'EmptyPartition' implicitly or explicitly
                        cryptoTransfer(
                                moving(100, "EmptyPartition").between("treasury", "user")
                        ),
                        cryptoTransfer(
                                moving(100, "EmptyPartition").between("user", "treasury")
                        )
                )
                .when(
                        // User attempts to disassociate from 'EmptyPartition' which now holds no tokens
                        tokenDisassociate("user", "EmptyPartition")
                )
                .then(
                        // Verify that the user is no longer associated with 'EmptyPartition'
                        getAccountInfo("user").hasNoAssociationWith("EmptyPartition")
                );
    }

o/**
     * <b>Association-6</b>
     * <p>As a user, if a partition in my account holds tokens, I do not want to permit disassociation from that `partition-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec doNotAllowDisassociateWithNonEmptyPartition() {
        return defaultHapiSpec("DoNotAllowDisassociateWithNonEmptyPartition")
                .given(
                        newKeyNamed("TokenAdminKey"),
                        cryptoCreate("user"),
                        tokenCreate("MasterToken")
                                .adminKey("TokenAdminKey")
                                .initialSupply(100)
                                .withPartition("NonEmptyPartition"),
                        tokenAssociate("user", "MasterToken"),
                        cryptoTransfer(
                                moving(100, "NonEmptyPartition").between("treasury", "user")
                        )
                        // User now holds tokens in 'NonEmptyPartition'
                )
                .when(
                        // User attempts to disassociate from 'NonEmptyPartition' which holds tokens
                        tokenDisassociate("user", "NonEmptyPartition")
                                .hasKnownStatus(TOKEN_NOT_EMPTY) // Example status representing that a token partition is not empty
                )
                .then(
                        // Verify that the user is still associated with 'NonEmptyPartition'
                        getAccountInfo("user").hasAssociationWith("NonEmptyPartition")
                );
    }

    /**
     * <b>Association-7</b>
     * <p>As a node operator, I do not want to permit a user to disassociate from a `token-definition`
     * if the user account has any related partitions. The partitions must be removed first.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec ensurePartitionsRemovedBeforeTokenDisassociation() {
        return defaultHapiSpec("EnsurePartitionsRemovedBeforeTokenDisassociation")
                .given(
                        newKeyNamed("TokenAdminKey"),
                        cryptoCreate("user"),
                        tokenCreate("MasterToken")
                                .adminKey("TokenAdminKey")
                                .initialSupply(100)
                                .withPartition("PartitionA"),
                        tokenAssociate("user", "MasterToken"),
                        // User is associated with the 'MasterToken' that has 'PartitionA'
                        cryptoTransfer(
                                moving(10, "PartitionA").between("treasury", "user")
                        )
                )
                .when(
                        // User attempts to disassociate from 'MasterToken' which has a non-empty partition
                        tokenDisassociate("user", "MasterToken")
                                .hasKnownStatus(TOKEN_HAS_NON_EMPTY_PARTITIONS) // Example status representing non-empty related partitions exist
                )
                .then(
                        // Verify that the user is still associated with 'MasterToken'
                        getAccountInfo("user").hasAssociationWith("MasterToken")
                );
    }


    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
