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

import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Token;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ROYALTY_FRACTION_CANNOT_EXCEED_ONE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class RoyaltyFeeSpecTest {
	@Mock
	private Account feeCollector;
	@Mock
	private Token token;
	@Mock
	private FixedFeeSpec fallbackSpec;
	@Mock
	private TypedTokenStore tokenStore;

	@Test
	void validationRequiresNonFungibleUnique() {
		final var subject = new RoyaltyFeeSpec(1, 10, fallbackSpec);

		assertFailsWith(
				() -> subject.validateWith(token, feeCollector, tokenStore),
				CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
	}

	@Test
	void validationPropagatesToFallbackSpecOnUpdate() {
		given(token.isNonFungibleUnique()).willReturn(true);

		final var subject = new RoyaltyFeeSpec(1, 10, fallbackSpec);

		subject.validateWith(token, feeCollector, tokenStore);

		verify(fallbackSpec).validateWith(feeCollector, tokenStore);
	}

	@Test
	void validationPropagatesToFallbackSpecOnCreate() {
		given(token.isNonFungibleUnique()).willReturn(true);

		final var subject = new RoyaltyFeeSpec(1, 10, fallbackSpec);

		subject.validateAndFinalizeWith(token, feeCollector, tokenStore);

		verify(fallbackSpec).validateAndFinalizeWith(token, feeCollector, tokenStore);
	}

	@Test
	void validationOkWithNoFallback() {
		given(token.isNonFungibleUnique()).willReturn(true);

		final var subject = new RoyaltyFeeSpec(1, 10, null);

		assertDoesNotThrow(() -> subject.validateWith(token, feeCollector, tokenStore));
	}

	@Test
	void sanityChecksEnforced() {
		assertFailsWith(
				() -> new RoyaltyFeeSpec(1, 0, null),
				FRACTION_DIVIDES_BY_ZERO);
		assertFailsWith(
				() -> new RoyaltyFeeSpec(2, 1, null),
				ROYALTY_FRACTION_CANNOT_EXCEED_ONE);
		assertFailsWith(
				() -> new RoyaltyFeeSpec(-1, 2, null),
				CUSTOM_FEE_MUST_BE_POSITIVE);
		assertFailsWith(
				() -> new RoyaltyFeeSpec(1, -2, null),
				CUSTOM_FEE_MUST_BE_POSITIVE);
	}

	@Test
	void gettersWork() {
		final var fallback = new FixedFeeSpec(1, MISSING_ENTITY_ID);
		final var a = new RoyaltyFeeSpec(1, 10, fallback);

		assertEquals(1, a.getNumerator());
		assertEquals(10, a.getDenominator());
		assertSame(fallback, a.getFallbackFee());
	}

	@Test
	void objectContractMet() {
		final var fallback = new FixedFeeSpec(1, MISSING_ENTITY_ID);
		final var a = new RoyaltyFeeSpec(1, 10, fallback);
		final var b = new RoyaltyFeeSpec(2, 10, fallback);
		final var c = new RoyaltyFeeSpec(1, 11, fallback);
		final var d = new RoyaltyFeeSpec(1, 10, null);
		final var e = new RoyaltyFeeSpec(1, 10, fallback);
		final var f = a;

		Assertions.assertEquals(a, e);
		Assertions.assertEquals(a, f);
		Assertions.assertNotEquals(a, b);
		Assertions.assertNotEquals(a, c);
		Assertions.assertNotEquals(a, d);
		Assertions.assertNotEquals(null, a);
		Assertions.assertNotEquals(new Object(), a);

		Assertions.assertEquals(a.hashCode(), e.hashCode());
	}
}
