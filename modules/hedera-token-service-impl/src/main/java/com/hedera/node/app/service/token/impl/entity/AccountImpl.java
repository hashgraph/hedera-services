package com.hedera.node.app.service.token.impl.entity;

import com.hedera.node.app.service.token.entity.Account;
import com.hedera.node.app.service.token.entity.AccountBuilder;
import com.hedera.node.app.spi.key.HederaKey;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;

/**
 * An implementation of {@link Account}.
 */
public record AccountImpl(
        long accountNumber,
        Optional<byte[]> alias,
        Optional<HederaKey> key,
        long expiry,
        long balance,
        String memo,
        boolean isDeleted,
        boolean isSmartContract,
        boolean isReceiverSigRequired,
        long proxyAccountNumber,
        long numberOfOwnedNfts,
        int maxAutoAssociations,
        int usedAutoAssociations,
        int numAssociations,
        int numPositiveBalances,
        long ethereumNonce,
        long stakedToMe,
        long stakePeriodStart,
        long stakedNum,
        boolean declineReward,
        long stakeAtStartOfLastRewardedPeriod,
        long autoRenewAccountNumber
        ) implements Account {

    /**
     * @inheritDoc
     */
    @Override
    public long shardNumber() {
        // LATER: In the real world, we would want to get this from config
        return 0;
    }

    /**
     * @inheritDoc
     */
    @Override
    public long realmNumber() {
        // LATER: In the real world, we would want to get this from config
        return 0;
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isHollow() {
        return key.isEmpty();
    }

    @Override
    public long balanceInHbar() {
        return balance * ;
    }

    @Override
    public long balanceInTinyBar() {
        return ;
    }

    @Override
    public long autoRenewSecs() {
        return 0;
    }

    @Override
    @NonNull
    public AccountBuilder copy() {
        return new AccountBuilderImpl(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountImpl account = (AccountImpl) o;
        return accountNumber == account.accountNumber && expiry == account.expiry && balance == account.balance
                && isDeleted == account.isDeleted && isSmartContract == account.isSmartContract
                && isReceiverSigRequired == account.isReceiverSigRequired
                && proxyAccountNumber == account.proxyAccountNumber && numberOfOwnedNfts == account.numberOfOwnedNfts
                && maxAutoAssociations == account.maxAutoAssociations
                && usedAutoAssociations == account.usedAutoAssociations && numAssociations == account.numAssociations
                && numPositiveBalances == account.numPositiveBalances && ethereumNonce == account.ethereumNonce
                && stakedToMe == account.stakedToMe && stakePeriodStart == account.stakePeriodStart
                && stakedNum == account.stakedNum && declineReward == account.declineReward
                && stakeAtStartOfLastRewardedPeriod == account.stakeAtStartOfLastRewardedPeriod
                && autoRenewAccountNumber == account.autoRenewAccountNumber && alias.equals(account.alias)
                && key.equals(account.key) && memo.equals(account.memo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountNumber, alias, key, expiry, balance, memo, isDeleted, isSmartContract,
                isReceiverSigRequired, proxyAccountNumber, numberOfOwnedNfts, maxAutoAssociations,
                usedAutoAssociations, numAssociations, numPositiveBalances, ethereumNonce, stakedToMe,
                stakePeriodStart, stakedNum, declineReward, stakeAtStartOfLastRewardedPeriod, autoRenewAccountNumber);
    }
}
