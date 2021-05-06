package com.hedera.services.state.expiry.renewal;

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.state.merkle.MerkleEntityId;

import java.time.Instant;

public class RenewalProcess {
	private final long shard, realm;

	private final FeeCalculator fees;
	private final RenewalHelper helper;
	private final RenewalFeeHelper feeHelper;
	private final RenewalRecordsHelper recordsHelper;
	private final GlobalDynamicProperties dynamicProperties;

	private long longNow;
	private Instant cycleTime = null;

	public RenewalProcess(
			FeeCalculator fees,
			HederaNumbers hederaNums,
			RenewalHelper helper,
			RenewalFeeHelper feeHelper,
			RenewalRecordsHelper recordsHelper,
			GlobalDynamicProperties dynamicProperties
	) {
		this.fees = fees;
		this.helper = helper;
		this.feeHelper = feeHelper;
		this.recordsHelper = recordsHelper;
		this.dynamicProperties = dynamicProperties;

		this.realm = hederaNums.realm();
		this.shard = hederaNums.shard();
	}

	public void beginRenewalCycle(Instant now) {
		cycleTime = now;
	}

	public boolean process(long entityNum) {
		assertInCycle();

		longNow = cycleTime.getEpochSecond();
		final var classification = helper.classify(entityNum, longNow);
		switch (classification) {
			case OTHER:
				break;
			case ACCOUNT_EXPIRED_ZERO_BALANCE:
				processExpiredAccountZeroBalance(new MerkleEntityId(shard, realm, entityNum));
				return true;
			case ACCOUNT_EXPIRED_NONZERO_BALANCE:
		}
		return false;
	}

	private void processExpiredAccountZeroBalance(MerkleEntityId accountId) {
		final long gracePeriod = dynamicProperties.autoRenewGracePeriod();
		if (gracePeriod == 0L) {
			helper.removeLastClassifiedEntity();
			recordsHelper.streamCryptoRemoval(accountId);
		} else {
			helper.renewLastClassifiedWith(0L, gracePeriod);
			recordsHelper.streamCryptoRenewal(accountId, 0L, longNow + gracePeriod);
		}
	}

	public void endRenewalCycle() {
		throw new AssertionError("Not implemented!");
	}

	private void assertInCycle() {
		if (cycleTime == null) {
			throw new IllegalStateException("Cannot stream records if not in a renewal cycle!");
		}
	}

	Instant getCycleTime() {
		return cycleTime;
	}
}
