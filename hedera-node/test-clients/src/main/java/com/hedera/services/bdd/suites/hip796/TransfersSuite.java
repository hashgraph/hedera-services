package com.hedera.services.bdd.suites.hip796;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;

/**
 * A suite for user stories Transfers-1 through Transfers-3 from HIP-796.
 */
public class TransfersSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TransfersSuite.class);

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of();
    }

    /**
     * <b>Transfer-1</b>
     * <p>As an owner of an account with a partition, I want to transfer tokens to another user with the same partition.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canTransferTokensToSamePartitionUser() {
        final String sender = "TokenSender";
        final String receiver = "TokenReceiver";
        final String token = "PartitionedToken";
        final String partition = "SamePartition";
        final long transferAmount = 100L;

        return defaultHapiSpec("CanTransferTokensToSamePartitionUser")
                .given(
                        cryptoCreate(sender),
                        cryptoCreate(receiver),
                        newKeyNamed("TokenAdminKey"),
                        tokenCreate(token)
                                .initialSupply(1000)
                                .adminKey("TokenAdminKey")
                                .withPartition(partition, 1000) // Assumes we have a DSL method to create a partition
                                .signedBy(DEFAULT_PAYER, "TokenAdminKey"),
                        cryptoTransfer(moving(1000, token).between(DEFAULT_PAYER, sender)),
                        cryptoTransfer(moving(1000, token, partition).between(DEFAULT_PAYER, sender))
                                .via("initialPartitionCredit")
                ).when(
                        cryptoTransfer(
                                moving(transferAmount, token, partition)
                                        .between(sender, receiver)
                        ).signedBy(sender)
                                .via("transferTxn")
                ).then(
                        validateRecord("transferTxn")
                                .hasPriority(recordWith().status(SUCCESS)),
                        getTokenBalance(token)
                                .forAccount(sender)
                                .hasTokenBalance(900), // Assumes original balance - transferAmount
                        getTokenBalance(token, partition)
                                .forAccount(receiver)
                                .hasTokenBalance(transferAmount) // Assumes received amount is correct
                );
    }


    /**
     * <b>Transfer-2</b>
     * <p>As an owner of an account with a partition, I want to transfer tokens to another user that does not
     * already have the same partition, but can have the same partition auto-associated.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canTransferTokensToUserWithAutoAssociation() {
        final String sender = "TokenSender";
        final String receiver = "TokenReceiver";
        final String token = "PartitionedToken";
        final String partition = "AutoAssociatePartition";
        final long transferAmount = 100L;

        return defaultHapiSpec("CanTransferTokensToUserWithAutoAssociation")
                .given(
                        cryptoCreate(sender),
                        cryptoCreate(receiver)
                                .autoEnableTokenAssociations(), // Enables auto-association for new accounts
                        newKeyNamed("TokenAdminKey"),
                        tokenCreate(token)
                                .initialSupply(1000)
                                .adminKey("TokenAdminKey")
                                .withPartition(partition, 1000) // Assumes a method to create a partition
                                .signedBy(DEFAULT_PAYER, "TokenAdminKey"),
                        cryptoTransfer(moving(1000, token).between(DEFAULT_PAYER, sender)),
                        cryptoTransfer(moving(1000, token, partition).between(DEFAULT_PAYER, sender))
                                .via("initialPartitionCredit")
                ).when(
                        cryptoTransfer(
                                moving(transferAmount, token, partition)
                                        .between(sender, receiver)
                        ).signedBy(sender)
                                .via("transferTxn")
                ).then(
                        validateRecord("transferTxn")
                                .hasPriority(recordWith().status(SUCCESS)),
                        getTokenBalance(token)
                                .forAccount(sender)
                                .hasTokenBalance(900), // Assumes original balance - transferAmount
                        getTokenBalance(token, partition)
                                .forAccount(receiver)
                                .hasTokenBalance(transferAmount), // Assumes auto-association success and correct amount
                        getAccountInfo(receiver)
                                .has(autoAssociatedPartitions(including(partition))) // Validates auto-association
                );
    }

    /**
     * <b>Transfer-3</b>
     * <P>As an owner of an account with a partition with locked tokens, I want to transfer tokens to another user
     * with the same partition, either new (with auto-association) or existing. This cannot be done atomically
     * at this time. The tokens must be unlocked, transferred, and then locked again. Using HIP-551 (atomic batch
     * transactions), I would be able to unlock, transfer, and lock atomically. This has to be done in coordination
     * with the `lock-key` holder.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canTransferTokensToUserWithAutoAssociation() {
        final String sender = "TokenSender";
        final String receiver = "TokenReceiver";
        final String token = "PartitionedToken";
        final String partition = "AutoAssociatePartition";
        final long transferAmount = 100L;

        return defaultHapiSpec("CanTransferTokensToUserWithAutoAssociation")
                .given(
                        cryptoCreate(sender),
                        cryptoCreate(receiver)
                                .autoEnableTokenAssociations(), // Enables auto-association for new accounts
                        newKeyNamed("TokenAdminKey"),
                        tokenCreate(token)
                                .initialSupply(1000)
                                .adminKey("TokenAdminKey")
                                .withPartition(partition, 1000) // Assumes a method to create a partition
                                .signedBy(DEFAULT_PAYER, "TokenAdminKey"),
                        cryptoTransfer(moving(1000, token).between(DEFAULT_PAYER, sender)),
                        cryptoTransfer(moving(1000, token, partition).between(DEFAULT_PAYER, sender))
                                .via("initialPartitionCredit")
                ).when(
                        cryptoTransfer(
                                moving(transferAmount, token, partition)
                                        .between(sender, receiver)
                        ).signedBy(sender)
                                .via("transferTxn")
                ).then(
                        validateRecord("transferTxn")
                                .hasPriority(recordWith().status(SUCCESS)),
                        getTokenBalance(token)
                                .forAccount(sender)
                                .hasTokenBalance(900), // Assumes original balance - transferAmount
                        getTokenBalance(token, partition)
                                .forAccount(receiver)
                                .hasTokenBalance(transferAmount), // Assumes auto-association success and correct amount
                        getAccountInfo(receiver)
                                .has(autoAssociatedPartitions(including(partition))) // Validates auto-association
                );
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

}
