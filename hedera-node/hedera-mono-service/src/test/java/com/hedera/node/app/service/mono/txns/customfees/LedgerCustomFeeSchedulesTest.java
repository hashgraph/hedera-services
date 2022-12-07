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
package com.hedera.node.app.service.mono.txns.customfees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.node.app.service.mono.grpc.marshalling.CustomFeeMeta;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.backing.BackingTokens;
import com.hedera.node.app.service.mono.ledger.properties.ChangeSummaryManager;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerCustomFeeSchedulesTest {

    private LedgerCustomFeeSchedules subject;

    MerkleMap<EntityNum, MerkleToken> tokens = new MerkleMap<>();
    TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;

    private final EntityId aTreasury = new EntityId(0, 0, 12);
    private final EntityId bTreasury = new EntityId(0, 0, 13);
    private final EntityId tokenA = new EntityId(0, 0, 1);
    private final EntityId tokenB = new EntityId(0, 0, 2);
    private final EntityId feeCollector = new EntityId(0, 0, 3);
    private final EntityId missingToken = new EntityId(0, 0, 4);
    private final MerkleToken aToken = new MerkleToken();
    private final MerkleToken bToken = new MerkleToken();

    @BeforeEach
    void setUp() {
        // setup:
        final var tokenAFees =
                List.of(FcCustomFee.fixedFee(20L, tokenA, feeCollector, false).asGrpc());
        final var tokenBFees =
                List.of(FcCustomFee.fixedFee(40L, tokenB, feeCollector, false).asGrpc());
        aToken.setFeeScheduleFrom(tokenAFees);
        aToken.setTreasury(aTreasury);
        bToken.setFeeScheduleFrom(tokenBFees);
        bToken.setTreasury(bTreasury);

        tokens.put(EntityNum.fromLong(tokenA.num()), aToken);
        tokens.put(EntityNum.fromLong(tokenB.num()), bToken);

        tokensLedger =
                new TransactionalLedger<>(
                        TokenProperty.class,
                        MerkleToken::new,
                        new BackingTokens(() -> tokens),
                        new ChangeSummaryManager<>());
        subject = new LedgerCustomFeeSchedules(tokensLedger);
    }

    @Test
    void validateLookUpScheduleFor() {
        // then:
        final var tokenAFees = subject.lookupMetaFor(tokenA.asId());
        final var tokenBFees = subject.lookupMetaFor(tokenB.asId());
        final var missingTokenFees = subject.lookupMetaFor(missingToken.asId());

        // expect:
        assertEquals(aToken.customFeeSchedule(), tokenAFees.customFees());
        assertEquals(aTreasury, tokenAFees.treasuryId().asEntityId());
        assertEquals(bToken.customFeeSchedule(), tokenBFees.customFees());
        assertEquals(bTreasury, tokenBFees.treasuryId().asEntityId());
        final var missingId = missingToken.asId();
        assertEquals(CustomFeeMeta.forMissingLookupOf(missingId), missingTokenFees);
    }

    @Test
    void validateLookUpScheduleForUsingLedger() {
        // then:
        final var tokenAFees = subject.lookupMetaFor(tokenA.asId());
        final var tokenBFees = subject.lookupMetaFor(tokenB.asId());
        final var missingTokenFees = subject.lookupMetaFor(missingToken.asId());

        // expect:
        assertEquals(aToken.customFeeSchedule(), tokenAFees.customFees());
        assertEquals(aTreasury, tokenAFees.treasuryId().asEntityId());
        assertEquals(bToken.customFeeSchedule(), tokenBFees.customFees());
        assertEquals(bTreasury, tokenBFees.treasuryId().asEntityId());
        final var missingId = missingToken.asId();
        assertEquals(CustomFeeMeta.forMissingLookupOf(missingId), missingTokenFees);
    }

    @Test
    void getterWorks() {
        assertEquals(tokensLedger, subject.getTokens());
    }

    @Test
    void testObjectContract() {
        // given:
        MerkleMap<EntityNum, MerkleToken> secondMerkleMap = new MerkleMap<>();
        final var token = new MerkleToken();
        final var missingFees =
                List.of(FcCustomFee.fixedFee(50L, missingToken, feeCollector, false).asGrpc());

        token.setFeeScheduleFrom(missingFees);
        secondMerkleMap.put(EntityNum.fromLong(missingToken.num()), new MerkleToken());

        final var secondTokensLedger =
                new TransactionalLedger<>(
                        TokenProperty.class,
                        MerkleToken::new,
                        new BackingTokens(() -> secondMerkleMap),
                        new ChangeSummaryManager<>());

        final var fees1 = new LedgerCustomFeeSchedules(tokensLedger);
        final var fees2 = new LedgerCustomFeeSchedules(secondTokensLedger);

        // expect:
        assertNotEquals(fees1, fees2);
        assertNotEquals(fees1.hashCode(), fees2.hashCode());
    }
}
