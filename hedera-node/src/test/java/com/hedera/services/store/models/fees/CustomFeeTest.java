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
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
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
		var fixedFeeGrpc = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
				.setFixedFee(FixedFee.newBuilder().setAmount(-10).build())
				.build();
		assertFailsWith(
				() -> CustomFee.fromGrpc(fixedFeeGrpc, collector),
				CUSTOM_FEE_MUST_BE_POSITIVE
		);

		var feeGrpc2 = com.hederahashgraph.api.proto.java.CustomFee.newBuilder().setFractionalFee(
						FractionalFee.newBuilder()
								.setFractionalAmount(Fraction.newBuilder()
										.setNumerator(10)
										.setDenominator(0)
										.build())
								.build())
				.build();
		assertFailsWith(
				() -> CustomFee.fromGrpc(feeGrpc2, collector),
				FRACTION_DIVIDES_BY_ZERO
		);

		var feeGrpc3 = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
				.setFractionalFee(FractionalFee.newBuilder()
						.setMaximumAmount(-10)
						.setMinimumAmount(-15)
						.setFractionalAmount(Fraction.newBuilder()
								.setDenominator(1)
								.setNumerator(1)
								.build()))
				.build();
		assertFailsWith(
				() -> CustomFee.fromGrpc(feeGrpc3, collector),
				CUSTOM_FEE_MUST_BE_POSITIVE
		);

		var feeGrpc4 = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
				.setFractionalFee(FractionalFee.newBuilder()
						.setMinimumAmount(16)
						.setMaximumAmount(15)
						.setFractionalAmount(Fraction.newBuilder()
								.setDenominator(10)
								.setNumerator(10)
								.build()))
				.build();
		assertFailsWith(
				() -> CustomFee.fromGrpc(feeGrpc4, collector),
				FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT
		);

		var feeGrpc5 = com.hederahashgraph.api.proto.java.CustomFee.newBuilder().build();
		assertFailsWith(
				() -> CustomFee.fromGrpc(feeGrpc5, collector),
				CUSTOM_FEE_NOT_FULLY_SPECIFIED
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
		assertFalse(fee.shouldBeEnabled());

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
		assertTrue(fractFee.shouldBeEnabled());
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

		final var merkleFractional = FcCustomFee.
				fractionalFee(10, 1, 1, 10, collectorId.asEntityId());
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
		assertEquals(fee.getFixedFee().getAmount(), 15);

		fee.setFixedFee(null);
		fee.setFractionalFee(new com.hedera.services.store.models.fees.FractionalFee(15, 10, 10, 10));
		assertEquals(fee.getFractionalFee().getMaximumAmount(), 15);
	}

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}
}