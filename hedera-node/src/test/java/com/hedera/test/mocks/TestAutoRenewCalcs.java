package com.hedera.test.mocks;

import com.hedera.services.fees.calculation.AutoRenewCalcs;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.FeeData;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.time.Instant;

public class TestAutoRenewCalcs extends AutoRenewCalcs {
	public TestAutoRenewCalcs() {
		super(null);
	}

	@Override
	public Pair<Long, Long> maxRenewalAndFeeFor(MerkleAccount expiredAccount, long requestedPeriod, Instant at) {
		return Pair.of(Long.MAX_VALUE, 0L);
	}

	@Override
	public void setCryptoAutoRenewPriceSeq(Triple<FeeData, Instant, FeeData> cryptoAutoRenewPriceSeq) {
		/* No-op */
	}
}
