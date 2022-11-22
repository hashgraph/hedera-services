package com.hedera.node.app.service.token.entity;

import com.hedera.node.app.spi.key.HederaKey;
import edu.umd.cs.findbugs.annotations.NonNull;


/**
 * Builds an account
 */
public interface AccountBuilder {
    /**
     * Override the keys specified on the account
     * @param key
     * @return
     */
    @NonNull
    AccountBuilder key(@NonNull HederaKey key);

    @NonNull AccountBuilder expiry(long value);

    /**
     * Sets the hbar balance of the account.
     * @param value The hbar balance of the account (in tinybar). Must be non-negative or IAE.
     * @throws IllegalArgumentException if the value is less than 0 or greater than 50 billion.
     */
    @NonNull AccountBuilder balance(long value);

    @NonNull AccountBuilder memo(String value);
    @NonNull AccountBuilder deleted(boolean value);
    @NonNull AccountBuilder receiverSigRequired(boolean value);
    @NonNull AccountBuilder proxyAccountNumber(long value);
    @NonNull AccountBuilder numberOfOwnedNfts(long value);
    @NonNull AccountBuilder maxAutoAssociations(int value);
    @NonNull AccountBuilder usedAutoAssociations(int value);
    @NonNull AccountBuilder numAssociations(int value);
    @NonNull AccountBuilder numPositiveBalances(int value);
    @NonNull AccountBuilder ethereumNonce(long value);
    @NonNull AccountBuilder stakedToMe(long value);
    @NonNull AccountBuilder stakePeriodStart(long value);
    @NonNull AccountBuilder stakedNum(long value);
    @NonNull AccountBuilder declineReward(boolean value);
    @NonNull AccountBuilder stakeAtStartOfLastRewardedPeriod(long value);
    @NonNull AccountBuilder autoRenewAccountNumber(long value);

    /**
     * Builds and returns an account with the state specified in the builder
     *
     * @return A non-null reference to a **new** account. Two calls to this method return different instances.
     */
    @NonNull Account build();
}
