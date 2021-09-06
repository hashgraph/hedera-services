package com.hedera.services.state.submerkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.common.base.MoreObjects;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Token;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Objects;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ROYALTY_FRACTION_CANNOT_EXCEED_ONE;

public class RoyaltyFeeSpec {
	private final long numerator;
	private final long denominator;
	private final FixedFeeSpec fallbackFee;

	public RoyaltyFeeSpec(long numerator, long denominator, FixedFeeSpec fallbackFee) {
		validateTrue(denominator != 0, FRACTION_DIVIDES_BY_ZERO);
		validateTrue(bothPositive(numerator, denominator), CUSTOM_FEE_MUST_BE_POSITIVE);
		validateTrue(numerator <= denominator, ROYALTY_FRACTION_CANNOT_EXCEED_ONE);
		this.numerator = numerator;
		this.denominator = denominator;
		this.fallbackFee = fallbackFee;
	}

	public void validateWith(final Token owningToken, final Account feeCollector, final TypedTokenStore tokenStore) {
		validateInternal(owningToken, false, feeCollector, tokenStore);
	}

	public void validateAndFinalizeWith(
			final Token provisionalToken,
			final Account feeCollector,
			final TypedTokenStore tokenStore
	) {
		validateInternal(provisionalToken, true, feeCollector, tokenStore);
	}

	private void validateInternal(
			final Token token,
			final boolean beingCreated,
			final Account feeCollector,
			final TypedTokenStore tokenStore
	) {
		validateTrue(token.isNonFungibleUnique(), CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
		if (fallbackFee != null) {
			if (beingCreated) {
				fallbackFee.validateAndFinalizeWith(token, feeCollector, tokenStore);
			} else {
				fallbackFee.validateWith(feeCollector, tokenStore);
			}
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || !obj.getClass().equals(RoyaltyFeeSpec.class)) {
			return false;
		}

		final var that = (RoyaltyFeeSpec) obj;
		return this.numerator == that.numerator &&
				this.denominator == that.denominator &&
				Objects.equals(this.fallbackFee, that.fallbackFee);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(RoyaltyFeeSpec.class)
				.add("numerator", numerator)
				.add("denominator", denominator)
				.add("fallbackFee", fallbackFee)
				.toString();
	}

	public long getNumerator() {
		return numerator;
	}

	public long getDenominator() {
		return denominator;
	}

	public FixedFeeSpec getFallbackFee() {
		return fallbackFee;
	}

	public boolean hasFallbackFee() {
		return fallbackFee != null;
	}

	private boolean bothPositive(long a, long b) {
		return a > 0 && b > 0;
	}
}
