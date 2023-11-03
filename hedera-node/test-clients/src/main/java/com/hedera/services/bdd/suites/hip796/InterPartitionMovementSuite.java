package com.hedera.services.bdd.suites.hip796;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;

/**
 * A suite for user stories Move-1 through Move-5 from HIP-796.
 */
public class InterPartitionMovementSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(InterPartitionMovementSuite.class);

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                partitionMoveWithoutUserSignature(),
                partitionMoveWithUserSignature(),
                moveNftsBetweenPartitions(),
                moveNftsBetweenPartitionsWithUserSignature(),
                moveTokensViaSmartContractAsPartitionMoveKey());
    }

    /**
     * <b>Move-1</b>
     * <p>As a `partition-move-manager`, I want to move fungible tokens from one partition
     * (existing or deleted) to a different (new or existing) partition on the same user account,
     * without requiring a signature from the user holding the balance.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec partitionMoveWithoutUserSignature() {
        return defaultHapiSpec("PartitionMoveWithoutUserSignature")
                .given(
                        // Creating the token and partitions
                        newTokenWithFixedSupply("tokenA")
                                .initialSupply(10000)
                                .treasury("treasuryAccount"),

                        // Creating partition definitions
                        newPartition("partition1").forToken("tokenA"),
                        newPartition("partition2").forToken("tokenA"),

                        // Associating the `partition-move-manager`'s account with the token
                        // This may also include setting up necessary keys
                        associateAccount("partitionMoveManagerAccount", "tokenA"),
                        withRole("partitionMoveManagerAccount", "partition-move-manager"),

                        // Distributing tokens to a partition
                        // This assumes partitions can hold balances directly
                        // If not, the move should be from the user's balance within the partition
                        distributeTokens("tokenA", "partition1", 1000),

                        // Setting up any necessary keys and permissions
                        // This would likely involve assigning the partition-move-manager key
                        // or permission that allows them to move tokens without user signatures
                        withMoveManagerPrivileges("partitionMoveManagerAccount", "tokenA")
                )
                .when(
                        // The actual operation to move fungible tokens between partitions
                        moveTokensBetweenPartitions(
                                "partition1",
                                "partition2",
                                1000
                        ).via("partitionMoveTxn").signedBy("partition-move-manager")
                )
                .then(
                        // Verifying the move was successful
                        assertBalance("partition1").hasTokenBalance(0),
                        assertBalance("partition2").hasTokenBalance(1000),

                        // Optionally verifying the transaction record
                        // to ensure it was executed as expected
                        assertTransaction("partitionMoveTxn")
                                .hasKnownStatus(SUCCESS)
                                .hasMemo("[Move-1] Moving 1000 tokens from partition1 to partition2")
                );
    }

    /**
     * <b>Move-2</b>
     * <p>As a `partition-move-manager`, I want to move fungible tokens from one partition
     * (existing or deleted) to a different (new or existing) partition on a different user account,
     * but requiring a signature from the user's account being debited.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec partitionMoveWithUserSignature() {
        return defaultHapiSpec("PartitionMoveWithUserSignature")
                .given(
                        // Token and partitions setup
                        newTokenWithFixedSupply("tokenB")
                                .initialSupply(10000)
                                .treasury("treasuryAccount"),

                        newPartition("partition3").forToken("tokenB"),
                        newPartition("partition4").forToken("tokenB"),

                        // Associating user accounts with the token
                        associateAccount("userAccount1", "tokenB"),
                        associateAccount("userAccount2", "tokenB"),

                        // Assigning roles and permissions
                        withRole("partitionMoveManagerAccount", "partition-move-manager"),
                        withRole("userAccount1", "signatory"),

                        // Distributing tokens to the user's partition
                        distributeTokens("tokenB", "partition3", "userAccount1", 5000),

                        // Move manager permissions setup
                        withMoveManagerPrivileges("partitionMoveManagerAccount", "tokenB")
                )
                .when(
                        // Move operation with signature from the debited user's account
                        moveTokensBetweenPartitions(
                                "partition3",
                                "partition4",
                                2500
                        ).via("partitionMoveTxn")
                                .signedBy("partitionMoveManagerAccount", "userAccount1")
                )
                .then(
                        // Assertions to ensure the operation succeeded
                        assertBalance("partition3", "userAccount1").hasTokenBalance(2500),
                        assertBalance("partition4", "userAccount2").hasTokenBalance(2500),

                        // Confirming the transaction was signed by the required parties
                        assertTransaction("partitionMoveTxn")
                                .hasKnownStatus(SUCCESS)
                                .hasSignatories("partitionMoveManagerAccount", "userAccount1")
                                .hasMemo("[Move-2] Moving 2500 tokens from userAccount1's partition3 to userAccount2's partition4")
                );
    }

    /**
     * <b>Move-3</b>
     * <p>As a `partition-move-manager`, I want to move non-fungible tokens from one partition
     * (existing or deleted) to another (new or existing) partition on the same user account,
     * without requiring a signature from the user.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiApiSpec moveNftsBetweenPartitions() {
        return HapiApiSpec.defaultHapiSpec("MoveNftsBetweenPartitions")
                .given(
                        newKeyNamed("PartitionMoveManager"),
                        cryptoCreate("tokenOwner"),
                        cryptoCreate("partitionManager").key("PartitionMoveManager"),
                        tokenCreate("SomeToken")
                                .initialSupply(0)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE),
                        mintToken("SomeToken", List.of(metadata("first"), metadata("second"))),
                        tokenAssociate("tokenOwner", "SomeToken"),
                        cryptoTransfer(
                                movingUnique("SomeToken", 1L, 2L).between("treasury", "tokenOwner")
                        ),
                        // Assuming there is a way to create partitions, though it's not part of HAPI yet.
                        // These methods are speculative and just for example purposes.
                        createPartition("PartitionOne").forToken("SomeToken"),
                        createPartition("PartitionTwo").forToken("SomeToken"),
                        tokenAssociate("tokenOwner", "PartitionOne", "PartitionTwo")
                ).when(
                        cryptoApproveAllowance()
                                .payingWith("partitionManager")
                                .addNftAllowance("tokenOwner", "SomeToken", serialNos(1L, 2L), "partitionManager")
                                .signedBy("tokenOwner", "partitionManager"),
                        moveNFT("SomeToken", 1L).toPartition("PartitionTwo").via("partitionManager"),
                        moveNFT("SomeToken", 2L).toPartition("PartitionOne").via("partitionManager")
                ).then(
                        getTokenNftInfo("SomeToken", 1L)
                                .hasExpectedPartition("PartitionTwo"),
                        getTokenNftInfo("SomeToken", 2L)
                                .hasExpectedPartition("PartitionOne"),
                        validatePartitionBalance("PartitionOne", "SomeToken").hasNFTs(2L),
                        validatePartitionBalance("PartitionTwo", "SomeToken").hasNFTs(1L)
                );
    }

    /**
     * <b>Move-4</b>
     * <p>As a `partition-move-manager`, I want to move non-fungible tokens from one partition
     * (existing or deleted) to another (new or existing) partition on a different user account,
     * but requiring a signature from the user.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec moveNftsBetweenPartitionsWithUserSignature() {
        final String partitionMoveManager = "partitionMoveManager";
        final String sourceAccount = "sourceAccount";
        final String destinationAccount = "destinationAccount";
        final String someToken = "SomeToken";
        final long serialNumber = 1L; // example serial number

        return defaultHapiSpec("MoveNftsBetweenPartitionsWithUserSignature")
                .given(
                        newKeyNamed(partitionMoveManager),
                        cryptoCreate(sourceAccount),
                        cryptoCreate(destinationAccount),
                        tokenCreate(someToken)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0),
                        mintToken(someToken, List.of(metadata("example"))),
                        tokenAssociate(sourceAccount, someToken),
                        tokenAssociate(destinationAccount, someToken),
                        cryptoTransfer(
                                movingUnique(someToken, serialNumber).between(TREASURY, sourceAccount)
                        ),
                        // Create partitions and associate them with the accounts
                        createPartition("PartitionA").forToken(someToken).onAccount(sourceAccount),
                        createPartition("PartitionB").forToken(someToken).onAccount(destinationAccount)
                ).when(
                        // Move NFT from PartitionA on sourceAccount to PartitionB on destinationAccount
                        // Requires signature from sourceAccount
                        moveNFT(someToken, serialNumber)
                                .fromPartition("PartitionA").onAccount(sourceAccount)
                                .toPartition("PartitionB").onAccount(destinationAccount)
                                .via(partitionMoveManager)
                                .signedBy(sourceAccount, partitionMoveManager)
                ).then(
                        // Validate the NFT is now associated with the correct partition and account
                        getAccountNfts(destinationAccount)
                                .hasNfts(1),
                        getAccountInfo(destinationAccount)
                                .hasToken(someToken)
                                .logged(),
                        // Ensure source account's partition no longer has the NFT
                        getAccountNfts(sourceAccount)
                                .hasNoNfts()
                );
    }

    /**
     * <b>Move-5</b>
     * <p>As a `token-administrator` smart contract, I want to move tokens from one partition
     * to another, in the same account or to a different account, if my contract ID is specified
     * as the `partition-move-key`, and all other conditions are met.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec moveTokensViaSmartContractAsPartitionMoveKey() {
        final String smartContractAdmin = "smartContractAdmin";
        final String sourceAccount = "sourceAccount";
        final String destinationAccount = "destinationAccount";
        final String someToken = "SomeToken";

        return defaultHapiSpec("MoveTokensViaSmartContractAsPartitionMoveKey")
                .given(
                        fileCreate("smartContractByteCode").path(PATH_TO_SMART_CONTRACT_BYTECODE),
                        contractCreate(smartContractAdmin).bytecode("smartContractByteCode"),
                        cryptoCreate(sourceAccount),
                        cryptoCreate(destinationAccount),
                        tokenCreate(someToken),
                        tokenAssociate(sourceAccount, someToken),
                        tokenAssociate(destinationAccount, someToken),
                        cryptoTransfer(moving(100, someToken).between(TREASURY, sourceAccount)),
                        // Create partitions and associate them with the accounts
                        createPartition("PartitionA").forToken(someToken).onAccount(sourceAccount),
                        createPartition("PartitionB").forToken(someToken).onAccount(destinationAccount),
                        // Set smart contract as partition move key
                        updateToken(someToken).partitionMoveKey(smartContractAdmin)
                ).when(
                        // Smart contract moves tokens from PartitionA on sourceAccount to PartitionB on destinationAccount
                        contractCall(smartContractAdmin, "moveTokensBetweenPartitions", someToken, 100)
                                .sending(100)
                                .via("tokenMoveTransaction")
                ).then(
                        // Validate the tokens are now associated with the correct partition and account
                        getAccountBalance(destinationAccount)
                                .hasTokenBalance(someToken, 100),
                        getAccountBalance(sourceAccount)
                                .hasTokenBalance(someToken, 0),
                        // Confirm transaction status
                        getTxnRecord("tokenMoveTransaction").logged()
                );
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
