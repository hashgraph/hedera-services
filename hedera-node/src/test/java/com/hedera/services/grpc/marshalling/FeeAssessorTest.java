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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.NftTransfer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeAssessorTest {
    private final List<FcAssessedCustomFee> accumulator = new ArrayList<>();
    private final ImpliedTransfersMeta.ValidationProps props =
            new ImpliedTransfersMeta.ValidationProps(0, 0, 0, 1, 20, true, true, true);

    @Mock private FixedFeeAssessor fixedFeeAssessor;
    @Mock private FractionalFeeAssessor fractionalFeeAssessor;
    @Mock private RoyaltyFeeAssessor royaltyFeeAssessor;
    @Mock private CustomSchedulesManager customSchedulesManager;
    @Mock private BalanceChangeManager balanceChangeManager;

    private FeeAssessor subject;

    @BeforeEach
    void setUp() {
        subject = new FeeAssessor(fixedFeeAssessor, royaltyFeeAssessor, fractionalFeeAssessor);
    }

    @Test
    void abortsOnExcessiveNesting() {
        given(balanceChangeManager.getLevelNo()).willReturn(2);

        assertEquals(
                CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH,
                subject.assess(
                        fungibleTrigger,
                        customSchedulesManager,
                        balanceChangeManager,
                        accumulator,
                        props));
    }

    @Test
    void okOnNoFees() {
        givenFees(fungibleTokenId.asEntityId(), List.of());

        // when:
        final var result =
                subject.assess(
                        fungibleTrigger,
                        customSchedulesManager,
                        balanceChangeManager,
                        accumulator,
                        props);

        // then:
        verifyNoInteractions(fixedFeeAssessor, royaltyFeeAssessor, fractionalFeeAssessor);
        assertEquals(OK, result);
    }

    @Test
    void exemptForTreasury() {
        givenFees(fungibleTokenId.asEntityId(), List.of(hbarFee, htsFee, fractionalFee));

        // when:
        final var result =
                subject.assess(
                        treasuryTrigger,
                        customSchedulesManager,
                        balanceChangeManager,
                        accumulator,
                        props);

        // then:
        verifyNoInteractions(fixedFeeAssessor, royaltyFeeAssessor, fractionalFeeAssessor);
        assertEquals(OK, result);
    }

    @Test
    void exemptsSelfPayments() {
        // setup:
        final var feeMeta = newCustomMetaFee(List.of(htsFee, fractionalFee));
        givenFees(fungibleTokenId.asEntityId(), feeMeta.customFees());
        given(
                        fractionalFeeAssessor.assessAllFractional(
                                collectorTrigger, feeMeta, balanceChangeManager, accumulator))
                .willReturn(OK);

        // when:
        final var result =
                subject.assess(
                        collectorTrigger,
                        customSchedulesManager,
                        balanceChangeManager,
                        accumulator,
                        props);

        // then:
        verifyNoInteractions(fixedFeeAssessor);
        assertEquals(OK, result);
    }

    @Test
    void processMultiFeesCorrectly() {
        givenFees(fungibleTokenId.asEntityId(), List.of(htsFee, hbarFee, htsFee));

        // when:
        final var result =
                subject.assess(
                        fungibleTrigger,
                        customSchedulesManager,
                        balanceChangeManager,
                        accumulator,
                        props);

        // then:
        verify(fixedFeeAssessor).assess(payer, meta, hbarFee, balanceChangeManager, accumulator);
        verify(fixedFeeAssessor, times(2))
                .assess(payer, meta, htsFee, balanceChangeManager, accumulator);
        assertEquals(OK, result);
    }

    @Test
    void shouldProcessAllFixedFees() {
        // setup:
        final var feeMeta = newCustomMetaFee(List.of(hbarFee, htsFee, fractionalFee, htsFee));
        givenFees(fungibleTokenId.asEntityId(), feeMeta.customFees());
        given(
                        fractionalFeeAssessor.assessAllFractional(
                                fungibleTrigger, feeMeta, balanceChangeManager, accumulator))
                .willReturn(OK);

        // when:
        final var result =
                subject.assess(
                        fungibleTrigger,
                        customSchedulesManager,
                        balanceChangeManager,
                        accumulator,
                        props);

        // then:
        verify(fixedFeeAssessor).assess(payer, feeMeta, hbarFee, balanceChangeManager, accumulator);
        verify(fixedFeeAssessor, times(2))
                .assess(payer, feeMeta, htsFee, balanceChangeManager, accumulator);
        verify(fractionalFeeAssessor)
                .assessAllFractional(fungibleTrigger, feeMeta, balanceChangeManager, accumulator);
        assertEquals(OK, result);
    }

    @Test
    void useHbarAccessorAppropriately() {
        givenFees(fungibleTokenId.asEntityId(), List.of(hbarFee));

        // when:
        final var result =
                subject.assess(
                        fungibleTrigger,
                        customSchedulesManager,
                        balanceChangeManager,
                        accumulator,
                        props);

        // then:
        verify(fixedFeeAssessor).assess(payer, meta, hbarFee, balanceChangeManager, accumulator);
        assertEquals(OK, result);
    }

    @Test
    void useFractionalAccessorAppropriately() {
        // setup:
        final var feeMeta = newCustomMetaFee(List.of(fractionalFee));
        givenFees(fungibleTokenId.asEntityId(), feeMeta.customFees());
        given(
                        fractionalFeeAssessor.assessAllFractional(
                                fungibleTrigger, feeMeta, balanceChangeManager, accumulator))
                .willReturn(OK);

        // when:
        final var result =
                subject.assess(
                        fungibleTrigger,
                        customSchedulesManager,
                        balanceChangeManager,
                        accumulator,
                        props);

        // then:
        verify(fractionalFeeAssessor)
                .assessAllFractional(fungibleTrigger, feeMeta, balanceChangeManager, accumulator);
        assertEquals(OK, result);
    }

    @Test
    void usesRoyaltyAssessorAppropriately() {
        // setup:
        final var fees = newCustomMetaFee(uniqueTokenId.asEntityId().asId(), List.of(royaltyFee));
        givenFees(uniqueTokenId.asEntityId(), fees.customFees());
        given(
                        royaltyFeeAssessor.assessAllRoyalties(
                                royaltyTrigger, fees, balanceChangeManager, accumulator))
                .willReturn(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);

        // when:
        final var result =
                subject.assess(
                        royaltyTrigger,
                        customSchedulesManager,
                        balanceChangeManager,
                        accumulator,
                        props);

        // then:
        verify(royaltyFeeAssessor)
                .assessAllRoyalties(royaltyTrigger, fees, balanceChangeManager, accumulator);
        assertEquals(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE, result);
    }

    @Test
    void usesRoyaltyAssessorAppropriatelyWhenOk() {
        // setup:
        final var fees = newCustomMetaFee(uniqueTokenId.asEntityId().asId(), List.of(royaltyFee));
        givenFees(uniqueTokenId.asEntityId(), fees.customFees());
        given(
                        royaltyFeeAssessor.assessAllRoyalties(
                                royaltyTrigger, fees, balanceChangeManager, accumulator))
                .willReturn(OK);

        // when:
        final var result =
                subject.assess(
                        royaltyTrigger,
                        customSchedulesManager,
                        balanceChangeManager,
                        accumulator,
                        props);

        // then:
        verify(royaltyFeeAssessor)
                .assessAllRoyalties(royaltyTrigger, fees, balanceChangeManager, accumulator);
        assertEquals(OK, result);
    }

    @Test
    void doesntUseFractionalAccessorIfAllFeesAreFixed() {
        // setup:
        final var feeMeta = newCustomMetaFee(List.of(hbarFee, htsFee));
        givenFees(fungibleTokenId.asEntityId(), feeMeta.customFees());

        // when:
        final var result =
                subject.assess(
                        fungibleTrigger,
                        customSchedulesManager,
                        balanceChangeManager,
                        accumulator,
                        props);

        // then:
        verify(fractionalFeeAssessor, never())
                .assessAllFractional(fungibleTrigger, feeMeta, balanceChangeManager, accumulator);
        assertEquals(OK, result);
    }

    @Test
    void useHtsAccessorAppropriately() {
        givenFees(fungibleTokenId.asEntityId(), List.of(htsFee));

        // when:
        final var result =
                subject.assess(
                        fungibleTrigger,
                        customSchedulesManager,
                        balanceChangeManager,
                        accumulator,
                        props);

        // then:
        verify(fixedFeeAssessor).assess(payer, meta, htsFee, balanceChangeManager, accumulator);
        assertEquals(OK, result);
    }

    @Test
    void abortsOnExcessiveNonFractionalChanges() {
        // setup:
        final var feeMeta = newCustomMetaFee(List.of(hbarFee, htsFee, fractionalFee));
        givenFees(fungibleTokenId.asEntityId(), feeMeta.customFees());
        given(balanceChangeManager.numChangesSoFar()).willReturn(20).willReturn(21);

        // when:
        final var result =
                subject.assess(
                        fungibleTrigger,
                        customSchedulesManager,
                        balanceChangeManager,
                        accumulator,
                        props);

        // then:
        verify(fixedFeeAssessor).assess(payer, meta, hbarFee, balanceChangeManager, accumulator);
        verify(fixedFeeAssessor).assess(payer, meta, htsFee, balanceChangeManager, accumulator);
        verify(fractionalFeeAssessor, never())
                .assessAllFractional(fungibleTrigger, feeMeta, balanceChangeManager, accumulator);
        assertEquals(CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS, result);
    }

    @Test
    void propagatesFailedFractionalChanges() {
        // setup:
        final var feeMeta = newCustomMetaFee(List.of(hbarFee, htsFee, fractionalFee));
        givenFees(fungibleTokenId.asEntityId(), feeMeta.customFees());
        given(
                        fractionalFeeAssessor.assessAllFractional(
                                fungibleTrigger, feeMeta, balanceChangeManager, accumulator))
                .willReturn(CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE);

        // when:
        final var result =
                subject.assess(
                        fungibleTrigger,
                        customSchedulesManager,
                        balanceChangeManager,
                        accumulator,
                        props);

        // then:
        verify(fixedFeeAssessor).assess(payer, meta, hbarFee, balanceChangeManager, accumulator);
        verify(fixedFeeAssessor).assess(payer, meta, htsFee, balanceChangeManager, accumulator);
        verify(fractionalFeeAssessor)
                .assessAllFractional(fungibleTrigger, feeMeta, balanceChangeManager, accumulator);
        assertEquals(CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE, result);
    }

    @Test
    void abortsOnExcessiveFractionalChanges() {
        // setup:
        final var feeMeta = newCustomMetaFee(List.of(hbarFee, htsFee, fractionalFee));
        givenFees(fungibleTokenId.asEntityId(), feeMeta.customFees());
        given(balanceChangeManager.numChangesSoFar()).willReturn(19).willReturn(20).willReturn(21);
        given(
                        fractionalFeeAssessor.assessAllFractional(
                                fungibleTrigger, feeMeta, balanceChangeManager, accumulator))
                .willReturn(OK);

        // when:
        final var result =
                subject.assess(
                        fungibleTrigger,
                        customSchedulesManager,
                        balanceChangeManager,
                        accumulator,
                        props);

        // then:
        verify(fixedFeeAssessor).assess(payer, meta, hbarFee, balanceChangeManager, accumulator);
        verify(fixedFeeAssessor).assess(payer, meta, htsFee, balanceChangeManager, accumulator);
        verify(fractionalFeeAssessor)
                .assessAllFractional(fungibleTrigger, feeMeta, balanceChangeManager, accumulator);
        assertEquals(CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS, result);
    }

    private CustomFeeMeta meta;

    private void givenFees(EntityId token, List<FcCustomFee> customFees) {
        meta = new CustomFeeMeta(token.asId(), treasury, customFees);
        given(customSchedulesManager.managedSchedulesFor(token.asId())).willReturn(meta);
    }

    private CustomFeeMeta newCustomMetaFee(List<FcCustomFee> fees) {
        return newCustomMetaFee(fungibleTokenId, fees);
    }

    private CustomFeeMeta newCustomMetaFee(Id id, List<FcCustomFee> fees) {
        return new CustomFeeMeta(id, treasury, fees);
    }

    private final long amountOfFungibleDebit = 1_000L;
    private final long amountOfHbarFee = 100_000L;
    private final long amountOfHtsFee = 10L;
    private final long minAmountOfFractionalFee = 1L;
    private final long maxAmountOfFractionalFee = 100L;
    private final long numerator = 1L;
    private final long denominator = 100L;
    private final Id payer = new Id(0, 1, 2);
    private final Id treasury = new Id(6, 6, 6);
    private final Id fungibleTokenId = new Id(1, 2, 3);
    private final Id uniqueTokenId = new Id(11, 22, 33);
    private final EntityId feeDenom = new EntityId(6, 6, 6);
    private final EntityId hbarFeeCollector = new EntityId(2, 3, 4);
    private final EntityId htsFeeCollector = new EntityId(3, 4, 5);
    private final EntityId royaltyFeeCollector = new EntityId(5, 6, 7);
    private final EntityId fractionalFeeCollector = new EntityId(4, 5, 6);
    private final AccountAmount fungibleDebit =
            AccountAmount.newBuilder()
                    .setAccountID(payer.asGrpcAccount())
                    .setAmount(amountOfFungibleDebit)
                    .build();
    private final AccountAmount fungibleTreasuryDebit =
            AccountAmount.newBuilder()
                    .setAccountID(treasury.asGrpcAccount())
                    .setAmount(amountOfFungibleDebit)
                    .build();
    private final AccountAmount fungibleCollectorDebit =
            AccountAmount.newBuilder()
                    .setAccountID(htsFeeCollector.asId().asGrpcAccount())
                    .setAmount(amountOfFungibleDebit)
                    .build();
    private final BalanceChange fungibleTrigger =
            BalanceChange.changingFtUnits(
                    fungibleTokenId,
                    fungibleTokenId.asGrpcToken(),
                    fungibleDebit,
                    payer.asGrpcAccount());
    private final BalanceChange treasuryTrigger =
            BalanceChange.changingFtUnits(
                    fungibleTokenId,
                    fungibleTokenId.asGrpcToken(),
                    fungibleTreasuryDebit,
                    payer.asGrpcAccount());
    private final BalanceChange collectorTrigger =
            BalanceChange.changingFtUnits(
                    fungibleTokenId,
                    fungibleTokenId.asGrpcToken(),
                    fungibleCollectorDebit,
                    payer.asGrpcAccount());
    private final BalanceChange royaltyTrigger =
            BalanceChange.changingNftOwnership(
                    uniqueTokenId,
                    uniqueTokenId.asGrpcToken(),
                    NftTransfer.newBuilder()
                            .setSenderAccountID(payer.asGrpcAccount())
                            .setReceiverAccountID(treasury.asGrpcAccount())
                            .setSerialNumber(666L)
                            .build(),
                    payer.asGrpcAccount());
    private final FcCustomFee hbarFee =
            FcCustomFee.fixedFee(amountOfHbarFee, null, hbarFeeCollector, false);
    private final FcCustomFee htsFee =
            FcCustomFee.fixedFee(amountOfHtsFee, feeDenom, htsFeeCollector, false);
    private final FcCustomFee fractionalFee =
            FcCustomFee.fractionalFee(
                    numerator,
                    denominator,
                    minAmountOfFractionalFee,
                    maxAmountOfFractionalFee,
                    false,
                    fractionalFeeCollector,
                    false);
    private final FcCustomFee royaltyFee =
            FcCustomFee.royaltyFee(1, 2, null, royaltyFeeCollector, false);
}
