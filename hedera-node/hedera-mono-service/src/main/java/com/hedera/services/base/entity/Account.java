package com.hedera.services.base.entity;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JKey;

import javax.annotation.Nonnull;
import java.security.Key;
import java.util.Optional;

public interface Account {
	long accountNumber();
	ByteString alias();
	@Nonnull
	Optional<JKey> key();
	long expiry();
	long balance();
	String memo();
	boolean isDeleted();
	boolean isSmartContract();
	boolean isReceiverSigRequired();

	int maxAutoAssociations();
	int numAssociations();
	int numPositiveBalances();
	long ethereumNonce();
	long stakedToMe();
	long stakePeriodStart();
	long stakedNum();
	boolean declineReward();
	long stakeAtStartOfLastRewardedPeriod();
	long autoRenewAccountNumber();
}
