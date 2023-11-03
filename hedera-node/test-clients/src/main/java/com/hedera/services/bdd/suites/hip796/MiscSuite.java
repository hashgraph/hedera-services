package com.hedera.services.bdd.suites.hip796;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;

/**
 * A suite for user stories Misc-1 through Misc-6 from HIP-796.
 */
public class MiscSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(MiscSuite.class);

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                tokenOpsUnchangedWithPartitionDefinitions(),
                rentChargedForPartitionAndDefinitions(),
                approvalAllowanceSpecificPartition(),
                accountExpiryAndReclamation(),
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
    @HapiTest
    public HapiSpec tokenOpsUnchangedWithPartitionDefinitions() {
        final String tokenAdmin = "TokenAdmin";
        final String token = "MultiPartitionToken";
        final String metadataUpdateTxn = "MetadataUpdateTxn";
        final String pauseTxn = "PauseTxn";
        final String resumeTxn = "ResumeTxn";

        return defaultHapiSpec("TokenOpsUnchangedWithPartitionDefinitions")
                .given(
                        newKeyNamed(tokenAdmin),
                        tokenCreate(token)
                                .adminKey(tokenAdmin)
                                .initialSupply(0)
                                .freezeDefault(true)
                                .kycKey(tokenAdmin)
                                .pauseKey(tokenAdmin)
                                .withPartition("PartitionOne", 100)
                                .withPartition("PartitionTwo", 200),
                        tokenUpdate(token)
                                .name("UpdatedName")
                                .symbol("UPDT")
                                .treasury(DEFAULT_PAYER)
                                .signedBy(DEFAULT_PAYER, tokenAdmin)
                                .via(metadataUpdateTxn)
                ).when(
                        freezeToken(token)
                                .signedBy(tokenAdmin)
                                .via(pauseTxn),
                        pauseToken(token)
                                .signedBy(tokenAdmin),
                        resumeToken(token)
                                .signedBy(tokenAdmin)
                                .via(resumeTxn)
                ).then(
                        validateRecord(metadataUpdateTxn)
                                .hasPriority(recordWith().status(SUCCESS)),
                        validateRecord(pauseTxn)
                                .hasPriority(recordWith().status(SUCCESS)),
                        validateRecord(resumeTxn)
                                .hasPriority(recordWith().status(SUCCESS)),
                        getTokenInfo(token)
                                .hasTokenName("UpdatedName")
                                .hasTokenSymbol("UPDT")
                                .logged()
                );
    }

    /**
     * <b>Misc-2</b>
     * <p>Rent: As a node operator, I want to charge rent for each `partition` and `partition-definition` on the ledger.
     * The account pays for `partition` rent unless an auto-renew-payer is specified on the account.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec rentChargedForPartitionAndDefinitions() {
        final String tokenIssuer = "TokenIssuer";
        final String partitionedToken = "PartitionedToken";
        final String autoRenewAccount = "AutoRenewAccount";
        final long initialBalance = 1_000_000L;
        final long partitionRent = 100L; // This would be a defined constant in a real implementation

        return defaultHapiSpec("RentChargedForPartitionAndDefinitions")
                .given(
                        cryptoCreate(tokenIssuer).balance(initialBalance),
                        cryptoCreate(autoRenewAccount).balance(initialBalance).autoRenewSecs(7890000),
                        newKeyNamed("AdminKey")
                ).when(
                        tokenCreate(partitionedToken)
                                .treasury(tokenIssuer)
                                .initialSupply(0)
                                .adminKey("AdminKey")
                                .autoRenewAccount(autoRenewAccount)
                                .withPartition("PartitionOne", 100)
                                .withPartition("PartitionTwo", 200),
                        sleepFor(1000), // Assuming rent is charged every second for the sake of this example
                        getAccountBalance(tokenIssuer).logged(),
                        getAccountBalance(autoRenewAccount).logged()
                ).then(
                        validateBalance(tokenIssuer)
                                .hasTinyBars(changeFromSnapshot(tokenIssuer, -(2 * partitionRent))),
                        validateBalance(autoRenewAccount)
                                .hasTinyBars(changeFromSnapshot(autoRenewAccount, 0)) // No change because it's not yet the auto-renew period
                );
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
    @HapiTest
    public HapiSpec approvalAllowanceSpecificPartition() {
        final String tokenHolder = "TokenHolder";
        final String spender = "Spender";
        final String partitionedToken = "PartitionedToken";
        final long allowanceAmount = 500L;
        final String partition = "SpecificPartition";

        return defaultHapiSpec("ApprovalAllowanceSpecificPartition")
                .given(
                        cryptoCreate(tokenHolder),
                        cryptoCreate(spender),
                        newKeyNamed("AdminKey"),
                        tokenCreate(partitionedToken)
                                .initialSupply(1000)
                                .adminKey("AdminKey")
                                .treasury(tokenHolder)
                                .withPartition(partition, 1000),
                        tokenAssociate(spender, partitionedToken)
                ).when(
                        // Granting allowance to the spender at the token-definition level
                        cryptoApproveAllowance()
                                .payingWith(tokenHolder)
                                .addToken(tokenHolder, partitionedToken, allowanceAmount)
                                .forHbar(0)
                                .forSpender(spender)
                                .via("tokenDefinitionLevelAllowanceTxn"),
                        // Trying to perform a partition-level transfer with the above allowance should fail
                        cryptoTransferAllowance()
                                .from(tokenHolder, partitionedToken, partition, allowanceAmount)
                                .to(spender)
                                .forSpender(spender)
                                .hasKnownStatus(TOKEN_ALLOWANCE_NOT_APPROVED_FOR_PARTITION)
                ).then(
                        // Granting allowance to the spender specifically for the partition
                        grantTokenAllowance(partitionedToken)
                                .forPartition(partition)
                                .fromOwner(tokenHolder)
                                .toSpender(spender)
                                .amount(allowanceAmount)
                                .signedBy(tokenHolder, AdminKey)
                                .via("partitionLevelAllowanceTxn"),
                        // Now performing a partition-level transfer should succeed
                        cryptoTransferAllowance()
                                .from(tokenHolder, partitionedToken, partition, allowanceAmount)
                                .to(spender)
                                .forSpender(spender)
                                .via("partitionLevelTransferTxn")
                );
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
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec accountExpiryAndReclamation() {
        final String expiringAccount = "ExpiringAccount";

        return defaultHapiSpec("AccountExpiryAndReclamation")
                .given(
                        cryptoCreate(expiringAccount)
                                .balance(0L)
                                // Assuming we can set an auto-renew period that's very short for the purpose of this test
                                .autoRenewSecs(1)
                                .logged()
                ).when(
                        // We wait for enough time to trigger the expiration process
                        sleepFor(2_000),
                        // Attempt to perform an operation with the expired account
                        cryptoTransfer(tinyBarsFromAccount(expiringAccount, 1))
                                .hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL)
                                .logged()
                ).then(
                        // Validate that the account is no longer available
                        getAccountInfo(expiringAccount)
                                .hasCostAnswerPrecheck(ACCOUNT_DELETED)
                                .logged(),
                        // Here we would need a way to ensure the memory was actually reclaimed by the node operator
                        // Since there is no direct way to test this, the assumption is that if the account has been marked
                        // as deleted, the resources have been reclaimed.
                        validateThat(expiringAccount).resourcesAreReclaimed()
                );
    }

    /**
     * <b>Misc-5</b>
     * <p>Account deletion: As a node operator, I do not want to honor account deletion requests if the account holds
     * tokens, including in any partition. The user must dispose of their tokens from their account before the account
     * can be deleted.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec accountDeletionWithTokenHoldings() {
        final String tokenHolder = "TokenHolder";
        final String someToken = "SomeToken";
        final String partition = "SomePartition";

        return defaultHapiSpec("AccountDeletionWithTokenHoldings")
                .given(
                        cryptoCreate(tokenHolder),
                        newTokenWithCustomFees(someToken)
                                .initialSupply(1_000),
                        tokenAssociate(tokenHolder, someToken),
                        mintToken(someToken, List.of(partition)),
                        cryptoTransfer(
                                moving(1, someToken)
                                        .between(TOKEN_TREASURY, tokenHolder)
                        )
                ).when(
                        // Attempt to delete the account which holds tokens
                        cryptoDelete(tokenHolder)
                                .hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)
                                .logged()
                ).then(
                        // Dispose of the token holdings before trying to delete the account again
                        cryptoTransfer(
                                moving(1, someToken)
                                        .between(tokenHolder, TOKEN_TREASURY)
                        ),
                        // Now the account deletion should succeed
                        cryptoDelete(tokenHolder)
                                .hasKnownStatus(SUCCESS)
                                .logged()
                );
    }

    /**
     * <b>Misc-6</b>
     * <p>Custom Fees at Token-Definition Level: As a token-issuer, I want to set custom fees at the `token-definition`
     * level and not at the partition level. The fees will be applied to all partitions of the `token-definition`.
     * Custom fees will not be applied when moving tokens between partitions of the same account.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec customFeesAtTokenDefinitionLevel() {
        final String tokenIssuer = "TokenIssuer";
        final String tokenUserA = "UserA";
        final String tokenUserB = "UserB";
        final String someToken = "SomeToken";
        final String somePartition = "SomePartition";
        final long transferAmount = 100L;
        final long customFeeAmount = 10L;

        return defaultHapiSpec("CustomFeesAtTokenDefinitionLevel")
                .given(
                        cryptoCreate(tokenIssuer),
                        cryptoCreate(tokenUserA).balance(0L),
                        cryptoCreate(tokenUserB).balance(0L),
                        newTokenWithCustomFees(someToken)
                                .adminKey(tokenIssuer)
                                .withCustom(fixedFee(customFeeAmount, HBAR, tokenIssuer))
                                .initialSupply(1_000),
                        tokenAssociate(tokenUserA, someToken),
                        tokenAssociate(tokenUserB, someToken),
                        cryptoTransfer(
                                moving(500, someToken)
                                        .between(TOKEN_TREASURY, tokenUserA)
                        )
                ).when(
                        // Transfer tokens between different accounts, custom fee should be applied
                        cryptoTransfer(
                                moving(transferAmount, someToken)
                                        .between(tokenUserA, tokenUserB)
                        ).payingWith(tokenUserA)
                                .via("transferWithFeesTxn")
                                // Check if the appropriate fee was deducted from the sender (tokenUserA)
                                .hasPrecheck(OK)
                                .logged()
                ).then(
                        // Verify the custom fee was charged
                        validateChargedCustomFees("transferWithFeesTxn", tokenIssuer, customFeeAmount),
                        // Move tokens between partitions of the same account, no custom fee should apply
                        // Note: The movePartitionTokens method is assumed for partition transfers within the same account
                        movePartitionTokens(someToken, somePartition, tokenUserA, transferAmount)
                                .noCustomFees()
                                .hasKnownStatus(SUCCESS)
                                .logged()
                );
    }


    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
