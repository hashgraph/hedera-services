package com.hedera.services.base.entity;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JKey;

import java.util.Objects;
import java.util.Optional;

public record AccountImpl(long accountNumber,
						  Optional<ByteString> alias,
						  Optional<JKey> key,
						  long expiry,
						  long balance,
						  Optional<String> memo,
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
						  long autoRenewAccountNumber) implements Account {
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
