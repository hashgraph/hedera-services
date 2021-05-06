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
		assertNotInCycle();

		cycleTime = now;
		feeHelper.beginChargingCycle();
		recordsHelper.beginRenewalCycle(now);
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
				processExpiredAccountNonzeroBalance(new MerkleEntityId(shard, realm, entityNum));
				return true;
		}
		return false;
	}

	private void processExpiredAccountNonzeroBalance(MerkleEntityId accountId) {
		final var lastClassified = helper.getLastClassifiedAccount();
		final long reqPeriod = lastClassified.getAutoRenewSecs();
		final var usageAssessment = fees.assessCryptoAutoRenewal(lastClassified, reqPeriod, cycleTime);
		final long effPeriod = usageAssessment.renewalPeriod();
		final long renewalFee = usageAssessment.fee();

		helper.renewLastClassifiedWith(renewalFee, effPeriod);
		recordsHelper.streamCryptoRenewal(accountId, renewalFee, longNow + effPeriod);
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
		assertInCycle();

		cycleTime = null;
		feeHelper.endChargingCycle();
		recordsHelper.endRenewalCycle();
	}

	private void assertInCycle() {
		if (cycleTime == null) {
			throw new IllegalStateException("Cannot stream records if not in a renewal cycle!");
		}
	}

	private void assertNotInCycle() {
		if (cycleTime != null) {
			throw new IllegalStateException("Cannot end renewal cycle, none is started!");
		}
	}

	Instant getCycleTime() {
		return cycleTime;
	}
}
