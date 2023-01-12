/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.hapi.fees.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.node.app.hapi.fees.usage.token.meta.ExtantFeeScheduleContext;
import com.hedera.node.app.hapi.fees.usage.token.meta.FeeScheduleUpdateMeta;
import org.junit.jupiter.api.Test;

class MetadataObjectContractsTest {
    @Test
    void baseTxnMetaObjContractSanityChecks() {
        // given:
        final var a = new BaseTransactionMeta(12, 3);
        final var b = new BaseTransactionMeta(23, 4);
        final var c = new BaseTransactionMeta(12, 3);

        // expect:
        assertEquals(a, c);
        assertNotEquals(b, c);
        assertNotEquals(b.hashCode(), c.hashCode());
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void extantFeeScheduleCtxObjContractSanityChecks() {
        // given:
        final var a = new ExtantFeeScheduleContext(12, 3);
        final var b = new ExtantFeeScheduleContext(23, 4);
        final var c = new ExtantFeeScheduleContext(12, 3);

        // expect:
        assertEquals(a, c);
        assertNotEquals(b, c);
        assertNotEquals(b.hashCode(), c.hashCode());
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void feeScheduleMetaObjContractSanityChecks() {
        // given:
        final var a = new FeeScheduleUpdateMeta(12, 3);
        final var b = new FeeScheduleUpdateMeta(23, 4);
        final var c = new FeeScheduleUpdateMeta(12, 3);

        // expect:
        assertEquals(a, c);
        assertNotEquals(b, c);
        assertNotEquals(b.hashCode(), c.hashCode());
        assertNotEquals(a.hashCode(), b.hashCode());
    }
}
