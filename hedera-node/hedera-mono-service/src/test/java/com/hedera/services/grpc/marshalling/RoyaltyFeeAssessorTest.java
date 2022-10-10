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
package com.hedera.services.grpc.marshalling;

import static com.hedera.services.store.models.Id.MISSING_ID;
import static com.hedera.test.utils.IdUtils.asAliasAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.protobuf.ByteString;
import com.hedera.services.fees.CustomFeePayerExemptions;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.state.submerkle.FixedFeeSpec;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoyaltyFeeAssessorTest {
    private List<FcAssessedCustomFee> accumulator = new ArrayList<>();

    @Mock private FixedFeeAssessor fixedFeeAssessor;
    @Mock private FungibleAdjuster fungibleAdjuster;
    @Mock private BalanceChangeManager changeManager;
    @Mock private CustomFeePayerExemptions customFeePayerExemptions;

    private RoyaltyFeeAssessor subject;

    @BeforeEach
    void setUp() {
        subject =
                new RoyaltyFeeAssessor(
                        fixedFeeAssessor, fungibleAdjuster, customFeePayerExemptions);
    }

    @Test
    void doesNothingIfNoValueExchangedAndNoFallback() {
        // setup:
        final CustomFeeMeta feeMeta =
                newRoyaltyCustomFeeMeta(
                        List.of(
                                FcCustomFee.fixedFee(1, null, otherCollector, false),
                                FcCustomFee.royaltyFee(1, 2, null, targetCollector, false)));

        // when:
        final var result = subject.assessAllRoyalties(trigger, feeMeta, changeManager, accumulator);

        // then:
        assertEquals(OK, result);
        // and:
        verifyNoInteractions(fungibleAdjuster);
        assertTrue(accumulator.isEmpty());
    }

    @Test
    void chargesHbarFallbackAsExpected() {
        // setup:
        final var fallback = new FixedFeeSpec(33, null);
        final CustomFeeMeta feeMeta =
                newRoyaltyCustomFeeMeta(
                        List.of(
                                FcCustomFee.fixedFee(1, null, otherCollector, false),
                                FcCustomFee.royaltyFee(1, 2, fallback, targetCollector, false)));

        // when:
        final var result = subject.assessAllRoyalties(trigger, feeMeta, changeManager, accumulator);

        // then:
        assertEquals(OK, result);
        // and:
        verify(fixedFeeAssessor)
                .assess(
                        funding,
                        feeMeta,
                        FcCustomFee.fixedFee(33, null, targetCollector, false),
                        changeManager,
                        accumulator);
    }

    @Test
    void abortsWithNecessaryResponseCodeIfNoCounterpartyId() {
        final var fallback = new FixedFeeSpec(33, null);
        final CustomFeeMeta feeMeta =
                newRoyaltyCustomFeeMeta(
                        List.of(FcCustomFee.royaltyFee(1, 2, fallback, targetCollector, false)));

        final var result =
                subject.assessAllRoyalties(htsPayerPlusChange, feeMeta, changeManager, accumulator);

        assertEquals(ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON, result);
    }

    @Test
    void chargesHtsFallbackAsExpected() {
        // setup:
        final var denom = new EntityId(1, 2, 3);
        final var fallback = new FixedFeeSpec(33, denom);
        final CustomFeeMeta feeMeta =
                newRoyaltyCustomFeeMeta(
                        List.of(
                                FcCustomFee.fixedFee(1, null, otherCollector, false),
                                FcCustomFee.royaltyFee(1, 2, fallback, targetCollector, false)));

        // when:
        final var result = subject.assessAllRoyalties(trigger, feeMeta, changeManager, accumulator);

        // then:
        assertEquals(OK, result);
        // and:
        verify(fixedFeeAssessor)
                .assess(
                        funding,
                        feeMeta,
                        FcCustomFee.fixedFee(33, denom, targetCollector, false),
                        changeManager,
                        accumulator);
    }

    @Test
    void failsIfFallbackNftTransferredToUnknownAlias() {
        // setup:
        final var denom = new EntityId(1, 2, 3);
        final var fallback = new FixedFeeSpec(33, denom);
        final CustomFeeMeta fees =
                newRoyaltyCustomFeeMeta(
                        List.of(
                                FcCustomFee.fixedFee(1, null, otherCollector, false),
                                FcCustomFee.royaltyFee(1, 2, fallback, targetCollector, false)));

        // when:
        final var result =
                subject.assessAllRoyalties(
                        triggerWithAliasTransfer, fees, changeManager, accumulator);

        // then:
        assertEquals(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE, result);
    }

    @Test
    void skipsIfRoyaltyAlreadyPaid() {
        // setup:
        final CustomFeeMeta feeMeta =
                newRoyaltyCustomFeeMeta(
                        List.of(
                                FcCustomFee.fixedFee(1, null, otherCollector, false),
                                FcCustomFee.royaltyFee(1, 2, null, targetCollector, false)));
        // and:
        final var reclaimable = changesNoLongerWithOriginalUnits();

        given(changeManager.isRoyaltyPaid(nonFungibleTokenId, payer)).willReturn(true);

        // when:
        final var result = subject.assessAllRoyalties(trigger, feeMeta, changeManager, accumulator);

        // then:
        assertEquals(OK, result);
        // and:
        assertEquals(originalUnits / 2, reclaimable.get(0).getAggregatedUnits());
        assertEquals(originalUnits / 2, reclaimable.get(1).getAggregatedUnits());
        // and:
        verifyNoInteractions(fungibleAdjuster);
        assertTrue(accumulator.isEmpty());
    }

    @Test
    void reclaimsFromOriginalCreditsWhenAvailable() {
        // setup:
        final CustomFeeMeta feeMeta =
                newRoyaltyCustomFeeMeta(
                        List.of(
                                FcCustomFee.fixedFee(1, null, otherCollector, false),
                                FcCustomFee.royaltyFee(1, 2, null, targetCollector, false)));
        // and:
        final var reclaimable = changesNoLongerWithOriginalUnits();

        given(changeManager.fungibleCreditsInCurrentLevel(payer)).willReturn(reclaimable);

        // when:
        final var result = subject.assessAllRoyalties(trigger, feeMeta, changeManager, accumulator);

        // then:
        assertEquals(OK, result);
        // and:
        assertEquals(0, reclaimable.get(0).getAggregatedUnits());
        assertEquals(0, reclaimable.get(1).getAggregatedUnits());
        // and:
        verify(fungibleAdjuster)
                .adjustedChange(
                        targetCollector.asId(),
                        MISSING_ID,
                        MISSING_ID,
                        originalUnits / 2,
                        changeManager);
        verify(fungibleAdjuster)
                .adjustedChange(
                        targetCollector.asId(),
                        MISSING_ID,
                        firstFungibleTokenId,
                        originalUnits / 2,
                        changeManager);
        verify(changeManager).isRoyaltyPaid(nonFungibleTokenId, payer);
        verify(changeManager).markRoyaltyPaid(nonFungibleTokenId, payer);
        // and:
        assertEquals(2, accumulator.size());
        assertEquals(hbarAssessed, accumulator.get(0));
        assertEquals(htsAssessed, accumulator.get(1));
    }

    @Test
    void doesntCollectRoyaltyIfOriginalPayerIsExempt() {
        // setup:
        final CustomFeeMeta feeMeta =
                newRoyaltyCustomFeeMeta(
                        List.of(
                                FcCustomFee.fixedFee(1, null, otherCollector, false),
                                FcCustomFee.royaltyFee(1, 2, null, targetCollector, false)));
        // and:
        final var reclaimable = changesNoLongerWithOriginalUnits();

        given(changeManager.fungibleCreditsInCurrentLevel(payer)).willReturn(reclaimable);
        final var royaltyFee = feeMeta.customFees().get(1);
        given(customFeePayerExemptions.isPayerExempt(feeMeta, royaltyFee, trigger.getAccount()))
                .willReturn(true);

        // when:
        final var result = subject.assessAllRoyalties(trigger, feeMeta, changeManager, accumulator);

        // then:
        assertEquals(OK, result);
        verifyNoInteractions(fungibleAdjuster);
        verify(changeManager).isRoyaltyPaid(nonFungibleTokenId, payer);
        verify(changeManager).markRoyaltyPaid(nonFungibleTokenId, payer);
        assertEquals(0, accumulator.size());
    }

    @Test
    void abortsWhenCreditsNotAvailable() {
        // setup:
        final CustomFeeMeta feeMeta =
                newRoyaltyCustomFeeMeta(
                        List.of(
                                FcCustomFee.fixedFee(1, null, otherCollector, false),
                                FcCustomFee.royaltyFee(1, 2, null, targetCollector, false),
                                FcCustomFee.royaltyFee(1, 2, null, targetCollector, false)));
        // and:
        final var reclaimable = changesNoLongerWithOriginalUnits();

        given(changeManager.fungibleCreditsInCurrentLevel(payer)).willReturn(reclaimable);

        // when:
        final var result = subject.assessAllRoyalties(trigger, feeMeta, changeManager, accumulator);

        // then:
        assertEquals(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE, result);
    }

    private List<BalanceChange> changesNoLongerWithOriginalUnits() {
        hbarPayerPlusChange.aggregateUnits(-originalUnits / 2);
        htsPayerPlusChange.aggregateUnits(-originalUnits / 2);
        return List.of(hbarPayerPlusChange, htsPayerPlusChange);
    }

    private CustomFeeMeta newRoyaltyCustomFeeMeta(List<FcCustomFee> customFees) {
        return new CustomFeeMeta(firstFungibleTokenId, minter, customFees);
    }

    private final long originalUnits = 100;
    private final Id payer = new Id(0, 1, 2);
    private final EntityId otherCollector = new EntityId(10, 9, 8);
    private final EntityId targetCollector = new EntityId(9, 8, 7);
    private final Id funding = new Id(0, 0, 98);
    private final Id firstFungibleTokenId = new Id(1, 2, 3);
    private final Id minter = new Id(4, 5, 6);
    private final AccountID alias =
            asAliasAccount(ByteString.copyFromUtf8("01234567890123456789012345678901"));
    private final AccountAmount payerCredit =
            AccountAmount.newBuilder()
                    .setAccountID(payer.asGrpcAccount())
                    .setAmount(originalUnits)
                    .build();
    private final BalanceChange hbarPayerPlusChange =
            BalanceChange.changingHbar(payerCredit, payer.asGrpcAccount());
    private final BalanceChange htsPayerPlusChange =
            BalanceChange.changingFtUnits(
                    firstFungibleTokenId,
                    firstFungibleTokenId.asGrpcToken(),
                    payerCredit,
                    payer.asGrpcAccount());
    private final NftTransfer ownershipChange =
            NftTransfer.newBuilder()
                    .setSenderAccountID(payer.asGrpcAccount())
                    .setReceiverAccountID(funding.asGrpcAccount())
                    .build();

    private final NftTransfer ownershipChangeWithAlias =
            NftTransfer.newBuilder()
                    .setSenderAccountID(payer.asGrpcAccount())
                    .setReceiverAccountID(alias)
                    .build();
    private final Id nonFungibleTokenId = new Id(7, 4, 7);
    private final BalanceChange trigger =
            BalanceChange.changingNftOwnership(
                    nonFungibleTokenId,
                    nonFungibleTokenId.asGrpcToken(),
                    ownershipChange,
                    payer.asGrpcAccount());

    private final BalanceChange triggerWithAliasTransfer =
            BalanceChange.changingNftOwnership(
                    nonFungibleTokenId,
                    nonFungibleTokenId.asGrpcToken(),
                    ownershipChangeWithAlias,
                    payer.asGrpcAccount());
    private final long[] effPayerNum = new long[] {payer.num()};
    private final FcAssessedCustomFee hbarAssessed =
            new FcAssessedCustomFee(targetCollector, originalUnits / 2, effPayerNum);
    private final FcAssessedCustomFee htsAssessed =
            new FcAssessedCustomFee(
                    targetCollector,
                    firstFungibleTokenId.asEntityId(),
                    originalUnits / 2,
                    effPayerNum);
}
