package com.hedera.node.app.service.token.entity;

import com.hedera.node.app.spi.key.HederaKey;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * An Account entity represents a Hedera Account.
 */
public interface Account {
    long shardNumber();
    long realmNumber();
    long accountNumber();
    Optional<byte[]> alias();

    /**
     * Gets whether this is a "hollow" account. A hollow account is an account that was created
     * automatically as a result of a crypto transfer (or token transfer, or other transfer of value)
     * into a non-existent account via alias or virtual address.
     *
     * @return True if this is a hollow account
     */
    boolean isHollow();

    /**
     * The keys on the account. This may return an empty {@link Optional} if the account is
     * a "hollow" account (as determined by {@link #isHollow()()}).
     *
     * @return An optional key list. This will always be set unless the account is hollow.
     */
    @Nonnull
    Optional<HederaKey> key();

    /**
     * Gets the expiry of the account, in millis from the epoch.
     *
     * @return The expiry of the account in millis from the epoch.
     */
    long expiry();

    /**
     * Gets the hbar balance of the account. The balance will always be non-negative.
     *
     * @return The hbar balance (in tinybar)
     */
    long balanceInHbar();

    long balanceInTinyBar();

     long autoRenewSecs();

    @Nonnull String memo();
    boolean isDeleted();
    boolean isSmartContract();
    boolean isReceiverSigRequired();

    long proxyAccountNumber();
    long numberOfOwnedNfts();

    int maxAutoAssociations();
    int usedAutoAssociations();
    int numAssociations();
    int numPositiveBalances();

    long ethereumNonce();

    long stakedToMe();
    long stakePeriodStart();
    long stakedNum();
    boolean declineReward();
    long stakeAtStartOfLastRewardedPeriod();

    long autoRenewAccountNumber();

    /**
     * Creates an AccountBuilder that clones all state in this instance, allowing the user to override
     * only the specific state that they choose to override.
     *
     * @return A non-null builder pre-initialized with all state in this instance.
     */
    @Nonnull AccountBuilder copy();
}
