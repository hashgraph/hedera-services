/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.pbj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.transaction.CustomFee.FeeOneOfType;
import com.hedera.hapi.node.transaction.RoyaltyFee;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.state.submerkle.FixedFeeSpec;
import org.junit.jupiter.api.Test;

class PbjConverterTest {
    @Test
    void convertsFcCustomFeeToPbjCustomFee() {
        final var fcFee =
                FcCustomFee.royaltyFee(
                        1, 2, new FixedFeeSpec(1, null), new EntityId(1, 2, 5), false);
        final var pbjFee = PbjConverter.fromFcCustomFee(fcFee);

        assertEquals(FeeOneOfType.ROYALTY_FEE, pbjFee.fee().kind());
        assertEquals(fcFee.getFeeCollector().num(), pbjFee.feeCollectorAccountId().accountNum());
        assertEquals(fcFee.getFeeCollector().shard(), pbjFee.feeCollectorAccountId().shardNum());
        assertEquals(fcFee.getFeeCollector().realm(), pbjFee.feeCollectorAccountId().realmNum());
        assertFalse(pbjFee.allCollectorsAreExempt());

        final var value = (RoyaltyFee) pbjFee.fee().value();

        assertTrue(value.hasFallbackFee());
        assertEquals(1, value.exchangeValueFraction().numerator());
        assertEquals(2, value.exchangeValueFraction().denominator());
        assertEquals(1, value.fallbackFee().amount());
        assertNull(value.fallbackFee().denominatingTokenId());
    }
}
