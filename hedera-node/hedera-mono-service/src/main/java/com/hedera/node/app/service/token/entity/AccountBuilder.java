package com.hedera.node.app.service.token.entity;

import com.hedera.node.app.spi.key.HederaKey;

import javax.annotation.Nonnull;

/**
 * Builds an account
 */
public interface AccountBuilder {
    /**
     * Override the keys specified on the account
     * @param key
     * @return
     */
    @Nonnull AccountBuilder key(@Nonnull HederaKey key);

    @Nonnull AccountBuilder expiry(long value);

    /**
     * Sets the hbar balance of the account.
     * @param value The hbar balance of the account (in tinybar). Must be non-negative or IAE.
     * @throws IllegalArgumentException if the value is less than 0 or greater than 50 billion.
     */
    @Nonnull AccountBuilder balance(long value);

    @Nonnull AccountBuilder memo(String value);
    @Nonnull AccountBuilder deleted(boolean value);
    @Nonnull AccountBuilder receiverSigRequired(boolean value);
    @Nonnull AccountBuilder proxyAccountNumber(long value);
    @Nonnull AccountBuilder numberOfOwnedNfts(long value);
    @Nonnull AccountBuilder maxAutoAssociations(int value);
    @Nonnull AccountBuilder usedAutoAssociations(int value);
    @Nonnull AccountBuilder numAssociations(int value);
    @Nonnull AccountBuilder numPositiveBalances(int value);
    @Nonnull AccountBuilder ethereumNonce(long value);
    @Nonnull AccountBuilder stakedToMe(long value);
    @Nonnull AccountBuilder stakePeriodStart(long value);
    @Nonnull AccountBuilder stakedNum(long value);
    @Nonnull AccountBuilder declineReward(boolean value);
    @Nonnull AccountBuilder stakeAtStartOfLastRewardedPeriod(long value);
    @Nonnull AccountBuilder autoRenewAccountNumber(long value);

    /**
     * Builds and returns an account with the state specified in the builder
     *
     * @return A non-null reference to a **new** account. Two calls to this method return different instances.
     */
    @Nonnull Account build();
}
