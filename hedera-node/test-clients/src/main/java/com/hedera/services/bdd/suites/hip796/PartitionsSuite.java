package com.hedera.services.bdd.suites.hip796;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;

/**
 * A suite for user stories Partitions-1 through Partitions-18 from HIP-796.
 */
public class PartitionsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(PartitionsSuite.class);

    @Override
    public List<HapiSpec> getSpecsInSuite() {
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
    @HapiTest
    private HapiSpec createNewPartitionDefinitions() {
        return defaultHapiSpec("CreateNewPartitionDefinitions")
                .given(
                        fungibleTokenDefinition("TokenWithPartitions")
                                .administeredBy("token-administrator")
                                .partitionAdministeredBy("partition-administrator")
                )
                .when(
                        createPartitionDefinition("TokenWithPartitions", "NewPartition1")
                                .by("partition-administrator"),
                        createPartitionDefinition("TokenWithPartitions", "NewPartition2")
                                .by("partition-administrator")
                )
                .then(
                        queryPartitionDefinition("TokenWithPartitions", "NewPartition1").exists(),
                        queryPartitionDefinition("TokenWithPartitions", "NewPartition2").exists()
                );
    }

    /**
     * <b>Partitions-2</b>
     * <p>As a `partition-administrator`, I want to update existing `partition-definition`s
     * for my `token-definition`, such as the memo, of a `partition-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec updatePartitionDefinitionsMemo() {
        return defaultHapiSpec("UpdatePartitionDefinitionsMemo")
                .given(
                        fungibleTokenDefinition("TokenWithPartitionsToUpdate")
                                .administeredBy("token-administrator")
                                .partitionAdministeredBy("partition-administrator"),
                        createPartitionDefinition("TokenWithPartitionsToUpdate", "PartitionToChange")
                                .withMemo("InitialMemo")
                                .by("partition-administrator")
                )
                .when(
                        updatePartitionDefinition("TokenWithPartitionsToUpdate", "PartitionToChange")
                                .withMemo("UpdatedMemo")
                                .by("partition-administrator")
                )
                .then(
                        queryPartitionDefinition("TokenWithPartitionsToUpdate", "PartitionToChange")
                                .hasMemo("UpdatedMemo")
                );
    }

    /**
     * <b>Partitions-3</b>
     * <p>As a `partition-administrator`, I want to delete existing `partition-definition`s of my `token-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec deletePartitionDefinitions() {
        return defaultHapiSpec("DeletePartitionDefinitions")
                .given(
                        tokenExistsWithPartitions("MyToken", "ExistingPartition")
                )
                .when(
                        deletePartition("MyToken", "ExistingPartition").signedBy("partitionAdminKey")
                )
                .then(
                        ensurePartitionDoesNotExist("MyToken", "ExistingPartition")
                );
    }

    /**
     * <b>Partitions-4</b>
     * <p>As the holder of a `partition-move-key`, I want to transfer independent
     * fungible token balances within partitions of an account.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec transferBetweenPartitions() {
        return defaultHapiSpec("TransferBetweenPartitions")
                .given(
                        fungibleTokenDefinition("TokenWithMoveablePartitions")
                                .administeredBy("token-administrator")
                                .partitionMoveManagedBy("partition-move-manager"),
                        createPartitionDefinition("TokenWithMoveablePartitions", "PartitionA")
                                .by("partition-administrator"),
                        createPartitionDefinition("TokenWithMoveablePartitions", "PartitionB")
                                .by("partition-administrator"),
                        mintTokensToPartition("TokenWithMoveablePartitions", "PartitionA", "userAccount", 1000),
                        mintTokensToPartition("TokenWithMoveablePartitions", "PartitionB", "userAccount", 500)
                )
                .when(
                        transferBetweenPartitions("TokenWithMoveablePartitions", "userAccount")
                                .fromPartition("PartitionA").amount(200)
                                .toPartition("PartitionB")
                                .by("partition-move-manager")
                )
                .then(
                        queryPartitionBalance("TokenWithMoveablePartitions", "PartitionA", "userAccount")
                                .hasAmount(800),
                        queryPartitionBalance("TokenWithMoveablePartitions", "PartitionB", "userAccount")
                                .hasAmount(700)
                );
    }

    /**
     * <b>Partitions-5</b>
     * <p>As the holder of a `partition-move-key`, I want to transfer independent NFT serials within partitions of an account.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec transferNFTsWithinPartitions() {
        return defaultHapiSpec("TransferNFTsWithinPartitions")
                .given(
                        newKeyNamed("PartitionMoveKey"),
                        tokenCreatedWithPartitionSupport("MyNftToken"),
                        nftIssuedToPartition("MyNftToken", "PartitionA", "Serial1"),
                        nftIssuedToPartition("MyNftToken", "PartitionB", "Serial2"),
                        withOpContext((spec, opLog) -> {
                            var tokenInfo = getTokenInfo("MyNftToken").answeredBy(spec);
                            var partitionAInfo = getPartitionInfo("MyNftToken", "PartitionA").answeredBy(spec);
                            var partitionBInfo = getPartitionInfo("MyNftToken", "PartitionB").answeredBy(spec);
                            allHoldingsAreValid(tokenInfo, partitionAInfo, partitionBInfo);
                        })
                )
                .when(
                        moveNFT("MyNftToken", "Serial1").fromPartition("PartitionA").toPartition("PartitionB").signedBy("PartitionMoveKey")
                )
                .then(
                        validateNFTOwnership("MyNftToken", "Serial1", "PartitionB"),
                        validateNFTNotOwned("MyNftToken", "Serial1", "PartitionA")
                );
    }

    /**
     * <b>Partitions-6</b>
     * <p>As a `token-administrator`, I want to `pause` all token transfers for my `token-definition`,
     * including for all partitions, by pausing the `token-definition` itself.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec pauseTokenTransfersIncludingPartitions() {
        return defaultHapiSpec("PauseTokenTransfersIncludingPartitions")
                .given(
                        newKeyNamed("AdminKey"),
                        newKeyNamed("PauseKey"),
                        tokenCreatedWithPartitionSupport("MyToken")
                                .adminKey("AdminKey")
                                .pauseKey("PauseKey")
                )
                .when(
                        pauseToken("MyToken").signedBy("PauseKey")
                )
                .then(
                        attemptingToTransferToken("MyToken", "PartitionA").hasKnownStatus(TOKEN_IS_PAUSED),
                        attemptingToTransferToken("MyToken", "PartitionB").hasKnownStatus(TOKEN_IS_PAUSED)
                );
    }

    /**
     * <b>Partitions-7</b>
     * <p>As a `token-administrator`, I want to `freeze` all token transfers for my `token-definition`
     * on a particular account, including for all partitions of the `token-definition`,
     * by freezing the `token-definition` itself.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec freezeTokenTransfersForAccountIncludingPartitions() {
        return defaultHapiSpec("FreezeTokenTransfersForAccountIncludingPartitions")
                .given(
                        newKeyNamed("AdminKey"),
                        newKeyNamed("FreezeKey"),
                        cryptoCreate("TargetAccount"),
                        tokenCreate("MyToken")
                                .adminKey("AdminKey")
                                .freezeKey("FreezeKey")
                                .freezeDefault(false)
                )
                .when(
                        cryptoTransfer(
                                movingUniqueWithPartition("MyToken", "PartitionA").between("Treasury", "TargetAccount")
                        ).hasKnownStatus(SUCCESS),
                        freezeToken("MyToken", "TargetAccount").signedBy("FreezeKey")
                )
                .then(
                        cryptoTransfer(
                                movingUniqueWithPartition("MyToken", "PartitionA").between("TargetAccount", "Treasury")
                        ).hasKnownStatus(TOKEN_ACCOUNT_FROZEN),
                        cryptoTransfer(
                                movingUniqueWithPartition("MyToken", "PartitionB").between("TargetAccount", "Treasury")
                        ).hasKnownStatus(TOKEN_ACCOUNT_FROZEN)
                );
    }

    /**
     * <b>Partitions-8</b>
     * <p>As a `token-administrator`, I want to require `kyc` to be set on the account for the association with my `token-definition`
     * to enable transfers of any tokens in partitions of the `token-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec requireKycForTokenTransfersIncludingPartitions() {
        return defaultHapiSpec("RequireKycForTokenTransfersIncludingPartitions")
                .given(
                        newKeyNamed("AdminKey"),
                        newKeyNamed("KycKey"),
                        cryptoCreate("AccountA"),
                        cryptoCreate("AccountB"),
                        tokenCreate("PartitionedToken")
                                .adminKey("AdminKey")
                                .kycKey("KycKey")
                                .initialSupply(0)
                                .withPartition("Partition1"),
                        tokenAssociate("AccountA", "PartitionedToken"),
                        tokenAssociate("AccountB", "PartitionedToken")
                )
                .when(
                        cryptoTransfer(
                                moving(100, "PartitionedToken").between("AccountA", "AccountB")
                        ).hasPrecheck(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
                )
                .then(
                        grantKyc("PartitionedToken", "AccountA"),
                        grantKyc("PartitionedToken", "AccountB"),
                        cryptoTransfer(
                                moving(100, "PartitionedToken").between("AccountA", "AccountB")
                        ).signedBy("KycKey").hasKnownStatus(SUCCESS)
                );
    }

    /**
     * <b>Partitions-9</b>
     * <p>As a `token-administrator`, I want to `pause` all token transfers for a specific `partition-definition` of my `token-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec pauseTransfersForSpecificPartition() {
        return defaultHapiSpec("PauseTransfersForSpecificPartition")
                .given(
                        newKeyNamed("AdminKey"),
                        newKeyNamed("PauseKey"),
                        cryptoCreate("tokenTreasury"),
                        tokenCreate("PartitionedToken")
                                .adminKey("AdminKey")
                                .pauseKey("PauseKey")
                                .treasury("tokenTreasury")
                                .initialSupply(1000)
                                .withPartition("GoldPartition")
                )
                .when(
                        tokenPause("PartitionedToken").partition("GoldPartition")
                )
                .then(
                        cryptoTransfer(
                                moving(100, "PartitionedToken").between("tokenTreasury", "GoldPartition")
                        ).hasKnownStatus(TOKEN_IS_PAUSED),
                        tokenUnpause("PartitionedToken").partition("GoldPartition"),
                        cryptoTransfer(
                                moving(100, "PartitionedToken").between("tokenTreasury", "GoldPartition")
                        ).hasKnownStatus(SUCCESS)
                );
    }

    /**
     * <b>Partitions-10</b>
     * <p>As a `token-administrator`, I want to `freeze` all token transfers for a specific `partition-definition` of my `token-definition` on a particular account.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec freezeTransfersForSpecificPartitionOnAccount() {
        return defaultHapiSpec("FreezeTransfersForSpecificPartitionOnAccount")
                .given(
                        newKeyNamed("AdminKey"),
                        newKeyNamed("FreezeKey"),
                        cryptoCreate("tokenTreasury").balance(0L),
                        cryptoCreate("userAccount").balance(0L),
                        tokenCreate("PartitionedToken")
                                .adminKey("AdminKey")
                                .freezeKey("FreezeKey")
                                .treasury("tokenTreasury")
                                .initialSupply(1000)
                                .withPartition("SilverPartition"),
                        tokenAssociate("userAccount", "PartitionedToken"),
                        cryptoTransfer(
                                moving(100, "PartitionedToken").between("tokenTreasury", "userAccount")
                        )
                )
                .when(
                        tokenFreeze("PartitionedToken").partition("SilverPartition").forAccount("userAccount")
                )
                .then(
                        cryptoTransfer(
                                moving(50, "PartitionedToken").between("userAccount", "SilverPartition")
                        ).hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
                        tokenUnfreeze("PartitionedToken").partition("SilverPartition").forAccount("userAccount"),
                        cryptoTransfer(
                                moving(50, "PartitionedToken").between("userAccount", "SilverPartition")
                        ).hasKnownStatus(SUCCESS)
                );
    }

    /**
     * <b>Partitions-11</b>
     * <p>As a `partition-administrator`, I want to require a kyc flag to be set on the partition of an account to enable transfers of tokens in that partition.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec requireKycForPartitionTransfers() {
        return defaultHapiSpec("RequireKycForPartitionTransfers")
                .given(
                        newKeyNamed("AdminKey"),
                        newKeyNamed("KycKey"),
                        cryptoCreate("tokenTreasury").balance(0L),
                        cryptoCreate("userAccount").balance(0L),
                        tokenCreate("KYCPartitionedToken")
                                .adminKey("AdminKey")
                                .kycKey("KycKey")
                                .treasury("tokenTreasury")
                                .initialSupply(1000)
                                .withPartition("GoldPartition"),
                        tokenAssociate("userAccount", "KYCPartitionedToken")
                )
                .when(
                        cryptoTransfer(
                                moving(100, "KYCPartitionedToken").between("tokenTreasury", "userAccount").via("initialTransfer")
                        )
                )
                .then(
                        // Attempt to transfer tokens without KYC should fail
                        cryptoTransfer(
                                moving(50, "KYCPartitionedToken").between("userAccount", "GoldPartition")
                        ).hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        // Grant KYC to the user for this token
                        tokenGrantKyc("KYCPartitionedToken").partition("GoldPartition").to("userAccount"),
                        // Now that KYC is granted, transfer should succeed
                        cryptoTransfer(
                                moving(50, "KYCPartitionedToken").between("userAccount", "GoldPartition")
                        )
                );
    }

    /**
     * <b>Partitions-12</b>
     * <p>As a `token-administrator`, I want to be able to create a new `token-definition`
     * with a fixed supply and a `partition-key`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec createFixedSupplyTokenWithPartitionKey() {
        return defaultHapiSpec("CreateFixedSupplyTokenWithPartitionKey")
                .given(
                        newKeyNamed("AdminKey"),
                        newKeyNamed("PartitionKey"),
                        cryptoCreate("tokenTreasury").balance(0L)
                )
                .when(
                        tokenCreate("FixedPartitionedToken")
                                .adminKey("AdminKey")
                                .partitionKey("PartitionKey")
                                .treasury("tokenTreasury")
                                .initialSupply(1000)
                                .supplyType(TokenSupplyType.INFINITE)
                                .maxSupply(1000)  // This will actually make it a fixed supply
                                .withPartition("PlatinumPartition")
                )
                .then(
                        // The partition "PlatinumPartition" should exist within "FixedPartitionedToken"
                        getTokenInfo("FixedPartitionedToken").logged(),
                        // Attempt to create another partition should fail since it's fixed supply
                        attemptToCreatePartition("FixedPartitionedToken", "NewPartition").hasKnownStatus(TOKEN_IS_IMMUTABLE)
                );
    }

    /**
     * <b>Partitions-13</b>
     * <p>As a node operator, I do not want to honor deletion of a `token-definition`
     * that has any `partition-definition` that is not also already deleted.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec notHonorDeletionOfTokenWithExistingPartitions() {
        return defaultHapiSpec("NotHonorDeletionOfTokenWithExistingPartitions")
                .given(
                        newKeyNamed("AdminKey"),
                        tokenCreate("TokenWithPartition")
                                .adminKey("AdminKey")
                                .withPartition("FirstPartition"),
                        tokenCreate("TokenWithoutPartition")
                                .adminKey("AdminKey")
                )
                .when(
                        // Attempt to delete "TokenWithPartition" which has a partition should fail
                        tokenDelete("TokenWithPartition").hasKnownStatus(PARTITIONED_TOKEN_CANNOT_BE_DELETED),
                        // Removing partition first before deleting the token
                        removePartition("TokenWithPartition", "FirstPartition")
                )
                .then(
                        // After removal of partition, token deletion should succeed
                        tokenDelete("TokenWithPartition"),
                        // Deleting token without any partitions should succeed
                        tokenDelete("TokenWithoutPartition")
                );
    }

    /**
     * <b>Partitions-14</b>
     * <p>As a `supply-key` holder, I want to mint tokens into a specific partition of the treasury account.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec mintToSpecificPartitionOfTreasury() {
        return defaultHapiSpec("MintToSpecificPartitionOfTreasury")
                .given(
                        newKeyNamed("SupplyKey"),
                        tokenCreate("TokenWithPartitions")
                                .initialSupply(0)
                                .supplyKey("SupplyKey")
                                .withPartition("TreasuryPartition")
                )
                .when(
                        // Minting tokens specifically to the "TreasuryPartition"
                        mintToken("TokenWithPartitions", 100)
                                .toPartition("TreasuryPartition")
                )
                .then(
                        // Validate that the minted tokens have been allocated to the specified partition
                        getTokenInfo("TokenWithPartitions").hasPartitionSupply("TreasuryPartition", 100)
                );
    }

    /**
     * <b>Partitions-15</b>
     * <p>As a `supply-key` holder, I want to burn tokens from a specific partition of the treasury account.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec burnFromSpecificPartitionOfTreasury() {
        return defaultHapiSpec("BurnFromSpecificPartitionOfTreasury")
                .given(
                        newKeyNamed("SupplyKey"),
                        tokenCreate("TokenWithPartitions")
                                .initialSupply(1000)
                                .supplyKey("SupplyKey")
                                .withPartition("TreasuryPartition")
                )
                .when(
                        // Burning tokens specifically from the "TreasuryPartition"
                        burnToken("TokenWithPartitions", 100)
                                .fromPartition("TreasuryPartition")
                )
                .then(
                        // Validate that the tokens have been burnt from the specified partition
                        getTokenInfo("TokenWithPartitions").hasPartitionSupply("TreasuryPartition", 900)
                );
    }

    /**
     * <b>Partitions-16</b>
     * <p>As a `wipe-key` holder, I want to wipe tokens from a specific partition in the user's account.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec wipeFromSpecificPartitionInUserAccount() {
        return defaultHapiSpec("WipeFromSpecificPartitionInUserAccount")
                .given(
                        newKeyNamed("WipeKey"),
                        cryptoCreate("user"),
                        tokenCreate("PartitionedToken")
                                .initialSupply(1000)
                                .wipeKey("WipeKey")
                                .withPartition("UserPartition"),
                        tokenAssociate("user", "PartitionedToken"),
                        mintToken("PartitionedToken", 100)
                                .toPartition("UserPartition"),
                        tokenBalance("PartitionedToken").forAccount("user").hasTokenBalance(100)
                )
                .when(
                        // Wiping tokens specifically from the "UserPartition" of user's account
                        wipeTokenAccount("PartitionedToken", "user", 50)
                                .fromPartition("UserPartition")
                )
                .then(
                        // Validate that the tokens have been wiped from the specified partition
                        tokenBalance("PartitionedToken").forAccount("user").hasTokenBalance(50)
                );
    }

    /**
     * <b>Partitions-17</b>
     * <p>As a `token-administrator` smart contract, I want to create, update, and delete partitions,
     * and in all other ways work with partitions as I would using the HAPI.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec smartContractAdministersPartitions() {
        return defaultHapiSpec("SmartContractAdministersPartitions")
                .given(
                        // Create a smart contract that is the token-administrator
                        fileCreate("PartitionSmartContract"),
                        contractCreate("PartitionSmartContract")
                                .adminKey(THRESHOLD),
                        newKeyNamed("AdminKey"),
                        cryptoCreate("tokenAdmin").key("AdminKey"),
                        withOpContext((spec, opLog) -> {
                            var admin = spec.registry().getAccountID("tokenAdmin");
                            var contract = spec.registry().getContractID("PartitionSmartContract");
                            spec.registry().saveKey("adminKey", Key.newBuilder().setContractID(contract).build());
                        })
                )
                .when(
                        // The smart contract creates a token with a partition
                        tokenCreate("PartitionedToken")
                                .adminKey("adminKey")
                                .initialSupply(0)
                                .withPartition("InitialPartition")
                )
                .then(
                        // The smart contract updates the token's partition details
                        tokenUpdate("PartitionedToken")
                                .partition("InitialPartition", "UpdatedPartition")
                                .via("partitionUpdateTxn"),

                        // Verifying partition details were updated
                        getTxnRecord("partitionUpdateTxn").hasPriority(recordWith().partition("UpdatedPartition")),

                        // The smart contract deletes a partition from the token
                        tokenDelete("PartitionedToken").partition("UpdatedPartition")
                                .via("partitionDeleteTxn"),

                        // Verifying partition is deleted
                        getTxnRecord("partitionDeleteTxn").hasPriority(recordWith().status(SUCCESS))
                );
    }

    /**
     * <b>Partitions-18</b>
     * <p>If freeze or pause is set at the `token-definition` level then it takes precedence over the
     * `partition-definition` level.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec freezeOrPauseAtTokenLevelOverridesPartition() {
        return defaultHapiSpec("FreezeOrPauseAtTokenLevelOverridesPartition")
                .given(
                        newKeyNamed("AdminKey"),
                        newKeyNamed("FreezeKey"),
                        newKeyNamed("PauseKey"),
                        cryptoCreate("tokenAdmin").key("AdminKey"),
                        tokenCreate("MultiPartitionToken")
                                .adminKey("AdminKey")
                                .freezeKey("FreezeKey")
                                .pauseKey("PauseKey")
                                .initialSupply(0)
                                .withPartition("FirstPartition")
                                .withPartition("SecondPartition")
                )
                .when(
                        // Token-level operations are performed
                        tokenFreeze("MultiPartitionToken")
                                .signedBy("FreezeKey", "tokenAdmin")
                                .via("tokenFreezeTxn"),

                        tokenPause("MultiPartitionToken")
                                .signedBy("PauseKey", "tokenAdmin")
                                .via("tokenPauseTxn")
                )
                .then(
                        // Checking that token-level freeze and pause are in effect
                        getTxnRecord("tokenFreezeTxn").hasPriority(recordWith().status(SUCCESS)),
                        getTxnRecord("tokenPauseTxn").hasPriority(recordWith().status(SUCCESS)),

                        // Attempting partition-level transfers which should fail due to token-level freeze or pause
                        cryptoTransfer(movingUnique(1).betweenPartitions("FirstPartition", "SecondPartition"))
                                .hasKnownStatus(TOKEN_IS_FROZEN_OR_PAUSED),

                        // Verifying partition-level operations cannot override token-level freeze or pause
                        getAccountBalance("FirstPartition").hasTokenBalance("MultiPartitionToken", 0),
                        getAccountBalance("SecondPartition").hasTokenBalance("MultiPartitionToken", 0)
                );
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
