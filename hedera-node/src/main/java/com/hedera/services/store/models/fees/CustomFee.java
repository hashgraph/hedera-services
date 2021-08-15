package com.hedera.services.store.models.fees;

/*
 * -
 * ‌
 * Hedera Services Node
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.state.submerkle.FixedFeeSpec;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_NOT_FULLY_SPECIFIED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;

/**
 * A model and a validator of a {@link com.hederahashgraph.api.proto.java.CustomFee} grpc.
 * Represents the model of an element in a custom fee collection, coming from gRPC call.
 * Holds {@link FractionalFee} and {@link FixedFee}.
 * Can be mapped to {@link FcCustomFee} directly.
 *
 * @author Yoan Sredkov (yoansredkov@gmail.com)
 */
public class CustomFee {
	private final Account collector;

	private FixedFee fixedFee;
	private RoyaltyFee royaltyFee;
	private FractionalFee fractionalFee;

	public CustomFee(Account collector, FractionalFee fee) {
		this.collector = collector;
		this.fractionalFee = fee;
	}

	public CustomFee(Account collector, FixedFee fee) {
		this.collector = collector;
		this.fixedFee = fee;
	}

	public CustomFee(Account collector, RoyaltyFee fee) {
		this.collector = collector;
		this.royaltyFee = fee;
	}

	public static CustomFee fromGrpc(com.hederahashgraph.api.proto.java.CustomFee fee, Account collector) {
		if (fee.hasFixedFee()) {
			final var amount = fee.getFixedFee().getAmount();
			validateFalse(amount <= 0, CUSTOM_FEE_MUST_BE_POSITIVE);

			Id denomTokenId = null;
			if (fee.getFixedFee().hasDenominatingTokenId()) {
				denomTokenId = Id.fromGrpcToken(fee.getFixedFee().getDenominatingTokenId());
			}
			final var fixedFee = new FixedFee(fee.getFixedFee().getAmount(), denomTokenId);
			return new CustomFee(collector, fixedFee);
		} else if (fee.hasFractionalFee()) {
			final var fractionalFeeGrpc = fee.getFractionalFee();
			final var fractionGrpc = fractionalFeeGrpc.getFractionalAmount();
			validateFalse(fractionGrpc.getDenominator() == 0, FRACTION_DIVIDES_BY_ZERO);
			validateTrue(areAllPositiveNumbers(fractionGrpc.getNumerator(),
					fractionGrpc.getDenominator(), fractionalFeeGrpc.getMaximumAmount(),
					fractionalFeeGrpc.getMinimumAmount()), CUSTOM_FEE_MUST_BE_POSITIVE);
			validateTrue(fractionalFeeGrpc.getMinimumAmount() <= fractionalFeeGrpc.getMaximumAmount(),
					FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT);
			final var fractionalFee = new FractionalFee(
					fractionalFeeGrpc.getMaximumAmount(),
					fractionalFeeGrpc.getMinimumAmount(),
					fractionGrpc.getNumerator(),
					fractionGrpc.getDenominator(),
					fractionalFeeGrpc.getNetOfTransfers());
			return new CustomFee(collector, fractionalFee);
		} else if (fee.hasRoyaltyFee()) {
			final var royaltyFeeGrpc = fee.getRoyaltyFee();
			final var exchangeValueFraction = royaltyFeeGrpc.getExchangeValueFraction();

			validateFalse(exchangeValueFraction.getDenominator() == 0, FRACTION_DIVIDES_BY_ZERO);
			validateTrue(
					areAllPositiveNumbers(exchangeValueFraction.getNumerator(), exchangeValueFraction.getDenominator()),
					CUSTOM_FEE_MUST_BE_POSITIVE);
			FixedFee fallback = null;
			if (royaltyFeeGrpc.hasFallbackFee()) {
				final var fallbackFee = royaltyFeeGrpc.getFallbackFee();
				final var amount = fallbackFee.getAmount();
				validateFalse(amount <= 0, CUSTOM_FEE_MUST_BE_POSITIVE);
				fallback = new FixedFee(amount, Id.fromGrpcToken(fallbackFee.getDenominatingTokenId()));
			}
			RoyaltyFee royaltyFee = new RoyaltyFee(fallback, exchangeValueFraction.getNumerator(),
					exchangeValueFraction.getDenominator());
			return new CustomFee(collector, royaltyFee);
		} else {
			throw new InvalidTransactionException(CUSTOM_FEE_NOT_FULLY_SPECIFIED);
		}
	}

	private static boolean areAllPositiveNumbers(long... numbers) {
		boolean positive = true;
		for (long n : numbers) {
			positive &= n >= 0;
		}
		return positive;
	}

	public RoyaltyFee getRoyaltyFee() {
		return royaltyFee;
	}

	public FcCustomFee toMerkle() {
		if (fractionalFee != null) {
			return FcCustomFee.fractionalFee(
					fractionalFee.getFractionalNumerator(),
					fractionalFee.getFractionalDenominator(),
					fractionalFee.getMinimumAmount(),
					fractionalFee.getMaximumAmount(),
					fractionalFee.getNetOfTransfers(),
					collector.getId().asEntityId());
		} else if (royaltyFee != null) {
			final var fixedFeeGrpcBuilder = com.hederahashgraph.api.proto.java.FixedFee.newBuilder();
			if (royaltyFee.hasFallbackFee()) {
				final var fallback = royaltyFee.getFallbackFee();
				fixedFeeGrpcBuilder.setAmount(fallback.getAmount());
				final var optionalDenom = fallback.getDenominatingTokenId();
				optionalDenom.ifPresent(id -> fixedFeeGrpcBuilder.setDenominatingTokenId(id.asGrpcToken()));
			}
			return FcCustomFee.royaltyFee(
					royaltyFee.getNumerator(),
					royaltyFee.getDenominator(),
					royaltyFee.hasFallbackFee() ? FixedFeeSpec.fromGrpc(fixedFeeGrpcBuilder.build()) : null,
					collector.getId().asEntityId());
		} else {
			final var tokenDenom = fixedFee.getDenominatingTokenId().map(Id::asEntityId).orElse(null);
			return FcCustomFee.fixedFee(fixedFee.getAmount(), tokenDenom, collector.getId().asEntityId());
		}
	}

	/**
	 * Indicates if the fee collection account needs to be auto-associated to its
	 * owning token (as part of handling a TokenCreate transaction).
	 *
	 * @return whether the fee collection account should be auto-associated during a TokenCreate
	 */
	public boolean shouldCollectorBeAutoAssociated() {
		if (fractionalFee != null) {
			return true;
		} else if (fixedFee != null) {
			return fixedFee.getDenominatingTokenId().map(id -> id.getNum() == 0).orElse(false);
		} else if (royaltyFee != null && royaltyFee.hasFallbackFee()) {
			final var fallback = royaltyFee.getFallbackFee();
			return fallback.getDenominatingTokenId().map(id -> id.getNum() == 0).orElse(false);
		}
		return false;
	}

	public FractionalFee getFractionalFee() {
		return this.fractionalFee;
	}

	public void setFractionalFee(final FractionalFee fractionalFee) {
		this.fractionalFee = fractionalFee;
	}

	public FixedFee getFixedFee() {
		return this.fixedFee;
	}

	public void setFixedFee(final FixedFee fixedFee) {
		this.fixedFee = fixedFee;
	}

	public Account getCollector() {
		return collector;
	}
}
