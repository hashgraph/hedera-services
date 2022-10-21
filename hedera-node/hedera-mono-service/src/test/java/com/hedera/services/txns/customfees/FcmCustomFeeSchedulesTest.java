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
package com.hedera.services.txns.customfees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.services.grpc.marshalling.CustomFeeMeta;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FcmCustomFeeSchedulesTest {
    private FcmCustomFeeSchedules subject;

    MerkleMap<EntityNum, MerkleToken> tokens = new MerkleMap<>();

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
        subject = new FcmCustomFeeSchedules(() -> tokens);
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
    void getterWorks() {
        assertEquals(tokens, subject.getTokens().get());
    }

    @Test
    void testObjectContract() {
        // given:
        MerkleMap<EntityNum, MerkleToken> secondMerkleMap = new MerkleMap<>();
        MerkleToken token = new MerkleToken();
        final var missingFees =
                List.of(FcCustomFee.fixedFee(50L, missingToken, feeCollector, false).asGrpc());

        token.setFeeScheduleFrom(missingFees);
        secondMerkleMap.put(EntityNum.fromLong(missingToken.num()), new MerkleToken());
        final var fees1 = new FcmCustomFeeSchedules(() -> tokens);
        final var fees2 = new FcmCustomFeeSchedules(() -> secondMerkleMap);

        // expect:
        assertNotEquals(fees1, fees2);
        assertNotEquals(fees1.hashCode(), fees2.hashCode());
    }
}
