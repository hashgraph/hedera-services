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

import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hederahashgraph.api.proto.java.FixedFee;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FixedFeeSpecTest {
    private final Id provisionalId = new Id(0, 0, 666);
    private final EntityId selfDenom = new EntityId(0, 0, 0);
    private final EntityId otherDenom = new EntityId(0, 0, 1234);

    @Mock private Token token;
    @Mock private Account feeCollector;
    @Mock private TypedTokenStore tokenStore;

    @Test
    void validationRequiresFungibleDenom() {
        given(
                        tokenStore.loadTokenOrFailWith(
                                new Id(0, 0, otherDenom.num()), INVALID_TOKEN_ID_IN_CUSTOM_FEES))
                .willReturn(token);

        final var otherDenomSubject = new FixedFeeSpec(123, otherDenom);

        assertFailsWith(
                () -> otherDenomSubject.validateWith(feeCollector, tokenStore),
                CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
    }

    @Test
    void validationRequiresAssociatedFeeCollector() {
        given(
                        tokenStore.loadTokenOrFailWith(
                                new Id(0, 0, otherDenom.num()), INVALID_TOKEN_ID_IN_CUSTOM_FEES))
                .willReturn(token);
        given(token.isFungibleCommon()).willReturn(true);

        final var otherDenomSubject = new FixedFeeSpec(123, otherDenom);

        assertFailsWith(
                () -> otherDenomSubject.validateWith(feeCollector, tokenStore),
                TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
    }

    @Test
    void validationWorksWithWellBehaved() {
        given(
                        tokenStore.loadTokenOrFailWith(
                                new Id(0, 0, otherDenom.num()), INVALID_TOKEN_ID_IN_CUSTOM_FEES))
                .willReturn(token);
        given(token.isFungibleCommon()).willReturn(true);
        given(tokenStore.hasAssociation(token, feeCollector)).willReturn(true);

        final var otherDenomSubject = new FixedFeeSpec(123, otherDenom);

        assertDoesNotThrow(() -> otherDenomSubject.validateWith(feeCollector, tokenStore));
    }

    @Test
    void finalizationRequiresFungibleDenomAtCreationWithOtherDenom() {
        given(
                        tokenStore.loadTokenOrFailWith(
                                new Id(0, 0, otherDenom.num()), INVALID_TOKEN_ID_IN_CUSTOM_FEES))
                .willReturn(token);

        final var otherDenomSubject = new FixedFeeSpec(123, otherDenom);

        assertFailsWith(
                () -> otherDenomSubject.validateAndFinalizeWith(token, feeCollector, tokenStore),
                CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
    }

    @Test
    void finalizationWorksWithFungibleDenomAtCreation() {
        given(token.isFungibleCommon()).willReturn(true);
        given(
                        tokenStore.loadTokenOrFailWith(
                                new Id(0, 0, otherDenom.num()), INVALID_TOKEN_ID_IN_CUSTOM_FEES))
                .willReturn(token);
        given(tokenStore.hasAssociation(token, feeCollector)).willReturn(true);

        final var otherDenomSubject = new FixedFeeSpec(123, otherDenom);

        assertDoesNotThrow(
                () -> otherDenomSubject.validateAndFinalizeWith(token, feeCollector, tokenStore));
    }

    @Test
    void finalizationRequiresFungibleDenomAtCreationWithSelfDenom() {
        final var selfDenomSubject = new FixedFeeSpec(123, selfDenom);

        assertFailsWith(
                () -> selfDenomSubject.validateAndFinalizeWith(token, feeCollector, tokenStore),
                CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
    }

    @Test
    void finalizationUpdatesDenomWithProvisionalIdIfFungible() {
        given(token.isFungibleCommon()).willReturn(true);
        given(token.getId()).willReturn(provisionalId);

        final var selfDenomSubject = new FixedFeeSpec(123, selfDenom);

        selfDenomSubject.validateAndFinalizeWith(token, feeCollector, tokenStore);

        verifyNoInteractions(tokenStore);
        assertEquals(provisionalId.num(), selfDenomSubject.getTokenDenomination().num());
        assertTrue(selfDenomSubject.usedDenomWildcard());
    }

    @Test
    void semanticValidationIsNoopForHbarFee() {
        final var subject = new FixedFeeSpec(123, null);

        subject.validateWith(feeCollector, tokenStore);
        subject.validateAndFinalizeWith(token, feeCollector, tokenStore);

        verifyNoInteractions(token, tokenStore);
    }

    @Test
    void constructorValidatesSyntax() {
        assertFailsWith(() -> new FixedFeeSpec(-1, null), CUSTOM_FEE_MUST_BE_POSITIVE);
    }

    @Test
    void factoryWorksForHbar() {
        // setup:
        final var hbarGrpc = FixedFee.newBuilder().setAmount(123).build();
        final var expected = new FixedFeeSpec(123, null);

        // when
        final var actual = FixedFeeSpec.fromGrpc(hbarGrpc);

        // then:
        assertEquals(expected, actual);
    }

    @Test
    void factoryWorksForHts() {
        // setup:
        final var denom = new EntityId(1, 2, 3);
        final var htsGrpc =
                FixedFee.newBuilder()
                        .setAmount(123)
                        .setDenominatingTokenId(denom.toGrpcTokenId())
                        .build();
        final var expected = new FixedFeeSpec(123, denom);

        // when
        final var actual = FixedFeeSpec.fromGrpc(htsGrpc);

        // then:
        assertEquals(expected, actual);
    }

    @Test
    void reprWorksForHbar() {
        // setup:
        final var hbarGrpc = FixedFee.newBuilder().setAmount(123).build();

        // given:
        final var subject = new FixedFeeSpec(123, null);

        // when:
        final var repr = subject.asGrpc();

        // then:
        assertEquals(hbarGrpc, repr);
    }

    @Test
    void reprWorksForHts() {
        // setup:
        final var denom = new EntityId(1, 2, 3);
        final var htsGrpc =
                FixedFee.newBuilder()
                        .setAmount(123)
                        .setDenominatingTokenId(denom.toGrpcTokenId())
                        .build();

        // given:
        final var subject = new FixedFeeSpec(123, denom);

        // when:
        final var repr = subject.asGrpc();

        // then:
        assertEquals(htsGrpc, repr);
    }
}
