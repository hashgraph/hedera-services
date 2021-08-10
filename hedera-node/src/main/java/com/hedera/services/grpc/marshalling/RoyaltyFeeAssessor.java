package com.hedera.services.grpc.marshalling;

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.state.submerkle.RoyaltyFeeSpec;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.List;

import static com.hedera.services.grpc.marshalling.AdjustmentUtils.safeFractionMultiply;
import static com.hedera.services.state.submerkle.FcCustomFee.FeeType.ROYALTY_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class RoyaltyFeeAssessor {
	private final HtsFeeAssessor htsFeeAssessor;
	private final HbarFeeAssessor hbarFeeAssessor;
	private final FungibleAdjuster fungibleAdjuster;

	public RoyaltyFeeAssessor(
			HtsFeeAssessor htsFeeAssessor,
			HbarFeeAssessor hbarFeeAssessor,
			FungibleAdjuster fungibleAdjuster
	) {
		this.htsFeeAssessor = htsFeeAssessor;
		this.hbarFeeAssessor = hbarFeeAssessor;
		this.fungibleAdjuster = fungibleAdjuster;
	}

	public ResponseCodeEnum assessAllRoyalties(
			BalanceChange change,
			List<FcCustomFee> feesWithRoyalties,
			BalanceChangeManager changeManager,
			List<FcAssessedCustomFee> accumulator
	) {
		final var payer = change.getAccount();
		for (var fee : feesWithRoyalties) {
			final var collector = fee.getFeeCollectorAsId();
			if (fee.getFeeType() != ROYALTY_FEE) {
				continue;
			}
			final var spec = fee.getRoyaltyFeeSpec();
			final var token = change.getToken();
			if (changeManager.isRoyaltyPaid(token, payer)) {
				continue;
			}

			final var exchangedValue = changeManager.fungibleCreditsInCurrentLevel(payer);
			if (exchangedValue.isEmpty()) {
				final var fallback = spec.getFallbackFee();
				if (fallback != null) {
					final var receiver = Id.fromGrpcAccount(change.counterPartyAccountId());
					final var fallbackFee = FcCustomFee.fixedFee(
							fallback.getUnitsToCollect(),
							fallback.getTokenDenomination(),
							collector.asEntityId());
					if (fallback.getTokenDenomination() == null) {
						hbarFeeAssessor.assess(receiver, fallbackFee, changeManager, accumulator);
					} else {
						htsFeeAssessor.assess(receiver, fallbackFee, changeManager, accumulator);
					}
				}
			} else {
				final var fractionalValidity =
						chargeRoyalty(collector, spec, exchangedValue, fungibleAdjuster, changeManager, accumulator);
				if (fractionalValidity != OK) {
					return fractionalValidity;
				}
				changeManager.markRoyaltyPaid(token, payer);
			}
		}
		return OK;
	}

	private ResponseCodeEnum chargeRoyalty(
			Id collector,
			RoyaltyFeeSpec spec,
			List<BalanceChange> exchangedValue,
			FungibleAdjuster fungibleAdjuster,
			BalanceChangeManager changeManager,
			List<FcAssessedCustomFee> accumulator
	) {
		for (var exchange : exchangedValue) {
			long value = exchange.originalUnits();
			long royaltyFee = safeFractionMultiply(spec.getNumerator(), spec.getDenominator(), value);
			if (exchange.units() < royaltyFee) {
				return INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
			}
			exchange.adjustUnits(-royaltyFee);
			final var denom = exchange.isForHbar() ? Id.MISSING_ID : exchange.getToken();
			fungibleAdjuster.adjustedChange(collector, denom, royaltyFee, changeManager);
			final var assessed =
					exchange.isForHbar()
							? new FcAssessedCustomFee(collector.asEntityId(), royaltyFee)
							: new FcAssessedCustomFee(collector.asEntityId(), denom.asEntityId(), royaltyFee);
			accumulator.add(assessed);
		}
		return OK;
	}
}
