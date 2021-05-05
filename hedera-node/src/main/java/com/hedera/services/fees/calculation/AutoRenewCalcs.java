package com.hedera.services.fees.calculation;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.crypto.ExtantCryptoContext;
import com.hederahashgraph.api.proto.java.FeeData;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.time.Instant;

import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static com.hederahashgraph.fee.FeeBuilder.HRS_DIVISOR;

public class AutoRenewCalcs {
	private static final Pair<Long, Long> NO_RENEWAL_POSSIBLE = Pair.of(0L, 0L);

	private final CryptoOpsUsage cryptoOpsUsage;

	private Triple<FeeData, Instant, FeeData> cryptoAutoRenewPriceSeq = null;

	private long firstConstantCryptoAutoRenewFee = 0L;
	private long secondConstantCryptoAutoRenewFee = 0L;
	private long firstServiceRbhPrice = 0L;
	private long secondServiceRbhPrice = 0L;

	public AutoRenewCalcs(CryptoOpsUsage cryptoOpsUsage) {
		this.cryptoOpsUsage = cryptoOpsUsage;
	}

	public void setCryptoAutoRenewPriceSeq(Triple<FeeData, Instant, FeeData> cryptoAutoRenewPriceSeq) {
		this.cryptoAutoRenewPriceSeq = cryptoAutoRenewPriceSeq;

		this.firstConstantCryptoAutoRenewFee = constantFeeFrom(cryptoAutoRenewPriceSeq.getLeft());
		this.secondConstantCryptoAutoRenewFee = constantFeeFrom(cryptoAutoRenewPriceSeq.getRight());

		this.firstServiceRbhPrice = cryptoAutoRenewPriceSeq.getLeft().getServicedata().getRbh();
		this.secondServiceRbhPrice = cryptoAutoRenewPriceSeq.getRight().getServicedata().getRbh();
	}

	private long constantFeeFrom(FeeData prices) {
		return prices.getNodedata().getConstant()
				+ prices.getNetworkdata().getConstant()
				+ prices.getServicedata().getConstant();
	}

	public Pair<Long, Long> maxRenewalAndFeeFor(MerkleAccount expiredAccount, long requestedPeriod, Instant at) {
		if (cryptoAutoRenewPriceSeq == null) {
			throw new IllegalStateException("No crypto usage prices are set!");
		}

		final long balance = expiredAccount.getBalance();
		if (balance == 0L) {
			return NO_RENEWAL_POSSIBLE;
		}

		final long rbUsage = rbUsedBy(expiredAccount);
		final long maxRenewableRbh = Math.max(
				1L,
				maxRenewableRbhGiven(rbUsage, requestedPeriod, expiredAccount.getBalance()));

		final boolean isBeforeSwitch = at.isBefore(cryptoAutoRenewPriceSeq.getMiddle());
		final long fixedFee = isBeforeSwitch ? firstConstantCryptoAutoRenewFee : secondConstantCryptoAutoRenewFee;
		final long serviceRbhPrice = isBeforeSwitch ? firstServiceRbhPrice : secondServiceRbhPrice;

		final long maxRenewablePeriod = maxRenewableRbh * HRS_DIVISOR;
		final long feeForMaxRenewal = Math.min(fixedFee + maxRenewableRbh * serviceRbhPrice, balance);

		return Pair.of(maxRenewablePeriod, feeForMaxRenewal);
	}

	long maxRenewableRbhGiven(long rbUsage, long requestedPeriod, long balance) {
		final long remainingBalance = Math.max(0, balance - firstConstantCryptoAutoRenewFee);

		final long feePerHour = firstServiceRbhPrice * rbUsage;
		final long affordableHours = remainingBalance / feePerHour;
		final long requestedHours = requestedPeriod / HRS_DIVISOR;

		return Math.min(affordableHours, requestedHours);
	}

	long rbUsedBy(MerkleAccount account) {
		final var extantCtx = ExtantCryptoContext.newBuilder()
				.setCurrentExpiry(0L)
				.setCurrentKey(asKeyUnchecked(account.getKey()))
				.setCurrentlyHasProxy(account.getProxy() != null)
				.setCurrentMemo(account.getMemo())
				.setCurrentNumTokenRels(account.tokens().numAssociations())
				.build();
		return(cryptoOpsUsage.cryptoAutoRenewRb(extantCtx));
	}
}
