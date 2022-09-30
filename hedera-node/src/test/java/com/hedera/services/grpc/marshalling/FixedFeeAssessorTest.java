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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.BDDMockito.given;

import com.hedera.services.fees.CustomFeeExemptions;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FixedFeeAssessorTest {
    private final List<FcAssessedCustomFee> mockAccum = Collections.emptyList();

    @Mock private BalanceChangeManager changeManager;
    @Mock private HtsFeeAssessor htsFeeAssessor;
    @Mock private HbarFeeAssessor hbarFeeAssessor;
    @Mock private CustomFeeExemptions customFeeExemptions;

    private FixedFeeAssessor subject;

    @BeforeEach
    void setUp() {
        subject = new FixedFeeAssessor(htsFeeAssessor, hbarFeeAssessor, customFeeExemptions);
    }

    @Test
    void delegatesToHbarWhenDenomIsNull() {
        final var hbarFee = FcCustomFee.fixedFee(1, null, otherCollector, false);

        given(hbarFeeAssessor.assess(payer, hbarFee, changeManager, mockAccum)).willReturn(OK);

        // when:
        final var result = subject.assess(payer, MISSING_ID, hbarFee, changeManager, mockAccum);

        // then:
        Assertions.assertEquals(OK, result);
    }

    @Test
    void delegatesToHtsWhenDenomIsNonNull() {
        FcCustomFee htsFee = FcCustomFee.fixedFee(1, feeDenom, otherCollector, false);

        given(htsFeeAssessor.assess(payer, chargingToken, htsFee, changeManager, mockAccum))
                .willReturn(OK);

        // when:
        final var result = subject.assess(payer, chargingToken, htsFee, changeManager, mockAccum);

        // then:
        Assertions.assertEquals(OK, result);
    }

    private final EntityId feeDenom = new EntityId(6, 6, 6);
    private final Id payer = new Id(0, 1, 2);
    private final EntityId otherCollector = new EntityId(10, 9, 8);
    private final Id chargingToken = new Id(0, 1, 2222);
}
