package com.hedera.services.bdd.suites.hip796;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;

/**
 * A suite for user stories Lock-1 through Lock-8 from HIP-796.
 */
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
     * locked is governed by: `0 <= number_of_tokens_to_be_locked <= x`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canLockSubsetOfUnlockedTokens() {
        final String lockKeyHolder = "lockKeyHolder";
        final String tokenHolder = "tokenHolder";
        final String theToken = "TheToken";
        final long totalUnlockedTokens = 1000L; // The `x` from the user story
        final long tokensToLock = 500L; // This should be within the range of `0 <= tokensToLock <= totalUnlockedTokens`

        return defaultHapiSpec("CanLockSubsetOfUnlockedTokens")
                .given(
                        newKeyNamed(lockKeyHolder),
                        cryptoCreate(tokenHolder),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        tokenCreate(theToken)
                                .initialSupply(totalUnlockedTokens)
                                .treasury(TOKEN_TREASURY)
                                .withCustom(lockKeyHolder, TokenKeyType.LOCK), // Assuming there is an enum for different key types
                        tokenAssociate(tokenHolder, theToken),
                        cryptoTransfer(
                                moving(totalUnlockedTokens, theToken)
                                        .between(TOKEN_TREASURY, tokenHolder)
                        )
                ).when(
                        tokenLock(theToken)
                                .locking(tokensToLock)
                                .forAccount(tokenHolder)
                                .signedBy(lockKeyHolder)
                                .via("tokenLockTxn")
                ).then(
                        getAccountInfo(tokenHolder)
                                .hasToken(theToken, totalUnlockedTokens - tokensToLock), // Check if the lock was successful
                        validateTransaction("tokenLockTxn")
                                .hasPrecheck(OK)
                                .hasPriority(recordWith().status(SUCCESS))
                );
    }

    /**
     * <b>Lock-2</b>
     * <p>As a `lock-key` holder, I want to lock a subset of the currently held unlocked fungible
     * tokens held by a user's account in a partition without requiring the user's signature.
     * If an account has `x` unlocked tokens, then the number of tokens that can be additionally
     * locked is governed by: `0 <= number_of_tokens_to_be_locked <= x`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canLockSubsetOfUnlockedTokensInPartition() {
        final String lockKeyHolder = "lockKeyHolder";
        final String tokenHolder = "tokenHolder";
        final String theToken = "TheToken";
        final String partition = "TokenPartition";
        final long totalUnlockedTokens = 1000L; // The `x` from the user story
        final long tokensToLock = 500L; // This should be within the range of `0 <= tokensToLock <= totalUnlockedTokens`

        return defaultHapiSpec("CanLockSubsetOfUnlockedTokensInPartition")
                .given(
                        newKeyNamed(lockKeyHolder),
                        cryptoCreate(tokenHolder),
                        cryptoCreate("tokenTreasury").balance(0L),
                        tokenCreate(theToken)
                                .initialSupply(totalUnlockedTokens)
                                .treasury("tokenTreasury")
                                .withCustom(lockKeyHolder, "lock"),
                        tokenAssociate(tokenHolder, theToken),
                        cryptoTransfer(moving(totalUnlockedTokens, theToken).between("tokenTreasury", tokenHolder)),
                        tokenPartitionCreate(theToken).partitionName(partition).treasury("tokenTreasury")
                ).when(
                        tokenLock(theToken)
                                .locking(tokensToLock)
                                .forAccount(tokenHolder)
                                .forPartition(partition)
                                .signedBy(lockKeyHolder)
                                .via("tokenLockTxn")
                ).then(
                        getAccountBalance(tokenHolder)
                                .hasTokenBalance(theToken, totalUnlockedTokens - tokensToLock, partition), // Check if the lock was successful within the partition
                        assertStatus(SUCCESS, "tokenLockTxn")
                );
    }

    /**
     * <b>Lock-3</b>
     * <p>As a `lock-key` holder, I want to unlock a subset of the currently held unpartitioned locked
     * fungible tokens held by a user's account without requiring the user's signature.
     * If an account has `x` locked tokens, then the number of tokens that can be additionally
     * unlocked is governed by: `0 <= number_of_locked_tokens <= x`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canUnlockSubsetOfLockedTokens() {
        final String lockKeyHolder = "lockKeyHolder";
        final String tokenHolder = "tokenHolder";
        final String theToken = "TheToken";
        final long totalLockedTokens = 1000L; // Assume `x` tokens are already locked
        final long tokensToUnlock = 500L; // This should be within the range of `0 <= tokensToUnlock <= totalLockedTokens`

        return defaultHapiSpec("CanUnlockSubsetOfLockedTokens")
                .given(
                        newKeyNamed(lockKeyHolder),
                        cryptoCreate(tokenHolder),
                        cryptoCreate("tokenTreasury").balance(0L),
                        tokenCreate(theToken)
                                .initialSupply(totalLockedTokens)
                                .treasury("tokenTreasury")
                                .withCustom(lockKeyHolder, "lock"),
                        tokenAssociate(tokenHolder, theToken),
                        tokenLock(theToken) // Assume there's a method to lock the tokens initially
                                .locking(totalLockedTokens)
                                .forAccount(tokenHolder)
                                .signedBy(lockKeyHolder, tokenHolder)
                                .via("initialLockTxn")
                ).when(
                        tokenUnlock(theToken)
                                .unlocking(tokensToUnlock)
                                .forAccount(tokenHolder)
                                .signedBy(lockKeyHolder)
                                .via("tokenUnlockTxn")
                ).then(
                        getAccountBalance(tokenHolder)
                                .hasTokenBalance(theToken, totalLockedTokens - tokensToUnlock), // Check if the unlock was successful
                        assertStatus(SUCCESS, "tokenUnlockTxn")
                );
    }

    /**
     * <b>Lock-4</b>
     * <p>As a `lock-key` holder, I want to unlock a subset of the currently held locked fungible tokens
     * held by a user's account in a partition without requiring the user's signature.
     * If an account has `x` locked tokens in a partition, then the number of tokens that can be
     * additionally unlocked is governed by: `0 <= number_of_locked_tokens <= x`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canUnlockSubsetOfLockedTokensInPartition() {
        final String lockKeyHolder = "lockKeyHolder";
        final String tokenHolder = "tokenHolder";
        final String theToken = "TheToken";
        final String thePartition = "ThePartition";
        final long totalLockedTokens = 1000L; // Assume `x` tokens are already locked in a partition
        final long tokensToUnlock = 500L; // This should be within the range of `0 <= tokensToUnlock <= totalLockedTokens`

        return defaultHapiSpec("CanUnlockSubsetOfLockedTokensInPartition")
                .given(
                        newKeyNamed(lockKeyHolder),
                        cryptoCreate(tokenHolder),
                        cryptoCreate("tokenTreasury").balance(0L),
                        tokenCreate(theToken)
                                .initialSupply(totalLockedTokens)
                                .treasury("tokenTreasury")
                                .withCustom(lockKeyHolder, "lock"),
                        createPartition(theToken, thePartition)
                                .forToken(theToken)
                                .startAt(1L)
                                .endAt(totalLockedTokens),
                        tokenAssociate(tokenHolder, theToken),
                        partitionLock(theToken, thePartition) // Assume there's a method to lock the tokens in a partition initially
                                .locking(totalLockedTokens)
                                .forAccount(tokenHolder)
                                .signedBy(lockKeyHolder, tokenHolder)
                                .via("initialPartitionLockTxn")
                ).when(
                        partitionUnlock(theToken, thePartition)
                                .unlocking(tokensToUnlock)
                                .forAccount(tokenHolder)
                                .signedBy(lockKeyHolder)
                                .via("partitionUnlockTxn")
                ).then(
                        getPartitionBalance(tokenHolder, theToken, thePartition)
                                .hasLockedTokenAmount(totalLockedTokens - tokensToUnlock), // Check if the unlock was successful within the partition
                        assertStatus(SUCCESS, "partitionUnlockTxn")
                );
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
        final String lockKeyHolder = "lockKeyHolder";
        final String tokenHolder = "tokenHolder";
        final String theNFT = "TheNFT";
        final long[] nftSerialsToLock = {1L, 2L}; // Serial numbers of the NFTs to be locked

        return defaultHapiSpec("CanLockSpecificNFTSerials")
                .given(
                        newKeyNamed(lockKeyHolder),
                        cryptoCreate(tokenHolder),
                        cryptoCreate("nftTreasury").balance(0L),
                        tokenCreate(theNFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .treasury("nftTreasury"),
                        mintNFT(theNFT, nftSerialsToLock.length)
                                .serialNumbers(nftSerialsToLock)
                                .signedBy("nftTreasury"),
                        tokenAssociate(tokenHolder, theNFT)
                ).when(
                        lockNFTs(theNFT, nftSerialsToLock)
                                .forAccount(tokenHolder)
                                .signedBy(lockKeyHolder)
                                .via("nftLockTxn")
                ).then(
                        getAccountNFTs(tokenHolder, theNFT)
                                .hasSerialNosLocked(nftSerialsToLock), // Assuming there's a method to check locked NFT serials
                        assertStatus(SUCCESS, "nftLockTxn")
                );
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
        final String lockKeyHolder = "lockKeyHolder";
        final String tokenHolder = "tokenHolder";
        final String thePartitionedNFT = "ThePartitionedNFT";
        final long[] nftSerialsToLock = {1L, 2L}; // Serial numbers of the NFTs to be locked
        final String partition = "partition1";

        return defaultHapiSpec("CanLockSpecificNFTSerialsInPartition")
                .given(
                        newKeyNamed(lockKeyHolder),
                        cryptoCreate(tokenHolder),
                        cryptoCreate("nftTreasury").balance(0L),
                        tokenCreate(thePartitionedNFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .treasury("nftTreasury")
                                .withPartition(partition), // Assuming tokenCreate can be chained with partition creation
                        mintNFT(thePartitionedNFT, nftSerialsToLock.length)
                                .serialNumbers(nftSerialsToLock)
                                .signedBy("nftTreasury")
                                .toPartition(partition), // Assuming mintNFT can specify partition
                        tokenAssociate(tokenHolder, thePartitionedNFT)
                ).when(
                        lockNFTsInPartition(thePartitionedNFT, partition, nftSerialsToLock)
                                .forAccount(tokenHolder)
                                .signedBy(lockKeyHolder)
                                .via("nftPartitionLockTxn")
                ).then(
                        getAccountNFTsInPartition(tokenHolder, thePartitionedNFT, partition)
                                .hasSerialNosLocked(nftSerialsToLock), // Assuming there's a method to check locked NFT serials in partition
                        assertStatus(SUCCESS, "nftPartitionLockTxn")
                );
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
        final String lockKeyHolder = "lockKeyHolder";
        final String tokenHolder = "tokenHolder";
        final String theNFT = "TheNFT";
        final long[] nftSerialsToUnlock = {1L, 2L}; // Serial numbers of the NFTs to be unlocked

        return defaultHapiSpec("CanUnlockSpecificNFTSerials")
                .given(
                        newKeyNamed(lockKeyHolder),
                        cryptoCreate(tokenHolder),
                        cryptoCreate("nftTreasury").balance(0L),
                        tokenCreate(theNFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .treasury("nftTreasury"),
                        mintNFT(theNFT, nftSerialsToUnlock.length)
                                .serialNumbers(nftSerialsToUnlock)
                                .signedBy("nftTreasury"),
                        lockNFTs(theNFT, nftSerialsToUnlock)
                                .forAccount(tokenHolder)
                                .signedBy(lockKeyHolder)
                                .via("nftLockTxn"),
                        tokenAssociate(tokenHolder, theNFT)
                ).when(
                        unlockNFTs(theNFT, nftSerialsToUnlock)
                                .forAccount(tokenHolder)
                                .signedBy(lockKeyHolder)
                                .via("nftUnlockTxn")
                ).then(
                        getAccountNFTs(tokenHolder, theNFT)
                                .hasSerialNosUnlocked(nftSerialsToUnlock), // Assuming there's a method to check unlocked NFT serials
                        assertStatus(SUCCESS, "nftUnlockTxn")
                );
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
        final String lockKeyHolder = "lockKeyHolder";
        final String tokenHolder = "tokenHolder";
        final String theNFT = "TheNFT";
        final String partition = "PartitionA";
        final long[] nftSerialsToUnlock = {1L, 2L}; // Serial numbers of the NFTs to be unlocked

        return new HapiSpec("CanUnlockSpecificNFTSerialsInPartition")
                .given(
                        newKeyNamed(lockKeyHolder),
                        cryptoCreate(tokenHolder),
                        cryptoCreate("nftTreasury").balance(0L),
                        tokenCreate(theNFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .treasury("nftTreasury"),
                        mintNFT(theNFT, nftSerialsToUnlock.length)
                                .serialNumbers(nftSerialsToUnlock)
                                .signedBy("nftTreasury"),
                        lockNFTs(theNFT, nftSerialsToUnlock)
                                .inPartition(partition)
                                .forAccount(tokenHolder)
                                .signedBy(lockKeyHolder)
                                .via("nftLockTxn"),
                        tokenAssociate(tokenHolder, theNFT)
                ).when(
                        unlockNFTs(theNFT, nftSerialsToUnlock)
                                .inPartition(partition)
                                .forAccount(tokenHolder)
                                .signedBy(lockKeyHolder)
                                .via("nftUnlockTxn")
                ).then(
                        getAccountNFTs(tokenHolder, theNFT)
                                .inPartition(partition)
                                .hasSerialNosUnlocked(nftSerialsToUnlock), // Assuming a method to check unlocked NFT serials within a partition
                        assertStatus(SUCCESS, "nftUnlockTxn")
                );
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
