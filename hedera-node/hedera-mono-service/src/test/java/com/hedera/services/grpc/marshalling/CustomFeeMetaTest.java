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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomFeeMetaTest {
    @Test
    void allowsConstructingUsefulMissingCustomFeeMeta() {
        final var usableMissingMeta = CustomFeeMeta.forMissingLookupOf(aToken);
        assertEquals(aToken, usableMissingMeta.tokenId());
        assertEquals(Id.MISSING_ID, usableMissingMeta.treasuryId());
        assertEquals(Collections.emptyList(), usableMissingMeta.customFees());
    }

    @Test
    void objectContractWorks() {
        // setup:
        final var subject = new CustomFeeMeta(aToken, aTreasury, List.of(hbarFee));
        final var differentToken = new CustomFeeMeta(bToken, aTreasury, List.of(hbarFee));
        final var differentTreasury = new CustomFeeMeta(aToken, bTreasury, List.of(hbarFee));
        final var differentFees = new CustomFeeMeta(aToken, aTreasury, List.of(htsFee));
        final var sameButDifferent = new CustomFeeMeta(aToken, aTreasury, List.of(hbarFee));
        final var identical = subject;

        // expect:
        assertNotEquals(null, subject);
        assertNotEquals(subject, new Object());
        assertNotEquals(subject, differentFees);
        assertNotEquals(subject, differentToken);
        assertNotEquals(subject, differentTreasury);
        assertNotEquals(subject.hashCode(), differentFees.hashCode());
        assertNotEquals(subject.hashCode(), differentToken.hashCode());
        assertNotEquals(subject.hashCode(), differentTreasury.hashCode());
        // and:
        assertEquals(subject, identical);
        assertEquals(subject, sameButDifferent);
        assertEquals(subject.hashCode(), sameButDifferent.hashCode());
    }

    @Test
    void toStringWorks() {
        final var subject = new CustomFeeMeta(aToken, aTreasury, List.of(hbarFee, htsFee));

        // given:
        final var desired =
                "CustomFeeMeta[tokenId=1.1.1, treasuryId=9.9.9,"
                        + " customFees=[FcCustomFee{feeType=FIXED_FEE,"
                        + " fixedFee=FixedFeeSpec{unitsToCollect=100000, tokenDenomination=ℏ},"
                        + " feeCollector=EntityId{shard=2, realm=3, num=4},"
                        + " allCollectorsAreExempt=false}, FcCustomFee{feeType=FIXED_FEE,"
                        + " fixedFee=FixedFeeSpec{unitsToCollect=10, tokenDenomination=6.6.6},"
                        + " feeCollector=EntityId{shard=3, realm=4, num=5},"
                        + " allCollectorsAreExempt=false}]]";

        // expect:
        assertEquals(desired, subject.toString());
    }

    private final long amountOfHbarFee = 100_000L;
    private final long amountOfHtsFee = 10L;
    private final Id aToken = new Id(1, 1, 1);
    private final Id bToken = new Id(11, 11, 11);
    private final Id aTreasury = new Id(9, 9, 9);
    private final Id bTreasury = new Id(99, 99, 99);
    private final Id feeDenom = new Id(6, 6, 6);
    private final Id hbarFeeCollector = new Id(2, 3, 4);
    private final Id htsFeeCollector = new Id(3, 4, 5);
    private final FcCustomFee hbarFee =
            FcCustomFee.fixedFee(amountOfHbarFee, null, hbarFeeCollector.asEntityId(), false);
    private final FcCustomFee htsFee =
            FcCustomFee.fixedFee(
                    amountOfHtsFee, feeDenom.asEntityId(), htsFeeCollector.asEntityId(), false);
}
