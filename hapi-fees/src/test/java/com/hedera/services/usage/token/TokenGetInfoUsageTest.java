/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.usage.token;

import static com.hedera.services.hapi.fees.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hedera.services.hapi.utils.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.services.hapi.utils.fee.FeeBuilder.BASIC_QUERY_RES_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.hapi.fees.usage.token.TokenGetInfoUsage;
import com.hedera.services.test.IdUtils;
import com.hedera.services.test.KeyUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.TokenGetInfoQuery;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hedera.services.hapi.utils.fee.FeeBuilder;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenGetInfoUsageTest {
    private Optional<Key> aKey = Optional.of(KeyUtils.A_COMPLEX_KEY);
    private String memo = "Hope";
    private String name = "WhyWhyWhyWHY";
    private String symbol = "OKITSFINE";
    private TokenID id = IdUtils.asToken("0.0.75231");

    private TokenGetInfoUsage subject;

    @BeforeEach
    void setup() {
        subject = TokenGetInfoUsage.newEstimate(tokenQuery());
    }

    @Test
    void assessesEverything() {
        // given:
        subject.givenCurrentAdminKey(aKey)
                .givenCurrentFreezeKey(aKey)
                .givenCurrentWipeKey(aKey)
                .givenCurrentKycKey(aKey)
                .givenCurrentSupplyKey(aKey)
                .givenCurrentPauseKey(aKey)
                .givenCurrentlyUsingAutoRenewAccount()
                .givenCurrentName(name)
                .givenCurrentMemo(memo)
                .givenCurrentSymbol(symbol);
        // and:
        var expectedKeyBytes = 6 * FeeBuilder.getAccountKeyStorageSize(aKey.get());
        var expectedBytes =
                BASIC_QUERY_RES_HEADER
                        + expectedKeyBytes
                        + TOKEN_ENTITY_SIZES.totalBytesInTokenReprGiven(symbol, name)
                        + memo.length()
                        + BASIC_ENTITY_ID_SIZE;

        // when:
        var usage = subject.get();

        // then:
        var node = usage.getNodedata();
        assertEquals(FeeBuilder.BASIC_QUERY_HEADER + BASIC_ENTITY_ID_SIZE, node.getBpt());
        assertEquals(expectedBytes, node.getBpr());
    }

    private Query tokenQuery() {
        var op = TokenGetInfoQuery.newBuilder().setToken(id).build();
        return Query.newBuilder().setTokenGetInfo(op).build();
    }
}
