/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.grpc.marshalling.CustomFeeMeta;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import java.util.List;
import org.junit.jupiter.api.Test;

class StandardCustomExemptionsTest {
    private StandardCustomPayerExemptions subject = new StandardCustomPayerExemptions();

    @Test
    void treasuriesAreExemptFromAllFees() {
        assertTrue(subject.isPayerExempt(WELL_KNOWN_META, FEE_WITH_EXEMPTIONS, TREASURY));
        assertTrue(subject.isPayerExempt(WELL_KNOWN_META, FEE_WITHOUT_EXEMPTIONS, TREASURY));
    }

    @Test
    void collectorsAreExemptFromOwnFees() {
        assertTrue(
                subject.isPayerExempt(
                        WELL_KNOWN_META, FEE_WITH_EXEMPTIONS, COLLECTOR_OF_EXEMPT_FEE));
        assertTrue(
                subject.isPayerExempt(
                        WELL_KNOWN_META, FEE_WITHOUT_EXEMPTIONS, COLLECTOR_OF_NON_EXEMPT_FEE));
    }

    @Test
    void collectorsAreNotExemptFromOtherFeesWithoutCollectorsExemption() {
        assertFalse(
                subject.isPayerExempt(
                        WELL_KNOWN_META, FEE_WITHOUT_EXEMPTIONS, COLLECTOR_OF_EXEMPT_FEE));
    }

    @Test
    void nonCollectorsAreNotExemptFromFeesEvenWithCollectorExemption() {
        assertFalse(subject.isPayerExempt(WELL_KNOWN_META, FEE_WITH_EXEMPTIONS, CIVILIAN));
    }

    @Test
    void collectorsAreExemptFromOtherFeesWithCollectorExemption() {
        assertTrue(
                subject.isPayerExempt(
                        WELL_KNOWN_META, FEE_WITH_EXEMPTIONS, COLLECTOR_OF_NON_EXEMPT_FEE));
    }

    private static final Id IRRELEVANT_TOKEN = new Id(0, 0, 12345);
    private static final Id TREASURY = new Id(0, 0, 1001);
    private static final Id CIVILIAN = new Id(0, 0, 1002);
    private static final Id COLLECTOR_OF_EXEMPT_FEE = new Id(0, 0, 1003);
    private static final Id COLLECTOR_OF_NON_EXEMPT_FEE = new Id(0, 0, 1004);

    private static final FcCustomFee FEE_WITHOUT_EXEMPTIONS =
            FcCustomFee.fractionalFee(
                    1, 5, 1, 3, false, COLLECTOR_OF_NON_EXEMPT_FEE.asEntityId(), false);

    private static final FcCustomFee FEE_WITH_EXEMPTIONS =
            FcCustomFee.fractionalFee(
                    1, 5, 1, 3, false, COLLECTOR_OF_EXEMPT_FEE.asEntityId(), true);

    private static final CustomFeeMeta WELL_KNOWN_META =
            new CustomFeeMeta(
                    IRRELEVANT_TOKEN,
                    TREASURY,
                    List.of(FEE_WITH_EXEMPTIONS, FEE_WITHOUT_EXEMPTIONS));
}
