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
import static com.hedera.test.factories.fees.CustomFeeBuilder.fixedHbar;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fixedHts;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fractional;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_NOT_FULLY_SPECIFIED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.test.factories.fees.CustomFeeBuilder;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FcCustomFeeTest {
    private final long validNumerator = 5;
    private final long validDenominator = 100;
    private final long invalidDenominator = 0;
    private final long fixedUnitsToCollect = 7;
    private final long minimumUnitsToCollect = 1;
    private final long maximumUnitsToCollect = 55;
    private final boolean netOfTransfers = true;
    private final EntityId denom = new EntityId(0, 0, 3);
    private final TokenID grpcDenom = denom.toGrpcTokenId();
    private final EntityId feeCollector = new EntityId(0, 0, 6);
    private final AccountID grpcFeeCollector = feeCollector.toGrpcAccountId();
    private final CustomFeeBuilder builder = new CustomFeeBuilder(grpcFeeCollector);
    private final FixedFeeSpec fallbackFee = new FixedFeeSpec(1, MISSING_ENTITY_ID);
    private final Id tokenId = new Id(0, 0, 666);

    @Mock private SerializableDataInputStream din;
    @Mock private SerializableDataOutputStream dos;
    @Mock private Token token;
    @Mock private Account collectionAccount;
    @Mock private AccountStore accountStore;
    @Mock private TypedTokenStore tokenStore;
    @Mock private FixedFeeSpec fixedFeeSpec;
    @Mock private FractionalFeeSpec fractionalFeeSpec;
    @Mock private RoyaltyFeeSpec royaltyFeeSpec;

    final FcCustomFee subject =
            new FcCustomFee(
                    FcCustomFee.FeeType.FRACTIONAL_FEE,
                    feeCollector,
                    fixedFeeSpec,
                    fractionalFeeSpec,
                    royaltyFeeSpec,
                    false);

    @Test
    void fractionalRequiresCollectorAssociation() {
        assertTrue(subject.requiresCollectorAutoAssociation());
    }

    @Test
    void fixedRequiresCollectorAssociationIfWildcardUsed() {
        final var subject =
                new FcCustomFee(
                        FcCustomFee.FeeType.FIXED_FEE,
                        feeCollector,
                        fixedFeeSpec,
                        fractionalFeeSpec,
                        royaltyFeeSpec,
                        false);

        assertFalse(subject.requiresCollectorAutoAssociation());

        given(fixedFeeSpec.usedDenomWildcard()).willReturn(true);

        assertTrue(subject.requiresCollectorAutoAssociation());
    }

    @Test
    void royaltyRequiresCollectorAssociationIfFallbackWildcardUsed() {
        final var subject =
                new FcCustomFee(
                        FcCustomFee.FeeType.ROYALTY_FEE,
                        feeCollector,
                        fixedFeeSpec,
                        fractionalFeeSpec,
                        royaltyFeeSpec,
                        false);

        assertFalse(subject.requiresCollectorAutoAssociation());

        given(royaltyFeeSpec.hasFallbackFee()).willReturn(true);
        given(royaltyFeeSpec.fallbackFee()).willReturn(fixedFeeSpec);
        given(fixedFeeSpec.usedDenomWildcard()).willReturn(true);

        assertTrue(subject.requiresCollectorAutoAssociation());
    }

    @Test
    void requiresSomeFeeTypeInGrpc() {
        assertFailsWith(
                () -> FcCustomFee.fromGrpc(CustomFee.getDefaultInstance()),
                CUSTOM_FEE_NOT_FULLY_SPECIFIED);
    }

    @Test
    void validationRequiresNonFungibleForRoyalty() {
        given(accountStore.loadAccountOrFailWith(feeCollector.asId(), INVALID_CUSTOM_FEE_COLLECTOR))
                .willReturn(collectionAccount);

        final var subject =
                new FcCustomFee(
                        FcCustomFee.FeeType.ROYALTY_FEE,
                        feeCollector,
                        fixedFeeSpec,
                        fractionalFeeSpec,
                        royaltyFeeSpec,
                        false);

        assertFailsWith(
                () -> subject.validateWith(token, accountStore, tokenStore),
                CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
    }

    @Test
    void validationWorksAtUpdateForRoyalty() {
        given(token.isNonFungibleUnique()).willReturn(true);
        given(accountStore.loadAccountOrFailWith(feeCollector.asId(), INVALID_CUSTOM_FEE_COLLECTOR))
                .willReturn(collectionAccount);

        final var subject =
                new FcCustomFee(
                        FcCustomFee.FeeType.ROYALTY_FEE,
                        feeCollector,
                        fixedFeeSpec,
                        fractionalFeeSpec,
                        royaltyFeeSpec,
                        false);

        subject.validateWith(token, accountStore, tokenStore);

        verify(royaltyFeeSpec).validateWith(token, collectionAccount, tokenStore);
        assertSame(collectionAccount, subject.getValidatedCollector());
        subject.nullOutCollector();
        assertNull(subject.getValidatedCollector());
    }

    @Test
    void validationWorksAtCreationForRoyalty() {
        given(token.isNonFungibleUnique()).willReturn(true);
        given(accountStore.loadAccountOrFailWith(feeCollector.asId(), INVALID_CUSTOM_FEE_COLLECTOR))
                .willReturn(collectionAccount);

        final var subject =
                new FcCustomFee(
                        FcCustomFee.FeeType.ROYALTY_FEE,
                        feeCollector,
                        fixedFeeSpec,
                        fractionalFeeSpec,
                        royaltyFeeSpec,
                        false);

        subject.validateAndFinalizeWith(token, accountStore, tokenStore);

        verify(royaltyFeeSpec).validateAndFinalizeWith(token, collectionAccount, tokenStore);
    }

    @Test
    void validationWorksAtCreationForFixed() {
        given(accountStore.loadAccountOrFailWith(feeCollector.asId(), INVALID_CUSTOM_FEE_COLLECTOR))
                .willReturn(collectionAccount);

        final var subject =
                new FcCustomFee(
                        FcCustomFee.FeeType.FIXED_FEE,
                        feeCollector,
                        fixedFeeSpec,
                        fractionalFeeSpec,
                        royaltyFeeSpec,
                        false);

        subject.validateAndFinalizeWith(token, accountStore, tokenStore);

        verify(fixedFeeSpec).validateAndFinalizeWith(token, collectionAccount, tokenStore);
    }

    @Test
    void validationWorksAtUpdateForFixed() {
        given(accountStore.loadAccountOrFailWith(feeCollector.asId(), INVALID_CUSTOM_FEE_COLLECTOR))
                .willReturn(collectionAccount);

        final var subject =
                new FcCustomFee(
                        FcCustomFee.FeeType.FIXED_FEE,
                        feeCollector,
                        fixedFeeSpec,
                        fractionalFeeSpec,
                        royaltyFeeSpec,
                        false);

        subject.validateWith(token, accountStore, tokenStore);

        verify(fixedFeeSpec).validateWith(collectionAccount, tokenStore);
    }

    @Test
    void validationRequiresFungibleForFractional() {
        given(accountStore.loadAccountOrFailWith(feeCollector.asId(), INVALID_CUSTOM_FEE_COLLECTOR))
                .willReturn(collectionAccount);

        final var subject =
                new FcCustomFee(
                        FcCustomFee.FeeType.FRACTIONAL_FEE,
                        feeCollector,
                        fixedFeeSpec,
                        fractionalFeeSpec,
                        royaltyFeeSpec,
                        false);

        assertFailsWith(
                () -> subject.validateWith(token, accountStore, tokenStore),
                CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON);
    }

    @Test
    void validationRequiresAssociatedCollectorAtUpdateForFractional() {
        given(token.isFungibleCommon()).willReturn(true);
        given(accountStore.loadAccountOrFailWith(feeCollector.asId(), INVALID_CUSTOM_FEE_COLLECTOR))
                .willReturn(collectionAccount);

        final var subject =
                new FcCustomFee(
                        FcCustomFee.FeeType.FRACTIONAL_FEE,
                        feeCollector,
                        fixedFeeSpec,
                        fractionalFeeSpec,
                        royaltyFeeSpec,
                        false);

        assertFailsWith(
                () -> subject.validateWith(token, accountStore, tokenStore),
                TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
    }

    @Test
    void validationWorksAtUpdateForWellBehavedFractional() {
        given(token.isFungibleCommon()).willReturn(true);
        given(tokenStore.hasAssociation(token, collectionAccount)).willReturn(true);
        given(accountStore.loadAccountOrFailWith(feeCollector.asId(), INVALID_CUSTOM_FEE_COLLECTOR))
                .willReturn(collectionAccount);

        final var subject =
                new FcCustomFee(
                        FcCustomFee.FeeType.FRACTIONAL_FEE,
                        feeCollector,
                        fixedFeeSpec,
                        fractionalFeeSpec,
                        royaltyFeeSpec,
                        false);

        assertDoesNotThrow(() -> subject.validateWith(token, accountStore, tokenStore));
    }

    @Test
    void grpcConversionWorksForFixed() {
        final var wildcardId = new EntityId(0, 0, 0);
        final var expectedHtsSubject =
                FcCustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector, false);
        final var expectedHtsSameTokenSubject =
                FcCustomFee.fixedFee(fixedUnitsToCollect, wildcardId, feeCollector, false);
        final var expectedHbarSubject =
                FcCustomFee.fixedFee(fixedUnitsToCollect, null, feeCollector, false);
        final var htsGrpc = builder.withFixedFee(fixedHts(grpcDenom, fixedUnitsToCollect));
        final var htsSameTokenGrpc = builder.withFixedFee(fixedHts(fixedUnitsToCollect));
        final var hbarGrpc = builder.withFixedFee(fixedHbar(fixedUnitsToCollect));

        final var htsSubject = FcCustomFee.fromGrpc(htsGrpc);
        final var htsSameTokenSubject = FcCustomFee.fromGrpc(htsSameTokenGrpc);
        final var hbarSubject = FcCustomFee.fromGrpc(hbarGrpc);

        assertEquals(expectedHtsSubject, htsSubject);
        assertEquals(expectedHtsSameTokenSubject, htsSameTokenSubject);
        assertEquals(expectedHbarSubject, hbarSubject);
    }

    @Test
    void grpcReprWorksForRoyaltyNoFallback() {
        // setup:
        final var royaltyGrpc =
                builder.withRoyaltyFee(
                        RoyaltyFee.newBuilder()
                                .setExchangeValueFraction(
                                        Fraction.newBuilder()
                                                .setNumerator(validNumerator)
                                                .setDenominator(validDenominator)));

        // given:
        final var royaltySubject =
                FcCustomFee.royaltyFee(validNumerator, validDenominator, null, feeCollector, false);

        // when:
        final var repr = royaltySubject.asGrpc();

        // then:
        assertEquals(royaltyGrpc, repr);
    }

    @Test
    void grpcConversionWorksForRoyaltyNoFallback() {
        // setup:
        final var targetId = new EntityId(7, 8, 9);
        final var expectedRoyaltySubject =
                FcCustomFee.royaltyFee(validNumerator, validDenominator, null, feeCollector, false);

        // given:
        final var royaltyGrpc =
                builder.withRoyaltyFee(
                        RoyaltyFee.newBuilder()
                                .setExchangeValueFraction(
                                        Fraction.newBuilder()
                                                .setNumerator(validNumerator)
                                                .setDenominator(validDenominator)));

        // when:
        final var actualSubject = FcCustomFee.fromGrpc(royaltyGrpc);

        // then:
        assertEquals(expectedRoyaltySubject, actualSubject);
    }

    @Test
    void grpcConversionWorksForRoyalty() {
        // setup:
        final var targetId = new EntityId(7, 8, 9);
        final var expectedRoyaltySubject =
                FcCustomFee.royaltyFee(
                        validNumerator, validDenominator, fallbackFee, feeCollector, false);

        // given:
        final var royaltyGrpc =
                builder.withRoyaltyFee(
                        RoyaltyFee.newBuilder()
                                .setExchangeValueFraction(
                                        Fraction.newBuilder()
                                                .setNumerator(validNumerator)
                                                .setDenominator(validDenominator))
                                .setFallbackFee(
                                        FixedFee.newBuilder()
                                                .setAmount(fallbackFee.getUnitsToCollect())
                                                .setDenominatingTokenId(
                                                        fallbackFee
                                                                .getTokenDenomination()
                                                                .toGrpcTokenId())));

        // when:
        final var actualSubject = FcCustomFee.fromGrpc(royaltyGrpc);

        // then:
        assertEquals(expectedRoyaltySubject, actualSubject);
    }

    @Test
    void grpcReprWorksForRoyalty() {
        // setup:
        final var royaltyGrpc =
                builder.withRoyaltyFee(
                        RoyaltyFee.newBuilder()
                                .setExchangeValueFraction(
                                        Fraction.newBuilder()
                                                .setNumerator(validNumerator)
                                                .setDenominator(validDenominator))
                                .setFallbackFee(
                                        FixedFee.newBuilder()
                                                .setAmount(fallbackFee.getUnitsToCollect())
                                                .setDenominatingTokenId(
                                                        fallbackFee
                                                                .getTokenDenomination()
                                                                .toGrpcTokenId())));

        // given:
        final var royaltySubject =
                FcCustomFee.royaltyFee(
                        validNumerator, validDenominator, fallbackFee, feeCollector, false);

        // when:
        final var repr = royaltySubject.asGrpc();

        // then:
        assertEquals(royaltyGrpc, repr);
    }

    @Test
    void grpcReprWorksForFixedHbar() {
        final var expected = builder.withFixedFee(fixedHbar(fixedUnitsToCollect));
        final var hbarFee = FcCustomFee.fixedFee(fixedUnitsToCollect, null, feeCollector, false);

        final var actual = hbarFee.asGrpc();

        assertEquals(expected, actual);
    }

    @Test
    void grpcReprWorksForFixedHts() {
        final var expected = builder.withFixedFee(fixedHts(grpcDenom, fixedUnitsToCollect));
        final var htsFee =
                FcCustomFee.fixedFee(
                        fixedUnitsToCollect,
                        EntityId.fromGrpcTokenId(grpcDenom),
                        feeCollector,
                        false);

        final var actual = htsFee.asGrpc();

        assertEquals(expected, actual);
    }

    @Test
    void grpcReprWorksForFractional() {
        final var expected =
                builder.withFractionalFee(
                        fractional(validNumerator, validDenominator)
                                .setMinimumAmount(minimumUnitsToCollect)
                                .setMaximumAmount(maximumUnitsToCollect)
                                .setNetOfTransfers(netOfTransfers));
        final var fractionalFee =
                FcCustomFee.fractionalFee(
                        validNumerator,
                        validDenominator,
                        minimumUnitsToCollect,
                        maximumUnitsToCollect,
                        netOfTransfers,
                        feeCollector,
                        false);

        final var actual = fractionalFee.asGrpc();

        assertEquals(expected, actual);
    }

    @Test
    void grpcReprWorksForFractionalNoMax() {
        final var expected =
                builder.withFractionalFee(
                        fractional(validNumerator, validDenominator)
                                .setMinimumAmount(minimumUnitsToCollect)
                                .setNetOfTransfers(netOfTransfers));
        final var fractionalFee =
                FcCustomFee.fractionalFee(
                        validNumerator,
                        validDenominator,
                        minimumUnitsToCollect,
                        Long.MAX_VALUE,
                        netOfTransfers,
                        feeCollector,
                        false);

        final var actual = fractionalFee.asGrpc();

        assertEquals(expected, actual);
    }

    @Test
    void grpcConversionWorksForFractional() {
        final var expectedExplicitMaxSubject =
                FcCustomFee.fractionalFee(
                        validNumerator,
                        validDenominator,
                        minimumUnitsToCollect,
                        maximumUnitsToCollect,
                        netOfTransfers,
                        feeCollector,
                        false);
        final var expectedNoExplicitMaxSubject =
                FcCustomFee.fractionalFee(
                        validNumerator,
                        validDenominator,
                        minimumUnitsToCollect,
                        Long.MAX_VALUE,
                        !netOfTransfers,
                        feeCollector,
                        false);
        final var grpcWithExplicitMax =
                builder.withFractionalFee(
                        fractional(validNumerator, validDenominator)
                                .setMinimumAmount(minimumUnitsToCollect)
                                .setMaximumAmount(maximumUnitsToCollect)
                                .setNetOfTransfers(netOfTransfers));
        final var grpcWithoutExplicitMax =
                builder.withFractionalFee(
                        fractional(validNumerator, validDenominator)
                                .setMinimumAmount(minimumUnitsToCollect)
                                .setNetOfTransfers(!netOfTransfers));

        final var explicitMaxSubject = FcCustomFee.fromGrpc(grpcWithExplicitMax);
        final var noExplicitMaxSubject = FcCustomFee.fromGrpc(grpcWithoutExplicitMax);

        assertEquals(expectedExplicitMaxSubject, explicitMaxSubject);
        assertEquals(expectedNoExplicitMaxSubject, noExplicitMaxSubject);
    }

    @Test
    void liveFireSerdesWorkForRoyaltyWithFallback() throws IOException {
        final var subject =
                FcCustomFee.royaltyFee(
                        validNumerator,
                        validDenominator,
                        new FixedFeeSpec(123, denom),
                        feeCollector,
                        false);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);
        subject.serialize(dos);
        dos.flush();
        final var bytes = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        final var newSubject = new FcCustomFee();
        newSubject.deserialize(din, FcCustomFee.CURRENT_VERSION);

        assertEquals(subject.getRoyaltyFeeSpec(), newSubject.getRoyaltyFeeSpec());
        assertEquals(subject.getFeeCollector(), newSubject.getFeeCollector());
    }

    @Test
    void liveFireSerdesWorkForRoyaltyNoFallback() throws IOException {
        final var subject =
                FcCustomFee.royaltyFee(validNumerator, validDenominator, null, feeCollector, false);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);
        subject.serialize(dos);
        dos.flush();
        final var bytes = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        final var newSubject = new FcCustomFee();
        newSubject.deserialize(din, FcCustomFee.CURRENT_VERSION);

        assertEquals(subject.getRoyaltyFeeSpec(), newSubject.getRoyaltyFeeSpec());
        assertEquals(subject.getFeeCollector(), newSubject.getFeeCollector());
    }

    @Test
    void liveFireSerdesWorkForFractional() throws IOException {
        final var subject =
                FcCustomFee.fractionalFee(
                        validNumerator,
                        validDenominator,
                        minimumUnitsToCollect,
                        maximumUnitsToCollect,
                        netOfTransfers,
                        feeCollector,
                        false);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);
        subject.serialize(dos);
        dos.flush();
        final var bytes = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        final var newSubject = new FcCustomFee();
        newSubject.deserialize(din, FcCustomFee.CURRENT_VERSION);

        assertEquals(subject.getFractionalFeeSpec(), newSubject.getFractionalFeeSpec());
        assertEquals(subject.getFeeCollector(), newSubject.getFeeCollector());
    }

    @Test
    void liveFireSerdesWorkForFixed() throws IOException {
        final var fixedSubject =
                FcCustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector, false);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);
        fixedSubject.serialize(dos);
        dos.flush();
        final var bytes = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        final var newSubject = new FcCustomFee();
        newSubject.deserialize(din, FcCustomFee.CURRENT_VERSION);

        assertEquals(fixedSubject.getFixedFeeSpec(), newSubject.getFixedFeeSpec());
        assertEquals(fixedSubject.getFeeCollector(), newSubject.getFeeCollector());
    }

    @Test
    void liveFireSerdesWorkForFixedWithNullDenom() throws IOException {
        final var fixedSubject =
                FcCustomFee.fixedFee(fixedUnitsToCollect, null, feeCollector, false);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);
        fixedSubject.serialize(dos);
        dos.flush();
        final var bytes = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        final var newSubject = new FcCustomFee();
        newSubject.deserialize(din, FcCustomFee.CURRENT_VERSION);

        assertEquals(fixedSubject.getFixedFeeSpec(), newSubject.getFixedFeeSpec());
        assertEquals(fixedSubject.getFeeCollector(), newSubject.getFeeCollector());
    }

    @Test
    void deserializeWorksAsExpectedForFixed() throws IOException {
        final var expectedFixedSpec = new FixedFeeSpec(fixedUnitsToCollect, denom);
        given(din.readByte()).willReturn(FcCustomFee.FIXED_CODE);
        given(din.readLong()).willReturn(fixedUnitsToCollect);
        given(din.readSerializable(anyBoolean(), Mockito.any()))
                .willReturn(denom)
                .willReturn(feeCollector);

        final var subject = new FcCustomFee();
        subject.deserialize(din, FcCustomFee.CURRENT_VERSION);

        assertEquals(FcCustomFee.FeeType.FIXED_FEE, subject.getFeeType());
        assertEquals(expectedFixedSpec, subject.getFixedFeeSpec());
        assertNull(subject.getFractionalFeeSpec());
        assertEquals(feeCollector, subject.getFeeCollector());
    }

    @Test
    void deserializeWorksAsExpectedForFractional() throws IOException {
        final var expectedFractionalSpec =
                new FractionalFeeSpec(
                        validNumerator,
                        validDenominator,
                        minimumUnitsToCollect,
                        maximumUnitsToCollect,
                        netOfTransfers);
        given(din.readByte()).willReturn(FcCustomFee.FRACTIONAL_CODE);
        given(din.readBoolean()).willReturn(netOfTransfers);
        given(din.readLong())
                .willReturn(validNumerator)
                .willReturn(validDenominator)
                .willReturn(minimumUnitsToCollect)
                .willReturn(maximumUnitsToCollect);
        given(din.readSerializable(anyBoolean(), Mockito.any())).willReturn(feeCollector);

        final var subject = new FcCustomFee();
        subject.deserialize(din, FcCustomFee.CURRENT_VERSION);

        assertEquals(FcCustomFee.FeeType.FRACTIONAL_FEE, subject.getFeeType());
        assertEquals(expectedFractionalSpec, subject.getFractionalFeeSpec());
        assertNull(subject.getFixedFeeSpec());
        assertEquals(feeCollector, subject.getFeeCollector());
    }

    @Test
    void deserializeWorksAsExpectedForFractionalFromRelease016x() throws IOException {
        final var expectedFractionalSpec =
                new FractionalFeeSpec(
                        validNumerator,
                        validDenominator,
                        minimumUnitsToCollect,
                        maximumUnitsToCollect,
                        !netOfTransfers);
        given(din.readByte()).willReturn(FcCustomFee.FRACTIONAL_CODE);
        given(din.readLong())
                .willReturn(validNumerator)
                .willReturn(validDenominator)
                .willReturn(minimumUnitsToCollect)
                .willReturn(maximumUnitsToCollect);
        given(din.readSerializable(anyBoolean(), Mockito.any())).willReturn(feeCollector);

        final var subject = new FcCustomFee();
        subject.deserialize(din, FcCustomFee.RELEASE_016X_VERSION);

        assertEquals(FcCustomFee.FeeType.FRACTIONAL_FEE, subject.getFeeType());
        assertEquals(expectedFractionalSpec, subject.getFractionalFeeSpec());
        assertNull(subject.getFixedFeeSpec());
        assertEquals(feeCollector, subject.getFeeCollector());
        // and:
        verify(din, never()).readBoolean();
    }

    @Test
    void serializeWorksAsExpectedForFractional() throws IOException {
        InOrder inOrder = Mockito.inOrder(dos);
        final var subject =
                FcCustomFee.fractionalFee(
                        validNumerator,
                        validDenominator,
                        minimumUnitsToCollect,
                        maximumUnitsToCollect,
                        netOfTransfers,
                        feeCollector,
                        false);

        subject.serialize(dos);

        inOrder.verify(dos).writeByte(FcCustomFee.FRACTIONAL_CODE);
        inOrder.verify(dos).writeLong(validNumerator);
        inOrder.verify(dos).writeLong(validDenominator);
        inOrder.verify(dos).writeLong(minimumUnitsToCollect);
        inOrder.verify(dos).writeLong(maximumUnitsToCollect);
        inOrder.verify(dos).writeSerializable(feeCollector, true);
    }

    @Test
    void serializeWorksAsExpectedForFixed() throws IOException {
        InOrder inOrder = Mockito.inOrder(dos);
        final var subject = FcCustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector, false);

        subject.serialize(dos);

        inOrder.verify(dos).writeByte(FcCustomFee.FIXED_CODE);
        inOrder.verify(dos).writeLong(fixedUnitsToCollect);
        inOrder.verify(dos).writeSerializable(denom, true);
        inOrder.verify(dos).writeSerializable(feeCollector, true);
    }

    @Test
    void merkleMethodsWork() {
        final var subject = FcCustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector, false);

        assertEquals(FcCustomFee.CURRENT_VERSION, subject.getVersion());
        assertEquals(FcCustomFee.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }

    @Test
    void fixedFactoryWorks() {
        final var expectedFixedSpec = new FixedFeeSpec(fixedUnitsToCollect, denom);
        final var fixedSubject =
                FcCustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector, false);

        assertEquals(FcCustomFee.FeeType.FIXED_FEE, fixedSubject.getFeeType());
        assertEquals(expectedFixedSpec, fixedSubject.getFixedFeeSpec());
        assertNull(fixedSubject.getFractionalFeeSpec());
        assertEquals(feeCollector, fixedSubject.getFeeCollector());
    }

    @Test
    void fractionalFactoryWorks() {
        final var expectedFractionalSpec =
                new FractionalFeeSpec(
                        validNumerator,
                        validDenominator,
                        minimumUnitsToCollect,
                        maximumUnitsToCollect,
                        netOfTransfers);
        final var fractionalSubject =
                FcCustomFee.fractionalFee(
                        validNumerator,
                        validDenominator,
                        minimumUnitsToCollect,
                        maximumUnitsToCollect,
                        netOfTransfers,
                        feeCollector,
                        false);

        assertEquals(FcCustomFee.FeeType.FRACTIONAL_FEE, fractionalSubject.getFeeType());
        assertEquals(expectedFractionalSpec, fractionalSubject.getFractionalFeeSpec());
        assertNull(fractionalSubject.getFixedFeeSpec());
        assertEquals(feeCollector, fractionalSubject.getFeeCollector());
    }

    @Test
    void royaltyFactoryWorks() {
        final var expectedRoyaltySpec =
                new RoyaltyFeeSpec(validNumerator, validDenominator, fallbackFee);

        // given:
        final var royaltySubject =
                FcCustomFee.royaltyFee(
                        validNumerator, validDenominator, fallbackFee, feeCollector, false);

        // expect:
        assertEquals(FcCustomFee.FeeType.ROYALTY_FEE, royaltySubject.getFeeType());
        assertEquals(expectedRoyaltySpec, royaltySubject.getRoyaltyFeeSpec());
    }

    @Test
    void toStringsWork() {
        final var fractionalSpec =
                new FractionalFeeSpec(
                        validNumerator,
                        validDenominator,
                        minimumUnitsToCollect,
                        maximumUnitsToCollect,
                        netOfTransfers);
        final var fixedSpec = new FixedFeeSpec(fixedUnitsToCollect, denom);
        final var desiredFracRepr =
                "FractionalFeeSpec{numerator=5, denominator=100, minimumUnitsToCollect=1, "
                        + "maximumUnitsToCollect=55, netOfTransfers=true}";
        final var desiredFixedRepr = "FixedFeeSpec{unitsToCollect=7, tokenDenomination=0.0.3}";

        assertEquals(desiredFixedRepr, fixedSpec.toString());
        assertEquals(desiredFracRepr, fractionalSpec.toString());
    }

    @Test
    void failFastIfInvalidFractionUsed() {
        assertFailsWith(
                () ->
                        new FractionalFeeSpec(
                                validNumerator,
                                invalidDenominator,
                                minimumUnitsToCollect,
                                maximumUnitsToCollect,
                                netOfTransfers),
                FRACTION_DIVIDES_BY_ZERO);
        assertFailsWith(
                () ->
                        new FractionalFeeSpec(
                                -validNumerator,
                                validDenominator,
                                minimumUnitsToCollect,
                                maximumUnitsToCollect,
                                netOfTransfers),
                CUSTOM_FEE_MUST_BE_POSITIVE);
        assertFailsWith(
                () ->
                        new FractionalFeeSpec(
                                validNumerator,
                                validDenominator,
                                minimumUnitsToCollect,
                                -maximumUnitsToCollect,
                                netOfTransfers),
                CUSTOM_FEE_MUST_BE_POSITIVE);
        assertFailsWith(
                () ->
                        new FractionalFeeSpec(
                                validNumerator,
                                -validDenominator,
                                minimumUnitsToCollect,
                                maximumUnitsToCollect,
                                netOfTransfers),
                CUSTOM_FEE_MUST_BE_POSITIVE);
        assertFailsWith(
                () ->
                        new FractionalFeeSpec(
                                validNumerator,
                                validDenominator,
                                -minimumUnitsToCollect,
                                maximumUnitsToCollect,
                                netOfTransfers),
                CUSTOM_FEE_MUST_BE_POSITIVE);
        assertFailsWith(
                () ->
                        new FractionalFeeSpec(
                                validNumerator,
                                validDenominator,
                                maximumUnitsToCollect,
                                minimumUnitsToCollect,
                                netOfTransfers),
                FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT);
    }

    @Test
    void gettersWork() {
        final var fractionalSpec =
                new FractionalFeeSpec(
                        validNumerator,
                        validDenominator,
                        minimumUnitsToCollect,
                        maximumUnitsToCollect,
                        netOfTransfers);
        final var fixedSpec = new FixedFeeSpec(fixedUnitsToCollect, denom);

        assertEquals(validNumerator, fractionalSpec.getNumerator());
        assertEquals(validDenominator, fractionalSpec.getDenominator());
        assertEquals(minimumUnitsToCollect, fractionalSpec.getMinimumAmount());
        assertEquals(maximumUnitsToCollect, fractionalSpec.getMaximumUnitsToCollect());
        assertEquals(fixedUnitsToCollect, fixedSpec.getUnitsToCollect());
        assertEquals(denom, fixedSpec.getTokenDenomination());
        assertEquals(Id.fromGrpcAccount(IdUtils.asAccount("0.0.6")), subject.getFeeCollectorAsId());
    }

    @Test
    void hashCodeWorks() {
        final var fractionalSpec =
                new FractionalFeeSpec(
                        validNumerator,
                        validDenominator,
                        minimumUnitsToCollect,
                        maximumUnitsToCollect,
                        netOfTransfers);
        final var fixedSpec = new FixedFeeSpec(fixedUnitsToCollect, denom);

        assertDoesNotThrow(fractionalSpec::hashCode);
        assertDoesNotThrow(fixedSpec::hashCode);
    }

    @Test
    void fixedFeeEqualsWorks() {
        final var aFixedSpec = new FixedFeeSpec(fixedUnitsToCollect, denom);
        final var bFixedSpec = new FixedFeeSpec(fixedUnitsToCollect, denom);
        final var cFixedSpec = new FixedFeeSpec(fixedUnitsToCollect + 1, denom);
        final var dFixedSpec = new FixedFeeSpec(fixedUnitsToCollect, null);
        final var eFixedSpec = aFixedSpec;

        assertEquals(aFixedSpec, bFixedSpec);
        assertEquals(aFixedSpec, eFixedSpec);
        assertNotEquals(null, aFixedSpec);
        assertNotEquals(aFixedSpec, new Object());
        assertNotEquals(aFixedSpec, cFixedSpec);
        assertNotEquals(aFixedSpec, dFixedSpec);
    }

    @Test
    void fractionalFeeEqualsWorks() {
        long n = 3;
        long d = 7;
        long min = 22;
        long max = 99;
        final var aFractionalSpec = new FractionalFeeSpec(n, d, min, max, netOfTransfers);
        final var bFractionalSpec = new FractionalFeeSpec(n + 1, d, min, max, netOfTransfers);
        final var cFractionalSpec = new FractionalFeeSpec(n, d + 1, min, max, netOfTransfers);
        final var dFractionalSpec = new FractionalFeeSpec(n, d, min + 1, max, netOfTransfers);
        final var eFractionalSpec = new FractionalFeeSpec(n, d, min, max + 1, netOfTransfers);
        final var hFractionalSpec = new FractionalFeeSpec(n, d, min, max, !netOfTransfers);
        final var fFractionalSpec = new FractionalFeeSpec(n, d, min, max, netOfTransfers);
        final var gFractionalSpec = aFractionalSpec;

        assertEquals(aFractionalSpec, fFractionalSpec);
        assertEquals(aFractionalSpec, gFractionalSpec);
        assertNotEquals(null, aFractionalSpec);
        assertNotEquals(aFractionalSpec, new Object());
        assertNotEquals(aFractionalSpec, bFractionalSpec);
        assertNotEquals(aFractionalSpec, cFractionalSpec);
        assertNotEquals(aFractionalSpec, dFractionalSpec);
        assertNotEquals(aFractionalSpec, eFractionalSpec);
        assertNotEquals(aFractionalSpec, hFractionalSpec);
    }

    @Test
    void customFeeEqualsWorks() {
        long n = 3;
        long d = 7;
        long min = 22;
        long max = 99;
        final var aFeeCollector = new EntityId(1, 2, 3);
        final var bFeeCollector = new EntityId(2, 3, 4);
        final var aCustomFee =
                FcCustomFee.fixedFee(fixedUnitsToCollect, denom, aFeeCollector, false);
        final var bCustomFee =
                FcCustomFee.fixedFee(fixedUnitsToCollect + 1, denom, aFeeCollector, false);
        final var cCustomFee =
                FcCustomFee.fixedFee(fixedUnitsToCollect, denom, bFeeCollector, false);
        final var dCustomFee =
                FcCustomFee.fractionalFee(n, d, min, max, netOfTransfers, aFeeCollector, false);
        final var eCustomFee = aCustomFee;
        final var fCustomFee =
                FcCustomFee.fixedFee(fixedUnitsToCollect, denom, aFeeCollector, false);

        assertEquals(aCustomFee, eCustomFee);
        assertEquals(aCustomFee, fCustomFee);
        assertNotEquals(null, aCustomFee);
        assertNotEquals(aCustomFee, new Object());
        assertNotEquals(aCustomFee, bCustomFee);
        assertNotEquals(aCustomFee, cCustomFee);
        assertNotEquals(aCustomFee, dCustomFee);
        assertEquals(aCustomFee.hashCode(), fCustomFee.hashCode());
    }

    @Test
    void toStringWorks() {
        final var denom = new EntityId(0, 0, 333);
        final var fractionalFee =
                FcCustomFee.fractionalFee(
                        validNumerator,
                        validDenominator,
                        minimumUnitsToCollect,
                        maximumUnitsToCollect,
                        netOfTransfers,
                        feeCollector,
                        false);
        final var fixedHbarFee =
                FcCustomFee.fixedFee(fixedUnitsToCollect, null, feeCollector, false);
        final var fixedHtsFee =
                FcCustomFee.fixedFee(fixedUnitsToCollect, denom, feeCollector, false);
        final var expectedFractional =
                "FcCustomFee{feeType=FRACTIONAL_FEE, fractionalFee=FractionalFeeSpec{numerator=5,"
                        + " denominator=100, minimumUnitsToCollect=1, maximumUnitsToCollect=55,"
                        + " netOfTransfers=true}, feeCollector=EntityId{shard=0, realm=0, num=6},"
                        + " allCollectorsAreExempt=false}";
        final var expectedFixedHbar =
                "FcCustomFee{feeType=FIXED_FEE, fixedFee=FixedFeeSpec{unitsToCollect=7, "
                        + "tokenDenomination=ℏ}, feeCollector=EntityId{shard=0, realm=0, num=6}, "
                        + "allCollectorsAreExempt=false}";
        final var expectedFixedHts =
                "FcCustomFee{feeType=FIXED_FEE, fixedFee=FixedFeeSpec{unitsToCollect=7,"
                        + " tokenDenomination=0.0.333}, feeCollector=EntityId{shard=0, realm=0,"
                        + " num=6}, allCollectorsAreExempt=false}";

        assertEquals(expectedFractional, fractionalFee.toString());
        assertEquals(expectedFixedHts, fixedHtsFee.toString());
        assertEquals(expectedFixedHbar, fixedHbarFee.toString());
    }
}
