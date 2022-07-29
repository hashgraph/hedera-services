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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.NftTransfer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BalanceChangeManagerTest {
    private BalanceChangeManager subject;

    @BeforeEach
    void setUp() {
        subject = new BalanceChangeManager(startingList, 2);
    }

    @Test
    void markingRoyaltiesWorks() {
        // expect:
        assertFalse(subject.isRoyaltyPaid(nonFungibleTokenId, misc));

        // and when:
        subject.markRoyaltyPaid(nonFungibleTokenId, misc);

        // then:
        assertTrue(subject.isRoyaltyPaid(nonFungibleTokenId, misc));
    }

    @Test
    void initializesLevelData() {
        // expect:
        assertEquals(0, subject.getLevelNo());
        assertEquals(0, subject.getLevelStart());
        assertEquals(8, subject.getLevelEnd());
    }

    @Test
    void understandsTriggerCandidates() {
        // expect:
        assertSame(firstFungibleTrigger, subject.nextAssessableChange());
        assertSame(secondFungibleTrigger, subject.nextAssessableChange());
        assertSame(firstNonFungibleTrigger, subject.nextAssessableChange());
        assertNull(subject.nextAssessableChange());
    }

    @Test
    void changesSoFarAreSized() {
        // expect:
        assertEquals(8, subject.numChangesSoFar());
    }

    @Test
    void includesChangeAsExpected() {
        // when:
        subject.includeChange(miscHbarAdjust);
        // and:
        final var newChanges = subject.getChangesSoFar();

        // then:
        assertEquals(9, newChanges.size());
        assertSame(miscHbarAdjust, newChanges.get(8));
        assertSame(miscHbarAdjust, subject.changeFor(misc, Id.MISSING_ID));
    }

    @Test
    void failsHardOnRepeatedHbarInclusion() {
        // expect:
        assertThrows(IllegalArgumentException.class, () -> subject.includeChange(payerHbarAdjust));
    }

    @Test
    void existingChangesAreIndexed() {
        // expect:
        assertSame(payerHbarAdjust, subject.changeFor(payer, Id.MISSING_ID));
        assertSame(fundingHbarAdjust, subject.changeFor(funding, Id.MISSING_ID));
        assertSame(firstFungibleTrigger, subject.changeFor(payer, firstFungibleTokenId));
        assertSame(firstFungibleNonTrigger, subject.changeFor(funding, firstFungibleTokenId));
        assertSame(secondFungibleTrigger, subject.changeFor(payer, secondFungibleTokenId));
        assertSame(secondFungibleNonTrigger, subject.changeFor(funding, secondFungibleTokenId));
        assertNull(subject.changeFor(payer, nonFungibleTokenId));
    }

    @Test
    void understandsLevelCredits() {
        // setup:
        final List<BalanceChange> smallStarterList = new ArrayList<>();
        smallStarterList.add(firstNonFungibleTrigger);
        smallStarterList.add(firstCredit);
        smallStarterList.add(secondCredit);

        // given:
        subject = new BalanceChangeManager(smallStarterList, 0);

        // expect:
        bothCreditsInCurrentLevel();

        // and when:
        assertSame(firstNonFungibleTrigger, subject.nextAssessableChange());
        subject.includeChange(secondNonFungibleTrigger);
        subject.includeChange(firstCredit);
        subject.includeChange(secondCredit);
        assertSame(secondNonFungibleTrigger, subject.nextAssessableChange());

        // expect:
        bothCreditsInCurrentLevel();
    }

    @Test
    void canFindFungibleCredits() {
        // setup:
        final List<BalanceChange> nftExchangeList = new ArrayList<>();
        nftExchangeList.add(hbarPayerPlusChange);
        nftExchangeList.add(firstNonFungibleTrigger);
        nftExchangeList.add(firstCredit);
        nftExchangeList.add(secondCredit);
        nftExchangeList.add(htsPayerPlusChange);

        // given:
        subject = new BalanceChangeManager(nftExchangeList, 1);

        // when:
        final var fungibleCredits = subject.fungibleCreditsInCurrentLevel(payer);

        // then:
        assertEquals(2, fungibleCredits.size());
        assertSame(hbarPayerPlusChange, fungibleCredits.get(0));
        assertSame(htsPayerPlusChange, fungibleCredits.get(1));
    }

    private void bothCreditsInCurrentLevel() {
        final var inLevel = subject.creditsInCurrentLevel(repeatedCreditsFungibleTokenId);
        assertSame(firstCredit, inLevel.get(0));
        assertSame(secondCredit, inLevel.get(1));
    }

    @Test
    void levelChangesWork() {
        // setup:
        final List<BalanceChange> smallStarterList = new ArrayList<>();
        smallStarterList.add(secondNonFungibleTrigger);

        // given:
        subject = new BalanceChangeManager(smallStarterList, 0);

        // when:
        assertSame(secondNonFungibleTrigger, subject.nextAssessableChange());
        // and:
        subject.includeChange(firstFungibleTrigger);
        // and:
        assertSame(firstFungibleTrigger, subject.nextAssessableChange());

        // then:
        assertEquals(1, subject.getLevelNo());
        assertEquals(1, subject.getLevelStart());
        assertEquals(2, subject.getLevelEnd());

        // and when:
        subject.includeChange(firstFungibleNonTrigger);
        subject.includeChange(secondFungibleTrigger);
        subject.includeChange(secondFungibleNonTrigger);
        // and:
        assertSame(secondFungibleTrigger, subject.nextAssessableChange());

        // then:
        assertEquals(2, subject.getLevelNo());
        assertEquals(2, subject.getLevelStart());
        assertEquals(5, subject.getLevelEnd());
    }

    private final long amountOfFirstFungibleDebit = 1_000L;
    private final long amountOfSecondFungibleDebit = 2_000L;
    private final Id misc = new Id(1, 1, 2);
    private final Id payer = new Id(0, 1, 2);
    private final Id funding = new Id(0, 0, 98);
    private final Id firstFungibleTokenId = new Id(1, 2, 3);
    private final Id nonFungibleTokenId = new Id(7, 4, 7);
    private final Id secondFungibleTokenId = new Id(3, 2, 1);
    private final Id repeatedCreditsFungibleTokenId = new Id(4, 3, 2);
    private final AccountAmount payerCredit =
            AccountAmount.newBuilder().setAccountID(payer.asGrpcAccount()).setAmount(100).build();
    private final AccountAmount firstFungibleDebit =
            AccountAmount.newBuilder()
                    .setAccountID(payer.asGrpcAccount())
                    .setAmount(-amountOfFirstFungibleDebit)
                    .build();
    private final AccountAmount firstFungibleCredit =
            AccountAmount.newBuilder()
                    .setAccountID(funding.asGrpcAccount())
                    .setAmount(+amountOfFirstFungibleDebit)
                    .build();
    private final AccountAmount secondFungibleDebit =
            AccountAmount.newBuilder()
                    .setAccountID(payer.asGrpcAccount())
                    .setAmount(-amountOfSecondFungibleDebit)
                    .build();
    private final AccountAmount secondFungibleCredit =
            AccountAmount.newBuilder()
                    .setAccountID(funding.asGrpcAccount())
                    .setAmount(+amountOfSecondFungibleDebit)
                    .build();
    private final BalanceChange hbarPayerPlusChange =
            BalanceChange.changingHbar(payerCredit, payer.asGrpcAccount());
    private final BalanceChange htsPayerPlusChange =
            BalanceChange.changingFtUnits(
                    firstFungibleTokenId,
                    firstFungibleTokenId.asGrpcToken(),
                    payerCredit,
                    payer.asGrpcAccount());
    private final BalanceChange firstFungibleTrigger =
            BalanceChange.changingFtUnits(
                    firstFungibleTokenId,
                    firstFungibleTokenId.asGrpcToken(),
                    firstFungibleDebit,
                    payer.asGrpcAccount());
    private final BalanceChange exemptFungibleTrigger =
            BalanceChange.changingFtUnits(
                    firstFungibleTokenId,
                    firstFungibleTokenId.asGrpcToken(),
                    firstFungibleDebit,
                    payer.asGrpcAccount());

    {
        exemptFungibleTrigger.setExemptFromCustomFees(true);
    }

    private final BalanceChange firstFungibleNonTrigger =
            BalanceChange.changingFtUnits(
                    firstFungibleTokenId,
                    firstFungibleTokenId.asGrpcToken(),
                    firstFungibleCredit,
                    payer.asGrpcAccount());
    private final BalanceChange secondFungibleTrigger =
            BalanceChange.changingFtUnits(
                    secondFungibleTokenId,
                    secondFungibleTokenId.asGrpcToken(),
                    secondFungibleDebit,
                    payer.asGrpcAccount());
    private final BalanceChange secondFungibleNonTrigger =
            BalanceChange.changingFtUnits(
                    secondFungibleTokenId,
                    secondFungibleTokenId.asGrpcToken(),
                    secondFungibleCredit,
                    payer.asGrpcAccount());
    private final BalanceChange firstCredit =
            BalanceChange.changingFtUnits(
                    repeatedCreditsFungibleTokenId,
                    repeatedCreditsFungibleTokenId.asGrpcToken(),
                    firstFungibleCredit,
                    payer.asGrpcAccount());
    private final BalanceChange secondCredit =
            BalanceChange.changingFtUnits(
                    repeatedCreditsFungibleTokenId,
                    repeatedCreditsFungibleTokenId.asGrpcToken(),
                    secondFungibleCredit,
                    payer.asGrpcAccount());
    private final NftTransfer firstOwnershipChange =
            NftTransfer.newBuilder()
                    .setSenderAccountID(payer.asGrpcAccount())
                    .setReceiverAccountID(funding.asGrpcAccount())
                    .build();
    private final BalanceChange firstNonFungibleTrigger =
            BalanceChange.changingNftOwnership(
                    nonFungibleTokenId,
                    nonFungibleTokenId.asGrpcToken(),
                    firstOwnershipChange,
                    payer.asGrpcAccount());
    private final BalanceChange secondNonFungibleTrigger =
            BalanceChange.changingNftOwnership(
                    nonFungibleTokenId,
                    nonFungibleTokenId.asGrpcToken(),
                    firstOwnershipChange,
                    payer.asGrpcAccount());
    private final BalanceChange payerHbarAdjust = BalanceChange.hbarAdjust(payer, -1_234_567);
    private final BalanceChange fundingHbarAdjust = BalanceChange.hbarAdjust(funding, +1_234_567);
    private final BalanceChange miscHbarAdjust = BalanceChange.hbarAdjust(misc, +1);

    private final List<BalanceChange> startingList = new ArrayList<>();

    {
        startingList.add(payerHbarAdjust);
        startingList.add(fundingHbarAdjust);
        startingList.add(exemptFungibleTrigger);
        startingList.add(firstFungibleTrigger);
        startingList.add(firstFungibleNonTrigger);
        startingList.add(secondFungibleTrigger);
        startingList.add(secondFungibleNonTrigger);
        startingList.add(firstNonFungibleTrigger);
    }
}
