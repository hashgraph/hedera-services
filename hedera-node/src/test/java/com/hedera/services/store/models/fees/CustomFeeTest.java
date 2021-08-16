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
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_NOT_FULLY_SPECIFIED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CustomFeeTest {

	@Mock
	Account collector;

	Id denomId = IdUtils.asModelId("1.2.3");
	Id collectorId = IdUtils.asModelId("3.3.4");

	@Test
	void invalidCases() {
		var negativeFixed = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
				.setFixedFee(FixedFee.newBuilder().setAmount(-10).build())
				.build();
		assertFailsWith(
				() -> CustomFee.fromGrpc(negativeFixed, collector),
				CUSTOM_FEE_MUST_BE_POSITIVE
		);

		var zeroDivisibleFractional = com.hederahashgraph.api.proto.java.CustomFee.newBuilder().setFractionalFee(
						FractionalFee.newBuilder()
								.setFractionalAmount(Fraction.newBuilder()
										.setNumerator(10)
										.setDenominator(0)
										.build())
								.build())
				.build();
		assertFailsWith(
				() -> CustomFee.fromGrpc(zeroDivisibleFractional, collector),
				FRACTION_DIVIDES_BY_ZERO
		);

		var negativeFractional = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
				.setFractionalFee(FractionalFee.newBuilder()
						.setMaximumAmount(-10)
						.setMinimumAmount(-15)
						.setFractionalAmount(Fraction.newBuilder()
								.setDenominator(1)
								.setNumerator(1)
								.build()))
				.build();
		assertFailsWith(
				() -> CustomFee.fromGrpc(negativeFractional, collector),
				CUSTOM_FEE_MUST_BE_POSITIVE
		);

		var fractionalWithMaxLessThanMin = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
				.setFractionalFee(FractionalFee.newBuilder()
						.setMinimumAmount(16)
						.setMaximumAmount(15)
						.setFractionalAmount(Fraction.newBuilder()
								.setDenominator(10)
								.setNumerator(10)
								.build()))
				.build();
		assertFailsWith(
				() -> CustomFee.fromGrpc(fractionalWithMaxLessThanMin, collector),
				FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT
		);

		var nonSpecifiedFeeGrpc = com.hederahashgraph.api.proto.java.CustomFee.newBuilder().build();
		assertFailsWith(
				() -> CustomFee.fromGrpc(nonSpecifiedFeeGrpc, collector),
				CUSTOM_FEE_NOT_FULLY_SPECIFIED
		);
		
		final var royaltyWithNegativeFallbackAmount = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
				.setRoyaltyFee(RoyaltyFee.newBuilder()
						.setExchangeValueFraction(Fraction.newBuilder().setNumerator(10).setDenominator(9))
						.setFallbackFee(
						FixedFee.newBuilder()
								.setAmount(-10)
								.build()))
				.build();
		assertFailsWith(
				() -> CustomFee.fromGrpc(royaltyWithNegativeFallbackAmount, collector),
				CUSTOM_FEE_MUST_BE_POSITIVE
		);
		final var royaltyFeeWithNegativeDenominator = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
				.setRoyaltyFee(RoyaltyFee.newBuilder()
						.setExchangeValueFraction(Fraction.newBuilder().setNumerator(10).setDenominator(9))
						.setFallbackFee(
								FixedFee.newBuilder()
										.setAmount(-10)
										.build()))
				.build();
		assertFailsWith(
				() -> CustomFee.fromGrpc(royaltyFeeWithNegativeDenominator, collector),
				CUSTOM_FEE_MUST_BE_POSITIVE
		);
		
		final var zeroDivisibleRoyaltyFee = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
				.setRoyaltyFee(RoyaltyFee.newBuilder()
						.setExchangeValueFraction(Fraction.newBuilder().setNumerator(10).setDenominator(0))
						.setFallbackFee(
								FixedFee.newBuilder()
										.setAmount(10)
										.build()))
				.build();
		assertFailsWith(
				() -> CustomFee.fromGrpc(zeroDivisibleRoyaltyFee, collector),
				FRACTION_DIVIDES_BY_ZERO
		);
	}

	@Test
	void okCases() {
		var fixedFeeGrpc = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
				.setFixedFee(FixedFee.newBuilder()
						.setDenominatingTokenId(denomId.asGrpcToken())
						.setAmount(10)
						.build())
				.build();
		final var fee = CustomFee.fromGrpc(fixedFeeGrpc, collector);
		assertNotNull(fee);
		assertNotNull(fee.getFixedFee());
		assertNotNull(fee.getCollector());
		assertFalse(fee.shouldCollectorBeAutoAssociated());

		var fractionalFeeGrpc = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
				.setFractionalFee(FractionalFee.newBuilder()
						.setMinimumAmount(5)
						.setMaximumAmount(15)
						.setFractionalAmount(Fraction.newBuilder()
								.setDenominator(10)
								.setNumerator(10)
								.build()))
				.build();
		final var fractFee = CustomFee.fromGrpc(fractionalFeeGrpc, collector);
		assertNotNull(fractFee);
		assertNotNull(fractFee.getFractionalFee());
		assertTrue(fractFee.shouldCollectorBeAutoAssociated());
		
		final var royaltyFeeGrpc = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
				.setRoyaltyFee(RoyaltyFee.newBuilder()
						.setFallbackFee(FixedFee.newBuilder().setAmount(10).build())
						.setExchangeValueFraction(Fraction.newBuilder()
								.setNumerator(10)
								.setDenominator(20))
				)
				.build();
		final var royaltyFee = CustomFee.fromGrpc(royaltyFeeGrpc, collector);
		assertNotNull(royaltyFee);
		assertNotNull(royaltyFee.getRoyaltyFee());
		assertNotNull(royaltyFee.getRoyaltyFee().getFallbackFee());
		assertTrue(royaltyFee.shouldCollectorBeAutoAssociated());
	}

	@Test
	final void mappingToMerkle() {
		given(collector.getId()).willReturn(collectorId);
		final var merkleFixedHbar = FcCustomFee.fixedFee(10, null, collectorId.asEntityId());
		final var fee = CustomFee.fromGrpc(
				com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
						.setFixedFee(FixedFee.newBuilder().setAmount(10).build())
						.build(),
				collector
		);
		assertEquals(fee.toMerkle(), merkleFixedHbar);
		assertFalse(fee.shouldCollectorBeAutoAssociated());

		final var merkleFractional = FcCustomFee.
				fractionalFee(10, 1, 1, 10, false, collectorId.asEntityId());
		final var fractGrpc = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
				.setFractionalFee(FractionalFee.newBuilder()
						.setFractionalAmount(Fraction.newBuilder()
								.setNumerator(10)
								.setDenominator(1)
								.build())
						.setMaximumAmount(10)
						.setMinimumAmount(1)
						.build())
				.build();
		final var fractCustom = CustomFee.fromGrpc(fractGrpc, collector);
		assertEquals(fractCustom.toMerkle(), merkleFractional);

		final var royaltyFeeGrpc = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
				.setRoyaltyFee(RoyaltyFee.newBuilder()
						.setFallbackFee(FixedFee.newBuilder().setAmount(10)
								.setDenominatingTokenId(denomId.asGrpcToken()).build())
						.setExchangeValueFraction(Fraction.newBuilder()
								.setNumerator(10)
								.setDenominator(20))
				)
				.build();
		final var royaltyMerkle = FcCustomFee
				.royaltyFee(10, 20, FixedFeeSpec.fromGrpc(royaltyFeeGrpc.getRoyaltyFee().getFallbackFee()), collectorId.asEntityId());
		final var royaltyFee = CustomFee.fromGrpc(royaltyFeeGrpc, collector);
		assertEquals(royaltyFee.toMerkle(), royaltyMerkle);
	}

	@Test
	void objectContactWorks() {
		final var fee = CustomFee.fromGrpc(
				com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
						.setFixedFee(FixedFee.newBuilder().setAmount(10).build())
						.build(),
				collector
		);
		fee.setFixedFee(new com.hedera.services.store.models.fees.FixedFee(15, denomId));
		assertEquals(15, fee.getFixedFee().getAmount());

		fee.getFixedFee().setDenominatingTokenId(null);
		assertFalse(fee.getFixedFee().getDenominatingTokenId().isPresent());
		
		fee.setFixedFee(null);
		fee.setFractionalFee(new com.hedera.services.store.models.fees.FractionalFee(15, 10, 10, 10, false));
		assertEquals(15, fee.getFractionalFee().getMaximumAmount());
		
		CustomFee someCustomFee = new CustomFee(null, (com.hedera.services.store.models.fees.FixedFee) null);
		assertFalse(someCustomFee.shouldCollectorBeAutoAssociated());
	}

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}
}