/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.state.submerkle;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ROYALTY_FRACTION_CANNOT_EXCEED_ONE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Token;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoyaltyFeeSpecTest {
    @Mock private Account feeCollector;
    @Mock private Token token;
    @Mock private FixedFeeSpec fallbackSpec;
    @Mock private TypedTokenStore tokenStore;

    final RoyaltyFeeSpec subject = new RoyaltyFeeSpec(1, 10, fallbackSpec);

    @Test
    void validationRequiresNonFungibleUnique() {
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

        assertFalse(subject.hasFallbackFee());
        assertDoesNotThrow(() -> subject.validateWith(token, feeCollector, tokenStore));
    }

    @Test
    void sanityChecksEnforced() {
        assertFailsWith(() -> new RoyaltyFeeSpec(1, 0, null), FRACTION_DIVIDES_BY_ZERO);
        assertFailsWith(() -> new RoyaltyFeeSpec(2, 1, null), ROYALTY_FRACTION_CANNOT_EXCEED_ONE);
        assertFailsWith(() -> new RoyaltyFeeSpec(-1, 2, null), CUSTOM_FEE_MUST_BE_POSITIVE);
        assertFailsWith(() -> new RoyaltyFeeSpec(1, -2, null), CUSTOM_FEE_MUST_BE_POSITIVE);
    }

    @Test
    void gettersWork() {
        final var fallback = new FixedFeeSpec(1, MISSING_ENTITY_ID);
        final var a = new RoyaltyFeeSpec(1, 10, fallback);

        assertEquals(1, a.numerator());
        assertEquals(10, a.denominator());
        assertSame(fallback, a.fallbackFee());
        assertTrue(a.hasFallbackFee());
    }

    @Test
    void toStringWorks() {
        final var fallback = new FixedFeeSpec(1, MISSING_ENTITY_ID);
        final var a = new RoyaltyFeeSpec(1, 10, fallback);
        final var desired =
                "RoyaltyFeeSpec[numerator=1, denominator=10, "
                        + "fallbackFee=FixedFeeSpec{unitsToCollect=1, tokenDenomination=0.0.0}]";

        assertEquals(desired, a.toString());
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

        assertEquals(a, e);
        assertEquals(a, f);
        assertNotEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(a, d);
        assertNotEquals(null, a);
        assertNotEquals(new Object(), a);

        assertEquals(a.hashCode(), e.hashCode());
    }
}
