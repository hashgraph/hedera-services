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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.payer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.contracts.precompile.codec.TokenExpiryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenExpiryWrapperTest {

    private TokenExpiryWrapper wrapper;

    @BeforeEach
    void setup() {
        wrapper = createTokenExpiryWrapper();
    }

    @Test
    void autoRenewAccountIsCheckedAsExpected() {
        assertEquals(payer, wrapper.autoRenewAccount());
        assertEquals(442L, wrapper.second());
        assertEquals(555L, wrapper.autoRenewPeriod());
        wrapper.setAutoRenewAccount(EntityId.fromIdentityCode(10).toGrpcAccountId());
        assertEquals(EntityId.fromIdentityCode(10).toGrpcAccountId(), wrapper.autoRenewAccount());
    }

    @Test
    void objectContractWorks() {
        final var one = wrapper;
        final var two = createTokenExpiryWrapper();
        final var three = createTokenExpiryWrapper();
        three.setAutoRenewAccount(EntityId.fromIdentityCode(10).toGrpcAccountId());

        assertNotEquals(null, one);
        assertNotEquals(new Object(), one);
        assertEquals(one, two);
        assertNotEquals(one, three);

        assertNotEquals(one.hashCode(), three.hashCode());
        assertEquals(one.hashCode(), two.hashCode());

        assertEquals(
                "TokenExpiryWrapper{second=442, autoRenewAccount=accountNum: 12345\n"
                        + ", autoRenewPeriod=555}",
                wrapper.toString());
    }

    public static TokenExpiryWrapper createTokenExpiryWrapper() {
        return new TokenExpiryWrapper(442L, payer, 555L);
    }
}
