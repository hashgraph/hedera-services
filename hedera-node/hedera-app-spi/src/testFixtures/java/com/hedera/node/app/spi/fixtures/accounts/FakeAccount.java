package com.hedera.node.app.spi.fixtures.accounts;

import com.hedera.node.app.spi.accounts.Account;
import com.hedera.node.app.spi.accounts.AccountBuilder;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

// This class will be removed as soon as Neeha's PR is merged
public record FakeAccount(
        long accountNumber,
        Bytes alias,
        @Nullable HederaKey key,
        long expiry,
        long balance,
        String memo,
        boolean isDeleted,
        boolean isSmartContract,
        boolean isReceiverSigRequired,
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
        long autoRenewAccountNumber,
        long autoRenewSecs)
        implements Account {

    @Override
    public boolean isHollow() {
        return key == null;
    }

    @Override
    public HederaKey getKey() {
        return key;
    }

    @Override
    public long balanceInTinyBar() {
        return balance;
    }

    @Override
    @NonNull
    public AccountBuilder copy() {
        return new FakeAccountBuilder(this);
    }
}